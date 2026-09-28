package com.learnpaper.render

import android.content.Context
import android.graphics.Typeface

/**
 * Bundled variable fonts, one cached Typeface per (file, weight).
 *
 * Onest is the brand face (words, translations, examples); it covers Latin, Russian and all Tajik
 * letters. Inter is used for transcriptions because it has every IPA symbol and the macron and acute
 * vowels of the Latin transliterations.
 */
object Fonts {
    const val ONEST = "fonts/Onest-Variable.ttf"
    const val INTER = "fonts/Inter-Variable.ttf"
    private val cache = HashMap<String, Typeface>()

    fun get(context: Context, weight: Int, path: String = ONEST): Typeface = synchronized(cache) {
        cache.getOrPut("$path@$weight") {
            Typeface.Builder(context.assets, path)
                .setFontVariationSettings("'wght' $weight")
                .build() ?: Typeface.DEFAULT
        }
    }
}
