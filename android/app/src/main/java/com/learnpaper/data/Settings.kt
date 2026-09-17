package com.learnpaper.data

import com.learnpaper.content.Lang

enum class LayoutPreset { LOCK, HOME, COMPACT }

enum class WallpaperTarget { BOTH, HOME, LOCK }

/**
 * How the card reaches the screen. [LIVE] draws it from our own wallpaper service, so a change is a
 * plain redraw: no wallpaper-changed broadcast, no colour extraction, no system re-theme (which on
 * some launchers restarts the whole home screen). [STATIC] sets a bitmap with WallpaperManager.
 */
enum class WallpaperMode { LIVE, STATIC }

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
    val rotatePalette: Boolean = false,
    val layout: LayoutPreset = LayoutPreset.LOCK,
    val mode: WallpaperMode = WallpaperMode.LIVE,
    /** Screens to set in [WallpaperMode.STATIC]; in LIVE mode the system picker decides. */
    val target: WallpaperTarget = WallpaperTarget.BOTH,
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
