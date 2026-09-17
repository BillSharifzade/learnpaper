package com.learnpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("progress_json")

    val flow: Flow<Progress> = context.progressStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun current(): Progress = flow.first()

    suspend fun update(transform: (Progress) -> Progress): Progress {
        var result = Progress()
        context.progressStore.edit { prefs ->
            result = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(Progress.serializer(), result)
        }
        return result
    }

    private fun decode(raw: String?): Progress =
        raw?.let { runCatching { json.decodeFromString(Progress.serializer(), it) }.getOrNull() } ?: Progress()
}
