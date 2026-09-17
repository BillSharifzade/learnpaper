package com.learnpaper.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnpaper.Graph
import com.learnpaper.R
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.Progress
import com.learnpaper.data.Settings
import com.learnpaper.render.Palettes
import com.learnpaper.wallpaper.ChangeResult
import com.learnpaper.wallpaper.Speaker
import com.learnpaper.work.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
    val words: List<Word> = emptyList(),
    /** Levels present in the bundled content, in CEFR order. */
    val availableLevels: List<String> = emptyList(),
    val current: Word? = null,
    val busy: Boolean = false,
) {
    fun word(id: String): Word? = words.firstOrNull { it.id == id }

    /** Word used for previews before anything has been applied. */
    val previewWord: Word?
        get() = current ?: words.firstOrNull { it.level in settings.levels } ?: words.firstOrNull()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()
    private val graph = Graph.get(application)
    private val speaker = Speaker(application)
    private val busy = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** String resource ids for one-off snackbar messages. */
    val messages: SharedFlow<Int> = _messages

    val state: StateFlow<UiState> = combine(
        graph.settings.flow,
        graph.progress.flow,
        flow { emit(graph.content.words()) },
        busy,
    ) { settings, progress, words, isBusy ->
        UiState(
            loading = false,
            settings = settings,
            progress = progress,
            words = words,
            availableLevels = ContentRepository.sortLevels(words.map { it.level }),
            current = progress.currentId?.let { id -> words.firstOrNull { it.id == id } },
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = graph.settings.current()
            val after = graph.settings.update(transform)
            if (after.onboarded && after.intervalMinutes != before.intervalMinutes) {
                WallpaperScheduler.schedule(app, after.intervalMinutes)
            }
        }
    }

    fun completeOnboarding(draft: Settings) = launchBusy {
        graph.settings.update { draft.copy(onboarded = true) }
        WallpaperScheduler.schedule(app, draft.intervalMinutes)
        report(graph.changer.changeToNext())
    }

    fun nextWord() = launchBusy { report(graph.changer.changeToNext()) }

    fun applyCurrent() = launchBusy { report(graph.changer.applyCurrent()) }

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
        _messages.emit(
            when (result) {
                is ChangeResult.Applied -> R.string.msg_applied
                ChangeResult.NoWords -> R.string.msg_no_words
                ChangeResult.Failed -> R.string.msg_failed
            },
        )
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
