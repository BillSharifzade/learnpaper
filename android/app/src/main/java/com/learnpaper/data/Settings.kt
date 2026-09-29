package com.learnpaper.data

import com.learnpaper.content.Lang

/**
 * Where the card sits. LOCK, HOME and COMPACT are the user's choices for the home screen; the lock screen
 * uses LOCK, or UNDER_CLOCK when the phone draws a large clock in the middle (see [LockStyle]).
 */
enum class LayoutPreset {
    LOCK, HOME, COMPACT, UNDER_CLOCK;

    companion object {
        /** The presets offered to the user for the home screen. */
        val choices = listOf(LOCK, HOME, COMPACT)
    }
}

/**
 * How the phone draws its lock-screen clock. Most skins (Xiaomi, Samsung) put a small clock at the top;
 * stock Android on Pixels shows a large two-line clock in the middle when there are no notifications.
 */
enum class LockStyle {
    AUTO, TOP_CLOCK, BIG_CLOCK;

    /** AUTO resolves by manufacturer: Google (Pixel, emulator) uses the large clock by default. */
    fun resolve(manufacturer: String = android.os.Build.MANUFACTURER ?: ""): LockStyle = when (this) {
        AUTO -> if (manufacturer.equals("Google", ignoreCase = true)) BIG_CLOCK else TOP_CLOCK
        else -> this
    }
}

data class Settings(
    val onboarded: Boolean = false,
    /** Language being learned: the big word on the card. */
    val headline: Lang = Lang.EN,
    /** Languages whose translations are shown under the headline. */
    val shown: Set<Lang> = setOf(Lang.RU, Lang.TJ),
    val showTranscriptions: Boolean = true,
    val showExamples: Boolean = true,
    val levels: Set<String> = setOf("A1"),
    val paletteId: String = "peach",
    /** A new palette with every word, taken from the group (light or dark) of [paletteId]. */
    val rotatePalette: Boolean = false,
    val layout: LayoutPreset = LayoutPreset.LOCK,
    val lockStyle: LockStyle = LockStyle.AUTO,
    /** Minutes between words, [MIN_INTERVAL]..[MAX_INTERVAL]: one of [INTERVALS] or a custom value. */
    val intervalMinutes: Int = 60,
    val quietEnabled: Boolean = true,
    /** Minutes since midnight. */
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    /** Daily "word of the day" notification. */
    val notifyDaily: Boolean = false,
    val notifyMinute: Int = 9 * 60,
) {
    /** Translation languages in display order, never including the headline. */
    val translations: List<Lang>
        get() = Lang.entries.filter { it != headline && it in shown }

    /** The layout used on the lock screen: clear of the clock wherever the phone draws it. */
    fun lockLayout(style: LockStyle = lockStyle.resolve()): LayoutPreset =
        if (style == LockStyle.BIG_CLOCK) LayoutPreset.UNDER_CLOCK else LayoutPreset.LOCK

    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!quietEnabled || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) {
            minuteOfDay in quietStart until quietEnd
        } else {
            minuteOfDay >= quietStart || minuteOfDay < quietEnd
        }
    }

    companion object {
        /** Offered as chips; any other value between the bounds can be entered as a custom interval. */
        val INTERVALS = listOf(15, 30, 60, 120, 180, 360, 720, 1440)

        /**
         * Words change only while the wallpaper is on screen (see [com.learnpaper.domain.Schedule]),
         * so there is no background job and no 15-minute WorkManager floor: one minute is fine.
         */
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 24 * 60
    }
}
