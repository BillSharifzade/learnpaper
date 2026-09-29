package com.learnpaper.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.content.Word
import com.learnpaper.domain.Schedule
import com.learnpaper.render.PosNames
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.LocalizedContent
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.CardPreview
import com.learnpaper.ui.components.ExampleBlock
import com.learnpaper.ui.components.Headword
import com.learnpaper.ui.components.LevelBadge
import com.learnpaper.ui.components.SectionCard
import com.learnpaper.ui.components.StatTile
import com.learnpaper.ui.components.TranslationRows
import com.learnpaper.ui.components.WordToggles
import com.learnpaper.ui.components.formatMinutes
import com.learnpaper.ui.components.intervalLabel
import com.learnpaper.ui.components.rememberNow
import com.learnpaper.ui.theme.Brand
import com.learnpaper.ui.theme.LpTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(state: UiState, vm: AppViewModel, scroll: ScrollState) {
    val settings = state.settings
    val word = state.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        TopBar(streak = state.stats.streakDays)

        AnimatedVisibility(visible = state.needsLiveSetup, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LiveSetupBanner(onSetUp = vm::openLivePicker, modifier = Modifier.padding(bottom = 16.dp))
        }

        var fullScreen by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            SwipeCard(onSwiped = vm::nextWord, onTap = { fullScreen = true }, modifier = Modifier.fillMaxWidth(0.5f)) {
                CardPreview(
                    settings = settings,
                    word = word ?: state.previewWord,
                    paletteIndex = state.progress.paletteIndex,
                    previews = vm,
                    showClock = true,
                )
            }
        }
        if (fullScreen) {
            FullScreenPreview(state, vm) { fullScreen = false }
        }
        Spacer(Modifier.height(18.dp))
        ScheduleChip(state, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            targetState = word,
            transitionSpec = {
                (fadeIn(tween(320, delayMillis = 60)) + slideInVertically(tween(380)) { it / 10 } + scaleIn(tween(380), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it / 14 })
            },
            contentKey = { it?.id },
            label = "word",
        ) { w ->
            if (w != null) WordPanel(w, state, vm) else NoWordYet()
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (word != null) {
                WordToggles(
                    favorite = word.id in state.progress.favorites,
                    learned = word.id in state.progress.learned,
                    onFavorite = { vm.toggleFavorite(word.id) },
                    onLearned = { vm.toggleLearned(word.id) },
                )
                Spacer(Modifier.width(12.dp))
            }
            Button(
                onClick = vm::nextWord,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_next_word), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(state.stats.wordsSeen.toString(), stringResource(R.string.stat_seen))
            StatTile(state.stats.wordsLearned.toString(), stringResource(R.string.stat_learned), accent = LpTheme.extra.success)
            StatTile(state.stats.dueToday.toString(), stringResource(R.string.stat_due), accent = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        WeekStrip(state.progress.activeDays)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TopBar(streak: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("Learn") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Paper") }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        )
        Spacer(Modifier.weight(1f))
        androidx.compose.animation.AnimatedVisibility(visible = streak > 0, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(LpTheme.extra.streak.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = LpTheme.extra.streak, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    pluralStringResource(R.plurals.stat_streak, streak, streak),
                    style = MaterialTheme.typography.labelLarge,
                    color = LpTheme.extra.streak,
                )
            }
        }
    }
}

/**
 * "Next word at 14:00 · every 1 h", the quiet-hours note at night, or, once the next word is due while the
 * app is open, that it will appear when the user goes back to the wallpaper (words change only there).
 */
@Composable
private fun ScheduleChip(state: UiState, modifier: Modifier = Modifier) {
    val s = state.settings
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(now).atZone(zone)
    val quiet = s.isQuiet(time.hour * 60 + time.minute)
    val every = stringResource(R.string.today_every, intervalLabel(s.intervalMinutes))
    val text = when {
        quiet -> stringResource(R.string.today_quiet_now, formatMinutes(s.quietEnd))
        state.progress.lastChangeAt == 0L || state.needsLiveSetup -> every.replaceFirstChar { it.uppercase() }
        else -> {
            val next = Schedule.nextChangeAt(s, state.progress.lastChangeAt, now, zone)
            if (next <= now) {
                stringResource(R.string.today_next_on_leave)
            } else {
                stringResource(R.string.today_next_change, Instant.ofEpochMilli(next).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))) +
                    "  ·  " + every
            }
        }
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (quiet) Icons.Rounded.NightsStay else Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WordPanel(word: Word, state: UiState, vm: AppViewModel) {
    val settings = state.settings
    val context = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(word.level)
            Spacer(Modifier.width(8.dp))
            Text(PosNames.localized(context, word.pos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Headword(word, settings, vm::canSpeak, vm::speak)
        if (settings.translations.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TranslationRows(word, settings, vm::canSpeak, vm::speak)
        }
        if (settings.showExamples && word.example.of(settings.headline).isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            ExampleBlock(word, settings)
        }
    }
}

@Composable
private fun NoWordYet() {
    SectionCard {
        Text(
            stringResource(R.string.home_no_word),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown while live mode is selected but our wallpaper service is not the active wallpaper. */
@Composable
private fun LiveSetupBanner(onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Brand.IrisLight, Brand.IrisDeep)))
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.live_setup_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.live_setup_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSetUp,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.IrisDeep),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(stringResource(R.string.live_setup_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}


/** The hero card: drag sideways to get the next word (it tilts with the finger and springs away), tap to see it full size. */
@Composable
private fun SwipeCard(
    onSwiped: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 90.dp.toPx() }
    Box(
        modifier
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 40f
                alpha = 1f - (kotlin.math.abs(offset.value) / (threshold * 4)).coerceIn(0f, 0.5f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (kotlin.math.abs(offset.value) > threshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val out = if (offset.value > 0) size.width * 1.2f else -size.width * 1.2f
                                offset.animateTo(out, tween(180))
                                onSwiped()
                                offset.snapTo(-out * 0.6f)
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.6f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) { content() }
}

/** The card at full size, as it looks on the phone; tap anywhere to close. */
@Composable
private fun FullScreenPreview(state: UiState, vm: AppViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LocalizedContent { FullScreenCard(state, vm, onDismiss) }
    }
}

@Composable
private fun FullScreenCard(state: UiState, vm: AppViewModel, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CardPreview(
            settings = state.settings,
            word = state.current ?: state.previewWord,
            paletteIndex = state.progress.paletteIndex,
            previews = vm,
            modifier = Modifier.fillMaxWidth(),
            corner = 36.dp,
            showClock = true,
            renderWidth = LocalContext.current.resources.displayMetrics.widthPixels.coerceAtMost(1440),
        )
    }
}

/** The last seven days as dots: filled on days a word was shown. */
@Composable
private fun WeekStrip(activeDays: List<Long>) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val active = remember(activeDays) { activeDays.toSet() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val on = day.toEpochDay() in active
            val isToday = back == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).take(2).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (on) LpTheme.extra.streak else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
