package com.learnpaper.wallpaper

import android.app.WallpaperManager
import android.content.Context
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Word
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.Progress
import com.learnpaper.data.ProgressRepository
import com.learnpaper.data.Settings
import com.learnpaper.data.SettingsRepository
import com.learnpaper.data.WallpaperMode
import com.learnpaper.data.WallpaperTarget
import com.learnpaper.domain.Rotation
import com.learnpaper.render.CardRenderer
import com.learnpaper.render.Palettes
import com.learnpaper.render.ScreenSize
import com.learnpaper.widget.CardWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

sealed interface ChangeResult {
    data class Applied(val word: Word) : ChangeResult
    data object NoWords : ChangeResult
    /** Live mode is selected but our wallpaper service is not the active wallpaper yet. */
    data object NeedsLiveSetup : ChangeResult
    data object Failed : ChangeResult
}

/** Advances to the next word, renders it and puts it on screen. Used by the worker and the UI. */
class WallpaperChanger(
    private val context: Context,
    private val content: ContentRepository,
    private val settingsRepo: SettingsRepository,
    private val progressRepo: ProgressRepository,
    private val renderer: CardRenderer,
    private val applier: WallpaperApplier,
) {
    suspend fun changeToNext(): ChangeResult = withContext(Dispatchers.Default) {
        val settings = settingsRepo.current()
        val words = content.words()
        val now = System.currentTimeMillis()
        val progress = progressRepo.update {
            Rotation.advance(it, settings, words, now, TimeZone.getDefault().getOffset(now).toLong())
        }
        val word = progress.currentId?.let { id -> words.firstOrNull { it.id == id } } ?: return@withContext ChangeResult.NoWords
        apply(word, settings, progress)
    }

    /** Re-applies the current word (after a look change), or picks the first one if there is none yet. */
    suspend fun applyCurrent(): ChangeResult = withContext(Dispatchers.Default) {
        val settings = settingsRepo.current()
        val progress = progressRepo.current()
        val word = progress.currentId?.let { content.word(it) } ?: return@withContext changeToNext()
        apply(word, settings, progress)
    }

    private fun apply(word: Word, settings: Settings, progress: Progress): ChangeResult {
        CardWidget.update(context)
        return when (settings.mode) {
            // The live engine observes the progress and settings flows and redraws by itself.
            WallpaperMode.LIVE -> if (LiveCardWallpaper.isActive(context)) ChangeResult.Applied(word) else ChangeResult.NeedsLiveSetup
            WallpaperMode.STATIC -> applyStatic(word, settings, progress)
        }
    }

    /**
     * The lock screen always gets the LOCK layout (clock zone kept clear); the home screen gets
     * the layout the user chose. When both would be identical, one bitmap is set for both.
     */
    private fun applyStatic(word: Word, settings: Settings, progress: Progress): ChangeResult {
        if (!applier.isAllowed()) return ChangeResult.Failed
        val palette = Palettes.forSettings(settings, progress.paletteIndex)
        val (w, h) = ScreenSize.portrait(context)
        val lockSettings = settings.copy(layout = LayoutPreset.LOCK)

        fun set(renderWith: Settings, flags: Int): Boolean {
            val bitmap = renderer.render(word, renderWith, palette, w, h)
            return try {
                applier.apply(bitmap, flags)
            } finally {
                bitmap.recycle()
            }
        }

        val ok = when (settings.target) {
            WallpaperTarget.LOCK -> set(lockSettings, WallpaperManager.FLAG_LOCK)
            WallpaperTarget.HOME -> set(settings, WallpaperManager.FLAG_SYSTEM)
            WallpaperTarget.BOTH ->
                if (settings.layout == LayoutPreset.LOCK) {
                    set(settings, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } else {
                    set(settings, WallpaperManager.FLAG_SYSTEM) && set(lockSettings, WallpaperManager.FLAG_LOCK)
                }
        }
        return if (ok) ChangeResult.Applied(word) else ChangeResult.Failed
    }
}
