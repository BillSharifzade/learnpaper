package com.learnpaper.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnpaper.R
import com.learnpaper.content.PackInfo
import com.learnpaper.data.Settings
import com.learnpaper.data.WallpaperMode
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.PacksUiState
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.IntervalControls
import com.learnpaper.ui.components.LanguageControls
import com.learnpaper.ui.components.LayoutControls
import com.learnpaper.ui.components.LevelControls
import com.learnpaper.ui.components.ModeControls
import com.learnpaper.ui.components.NotificationControls
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
    val packs by vm.packs.collectAsStateWithLifecycle()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        Section(stringResource(R.string.settings_packs)) {
            PacksSection(packs, state, vm)
        }
        Section(stringResource(R.string.settings_look)) {
            PaletteControls(s, update)
            Spacer(Modifier.height(20.dp))
            LayoutControls(s, update)
            Spacer(Modifier.height(20.dp))
            ModeControls(s, update)
            if (s.mode == WallpaperMode.STATIC) {
                Spacer(Modifier.height(20.dp))
                TargetControls(s, update)
            }
            Spacer(Modifier.height(16.dp))
            if (s.mode == WallpaperMode.LIVE) {
                Text(
                    stringResource(if (state.liveActive) R.string.live_active else R.string.live_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = vm::openLivePicker, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.live_setup_action))
                }
            } else {
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
        }
        Section(stringResource(R.string.settings_schedule)) {
            IntervalControls(s, update)
            Spacer(Modifier.height(16.dp))
            QuietHoursControls(s, update)
        }
        Section(stringResource(R.string.settings_notifications)) {
            NotificationControls(s) { new ->
                if (new.notifyDaily && !s.notifyDaily && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                update(new)
            }
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

/** Downloadable packs: what the manifest offers, what is installed, and download progress. */
@Composable
private fun PacksSection(packs: PacksUiState, state: UiState, vm: AppViewModel) {
    val lang = state.settings.headline
    Text(
        stringResource(R.string.packs_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val available = packs.available
    val shown: List<PackInfo> = available ?: packs.installed.values.map { i ->
        PackInfo(id = i.id, version = i.version, words = i.words, url = "")
    }
    shown.forEach { pack ->
        val installed = packs.installed[pack.id]
        val progress = packs.installing[pack.id]
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.displayName(lang), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            pack.levels.joinToString(", ").takeIf { it.isNotBlank() },
                            stringResource(R.string.packs_words, pack.words),
                            when {
                                installed == null -> null
                                installed.version < pack.version -> stringResource(R.string.packs_update_available)
                                else -> stringResource(R.string.packs_installed_label)
                            },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    progress != null -> CircularProgressIndicator(Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                    installed == null && pack.url.isNotBlank() ->
                        FilledTonalButton(onClick = { vm.installPack(pack) }) { Text(stringResource(R.string.packs_download)) }
                    installed != null && installed.version < pack.version && pack.url.isNotBlank() ->
                        FilledTonalButton(onClick = { vm.installPack(pack) }) { Text(stringResource(R.string.packs_update)) }
                    installed != null ->
                        OutlinedButton(onClick = { vm.removePack(pack.id) }) { Text(stringResource(R.string.packs_remove)) }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (available != null && available.isEmpty()) {
        Text(stringResource(R.string.packs_none), style = MaterialTheme.typography.bodyMedium)
    }
    if (packs.error) {
        Text(stringResource(R.string.packs_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = vm::refreshPacks, enabled = !packs.loading) {
            Text(stringResource(R.string.packs_check))
        }
        if (packs.loading) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
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
