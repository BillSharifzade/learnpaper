package com.learnpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.learnpaper.render.Fonts

private val Light = lightColorScheme(
    primary = Color(0xFF6A5AE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0F8),
    onPrimaryContainer = Color(0xFF2E2452),
    secondary = Color(0xFF4CAF8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F2E6),
    onSecondaryContainer = Color(0xFF173E31),
    tertiary = Color(0xFFE8845C),
    tertiaryContainer = Color(0xFFFFE1CF),
    onTertiaryContainer = Color(0xFF4A2A1C),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF2A2420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFF1ECE4),
    onSurfaceVariant = Color(0xFF6B625A),
    outline = Color(0xFFD8D0C6),
    outlineVariant = Color(0xFFE9E3DA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9ADFF),
    onPrimary = Color(0xFF241A5C),
    primaryContainer = Color(0xFF3F3470),
    onPrimaryContainer = Color(0xFFE6E0F8),
    secondary = Color(0xFF8BD6B8),
    secondaryContainer = Color(0xFF1F4A3B),
    onSecondaryContainer = Color(0xFFD6F2E6),
    tertiary = Color(0xFFF0A585),
    tertiaryContainer = Color(0xFF5A3221),
    onTertiaryContainer = Color(0xFFFFE1CF),
    background = Color(0xFF17151A),
    onBackground = Color(0xFFEDE7E0),
    surface = Color(0xFF1F1C22),
    onSurface = Color(0xFFEDE7E0),
    surfaceVariant = Color(0xFF2B272F),
    onSurfaceVariant = Color(0xFFC6BFB8),
    outline = Color(0xFF4A444F),
    outlineVariant = Color(0xFF332F38),
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun LearnPaperTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val inter = remember(context) {
        FontFamily(
            listOf(400, 500, 600, 700).map { w ->
                Font(
                    path = Fonts.PATH,
                    assetManager = context.assets,
                    weight = FontWeight(w),
                    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
                )
            },
        )
    }
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = inter),
        displayMedium = base.displayMedium.copy(fontFamily = inter),
        displaySmall = base.displaySmall.copy(fontFamily = inter),
        headlineLarge = base.headlineLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = inter),
        bodyMedium = base.bodyMedium.copy(fontFamily = inter),
        bodySmall = base.bodySmall.copy(fontFamily = inter),
        labelLarge = base.labelLarge.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = inter, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = typography,
        content = content,
    )
}

/** Uppercase section label used in onboarding and settings. */
val SectionLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
