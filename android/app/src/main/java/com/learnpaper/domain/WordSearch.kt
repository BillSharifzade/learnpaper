package com.learnpaper.domain

import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import java.text.Normalizer

/**
 * Search over all three languages at once. Text is folded so that people can type without the
 * special letters: case, stress marks and diacritics are ignored, ё = е, and the Tajik letters
 * ғ ӣ қ ӯ ҳ ҷ match their Russian look-alikes (г и к у х ч). Latin transliterations are indexed too,
 * so "salom" finds "салом". No Android dependencies; unit-tested.
 */
class WordSearch(words: List<Word>) {
    private val entries: List<Pair<Word, List<String>>> = words.map { w ->
        w to listOf(
            fold(w.en.text), fold(w.ru.text), fold(w.tj.text),
            fold(w.ru.tr), fold(w.tj.tr),
        ).filter { it.isNotEmpty() }
    }

    /** Words matching [query], best first: exact, then prefix, then word-prefix, then substring; ties by level. */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Word> {
        val q = fold(query).trim()
        if (q.isEmpty()) return emptyList()
        return entries.asSequence()
            .mapNotNull { (w, keys) -> rank(keys, q)?.let { w to it } }
            .sortedWith(compareBy<Pair<Word, Int>> { it.second }.thenBy { levelIndex(it.first.level) }.thenBy { it.first.en.text.length })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun rank(keys: List<String>, q: String): Int? {
        var best: Int? = null
        for (k in keys) {
            val r = when {
                k == q -> 0
                k.startsWith(q) -> 1
                k.split(' ', '-').any { it.startsWith(q) } -> 2
                k.contains(q) -> 3
                else -> null
            }
            if (r != null && (best == null || r < best)) best = r
        }
        return best
    }

    companion object {
        private val TAJIK_FOLD = mapOf('ғ' to 'г', 'ӣ' to 'и', 'қ' to 'к', 'ӯ' to 'у', 'ҳ' to 'х', 'ҷ' to 'ч', 'ё' to 'е')

        fun fold(text: String): String {
            val lower = text.lowercase()
            val sb = StringBuilder(lower.length)
            for (ch in lower) sb.append(TAJIK_FOLD[ch] ?: ch)
            // strip combining marks (stress accent, macrons and acutes of the transliterations)
            return Normalizer.normalize(sb, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .replace('ʼ', ' ').replace('\'', ' ')
        }

        fun levelIndex(level: String): Int = ContentRepository.LEVEL_ORDER.indexOf(level).let { if (it < 0) 99 else it }

        /** Library order: by level, then alphabetically in the headline language. */
        fun sorted(words: List<Word>, headline: Lang): List<Word> =
            words.sortedWith(compareBy<Word> { levelIndex(it.level) }.thenBy { fold(it.entry(headline).text) })
    }
}
