package com.learnpaper.render

import android.content.Context
import com.learnpaper.R

object PosNames {
    private val map = mapOf(
        "noun" to R.string.pos_noun,
        "verb" to R.string.pos_verb,
        "adjective" to R.string.pos_adjective,
        "adverb" to R.string.pos_adverb,
        "interjection" to R.string.pos_interjection,
        "phrase" to R.string.pos_phrase,
        "pronoun" to R.string.pos_pronoun,
        "preposition" to R.string.pos_preposition,
        "numeral" to R.string.pos_numeral,
        "conjunction" to R.string.pos_conjunction,
    )

    fun localized(context: Context, pos: String): String = map[pos]?.let(context::getString) ?: pos
}
