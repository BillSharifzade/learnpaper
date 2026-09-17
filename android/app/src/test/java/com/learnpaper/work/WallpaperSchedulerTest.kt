package com.learnpaper.work

import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals

class WallpaperSchedulerTest {
    private val now = LocalDateTime.of(2026, 9, 17, 8, 30)

    @Test
    fun `delay reaches the next occurrence today`() {
        assertEquals(Duration.ofMinutes(30), WallpaperScheduler.delayUntil(9 * 60, now))
    }

    @Test
    fun `delay rolls over to tomorrow when the time has passed`() {
        assertEquals(Duration.ofHours(23), WallpaperScheduler.delayUntil(7 * 60 + 30, now))
        assertEquals(Duration.ofHours(24), WallpaperScheduler.delayUntil(8 * 60 + 30, now), "right now counts as passed")
    }
}
