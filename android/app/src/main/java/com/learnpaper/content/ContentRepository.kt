package com.learnpaper.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the bundled content pack from assets/content (built by content/scripts/build.py) and merges
 * in any packs downloaded to files/packs/<id>/ (see [PackRepository]). A downloaded word with the
 * same id as a bundled one replaces it, so packs can also ship corrections.
 */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    val packsDir: File = File(context.filesDir, "packs")

    @Volatile
    private var cache: ContentPack? = null

    /** Bumped whenever the merged content changes, so UI flows can reload. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    suspend fun pack(): ContentPack = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) { load() }.also { cache = it }
    }

    suspend fun words(): List<Word> = pack().words

    suspend fun word(id: String): Word? = words().firstOrNull { it.id == id }

    /** Drops the cache after packs were installed or removed. */
    suspend fun reload() {
        mutex.withLock { cache = null }
        pack()
        _generation.value++
    }

    /** Decodes the word's illustration, or null when it has none. Caller owns the bitmap. */
    fun loadImage(word: Word): Bitmap? {
        val name = word.image ?: return null
        return runCatching {
            val dir = word.packDir
            if (dir != null) {
                BitmapFactory.decodeFile(File(dir, "images/$name").path)
            } else {
                context.assets.open("content/images/$name").use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    private fun load(): ContentPack {
        val bundled = context.assets.open("content/words.json").bufferedReader().use { reader ->
            json.decodeFromString<ContentPack>(reader.readText())
        }
        val merged = LinkedHashMap<String, Word>()
        bundled.words.forEach { merged[it.id] = it }
        installedPackDirs().forEach { dir ->
            val file = File(dir, "words.json")
            val pack = runCatching { json.decodeFromString<ContentPack>(file.readText()) }.getOrNull() ?: return@forEach
            pack.words.forEach { merged[it.id] = it.copy(packDir = dir.path) }
        }
        return ContentPack(bundled.version, merged.values.toList())
    }

    /** Directories of fully installed packs (those with a meta.json), in name order. */
    fun installedPackDirs(): List<File> =
        packsDir.listFiles { f -> f.isDirectory && File(f, "meta.json").exists() }?.sortedBy { it.name } ?: emptyList()

    companion object {
        val LEVEL_ORDER = listOf("A1", "A2", "B1", "B2", "C1", "C2")

        fun sortLevels(levels: Collection<String>): List<String> =
            levels.distinct().sortedBy { LEVEL_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
}
