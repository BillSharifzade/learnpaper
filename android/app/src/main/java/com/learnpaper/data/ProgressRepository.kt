package com.learnpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("progress_json")

    /**
     * The last decoded blob. The UI, the wallpaper engines and the widget all read progress, and the blob
     * grows with every word seen, so it is decoded once per change instead of once per reader.
     */
    @Volatile
    private var memo: Pair<String?, Progress>? = null

    val flow: Flow<Progress> = context.progressStore.data
        .map { prefs -> prefs[key] }
        .distinctUntilChanged()
        .map(::decode)

    suspend fun current(): Progress = flow.first()

    /** Applies [transform] atomically. Returning the same instance means "no change" and writes nothing. */
    suspend fun update(transform: (Progress) -> Progress): Progress {
        var result = Progress()
        context.progressStore.edit { prefs ->
            val before = decode(prefs[key])
            result = transform(before)
            if (result !== before) {
                val raw = json.encodeToString(Progress.serializer(), result)
                prefs[key] = raw
                memo = raw to result
            }
        }
        return result
    }

    private fun decode(raw: String?): Progress {
        memo?.let { (r, p) -> if (r == raw) return p }
        val p = raw?.let { runCatching { json.decodeFromString(Progress.serializer(), it) }.getOrNull() } ?: Progress()
        memo = raw to p
        return p
    }
}
