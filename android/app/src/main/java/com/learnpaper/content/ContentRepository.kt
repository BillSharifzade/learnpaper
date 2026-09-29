package com.learnpaper.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import kotlin.math.max

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

    @Volatile
    private var index: Map<String, Word> = emptyMap()

    /** Small decoded illustrations for lists, keyed by image path; sized in kilobytes. */
    private val thumbs = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Bumped whenever the merged content changes, so UI flows can reload. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    // The lock is taken and released on an IO thread only, so a caller that blocks the main thread (the
    // wallpaper's first frame) can never wait for a holder that needs the main thread to finish.
    suspend fun pack(): ContentPack = cache ?: withContext(Dispatchers.IO) {
        mutex.withLock {
            cache ?: load().also { loaded ->
                index = loaded.words.associateBy { it.id }
                cache = loaded
            }
        }
    }

    suspend fun words(): List<Word> = pack().words

    suspend fun word(id: String): Word? {
        pack()
        return index[id]
    }

    /** Drops the cache after packs were installed or removed. */
    suspend fun reload() {
        withContext(Dispatchers.IO) { mutex.withLock { cache = null; index = emptyMap() } }
        thumbs.evictAll()
        pack()
        _generation.value++
    }

    /** Decodes the word's illustration at full size, or [sampleSize] times smaller. Caller owns the bitmap. */
    fun loadImage(word: Word, sampleSize: Int = 1): Bitmap? {
        val name = word.image ?: return null
        return decode(word, name, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    /**
     * Decodes the illustration no smaller than [targetPx] on its long side, halving the source while that
     * still holds (WebP decodes scaled natively, so this is also the fastest decode). Caller owns the bitmap.
     */
    fun loadImageSized(word: Word, targetPx: Int): Bitmap? {
        val name = word.image ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(word, name, bounds)
        val source = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        if (source > 0 && targetPx > 0) while (source / (sample * 2) >= targetPx) sample *= 2
        return decode(word, name, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun decode(word: Word, name: String, options: BitmapFactory.Options): Bitmap? = runCatching {
        val dir = word.packDir
        if (dir != null) {
            BitmapFactory.decodeFile(File(dir, "images/$name").path, options)
        } else {
            context.assets.open("content/images/$name").use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    /** The list-sized illustration if it is already decoded; never touches the disk. */
    fun cachedThumbnail(word: Word): Bitmap? = word.image?.let { thumbs.get(thumbKey(word, it)) }

    /** A small shared copy of the illustration for lists (about 128 px). Do not recycle it. */
    fun thumbnail(word: Word): Bitmap? {
        val name = word.image ?: return null
        val key = thumbKey(word, name)
        thumbs.get(key)?.let { return it }
        return loadImage(word, sampleSize = 4)?.also { thumbs.put(key, it) }
    }

    /** Frees the thumbnails when the system is short of memory. */
    fun trimMemory() = thumbs.evictAll()

    private fun thumbKey(word: Word, name: String) = (word.packDir ?: "") + name

    @OptIn(ExperimentalSerializationApi::class)
    private fun load(): ContentPack {
        val bundled = context.assets.open("content/words.json").buffered().use { json.decodeFromStream<ContentPack>(it) }
        val merged = LinkedHashMap<String, Word>()
        bundled.words.forEach { merged[it.id] = it }
        installedPackDirs().forEach { dir ->
            val file = File(dir, "words.json")
            val pack = runCatching { file.inputStream().buffered().use { json.decodeFromStream<ContentPack>(it) } }.getOrNull() ?: return@forEach
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
