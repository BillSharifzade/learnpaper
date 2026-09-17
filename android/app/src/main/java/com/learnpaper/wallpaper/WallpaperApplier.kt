package com.learnpaper.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap

class WallpaperApplier(private val context: Context) {

    fun isAllowed(): Boolean = WallpaperManager.getInstance(context).isSetWallpaperAllowed

    /** Sets [bitmap] for the screens in [flags] (WallpaperManager.FLAG_SYSTEM / FLAG_LOCK). */
    fun apply(bitmap: Bitmap, flags: Int): Boolean {
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isSetWallpaperAllowed) return false
        return runCatching { wm.setBitmap(bitmap, null, true, flags) }.isSuccess
    }
}
