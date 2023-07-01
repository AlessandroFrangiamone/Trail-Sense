package com.kylecorry.trail_sense.astronomy.infrastructure

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kylecorry.andromeda.notify.Notify
import com.kylecorry.andromeda.background.services.CoroutineService
import com.kylecorry.sol.science.astronomy.SunTimesMode
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.astronomy.infrastructure.receivers.SunsetAlarmReceiver
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.NavigationUtils
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.onIO
import com.kylecorry.trail_sense.shared.extensions.onMain
import com.kylecorry.trail_sense.shared.sensors.SensorService
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class SunsetAlarmService : CoroutineService() {

    private val gps by lazy { SensorService(this).getGPS(true) }
    private val userPrefs by lazy { UserPreferences(this) }
    private val astronomyService = AstronomyService()

    override suspend fun doWork() {
        acquireWakelock(TAG, Duration.ofSeconds(30))
        Log.i(TAG, "Started")

        try {
            onIO {
                withTimeoutOrNull(Duration.ofSeconds(10).toMillis()) {
                    if (!gps.hasValidReading) {
                        gps.read()
                    }
                }
            }
        } finally {
            gps.stop(null)
        }

        val now = LocalDateTime.now()

        if (gps.location == Coordinate.zero) {
            setAlarm(now.plusDays(1))
            stopSelf()
            return
        }

        val alertMinutes = userPrefs.astronomy.sunsetAlertMinutesBefore
        val nextAlertMinutes = alertMinutes - 1


        val todaySunset =
            astronomyService.getTodaySunTimes(
                gps.location,
                SunTimesMode.Actual
            ).set?.toLocalDateTime()
        val tomorrowSunset =
            astronomyService.getTomorrowSunTimes(
                gps.location,
                SunTimesMode.Actual
            ).set?.toLocalDateTime()

        onMain {
            if (todaySunset != null) {
                when {
                    withinAlertWindow(todaySunset, alertMinutes) -> {
                        // Send alert, schedule alarm for tomorrow's sunset or else at some point tomorrow
                        sendNotification(todaySunset)
                        setAlarm(
                            tomorrowSunset?.minusMinutes(nextAlertMinutes)
                                ?: todaySunset.plusDays(1)
                        )
                    }
                    isPastSunset(todaySunset) -> {
                        // Missed the sunset, schedule the alarm for tomorrow or else at some point tomorrow
                        setAlarm(
                            tomorrowSunset?.minusMinutes(nextAlertMinutes)
                                ?: todaySunset.plusDays(1)
                        )
                    }
                    else -> { // Before the alert window
                        // Schedule alarm for sunset
                        setAlarm(todaySunset.minusMinutes(nextAlertMinutes))
                    }
                }
            } else {
                // There isn't a sunset today, schedule it for tomorrow's sunset or else the same time tomorrow
                setAlarm(tomorrowSunset?.minusMinutes(nextAlertMinutes) ?: now.plusDays(1))
            }

            stopSelf()
        }

    }

    override fun onDestroy() {
        stopSelf()
        super.onDestroy()
    }


    private fun isPastSunset(sunset: LocalDateTime): Boolean {
        return LocalDateTime.now().isAfter(sunset)
    }

    private fun withinAlertWindow(sunset: LocalDateTime, alertMinutes: Long): Boolean {
        if (isPastSunset(sunset)) {
            return false
        }
        val timeUntilSunset = Duration.between(LocalDateTime.now(), sunset)
        return timeUntilSunset <= Duration.ofMinutes(alertMinutes)
    }

    private fun sendNotification(sunset: LocalDateTime) {

        val lastSentDate = userPrefs.astronomy.sunsetAlertLastSent
        if (LocalDate.now() == lastSentDate) {
            return
        }

        userPrefs.astronomy.setSunsetAlertLastSentDate(LocalDate.now())

        val formatService = FormatService.getInstance(this)
        val formattedTime = formatService.formatTime(sunset.toLocalTime(), false)

        val openIntent = NavigationUtils.pendingIntent(this, R.id.action_astronomy)

        val notification = Notify.alert(
            this,
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.sunset_alert_notification_title),
            getString(
                R.string.sunset_alert_notification_text,
                formattedTime
            ),
            R.drawable.ic_sunset_notification,
            intent = openIntent,
            autoCancel = true
        )

        Notify.send(this, NOTIFICATION_ID, notification)
    }

    private fun setAlarm(time: LocalDateTime) {
        val scheduler = SunsetAlarmReceiver.scheduler(this)
        scheduler.cancel()
        scheduler.once(time.toZonedDateTime().toInstant())
        Log.i(TAG, "Scheduled next run at $time")
    }

    companion object {

        const val TAG = "SunsetAlarmService"
        const val NOTIFICATION_ID = 1231
        const val NOTIFICATION_CHANNEL_ID = "Sunset alert"


        fun intent(context: Context): Intent {
            return Intent(context, SunsetAlarmService::class.java)
        }
    }

}