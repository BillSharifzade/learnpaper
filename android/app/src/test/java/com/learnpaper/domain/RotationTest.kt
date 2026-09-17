package com.learnpaper.domain

import com.learnpaper.data.Progress
import com.learnpaper.data.Review
import com.learnpaper.data.Settings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RotationTest {
    private val settings = Settings(levels = setOf("A1"))
    private val now = 1_700_000_000_000L

    @Test
    fun `first advance picks a word of the selected level and records it`() {
        val all = words(5, "A1") + words(3, "A2")
        val p = Rotation.advance(Progress(), settings, all, now)

        val id = assertNotNull(p.currentId)
        assertTrue(id.startsWith("a1-"), "picked $id")
        assertEquals(listOf(id), p.history.map { it.id })
        assertEquals(now, p.lastChangeAt)
        assertEquals(Review(stage = 1, dueAt = now + DAY), p.reviews[id])
        assertEquals(4, p.queue.size, "the rest of the pass stays queued")
        assertNotEquals(0L, p.seed)
        assertEquals("A1", p.levelsKey)
    }

    @Test
    fun `a pass shows every word once before repeating`() {
        val all = words(6)
        var p = Progress()
        val shown = (1..6).map {
            p = Rotation.advance(p, settings, all, now + it * 60_000L)
            p.currentId!!
        }
        assertEquals(all.map { it.id }.toSet(), shown.toSet())
        assertEquals(6, shown.distinct().size)
    }

    @Test
    fun `same seed gives the same order`() {
        val all = words(10)
        val a = Rotation.advance(Progress(), settings, all, now)
        val b = Rotation.advance(Progress(), settings, all, now)
        assertEquals(a.queue, b.queue)
        assertEquals(a.currentId, b.currentId)
    }

    @Test
    fun `a new pass does not repeat the last word of the previous one`() {
        val all = words(2)
        var p = Progress()
        // Reviews are far in the future, so the queue (not due reviews) drives the order.
        repeat(20) { i ->
            val next = Rotation.advance(p, settings, all, now + i * 60_000L)
            assertNotEquals(p.currentId, next.currentId, "step $i repeated ${next.currentId}")
            p = next
        }
    }

    @Test
    fun `learned words are skipped until nothing else is left`() {
        val all = words(3)
        var p = Progress(learned = setOf("a1-1", "a1-2"))
        p = Rotation.advance(p, settings, all, now)
        assertEquals("a1-3", p.currentId)
        // With one unlearned word left it simply stays on screen.
        p = Rotation.advance(p, settings, all, now + 60_000L)
        assertEquals("a1-3", p.currentId)
        // Everything learned: learned words are shown rather than leaving the wallpaper stuck.
        p = Rotation.advance(p.copy(learned = p.learned + "a1-3"), settings, all, now + 120_000L)
        assertTrue(p.currentId in setOf("a1-1", "a1-2"), "got ${p.currentId}")
    }

    @Test
    fun `changing levels rebuilds the queue`() {
        val all = words(3, "A1") + words(3, "A2")
        var p = Rotation.advance(Progress(), settings, all, now)
        assertTrue(p.queue.all { it.startsWith("a1-") })
        p = Rotation.advance(p, settings.copy(levels = setOf("A2")), all, now + 60_000L)
        assertEquals("A2", p.levelsKey)
        assertTrue(p.currentId!!.startsWith("a2-"))
        assertTrue(p.queue.all { it.startsWith("a2-") })
    }

    @Test
    fun `a due review is shown before new words and its interval grows`() {
        val all = words(10)
        val due = Progress(
            queue = listOf("a1-5", "a1-6"),
            levelsKey = "A1",
            seed = 1L,
            currentId = "a1-6",
            reviews = mapOf(
                "a1-1" to Review(stage = 2, dueAt = now - 1),
                "a1-2" to Review(stage = 1, dueAt = now - DAY), // due earlier -> first
                "a1-3" to Review(stage = 1, dueAt = now + DAY), // not due
            ),
        )
        val p = Rotation.advance(due, settings, all, now)
        assertEquals("a1-2", p.currentId)
        assertEquals(Review(stage = 2, dueAt = now + 3 * DAY), p.reviews["a1-2"])
        assertEquals(listOf("a1-5", "a1-6"), p.queue, "the queue is untouched by a review")

        val p2 = Rotation.advance(p, settings, all, now + 1)
        assertEquals("a1-1", p2.currentId)
        assertEquals(Review(stage = 3, dueAt = now + 1 + 7 * DAY), p2.reviews["a1-1"])
    }

    @Test
    fun `review interval caps at the last step`() {
        val all = words(2)
        val last = Rotation.STEPS_DAYS.lastIndex
        val p = Progress(reviews = mapOf("a1-1" to Review(stage = last + 5, dueAt = now - 1)), currentId = "a1-2")
        val next = Rotation.advance(p, settings, all, now)
        assertEquals("a1-1", next.currentId)
        assertEquals(Review(stage = last + 1, dueAt = now + Rotation.STEPS_DAYS[last] * DAY), next.reviews["a1-1"])
    }

    @Test
    fun `learned words are not reviewed`() {
        val all = words(3)
        val p = Progress(learned = setOf("a1-1"), reviews = mapOf("a1-1" to Review(1, now - 1)))
        assertEquals(emptyList(), Rotation.dueNow(all, setOf("A1"), p, now))
        assertNotEquals("a1-1", Rotation.advance(p, settings, all, now).currentId)
    }

    @Test
    fun `queue falls back to seen words ordered by due time when everything was seen`() {
        val all = words(3)
        val p = Progress(
            reviews = mapOf(
                "a1-1" to Review(1, now + 3 * DAY),
                "a1-2" to Review(1, now + 1 * DAY),
                "a1-3" to Review(1, now + 2 * DAY),
            ),
        )
        assertEquals(listOf("a1-2", "a1-3", "a1-1"), Rotation.buildQueue(all, setOf("A1"), p, now, seed = 42L))
    }

    @Test
    fun `active days are recorded once per local day`() {
        val all = words(5)
        val tz = 5 * 3_600_000L // UTC+5, Dushanbe
        val day0 = Rotation.epochDay(now, tz)
        var p = Rotation.advance(Progress(), settings, all, now, tz)
        p = Rotation.advance(p, settings, all, now + 3_600_000L, tz)
        assertEquals(listOf(day0), p.activeDays)
        p = Rotation.advance(p, settings, all, now + DAY, tz)
        assertEquals(listOf(day0, day0 + 1), p.activeDays)
    }

    @Test
    fun `no words leaves progress without a current word`() {
        val p = Rotation.advance(Progress(), settings, emptyList(), now)
        assertNull(p.currentId)
        assertTrue(p.history.isEmpty())
    }

    @Test
    fun `history is capped`() {
        val all = words(3)
        var p = Progress()
        repeat(Progress.HISTORY_CAP + 20) { p = Rotation.advance(p, settings, all, now + it * 60_000L) }
        assertEquals(Progress.HISTORY_CAP, p.history.size)
    }
}
