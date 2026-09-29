package com.learnpaper.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnpaper.Graph

/**
 * Keeps home-screen widgets moving (scheduled only while a widget exists, see [WallpaperScheduler]): changes
 * the word if it is due, which also refreshes the widgets. The wallpaper itself never needs this.
 */
class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.get(applicationContext).changer.advanceIfDue()
        return Result.success()
    }
}
