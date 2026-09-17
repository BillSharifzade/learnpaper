package com.learnpaper

import android.content.Context
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.PackRepository
import com.learnpaper.data.ProgressRepository
import com.learnpaper.data.SettingsRepository
import com.learnpaper.render.CardRenderer
import com.learnpaper.wallpaper.WallpaperApplier
import com.learnpaper.wallpaper.WallpaperChanger

/** Hand-rolled dependency graph. One instance per process, shared by UI, worker, widget and wallpaper service. */
class Graph private constructor(context: Context) {
    val content = ContentRepository(context)
    val packs = PackRepository(content)
    val settings = SettingsRepository(context)
    val progress = ProgressRepository(context)
    val renderer = CardRenderer(context, content)
    val applier = WallpaperApplier(context)
    val changer = WallpaperChanger(context, content, settings, progress, renderer, applier)

    companion object {
        @Volatile
        private var instance: Graph? = null

        fun get(context: Context): Graph =
            instance ?: synchronized(this) {
                instance ?: Graph(context.applicationContext).also { instance = it }
            }
    }
}
