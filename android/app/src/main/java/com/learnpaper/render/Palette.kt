package com.learnpaper.render

import androidx.annotation.StringRes
import com.learnpaper.R
import com.learnpaper.data.Settings

/** A pastel background with matching text colours. All colours are ARGB ints. */
data class Palette(
    val id: String,
    @StringRes val nameRes: Int,
    val bg: Int,
    val text: Int,
    val muted: Int,
    val tile: Int,
    val accent: Int,
)

object Palettes {
    val all: List<Palette> = listOf(
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

    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: all.first()

    /** The palette for a given change: fixed, or rotating through [all] by [index]. */
    fun forSettings(settings: Settings, index: Int): Palette =
        if (settings.rotatePalette) all[Math.floorMod(index, all.size)] else byId(settings.paletteId)
}
