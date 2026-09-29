package com.learnpaper.domain

import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import java.text.Normalizer

/**
 * Search over all three languages at once. Text is folded so that people can type without the
 * special letters: case, stress marks and diacritics are ignored, ё = е, and the Tajik letters
 * ғ ӣ қ ӯ ҳ ҷ match their Russian look-alikes (г и к у х ч). Latin transliterations are indexed too,
 * so "salom" finds "салом". The folded keys are built once per word list (build it off the main thread);
 * a query then only compares strings. No Android dependencies; unit-tested.
 */
class WordSearch(private val words: List<Word>) {
    init {
        require(words.size <= 0xFFFFF) { "search() packs a word's position into 20 bits" }
    }

    private val keys: Array<Array<String>> = Array(words.size) { i ->
        val w = words[i]
        arrayOf(fold(w.en.text), fold(w.ru.text), fold(w.tj.text), fold(w.ru.tr), fold(w.tj.tr))
            .filter { it.isNotEmpty() }.toTypedArray()
    }
    private val levels = IntArray(words.size) { levelIndex(words[it].level) }

    /** Words matching [query], best first: exact, then prefix, then word-prefix, then substring; ties by level. */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Word> {
        val q = fold(query).trim()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<Long>()
        for (i in words.indices) {
            val r = rank(keys[i], q) ?: continue
            // rank, level, headword length and position packed into one number that sorts in that order
            val length = words[i].en.text.length.coerceAtMost(0xFFF).toLong()
            hits += (r.toLong() shl 40) or (levels[i].toLong() shl 32) or (length shl 20) or i.toLong()
        }
        hits.sort()
        return hits.asSequence().take(limit).map { words[(it and 0xFFFFF).toInt()] }.toList()
    }

    private fun rank(keys: Array<String>, q: String): Int? {
        var best: Int? = null
        for (k in keys) {
            val r = when {
                k == q -> 0
                k.startsWith(q) -> 1
                startsAWord(k, q) -> 2
                k.contains(q) -> 3
                else -> null
            }
            if (r != null && (best == null || r < best)) best = r
        }
        return best
    }

    /** Whether [q] starts one of the words of [k] (after a space or a hyphen). */
    private fun startsAWord(k: String, q: String): Boolean {
        var i = k.indexOf(q, 1)
        while (i > 0) {
            if (k[i - 1] == ' ' || k[i - 1] == '-') return true
            i = k.indexOf(q, i + 1)
        }
        return false
    }

    companion object {
        fun fold(text: String): String {
            // NFD splits ӣ ӯ ё й and accented Latin letters into a base letter plus combining marks.
            val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            val sb = StringBuilder(decomposed.length)
            for (ch in decomposed) {
                if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                sb.append(
                    when (ch) {
                        'ғ' -> 'г'
                        'ӣ' -> 'и'
                        'қ' -> 'к'
                        'ӯ' -> 'у'
                        'ҳ' -> 'х'
                        'ҷ' -> 'ч'
                        'ё' -> 'е'
                        'ʼ', '\'' -> ' '
                        else -> ch
                    },
                )
            }
            return sb.toString()
        }

        fun levelIndex(level: String): Int = ContentRepository.LEVEL_ORDER.indexOf(level).let { if (it < 0) 99 else it }

        /** Library order: by level, then alphabetically in the headline language. */
        fun sorted(words: List<Word>, headline: Lang): List<Word> =
            words.map { it to fold(it.entry(headline).text) }
                .sortedWith(compareBy<Pair<Word, String>> { levelIndex(it.first.level) }.thenBy { it.second })
                .map { it.first }
    }
}
