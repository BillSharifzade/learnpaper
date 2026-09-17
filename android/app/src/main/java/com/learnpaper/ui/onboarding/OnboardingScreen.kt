package com.learnpaper.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.CardPreview
import com.learnpaper.ui.components.IntervalControls
import com.learnpaper.ui.components.LanguageControls
import com.learnpaper.ui.components.LayoutControls
import com.learnpaper.ui.components.LevelControls
import com.learnpaper.ui.components.PaletteControls
import com.learnpaper.ui.components.QuietHoursControls
import com.learnpaper.ui.components.TargetControls

private enum class Step { WELCOME, LANGUAGES, LEVEL, LOOK, SCHEDULE, READY }

@Composable
fun OnboardingScreen(state: UiState, vm: AppViewModel) {
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var draft by remember { mutableStateOf(state.settings) }
    val previewWord = state.previewWord

    fun back() { if (step.ordinal > 0) step = Step.entries[step.ordinal - 1] }
    fun next() { if (step.ordinal < Step.entries.lastIndex) step = Step.entries[step.ordinal + 1] }

    BackHandler(enabled = step != Step.WELCOME) { back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Step.entries.forEach { s ->
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (s == step) 24.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (s.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when (step) {
                Step.WELCOME -> {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(28.dp))
                    CardPreview(
                        settings = draft,
                        word = previewWord,
                        paletteIndex = 0,
                        render = vm::renderPreview,
                        modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
                    )
                }
                Step.LANGUAGES -> {
                    StepHeader(R.string.onb_languages_title, R.string.onb_languages_body)
                    LanguageControls(draft) { draft = it }
                }
                Step.LEVEL -> {
                    StepHeader(R.string.onb_level_title, R.string.onb_level_body)
                    LevelControls(draft, state.availableLevels) { draft = it }
                }
                Step.LOOK -> {
                    StepHeader(R.string.onb_look_title, null)
                    CardPreview(
                        settings = draft,
                        word = previewWord,
                        paletteIndex = 0,
                        render = vm::renderPreview,
                        modifier = Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(24.dp))
                    PaletteControls(draft) { draft = it }
                    Spacer(Modifier.height(24.dp))
                    LayoutControls(draft) { draft = it }
                }
                Step.SCHEDULE -> {
                    StepHeader(R.string.onb_schedule_title, null)
                    IntervalControls(draft) { draft = it }
                    Spacer(Modifier.height(24.dp))
                    QuietHoursControls(draft) { draft = it }
                    Spacer(Modifier.height(24.dp))
                    TargetControls(draft) { draft = it }
                }
                Step.READY -> {
                    StepHeader(R.string.onb_ready_title, R.string.onb_ready_body)
                    CardPreview(
                        settings = draft,
                        word = previewWord,
                        paletteIndex = 0,
                        render = vm::renderPreview,
                        modifier = Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step != Step.WELCOME) {
                OutlinedButton(onClick = ::back, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onb_back))
                }
            }
            Button(
                onClick = { if (step == Step.READY) vm.completeOnboarding(draft) else next() },
                enabled = !state.busy,
                modifier = Modifier.weight(2f),
            ) {
                if (state.busy && step == Step.READY) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        stringResource(
                            when (step) {
                                Step.WELCOME -> R.string.onb_start
                                Step.READY -> R.string.onb_finish
                                else -> R.string.onb_next
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.StepHeader(titleRes: Int, bodyRes: Int?) {
    Spacer(Modifier.height(8.dp))
    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineMedium)
    if (bodyRes != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
}
