package com.learnpaper.domain

import com.learnpaper.content.Word
import com.learnpaper.data.HistoryEntry
import com.learnpaper.data.Progress
import com.learnpaper.data.Settings
import kotlin.random.Random

/**
 * Picks the next word. Words of the selected levels are shuffled once per pass with a
 * stable seed; learned words are skipped until nothing else is left.
 */
object Rotation {

    fun advance(progress: Progress, settings: Settings, words: List<Word>, now: Long): Progress {
        val levelsKey = settings.levels.sorted().joinToString(",")
        val seed = if (progress.seed == 0L) now else progress.seed
        var queue = progress.queue.filter { id -> id !in progress.learned && words.any { it.id == id } }

        if (progress.levelsKey != levelsKey || queue.isEmpty()) {
            queue = buildQueue(words, settings.levels, progress.learned, seed + progress.history.size)
            // Avoid showing the same word twice in a row when a new pass starts.
            if (queue.size > 1 && queue.first() == progress.currentId) queue = queue.drop(1) + queue.first()
        }
        if (queue.isEmpty()) return progress.copy(seed = seed, levelsKey = levelsKey, queue = emptyList())

        val nextId = queue.first()
        return progress.copy(
            seed = seed,
            levelsKey = levelsKey,
            queue = queue.drop(1),
            currentId = nextId,
            lastChangeAt = now,
            history = (listOf(HistoryEntry(nextId, now)) + progress.history).take(Progress.HISTORY_CAP),
            paletteIndex = progress.paletteIndex + 1,
        )
    }

    fun buildQueue(words: List<Word>, levels: Set<String>, learned: Set<String>, seed: Long): List<String> {
        val pool = words.filter { it.level in levels }
        val unlearned = pool.filter { it.id !in learned }
        val chosen = if (unlearned.isNotEmpty()) unlearned else pool
        return chosen.map { it.id }.shuffled(Random(seed))
    }
}
