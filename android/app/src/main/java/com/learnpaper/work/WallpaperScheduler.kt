package com.learnpaper.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.learnpaper.data.Settings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WallpaperScheduler {
    private const val NAME = "wallpaper-rotation"
    private const val NOTIFY_NAME = "daily-notification"
    const val MIN_INTERVAL_MINUTES = 15L

    /** (Re)schedules the periodic change. The first run happens one interval from now. */
    fun schedule(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    /** Daily notification at [Settings.notifyMinute]; cancelled when the setting is off. */
    fun scheduleNotification(context: Context, settings: Settings, now: LocalDateTime = LocalDateTime.now()) {
        val wm = WorkManager.getInstance(context)
        if (!settings.notifyDaily) {
            wm.cancelUniqueWork(NOTIFY_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(settings.notifyMinute, now).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(NOTIFY_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Time until the next occurrence of [minuteOfDay], at least one minute away. */
    fun delayUntil(minuteOfDay: Int, now: LocalDateTime): Duration {
        val time = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60)
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
