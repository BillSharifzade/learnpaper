package com.learnpaper.domain

import com.learnpaper.content.Example
import com.learnpaper.content.Lang
import com.learnpaper.content.LangEntry
import com.learnpaper.content.Word
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordSearchTest {
    private fun w(id: String, en: String, ru: String, ruTr: String, tj: String, tjTr: String, level: String = "A1") = Word(
        id = id, level = level, pos = "noun",
        en = LangEntry(en, ""), ru = LangEntry(ru, ruTr), tj = LangEntry(tj, tjTr), example = Example(),
    )

    private val words = listOf(
        w("hello", "hello", "привет", "privét", "салом", "salom"),
        w("airport", "airport", "аэропорт", "aeropórt", "фурудгоҳ", "furudgoh", "A2"),
        w("tea", "tea", "чай", "chay", "чой", "choy"),
        w("hedgehog", "hedgehog", "ёж", "yozh", "хорпушт", "khorpusht", "B1"),
        w("teacher", "teacher", "учитель", "uchítel", "муаллим", "muallim"),
    )
    private val search = WordSearch(words)

    @Test
    fun `finds words in any of the three languages`() {
        assertEquals("hello", search.search("hello").first().id)
        assertEquals("hello", search.search("привет").first().id)
        assertEquals("hello", search.search("салом").first().id)
    }

    @Test
    fun `Tajik letters can be typed with their Russian look-alikes`() {
        assertEquals("airport", search.search("фурудгох").first().id)
        assertEquals("airport", search.search("ФУРУДГОҲ").first().id)
    }

    @Test
    fun `transliterations and stress marks are searchable without diacritics`() {
        assertEquals("airport", search.search("aeroport").first().id)
        assertEquals("hello", search.search("salom").first().id)
        assertEquals("hedgehog", search.search("еж").first().id)
    }

    @Test
    fun `exact and prefix matches rank before substrings`() {
        val r = search.search("tea").map { it.id }
        assertEquals("tea", r.first())
        assertTrue("teacher" in r)
    }

    @Test
    fun `blank query finds nothing and library order is level then alphabet`() {
        assertEquals(emptyList(), search.search("  "))
        assertEquals(listOf("hello", "tea", "teacher", "airport", "hedgehog"), WordSearch.sorted(words, Lang.EN).map { it.id })
    }
}
