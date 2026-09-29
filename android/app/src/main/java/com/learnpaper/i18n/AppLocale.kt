package com.learnpaper.i18n

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
 * The UI never restarts to change language: the activity handles locale changes itself
 * (`android:configChanges`), and Compose reads its texts through a context in the chosen language
 * (see `LocalizedContent`), so a switch simply redraws the screen in place. On Android 13+ the choice
 * is also stored as the system per-app language, so it shows in the system's "App languages" screen
 * and a change made there arrives as a configuration change ([invalidate]). Everything else that draws
 * text (wallpaper, widget, notification) asks for [localized].
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "lang"

    private val _language = MutableStateFlow(AppLanguage.DEFAULT)

    /** The current interface language; the live wallpaper and the UI redraw when it changes. */
    val language: StateFlow<AppLanguage> = _language

    @Volatile
    private var loaded = false

    /** Application-context resources per language, so off-screen renderers do not rebuild them per card. */
    private val contexts = HashMap<AppLanguage, Context>()

    fun current(context: Context): AppLanguage {
        if (!loaded) init(context)
        return _language.value
    }

    private fun read(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (list != null && !list.isEmpty) AppLanguage.fromTag(list[0].toLanguageTag())?.let { return it }
        }
        return AppLanguage.fromTag(prefs(context).getString(KEY, null)) ?: AppLanguage.DEFAULT
    }

    /** Switches the interface language. Nothing is recreated: observers of [language] redraw. */
    fun set(context: Context, lang: AppLanguage) {
        prefs(context).edit { putString(KEY, lang.tag) }
        _language.value = lang
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(lang.tag)
        }
    }

    /**
     * Called from Application.onCreate. The first launch stores the default (Tajik) so the very first
     * screen is already in it; later launches pick up a change made in the system settings (13+).
     */
    @Synchronized
    fun init(context: Context) {
        val lang = read(context)
        val stored = prefs(context).getString(KEY, null)
        if (stored != lang.tag) prefs(context).edit { putString(KEY, lang.tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            if (lm != null && lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags(lang.tag)
        }
        _language.value = lang
        loaded = true
    }

    /** Re-reads the language after a configuration change, which may come from the system settings (13+). */
    fun invalidate(context: Context) {
        _language.value = read(context)
    }

    /** [base] with its resources and formatting switched to the interface language. */
    fun localized(base: Context): Context = localized(base, current(base))

    /** [base] with its resources and formatting switched to [lang]. */
    fun localized(base: Context, lang: AppLanguage): Context {
        val app = base.applicationContext
        if (base === app) synchronized(contexts) { contexts[lang]?.let { return it } }
        val config = Configuration(base.resources.configuration)
        val result = if (config.locales.get(0) == lang.locale) {
            base
        } else {
            config.setLocales(LocaleList(lang.locale))
            config.setLayoutDirection(lang.locale)
            base.createConfigurationContext(config)
        }
        if (base === app) synchronized(contexts) { contexts[lang] = result }
        return result
    }

    /**
     * Base context for the activity, so windows Compose does not reach (Material's own texts in dialogs
     * and sheets) start in the interface language on every Android version, not only on 13+.
     */
    fun wrapForActivity(base: Context): Context = localized(base)

    /** Drops cached resources, e.g. after the font scale or the theme changed. */
    fun clearCache() = synchronized(contexts) { contexts.clear() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
