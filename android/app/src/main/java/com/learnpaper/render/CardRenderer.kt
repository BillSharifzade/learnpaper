package com.learnpaper.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.Settings
import kotlin.math.max

/**
 * Draws a word card onto a bitmap of the given size.
 *
 * All dimensions are expressed for a 1080 px wide canvas and scaled by `width / 1080`.
 * The content is a vertical stack of blocks centred inside a band that depends on the
 * layout preset (lower part of the screen for the lock screen, so the clock stays clear).
 * If the stack does not fit, everything is shrunk step by step.
 */
class CardRenderer(private val context: Context, private val content: ContentRepository) {

    fun render(word: Word, settings: Settings, palette: Palette, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = width / 1080f
        drawBackground(canvas, palette, width.toFloat(), height.toFloat(), s)

        val (bandTop, bandBottom) = when (settings.layout) {
            LayoutPreset.LOCK -> 0.30f to 0.90f
            LayoutPreset.HOME -> 0.16f to 0.88f
            LayoutPreset.COMPACT -> 0.58f to 0.92f
        }
        val top = bandTop * height
        val bottom = bandBottom * height
        val maxWidth = width - 2 * MARGIN * s
        val image = if (settings.layout == LayoutPreset.COMPACT) null else content.loadImage(word)

        var scale = 1f
        var blocks = buildBlocks(word, settings, palette, image, s, maxWidth)
        var total = blocks.sumOf { it.height.toDouble() }.toFloat()
        while (total > bottom - top && scale > 0.5f) {
            scale *= 0.92f
            blocks = buildBlocks(word, settings, palette, image, s * scale, maxWidth)
            total = blocks.sumOf { it.height.toDouble() }.toFloat()
        }

        var y = top + ((bottom - top) - total) / 2f
        val cx = width / 2f
        for (block in blocks) {
            block.draw(canvas, cx, y)
            y += block.height
        }
        image?.recycle()
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, palette: Palette, w: Float, h: Float, s: Float) {
        canvas.drawColor(palette.bg)
        val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.tile
            maskFilter = BlurMaskFilter(140f * s, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(w * 0.88f, h * 0.10f, 300f * s, deco)
        canvas.drawCircle(w * 0.10f, h * 0.96f, 340f * s, deco)
    }

    private fun buildBlocks(
        word: Word,
        settings: Settings,
        palette: Palette,
        image: Bitmap?,
        s: Float,
        maxWidth: Float,
    ): List<Block> {
        val blocks = ArrayList<Block>()
        val headline = word.entry(settings.headline)
        val translations = settings.translations
        val compact = settings.layout == LayoutPreset.COMPACT

        if (image != null) {
            blocks += ImageTile(image, size = 360f * s, radius = 64f * s, tileColor = palette.tile)
            blocks += Spacer(56f * s)
        }

        val headlineSize = fitSize(headline.text, 88f * s, 700, maxWidth, minSize = 40f * s)
        blocks += TextLine(headline.text, paint(headlineSize, 700, palette.text))
        if (settings.showTranscriptions && headline.tr.isNotBlank()) {
            blocks += Spacer(10f * s)
            blocks += TextLine(formatTr(settings.headline, headline.tr), paint(34f * s, 500, palette.muted))
        }
        blocks += Spacer(20f * s)
        val tag = listOf(word.level, PosNames.localized(context, word.pos)).filter { it.isNotBlank() }
            .joinToString("  ·  ").uppercase()
        blocks += Tag(tag, paint(22f * s, 600, palette.muted).apply { letterSpacing = 0.12f }, palette.tile, s)

        if (translations.isNotEmpty()) {
            blocks += Spacer(if (compact) 44f * s else 56f * s)
            translations.forEachIndexed { i, lang ->
                if (i > 0) blocks += Spacer(22f * s)
                val e = word.entry(lang)
                blocks += WordLine(
                    label = lang.label,
                    labelPaint = paint(22f * s, 700, palette.muted).apply { letterSpacing = 0.1f },
                    labelBg = palette.tile,
                    text = e.text,
                    textPaint = paint(fitSize(e.text, 46f * s, 600, maxWidth * 0.7f, minSize = 28f * s), 600, palette.text),
                    tr = if (settings.showTranscriptions && e.tr.isNotBlank()) formatTr(lang, e.tr) else null,
                    trPaint = paint(30f * s, 500, palette.muted),
                    gap = 18f * s,
                    maxWidth = maxWidth,
                    s = s,
                )
            }
        }

        if (settings.showExamples && !compact) {
            val main = word.example.of(settings.headline)
            if (main.isNotBlank()) {
                blocks += Spacer(48f * s)
                blocks += Divider(300f * s, 2f * s, withAlpha(palette.muted, 0.35f))
                blocks += Spacer(40f * s)
                blocks += Paragraph(main, paint(34f * s, 500, palette.text), maxWidth, 1.2f)
                for (lang in translations) {
                    val t = word.example.of(lang)
                    if (t.isBlank()) continue
                    blocks += Spacer(16f * s)
                    blocks += Paragraph(t, paint(28f * s, 400, palette.muted), maxWidth, 1.2f)
                }
            }
        }
        return blocks
    }

    private fun paint(size: Float, weight: Int, color: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.get(context, weight)
        textSize = size
        this.color = color
        isSubpixelText = true
    }

    private fun fitSize(text: String, start: Float, weight: Int, maxWidth: Float, minSize: Float): Float {
        var size = start
        val p = paint(size, weight, 0)
        while (size > minSize && p.measureText(text) > maxWidth) {
            size *= 0.94f
            p.textSize = size
        }
        return size
    }

    private fun formatTr(lang: Lang, tr: String): String = if (lang == Lang.EN) "/$tr/" else "[$tr]"

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)

    // --- blocks -----------------------------------------------------------------

    private interface Block {
        val height: Float
        fun draw(canvas: Canvas, cx: Float, y: Float)
    }

    private class Spacer(override val height: Float) : Block {
        override fun draw(canvas: Canvas, cx: Float, y: Float) = Unit
    }

    private class TextLine(private val text: String, private val paint: TextPaint) : Block {
        private val fm = paint.fontMetrics
        override val height = fm.descent - fm.ascent
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            canvas.drawText(text, cx - paint.measureText(text) / 2f, y - fm.ascent, paint)
        }
    }

    /** Small uppercase pill, e.g. "A1 · NOUN". */
    private class Tag(private val text: String, private val paint: TextPaint, private val bg: Int, s: Float) : Block {
        private val fm = paint.fontMetrics
        private val padX = 22f * s
        private val padY = 10f * s
        override val height = fm.descent - fm.ascent + 2 * padY
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            val textW = paint.measureText(text)
            val w = textW + 2 * padX
            canvas.drawRoundRect(RectF(cx - w / 2f, y, cx + w / 2f, y + height), height / 2f, height / 2f, fill)
            canvas.drawText(text, cx - textW / 2f, y + padY - fm.ascent, paint)
        }
    }

    /** "[RU] зонт [zont]" on one line, with the transcription wrapping below when it does not fit. */
    private class WordLine(
        private val label: String,
        private val labelPaint: TextPaint,
        labelBg: Int,
        private val text: String,
        private val textPaint: TextPaint,
        private val tr: String?,
        private val trPaint: TextPaint,
        private val gap: Float,
        maxWidth: Float,
        s: Float,
    ) : Block {
        private val labelPadX = 14f * s
        private val labelPadY = 8f * s
        private val labelFm = labelPaint.fontMetrics
        private val labelH = labelFm.descent - labelFm.ascent + 2 * labelPadY
        private val labelW = labelPaint.measureText(label) + 2 * labelPadX
        private val textW = textPaint.measureText(text)
        private val trW = tr?.let { trPaint.measureText(it) } ?: 0f
        private val oneLine = tr == null || labelW + gap + textW + gap + trW <= maxWidth
        private val textFm = textPaint.fontMetrics
        private val trFm = trPaint.fontMetrics
        private val ascent = max(-textFm.ascent, if (oneLine && tr != null) -trFm.ascent else 0f)
        private val descent = max(textFm.descent, if (oneLine && tr != null) trFm.descent else 0f)
        private val line1H = max(ascent + descent, labelH)
        private val line2Gap = 6f * s
        private val line2H = if (!oneLine && tr != null) (trFm.descent - trFm.ascent) + line2Gap else 0f
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelBg }
        override val height = line1H + line2H

        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            val total = labelW + gap + textW + if (oneLine && tr != null) gap + trW else 0f
            var x = cx - total / 2f
            val pillTop = y + (line1H - labelH) / 2f
            canvas.drawRoundRect(RectF(x, pillTop, x + labelW, pillTop + labelH), labelH / 2f, labelH / 2f, pill)
            canvas.drawText(label, x + labelPadX, pillTop + labelPadY - labelFm.ascent, labelPaint)
            x += labelW + gap
            val baseline = y + (line1H - (ascent + descent)) / 2f + ascent
            canvas.drawText(text, x, baseline, textPaint)
            if (tr != null) {
                if (oneLine) {
                    canvas.drawText(tr, x + textW + gap, baseline, trPaint)
                } else {
                    canvas.drawText(tr, cx - trW / 2f, y + line1H + line2Gap - trFm.ascent, trPaint)
                }
            }
        }
    }

    private class Paragraph(text: String, paint: TextPaint, private val width: Float, lineSpacing: Float) : Block {
        private val layout: StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        override val height = layout.height.toFloat()
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            canvas.save()
            canvas.translate(cx - width / 2f, y)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private class Divider(private val w: Float, thickness: Float, color: Int) : Block {
        override val height = thickness
        private val paint = Paint().apply { this.color = color }
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            canvas.drawRect(cx - w / 2f, y, cx + w / 2f, y + height, paint)
        }
    }

    private class ImageTile(
        private val bitmap: Bitmap,
        private val size: Float,
        private val radius: Float,
        tileColor: Int,
    ) : Block {
        override val height = size
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tileColor }
        private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            canvas.drawRoundRect(RectF(cx - size / 2f, y, cx + size / 2f, y + size), radius, radius, fill)
            val inner = size * 0.64f
            val dst = RectF(cx - inner / 2f, y + (size - inner) / 2f, cx + inner / 2f, y + (size + inner) / 2f)
            canvas.drawBitmap(bitmap, null, dst, img)
        }
    }

    companion object {
        private const val MARGIN = 96f
    }
}
