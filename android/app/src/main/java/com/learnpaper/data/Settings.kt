package com.learnpaper.data

import com.learnpaper.content.Lang

enum class LayoutPreset { LOCK, HOME, COMPACT }

enum class WallpaperTarget { BOTH, HOME, LOCK }

data class Settings(
    val onboarded: Boolean = false,
    /** Language being learned: the big word on the card. */
    val headline: Lang = Lang.EN,
    /** Languages whose translations are shown under the headline. */
    val shown: Set<Lang> = setOf(Lang.RU, Lang.TG),
    val showTranscriptions: Boolean = true,
    val showExamples: Boolean = true,
    val levels: Set<String> = setOf("A1"),
    val paletteId: String = "peach",
    val rotatePalette: Boolean = false,
    val layout: LayoutPreset = LayoutPreset.LOCK,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val intervalMinutes: Int = 60,
    val quietEnabled: Boolean = true,
    /** Minutes since midnight. */
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
) {
    /** Translation languages in display order, never including the headline. */
    val translations: List<Lang>
        get() = Lang.entries.filter { it != headline && it in shown }

    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!quietEnabled || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) {
            minuteOfDay in quietStart until quietEnd
        } else {
            minuteOfDay >= quietStart || minuteOfDay < quietEnd
        }
    }

    companion object {
        val INTERVALS = listOf(15, 30, 60, 120, 180, 360, 720, 1440)
    }
}
