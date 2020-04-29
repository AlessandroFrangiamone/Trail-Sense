package com.kylecorry.trail_sense.astronomy.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.domain.moon.*
import com.kylecorry.trail_sense.astronomy.domain.sun.ISunTimesCalculator
import com.kylecorry.trail_sense.astronomy.domain.sun.SunTimes
import com.kylecorry.trail_sense.astronomy.domain.sun.SunTimesCalculatorFactory
import com.kylecorry.trail_sense.shared.Coordinate
import com.kylecorry.trail_sense.shared.formatHM
import com.kylecorry.trail_sense.shared.math.getPercentOfDuration
import com.kylecorry.trail_sense.shared.sensors.GPS
import com.kylecorry.trail_sense.shared.sensors.IGPS
import com.kylecorry.trail_sense.shared.toDisplayFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt

class AstronomyFragment : Fragment() {

    private lateinit var gps: IGPS
    private lateinit var location: Coordinate

    private lateinit var sunTxt: TextView
    private lateinit var remDaylightTxt: TextView
    private lateinit var moonTxt: TextView
    private lateinit var sunStartTimeTxt: TextView
    private lateinit var sunEndTimeTxt: TextView
    private lateinit var sunStartTomorrowTimeTxt: TextView
    private lateinit var sunEndTomorrowTimeTxt: TextView
    private lateinit var moonRiseTimeTxt: TextView
    private lateinit var moonSetTimeTxt: TextView
    private lateinit var moonRiseTomorrowTimeTxt: TextView
    private lateinit var moonSetTomorrowTimeTxt: TextView
    private lateinit var timer: Timer
    private lateinit var handler: Handler
    private lateinit var moonPosition: ImageView
    private lateinit var sunPosition: ImageView
    private lateinit var dayCircle: ImageView
    private lateinit var moonIconClock: IconClock
    private lateinit var sunIconClock: IconClock

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_astronomy, container, false)

        sunTxt = view.findViewById(R.id.remaining_time)
        moonTxt = view.findViewById(R.id.moon_phase)
        remDaylightTxt = view.findViewById(R.id.remaining_time_lbl)
        sunStartTimeTxt = view.findViewById(R.id.sun_start_time)
        sunEndTimeTxt = view.findViewById(R.id.sun_end_time)
        sunStartTomorrowTimeTxt = view.findViewById(R.id.sun_start_time_tomorrow)
        sunEndTomorrowTimeTxt = view.findViewById(R.id.sun_end_time_tomorrow)

        moonRiseTimeTxt = view.findViewById(R.id.moon_rise_time)
        moonSetTimeTxt = view.findViewById(R.id.moon_set_time)
        moonRiseTomorrowTimeTxt = view.findViewById(R.id.moon_rise_time_tomorrow)
        moonSetTomorrowTimeTxt = view.findViewById(R.id.moon_set_time_tomorrow)

        sunPosition = view.findViewById(R.id.sun_position)
        moonPosition = view.findViewById(R.id.moon_position)
        dayCircle = view.findViewById(R.id.day_circle)
        moonIconClock = IconClock(dayCircle, moonPosition)
        sunIconClock = IconClock(dayCircle, sunPosition)

        gps = GPS(context!!)

        return view
    }

    override fun onResume() {
        super.onResume()
        location = gps.location
        gps.start(this::onLocationUpdate)
        handler = Handler(Looper.getMainLooper())
        timer = fixedRateTimer(period = 1000 * 60) {
            handler.post { updateUI() }
        }
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        gps.stop(this::onLocationUpdate)
        timer.cancel()
    }

    private fun onLocationUpdate(): Boolean {
        location = gps.location
        updateUI()
        return false
    }

    private fun updateUI() {
        updateSunUI()
        updateMoonUI()
    }

    private fun updateMoonUI() {
        val time = ZonedDateTime.now()

        val moonPhase = MoonPhaseCalculator().getPhase(time)
        val calculator = AltitudeMoonTimesCalculator()

        val today = calculator.calculate(gps.location, LocalDate.now())
        val tomorrow = calculator.calculate(gps.location, LocalDate.now().plusDays(1))

        moonRiseTimeTxt.text = today.up?.toDisplayFormat(context!!) ?: "-"
        moonSetTimeTxt.text = today.down?.toDisplayFormat(context!!) ?: "-"
        moonRiseTomorrowTimeTxt.text = tomorrow.up?.toDisplayFormat(context!!) ?: "-"
        moonSetTomorrowTimeTxt.text = tomorrow.down?.toDisplayFormat(context!!) ?: "-"

        moonPosition.setImageResource(getMoonImage(moonPhase.phase))

        moonTxt.text =
            "${moonPhase.phase.longName} (${moonPhase.illumination.roundToInt()}% illumination)"

        updateMoonPosition(LocalDateTime.now(), calculator)
    }

    private fun updateSunUI() {
        val sunChartCalculator = SunTimesCalculatorFactory().create(context!!)

        val currentTime = LocalDateTime.now()
        val today = currentTime.toLocalDate()
        val tomorrow = today.plusDays(1)

        val todayTimes = sunChartCalculator.calculate(location, today)
        val tomorrowTimes = sunChartCalculator.calculate(location, tomorrow)

        displaySunTimes(todayTimes, sunStartTimeTxt, sunEndTimeTxt)
        displaySunTimes(tomorrowTimes, sunStartTomorrowTimeTxt, sunEndTomorrowTimeTxt)

        displayTimeUntilNextSunEvent(currentTime, todayTimes, tomorrowTimes)

        updateSunPosition(currentTime, sunChartCalculator)
    }

    private fun updateSunPosition(currentTime: LocalDateTime, calculator: ISunTimesCalculator){
        val today = calculator.calculate(gps.location, LocalDate.now())
        val tomorrow = calculator.calculate(gps.location, LocalDate.now().plusDays(1))
        val yesterday = calculator.calculate(gps.location, LocalDate.now().minusDays(1))

        val percent = when {
            currentTime.isAfter(today.down) -> {
                getPercentOfDuration(today.down, tomorrow.up, currentTime)
            }
            currentTime.isAfter(today.up) -> {
                getPercentOfDuration(today.up, today.down, currentTime)
            }
            else -> {
                getPercentOfDuration(yesterday.down, today.up, currentTime)
            }
        }

        val angle = if (currentTime.isAfter(today.up) && currentTime.isBefore(today.down)){
            // Day time
            180 * percent
        } else {
            // Night time
            180 + 180 * percent
        }

        sunIconClock.display(angle, 0.98f)
    }

    private fun updateMoonPosition(currentTime: LocalDateTime, calculator: IMoonTimesCalculator){
        val today = calculator.calculate(gps.location, LocalDate.now())
        val tomorrow = calculator.calculate(gps.location, LocalDate.now().plusDays(1))
        val yesterday = calculator.calculate(gps.location, LocalDate.now().minusDays(1))

        val isUp = MoonStateCalculator().isUp(today, currentTime.toLocalTime())

        val percent = if (isUp){
            val lastUp = today.up ?: yesterday.up
            val nextDown = if (today.down != null && today.down.isAfter(lastUp)) today.down else tomorrow.down

            if (lastUp != null && nextDown != null){
                getPercentOfDuration(lastUp, nextDown, currentTime)
            } else {
                0f
            }
        } else {
            val lastDown = today.down ?: yesterday.down
            val nextUp = if (today.up != null && today.up.isAfter(lastDown)) today.up else tomorrow.up

            if (lastDown != null && nextUp != null){
                getPercentOfDuration(lastDown, nextUp, currentTime)
            } else {
                0f
            }
        }

        val angle = if (isUp){
            // Day time
            180 * percent
        } else {
            // Night time
            180 + 180 * percent
        }

        moonIconClock.display(angle, 0.5f)
    }

    private fun getMoonImage(phase: MoonTruePhase): Int {
        return when (phase) {
            MoonTruePhase.FirstQuarter -> R.drawable.moon_first_quarter
            MoonTruePhase.Full -> R.drawable.moon_full
            MoonTruePhase.ThirdQuarter -> R.drawable.moon_last_quarter
            MoonTruePhase.New -> R.drawable.moon_new
            MoonTruePhase.WaningCrescent -> R.drawable.moon_waning_crescent
            MoonTruePhase.WaningGibbous -> R.drawable.moon_waning_gibbous
            MoonTruePhase.WaxingCrescent -> R.drawable.moon_waxing_crescent
            MoonTruePhase.WaxingGibbous -> R.drawable.moon_waxing_gibbous
        }
    }

    private fun displayTimeUntilNextSunEvent(
        currentTime: LocalDateTime,
        today: SunTimes,
        tomorrow: SunTimes
    ) {
        when {
            currentTime > today.down -> {
                // Time until tomorrow's sunrise
                sunTxt.text = Duration.between(currentTime, tomorrow.up).formatHM()
                remDaylightTxt.text = getString(R.string.until_sunrise_label)
            }
            currentTime < today.up -> {
                // Time until today's sunrise
                sunTxt.text = Duration.between(currentTime, today.up).formatHM()
                remDaylightTxt.text = getString(R.string.until_sunrise_label)
            }
            else -> {
                sunTxt.text = Duration.between(currentTime, today.down).formatHM()
                remDaylightTxt.text = getString(R.string.until_sunset_label)
            }
        }
    }


    private fun displaySunTimes(sunTimes: SunTimes, upTxt: TextView, downTxt: TextView) {
        upTxt.text = sunTimes.up.toDisplayFormat(context!!)
        downTxt.text = sunTimes.down.toDisplayFormat(context!!)
    }

}
