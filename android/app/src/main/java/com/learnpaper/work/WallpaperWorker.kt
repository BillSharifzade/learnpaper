package com.learnpaper.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnpaper.Graph
import com.learnpaper.wallpaper.ChangeResult
import java.time.LocalTime

class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        val settings = graph.settings.current()
        if (!settings.onboarded) return Result.success()

        val now = LocalTime.now()
        if (settings.isQuiet(now.hour * 60 + now.minute)) return Result.success()

        return when (graph.changer.changeToNext()) {
            is ChangeResult.Applied, ChangeResult.NoWords -> Result.success()
            ChangeResult.Failed -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
