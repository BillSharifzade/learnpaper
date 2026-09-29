package com.learnpaper

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.learnpaper.i18n.AppLocale

class LearnPaperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocale.init(this)
        Graph.get(this).warmUp(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The per-app language may have been changed from the system settings (Android 13+).
        AppLocale.clearCache()
        AppLocale.invalidate(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) Graph.get(this).content.trimMemory()
    }
}
