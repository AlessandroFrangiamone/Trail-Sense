package com.kylecorry.trail_sense.tools.weather.infrastructure.commands

import com.kylecorry.sol.science.meteorology.PressureCharacteristic
import com.kylecorry.sol.science.meteorology.PressureTendency
import com.kylecorry.sol.science.meteorology.WeatherFront
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.shared.commands.generic.Command
import com.kylecorry.trail_sense.main.persistence.IReadingRepo
import com.kylecorry.trail_sense.tools.weather.domain.CurrentWeather
import com.kylecorry.trail_sense.tools.weather.domain.RawWeatherObservation
import com.kylecorry.trail_sense.tools.weather.domain.WeatherPrediction
import com.kylecorry.trail_sense.tools.weather.domain.forecasting.arrival.WeatherArrivalTime
import com.kylecorry.trail_sense.tools.weather.infrastructure.subsystem.IWeatherSubsystem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

internal class MonitorWeatherCommandTest {

    private lateinit var monitor: MonitorWeatherCommand
    private lateinit var repo: IReadingRepo<RawWeatherObservation>
    private lateinit var observer: IWeatherObserver
    private lateinit var subsystem: IWeatherSubsystem
    private lateinit var alerter: Command<CurrentWeather>

    @BeforeEach
    fun setup() {
        repo = mock()
        observer = mock()
        subsystem = mock()
        alerter = mock()
        monitor = MonitorWeatherCommand(repo, observer, subsystem, alerter)
    }

    @Test
    fun canRecordWeather() = runBlocking {
        val weather = CurrentWeather(
            WeatherPrediction(
                emptyList(),
                emptyList(),
                WeatherFront.Warm,
                WeatherArrivalTime(Instant.now(), false),
                null,
                emptyList()
            ),
            PressureTendency(PressureCharacteristic.Falling, -1f),
            null,
            null
        )
        val observation = Reading(RawWeatherObservation(0, 1000f, 0f, 0f), Instant.now())

        whenever(observer.getWeatherObservation()).thenReturn(observation)
        whenever(subsystem.getWeather()).thenReturn(weather)
        whenever(repo.add(observation)).thenReturn(1L)

        monitor.execute()

        verify(repo, times(1)).add(observation)
        verify(alerter, times(1)).execute(weather)
    }

}