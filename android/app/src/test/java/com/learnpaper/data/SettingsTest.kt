package com.learnpaper.data

import com.learnpaper.content.Lang
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    @Test
    fun `quiet hours wrap around midnight`() {
        val s = Settings(quietEnabled = true, quietStart = 23 * 60, quietEnd = 7 * 60)
        assertTrue(s.isQuiet(23 * 60))
        assertTrue(s.isQuiet(2 * 60))
        assertTrue(s.isQuiet(6 * 60 + 59))
        assertFalse(s.isQuiet(7 * 60))
        assertFalse(s.isQuiet(12 * 60))
        assertFalse(s.isQuiet(22 * 60 + 59))
    }

    @Test
    fun `quiet hours inside one day`() {
        val s = Settings(quietEnabled = true, quietStart = 9 * 60, quietEnd = 17 * 60)
        assertTrue(s.isQuiet(9 * 60))
        assertTrue(s.isQuiet(12 * 60))
        assertFalse(s.isQuiet(17 * 60))
        assertFalse(s.isQuiet(3 * 60))
    }

    @Test
    fun `quiet hours off or empty never match`() {
        assertFalse(Settings(quietEnabled = false).isQuiet(0))
        assertFalse(Settings(quietEnabled = true, quietStart = 60, quietEnd = 60).isQuiet(60))
    }

    @Test
    fun `translations never include the headline and keep the fixed order`() {
        assertEquals(listOf(Lang.RU, Lang.TJ), Settings(headline = Lang.EN, shown = setOf(Lang.TJ, Lang.RU, Lang.EN)).translations)
        assertEquals(listOf(Lang.EN, Lang.RU), Settings(headline = Lang.TJ, shown = setOf(Lang.RU, Lang.EN)).translations)
        assertEquals(listOf(Lang.EN), Settings(headline = Lang.RU, shown = setOf(Lang.EN)).translations)
    }

    @Test
    fun `tajik code is tj`() {
        assertEquals("tj", Lang.TJ.code)
        assertEquals("TJ", Lang.TJ.label)
    }
}
