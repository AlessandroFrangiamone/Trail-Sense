package com.kylecorry.trail_sense.tools.maps.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.core.system.GeoUri
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.core.ui.Colors.withAlpha
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentMapsViewBinding
import com.kylecorry.trail_sense.navigation.beacons.domain.Beacon
import com.kylecorry.trail_sense.navigation.beacons.domain.BeaconOwner
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.persistence.BeaconRepo
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.persistence.BeaconService
import com.kylecorry.trail_sense.navigation.paths.domain.Path
import com.kylecorry.trail_sense.navigation.paths.infrastructure.PathLoader
import com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.navigation.paths.ui.asMappable
import com.kylecorry.trail_sense.navigation.ui.NavigatorFragment
import com.kylecorry.trail_sense.navigation.ui.data.UpdateTideLayerCommand
import com.kylecorry.trail_sense.navigation.ui.layers.BeaconLayer
import com.kylecorry.trail_sense.navigation.ui.layers.MyAccuracyLayer
import com.kylecorry.trail_sense.navigation.ui.layers.MyLocationLayer
import com.kylecorry.trail_sense.navigation.ui.layers.NavigationLayer
import com.kylecorry.trail_sense.navigation.ui.layers.PathLayer
import com.kylecorry.trail_sense.navigation.ui.layers.TideLayer
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Position
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.extensions.onIO
import com.kylecorry.trail_sense.shared.extensions.onMain
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sharing.ActionItem
import com.kylecorry.trail_sense.shared.sharing.Share
import com.kylecorry.trail_sense.tools.maps.domain.PhotoMap
import com.kylecorry.trail_sense.tools.maps.infrastructure.MapRepo
import com.kylecorry.trail_sense.tools.maps.ui.commands.CreatePathCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class ViewMapFragment : BoundFragment<FragmentMapsViewBinding>() {

    private val sensorService by lazy { SensorService(requireContext()) }
    private val gps by lazy { sensorService.getGPS() }
    private val altimeter by lazy { sensorService.getAltimeter() }
    private val compass by lazy { sensorService.getCompass() }
    private val hasCompass by lazy { sensorService.hasCompass() }
    private val beaconRepo by lazy { BeaconRepo.getInstance(requireContext()) }
    private val beaconService by lazy { BeaconService(requireContext()) }
    private val cache by lazy { PreferencesSubsystem.getInstance(requireContext()).preferences }
    private val mapRepo by lazy { MapRepo.getInstance(requireContext()) }
    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }

    // Map layers
    private val tideLayer = TideLayer()
    private val beaconLayer = BeaconLayer { navigateTo(it) }
    private val pathLayer = PathLayer()
    private val distanceLayer = MapDistanceLayer { onDistancePathChange(it) }
    private val myLocationLayer = MyLocationLayer()
    private val myAccuracyLayer = MyAccuracyLayer()
    private val navigationLayer = NavigationLayer()
    private val selectedPointLayer = BeaconLayer()

    // Paths
    private var paths: List<Path> = emptyList()
    private val pathService by lazy { PathService.getInstance(requireContext()) }
    private val pathLoader by lazy { PathLoader(pathService) }

    private var lastDistanceToast: Toast? = null

    private var mapId = 0L
    private var map: PhotoMap? = null
    private var destination: Beacon? = null

    private var mapLockMode = MapLockMode.Free

    private val throttle = Throttle(20)

    private var beacons: List<Beacon> = emptyList()

    private val updateTideLayerCommand by lazy {
        UpdateTideLayerCommand(
            requireContext(),
            tideLayer
        )
    }

    private val tideTimer = Timer {
        updateTides()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapId = requireArguments().getLong("mapId")
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMapsViewBinding {
        return FragmentMapsViewBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.map.setLayers(
            listOf(
                navigationLayer,
                pathLayer,
                myAccuracyLayer,
                myLocationLayer,
                tideLayer,
                beaconLayer,
                selectedPointLayer,
                distanceLayer
            )
        )
        distanceLayer.setOutlineColor(Color.WHITE)
        distanceLayer.setPathColor(Color.BLACK)
        distanceLayer.isEnabled = false
        beaconLayer.setOutlineColor(Color.WHITE)
        selectedPointLayer.setOutlineColor(Color.WHITE)
        myLocationLayer.setColor(AppColor.Orange.color)
        myAccuracyLayer.setColors(
            AppColor.Orange.color,
            Color.TRANSPARENT
        )

        observe(gps) {
            myLocationLayer.setLocation(gps.location)
            myAccuracyLayer.setLocation(gps.location, gps.horizontalAccuracy)
            navigationLayer.setStart(gps.location)
            inBackground {
                updatePaths()
            }
            updateDestination()
            if (!tideTimer.isRunning()) {
                tideTimer.interval(Duration.ofMinutes(1))
            }
            if (mapLockMode != MapLockMode.Free) {
                binding.map.mapCenter = gps.location
            }
        }
        observe(altimeter) { updateDestination() }
        observe(compass) {
            compass.declination = Geology.getGeomagneticDeclination(gps.location, gps.altitude)
            val bearing = compass.bearing
            binding.map.azimuth = bearing
            myLocationLayer.setAzimuth(bearing)
            if (mapLockMode == MapLockMode.Compass) {
                myLocationLayer.setAzimuth(bearing) // TODO: Not sure why this is needed - it shouldn't be
                binding.map.mapAzimuth = bearing.value
            }
            updateDestination()
        }
        observe(beaconRepo.getBeacons()) {
            beacons = it.map { it.toBeacon() }.filter { it.visible }
            updateBeacons()
        }

        observe(pathService.getLivePaths()) {
            inBackground {
                onDefault {
                    paths = it.filter { path -> path.style.visible }
                }
                updatePaths(true)
            }
        }

        reloadMap()

        binding.map.onMapLongClick = {
            onLongPress(it)
        }

        val keepMapUp = prefs.navigation.keepMapFacingUp

        // TODO: Don't show if location not on map

        // Update initial map rotation
        binding.map.mapAzimuth = 0f
        binding.map.keepMapUp = keepMapUp

        // Set the button states
        CustomUiUtils.setButtonState(binding.lockBtn, false)
        CustomUiUtils.setButtonState(binding.zoomInBtn, false)
        CustomUiUtils.setButtonState(binding.zoomOutBtn, false)

        // Handle when the lock button is pressed
        binding.lockBtn.setOnClickListener {
            mapLockMode = getNextLockMode(mapLockMode)

            when (mapLockMode){
                MapLockMode.Location -> {
                    // Disable pan
                    binding.map.isPanEnabled = false

                    // Zoom in and center on location
                    binding.map.metersPerPixel = 0.5f
                    binding.map.mapCenter = gps.location

                    // Reset the rotation
                    binding.map.mapAzimuth = 0f
                    binding.map.keepMapUp = keepMapUp

                    // Show as locked
                    binding.lockBtn.setImageResource(R.drawable.satellite)
                    CustomUiUtils.setButtonState(binding.lockBtn, true)
                }
                MapLockMode.Compass -> {
                    // Disable pan
                    binding.map.isPanEnabled = false

                    // Center on location
                    binding.map.mapCenter = gps.location

                    // Rotate
                    binding.map.keepMapUp = false
                    binding.map.mapAzimuth = -compass.rawBearing

                    // Show as locked
                    binding.lockBtn.setImageResource(R.drawable.ic_compass_icon)
                    CustomUiUtils.setButtonState(binding.lockBtn, true)
                }
                MapLockMode.Free -> {
                    // Enable pan
                    binding.map.isPanEnabled = true

                    // Reset the rotation
                    binding.map.mapAzimuth = 0f
                    binding.map.keepMapUp = keepMapUp

                    // Show as unlocked
                    binding.lockBtn.setImageResource(R.drawable.satellite)
                    CustomUiUtils.setButtonState(binding.lockBtn, false)
                }
            }
        }

        binding.cancelNavigationBtn.setOnClickListener {
            cancelNavigation()
        }

        binding.zoomOutBtn.setOnClickListener {
            binding.map.zoomBy(0.5f)
        }

        binding.zoomInBtn.setOnClickListener {
            binding.map.zoomBy(2f)
        }

        val dest = cache.getLong(NavigatorFragment.LAST_BEACON_ID)
        if (dest != null) {
            inBackground {
                val beacon = withContext(Dispatchers.IO) {
                    beaconRepo.getBeacon(dest)?.toBeacon()
                }
                if (beacon != null) {
                    withContext(Dispatchers.Main) {
                        navigateTo(beacon)
                    }
                }
            }
        }
    }

    private fun onLongPress(location: Coordinate) {
        if (map?.isCalibrated != true || distanceLayer.isEnabled) {
            return
        }

        selectLocation(location)

        lastDistanceToast?.cancel()
        Share.actions(
            this,
            formatService.formatLocation(location),
            listOf(
                ActionItem(getString(R.string.beacon), R.drawable.ic_location) {
                    createBeacon(location)
                    selectLocation(null)
                },
                ActionItem(getString(R.string.navigate), R.drawable.ic_beacon) {
                    navigateTo(location)
                    selectLocation(null)
                },
                ActionItem(getString(R.string.distance), R.drawable.ruler) {
                    startDistanceMeasurement(gps.location, location)
                    selectLocation(null)
                },
            )
        ) {
            selectLocation(null)
        }
    }

    fun reloadMap() {
        inBackground {
            withContext(Dispatchers.IO) {
                map = mapRepo.getMap(mapId)
            }
            withContext(Dispatchers.Main) {
                map?.let {
                    onMapLoad(it)
                }
            }
        }
    }

    private fun updateBeacons() {
        val all = (beacons + listOfNotNull(destination)).distinctBy { it.id }
        beaconLayer.setBeacons(all)
    }

    private suspend fun updatePaths(reload: Boolean = false) = onDefault {
        if (reload) {
            val bounds = map?.boundary() ?: return@onDefault
            pathLoader.update(paths, bounds, bounds, true)
        }

        val mappablePaths =
            pathLoader.getPointsWithBacktrack(requireContext()).mapNotNull {
                val path =
                    paths.firstOrNull { p -> p.id == it.key } ?: return@mapNotNull null
                it.value.asMappable(requireContext(), path)
            }

        pathLayer.setPaths(mappablePaths)
    }

    private fun updateDestination() {
        if (throttle.isThrottled()) {
            return
        }

        val beacon = destination ?: return
        binding.navigationSheet.show(
            Position(gps.location, altimeter.altitude, compass.bearing, gps.speed.speed),
            beacon,
            compass.declination,
            true
        )
    }

    private fun selectLocation(location: Coordinate?) {
        selectedPointLayer.setBeacons(
            listOfNotNull(
                if (location == null) {
                    null
                } else {
                    Beacon(0, "", location)
                }
            )
        )
    }

    private fun createBeacon(location: Coordinate) {
        val bundle = bundleOf(
            "initial_location" to GeoUri(location)
        )
        findNavController().navigate(R.id.place_beacon, bundle)
    }

    private fun showDistance(distance: Distance) {
        lastDistanceToast?.cancel()
        val relative = distance
            .convertTo(prefs.baseDistanceUnits)
            .toRelativeDistance()
        binding.distanceSheet.setDistance(relative)
    }

    fun startDistanceMeasurement(vararg initialPoints: Coordinate) {
        if (map?.isCalibrated != true) {
            toast(getString(R.string.map_is_not_calibrated))
            return
        }

        distanceLayer.isEnabled = true
        distanceLayer.clear()
        initialPoints.forEach { distanceLayer.add(it) }
        binding.distanceSheet.show()
        binding.distanceSheet.cancelListener = {
            stopDistanceMeasurement()
        }
        binding.distanceSheet.createPathListener = {
            inBackground {
                map?.let {
                    val id = CreatePathCommand(
                        pathService,
                        prefs.navigation,
                        it
                    ).execute(distanceLayer.getPoints())

                    onMain {
                        findNavController().navigate(
                            R.id.action_maps_to_path,
                            bundleOf("path_id" to id)
                        )
                    }
                }
            }
        }
        binding.distanceSheet.undoListener = {
            distanceLayer.undo()
        }
    }

    private fun stopDistanceMeasurement() {
        distanceLayer.isEnabled = false
        distanceLayer.clear()
        binding.distanceSheet.hide()
    }

    private fun navigateTo(location: Coordinate) {
        inBackground {
            // Create a temporary beacon
            val beacon = Beacon(
                0L,
                map?.name ?: "",
                location,
                visible = false,
                temporary = true,
                color = AppColor.Orange.color,
                owner = BeaconOwner.Maps
            )
            val id = onIO {
                beaconService.add(beacon)
            }

            navigateTo(beacon.copy(id = id))
        }
    }

    private fun onDistancePathChange(points: List<Coordinate>) {
        // Display distance
        val distance = Geology.getPathDistance(points)
        showDistance(distance)
    }

    private fun navigateTo(beacon: Beacon): Boolean {
        cache.putLong(NavigatorFragment.LAST_BEACON_ID, beacon.id)
        destination = beacon
        val colorWithAlpha = beacon.color.withAlpha(127)
        navigationLayer.setColor(colorWithAlpha)
        navigationLayer.setEnd(beacon.coordinate)
        beaconLayer.highlight(beacon)
        binding.cancelNavigationBtn.show()
        updateBeacons()
        updateDestination()
        return true
    }

    private fun hideNavigation() {
        navigationLayer.setEnd(null)
        beaconLayer.highlight(null)
        binding.cancelNavigationBtn.hide()
        binding.navigationSheet.hide()
        destination = null
        updateBeacons()
    }

    private fun cancelNavigation() {
        cache.remove(NavigatorFragment.LAST_BEACON_ID)
        destination = null
        hideNavigation()
    }

    private fun onMapLoad(map: PhotoMap) {
        this.map = map
        binding.map.showMap(map)
    }

    override fun onPause() {
        super.onPause()
        tideTimer.stop()
        lastDistanceToast?.cancel()
    }

    fun recenter() {
        binding.map.recenter()
    }

    private fun updateTides() = inBackground {
        updateTideLayerCommand.execute()
    }

    private fun getNextLockMode(mode: MapLockMode): MapLockMode {
        return when (mode) {
            MapLockMode.Location -> {
                if (hasCompass) {
                    MapLockMode.Compass
                } else {
                    MapLockMode.Free
                }
            }
            MapLockMode.Compass -> {
                MapLockMode.Free
            }
            MapLockMode.Free -> {
                MapLockMode.Location
            }
        }
    }

    private enum class MapLockMode {
        Location,
        Compass,
        Free
    }

    companion object {
        fun create(mapId: Long): ViewMapFragment {
            return ViewMapFragment().apply {
                arguments = bundleOf("mapId" to mapId)
            }
        }
    }

}