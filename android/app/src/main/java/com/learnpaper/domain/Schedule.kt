package com.learnpaper.domain

import com.learnpaper.data.Settings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the card may change by itself. There is no background job: the wallpaper engine asks this while
 * the wallpaper is on screen and changes the word once it is due, so a phone in a pocket does no work and
 * a word is never "shown" to nobody. The next change is one interval after the last one, moved to the end
 * of quiet hours when it falls inside them. No Android dependencies; unit-tested.
 */
object Schedule {

    /**
     * The moment of the next automatic change, never before [now]: [now] itself means it is due (or
     * overdue). A change that falls into quiet hours, or is overdue while they last, waits for their end.
     */
    fun nextChangeAt(settings: Settings, lastChangeAt: Long, now: Long, zone: ZoneId): Long {
        val interval = settings.intervalMinutes.coerceIn(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL) * 60_000L
        // Never changed yet: due at once. A last change "in the future" (the clock was set back) counts as now.
        val due = if (lastChangeAt <= 0L) now else minOf(lastChangeAt, now) + interval
        return afterQuietHours(settings, maxOf(due, now), zone)
    }

    fun isDue(settings: Settings, lastChangeAt: Long, now: Long, zone: ZoneId): Boolean =
        nextChangeAt(settings, lastChangeAt, now, zone) <= now

    /** [at] itself, or the end of the quiet hours that contain it. */
    fun afterQuietHours(settings: Settings, at: Long, zone: ZoneId): Long {
        val time = Instant.ofEpochMilli(at).atZone(zone)
        if (!settings.isQuiet(time.hour * 60 + time.minute)) return at
        val end = LocalTime.of(settings.quietEnd / 60 % 24, settings.quietEnd % 60)
        var next = time.toLocalDate().atTime(end).atZone(zone)
        if (!next.isAfter(time)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }
}
