package com.learnpaper.domain

import com.learnpaper.data.Settings
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTest {
    private val zone = ZoneId.of("Asia/Dushanbe")
    private val quiet = Settings(intervalMinutes = 60, quietEnabled = true, quietStart = 23 * 60, quietEnd = 7 * 60)
    private val noQuiet = quiet.copy(quietEnabled = false)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `the next word comes one interval after the last change`() {
        val last = at(29, 10)
        assertEquals(at(29, 11), Schedule.nextChangeAt(quiet, last, at(29, 10, 20), zone))
        assertFalse(Schedule.isDue(quiet, last, at(29, 10, 59), zone))
        assertTrue(Schedule.isDue(quiet, last, at(29, 11), zone))
    }

    @Test
    fun `an overdue word is due now, not in the past`() {
        val now = at(29, 15)
        assertEquals(now, Schedule.nextChangeAt(quiet, at(29, 10), now, zone))
    }

    @Test
    fun `a word that was never shown is due at once`() {
        assertTrue(Schedule.isDue(quiet, 0L, at(29, 12), zone))
    }

    @Test
    fun `one-minute intervals work and shorter ones are clamped`() {
        val last = at(29, 12)
        assertEquals(at(29, 12, 1), Schedule.nextChangeAt(noQuiet.copy(intervalMinutes = 1), last, last, zone))
        assertEquals(at(29, 12, 1), Schedule.nextChangeAt(noQuiet.copy(intervalMinutes = 0), last, last, zone))
    }

    @Test
    fun `a change inside quiet hours waits for their end`() {
        // due at 23:30 -> 07:00 the next morning
        assertEquals(at(30, 7), Schedule.nextChangeAt(quiet, at(29, 22, 30), at(29, 22, 45), zone))
        // due at 02:00 -> 07:00 the same morning
        assertEquals(at(30, 7), Schedule.nextChangeAt(quiet, at(30, 1), at(30, 1, 5), zone))
    }

    @Test
    fun `an overdue change does not happen during quiet hours`() {
        // last change at 20:00, nobody looked until 23:30: the word waits for the morning
        assertEquals(at(30, 7), Schedule.nextChangeAt(quiet, at(29, 20), at(29, 23, 30), zone))
        assertFalse(Schedule.isDue(quiet, at(29, 20), at(29, 23, 30), zone))
        assertTrue(Schedule.isDue(quiet, at(29, 20), at(30, 7), zone))
        assertTrue(Schedule.isDue(noQuiet, at(29, 20), at(29, 23, 30), zone))
    }

    @Test
    fun `a clock set back does not stop the rotation for long`() {
        val now = at(29, 12)
        assertEquals(at(29, 13), Schedule.nextChangeAt(quiet, at(30, 12), now, zone))
    }
}
