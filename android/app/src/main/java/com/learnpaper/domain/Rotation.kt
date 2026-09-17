package com.learnpaper.domain

import com.learnpaper.content.Word
import com.learnpaper.data.HistoryEntry
import com.learnpaper.data.Progress
import com.learnpaper.data.Review
import com.learnpaper.data.Settings
import kotlin.random.Random

/**
 * Picks the next word.
 *
 * Light spaced repetition: every shown word gets a [Review] with a due time that grows with each
 * showing ([STEPS_DAYS]). A word that is due is shown before anything else. Otherwise words come
 * from the queue: unseen words of the selected levels, shuffled once per pass with a stable seed,
 * followed by seen words in the order they become due (so the wallpaper keeps changing even when
 * everything has been seen). Learned words are skipped until nothing else is left.
 */
object Rotation {

    /** Days until a word is shown again after its 1st, 2nd, … showing. */
    val STEPS_DAYS = listOf(1, 3, 7, 14, 30, 60)
    private const val DAY_MS = 86_400_000L

    fun advance(progress: Progress, settings: Settings, words: List<Word>, now: Long, tzOffsetMs: Long = 0L): Progress {
        val levelsKey = settings.levels.sorted().joinToString(",")
        val seed = if (progress.seed == 0L) now else progress.seed
        val byId = words.associateBy { it.id }
        val eligible = { id: String -> id !in progress.learned && byId[id]?.level in settings.levels }

        var queue = progress.queue.filter(eligible)
        if (progress.levelsKey != levelsKey || queue.isEmpty()) {
            queue = buildQueue(words, settings.levels, progress, now, seed + progress.history.size)
            // Avoid showing the same word twice in a row when a new pass starts.
            if (queue.size > 1 && queue.first() == progress.currentId) queue = queue.drop(1) + queue.first()
        }

        val due = dueNow(words, settings.levels, progress, now).firstOrNull { it != progress.currentId }
        val nextId = due ?: queue.firstOrNull()
            ?: return progress.copy(seed = seed, levelsKey = levelsKey, queue = emptyList())

        val stage = (progress.reviews[nextId]?.stage ?: 0).coerceAtMost(STEPS_DAYS.lastIndex)
        val review = Review(stage = stage + 1, dueAt = now + STEPS_DAYS[stage] * DAY_MS)
        val day = epochDay(now, tzOffsetMs)
        val days = if (progress.activeDays.lastOrNull() == day) progress.activeDays else (progress.activeDays + day).takeLast(Progress.ACTIVE_DAYS_CAP)

        return progress.copy(
            seed = seed,
            levelsKey = levelsKey,
            queue = queue - nextId,
            currentId = nextId,
            lastChangeAt = now,
            history = (listOf(HistoryEntry(nextId, now)) + progress.history).take(Progress.HISTORY_CAP),
            paletteIndex = progress.paletteIndex + 1,
            reviews = progress.reviews + (nextId to review),
            activeDays = days,
        )
    }

    /** Unlearned words of [levels] that are due for review, earliest first. */
    fun dueNow(words: List<Word>, levels: Set<String>, progress: Progress, now: Long): List<String> =
        words.asSequence()
            .filter { it.level in levels && it.id !in progress.learned }
            .mapNotNull { w -> progress.reviews[w.id]?.takeIf { it.dueAt <= now }?.let { w.id to it.dueAt } }
            .sortedBy { it.second }
            .map { it.first }
            .toList()

    /** Unseen words shuffled by [seed], then seen ones by due time. Falls back to learned words when nothing else is left. */
    fun buildQueue(words: List<Word>, levels: Set<String>, progress: Progress, now: Long, seed: Long): List<String> {
        val pool = words.filter { it.level in levels }
        val unlearned = pool.filter { it.id !in progress.learned }
        val chosen = if (unlearned.isNotEmpty()) unlearned else pool
        val (seen, unseen) = chosen.partition { it.id in progress.reviews }
        return unseen.map { it.id }.shuffled(Random(seed)) +
            seen.sortedBy { progress.reviews.getValue(it.id).dueAt }.map { it.id }
    }

    fun epochDay(now: Long, tzOffsetMs: Long): Long = Math.floorDiv(now + tzOffsetMs, DAY_MS)
}
