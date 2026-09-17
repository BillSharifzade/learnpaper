package com.learnpaper.render

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object ScreenSize {
    /** Full display size in pixels as (width, height), always portrait. */
    fun portrait(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
        return minOf(w, h) to maxOf(w, h)
    }
}
