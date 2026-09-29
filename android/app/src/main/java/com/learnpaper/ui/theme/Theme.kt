package com.learnpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnpaper.render.Fonts

/**
 * LearnPaper brand: warm "paper" surfaces, ink text, Iris violet for actions and Apricot as the
 * friendly second colour (the two cards of the logo). Mint marks progress ("learned").
 */
object Brand {
    val Iris = Color(0xFF5A48E6)
    val IrisLight = Color(0xFF7A6BFF)
    val IrisDeep = Color(0xFF4A38D4)
    val Apricot = Color(0xFFFF9A76)
    val Paper = Color(0xFFFBF8F4)
    val Ink = Color(0xFF1F1A2E)
    val Mint = Color(0xFF2E9C74)
}

private val Light = lightColorScheme(
    primary = Brand.Iris,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF22186B),
    inversePrimary = Color(0xFFB9AEFF),
    secondary = Color(0xFFC4582F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D6),
    onSecondaryContainer = Color(0xFF4A1E0E),
    tertiary = Brand.Mint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F3E6),
    onTertiaryContainer = Color(0xFF0D3B2A),
    background = Brand.Paper,
    onBackground = Brand.Ink,
    surface = Brand.Paper,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF1ECE5),
    onSurfaceVariant = Color(0xFF6B6478),
    surfaceTint = Brand.Iris,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE9E3DB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDFA),
    surfaceContainer = Color(0xFFF6F2EC),
    surfaceContainerHigh = Color(0xFFF1ECE5),
    surfaceContainerHighest = Color(0xFFEBE5DD),
    inverseSurface = Color(0xFF2E2A38),
    inverseOnSurface = Color(0xFFF5F1EC),
    outline = Color(0xFFD5CDC3),
    outlineVariant = Color(0xFFE8E2DA),
    error = Color(0xFFC8364E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410011),
    scrim = Color(0xFF14101F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB0A5FF),
    onPrimary = Color(0xFF1E1466),
    primaryContainer = Color(0xFF3A2DA6),
    onPrimaryContainer = Color(0xFFE9E5FF),
    inversePrimary = Brand.Iris,
    secondary = Color(0xFFFFB396),
    onSecondary = Color(0xFF55200B),
    secondaryContainer = Color(0xFF6B2E17),
    onSecondaryContainer = Color(0xFFFFE3D6),
    tertiary = Color(0xFF7FDDB7),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF11533C),
    onTertiaryContainer = Color(0xFFD5F3E6),
    background = Color(0xFF121018),
    onBackground = Color(0xFFF1ECF7),
    surface = Color(0xFF121018),
    onSurface = Color(0xFFF1ECF7),
    surfaceVariant = Color(0xFF2A2634),
    onSurfaceVariant = Color(0xFFB9B1C7),
    surfaceTint = Color(0xFFB0A5FF),
    surfaceBright = Color(0xFF363240),
    surfaceDim = Color(0xFF121018),
    surfaceContainerLowest = Color(0xFF0D0B12),
    surfaceContainerLow = Color(0xFF17141E),
    surfaceContainer = Color(0xFF1C1924),
    surfaceContainerHigh = Color(0xFF24202D),
    surfaceContainerHighest = Color(0xFF2D2937),
    inverseSurface = Color(0xFFF1ECF7),
    inverseOnSurface = Color(0xFF2E2A38),
    outline = Color(0xFF4B4557),
    outlineVariant = Color(0xFF34303F),
    error = Color(0xFFFF8FA2),
    onError = Color(0xFF5E0019),
    errorContainer = Color(0xFF8A1830),
    onErrorContainer = Color(0xFFFFDADF),
    scrim = Color.Black,
)

/** Colours that Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val favorite: Color,
    val streak: Color,
    val cardShadow: Color,
    /** Background tints for the CEFR level badges A1…C2. */
    val levels: List<Color>,
    val onLevel: Color,
)

private val LightExtra = ExtraColors(
    success = Brand.Mint,
    onSuccessContainer = Color(0xFF0D3B2A),
    successContainer = Color(0xFFD5F3E6),
    favorite = Color(0xFFE5486A),
    streak = Color(0xFFF07A3A),
    cardShadow = Color(0x331B1150),
    levels = listOf(
        Color(0xFFD5F3E6), Color(0xFFD3EEF4), Color(0xFFDDE8FF),
        Color(0xFFE9E5FF), Color(0xFFFFE3D6), Color(0xFFFFDCE6),
    ),
    onLevel = Brand.Ink,
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF7FDDB7),
    onSuccessContainer = Color(0xFFD5F3E6),
    successContainer = Color(0xFF11533C),
    favorite = Color(0xFFFF7D98),
    streak = Color(0xFFFFA36E),
    cardShadow = Color(0x66000000),
    levels = listOf(
        Color(0xFF1C4A3A), Color(0xFF1B4550), Color(0xFF263A66),
        Color(0xFF362D78), Color(0xFF5E3020), Color(0xFF5C2438),
    ),
    onLevel = Color(0xFFF1ECF7),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(context: android.content.Context, path: String, weights: List<Int>): FontFamily =
    FontFamily(
        weights.map { w ->
            Font(
                path = path,
                assetManager = context.assets,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )

/** Onest for everything the brand speaks with; Inter for transcriptions (IPA and Latin transliteration). */
@Immutable
data class BrandFonts(val display: FontFamily, val text: FontFamily)

val LocalBrandFonts = staticCompositionLocalOf<BrandFonts?> { null }

private fun typography(display: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.brand(size: Int, line: Int, weight: Int, tracking: Double = 0.0) =
        copy(fontFamily = display, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), letterSpacing = tracking.sp)
    return Typography(
        displayLarge = base.displayLarge.brand(54, 60, 800, -1.2),
        displayMedium = base.displayMedium.brand(44, 50, 800, -0.9),
        displaySmall = base.displaySmall.brand(36, 42, 700, -0.6),
        headlineLarge = base.headlineLarge.brand(32, 38, 700, -0.5),
        headlineMedium = base.headlineMedium.brand(28, 34, 700, -0.4),
        headlineSmall = base.headlineSmall.brand(24, 30, 700, -0.2),
        titleLarge = base.titleLarge.brand(21, 27, 700, -0.1),
        titleMedium = base.titleMedium.brand(17, 23, 600),
        titleSmall = base.titleSmall.brand(15, 20, 600),
        bodyLarge = base.bodyLarge.brand(16, 24, 400),
        bodyMedium = base.bodyMedium.brand(14, 20, 400),
        bodySmall = base.bodySmall.brand(13, 18, 400),
        labelLarge = base.labelLarge.brand(15, 20, 600),
        labelMedium = base.labelMedium.brand(13, 18, 500),
        labelSmall = base.labelSmall.brand(11, 16, 600, 0.4),
    )
}

val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * One set of font families for the whole process: the same instances keep hitting Compose's typeface cache
 * when the activity is recreated or the language is switched, so fonts are never loaded twice.
 */
@Volatile
private var processFonts: BrandFonts? = null

private fun brandFonts(context: android.content.Context): BrandFonts = processFonts ?: synchronized(BrandFonts::class) {
    processFonts ?: BrandFonts(
        display = variableFamily(context, Fonts.ONEST, listOf(400, 500, 600, 700, 800)),
        text = variableFamily(context, Fonts.INTER, listOf(400, 500, 600)),
    ).also { processFonts = it }
}

@Composable
fun LearnPaperTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fonts = remember { brandFonts(context.applicationContext) }
    val type = remember(fonts) { typography(fonts.display) }
    CompositionLocalProvider(
        LocalExtraColors provides if (dark) DarkExtra else LightExtra,
        LocalBrandFonts provides fonts,
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = type,
            shapes = BrandShapes,
            content = content,
        )
    }
}

object LpTheme {
    val extra: ExtraColors
        @Composable get() = LocalExtraColors.current

    val colors: ColorScheme
        @Composable get() = MaterialTheme.colorScheme

    /** Transcription text: Inter, which carries every IPA symbol and the macron/acute letters of the transliterations. */
    val transcription: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = LocalBrandFonts.current?.text ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    /** Small caps-like label for section headers. */
    val overline: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}
