package com.learnpaper.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.render.ScreenSize
import kotlin.math.roundToInt

/** Live preview of the wallpaper for [word] with [settings], rendered at half resolution. */
@Composable
fun CardPreview(
    settings: Settings,
    word: Word?,
    paletteIndex: Int,
    render: suspend (Settings, Word, Int, Int, Int) -> Bitmap,
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
) {
    val context = LocalContext.current
    val screen = remember { ScreenSize.portrait(context) }
    val aspect = screen.first.toFloat() / screen.second
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(settings, word?.id, paletteIndex) {
        if (word != null) {
            val w = PREVIEW_WIDTH
            val h = (w / aspect).roundToInt()
            image = render(settings, word, paletteIndex, w, h).asImageBitmap()
        }
    }

    Box(
        modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        image?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private const val PREVIEW_WIDTH = 540
