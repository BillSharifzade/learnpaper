package com.learnpaper.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnpaper.Graph
import com.learnpaper.MainActivity
import com.learnpaper.R
import com.learnpaper.content.Lang

/** Posts the current word once a day at the time chosen in settings. */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        val settings = graph.settings.current()
        if (!settings.onboarded || !settings.notifyDaily) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val progress = graph.progress.current()
        val word = progress.currentId?.let { graph.content.word(it) } ?: return Result.success()
        val headline = word.entry(settings.headline)
        val translations = settings.translations.joinToString("  ·  ") { word.entry(it).text }
        val example = if (settings.showExamples) word.example.of(settings.headline) else ""

        ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, 1, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headline.text + if (headline.tr.isNotBlank()) "   ${tr(settings.headline, headline.tr)}" else "")
            .setContentText(translations)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(translations, example).filter { it.isNotBlank() }.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setLargeIcon(graph.content.loadImage(word))
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(ID, notification)
        return Result.success()
    }

    private fun tr(lang: Lang, value: String) = if (lang == Lang.EN) "/$value/" else "[$value]"

    companion object {
        const val CHANNEL = "daily-word"
        private const val ID = 1

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }
}
