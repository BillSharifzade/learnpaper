package com.learnpaper.wallpaper

import android.content.Context
import android.speech.tts.TextToSpeech
import com.learnpaper.content.Lang
import java.util.Locale

/** Thin wrapper over the platform TTS engine. English and Russian only; Tajik has no on-device voice. */
class Speaker(context: Context) {
    private var ready = false
    private var pending: Pair<String, Locale>? = null
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        pending?.let { (text, locale) -> if (ready) speakNow(text, locale) }
        pending = null
    }

    fun supports(lang: Lang): Boolean = lang != Lang.TJ

    fun speak(text: String, lang: Lang) {
        if (!supports(lang)) return
        val locale = when (lang) {
            Lang.EN -> Locale.UK
            Lang.RU -> Locale("ru", "RU")
            Lang.TJ -> return
        }
        if (ready) speakNow(text, locale) else pending = text to locale
    }

    private fun speakNow(text: String, locale: Locale) {
        tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learnpaper")
    }

    fun shutdown() = tts.shutdown()
}
