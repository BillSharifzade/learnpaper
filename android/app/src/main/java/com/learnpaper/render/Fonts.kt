package com.learnpaper.render

import android.content.Context
import android.graphics.Typeface

/** Inter variable font from assets, one Typeface per weight. Covers Latin, IPA, Russian and Tajik Cyrillic. */
object Fonts {
    const val PATH = "fonts/Inter-Variable.ttf"
    private val cache = HashMap<Int, Typeface>()

    fun get(context: Context, weight: Int): Typeface = synchronized(cache) {
        cache.getOrPut(weight) {
            Typeface.Builder(context.assets, PATH)
                .setFontVariationSettings("'wght' $weight")
                .build() ?: Typeface.DEFAULT
        }
    }
}
