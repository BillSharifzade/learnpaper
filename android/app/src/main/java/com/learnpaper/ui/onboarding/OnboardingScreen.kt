package com.learnpaper.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.data.WallpaperMode
import com.learnpaper.i18n.AppLanguage
import com.learnpaper.render.ScreenSize
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.CardPreview
import com.learnpaper.ui.components.IntervalControls
import com.learnpaper.ui.components.LanguageControls
import com.learnpaper.ui.components.LayoutControls
import com.learnpaper.ui.components.LevelControls
import com.learnpaper.ui.components.ModeControls
import com.learnpaper.ui.components.Overline
import com.learnpaper.ui.components.PaletteControls
import com.learnpaper.ui.components.QuietHoursControls
import com.learnpaper.ui.components.SegmentedControl
import com.learnpaper.ui.components.TargetControls
import com.learnpaper.ui.theme.Brand

private enum class Step { WELCOME, LANGUAGES, LEVEL, LOOK, SCHEDULE, READY }

@Composable
fun OnboardingScreen(state: UiState, vm: AppViewModel) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]
    var draft by remember { mutableStateOf(state.settings) }
    val previewWord = state.previewWord
    val context = LocalContext.current

    fun back() { if (stepIndex > 0) stepIndex-- }
    fun next() { if (stepIndex < Step.entries.lastIndex) stepIndex++ }

    BackHandler(enabled = step != Step.WELCOME) { back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // top bar: back + progress
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = step != Step.WELCOME, enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = ::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onb_back))
                    }
                }
            }
            val progress by animateFloatAsState((stepIndex + 1f) / Step.entries.size, spring(dampingRatio = 0.8f, stiffness = 300f), label = "progress")
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Brand.IrisLight, Brand.Iris))),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                "${stepIndex + 1}/${Step.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(tween(380)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(300, delayMillis = 60))
                val exit = slideOutHorizontally(tween(300)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(160))
                enter togetherWith exit
            },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                when (s) {
                    Step.WELCOME -> {
                        Hero(state, vm, draft, Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(22.dp))
                        Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.onb_welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Overline(stringResource(R.string.onb_app_language))
                        SegmentedControl(
                            items = AppLanguage.entries,
                            selected = vm.appLanguage(),
                            onSelect = { vm.setAppLanguage(context, it) },
                            label = { it.nativeName },
                        )
                    }
                    Step.LANGUAGES -> {
                        StepHeader(R.string.onb_languages_title, R.string.onb_languages_body)
                        LanguageControls(draft) { draft = it }
                        Spacer(Modifier.height(28.dp))
                        CardPreview(
                            settings = draft,
                            word = previewWord,
                            paletteIndex = 0,
                            render = vm::renderPreview,
                            modifier = Modifier.fillMaxWidth(0.44f).align(Alignment.CenterHorizontally),
                            corner = 24.dp,
                            showClock = true,
                        )
                    }
                    Step.LEVEL -> {
                        StepHeader(R.string.onb_level_title, R.string.onb_level_body)
                        LevelControls(draft, state.availableLevels, state.levelCounts) { draft = it }
                    }
                    Step.LOOK -> {
                        StepHeader(R.string.onb_look_title, R.string.onb_look_body)
                        CardPreview(
                            settings = draft,
                            word = previewWord,
                            paletteIndex = 0,
                            render = vm::renderPreview,
                            modifier = Modifier.fillMaxWidth(0.46f).align(Alignment.CenterHorizontally),
                            corner = 24.dp,
                            showClock = true,
                        )
                        Spacer(Modifier.height(24.dp))
                        PaletteControls(draft) { draft = it }
                        Spacer(Modifier.height(20.dp))
                        LayoutControls(draft) { draft = it }
                    }
                    Step.SCHEDULE -> {
                        StepHeader(R.string.onb_schedule_title, R.string.onb_schedule_body)
                        IntervalControls(draft) { draft = it }
                        Spacer(Modifier.height(16.dp))
                        QuietHoursControls(draft) { draft = it }
                        Spacer(Modifier.height(20.dp))
                        ModeControls(draft) { draft = it }
                        AnimatedVisibility(visible = draft.mode == WallpaperMode.STATIC) {
                            Column {
                                Spacer(Modifier.height(20.dp))
                                TargetControls(draft) { draft = it }
                            }
                        }
                    }
                    Step.READY -> {
                        StepHeader(
                            R.string.onb_ready_title,
                            if (draft.mode == WallpaperMode.LIVE) R.string.onb_ready_body_live else R.string.onb_ready_body,
                        )
                        CardPreview(
                            settings = draft,
                            word = previewWord,
                            paletteIndex = 0,
                            render = vm::renderPreview,
                            modifier = Modifier.fillMaxWidth(0.56f).align(Alignment.CenterHorizontally),
                            showClock = true,
                            float = true,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Button(
            onClick = { if (step == Step.READY) vm.completeOnboarding(draft) else next() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(58.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            if (state.busy && step == Step.READY) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(
                    stringResource(
                        when (step) {
                            Step.WELCOME -> R.string.onb_start
                            Step.READY -> R.string.onb_finish
                            else -> R.string.onb_next
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (step != Step.READY) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The logo made real: the live card floating on an apricot card, like the two cards of the icon. */
@Composable
private fun Hero(state: UiState, vm: AppViewModel, draft: com.learnpaper.data.Settings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val aspect = remember { ScreenSize.portrait(context).let { it.first.toFloat() / it.second } }
    Box(modifier.fillMaxWidth(0.5f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(aspect)
                .rotate(8f)
                .padding(start = 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Brand.Apricot, Brand.Apricot.copy(alpha = 0.75f)))),
        )
        CardPreview(
            settings = draft,
            word = state.previewWord,
            paletteIndex = 0,
            render = vm::renderPreview,
            modifier = Modifier.fillMaxWidth(0.86f).rotate(-4f),
            corner = 28.dp,
            showClock = true,
            float = true,
        )
    }
}

@Composable
private fun ColumnScope.StepHeader(titleRes: Int, bodyRes: Int?) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineLarge)
    if (bodyRes != null) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}
