package com.learnpaper.content

import com.learnpaper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads extra content packs listed in a manifest (default: this repo on GitHub, see
 * content/packs/). A pack is a zip with words.json and PNGs under images/, installed under
 * files/packs/<id>/ and merged by [ContentRepository].
 */
class PackRepository(private val content: ContentRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _installed = MutableStateFlow(readInstalled())
    val installed: StateFlow<Map<String, InstalledPack>> = _installed

    suspend fun fetchManifest(): Result<List<PackInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = open(BuildConfig.PACKS_MANIFEST_URL).use { it.readBytes().decodeToString() }
            json.decodeFromString<PackManifest>(text).packs
        }
    }

    /** Downloads and unpacks [pack]; [onProgress] gets 0..1 when the size is known. */
    suspend fun install(pack: PackInfo, onProgress: (Float) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(content.packsDir, pack.id)
            val staging = File(content.packsDir, "${pack.id}.tmp")
            staging.deleteRecursively()
            staging.mkdirs()
            val url = URL(URL(BuildConfig.PACKS_MANIFEST_URL), pack.url).toString()
            val conn = connect(url)
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: pack.bytes
            var read = 0L
            ZipInputStream(object : java.io.FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { n -> if (n > 0) { read += n; if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f)) } }
            }).use { zip ->
                var entry = zip.nextEntry
                var words = 0
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    if (name.startsWith("/") || name.contains("..")) throw IOException("bad zip entry $name")
                    if (!entry.isDirectory && (name == "words.json" || (name.startsWith("images/") && name.endsWith(".png")))) {
                        val out = File(staging, name)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        if (name == "words.json") words = json.decodeFromString<ContentPack>(out.readText()).words.size
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                if (!File(staging, "words.json").exists()) throw IOException("pack has no words.json")
                File(staging, "meta.json").writeText(
                    json.encodeToString(InstalledPack.serializer(), InstalledPack(pack.id, pack.version, words, System.currentTimeMillis())),
                )
            }
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("could not move pack into place")
            _installed.value = readInstalled()
            content.reload()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(content.packsDir, id).deleteRecursively()
        _installed.value = readInstalled()
        content.reload()
    }

    private fun readInstalled(): Map<String, InstalledPack> =
        content.installedPackDirs().mapNotNull { dir ->
            runCatching { json.decodeFromString<InstalledPack>(File(dir, "meta.json").readText()) }.getOrNull()
        }.associateBy { it.id }

    private fun connect(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for $url")
        return conn
    }

    private fun open(url: String) = connect(url).inputStream
}
