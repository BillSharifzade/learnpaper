package com.learnpaper.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The three languages of the app. [code] is the key used in the content pack and [label] the short
 * tag drawn on the wallpaper. Tajik is "TJ" everywhere the user can see it; only the Android locale
 * qualifier (`values-tg`, `locales_config.xml`) keeps the ISO 639-1 code `tg`, because that is what
 * the system uses to pick the translation.
 */
@Serializable
enum class Lang(val code: String, val label: String) {
    EN("en", "EN"),
    RU("ru", "RU"),
    TJ("tj", "TJ");
}

/** A word in one language: display text plus transcription (IPA for English, Latin transliteration otherwise). */
@Serializable
data class LangEntry(val text: String, val tr: String = "")

@Serializable
data class Example(val en: String = "", val ru: String = "", val tj: String = "") {
    fun of(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.RU -> ru
        Lang.TJ -> tj
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
    val tj: LangEntry,
    val example: Example = Example(),
    val image: String? = null,
    /** Directory of the downloaded pack this word came from; null for bundled words. Not serialized. */
    @Transient val packDir: String? = null,
) {
    fun entry(lang: Lang): LangEntry = when (lang) {
        Lang.EN -> en
        Lang.RU -> ru
        Lang.TJ -> tj
    }
}

@Serializable
data class ContentPack(val version: Int = 1, val words: List<Word> = emptyList())

/** One downloadable pack as listed in the manifest. [url] may be relative to the manifest. */
@Serializable
data class PackInfo(
    val id: String,
    val name: Map<String, String> = emptyMap(),
    val levels: List<String> = emptyList(),
    val version: Int = 1,
    val words: Int = 0,
    val url: String,
    val bytes: Long = 0L,
) {
    fun displayName(lang: Lang): String = name[lang.code] ?: name["en"] ?: id
}

@Serializable
data class PackManifest(val packs: List<PackInfo> = emptyList())

/** What is stored next to an installed pack. */
@Serializable
data class InstalledPack(val id: String, val version: Int, val words: Int, val installedAt: Long)
