package com.learnpaper.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.learnpaper.i18n.AppLanguage
import com.learnpaper.i18n.AppLocale

/** The app's interface language (not the phone's), for code that needs it outside of string resources. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.DEFAULT }

/**
 * Serves the UI in the app's interface language without restarting anything: [LocalContext] and
 * [LocalConfiguration] are replaced by ones in that language, so every string, plural and date (and
 * Material's own texts) follow a switch within the same frame. The activity itself handles locale
 * changes (`android:configChanges`), so nothing is recreated and nothing flickers. Dialogs and sheets
 * are windows of their own and wrap their content in this again (see [LocalizedAlertDialog]).
 */
@Composable
fun LocalizedContent(content: @Composable () -> Unit) {
    val lang by AppLocale.language.collectAsState()
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val config = remember(baseConfig, lang) {
        Configuration(baseConfig).apply {
            setLocales(LocaleList(lang.locale))
            setLayoutDirection(lang.locale)
        }
    }
    val context = remember(base, config) { LocalizedContext(base, base.createConfigurationContext(config).resources) }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides config,
        LocalAppLanguage provides lang,
        content = content,
    )
}

/**
 * Material's AlertDialog with every slot in the interface language. A dialog is a window of its own and
 * Compose gives it the activity's context, which only follows the in-app language on Android 13+.
 */
@Composable
fun LocalizedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { LocalizedContent(confirmButton) },
        dismissButton = dismissButton?.let { slot -> { LocalizedContent(slot) } },
        title = title?.let { slot -> { LocalizedContent(slot) } },
        text = text?.let { slot -> { LocalizedContent(slot) } },
        containerColor = containerColor,
    )
}

/** The activity, with resources in the interface language. */
private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}
