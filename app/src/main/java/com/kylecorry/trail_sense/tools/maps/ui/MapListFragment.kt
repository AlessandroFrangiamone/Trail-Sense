package com.kylecorry.trail_sense.tools.maps.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.tryOrNothing
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.pickers.CoroutinePickers
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.preferences.Preferences
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentMapListBinding
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.alerts.AlertLoadingIndicator
import com.kylecorry.trail_sense.shared.extensions.*
import com.kylecorry.trail_sense.shared.io.FileSubsystem
import com.kylecorry.trail_sense.shared.io.FragmentUriPicker
import com.kylecorry.trail_sense.shared.lists.GroupListManager
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.guide.infrastructure.UserGuideUtils
import com.kylecorry.trail_sense.tools.maps.domain.IMap
import com.kylecorry.trail_sense.tools.maps.domain.Map
import com.kylecorry.trail_sense.tools.maps.domain.MapGroup
import com.kylecorry.trail_sense.tools.maps.domain.sort.ClosestMapSortStrategy
import com.kylecorry.trail_sense.tools.maps.domain.sort.MapSortMethod
import com.kylecorry.trail_sense.tools.maps.domain.sort.MostRecentMapSortStrategy
import com.kylecorry.trail_sense.tools.maps.domain.sort.NameMapSortStrategy
import com.kylecorry.trail_sense.tools.maps.infrastructure.MapGroupLoader
import com.kylecorry.trail_sense.tools.maps.infrastructure.MapRepo
import com.kylecorry.trail_sense.tools.maps.infrastructure.MapService
import com.kylecorry.trail_sense.tools.maps.infrastructure.create.CreateMapFromCameraCommand
import com.kylecorry.trail_sense.tools.maps.infrastructure.create.CreateMapFromFileCommand
import com.kylecorry.trail_sense.tools.maps.infrastructure.create.CreateMapFromUriCommand
import com.kylecorry.trail_sense.tools.maps.infrastructure.create.ICreateMapCommand
import com.kylecorry.trail_sense.tools.maps.infrastructure.reduce.HighQualityMapReducer
import com.kylecorry.trail_sense.tools.maps.infrastructure.reduce.LowQualityMapReducer
import com.kylecorry.trail_sense.tools.maps.infrastructure.reduce.MediumQualityMapReducer
import com.kylecorry.trail_sense.tools.maps.ui.commands.MoveMapCommand
import com.kylecorry.trail_sense.tools.maps.ui.commands.RenameMapCommand
import com.kylecorry.trail_sense.tools.maps.ui.mappers.IMapMapper
import com.kylecorry.trail_sense.tools.maps.ui.mappers.MapAction
import com.kylecorry.trail_sense.tools.maps.ui.mappers.MapGroupAction

class MapListFragment : BoundFragment<FragmentMapListBinding>() {

    private val sensorService by lazy { SensorService(requireContext()) }
    private val gps by lazy { sensorService.getGPS() }
    private val mapRepo by lazy { MapRepo.getInstance(requireContext()) }
    private val cache by lazy { Preferences(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val mapService by lazy { MapService(mapRepo) }
    private val mapLoader by lazy { MapGroupLoader(mapService.loader) }
    private lateinit var manager: GroupListManager<IMap>
    private lateinit var mapper: IMapMapper

    // TODO: Load from prefs
    private var sort = MapSortMethod.Closest


    private var lastRoot: IMap? = null

    private val uriPicker = FragmentUriPicker(this)
    private val mapImportingIndicator by lazy {
        AlertLoadingIndicator(
            requireContext(),
            getString(R.string.importing_map)
        )
    }
    private val exportService by lazy { FragmentMapExportService(this) }
    private val files by lazy { FileSubsystem.getInstance(requireContext()) }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMapListBinding {
        return FragmentMapListBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        manager = GroupListManager(
            lifecycleScope,
            mapLoader,
            lastRoot,
            this::sortMaps
        )

        mapper = IMapMapper(
            gps,
            requireContext(),
            this,
            this::onMapAction,
            this::onMapGroupAction
        )

        val mapIntentUri: Uri? = arguments?.getParcelable("map_intent_uri")
        arguments?.remove("map_intent_uri")
        if (mapIntentUri != null) {
            createMap(
                CreateMapFromUriCommand(
                    requireContext(),
                    mapRepo,
                    mapIntentUri,
                    mapImportingIndicator
                )
            )
        }

        // TODO: Convert into disclaimer
        if (cache.getBoolean("tool_maps_experimental_disclaimer_shown") != true) {
            Alerts.dialog(
                requireContext(),
                getString(R.string.experimental),
                "Photo Maps is an experimental feature, please only use this to test it out at this point. Feel free to share your feedback on this feature and note that there is still a lot to be done before this will be non-experimental.",
                okText = getString(R.string.tool_user_guide_title),
                cancelText = getString(android.R.string.ok)
            ) { cancelled ->
                cache.putBoolean("tool_maps_experimental_disclaimer_shown", true)
                if (!cancelled) {
                    UserGuideUtils.openGuide(this, R.raw.importing_maps)
                }
            }
        }

        binding.mapList.emptyView = binding.mapEmptyText

        binding.mapListTitle.leftButton.setOnClickListener {
            UserGuideUtils.showGuide(this, R.raw.importing_maps)
        }

        sort = prefs.navigation.mapSort
        binding.mapListTitle.rightButton.setOnClickListener {
            Pickers.menu(
                it, listOf(
                    getString(R.string.sort_by, getSortString(sort))
                )
            ) { selected ->
                when (selected) {
                    0 -> changeSort()
                }
                true
            }
        }

        binding.searchbox.setOnQueryTextListener { _, _ ->
            manager.search(binding.searchbox.query)
            true
        }

        manager.onChange = { root, items, rootChanged ->
            if (isBound) {
                binding.mapList.setItems(items, mapper)
                if (rootChanged) {
                    binding.mapList.scrollToPosition(0, false)
                }
                binding.mapListTitle.title.text =
                    (root as MapGroup?)?.name ?: getString(R.string.photo_maps)
            }
        }

        onBackPressed {
            if (!manager.up()) {
                remove()
                findNavController().navigateUp()
            }
        }

        setupMapCreateMenu()
    }

    private fun changeSort() {
        val sortOptions = MapSortMethod.values()
        Pickers.item(
            requireContext(),
            getString(R.string.sort),
            sortOptions.map { getSortString(it) },
            sortOptions.indexOf(prefs.navigation.mapSort)
        ) { newSort ->
            if (newSort != null) {
                prefs.navigation.mapSort = sortOptions[newSort]
                sort = sortOptions[newSort]
                onSortChanged()
            }
        }
    }

    private fun getSortString(sortMethod: MapSortMethod): String {
        return when (sortMethod) {
            MapSortMethod.MostRecent -> getString(R.string.most_recent)
            MapSortMethod.Closest -> getString(R.string.closest)
            MapSortMethod.Name -> getString(R.string.name)
        }
    }

    private fun onSortChanged() {
        manager.refresh(true)
    }

    private suspend fun sortMaps(maps: List<IMap>): List<IMap> = onDefault {
        val strategy = when (sort) {
            MapSortMethod.Closest -> ClosestMapSortStrategy(gps.location, mapService.loader)
            MapSortMethod.MostRecent -> MostRecentMapSortStrategy(mapService.loader)
            MapSortMethod.Name -> NameMapSortStrategy()
        }

        strategy.sort(maps)
    }

    private fun onMapGroupAction(group: MapGroup, action: MapGroupAction) {
        when (action) {
            MapGroupAction.View -> view(group)
            MapGroupAction.Delete -> delete(group)
            MapGroupAction.Rename -> rename(group)
            MapGroupAction.Move -> move(group)
        }
    }

    private fun onMapAction(map: Map, action: MapAction) {
        when (action) {
            MapAction.View -> view(map)
            MapAction.Delete -> delete(map)
            MapAction.Export -> export(map)
            MapAction.Resize -> resize(map)
            MapAction.Rename -> rename(map)
            MapAction.Move -> move(map)
        }
    }

    // TODO: Extract these to commands

    private fun resize(map: Map) {
        Pickers.item(
            requireContext(),
            getString(R.string.change_resolution),
            listOf(
                getString(R.string.low),
                getString(R.string.moderate),
                getString(R.string.high)
            )
        ) {
            if (it != null) {
                inBackground {
                    mapImportingIndicator.show()
                    onIO {
                        val reducer = when (it) {
                            0 -> LowQualityMapReducer(requireContext())
                            1 -> MediumQualityMapReducer(requireContext())
                            else -> HighQualityMapReducer(requireContext())
                        }
                        reducer.reduce(map)
                    }
                    onMain {
                        if (isBound) {
                            mapImportingIndicator.hide()
                        }
                        manager.refresh()
                    }
                }
            }
        }
    }

    private fun export(map: Map) {
        exportService.export(map)
    }

    private fun rename(map: IMap) {
        inBackground {
            RenameMapCommand(requireContext(), mapService).execute(map)
            manager.refresh()
        }
    }

    private fun move(map: IMap) {
        inBackground {
            MoveMapCommand(requireContext(), mapService).execute(map)
            manager.refresh()
        }
    }

    private fun delete(map: IMap) {
        Alerts.dialog(
            requireContext(),
            getString(R.string.delete_map),
            map.name
        ) { cancelled ->
            if (!cancelled) {
                inBackground {
                    mapService.delete(map)
                    manager.refresh()
                }
            }
        }
    }

    private fun view(map: IMap) {
        if (map is MapGroup) {
            manager.open(map.id)
        } else {
            findNavController().navigate(
                R.id.action_mapList_to_maps,
                bundleOf("mapId" to map.id)
            )
        }
    }

    private fun setupMapCreateMenu() {
        binding.addMenu.setOverlay(binding.overlayMask)
        binding.addMenu.fab = binding.addBtn
        binding.addMenu.hideOnMenuOptionSelected = true
        binding.addMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import_map_file -> {
                    createMap(
                        CreateMapFromFileCommand(
                            requireContext(),
                            uriPicker,
                            mapRepo,
                            mapImportingIndicator
                        )
                    )
                }
                R.id.action_import_map_camera -> {
                    createMap(
                        CreateMapFromCameraCommand(
                            this,
                            mapRepo,
                            mapImportingIndicator
                        )
                    )
                }
                R.id.action_create_map_group -> {
                    createMapGroup()
                }
            }
            true
        }
    }

    private fun createMapGroup() {
        inBackground {
            val name = CoroutinePickers.text(requireContext(), getString(R.string.name))
                ?: return@inBackground
            mapService.add(MapGroup(0, name, manager.root?.id))
            manager.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        manager.refresh()
    }

    override fun onPause() {
        super.onPause()
        tryOrNothing {
            lastRoot = manager.root
        }
    }

    private fun createMap(command: ICreateMapCommand) {
        inBackground {
            val name = CoroutinePickers.text(requireContext(), getString(R.string.name))
                ?: return@inBackground

            binding.addBtn.isEnabled = false

            val map = command.execute()?.copy(name = name, parentId = manager.root?.id)

            if (map == null) {
                toast(getString(R.string.error_importing_map))
                binding.addBtn.isEnabled = true
                return@inBackground
            }

            if (name.isNotBlank() || map.parentId != null) {
                onIO {
                    mapRepo.addMap(map)
                }
            }

            if (prefs.navigation.autoReduceMaps) {
                mapImportingIndicator.show()
                val reducer = HighQualityMapReducer(requireContext())
                reducer.reduce(map)
                mapImportingIndicator.hide()
            }

            if (map.calibration.calibrationPoints.isNotEmpty()) {
                toast(getString(R.string.map_auto_calibrated))
            }

            binding.addBtn.isEnabled = true
            manager.refresh(true)
            findNavController().navigate(
                R.id.action_mapList_to_maps,
                bundleOf("mapId" to map.id)
            )

        }
    }


}
