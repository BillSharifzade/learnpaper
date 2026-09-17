package com.learnpaper.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Loads the bundled content pack from assets/content (built by content/scripts/build.py). */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cache: ContentPack? = null

    suspend fun pack(): ContentPack = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            context.assets.open("content/words.json").bufferedReader().use { reader ->
                json.decodeFromString<ContentPack>(reader.readText())
            }
        }.also { cache = it }
    }

    suspend fun words(): List<Word> = pack().words

    suspend fun word(id: String): Word? = words().firstOrNull { it.id == id }

    /** Decodes the word's illustration, or null when it has none. Caller owns the bitmap. */
    fun loadImage(word: Word): Bitmap? {
        val name = word.image ?: return null
        return runCatching {
            context.assets.open("content/images/$name").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    companion object {
        val LEVEL_ORDER = listOf("A1", "A2", "B1", "B2", "C1", "C2")

        fun sortLevels(levels: Collection<String>): List<String> =
            levels.distinct().sortedBy { LEVEL_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
}
