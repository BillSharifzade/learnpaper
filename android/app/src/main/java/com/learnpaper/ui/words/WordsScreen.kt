package com.learnpaper.ui.words

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnpaper.R
import com.learnpaper.content.Lang
import com.learnpaper.data.Settings
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.LocalizedContent
import com.learnpaper.ui.WordFilter
import com.learnpaper.ui.WordRow
import com.learnpaper.ui.components.ChoiceChip
import com.learnpaper.ui.components.EmptyState
import com.learnpaper.ui.components.LevelBadge
import com.learnpaper.ui.components.Thumbnails
import com.learnpaper.ui.components.WordDetail
import com.learnpaper.ui.components.WordThumb
import com.learnpaper.ui.components.levelName
import com.learnpaper.ui.components.rememberNow
import com.learnpaper.ui.theme.LpTheme

/**
 * The whole library. The list itself (search, filters, sorting over ~2,500 words) is computed by the view
 * model off the main thread; this screen only shows rows, so it opens and scrolls without work.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WordsScreen(settings: Settings, vm: AppViewModel, listState: LazyListState) {
    val ui by vm.words.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(ui.query.text) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val query = ui.query
    val progress = state.progress

    // Opening a word hides the keyboard, so it does not come back when the sheet closes.
    fun open(id: String) {
        focus.clearFocus()
        selectedId = id
    }

    LaunchedEffect(text) { vm.setWordsText(text) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() }
    LaunchedEffect(query.filter, query.levels) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Takes the focus Android hands out by itself: before Android 9 the system gives focus back to the
        // first focusable view after every clearFocus(), and Compose would pass it to the search field,
        // opening the keyboard while scrolling or switching tabs. Taps still focus the field directly.
        Spacer(Modifier.size(1.dp).focusable().clearAndSetSemantics {})
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.words_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Text(
                    pluralStringResource(R.plurals.words_count, ui.rows.size, ui.rows.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            SearchField(text, onChange = { text = it }, onDone = { focus.clearFocus() })
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordFilter.entries.forEach { f ->
                val on = query.filter == f
                ChoiceChip(
                    text = stringResource(
                        when (f) {
                            WordFilter.ALL -> R.string.words_filter_all
                            WordFilter.HISTORY -> R.string.words_filter_history
                            WordFilter.FAVORITES -> R.string.words_filter_favorites
                            WordFilter.LEARNED -> R.string.words_filter_learned
                        },
                    ),
                    selected = on,
                    onClick = { vm.setWordsFilter(f) },
                    leading = when (f) {
                        WordFilter.ALL -> null
                        WordFilter.HISTORY -> { { Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } }
                        WordFilter.FAVORITES -> { { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = if (on) MaterialTheme.colorScheme.onPrimary else LpTheme.extra.favorite) } }
                        WordFilter.LEARNED -> { { Icon(Icons.Rounded.TaskAlt, null, Modifier.size(18.dp), tint = if (on) MaterialTheme.colorScheme.onPrimary else LpTheme.extra.success) } }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableLevels.forEach { level ->
                ChoiceChip(text = level, selected = level in query.levels, onClick = { vm.toggleWordsLevel(level) })
            }
        }
        Spacer(Modifier.height(6.dp))

        if (ui.rows.isEmpty()) {
            // Before the first result arrives the list is simply empty; the message is for real empty results.
            if (!state.loading && ui.query.text == text) {
                EmptyState(
                    stringResource(
                        when {
                            query.text.isNotBlank() -> R.string.words_empty_search
                            query.filter == WordFilter.HISTORY -> R.string.words_empty_history
                            query.filter == WordFilter.FAVORITES -> R.string.words_empty_favorites
                            query.filter == WordFilter.LEARNED -> R.string.words_empty_learned
                            else -> R.string.words_empty_search
                        },
                    ),
                )
            }
        } else {
            val headline = settings.headline
            val translations = settings.translations
            val now = rememberNow()
            val groups = ui.groups
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (groups != null) {
                    groups.forEach { (level, group) ->
                        stickyHeader(key = "h-$level", contentType = "header") { LevelHeader(level, group.size) }
                        items(group, key = { it.word.id }, contentType = { "word" }) { row ->
                            WordRowItem(row, headline, translations, vm, now, onClick = { open(row.word.id) }, showLevel = false)
                        }
                    }
                } else {
                    items(ui.rows, key = { it.word.id }, contentType = { "word" }) { row ->
                        WordRowItem(row, headline, translations, vm, now, onClick = { open(row.word.id) })
                    }
                }
            }
        }
    }

    val selected = selectedId?.let { state.word(it) }
    if (selected != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            // The sheet is a window of its own: its texts need the interface language provided again.
            LocalizedContent {
                WordDetail(
                    word = selected,
                    settings = settings,
                    favorite = selected.id in progress.favorites,
                    learned = selected.id in progress.learned,
                    isCurrent = progress.currentId == selected.id,
                    thumbnails = vm,
                    canSpeak = vm::canSpeak,
                    speak = vm::speak,
                    onFavorite = { vm.toggleFavorite(selected.id) },
                    onLearned = { vm.toggleLearned(selected.id) },
                    onShow = { vm.showWord(selected.id) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.words_search_hint), style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.words_clear_search))
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun LevelHeader(level: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadge(level)
        Spacer(Modifier.width(10.dp))
        Text(levelName(level), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.words_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One word in the list. Takes only what it shows, so rows are skipped unless their own word changed. */
@Composable
private fun WordRowItem(
    row: WordRow,
    headline: Lang,
    translations: List<Lang>,
    thumbnails: Thumbnails,
    now: Long,
    onClick: () -> Unit,
    showLevel: Boolean = true,
) {
    val word = row.word
    val subtitle = remember(word, translations) { translations.joinToString("  ·  ") { word.entry(it).text } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (row.current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WordThumb(word, thumbnails, size = 46.dp, headline = headline)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                word.entry(headline).text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.learned) Icon(Icons.Rounded.TaskAlt, null, tint = LpTheme.extra.success, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                if (row.favorite) Icon(Icons.Rounded.Favorite, null, tint = LpTheme.extra.favorite, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                if (showLevel) {
                    Spacer(Modifier.width(4.dp))
                    LevelBadge(word.level)
                }
            }
            row.shownAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(relativeTime(it, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "just now", "5 min ago", "3 h ago", "yesterday", or a short date, in the interface language. */
@Composable
private fun relativeTime(at: Long, now: Long): String {
    val configuration = LocalConfiguration.current
    val diff = (now - at).coerceAtLeast(0L)
    val minutes = (diff / 60_000L).toInt()
    val hours = minutes / 60
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
        hours < 24 -> pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        hours < 48 -> stringResource(R.string.time_yesterday)
        else -> {
            val locale = configuration.locales[0]
            java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", locale))
        }
    }
}
