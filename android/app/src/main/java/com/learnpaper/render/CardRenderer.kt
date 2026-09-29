package com.learnpaper.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.Settings
import com.learnpaper.i18n.AppLocale
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything about a card's look that comes from the settings. Also the key of cached previews. */
data class CardStyle(
    val headline: Lang,
    val translations: List<Lang>,
    val showTranscriptions: Boolean,
    val showExamples: Boolean,
    val layout: LayoutPreset,
)

fun Settings.cardStyle(layout: LayoutPreset = this.layout): CardStyle =
    CardStyle(headline, translations, showTranscriptions, showExamples, layout)

/**
 * Lays out and draws a word card.
 *
 * All dimensions are expressed for a 1080 px wide canvas and scaled by `width / 1080`.
 * The content is a vertical stack of blocks centred inside a band that depends on the
 * layout preset (lower part of the screen for the lock screen, so the clock stays clear).
 * If the stack does not fit, everything is shrunk step by step.
 *
 * [layout] does the measuring once; the resulting [Card] draws onto any canvas, including the
 * hardware canvas of the wallpaper surface, so the wallpaper never keeps a screen-sized bitmap.
 */
class CardRenderer(private val context: Context, private val content: ContentRepository) {

    /** A card laid out for one size. Cheap to keep (text layouts and one small illustration); [release] frees the illustration. */
    class Card internal constructor(
        val width: Int,
        val height: Int,
        private val background: List<Pair<Paint, Shape>>,
        private val blocks: List<Block>,
        private val top: Float,
        private val image: Bitmap?,
    ) {
        fun draw(canvas: Canvas) {
            for ((paint, shape) in background) {
                when (shape) {
                    is Shape.Full -> canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    is Shape.Disc -> canvas.drawCircle(shape.cx, shape.cy, shape.r, paint)
                }
            }
            var y = top
            val cx = width / 2f
            for (block in blocks) {
                block.draw(canvas, cx, y)
                y += block.height
            }
        }

        fun release() {
            image?.recycle()
        }
    }

    internal sealed interface Shape {
        data object Full : Shape
        data class Disc(val cx: Float, val cy: Float, val r: Float) : Shape
    }

    fun layout(word: Word, style: CardStyle, palette: Palette, width: Int, height: Int): Card {
        val s = width / 1080f
        val (bandTop, bandBottom) = when (style.layout) {
            LayoutPreset.LOCK -> 0.30f to 0.90f
            LayoutPreset.HOME -> 0.16f to 0.88f
            LayoutPreset.COMPACT -> 0.58f to 0.92f
            // Below a large centred lock-screen clock and above the unlock affordance.
            LayoutPreset.UNDER_CLOCK -> 0.725f to 0.835f
        }
        val top = bandTop * height
        val bottom = bandBottom * height
        val maxWidth = width - 2 * MARGIN * s
        // Decoded at the size it is drawn (the tile's inner square), not at the source's 512 px.
        val image = if (style.layout.compact) null else content.loadImageSized(word, targetPx = (TILE * IMAGE_SHARE * s).roundToInt())

        var scale = 1f
        var blocks = buildBlocks(word, style, palette, image, s, maxWidth)
        var total = blocks.sumOf { it.height.toDouble() }.toFloat()
        while (total > bottom - top && scale > 0.5f) {
            scale *= 0.92f
            blocks = buildBlocks(word, style, palette, image, s * scale, maxWidth)
            total = blocks.sumOf { it.height.toDouble() }.toFloat()
        }
        return Card(width, height, background(palette, width.toFloat(), height.toFloat(), s), blocks, top + ((bottom - top) - total) / 2f, image)
    }

    /** The card as a bitmap (in-app previews). */
    fun render(word: Word, style: CardStyle, palette: Palette, width: Int, height: Int): Bitmap {
        val card = layout(word, style, palette, width, height)
        return try {
            createBitmap(width, height).also { card.draw(Canvas(it)) }
        } finally {
            card.release()
        }
    }

    /**
     * A soft vertical gradient with two glowing discs. The discs are radial gradients rather than blurred
     * circles: a blur is computed on the CPU pixel by pixel, a gradient is one cheap shader on any canvas.
     */
    private fun background(palette: Palette, w: Float, h: Float, s: Float): List<Pair<Paint, Shape>> {
        val base = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, palette.bg, mix(palette.bg, palette.tile, 0.55f), Shader.TileMode.CLAMP)
        }
        return listOf(
            base to Shape.Full,
            glow(w * 0.90f, h * 0.08f, radius = 320f * s, softness = 160f * s, palette.tile),
            glow(w * 0.06f, h * 0.97f, radius = 360f * s, softness = 160f * s, withAlpha(palette.accent, 0.10f)),
        )
    }

    /** A disc of [radius] whose edge fades out over [softness] on both sides, like a blurred circle. */
    private fun glow(cx: Float, cy: Float, radius: Float, softness: Float, color: Int): Pair<Paint, Shape> {
        val outer = radius + softness
        val inner = ((radius - softness) / outer).coerceIn(0f, 1f)
        val alpha = (color ushr 24) / 255f
        // Smoothstep-like falloff from the solid core to transparent.
        val stops = floatArrayOf(0f, inner, inner + (1f - inner) * 0.25f, inner + (1f - inner) * 0.5f, inner + (1f - inner) * 0.75f, 1f)
        val colors = floatArrayOf(1f, 1f, 0.84f, 0.5f, 0.16f, 0f).map { withAlpha(color, alpha * it) }.toIntArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, outer, colors, stops, Shader.TileMode.CLAMP)
        }
        return paint to Shape.Disc(cx, cy, outer)
    }

    private fun buildBlocks(
        word: Word,
        style: CardStyle,
        palette: Palette,
        image: Bitmap?,
        s: Float,
        maxWidth: Float,
    ): List<Block> {
        val blocks = ArrayList<Block>()
        val headline = word.entry(style.headline)
        val translations = style.translations
        val compact = style.layout.compact

        if (image != null) {
            blocks += ImageTile(image, size = TILE * s, radius = 88f * s, tileColor = palette.tile)
            blocks += Spacer(56f * s)
        } else if (!compact) {
            // Abstract words have no illustration: a drop-cap tile keeps the rhythm of the card.
            val letter = headline.text.trim().take(1).uppercase()
            if (letter.isNotBlank()) {
                blocks += Monogram(letter, paint(150f * s, 800, palette.accent), size = 250f * s, radius = 72f * s, tileColor = palette.tile)
                blocks += Spacer(56f * s)
            }
        }

        val headlineSize = fitSize(headline.text, 96f * s, 700, maxWidth, minSize = 40f * s)
        blocks += TextLine(headline.text, paint(headlineSize, 700, palette.text).apply { letterSpacing = -0.015f })
        if (style.showTranscriptions && headline.tr.isNotBlank()) {
            blocks += Spacer(12f * s)
            blocks += TextLine(formatTr(style.headline, headline.tr), paint(34f * s, 450, palette.muted, Fonts.INTER))
        }
        if (style.layout != LayoutPreset.UNDER_CLOCK) {
            blocks += Spacer(20f * s)
            val tag = listOf(word.level, PosNames.localized(AppLocale.localized(context), word.pos)).filter { it.isNotBlank() }
                .joinToString("  ·  ").uppercase()
            blocks += Tag(tag, paint(22f * s, 600, palette.muted).apply { letterSpacing = 0.12f }, palette.tile, s)
        }

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
                    tr = if (style.showTranscriptions && e.tr.isNotBlank()) formatTr(lang, e.tr) else null,
                    trPaint = paint(30f * s, 450, palette.muted, Fonts.INTER),
                    gap = 18f * s,
                    maxWidth = maxWidth,
                    s = s,
                )
            }
        }

        if (style.showExamples && !compact) {
            val main = word.example.of(style.headline)
            if (main.isNotBlank()) {
                blocks += Spacer(48f * s)
                blocks += Divider(300f * s, 2f * s, withAlpha(palette.muted, 0.35f))
                blocks += Spacer(40f * s)
                blocks += Paragraph(main, paint(36f * s, 500, palette.text), maxWidth, 1.22f)
                for (lang in translations) {
                    val t = word.example.of(lang)
                    if (t.isBlank()) continue
                    blocks += Spacer(16f * s)
                    blocks += Paragraph(t, paint(29f * s, 400, palette.muted), maxWidth, 1.22f)
                }
            }
        }
        return blocks
    }

    private fun paint(size: Float, weight: Int, color: Int, font: String = Fonts.ONEST): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.get(context, weight, font)
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

    /** Linear blend of two opaque colours; t = 0 gives [a]. */
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun lerp(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(ch(a, 16), ch(b, 16)) shl 16) or (lerp(ch(a, 8), ch(b, 8)) shl 8) or lerp(ch(a, 0), ch(b, 0))
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24)

    // --- blocks -----------------------------------------------------------------

    internal interface Block {
        val height: Float
        fun draw(canvas: Canvas, cx: Float, y: Float)
    }

    private class Spacer(override val height: Float) : Block {
        override fun draw(canvas: Canvas, cx: Float, y: Float) = Unit
    }

    private class TextLine(private val text: String, private val paint: TextPaint) : Block {
        private val fm = paint.fontMetrics
        private val width = paint.measureText(text)
        override val height = fm.descent - fm.ascent
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            canvas.drawText(text, cx - width / 2f, y - fm.ascent, paint)
        }
    }

    /** Small uppercase pill, e.g. "A1 · NOUN". */
    private class Tag(private val text: String, private val paint: TextPaint, bg: Int, s: Float) : Block {
        private val fm = paint.fontMetrics
        private val padX = 22f * s
        private val padY = 10f * s
        private val textW = paint.measureText(text)
        override val height = fm.descent - fm.ascent + 2 * padY
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        private val rect = RectF()
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            val w = textW + 2 * padX
            rect.set(cx - w / 2f, y, cx + w / 2f, y + height)
            canvas.drawRoundRect(rect, height / 2f, height / 2f, fill)
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
        private val rect = RectF()
        override val height = line1H + line2H

        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            val total = labelW + gap + textW + if (oneLine && tr != null) gap + trW else 0f
            var x = cx - total / 2f
            val pillTop = y + (line1H - labelH) / 2f
            rect.set(x, pillTop, x + labelW, pillTop + labelH)
            canvas.drawRoundRect(rect, labelH / 2f, labelH / 2f, pill)
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
            canvas.withTranslation(cx - width / 2f, y) { layout.draw(this) }
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
        private val rect = RectF()
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            rect.set(cx - size / 2f, y, cx + size / 2f, y + size)
            canvas.drawRoundRect(rect, radius, radius, fill)
            val inner = size * IMAGE_SHARE
            rect.set(cx - inner / 2f, y + (size - inner) / 2f, cx + inner / 2f, y + (size + inner) / 2f)
            canvas.drawBitmap(bitmap, null, rect, img)
        }
    }

    /** Rounded tile with one large letter, used instead of an illustration. */
    private class Monogram(
        private val letter: String,
        private val paint: TextPaint,
        private val size: Float,
        private val radius: Float,
        tileColor: Int,
    ) : Block {
        override val height = size
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tileColor }
        private val bounds = Rect().also { paint.getTextBounds(letter, 0, letter.length, it) }
        private val letterW = paint.measureText(letter)
        private val rect = RectF()
        override fun draw(canvas: Canvas, cx: Float, y: Float) {
            rect.set(cx - size / 2f, y, cx + size / 2f, y + size)
            canvas.drawRoundRect(rect, radius, radius, fill)
            val baseline = y + size / 2f + bounds.height() / 2f - bounds.bottom
            canvas.drawText(letter, cx - letterW / 2f, baseline, paint)
        }
    }

    private val LayoutPreset.compact: Boolean
        get() = this == LayoutPreset.COMPACT || this == LayoutPreset.UNDER_CLOCK

    companion object {
        private const val MARGIN = 96f
        /** Illustration tile edge, and the share of it the illustration covers. */
        private const val TILE = 360f
        private const val IMAGE_SHARE = 0.64f
    }
}
