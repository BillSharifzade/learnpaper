package com.learnpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.learnpaper.content.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HEADLINE = stringPreferencesKey("headline")
        val SHOWN = stringSetPreferencesKey("shown")
        val TRANSCRIPTIONS = booleanPreferencesKey("transcriptions")
        val EXAMPLES = booleanPreferencesKey("examples")
        val LEVELS = stringSetPreferencesKey("levels")
        val PALETTE = stringPreferencesKey("palette")
        val ROTATE_PALETTE = booleanPreferencesKey("rotate_palette")
        val LAYOUT = stringPreferencesKey("layout")
        val MODE = stringPreferencesKey("mode")
        val TARGET = stringPreferencesKey("target")
        val INTERVAL = intPreferencesKey("interval")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val NOTIFY_DAILY = booleanPreferencesKey("notify_daily")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
    }

    val flow: Flow<Settings> = context.settingsStore.data.map(::read)

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings): Settings {
        var result = Settings()
        context.settingsStore.edit { prefs ->
            result = transform(read(prefs))
            write(prefs, result)
        }
        return result
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            onboarded = p[Keys.ONBOARDED] ?: d.onboarded,
            headline = p[Keys.HEADLINE]?.let(::langOrNull) ?: d.headline,
            shown = p[Keys.SHOWN]?.mapNotNull(::langOrNull)?.toSet() ?: d.shown,
            showTranscriptions = p[Keys.TRANSCRIPTIONS] ?: d.showTranscriptions,
            showExamples = p[Keys.EXAMPLES] ?: d.showExamples,
            levels = p[Keys.LEVELS]?.takeIf { it.isNotEmpty() } ?: d.levels,
            paletteId = p[Keys.PALETTE] ?: d.paletteId,
            rotatePalette = p[Keys.ROTATE_PALETTE] ?: d.rotatePalette,
            layout = p[Keys.LAYOUT]?.let { v -> LayoutPreset.entries.firstOrNull { it.name == v } } ?: d.layout,
            mode = p[Keys.MODE]?.let { v -> WallpaperMode.entries.firstOrNull { it.name == v } } ?: d.mode,
            target = p[Keys.TARGET]?.let { v -> WallpaperTarget.entries.firstOrNull { it.name == v } } ?: d.target,
            intervalMinutes = p[Keys.INTERVAL] ?: d.intervalMinutes,
            quietEnabled = p[Keys.QUIET_ENABLED] ?: d.quietEnabled,
            quietStart = p[Keys.QUIET_START] ?: d.quietStart,
            quietEnd = p[Keys.QUIET_END] ?: d.quietEnd,
            notifyDaily = p[Keys.NOTIFY_DAILY] ?: d.notifyDaily,
            notifyMinute = p[Keys.NOTIFY_MINUTE] ?: d.notifyMinute,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[Keys.ONBOARDED] = s.onboarded
        p[Keys.HEADLINE] = s.headline.name
        p[Keys.SHOWN] = s.shown.map { it.name }.toSet()
        p[Keys.TRANSCRIPTIONS] = s.showTranscriptions
        p[Keys.EXAMPLES] = s.showExamples
        p[Keys.LEVELS] = s.levels
        p[Keys.PALETTE] = s.paletteId
        p[Keys.ROTATE_PALETTE] = s.rotatePalette
        p[Keys.LAYOUT] = s.layout.name
        p[Keys.MODE] = s.mode.name
        p[Keys.TARGET] = s.target.name
        p[Keys.INTERVAL] = s.intervalMinutes
        p[Keys.QUIET_ENABLED] = s.quietEnabled
        p[Keys.QUIET_START] = s.quietStart
        p[Keys.QUIET_END] = s.quietEnd
        p[Keys.NOTIFY_DAILY] = s.notifyDaily
        p[Keys.NOTIFY_MINUTE] = s.notifyMinute
    }

    /** "TG" was the stored name of Tajik before it was renamed to TJ. */
    private fun langOrNull(name: String): Lang? =
        if (name == "TG") Lang.TJ else Lang.entries.firstOrNull { it.name == name }
}
