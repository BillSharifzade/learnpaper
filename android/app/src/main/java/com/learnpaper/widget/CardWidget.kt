package com.learnpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import com.learnpaper.Graph
import com.learnpaper.MainActivity
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.i18n.AppLocale
import com.learnpaper.render.Palette
import com.learnpaper.render.Palettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget showing the current card: illustration (or drop-cap tile), word, transcription,
 * translations and the example, in the wallpaper's palette. The arrow button shows the next word.
 */
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val pending = goAsync()
            scope.launch {
                try {
                    // In live mode the wallpaper redraws by itself; the changer also refreshes this widget.
                    Graph.get(context).changer.changeToNext()
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_NEXT = "com.learnpaper.widget.NEXT"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Refreshes every placed widget; called after each wallpaper change. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CardWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context, manager, ids) }
        }

        private suspend fun render(base: Context, manager: AppWidgetManager, ids: IntArray) {
            val context = AppLocale.localized(base)
            val graph = Graph.get(base)
            val settings = graph.settings.current()
            val progress = graph.progress.current()
            val word = progress.currentId?.let { graph.content.word(it) }
            val palette = Palettes.forSettings(settings, progress.paletteIndex)
            val views = RemoteViews(base.packageName, R.layout.widget_card)
            style(views, palette)
            if (word == null) {
                views.setTextViewText(R.id.widget_word, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_tr, "")
                views.setTextViewText(R.id.widget_translations, context.getString(R.string.home_no_word))
                views.setTextViewText(R.id.widget_example, "")
                views.setImageViewBitmap(R.id.widget_image, null)
                views.setTextViewText(R.id.widget_monogram, "ā")
            } else {
                fill(base, views, word, settings)
            }
            val open = PendingIntent.getActivity(
                base, 0, Intent(base, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            val next = PendingIntent.getBroadcast(
                base, 1, Intent(base, CardWidget::class.java).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_next, next)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun style(views: RemoteViews, palette: Palette) {
            // A little lighter (or, on dark palettes, lighter by a touch) than the wallpaper, so the card
            // reads as a surface on top of it instead of melting into it.
            views.setInt(R.id.widget_bg, "setColorFilter", mixWithWhite(palette.bg, if (palette.isDark) 0.08f else 0.5f))
            views.setInt(R.id.widget_tile, "setColorFilter", palette.tile)
            views.setInt(R.id.widget_next, "setColorFilter", palette.muted)
            views.setTextColor(R.id.widget_word, palette.text)
            views.setTextColor(R.id.widget_tr, palette.muted)
            views.setTextColor(R.id.widget_translations, palette.text)
            views.setTextColor(R.id.widget_example, palette.muted)
            views.setTextColor(R.id.widget_monogram, palette.accent)
        }

        private fun fill(context: Context, views: RemoteViews, word: Word, settings: Settings) {
            val graph = Graph.get(context)
            val headline = word.entry(settings.headline)
            views.setTextViewText(R.id.widget_word, headline.text)
            views.setTextViewText(R.id.widget_tr, if (settings.showTranscriptions) tr(settings.headline, headline.tr) else "")
            views.setTextViewText(
                R.id.widget_translations,
                settings.translations.joinToString("   ") { lang -> "${lang.label}  ${word.entry(lang).text}" },
            )
            views.setTextViewText(R.id.widget_example, if (settings.showExamples) word.example.of(settings.headline) else "")
            val image = graph.content.loadImage(word, sampleSize = 2)
            if (image != null) {
                views.setImageViewBitmap(R.id.widget_image, image.scaleTo(160))
                views.setViewVisibility(R.id.widget_monogram, View.GONE)
                image.recycle()
            } else {
                views.setImageViewBitmap(R.id.widget_image, null)
                views.setViewVisibility(R.id.widget_monogram, View.VISIBLE)
                views.setTextViewText(R.id.widget_monogram, headline.text.take(1).uppercase())
            }
        }

        private fun Bitmap.scaleTo(size: Int): Bitmap = if (width == size) copy(config ?: Bitmap.Config.ARGB_8888, false) else scale(size, size)

        private fun mixWithWhite(color: Int, t: Float): Int {
            fun ch(shift: Int) = (color shr shift) and 0xFF
            fun lift(c: Int) = (c + (255 - c) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (lift(ch(16)) shl 16) or (lift(ch(8)) shl 8) or lift(ch(0))
        }

        private fun tr(lang: Lang, value: String): String =
            if (value.isBlank()) "" else if (lang == Lang.EN) "/$value/" else "[$value]"
    }
}
