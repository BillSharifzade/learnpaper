package com.learnpaper.work

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.learnpaper.data.Settings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Background work, kept to the minimum. The wallpaper needs none: it changes the word itself while it is on
 * screen. What remains is the daily notification and, only while a home-screen widget exists, a periodic
 * check that keeps the widget moving when the wallpaper is not visible or not LearnPaper.
 */
object WallpaperScheduler {
    /** 1.0 changed the wallpaper from this periodic job; removed once on the first start of 1.1. */
    private const val LEGACY_ROTATION = "wallpaper-rotation"
    private const val WIDGET_REFRESH = "widget-refresh"
    private const val NOTIFY_NAME = "daily-notification"

    /** WorkManager's floor for periodic work. */
    const val MIN_PERIOD_MINUTES = 15L

    /** Schedules the widget refresh at the word interval (at least 15 minutes) while widgets exist; cancels it otherwise. */
    fun syncWidgetRefresh(context: Context, intervalMinutes: Int, hasWidgets: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!hasWidgets) {
            wm.cancelUniqueWork(WIDGET_REFRESH)
            return
        }
        val period = intervalMinutes.toLong().coerceAtLeast(MIN_PERIOD_MINUTES)
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(period, TimeUnit.MINUTES)
            .setInitialDelay(period, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WIDGET_REFRESH, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Cancels 1.0's rotation job once; returns true when this start was the migration. */
    fun migrateOnce(context: Context): Boolean {
        val prefs = context.getSharedPreferences("work", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_rotation_cancelled", false)) return false
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_ROTATION)
        prefs.edit { putBoolean("legacy_rotation_cancelled", true) }
        return true
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
