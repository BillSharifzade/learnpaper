package com.learnpaper.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnpaper.R
import com.learnpaper.ui.LocalizedAlertDialog
import com.learnpaper.content.PackInfo
import com.learnpaper.data.Settings
import com.learnpaper.i18n.AppLanguage
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.LocalAppLanguage
import com.learnpaper.ui.PacksUiState
import com.learnpaper.ui.UiState
import com.learnpaper.ui.components.ActionRow
import com.learnpaper.ui.components.CardPreview
import com.learnpaper.ui.components.IntervalControls
import com.learnpaper.ui.components.LanguageControls
import com.learnpaper.ui.components.LayoutControls
import com.learnpaper.ui.components.LevelControls
import com.learnpaper.ui.components.LockClockControls
import com.learnpaper.ui.components.NotificationControls
import com.learnpaper.ui.components.Overline
import com.learnpaper.ui.components.PaletteControls
import com.learnpaper.ui.components.QuietHoursControls
import com.learnpaper.ui.components.SectionCard
import com.learnpaper.ui.components.SegmentedControl
import com.learnpaper.ui.components.ToggleRow
import com.learnpaper.ui.theme.LpTheme

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, scroll: ScrollState) {
    val s = state.settings
    val update: (Settings) -> Unit = { new -> vm.updateSettings { current -> new.copy(onboarded = current.onboarded) } }
    var confirmReset by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val packs by vm.packs.collectAsStateWithLifecycle()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        )

        SectionCard(title = stringResource(R.string.settings_app_language), icon = Icons.Rounded.Translate) {
            SegmentedControl(
                items = AppLanguage.entries,
                selected = LocalAppLanguage.current,
                onSelect = vm::setAppLanguage,
                label = { it.nativeName },
            )
        }

        SectionCard(title = stringResource(R.string.settings_learning), icon = Icons.Rounded.School) {
            LanguageControls(s, update)
            Spacer(Modifier.height(20.dp))
            Overline(stringResource(R.string.settings_levels))
            LevelControls(s, state.availableLevels, state.levelCounts, update)
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.settings_show_transcriptions), s.showTranscriptions, stringResource(R.string.settings_show_transcriptions_desc)) {
                update(s.copy(showTranscriptions = it))
            }
            ToggleRow(stringResource(R.string.settings_show_examples), s.showExamples, stringResource(R.string.settings_show_examples_desc)) {
                update(s.copy(showExamples = it))
            }
        }

        SectionCard(title = stringResource(R.string.settings_appearance), icon = Icons.Rounded.Palette) {
            Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
                CardPreview(
                    settings = s,
                    word = state.current ?: state.previewWord,
                    paletteIndex = state.progress.paletteIndex,
                    previews = vm,
                    modifier = Modifier.fillMaxWidth(0.42f),
                    corner = 22.dp,
                    showClock = true,
                )
            }
            PaletteControls(s, update)
            Spacer(Modifier.height(18.dp))
            LayoutControls(s, update)
            Spacer(Modifier.height(18.dp))
            LockClockControls(s, update)
        }

        SectionCard(title = stringResource(R.string.settings_wallpaper), icon = Icons.Rounded.Wallpaper) {
            Text(stringResource(R.string.wallpaper_how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.liveActive) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (state.liveActive) LpTheme.extra.success else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (state.liveActive) R.string.live_active else R.string.live_inactive),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (!state.liveActive) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = vm::openLivePicker, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.extraLarge) {
                    Text(stringResource(R.string.live_setup_action))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_schedule), icon = Icons.Rounded.Schedule) {
            IntervalControls(s, update)
            Spacer(Modifier.height(14.dp))
            QuietHoursControls(s, update)
        }

        SectionCard(title = stringResource(R.string.settings_notifications), icon = Icons.Rounded.Notifications) {
            NotificationControls(s) { new ->
                if (new.notifyDaily && !s.notifyDaily && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                update(new)
            }
        }

        SectionCard(title = stringResource(R.string.settings_packs), icon = Icons.Rounded.CloudDownload) {
            PacksSection(packs, state, vm)
        }

        SectionCard(title = stringResource(R.string.settings_about), icon = Icons.Rounded.Info) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        buildAnnotatedString {
                            append("Learn")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Paper") }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    )
                    Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ActionRow(stringResource(R.string.settings_licenses), icon = Icons.AutoMirrored.Rounded.Article) { showLicenses = true }
            ActionRow(stringResource(R.string.settings_reset), icon = Icons.Rounded.RestartAlt) { confirmReset = true }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmReset) {
        LocalizedAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { vm.resetProgress(); confirmReset = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.dialog_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.dialog_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }
    if (showLicenses) LicensesDialog { showLicenses = false }
}

/** Third-party licences bundled in assets/licenses. */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        val names = context.assets.list("licenses")?.sorted().orEmpty()
        names.joinToString("\n\n") { name ->
            "— ${name.substringBeforeLast('.').replace('-', ' ')} —\n\n" +
                context.assets.open("licenses/$name").bufferedReader().use { it.readText().trim() }
        }
    }
    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

/** Downloadable packs: what the manifest offers, what is installed, and download progress. */
@Composable
private fun PacksSection(packs: PacksUiState, state: UiState, vm: AppViewModel) {
    val lang = state.settings.headline
    Text(stringResource(R.string.packs_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val available = packs.available
    val shown: List<PackInfo> = available ?: packs.installed.values.map { i -> PackInfo(id = i.id, version = i.version, words = i.words, url = "") }
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
                            pluralStringResource(R.plurals.words_count, pack.words, pack.words),
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
                    progress != null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = vm::refreshPacks, enabled = !packs.loading, shape = MaterialTheme.shapes.extraLarge) {
            Text(stringResource(R.string.packs_check))
        }
        if (packs.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
