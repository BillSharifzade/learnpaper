package com.learnpaper.render

import com.learnpaper.data.Settings
import org.junit.Test
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PalettesTest {
    @Test
    fun `ten light and ten dark palettes with unique ids`() {
        assertEquals(10, Palettes.light.size)
        assertEquals(10, Palettes.dark.size)
        assertTrue(Palettes.light.none { it.isDark })
        assertTrue(Palettes.dark.all { it.isDark })
        assertEquals(Palettes.all.size, Palettes.all.map { it.id }.toSet().size)
    }

    @Test
    fun `rotation stays inside the chosen group and the reported palette never rotates`() {
        val dark = Settings(paletteId = "ocean", rotatePalette = true)
        val light = Settings(paletteId = "mint", rotatePalette = true)
        (0 until 40).forEach { i ->
            assertTrue(Palettes.forSettings(dark, i).isDark)
            assertTrue(!Palettes.forSettings(light, i).isDark)
            assertEquals("ocean", Palettes.reported(dark).id)
        }
        assertEquals(Palettes.dark.toSet(), (0 until 10).map { Palettes.forSettings(dark, it) }.toSet())
        assertEquals("pine", Palettes.forSettings(Settings(paletteId = "pine"), 7).id)
    }

    @Test
    fun `text reads well on both ends of the card gradient`() {
        for (p in Palettes.all) {
            val bottom = mix(p.bg, p.tile, 0.55)
            // Main text: WCAG AAA everywhere. Muted text: AA for large text on the pastels, AA on the dark ones.
            val mutedMin = if (p.isDark) 4.5 else 3.0
            for (bg in listOf(p.bg, bottom)) {
                assertTrue(contrast(p.text, bg) >= 7.0, "${p.id} text ${contrast(p.text, bg)}")
                assertTrue(contrast(p.muted, bg) >= mutedMin, "${p.id} muted ${contrast(p.muted, bg)}")
            }
        }
    }

    private fun luminance(c: Int): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun lerp(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return (lerp(ch(a, 16), ch(b, 16)) shl 16) or (lerp(ch(a, 8), ch(b, 8)) shl 8) or lerp(ch(a, 0), ch(b, 0))
    }
}
