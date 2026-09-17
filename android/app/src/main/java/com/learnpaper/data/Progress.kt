package com.learnpaper.data

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(val id: String, val at: Long)

/** Spaced-repetition state of one word: how many times it was shown and when it is due again. */
@Serializable
data class Review(val stage: Int = 0, val dueAt: Long = 0L)

/** Everything the app remembers about what the user has seen. Stored as one JSON blob. */
@Serializable
data class Progress(
    /** Shuffle seed; 0 means "not chosen yet". */
    val seed: Long = 0L,
    /** Sorted, comma-joined levels the [queue] was built for. */
    val levelsKey: String = "",
    /** Word ids still to be shown in this pass. */
    val queue: List<String> = emptyList(),
    val currentId: String? = null,
    val lastChangeAt: Long = 0L,
    /** Newest first, capped. */
    val history: List<HistoryEntry> = emptyList(),
    val learned: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    /** Incremented on every change; drives palette rotation. */
    val paletteIndex: Int = 0,
    /** One entry per word that has been shown at least once. */
    val reviews: Map<String, Review> = emptyMap(),
    /** Local calendar days (days since epoch) on which a word was shown; ascending, capped. */
    val activeDays: List<Long> = emptyList(),
) {
    companion object {
        const val HISTORY_CAP = 500
        const val ACTIVE_DAYS_CAP = 400
    }
}
