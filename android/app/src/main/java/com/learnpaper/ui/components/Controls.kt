package com.learnpaper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.Settings
import com.learnpaper.data.WallpaperMode
import com.learnpaper.data.WallpaperTarget
import com.learnpaper.render.Palette
import com.learnpaper.render.Palettes
import com.learnpaper.ui.theme.SectionLabelStyle
import java.util.Locale

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SectionLabelStyle, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun langName(lang: Lang): String = stringResource(
    when (lang) {
        Lang.EN -> R.string.lang_en
        Lang.RU -> R.string.lang_ru
        Lang.TJ -> R.string.lang_tj
    },
)

@Composable
fun intervalLabel(minutes: Int): String = when {
    minutes >= 1440 -> stringResource(R.string.interval_daily)
    minutes >= 60 -> stringResource(R.string.interval_hours, minutes / 60)
    else -> stringResource(R.string.interval_minutes, minutes)
}

fun formatMinutes(minutesOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", (minutesOfDay / 60) % 24, minutesOfDay % 60)

/** Headline language + translation languages. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_learning))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.forEach { lang ->
            FilterChip(
                selected = settings.headline == lang,
                onClick = {
                    if (settings.headline != lang) {
                        onChange(settings.copy(headline = lang, shown = Lang.entries.filter { it != lang }.toSet()))
                    }
                },
                label = { Text(langName(lang)) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.label_show_translations))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.filter { it != settings.headline }.forEach { lang ->
            FilterChip(
                selected = lang in settings.shown,
                onClick = {
                    val next = if (lang in settings.shown) settings.shown - lang else settings.shown + lang
                    onChange(settings.copy(shown = next))
                },
                label = { Text(langName(lang)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelControls(settings: Settings, available: List<String>, onChange: (Settings) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ContentRepository.LEVEL_ORDER.take(4).forEach { level ->
            val enabled = level in available
            val selected = level in settings.levels
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = {
                    val next = if (selected) settings.levels - level else settings.levels + level
                    if (next.isNotEmpty()) onChange(settings.copy(levels = next))
                },
                label = {
                    Text(if (enabled) level else "$level · ${stringResource(R.string.level_coming_soon)}")
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_palette))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Palettes.all.forEach { p ->
            PaletteSwatch(
                palette = p,
                selected = !settings.rotatePalette && settings.paletteId == p.id,
                onClick = { onChange(settings.copy(paletteId = p.id, rotatePalette = false)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = stringResource(R.string.palette_rotate),
        checked = settings.rotatePalette,
        onChecked = { onChange(settings.copy(rotatePalette = it)) },
    )
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(palette.bg))
                .border(
                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(palette.text)))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(palette.nameRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun LayoutControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_layout))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LayoutPreset.entries.forEach { preset ->
            LayoutOption(
                preset = preset,
                selected = settings.layout == preset,
                onClick = { onChange(settings.copy(layout = preset)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.layout_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LayoutOption(preset: LayoutPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (title, desc) = when (preset) {
        LayoutPreset.LOCK -> R.string.layout_lock to R.string.layout_lock_desc
        LayoutPreset.HOME -> R.string.layout_home to R.string.layout_home_desc
        LayoutPreset.COMPACT -> R.string.layout_compact to R.string.layout_compact_desc
    }
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(9f / 18f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .border(border, RoundedCornerShape(12.dp)),
        ) {
            // schematic of where the content sits
            val (top, height) = when (preset) {
                LayoutPreset.LOCK -> 0.32f to 0.56f
                LayoutPreset.HOME -> 0.18f to 0.68f
                LayoutPreset.COMPACT -> 0.60f to 0.30f
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 18f)
                    .padding(horizontal = 10.dp),
            ) {
                Spacer(Modifier.weight(top))
                Column(
                    Modifier.weight(height).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (preset != LayoutPreset.COMPACT) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.tertiary))
                        Spacer(Modifier.height(5.dp))
                    }
                    Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onTertiaryContainer))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)))
                    if (preset != LayoutPreset.COMPACT) {
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)))
                    }
                }
                Spacer(Modifier.weight((1f - top - height).coerceAtLeast(0.01f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_interval))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Settings.INTERVALS.forEach { minutes ->
            FilterChip(
                selected = settings.intervalMinutes == minutes,
                onClick = { onChange(settings.copy(intervalMinutes = minutes)) },
                label = { Text(intervalLabel(minutes)) },
            )
        }
    }
}

@Composable
fun QuietHoursControls(settings: Settings, onChange: (Settings) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.label_quiet),
        subtitle = stringResource(R.string.quiet_desc),
        checked = settings.quietEnabled,
        onChecked = { onChange(settings.copy(quietEnabled = it)) },
    )
    if (settings.quietEnabled) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HourStepper(
                label = stringResource(R.string.quiet_from),
                minutes = settings.quietStart,
                onChange = { onChange(settings.copy(quietStart = it)) },
                modifier = Modifier.weight(1f),
            )
            HourStepper(
                label = stringResource(R.string.quiet_to),
                minutes = settings.quietEnd,
                onChange = { onChange(settings.copy(quietEnd = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun HourStepper(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(Math.floorMod(minutes - 60, 1440)) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(formatMinutes(minutes), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(Math.floorMod(minutes + 60, 1440)) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/** Live (redraw in place) versus static (WallpaperManager.setBitmap) delivery. */
@Composable
fun ModeControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_mode))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WallpaperMode.entries.forEach { mode ->
            val (title, desc) = when (mode) {
                WallpaperMode.LIVE -> R.string.mode_live to R.string.mode_live_desc
                WallpaperMode.STATIC -> R.string.mode_static to R.string.mode_static_desc
            }
            val selected = settings.mode == mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onChange(settings.copy(mode = mode)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = selected, onClick = { onChange(settings.copy(mode = mode)) })
            }
        }
    }
}

/** Daily notification switch with its hour. */
@Composable
fun NotificationControls(settings: Settings, onChange: (Settings) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.notif_daily),
        subtitle = stringResource(R.string.notif_daily_desc),
        checked = settings.notifyDaily,
        onChecked = { onChange(settings.copy(notifyDaily = it)) },
    )
    if (settings.notifyDaily) {
        HourStepper(
            label = stringResource(R.string.notif_at),
            minutes = settings.notifyMinute,
            onChange = { onChange(settings.copy(notifyMinute = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetControls(settings: Settings, onChange: (Settings) -> Unit) {
    SectionLabel(stringResource(R.string.label_target))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WallpaperTarget.entries.forEach { t ->
            val res = when (t) {
                WallpaperTarget.BOTH -> R.string.target_both
                WallpaperTarget.HOME -> R.string.target_home
                WallpaperTarget.LOCK -> R.string.target_lock
            }
            FilterChip(
                selected = settings.target == t,
                onClick = { onChange(settings.copy(target = t)) },
                label = { Text(stringResource(res)) },
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
