package com.learnpaper.render

import androidx.annotation.StringRes
import com.learnpaper.R
import com.learnpaper.data.Settings

/** A card background with matching text colours. All colours are ARGB ints. */
data class Palette(
    val id: String,
    @StringRes val nameRes: Int,
    val bg: Int,
    val text: Int,
    val muted: Int,
    val tile: Int,
    val accent: Int,
) {
    /** Dark backgrounds get light text; the wallpaper reports this to the system (status bar icons, clock). */
    val isDark: Boolean = run {
        val r = (bg shr 16) and 0xFF
        val g = (bg shr 8) and 0xFF
        val b = bg and 0xFF
        (0.2126 * r + 0.7152 * g + 0.0722 * b) < 128
    }
}

/**
 * Two groups of ten: pastels and dark backgrounds. On both ends of the card's gradient the main text is
 * above 7:1 everywhere; muted text is above 3:1 on the pastels and 6.5:1 on the dark ones (PalettesTest).
 * Adding a palette also needs a `palette_*` string in all three languages.
 */
object Palettes {
    val light: List<Palette> = listOf(
        Palette("peach", R.string.palette_peach, 0xFFFFE1CF.toInt(), 0xFF4A2A1C.toInt(), 0xFF8C5A45.toInt(), 0xFFFFCDB2.toInt(), 0xFFE8845C.toInt()),
        Palette("mint", R.string.palette_mint, 0xFFD6F2E6.toInt(), 0xFF173E31.toInt(), 0xFF4F7D6C.toInt(), 0xFFBDE8D5.toInt(), 0xFF4CAF8A.toInt()),
        Palette("lavender", R.string.palette_lavender, 0xFFE6E0F8.toInt(), 0xFF2E2452.toInt(), 0xFF6B5E96.toInt(), 0xFFD5CCF2.toInt(), 0xFF8B76D6.toInt()),
        Palette("sky", R.string.palette_sky, 0xFFD8ECFA.toInt(), 0xFF163A57.toInt(), 0xFF4D7797.toInt(), 0xFFC2E0F6.toInt(), 0xFF5AA5DE.toInt()),
        Palette("sand", R.string.palette_sand, 0xFFF4E8D0.toInt(), 0xFF4A3A1D.toInt(), 0xFF8A7450.toInt(), 0xFFEBD9B4.toInt(), 0xFFC9A35C.toInt()),
        Palette("rose", R.string.palette_rose, 0xFFFADCE3.toInt(), 0xFF4E1F2E.toInt(), 0xFF93586B.toInt(), 0xFFF5C7D2.toInt(), 0xFFDD6E8B.toInt()),
        Palette("sage", R.string.palette_sage, 0xFFE1EBD9.toInt(), 0xFF2C3A22.toInt(), 0xFF647557.toInt(), 0xFFD0E0C2.toInt(), 0xFF7FA36A.toInt()),
        Palette("butter", R.string.palette_butter, 0xFFFFF1C2.toInt(), 0xFF4A3E12.toInt(), 0xFF8C7A3A.toInt(), 0xFFFFE79E.toInt(), 0xFFE3B939.toInt()),
        Palette("lilac", R.string.palette_lilac, 0xFFF1DDF3.toInt(), 0xFF43244A.toInt(), 0xFF855A8E.toInt(), 0xFFE8C9EB.toInt(), 0xFFB972C2.toInt()),
        Palette("powder", R.string.palette_powder, 0xFFE3E8EF.toInt(), 0xFF23303F.toInt(), 0xFF5A6B80.toInt(), 0xFFD3DAE5.toInt(), 0xFF6F87A6.toInt()),
    )

    val dark: List<Palette> = listOf(
        Palette("midnight", R.string.palette_midnight, 0xFF17142A.toInt(), 0xFFF2EEFF.toInt(), 0xFFA9A2C9.toInt(), 0xFF27224A.toInt(), 0xFFA99CFF.toInt()),
        Palette("pine", R.string.palette_pine, 0xFF10231C.toInt(), 0xFFE9F5EE.toInt(), 0xFF9DBCAC.toInt(), 0xFF1B3A2E.toInt(), 0xFF7FDDB7.toInt()),
        Palette("ocean", R.string.palette_ocean, 0xFF0E1B2E.toInt(), 0xFFEAF3FF.toInt(), 0xFF9EB4D0.toInt(), 0xFF19304F.toInt(), 0xFF6FB6FF.toInt()),
        Palette("plum", R.string.palette_plum, 0xFF241329.toInt(), 0xFFFBEFFF.toInt(), 0xFFC6A8CF.toInt(), 0xFF3B2045.toInt(), 0xFFD58CEA.toInt()),
        Palette("pomegranate", R.string.palette_pomegranate, 0xFF2A1117.toInt(), 0xFFFFEEF1.toInt(), 0xFFD4A4AD.toInt(), 0xFF46202B.toInt(), 0xFFFF7A90.toInt()),
        Palette("coffee", R.string.palette_coffee, 0xFF221912.toInt(), 0xFFFAF1E8.toInt(), 0xFFC8B2A1.toInt(), 0xFF3A2B1F.toInt(), 0xFFE8A66C.toInt()),
        Palette("graphite", R.string.palette_graphite, 0xFF1A1D22.toInt(), 0xFFEEF1F5.toInt(), 0xFFA8B0BB.toInt(), 0xFF2B3038.toInt(), 0xFF9DB4D2.toInt()),
        Palette("turquoise", R.string.palette_turquoise, 0xFF0C2426.toInt(), 0xFFE5F7F6.toInt(), 0xFF98C4C1.toInt(), 0xFF153D40.toInt(), 0xFF5CD6CD.toInt()),
        Palette("gold", R.string.palette_gold, 0xFF1F1A0C.toInt(), 0xFFFBF5E4.toInt(), 0xFFC9BD98.toInt(), 0xFF362E14.toInt(), 0xFFF2C04E.toInt()),
        // True black: pixels that are off on OLED screens.
        Palette("coal", R.string.palette_coal, 0xFF000000.toInt(), 0xFFF5F5F7.toInt(), 0xFFA3A3AB.toInt(), 0xFF18181C.toInt(), 0xFFFF9A76.toInt()),
    )

    val all: List<Palette> = light + dark

    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: all.first()

    /** The group (light or dark) a palette belongs to. */
    fun group(palette: Palette): List<Palette> = if (palette.isDark) dark else light

    /** The palette for a given change: the chosen one, or rotating by [index] through the chosen one's group. */
    fun forSettings(settings: Settings, index: Int): Palette {
        val chosen = byId(settings.paletteId)
        if (!settings.rotatePalette) return chosen
        val group = group(chosen)
        return group[Math.floorMod(index, group.size)]
    }

    /**
     * The colours the wallpaper reports to the system (Material You seed, dark or light status bar and
     * clock): always the chosen palette, never the rotating one, so a new word never re-themes the phone.
     * A rotation stays inside one group, so the dark/light hint stays true for every card.
     */
    fun reported(settings: Settings): Palette = byId(settings.paletteId)
}
