package com.learnpaper

import android.content.Context
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.PackRepository
import com.learnpaper.data.ProgressRepository
import com.learnpaper.data.SettingsRepository
import com.learnpaper.render.CardRenderer
import com.learnpaper.wallpaper.WallpaperChanger
import com.learnpaper.widget.CardWidget
import com.learnpaper.work.WallpaperScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency graph. One instance per process, shared by UI, worker, widget and wallpaper service. */
class Graph private constructor(context: Context) {
    /** Process-wide background work that must not die with a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val content = ContentRepository(context)
    val packs = PackRepository(content)
    val settings = SettingsRepository(context)
    val progress = ProgressRepository(context)
    val renderer = CardRenderer(context, content)
    val changer = WallpaperChanger(context, content, settings, progress)

    /**
     * Started with the process (by the app or by the wallpaper service): loads the words in the background so
     * the first frame of either finds them ready, and once after the update from 1.0 moves background work over.
     */
    fun warmUp(context: Context) {
        scope.launch {
            content.words()
            if (WallpaperScheduler.migrateOnce(context)) {
                WallpaperScheduler.syncWidgetRefresh(context, settings.current().intervalMinutes, CardWidget.hasWidgets(context))
            }
        }
    }

    companion object {
        @Volatile
        private var instance: Graph? = null

        fun get(context: Context): Graph =
            instance ?: synchronized(this) {
                instance ?: Graph(context.applicationContext).also { instance = it }
            }
    }
}
