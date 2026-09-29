package com.learnpaper.ui

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnpaper.Graph
import com.learnpaper.R
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.InstalledPack
import com.learnpaper.content.Lang
import com.learnpaper.content.PackInfo
import com.learnpaper.content.Word
import com.learnpaper.data.Progress
import com.learnpaper.data.Settings
import com.learnpaper.domain.Stats
import com.learnpaper.domain.StatsCalculator
import com.learnpaper.domain.WordSearch
import com.learnpaper.i18n.AppLanguage
import com.learnpaper.i18n.AppLocale
import com.learnpaper.render.CardStyle
import com.learnpaper.render.Palette
import com.learnpaper.ui.components.Thumbnails
import com.learnpaper.wallpaper.ChangeResult
import com.learnpaper.wallpaper.LiveCardWallpaper
import com.learnpaper.wallpaper.Speaker
import com.learnpaper.widget.CardWidget
import com.learnpaper.work.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * The loaded words and everything the UI derives from them: built once per load, off the main thread
 * (the search index folds ~12,000 strings), and shared by every screen.
 */
class Library(val words: List<Word>) {
    val byId: Map<String, Word> = words.associateBy { it.id }
    /** Levels present in the content, in CEFR order. */
    val levels: List<String> = ContentRepository.sortLevels(words.map { it.level })
    val levelCounts: Map<String, Int> = words.groupingBy { it.level }.eachCount()
    val levelOf: Map<String, String> = words.associate { it.id to it.level }
    val search = WordSearch(words)
    private val sorted = ConcurrentHashMap<Lang, List<Word>>()

    /** Library order (level, then alphabet) in the headline language; computed once per language. */
    fun sorted(headline: Lang): List<Word> = sorted.getOrPut(headline) { WordSearch.sorted(words, headline) }

    companion object {
        val EMPTY = Library(emptyList())
    }
}

data class UiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
    val library: Library = Library.EMPTY,
    val current: Word? = null,
    val stats: Stats = Stats(0, 0, 0, 0),
    /** Whether our wallpaper is the phone's wallpaper (refreshed on resume). */
    val liveActive: Boolean = true,
) {
    val words: List<Word> get() = library.words
    val availableLevels: List<String> get() = library.levels
    val levelCounts: Map<String, Int> get() = library.levelCounts

    fun word(id: String): Word? = library.byId[id]

    /** Word used for previews before anything has been shown. */
    val previewWord: Word?
        get() = current ?: words.firstOrNull { it.level in settings.levels } ?: words.firstOrNull()

    val needsLiveSetup: Boolean
        get() = settings.onboarded && !liveActive
}

enum class WordFilter { ALL, HISTORY, FAVORITES, LEARNED }

/** What the word list shows; everything but the typed text lives here so it can be computed off the main thread. */
data class WordsQuery(val text: String = "", val filter: WordFilter = WordFilter.ALL, val levels: Set<String> = emptySet())

data class WordRow(val word: Word, val favorite: Boolean, val learned: Boolean, val current: Boolean, val shownAt: Long? = null)

data class WordsUi(
    val query: WordsQuery = WordsQuery(),
    val rows: List<WordRow> = emptyList(),
    /** Rows grouped under level headers (the whole library without a search), else null. */
    val groups: List<Pair<String, List<WordRow>>>? = null,
)

data class PacksUiState(
    val available: List<PackInfo>? = null,
    val installed: Map<String, InstalledPack> = emptyMap(),
    val loading: Boolean = false,
    val error: Boolean = false,
    /** Download progress 0..1 per pack id. */
    val installing: Map<String, Float> = emptyMap(),
)

sealed interface UiEvent {
    /** Take the user to the system screen where our wallpaper is confirmed. */
    data object OpenLivePicker : UiEvent
}

/** Renders card previews, remembering the last few so a screen that comes back shows its card at once. */
interface CardPreviews {
    fun cached(key: PreviewKey): ImageBitmap?
    suspend fun render(key: PreviewKey, word: Word, palette: Palette): ImageBitmap
}

data class PreviewKey(val wordId: String, val style: CardStyle, val paletteId: String, val width: Int, val height: Int, val lang: AppLanguage)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application), CardPreviews, Thumbnails {
    private val app: Application get() = getApplication()
    private val graph = Graph.get(application)
    private var speaker: Speaker? = null
    private var changing = false
    private val liveActive = MutableStateFlow(true)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    private val packsFetch = MutableStateFlow(PacksUiState())
    private val wordsQuery = MutableStateFlow(WordsQuery())

    /** String resource ids for one-off snackbar messages. */
    val messages: SharedFlow<Int> = _messages
    val events: SharedFlow<UiEvent> = _events

    /** Onboarding choices not saved yet; kept here so they survive a rotation. */
    var draft: Settings? by mutableStateOf(null)

    private val library: StateFlow<Library?> = graph.content.generation
        .mapLatest { Library(graph.content.words()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<UiState> = combine(
        graph.settings.flow,
        graph.progress.flow,
        library.filterNotNull(),
        liveActive,
    ) { settings, progress, library, live ->
        val now = System.currentTimeMillis()
        UiState(
            loading = false,
            settings = settings,
            progress = progress,
            library = library,
            current = progress.currentId?.let { library.byId[it] },
            stats = StatsCalculator.of(progress, settings.levels, library.levelOf, now, TimeZone.getDefault().getOffset(now).toLong()),
            liveActive = live,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** The word list for the Words tab, recomputed off the main thread when the query, the words or the progress change. */
    val words: StateFlow<WordsUi> = combine(
        wordsQuery,
        state.map { Triple(it.library, it.settings.headline, it.progress) }.distinctUntilChanged(),
    ) { query, (library, headline, progress) -> rows(query, library, headline, progress) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordsUi())

    val packs: StateFlow<PacksUiState> = combine(packsFetch, graph.packs.installed) { ui, installed ->
        ui.copy(installed = installed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacksUiState())

    private val previews = object : LruCache<PreviewKey, ImageBitmap>(PREVIEW_CACHE_KB) {
        override fun sizeOf(key: PreviewKey, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    private val memory = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) previews.evictAll()
        }
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = previews.evictAll()
    }

    init {
        application.registerComponentCallbacks(memory)
    }

    /** Called when the activity resumes: the user may have just confirmed the wallpaper. */
    fun refreshLiveStatus() {
        viewModelScope.launch(Dispatchers.IO) { liveActive.value = LiveCardWallpaper.isActive(app) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = graph.settings.current()
            val after = graph.settings.update(transform)
            if (after.onboarded) {
                if (after.intervalMinutes != before.intervalMinutes) {
                    WallpaperScheduler.syncWidgetRefresh(app, after.intervalMinutes, CardWidget.hasWidgets(app))
                }
                if (after.notifyDaily != before.notifyDaily || after.notifyMinute != before.notifyMinute) {
                    WallpaperScheduler.scheduleNotification(app, after)
                }
            }
            if (after != before) CardWidget.update(app)
        }
    }

    fun completeOnboarding(settings: Settings) {
        if (changing) return
        changing = true
        viewModelScope.launch {
            graph.settings.update { settings.copy(onboarded = true) }
            draft = null
            WallpaperScheduler.scheduleNotification(app, settings)
            try {
                report(graph.changer.changeToNext(), onboarding = true)
            } finally {
                changing = false
            }
        }
    }

    fun nextWord() = change { graph.changer.changeToNext() }

    /** Puts a word picked in the library on the wallpaper right away. */
    fun showWord(id: String) = change(shown = true) { graph.changer.show(id) }

    /** Small shared illustration for lists; null for words without one. */
    override fun thumbnail(word: Word): Bitmap? = graph.content.thumbnail(word)

    override fun cachedThumbnail(word: Word): Bitmap? = graph.content.cachedThumbnail(word)

    /** Switches the interface language in place (nothing restarts). */
    fun setAppLanguage(lang: AppLanguage) {
        if (AppLocale.current(app) == lang) return
        AppLocale.set(app, lang)
        CardWidget.update(app)
    }

    fun openLivePicker() {
        _events.tryEmit(UiEvent.OpenLivePicker)
    }

    fun livePickerMissing() {
        _messages.tryEmit(R.string.live_unsupported)
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            graph.progress.update { p -> p.copy(favorites = if (id in p.favorites) p.favorites - id else p.favorites + id) }
        }
    }

    fun toggleLearned(id: String) {
        viewModelScope.launch {
            graph.progress.update { p -> p.copy(learned = if (id in p.learned) p.learned - id else p.learned + id) }
        }
    }

    fun resetProgress() {
        viewModelScope.launch { graph.progress.update { Progress() } }
    }

    // --- word list -------------------------------------------------------------

    fun setWordsText(text: String) = wordsQuery.update { it.copy(text = text) }

    fun setWordsFilter(filter: WordFilter) = wordsQuery.update { it.copy(filter = filter) }

    fun toggleWordsLevel(level: String) = wordsQuery.update { q -> q.copy(levels = if (level in q.levels) q.levels - level else q.levels + level) }

    private fun rows(query: WordsQuery, library: Library, headline: Lang, progress: Progress): WordsUi {
        val found = if (query.text.isBlank()) null else library.search.search(query.text)
        fun row(word: Word, shownAt: Long? = null) = WordRow(
            word = word,
            favorite = word.id in progress.favorites,
            learned = word.id in progress.learned,
            current = word.id == progress.currentId,
            shownAt = shownAt,
        )
        val base: List<WordRow> = when (query.filter) {
            WordFilter.ALL -> (found ?: library.sorted(headline)).map { row(it) }
            WordFilter.HISTORY -> {
                val seen = LinkedHashMap<String, Long>()
                progress.history.forEach { e -> if (e.id !in seen) seen[e.id] = e.at }
                seen.mapNotNull { (id, at) -> library.byId[id]?.let { row(it, at) } }
            }
            WordFilter.FAVORITES -> WordSearch.sorted(progress.favorites.mapNotNull { library.byId[it] }, headline).map { row(it) }
            WordFilter.LEARNED -> WordSearch.sorted(progress.learned.mapNotNull { library.byId[it] }, headline).map { row(it) }
        }
        val hits = found?.mapTo(HashSet()) { it.id }
        val rows = base.filter { r -> (query.levels.isEmpty() || r.word.level in query.levels) && (hits == null || r.word.id in hits) }
        val grouped = query.filter == WordFilter.ALL && query.text.isBlank()
        return WordsUi(query, rows, if (grouped) rows.groupBy { it.word.level }.toList() else null)
    }

    // --- content packs ---------------------------------------------------------

    fun refreshPacks() {
        viewModelScope.launch {
            packsFetch.update { it.copy(loading = true, error = false) }
            val result = graph.packs.fetchManifest()
            packsFetch.update { it.copy(loading = false, available = result.getOrNull() ?: it.available, error = result.isFailure) }
        }
    }

    fun installPack(pack: PackInfo) {
        viewModelScope.launch {
            packsFetch.update { it.copy(installing = it.installing + (pack.id to 0f)) }
            val result = graph.packs.install(pack) { p -> packsFetch.update { it.copy(installing = it.installing + (pack.id to p)) } }
            packsFetch.update { it.copy(installing = it.installing - pack.id) }
            _messages.emit(if (result.isSuccess) R.string.packs_installed else R.string.packs_failed)
        }
    }

    fun removePack(id: String) {
        viewModelScope.launch { graph.packs.remove(id) }
    }

    // --- misc --------------------------------------------------------------------

    fun canSpeak(lang: Lang): Boolean = lang != Lang.TJ

    /** The speech engine is started on the first tap of a listen button, not with the app. */
    fun speak(text: String, lang: Lang) {
        val s = speaker ?: Speaker(app).also { speaker = it }
        s.speak(text, lang)
    }

    override fun cached(key: PreviewKey): ImageBitmap? = previews.get(key)

    override suspend fun render(key: PreviewKey, word: Word, palette: Palette): ImageBitmap =
        previews.get(key) ?: withContext(Dispatchers.Default) {
            graph.renderer.render(word, key.style, palette, key.width, key.height).asImageBitmap()
        }.also { if (key.width <= MAX_CACHED_WIDTH) previews.put(key, it) }

    /** Runs a word change; taps that arrive while one is running are dropped instead of queued. */
    private fun change(shown: Boolean = false, block: suspend () -> ChangeResult) {
        if (changing) return
        changing = true
        viewModelScope.launch {
            try {
                report(block(), shown = shown)
            } finally {
                changing = false
            }
        }
    }

    private suspend fun report(result: ChangeResult, shown: Boolean = false, onboarding: Boolean = false) {
        when (result) {
            is ChangeResult.Applied -> if (shown) _messages.emit(R.string.msg_shown)
            ChangeResult.NoWords -> _messages.emit(R.string.msg_no_words)
            // The Today banner already asks for it; only the end of onboarding goes to the system screen.
            ChangeResult.NeedsLiveSetup -> if (onboarding) _events.emit(UiEvent.OpenLivePicker) else if (shown) _messages.emit(R.string.live_inactive)
        }
    }

    override fun onCleared() {
        app.unregisterComponentCallbacks(memory)
        speaker?.shutdown()
    }

    private companion object {
        /** About four half-size previews. */
        const val PREVIEW_CACHE_KB = 12 * 1024
        /** Full-screen renders are shown once and not kept. */
        const val MAX_CACHED_WIDTH = 720
    }
}
