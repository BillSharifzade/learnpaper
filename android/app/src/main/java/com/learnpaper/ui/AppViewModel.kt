package com.learnpaper.ui

import android.app.Application
import android.graphics.Bitmap
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
import com.learnpaper.data.WallpaperMode
import com.learnpaper.domain.Stats
import com.learnpaper.domain.StatsCalculator
import com.learnpaper.render.Palettes
import com.learnpaper.wallpaper.ChangeResult
import com.learnpaper.wallpaper.LiveCardWallpaper
import com.learnpaper.wallpaper.Speaker
import com.learnpaper.work.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

data class UiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
    val words: List<Word> = emptyList(),
    /** Levels present in the content, in CEFR order. */
    val availableLevels: List<String> = emptyList(),
    val current: Word? = null,
    val stats: Stats = Stats(0, 0, 0, 0),
    /** Whether our live wallpaper service is the active wallpaper (refreshed on resume). */
    val liveActive: Boolean = false,
    val busy: Boolean = false,
) {
    fun word(id: String): Word? = words.firstOrNull { it.id == id }

    /** Word used for previews before anything has been applied. */
    val previewWord: Word?
        get() = current ?: words.firstOrNull { it.level in settings.levels } ?: words.firstOrNull()

    val needsLiveSetup: Boolean
        get() = settings.mode == WallpaperMode.LIVE && !liveActive
}

data class PacksUiState(
    val available: List<PackInfo>? = null,
    val installed: Map<String, InstalledPack> = emptyMap(),
    val loading: Boolean = false,
    val error: Boolean = false,
    /** Download progress 0..1 per pack id. */
    val installing: Map<String, Float> = emptyMap(),
)

sealed interface UiEvent {
    /** Take the user to the system screen where our live wallpaper is confirmed. */
    data object OpenLivePicker : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()
    private val graph = Graph.get(application)
    private val speaker = Speaker(application)
    private val busy = MutableStateFlow(false)
    private val liveActive = MutableStateFlow(LiveCardWallpaper.isActive(application))
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    private val packsFetch = MutableStateFlow(PacksUiState())

    /** String resource ids for one-off snackbar messages. */
    val messages: SharedFlow<Int> = _messages
    val events: SharedFlow<UiEvent> = _events

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val words = graph.content.generation.flatMapLatest { flow { emit(graph.content.words()) } }

    val state: StateFlow<UiState> = combine(
        graph.settings.flow,
        graph.progress.flow,
        words,
        busy,
        liveActive,
    ) { settings, progress, words, isBusy, live ->
        val now = System.currentTimeMillis()
        UiState(
            loading = false,
            settings = settings,
            progress = progress,
            words = words,
            availableLevels = ContentRepository.sortLevels(words.map { it.level }),
            current = progress.currentId?.let { id -> words.firstOrNull { it.id == id } },
            stats = StatsCalculator.of(
                progress, settings.levels, words.associate { it.id to it.level }, now, TimeZone.getDefault().getOffset(now).toLong(),
            ),
            liveActive = live,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val packs: StateFlow<PacksUiState> = combine(packsFetch, graph.packs.installed) { ui, installed ->
        ui.copy(installed = installed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacksUiState())

    /** Called when the activity resumes: the user may have just confirmed the live wallpaper. */
    fun refreshLiveStatus() {
        liveActive.value = LiveCardWallpaper.isActive(app)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = graph.settings.current()
            val after = graph.settings.update(transform)
            if (after.onboarded) {
                if (after.intervalMinutes != before.intervalMinutes) WallpaperScheduler.schedule(app, after.intervalMinutes)
                if (after.notifyDaily != before.notifyDaily || after.notifyMinute != before.notifyMinute) {
                    WallpaperScheduler.scheduleNotification(app, after)
                }
            }
        }
    }

    fun completeOnboarding(draft: Settings) = launchBusy {
        graph.settings.update { draft.copy(onboarded = true) }
        WallpaperScheduler.schedule(app, draft.intervalMinutes)
        WallpaperScheduler.scheduleNotification(app, draft)
        report(graph.changer.changeToNext())
    }

    fun nextWord() = launchBusy { report(graph.changer.changeToNext()) }

    fun applyCurrent() = launchBusy { report(graph.changer.applyCurrent()) }

    fun openLivePicker() {
        _events.tryEmit(UiEvent.OpenLivePicker)
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            graph.progress.update { p ->
                p.copy(favorites = if (id in p.favorites) p.favorites - id else p.favorites + id)
            }
        }
    }

    fun toggleLearned(id: String) {
        viewModelScope.launch {
            graph.progress.update { p ->
                p.copy(learned = if (id in p.learned) p.learned - id else p.learned + id)
            }
        }
    }

    fun resetProgress() {
        viewModelScope.launch { graph.progress.update { Progress() } }
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

    fun canSpeak(lang: Lang): Boolean = speaker.supports(lang)

    fun speak(text: String, lang: Lang) = speaker.speak(text, lang)

    /** Renders a card off the main thread for in-app previews. */
    suspend fun renderPreview(settings: Settings, word: Word, paletteIndex: Int, width: Int, height: Int): Bitmap =
        withContext(Dispatchers.Default) {
            graph.renderer.render(word, settings, Palettes.forSettings(settings, paletteIndex), width, height)
        }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun report(result: ChangeResult) {
        when (result) {
            is ChangeResult.Applied -> _messages.emit(R.string.msg_applied)
            ChangeResult.NoWords -> _messages.emit(R.string.msg_no_words)
            ChangeResult.Failed -> _messages.emit(R.string.msg_failed)
            ChangeResult.NeedsLiveSetup -> _events.emit(UiEvent.OpenLivePicker)
        }
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
