package com.learnpaper.content

import kotlinx.serialization.Serializable

/** The three languages of the app. [label] is the short tag drawn on the wallpaper. */
@Serializable
enum class Lang(val code: String, val label: String) {
    EN("en", "EN"),
    RU("ru", "RU"),
    TG("tg", "TG");
}

/** A word in one language: display text plus transcription (IPA for English, Latin transliteration otherwise). */
@Serializable
data class LangEntry(val text: String, val tr: String = "")

@Serializable
data class Example(val en: String = "", val ru: String = "", val tg: String = "") {
    fun of(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.RU -> ru
        Lang.TG -> tg
    }
}

@Serializable
data class Word(
    val id: String,
    val level: String,
    val pos: String = "",
    val tags: List<String> = emptyList(),
    val en: LangEntry,
    val ru: LangEntry,
    val tg: LangEntry,
    val example: Example = Example(),
    val image: String? = null,
) {
    fun entry(lang: Lang): LangEntry = when (lang) {
        Lang.EN -> en
        Lang.RU -> ru
        Lang.TG -> tg
    }
}

@Serializable
data class ContentPack(val version: Int = 1, val words: List<Word> = emptyList())
