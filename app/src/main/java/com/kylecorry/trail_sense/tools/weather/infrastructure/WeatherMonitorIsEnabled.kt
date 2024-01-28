package com.kylecorry.trail_sense.tools.weather.infrastructure

import android.content.Context
import com.kylecorry.andromeda.core.specifications.Specification
import com.kylecorry.trail_sense.shared.UserPreferences

class WeatherMonitorIsEnabled: Specification<Context>() {
    override fun isSatisfiedBy(value: Context): Boolean {
        val prefs = UserPreferences(value)
        return prefs.weather.shouldMonitorWeather && WeatherMonitorIsAvailable().isSatisfiedBy(value)
    }
}