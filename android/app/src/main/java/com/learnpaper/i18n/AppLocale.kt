package com.learnpaper.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Language of the app's interface (not of the words on the card). [tag] is the Android locale
 * qualifier of the translation (`values-tg`), [nativeName] is how the option is shown in pickers.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    TJ("tg", "Тоҷикӣ"),
    RU("ru", "Русский"),
    EN("en", "English");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** The interface is Tajik until the user picks something else. */
        val DEFAULT = TJ

        fun fromTag(tag: String?): AppLanguage? {
            val lang = tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == lang }
        }
    }
}

/**
 * The interface language, independent of the system language.
 *
 * On Android 13+ it is the system per-app language (so it also appears in the system's "App languages"
 * screen and the platform applies it to every component). Older versions keep it in shared preferences;
 * the activity wraps its base context and everything else that draws text (wallpaper, widget,
 * notification) asks for [localized].
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "lang"

    @Volatile
    private var cached: AppLanguage? = null
    private val _changes = MutableStateFlow(0)

    /** Bumped whenever the language changes, so off-screen renderers (live wallpaper, widget) can redraw. */
    val changes: StateFlow<Int> = _changes

    fun current(context: Context): AppLanguage = cached ?: read(context).also { cached = it }

    private fun read(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (list != null && !list.isEmpty) AppLanguage.fromTag(list[0].toLanguageTag())?.let { return it }
        }
        return AppLanguage.fromTag(prefs(context).getString(KEY, null)) ?: AppLanguage.DEFAULT
    }

    /**
     * Switches the interface language. On Android 13+ the system recreates the activity by itself;
     * before that the caller's activity is recreated here.
     */
    fun set(context: Context, lang: AppLanguage) {
        prefs(context).edit { putString(KEY, lang.tag) }
        cached = lang
        _changes.value++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(lang.tag)
        } else {
            (context as? Activity)?.recreate()
        }
    }

    /**
     * Called from Application.onCreate. The first launch stores the default (Tajik) so the very first
     * screen is already in it; later launches pick up a change made in the system settings (13+).
     */
    fun init(context: Context) {
        cached = null
        val lang = read(context)
        val stored = prefs(context).getString(KEY, null)
        if (stored != lang.tag) prefs(context).edit { putString(KEY, lang.tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            if (lm != null && lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags(lang.tag)
        }
        cached = lang
    }

    /** Drops the cached value, e.g. after a configuration change coming from the system settings. */
    fun invalidate(context: Context) {
        val before = cached
        cached = null
        if (current(context) != before) _changes.value++
    }

    /** [base] with its resources and formatting switched to the interface language. */
    fun localized(base: Context): Context {
        val locale = current(base).locale
        val config = Configuration(base.resources.configuration)
        if (config.locales.get(0) == locale) return base
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /**
     * Base context for activities. Applied on every version: before Android 13 the platform has no
     * per-app locales at all, and on 13+ the first launch stores the default while the activity is
     * already being created with the system language, so the platform alone would show it too late.
     */
    fun wrapForActivity(base: Context): Context = localized(base)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
