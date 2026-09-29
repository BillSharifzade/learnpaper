package com.learnpaper.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnpaper.R
import com.learnpaper.ui.components.ReadableWidth
import com.learnpaper.ui.onboarding.OnboardingScreen
import com.learnpaper.ui.settings.SettingsScreen
import com.learnpaper.ui.theme.LearnPaperTheme
import com.learnpaper.ui.today.TodayScreen
import com.learnpaper.ui.words.WordsScreen
import com.learnpaper.wallpaper.LiveCardWallpaper

enum class Screen(val labelRes: Int, val icon: ImageVector) {
    TODAY(R.string.nav_today, Icons.Rounded.Style),
    WORDS(R.string.nav_words, Icons.AutoMirrored.Rounded.MenuBook),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Tune),
}

@Composable
fun LearnPaperApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The user may come back from the system wallpaper picker; re-check whether we are active.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshLiveStatus() }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.OpenLivePicker -> {
                    // The direct "set LearnPaper" screen, else the list of live wallpapers.
                    val opened = listOf(LiveCardWallpaper.pickerIntent(context), LiveCardWallpaper.chooserIntent()).any { intent ->
                        try {
                            context.startActivity(intent)
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        } catch (_: SecurityException) {
                            false
                        }
                    }
                    if (!opened) vm.livePickerMissing()
                }
            }
        }
    }

    LearnPaperTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = when {
                    state.loading -> 0
                    !state.settings.onboarded -> 1
                    else -> 2
                },
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "root",
            ) { phase ->
                when (phase) {
                    0 -> Box(Modifier.fillMaxSize())
                    1 -> ReadableWidth { OnboardingScreen(state = state, vm = vm) }
                    else -> MainScreen(state = state, vm = vm)
                }
            }
        }
    }
}

/** Soft fade under the status bar so scrolled content never collides with the system icons. */
@Composable
private fun StatusBarScrim() {
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f)))),
    )
}

@Composable
private fun MainScreen(state: UiState, vm: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val todayScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val wordsList = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.messages.collect { res -> snackbar.showSnackbar(context.getString(res)) }
    }
    BackHandler(enabled = screen != Screen.TODAY) { screen = Screen.TODAY }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = {
                            if (screen == s) return@NavigationBarItem
                            // A search field in a tab that goes away must not keep the keyboard open.
                            focus.clearFocus()
                            screen = s
                        },
                        icon = { Icon(s.icon, contentDescription = null) },
                        label = { Text(stringResource(s.labelRes), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        KeepAliveTabs(selected = screen, modifier = Modifier.padding(padding)) { tab ->
            ReadableWidth {
                when (tab) {
                    Screen.TODAY -> TodayScreen(state, vm, todayScroll)
                    Screen.WORDS -> WordsScreen(state.settings, vm, wordsList)
                    Screen.SETTINGS -> SettingsScreen(state, vm, settingsScroll)
                }
            }
        }
        StatusBarScrim()
    }
}

/**
 * Tabs that are built once and then kept: switching back to a tab shows it as it was, with nothing to
 * rebuild, reload or re-render. Only the selected tab is measured, placed and drawn; the others keep
 * their state (scroll positions, search, rendered cards) off screen. A tab is first built when it is
 * first opened, and fades in quickly when selected.
 */
@Composable
private fun <T : Any> KeepAliveTabs(selected: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val opened = remember { mutableStateListOf(selected) }
    if (selected !in opened) opened += selected
    val alpha = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) {
            first = false
        } else {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(TAB_FADE_MS))
        }
    }
    Layout(
        modifier = modifier,
        content = {
            opened.forEach { tab ->
                key(tab) {
                    Box(Modifier.graphicsLayer { if (tab == selected) this.alpha = alpha.value }) { content(tab) }
                }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables[opened.indexOf(selected)].measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }
}

private const val TAB_FADE_MS = 160
