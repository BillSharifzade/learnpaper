package com.learnpaper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.render.PosNames
import com.learnpaper.ui.theme.LpTheme

fun transcription(lang: Lang, value: String): String = if (lang == Lang.EN) "/$value/" else "[$value]"

/** Headword with transcription and a listen button; shared by Today and the word sheet. */
@Composable
fun Headword(
    word: Word,
    settings: Settings,
    canSpeak: (Lang) -> Boolean,
    speak: (String, Lang) -> Unit,
    big: Boolean = true,
) {
    val headline = word.entry(settings.headline)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                headline.text,
                style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            )
            if (headline.tr.isNotBlank()) {
                Text(transcription(settings.headline, headline.tr), style = LpTheme.transcription.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
            }
        }
        if (canSpeak(settings.headline)) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.action_speak),
                onClick = { speak(headline.text, settings.headline) },
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 48.dp,
            )
        }
    }
}

/** "RU  слово  [slóvo]  🔊" rows for every translation language. */
@Composable
fun TranslationRows(word: Word, settings: Settings, canSpeak: (Lang) -> Boolean, speak: (String, Lang) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        settings.translations.forEach { lang ->
            val e = word.entry(lang)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LangTag(lang)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.text, style = MaterialTheme.typography.titleLarge)
                    if (e.tr.isNotBlank() && settings.showTranscriptions) {
                        Text(transcription(lang, e.tr), style = LpTheme.transcription)
                    }
                }
                if (canSpeak(lang)) {
                    IconButton(onClick = { speak(e.text, lang) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = stringResource(R.string.action_speak),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The example sentence in the learned language, then its translations. */
@Composable
fun ExampleBlock(word: Word, settings: Settings) {
    val main = word.example.of(settings.headline)
    if (main.isBlank()) return
    Overline(stringResource(R.string.detail_example))
    Text(main, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
    settings.translations.forEach { lang ->
        val t = word.example.of(lang)
        if (t.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row {
                LangTag(lang, Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Favourite (heart that pops) and "I know it" toggles. */
@Composable
fun WordToggles(favorite: Boolean, learned: Boolean, onFavorite: () -> Unit, onLearned: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val pop by animateFloatAsState(if (favorite) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = 400f), label = "heart")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(
            icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onFavorite() },
            container = if (favorite) LpTheme.extra.favorite.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = if (favorite) LpTheme.extra.favorite else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { val k = 1f + 0.12f * pop * (1f - pop) * 4f; scaleX = k; scaleY = k },
        )
        CircleIconButton(
            icon = Icons.Rounded.TaskAlt,
            contentDescription = stringResource(if (learned) R.string.action_unlearned else R.string.action_learned),
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLearned() },
            container = if (learned) LpTheme.extra.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = if (learned) LpTheme.extra.success else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Everything about one word, as shown in the bottom sheet of the library. */
@Composable
fun WordDetail(
    word: Word,
    settings: Settings,
    favorite: Boolean,
    learned: Boolean,
    isCurrent: Boolean,
    busy: Boolean,
    thumbnail: (Word) -> android.graphics.Bitmap?,
    canSpeak: (Lang) -> Boolean,
    speak: (String, Lang) -> Unit,
    onFavorite: () -> Unit,
    onLearned: () -> Unit,
    onShow: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WordThumb(word, thumbnail, size = 64.dp, headline = settings.headline)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(word.level)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        PosNames.localized(context, word.pos),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WordToggles(favorite, learned, onFavorite, onLearned)
        }
        Spacer(Modifier.height(18.dp))
        Headword(word, settings, canSpeak, speak)
        Spacer(Modifier.height(18.dp))
        TranslationRows(word, settings, canSpeak, speak)
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        ExampleBlock(word, settings)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onShow,
            enabled = !busy && !isCurrent,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (isCurrent) R.string.msg_shown else R.string.action_show_on_wallpaper), style = MaterialTheme.typography.labelLarge)
        }
    }
}
