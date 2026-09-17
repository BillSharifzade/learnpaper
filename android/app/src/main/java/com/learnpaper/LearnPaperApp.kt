package com.learnpaper

import android.app.Application

class LearnPaperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.get(this)
    }
}
