package com.learnpaper.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnpaper.content.Word
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.LockStyle
import com.learnpaper.data.Settings
import com.learnpaper.render.Palettes
import com.learnpaper.render.ScreenSize
import com.learnpaper.ui.theme.LpTheme
import kotlin.math.roundToInt

/**
 * The real wallpaper for [word] with [settings], rendered off the main thread at half resolution and
 * shown in a phone-shaped frame. A new word or look cross-fades in. [showClock] draws a lock-screen
 * clock where the system would put it, so the user sees why the card sits low.
 */
@Composable
fun CardPreview(
    settings: Settings,
    word: Word?,
    paletteIndex: Int,
    render: suspend (Settings, Word, Int, Int, Int) -> Bitmap,
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    showClock: Boolean = false,
    float: Boolean = false,
    /** Render width in pixels; previews use half the usual phone width, full-screen views the real one. */
    renderWidth: Int = PREVIEW_WIDTH,
) {
    val context = LocalContext.current
    val screen = remember { ScreenSize.portrait(context) }
    val aspect = screen.first.toFloat() / screen.second
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(settings, word?.id, paletteIndex) {
        if (word != null) {
            val w = renderWidth
            val h = (w / aspect).roundToInt()
            image = render(settings, word, paletteIndex, w, h).asImageBitmap()
        }
    }

    val lift = if (float) {
        val t = rememberInfiniteTransition(label = "float")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lift").value
    } else 0f
    val density = LocalDensity.current
    val shape = RoundedCornerShape(corner)
    val palette = Palettes.forSettings(settings, paletteIndex)

    val description = word?.let { w ->
        (listOf(settings.headline) + settings.translations).joinToString(" — ") { w.entry(it).text }
    }
    Box(
        modifier
            .semantics { if (description != null) contentDescription = description }
            .graphicsLayer { translationY = -with(density) { 6.dp.toPx() } * lift }
            .aspectRatio(aspect)
            .shadow(elevation = 22.dp + 6.dp * lift, shape = shape, ambientColor = LpTheme.extra.cardShadow, spotColor = LpTheme.extra.cardShadow)
            .clip(shape)
            .background(Color(palette.bg))
            .border(1.dp, Color.Black.copy(alpha = 0.06f), shape),
    ) {
        Crossfade(targetState = image, animationSpec = tween(450), label = "preview") { img ->
            if (img != null) {
                Image(bitmap = img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Color(palette.bg)))
            }
        }
        // Only phones with a small clock at the top get the full card on the lock screen; with a large
        // centred clock (Pixel) the lock screen shows a compact card, so the preview skips the clock.
        if (showClock && settings.layout != LayoutPreset.HOME && settings.lockStyle.resolve() == LockStyle.TOP_CLOCK) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val size = (maxWidth.value * 0.22f).sp
                Text(
                    "09:41",
                    color = Color(palette.text).copy(alpha = 0.9f),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = size, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.1f),
                )
            }
        }
    }
}

/** Preview frame placeholder of the same proportions (e.g. while the first word loads). */
@Composable
fun PreviewPlaceholder(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val screen = remember { ScreenSize.portrait(context) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(screen.first.toFloat() / screen.second)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private const val PREVIEW_WIDTH = 540
