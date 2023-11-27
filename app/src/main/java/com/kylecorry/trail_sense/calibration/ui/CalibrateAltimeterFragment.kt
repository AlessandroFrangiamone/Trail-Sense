package com.kylecorry.trail_sense.calibration.ui

import android.hardware.SensorManager
import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.sensors.IAltimeter
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.barometer.IBarometer
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.CustomGPS
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.altimeter.FusedAltimeter
import com.kylecorry.trail_sense.weather.domain.RawWeatherObservation
import com.kylecorry.trail_sense.weather.domain.sealevel.SeaLevelCalibrationFactory
import java.time.Instant


class CalibrateAltimeterFragment : AndromedaPreferenceFragment() {

    private lateinit var barometer: IBarometer
    private lateinit var gps: IGPS
    private lateinit var altimeter: IAltimeter
    private lateinit var prefs: UserPreferences
    private lateinit var sensorService: SensorService
    private val throttle = Throttle(20)
    private var altimeterStarted = false
    private lateinit var distanceUnits: DistanceUnits

    private lateinit var altitudeTxt: Preference
    private lateinit var calibrationModeList: ListPreference
    private lateinit var altitudeOverridePref: Preference
    private lateinit var altitudeOverrideGpsBtn: Preference
    private lateinit var altitudeOverrideBarometerEdit: EditTextPreference
    private lateinit var accuracyPref: Preference
    private lateinit var clearCachePref: Preference
    private lateinit var continuousCalibrationPref: SwitchPreferenceCompat

    private lateinit var lastMode: UserPreferences.AltimeterMode
    private val intervalometer = Timer { updateAltitude() }
    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    private var seaLevelPressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.altimeter_calibration, rootKey)

        setIconColor(Resources.androidTextColorSecondary(requireContext()))

        prefs = UserPreferences(requireContext())
        sensorService = SensorService(requireContext())

        gps = CustomGPS(requireContext().applicationContext)
        barometer = sensorService.getBarometer()
        altimeter = sensorService.getAltimeter()

        distanceUnits = prefs.baseDistanceUnits

        bindPreferences()
    }

    private fun bindPreferences() {
        altitudeTxt = findPreference(getString(R.string.pref_holder_altitude))!!
        calibrationModeList = findPreference(getString(R.string.pref_altimeter_calibration_mode))!!
        altitudeOverridePref = findPreference(getString(R.string.pref_altitude_override))!!
        altitudeOverrideGpsBtn = findPreference(getString(R.string.pref_altitude_from_gps_btn))!!
        altitudeOverrideBarometerEdit =
            findPreference(getString(R.string.pref_altitude_override_sea_level))!!
        accuracyPref = preference(R.string.pref_altimeter_accuracy_holder)!!
        clearCachePref = preference(R.string.pref_altimeter_clear_cache_holder)!!
        continuousCalibrationPref = switch(R.string.pref_altimeter_continuous_calibration)!!

        val altitudeOverride = Distance.meters(prefs.altitudeOverride).convertTo(distanceUnits)
        altitudeOverridePref.summary = formatService.formatDistance(altitudeOverride)

        updateConditionalPreferences()

        altitudeOverrideBarometerEdit.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL)
                .or(InputType.TYPE_NUMBER_FLAG_SIGNED)
        }

        altitudeOverrideGpsBtn.setOnPreferenceClickListener {
            updateElevationFromGPS()
            true
        }

        altitudeOverrideBarometerEdit.setOnPreferenceChangeListener { _, newValue ->
            updateElevationFromBarometer(newValue.toString().toFloatOrNull() ?: 0.0f)
            true
        }

        altitudeOverridePref.setOnPreferenceClickListener {
            CustomUiUtils.pickElevation(
                requireContext(),
                Distance.meters(prefs.altitudeOverride).convertTo(distanceUnits),
                it.title.toString()
            ) { elevation, _ ->
                if (elevation != null) {
                    prefs.altitudeOverride = elevation.meters().distance
                    updateAltitude()
                }
            }
            true
        }

        val samples = (1..8).toList()
        accuracyPref.summary = prefs.altimeterSamples.toString()
        onClick(accuracyPref) {
            val idx = samples.indexOf(prefs.altimeterSamples)
            Pickers.item(
                requireContext(),
                getString(R.string.samples),
                samples.map { it.toString() },
                idx
            ) {
                if (it != null) {
                    prefs.altimeterSamples = samples[it]
                    accuracyPref.summary = samples[it].toString()
                    restartAltimeter()
                }
            }
        }

        onClick(clearCachePref) {
            FusedAltimeter.clearCachedCalibration(requireContext())
            toast(getString(R.string.done))
        }

        onClick(continuousCalibrationPref){
            restartAltimeter()
        }

        if (prefs.altimeterMode == UserPreferences.AltimeterMode.Barometer) {
            updateElevationFromBarometer(prefs.seaLevelPressureOverride)
        }

        lastMode = prefs.altimeterMode
    }

    private fun updateConditionalPreferences() {
        val hasBarometer = prefs.weather.hasBarometer
        val mode = prefs.altimeterMode

        // Cache and continuous calibration are only available on the fused barometer
        clearCachePref.isVisible =
            hasBarometer && mode == UserPreferences.AltimeterMode.GPSBarometer
        continuousCalibrationPref.isVisible =
            hasBarometer && mode == UserPreferences.AltimeterMode.GPSBarometer

        // Overrides are available on the barometer or the manual mode
        val canProvideOverrides =
            mode == UserPreferences.AltimeterMode.Barometer || mode == UserPreferences.AltimeterMode.Override
        altitudeOverridePref.isVisible = canProvideOverrides
        altitudeOverrideGpsBtn.isVisible = canProvideOverrides
        altitudeOverrideBarometerEdit.isVisible = canProvideOverrides && hasBarometer

        // Sample size is only available when the GPS is being used
        accuracyPref.isVisible =
            mode == UserPreferences.AltimeterMode.GPS || mode == UserPreferences.AltimeterMode.GPSBarometer

        // Restrict the calibration mode list if there is no barometer
        if (!hasBarometer) {
            calibrationModeList.setEntries(R.array.altimeter_mode_no_barometer_entries)
            calibrationModeList.setEntryValues(R.array.altimeter_mode_no_barometer_values)
        }
    }

    private fun restartAltimeter() {
        stopAltimeter()

        // Reset the cache of the fused altimeter
        FusedAltimeter.clearCachedCalibration(requireContext())

        altimeter = sensorService.getAltimeter()
        startAltimeter()
        updateAltitude()
    }

    override fun onResume() {
        super.onResume()
        startAltimeter()
        intervalometer.interval(20)
    }

    override fun onPause() {
        super.onPause()
        barometer.stop(this::onElevationFromBarometerCallback)
        barometer.stop(this::onSeaLevelPressureOverrideCallback)
        gps.stop(this::onElevationFromGPSCallback)
        stopAltimeter()
        intervalometer.stop()
    }

    private fun updateElevationFromGPS() {
        gps.start(this::onElevationFromGPSCallback)
    }

    private fun onElevationFromGPSCallback(): Boolean {
        val elevation = gps.altitude
        prefs.altitudeOverride = elevation
        updateSeaLevelPressureOverride()
        updateAltitude()
        Alerts.toast(requireContext(), getString(R.string.elevation_override_updated_toast))
        return false
    }

    private fun startAltimeter() {
        if (altimeterStarted) {
            return
        }
        altimeterStarted = true
        altimeter.start(this::updateAltitude)
    }

    private fun stopAltimeter() {
        altimeterStarted = false
        altimeter.stop(this::updateAltitude)
    }

    private fun updateSeaLevelPressureOverride() {
        barometer.start(this::onSeaLevelPressureOverrideCallback)
    }

    private fun onSeaLevelPressureOverrideCallback(): Boolean {
        val altitude = prefs.altitudeOverride
        val calibrator = SeaLevelCalibrationFactory().create(prefs)
        val readings = listOf(
            Reading(
                RawWeatherObservation(
                    0,
                    barometer.pressure,
                    altitude,
                    16f,
                    if (altimeter is IGPS) (altimeter as IGPS).verticalAccuracy else null
                ),
                Instant.now(),
            )
        )
        val seaLevel = calibrator.calibrate(readings).first()
        prefs.seaLevelPressureOverride = seaLevel.value.hpa().pressure
        restartAltimeter()
        return false
    }

    private fun updateElevationFromBarometer(seaLevelPressure: Float) {
        this.seaLevelPressure = seaLevelPressure
        barometer.start(this::onElevationFromBarometerCallback)
    }

    private fun onElevationFromBarometerCallback(): Boolean {
        val elevation = SensorManager.getAltitude(seaLevelPressure, barometer.pressure)
        prefs.altitudeOverride = elevation
        updateSeaLevelPressureOverride()
        updateAltitude()
        Alerts.toast(requireContext(), getString(R.string.elevation_override_updated_toast))
        return false
    }

    private fun updateAltitude(): Boolean {

        if (throttle.isThrottled()) {
            return true
        }

        val altitude = Distance.meters(altimeter.altitude).convertTo(distanceUnits)
        altitudeTxt.summary = formatService.formatDistance(altitude)

        if (lastMode != prefs.altimeterMode) {
            lastMode = prefs.altimeterMode
            restartAltimeter()
            updateConditionalPreferences()
            if (prefs.altimeterMode == UserPreferences.AltimeterMode.Barometer) {
                updateSeaLevelPressureOverride()
            }
        }

        val altitudeOverride = Distance.meters(prefs.altitudeOverride).convertTo(distanceUnits)
        altitudeOverridePref.summary = formatService.formatDistance(altitudeOverride)

        return true
    }


}