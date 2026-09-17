package com.learnpaper.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.content.Word
import com.learnpaper.data.HistoryEntry
import com.learnpaper.render.PosNames
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.SectionLabel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: UiState, vm: AppViewModel) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Word?>(null) }
    val entries = state.progress.history.filter { !favoritesOnly || it.id in state.progress.favorites }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(stringResource(R.string.history_favorites_only)) },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            )
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { "${it.id}-${it.at}" }) { entry ->
                    val word = state.word(entry.id)
                    if (word != null) {
                        HistoryRow(word, entry, state, vm) { selected = word }
                    }
                }
            }
        }
    }

    selected?.let { word ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            WordDetail(word, state, vm)
        }
    }
}

@Composable
private fun HistoryRow(word: Word, entry: HistoryEntry, state: UiState, vm: AppViewModel, onClick: () -> Unit) {
    val settings = state.settings
    val fav = word.id in state.progress.favorites
    val learned = word.id in state.progress.learned
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(word.entry(settings.headline).text, style = MaterialTheme.typography.titleMedium)
                Text(
                    settings.translations.joinToString("  ·  ") { word.entry(it).text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.toggleFavorite(word.id) }) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.toggleLearned(word.id) }) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_learned),
                    tint = if (learned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
fun WordDetail(word: Word, state: UiState, vm: AppViewModel) {
    val settings = state.settings
    val context = LocalContext.current
    val headline = word.entry(settings.headline)
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(headline.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (vm.canSpeak(settings.headline)) {
                IconButton(onClick = { vm.speak(headline.text, settings.headline) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_speak))
                }
            }
        }
        if (headline.tr.isNotBlank()) {
            Text(tr(settings.headline, headline.tr), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            listOf(stringResource(R.string.level_label, word.level), PosNames.localized(context, word.pos)).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        settings.translations.forEach { lang ->
            val e = word.entry(lang)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(lang.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.text, style = MaterialTheme.typography.titleMedium)
                    if (e.tr.isNotBlank()) {
                        Text(tr(lang, e.tr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (vm.canSpeak(lang)) {
                    IconButton(onClick = { vm.speak(e.text, lang) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_speak))
                    }
                }
            }
        }
        val example = word.example.of(settings.headline)
        if (example.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.detail_example))
            Text(example, style = MaterialTheme.typography.bodyLarge)
            settings.translations.forEach { lang ->
                val t = word.example.of(lang)
                if (t.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun tr(lang: Lang, value: String): String = if (lang == Lang.EN) "/$value/" else "[$value]"
