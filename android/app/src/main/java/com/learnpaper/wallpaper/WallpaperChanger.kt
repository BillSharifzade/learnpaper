package com.learnpaper.wallpaper

import android.content.Context
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Word
import com.learnpaper.data.ProgressRepository
import com.learnpaper.data.Settings
import com.learnpaper.data.SettingsRepository
import com.learnpaper.domain.Rotation
import com.learnpaper.domain.Schedule
import com.learnpaper.widget.CardWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.TimeZone

sealed interface ChangeResult {
    data class Applied(val word: Word) : ChangeResult
    data object NoWords : ChangeResult
    /** The word changed, but our wallpaper is not the phone's wallpaper, so only the app and widget show it. */
    data object NeedsLiveSetup : ChangeResult
}

/**
 * Moves the rotation on. Nothing is rendered or set here: the wallpaper engine and the widget observe the
 * progress and redraw themselves, so a change is just one small write.
 */
class WallpaperChanger(
    private val context: Context,
    private val content: ContentRepository,
    private val settingsRepo: SettingsRepository,
    private val progressRepo: ProgressRepository,
) {
    /** The next word, now (the "Next word" button, a swipe, the widget arrow). */
    suspend fun changeToNext(): ChangeResult = withContext(Dispatchers.Default) {
        val settings = settingsRepo.current()
        val words = content.words()
        val now = System.currentTimeMillis()
        val progress = progressRepo.update { Rotation.advance(it, settings, words, now, offset(now)) }
        val word = progress.currentId?.let { content.word(it) } ?: return@withContext ChangeResult.NoWords
        changed(word)
    }

    /** Shows a word the user picked (from the library) right away. */
    suspend fun show(id: String): ChangeResult = withContext(Dispatchers.Default) {
        val word = content.word(id) ?: return@withContext ChangeResult.NoWords
        val now = System.currentTimeMillis()
        progressRepo.update { Rotation.show(it, id, now, offset(now)) }
        changed(word)
    }

    /**
     * The automatic change: advances only if the schedule says it is due (checked and written atomically,
     * so the wallpaper on two screens and the widget can all ask without skipping words). Returns the new
     * word, or null when nothing was due.
     */
    suspend fun advanceIfDue(): Word? = withContext(Dispatchers.Default) {
        val settings = settingsRepo.current()
        if (!settings.onboarded) return@withContext null
        val words = content.words()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        var advanced = false
        val progress = progressRepo.update { p ->
            if (Schedule.isDue(settings, p.lastChangeAt, now, zone)) {
                advanced = true
                Rotation.advance(p, settings, words, now, offset(now))
            } else {
                p
            }
        }
        if (!advanced) return@withContext null
        CardWidget.update(context)
        progress.currentId?.let { content.word(it) }
    }

    /**
     * Keeps the rotation on schedule for as long as the caller runs it: the wallpaper engine does so only
     * while it is visible, so there is no timer at all while the screen is off. Waits until the next word
     * is due (at least one interval after the last change, never in quiet hours), changes it, and starts
     * over; a new interval or a manual change restarts the wait.
     */
    suspend fun followSchedule() {
        combine(settingsRepo.flow, progressRepo.flow.map { it.lastChangeAt }.distinctUntilChanged()) { s, last -> s to last }
            .distinctUntilChanged { a, b -> a.second == b.second && a.first.scheduleKey() == b.first.scheduleKey() }
            .collectLatest { (settings, last) ->
                if (!settings.onboarded) return@collectLatest
                val now = System.currentTimeMillis()
                val wait = Schedule.nextChangeAt(settings, last, now, ZoneId.systemDefault()) - now
                if (wait > 0) delay(wait)
                advanceIfDue()
            }
    }

    private fun Settings.scheduleKey() = listOf(onboarded, intervalMinutes, quietEnabled, quietStart, quietEnd)

    private fun changed(word: Word): ChangeResult {
        CardWidget.update(context)
        return if (LiveCardWallpaper.isActive(context)) ChangeResult.Applied(word) else ChangeResult.NeedsLiveSetup
    }

    private fun offset(now: Long): Long = TimeZone.getDefault().getOffset(now).toLong()
}
