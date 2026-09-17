package com.learnpaper.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.domain.Stats
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.CardPreview
import com.learnpaper.ui.components.formatMinutes
import com.learnpaper.ui.components.intervalLabel
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

@Composable
fun HomeScreen(state: UiState, vm: AppViewModel) {
    val settings = state.settings
    val word = state.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_schedule_every, intervalLabel(settings.intervalMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        nextChangeLine(state)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (state.needsLiveSetup) {
            LiveSetupBanner(onSetUp = vm::openLivePicker)
            Spacer(Modifier.height(16.dp))
        }
        StatsRow(state.stats)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth()) {
            CardPreview(
                settings = settings,
                word = word ?: state.previewWord,
                paletteIndex = state.progress.paletteIndex,
                render = vm::renderPreview,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (word != null) {
                    WordSummary(word, settings, vm)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fav = word.id in state.progress.favorites
                        FilledTonalIconButton(
                            onClick = { vm.toggleFavorite(word.id) },
                            colors = if (fav) IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiary,
                            ) else IconButtonDefaults.filledTonalIconButtonColors(),
                        ) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.action_favorite),
                            )
                        }
                        val learned = word.id in state.progress.learned
                        FilledTonalIconButton(
                            onClick = { vm.toggleLearned(word.id) },
                            colors = if (learned) IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.secondary,
                            ) else IconButtonDefaults.filledTonalIconButtonColors(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_learned))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.home_no_word),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = vm::nextWord, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_next_word))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = vm::applyCurrent, enabled = word != null && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_apply_again))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Shown while live mode is selected but our wallpaper service is not the active wallpaper. */
@Composable
private fun LiveSetupBanner(onSetUp: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.live_setup_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.live_setup_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSetUp) { Text(stringResource(R.string.live_setup_action)) }
        }
    }
}

@Composable
private fun StatsRow(stats: Stats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stats.streakDays.toString(), pluralStringResource(R.plurals.stat_streak, stats.streakDays), Modifier.weight(1f))
        StatTile(stats.wordsSeen.toString(), stringResource(R.string.stat_seen), Modifier.weight(1f))
        StatTile(stats.wordsLearned.toString(), stringResource(R.string.stat_learned), Modifier.weight(1f))
        StatTile(stats.dueToday.toString(), stringResource(R.string.stat_due), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun WordSummary(word: Word, settings: Settings, vm: AppViewModel) {
    val headline = word.entry(settings.headline)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(headline.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (vm.canSpeak(settings.headline)) {
            IconButton(onClick = { vm.speak(headline.text, settings.headline) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_speak))
            }
        }
    }
    if (headline.tr.isNotBlank()) {
        Text(
            if (settings.headline == Lang.EN) "/${headline.tr}/" else "[${headline.tr}]",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    settings.translations.forEach { lang ->
        val e = word.entry(lang)
        Text(
            "${lang.label}  ${e.text}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (e.tr.isNotBlank()) {
            Text(
                if (lang == Lang.EN) "/${e.tr}/" else "[${e.tr}]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun nextChangeLine(state: UiState): String? {
    val s = state.settings
    val last = state.progress.lastChangeAt
    if (last == 0L) return null
    val now = LocalTime.now()
    if (s.isQuiet(now.hour * 60 + now.minute)) {
        return stringResource(R.string.home_quiet_now, formatMinutes(s.quietEnd))
    }
    val next = last + s.intervalMinutes * 60_000L
    val text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(next))
    return stringResource(R.string.home_next_change, text)
}
