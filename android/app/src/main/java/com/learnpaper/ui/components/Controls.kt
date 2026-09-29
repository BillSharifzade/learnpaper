package com.learnpaper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.learnpaper.R
import com.learnpaper.ui.LocalizedAlertDialog
import com.learnpaper.content.ContentRepository
import com.learnpaper.content.Lang
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.LockStyle
import com.learnpaper.data.Settings
import com.learnpaper.render.Palette
import com.learnpaper.render.Palettes
import com.learnpaper.ui.theme.LpTheme
import java.util.Locale

@Composable
fun langName(lang: Lang): String = stringResource(
    when (lang) {
        Lang.EN -> R.string.lang_en
        Lang.RU -> R.string.lang_ru
        Lang.TJ -> R.string.lang_tj
    },
)

@Composable
fun levelName(level: String): String = when (level) {
    "A1" -> stringResource(R.string.level_a1)
    "A2" -> stringResource(R.string.level_a2)
    "B1" -> stringResource(R.string.level_b1)
    "B2" -> stringResource(R.string.level_b2)
    "C1" -> stringResource(R.string.level_c1)
    "C2" -> stringResource(R.string.level_c2)
    else -> level
}

@Composable
fun intervalLabel(minutes: Int): String = when {
    minutes >= 1440 -> stringResource(R.string.interval_daily)
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.interval_hours, minutes / 60)
    minutes >= 60 -> stringResource(R.string.interval_hours_minutes, minutes / 60, minutes % 60)
    else -> stringResource(R.string.interval_minutes, minutes)
}

fun formatMinutes(minutesOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", (minutesOfDay / 60) % 24, minutesOfDay % 60)

/** Language being learned (the big word) and the translation languages under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageControls(settings: Settings, onChange: (Settings) -> Unit) {
    Overline(stringResource(R.string.label_learning))
    SegmentedControl(
        items = Lang.entries,
        selected = settings.headline,
        onSelect = { lang ->
            if (settings.headline != lang) onChange(settings.copy(headline = lang, shown = Lang.entries.filter { it != lang }.toSet()))
        },
        label = { langName(it) },
    )
    Spacer(Modifier.height(18.dp))
    Overline(stringResource(R.string.label_show_translations))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.filter { it != settings.headline }.forEach { lang ->
            val on = lang in settings.shown
            ChoiceChip(
                text = langName(lang),
                selected = on,
                onClick = { onChange(settings.copy(shown = if (on) settings.shown - lang else settings.shown + lang)) },
                leading = if (on) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

/** Two-column grid of level cards; at least one level stays selected. */
@Composable
fun LevelControls(settings: Settings, available: List<String>, counts: Map<String, Int>, onChange: (Settings) -> Unit) {
    val levels = ContentRepository.LEVEL_ORDER.filter { it in available }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        levels.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { level ->
                    val selected = level in settings.levels
                    LevelCard(
                        level = level,
                        count = counts[level] ?: 0,
                        selected = selected,
                        onClick = {
                            val next = if (selected) settings.levels - level else settings.levels + level
                            if (next.isNotEmpty()) onChange(settings.copy(levels = next))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LevelCard(level: String, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "lvlBorder")
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, label = "lvlWidth")
    val i = ContentRepository.LEVEL_ORDER.indexOf(level).coerceIn(0, 5)
    Column(
        modifier
            .pressable(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) LpTheme.extra.levels[i].copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(width, border, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(level)
            Spacer(Modifier.weight(1f))
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(levelName(level), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.words_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Wallpaper colours in two groups, light and dark. Switching the group picks the colour last used in it;
 * "a new colour with every word" rotates inside the chosen group, so the phone's light or dark look
 * (clock, status bar) stays right for every card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteControls(settings: Settings, onChange: (Settings) -> Unit) {
    val chosen = Palettes.byId(settings.paletteId)
    var lastLight by rememberSaveable { mutableStateOf(Palettes.light.first().id) }
    var lastDark by rememberSaveable { mutableStateOf(Palettes.dark.first().id) }
    SideEffect { if (chosen.isDark) lastDark = chosen.id else lastLight = chosen.id }

    Overline(stringResource(R.string.label_palette))
    SegmentedControl(
        items = listOf(false, true),
        selected = chosen.isDark,
        onSelect = { dark -> if (dark != chosen.isDark) onChange(settings.copy(paletteId = if (dark) lastDark else lastLight)) },
        label = { dark -> stringResource(if (dark) R.string.palette_group_dark else R.string.palette_group_light) },
    )
    Spacer(Modifier.height(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Palettes.group(chosen).forEach { p ->
            PaletteSwatch(
                palette = p,
                selected = !settings.rotatePalette && settings.paletteId == p.id,
                onClick = { onChange(settings.copy(paletteId = p.id, rotatePalette = false)) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    ToggleRow(
        title = stringResource(R.string.palette_rotate),
        subtitle = stringResource(if (chosen.isDark) R.string.palette_rotate_dark else R.string.palette_rotate_light),
        checked = settings.rotatePalette,
        onChecked = { onChange(settings.copy(rotatePalette = it)) },
    )
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(dampingRatio = 0.6f), label = "ring")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f), label = "check")
    Column(
        Modifier
            .width(58.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .pressable(interaction, 0.88f)
                .then(if (ring > 0.5.dp) Modifier.border(BorderStroke(ring, MaterialTheme.colorScheme.primary), CircleShape) else Modifier)
                .padding(ring + 2.dp)
                .clip(CircleShape)
                .background(Color(palette.bg))
                .border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(palette.accent)))
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp).graphicsLayer { scaleX = check; scaleY = check; alpha = check },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(palette.nameRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LayoutControls(settings: Settings, onChange: (Settings) -> Unit) {
    Overline(stringResource(R.string.label_layout))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LayoutPreset.choices.forEach { preset ->
            LayoutOption(
                preset = preset,
                selected = settings.layout == preset,
                onClick = { onChange(settings.copy(layout = preset)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.layout_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LayoutOption(preset: LayoutPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (title, desc) = when (preset) {
        LayoutPreset.LOCK, LayoutPreset.UNDER_CLOCK -> R.string.layout_lock to R.string.layout_lock_desc
        LayoutPreset.HOME -> R.string.layout_home to R.string.layout_home_desc
        LayoutPreset.COMPACT -> R.string.layout_compact to R.string.layout_compact_desc
    }
    val interaction = remember { MutableInteractionSource() }
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "layoutBorder")
    val borderW by animateDpAsState(if (selected) 2.dp else 1.dp, label = "layoutBorderW")
    val ink = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .pressable(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(borderW, border, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(9f / 18f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        ) {
            val (top, height) = when (preset) {
                LayoutPreset.LOCK, LayoutPreset.UNDER_CLOCK -> 0.34f to 0.52f
                LayoutPreset.HOME -> 0.18f to 0.64f
                LayoutPreset.COMPACT -> 0.62f to 0.26f
            }
            Column(Modifier.fillMaxWidth().aspectRatio(9f / 18f).padding(horizontal = 8.dp)) {
                if (preset != LayoutPreset.HOME) {
                    Spacer(Modifier.weight(0.08f))
                    Box(Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.42f).height(5.dp).clip(CircleShape).background(ink.copy(alpha = 0.35f)))
                    Spacer(Modifier.weight((top - 0.08f).coerceAtLeast(0.01f)))
                } else {
                    Spacer(Modifier.weight(top))
                }
                Column(
                    Modifier.weight(height).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (preset != LayoutPreset.COMPACT) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(ink))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.45f)))
                    if (preset != LayoutPreset.COMPACT) {
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth(0.55f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.3f)))
                    }
                }
                Spacer(Modifier.weight((1f - top - height).coerceAtLeast(0.01f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            stringResource(desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** How the phone draws its lock-screen clock; the card keeps clear of it. */
@Composable
fun LockClockControls(settings: Settings, onChange: (Settings) -> Unit) {
    Overline(stringResource(R.string.label_lock_clock))
    SegmentedControl(
        items = LockStyle.entries,
        selected = settings.lockStyle,
        onSelect = { onChange(settings.copy(lockStyle = it)) },
        label = {
            stringResource(
                when (it) {
                    LockStyle.AUTO -> R.string.lock_clock_auto
                    LockStyle.TOP_CLOCK -> R.string.lock_clock_top
                    LockStyle.BIG_CLOCK -> R.string.lock_clock_big
                },
            )
        },
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.lock_clock_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Preset intervals plus "Custom…", which takes any value from one minute to a day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalControls(settings: Settings, onChange: (Settings) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val custom = settings.intervalMinutes !in Settings.INTERVALS
    Overline(stringResource(R.string.label_interval))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Settings.INTERVALS.forEach { minutes ->
            ChoiceChip(
                text = intervalLabel(minutes),
                selected = settings.intervalMinutes == minutes,
                onClick = { onChange(settings.copy(intervalMinutes = minutes)) },
            )
        }
        ChoiceChip(
            text = if (custom) intervalLabel(settings.intervalMinutes) else stringResource(R.string.interval_custom),
            selected = custom,
            onClick = { editing = true },
            leading = {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = if (custom) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
    if (editing) {
        IntervalDialog(
            initial = settings.intervalMinutes,
            onDismiss = { editing = false },
            onConfirm = { minutes ->
                editing = false
                onChange(settings.copy(intervalMinutes = minutes))
            },
        )
    }
}

/** "Every [ 45 ] minutes | hours": a number with steppers and a unit, from one minute to 24 hours. */
@Composable
private fun IntervalDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val startInHours = initial >= 60 && initial % 60 == 0
    var hours by rememberSaveable { mutableStateOf(startInHours) }
    var text by rememberSaveable { mutableStateOf((if (startInHours) initial / 60 else initial).toString()) }
    val number = text.toIntOrNull()
    val minutes = number?.let { if (hours) it * 60 else it }
    val valid = minutes != null && minutes in Settings.MIN_INTERVAL..Settings.MAX_INTERVAL
    val max = if (hours) Settings.MAX_INTERVAL / 60 else Settings.MAX_INTERVAL
    fun step(by: Int) {
        text = ((number ?: 0) + by).coerceIn(1, max).toString()
    }

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_interval), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(Icons.Rounded.Remove, contentDescription = "−", onClick = { step(-1) }, size = 44.dp, iconSize = 20.dp)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        isError = !valid,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.width(112.dp),
                    )
                    CircleIconButton(Icons.Rounded.Add, contentDescription = "+", onClick = { step(1) }, size = 44.dp, iconSize = 20.dp)
                }
                Spacer(Modifier.height(16.dp))
                SegmentedControl(
                    items = listOf(false, true),
                    selected = hours,
                    onSelect = { hours = it },
                    label = { inHours -> stringResource(if (inHours) R.string.interval_unit_hours else R.string.interval_unit_minutes) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.interval_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = valid) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

@Composable
fun QuietHoursControls(settings: Settings, onChange: (Settings) -> Unit) {
    ToggleRow(
        title = stringResource(R.string.label_quiet),
        subtitle = stringResource(R.string.quiet_desc),
        checked = settings.quietEnabled,
        onChecked = { onChange(settings.copy(quietEnabled = it)) },
    )
    androidx.compose.animation.AnimatedVisibility(visible = settings.quietEnabled) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeButton(
                label = stringResource(R.string.quiet_from),
                minutes = settings.quietStart,
                onChange = { onChange(settings.copy(quietStart = it)) },
                modifier = Modifier.weight(1f),
            )
            TimeButton(
                label = stringResource(R.string.quiet_to),
                minutes = settings.quietEnd,
                onChange = { onChange(settings.copy(quietEnd = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Tappable time field that opens a Material time picker (24-hour clock). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressable(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { open = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMinutes(minutes), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
        LocalizedAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.time_picker_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }
}

/** Daily notification switch with its time. */
@Composable
fun NotificationControls(settings: Settings, onChange: (Settings) -> Unit) {
    ToggleRow(
        title = stringResource(R.string.notif_daily),
        subtitle = stringResource(R.string.notif_daily_desc),
        checked = settings.notifyDaily,
        onChecked = { onChange(settings.copy(notifyDaily = it)) },
    )
    androidx.compose.animation.AnimatedVisibility(visible = settings.notifyDaily) {
        TimeButton(
            label = stringResource(R.string.notif_at),
            minutes = settings.notifyMinute,
            onChange = { onChange(settings.copy(notifyMinute = it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
