package com.learnpaper.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.data.Settings
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.IntervalControls
import com.learnpaper.ui.components.LanguageControls
import com.learnpaper.ui.components.LayoutControls
import com.learnpaper.ui.components.LevelControls
import com.learnpaper.ui.components.PaletteControls
import com.learnpaper.ui.components.QuietHoursControls
import com.learnpaper.ui.components.SectionLabel
import com.learnpaper.ui.components.SwitchRow
import com.learnpaper.ui.components.TargetControls

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    val s = state.settings
    val update: (Settings) -> Unit = { new -> vm.updateSettings { current -> new.copy(onboarded = current.onboarded) } }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        Section(stringResource(R.string.settings_languages)) {
            LanguageControls(s, update)
        }
        Section(stringResource(R.string.settings_content)) {
            SectionLabel(stringResource(R.string.onb_level_title))
            LevelControls(s, state.availableLevels, update)
            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.settings_show_transcriptions), s.showTranscriptions) { update(s.copy(showTranscriptions = it)) }
            SwitchRow(stringResource(R.string.settings_show_examples), s.showExamples) { update(s.copy(showExamples = it)) }
        }
        Section(stringResource(R.string.settings_look)) {
            PaletteControls(s, update)
            Spacer(Modifier.height(20.dp))
            LayoutControls(s, update)
            Spacer(Modifier.height(20.dp))
            TargetControls(s, update)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_apply_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = vm::applyCurrent, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_apply_now))
            }
        }
        Section(stringResource(R.string.settings_schedule)) {
            IntervalControls(s, update)
            Spacer(Modifier.height(16.dp))
            QuietHoursControls(s, update)
        }
        Section(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { confirmReset = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_reset))
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.resetProgress(); confirmReset = false }) {
                    Text(stringResource(R.string.dialog_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
    )
    content()
    Spacer(Modifier.height(20.dp))
}
