package com.kylecorry.trail_sense.tools.weather.domain.forecasting.arrival

import com.kylecorry.sol.science.meteorology.WeatherForecast
import com.kylecorry.sol.science.meteorology.clouds.CloudGenus
import com.kylecorry.sol.units.Reading

internal interface IWeatherArrivalTimeCalculator {
    fun getArrivalTime(
        forecast: List<WeatherForecast>,
        clouds: List<Reading<CloudGenus?>>
    ): WeatherArrivalTime?
}