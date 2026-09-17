package com.learnpaper.domain

import com.learnpaper.data.Progress
import com.learnpaper.data.Review
import org.junit.Test
import kotlin.test.assertEquals

class StatsTest {
    @Test
    fun `streak counts consecutive days ending today or yesterday`() {
        assertEquals(0, StatsCalculator.streak(emptyList(), today = 100))
        assertEquals(3, StatsCalculator.streak(listOf(98, 99, 100), today = 100))
        assertEquals(3, StatsCalculator.streak(listOf(97, 98, 99), today = 100), "still alive until the end of today")
        assertEquals(0, StatsCalculator.streak(listOf(96, 97, 98), today = 100), "broken after a missed day")
        assertEquals(2, StatsCalculator.streak(listOf(90, 91, 95, 99, 100), today = 100), "only the latest run counts")
    }

    @Test
    fun `stats count only words of the selected levels`() {
        val now = 1_700_000_000_000L
        val levels = mapOf("a" to "A1", "b" to "A1", "c" to "A2", "d" to "A2")
        val p = Progress(
            reviews = mapOf("a" to Review(1, now - 1), "b" to Review(2, now + 1), "c" to Review(1, now - 1)),
            learned = setOf("b", "d"),
            activeDays = listOf(Rotation.epochDay(now, 0)),
        )
        val s = StatsCalculator.of(p, setOf("A1"), levels, now, 0)
        assertEquals(Stats(streakDays = 1, wordsSeen = 2, wordsLearned = 1, dueToday = 1), s)
        assertEquals(Stats(streakDays = 1, wordsSeen = 3, wordsLearned = 2, dueToday = 2), StatsCalculator.of(p, setOf("A1", "A2"), levels, now, 0))
    }
}
