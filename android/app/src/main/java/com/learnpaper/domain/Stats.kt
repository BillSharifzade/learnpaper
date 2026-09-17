package com.learnpaper.domain

import com.learnpaper.data.Progress

data class Stats(val streakDays: Int, val wordsSeen: Int, val wordsLearned: Int, val dueToday: Int)

object StatsCalculator {

    fun of(progress: Progress, levels: Set<String>, wordLevels: Map<String, String>, now: Long, tzOffsetMs: Long): Stats {
        val today = Rotation.epochDay(now, tzOffsetMs)
        val inLevels = { id: String -> wordLevels[id] in levels }
        return Stats(
            streakDays = streak(progress.activeDays, today),
            wordsSeen = progress.reviews.keys.count(inLevels),
            wordsLearned = progress.learned.count(inLevels),
            dueToday = progress.reviews.count { (id, r) -> inLevels(id) && id !in progress.learned && r.dueAt <= now },
        )
    }

    /**
     * Consecutive days with activity ending today or yesterday (a streak survives until the end of
     * the next day). [activeDays] must be ascending and distinct.
     */
    fun streak(activeDays: List<Long>, today: Long): Int {
        if (activeDays.isEmpty()) return 0
        val last = activeDays.last()
        if (last < today - 1) return 0
        var count = 0
        var expected = last
        for (day in activeDays.asReversed()) {
            if (day != expected) break
            count++
            expected--
        }
        return count
    }
}
