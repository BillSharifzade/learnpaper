package com.learnpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.learnpaper.Graph
import com.learnpaper.MainActivity
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.render.Palettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home screen widget showing the current card: illustration, word, transcription and translations. */
class CardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Refreshes every placed widget; called after each wallpaper change. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CardWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val graph = Graph.get(context)
            val settings = graph.settings.current()
            val progress = graph.progress.current()
            val word = progress.currentId?.let { graph.content.word(it) }
            val palette = Palettes.forSettings(settings, progress.paletteIndex)
            val views = RemoteViews(context.packageName, R.layout.widget_card)
            views.setInt(R.id.widget_bg, "setColorFilter", palette.bg)
            views.setTextColor(R.id.widget_word, palette.text)
            views.setTextColor(R.id.widget_tr, palette.muted)
            views.setTextColor(R.id.widget_translations, palette.text)
            views.setTextColor(R.id.widget_example, palette.muted)
            if (word == null) {
                views.setTextViewText(R.id.widget_word, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_tr, "")
                views.setTextViewText(R.id.widget_translations, context.getString(R.string.home_no_word))
                views.setTextViewText(R.id.widget_example, "")
                views.setImageViewBitmap(R.id.widget_image, null)
            } else {
                fill(context, views, word, settings)
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun fill(context: Context, views: RemoteViews, word: Word, settings: Settings) {
            val graph = Graph.get(context)
            val headline = word.entry(settings.headline)
            views.setTextViewText(R.id.widget_word, headline.text)
            views.setTextViewText(R.id.widget_tr, if (settings.showTranscriptions) tr(settings.headline, headline.tr) else "")
            views.setTextViewText(
                R.id.widget_translations,
                settings.translations.joinToString("   ") { lang -> "${lang.label} ${word.entry(lang).text}" },
            )
            views.setTextViewText(R.id.widget_example, if (settings.showExamples) word.example.of(settings.headline) else "")
            val image = graph.content.loadImage(word)
            views.setImageViewBitmap(R.id.widget_image, image?.let { android.graphics.Bitmap.createScaledBitmap(it, 192, 192, true) })
            image?.recycle()
        }

        private fun tr(lang: Lang, value: String): String =
            if (value.isBlank()) "" else if (lang == Lang.EN) "/$value/" else "[$value]"
    }
}
