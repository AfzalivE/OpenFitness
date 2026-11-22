package app.openfitness.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openfitness.app.data.ThemeMode

/*
 * All design tokens of the app are in this file: colors, status colors,
 * type scale, spacing, and sizes. Screens use these tokens, not raw values.
 *
 * Contrast (WCAG 2.x), dark theme:
 * - Accent red #FF5A68 on the darkest to the lightest surface: 6.3 to 4.7 (AA).
 * - Text on the accent (#16080A on #FF5A68): 6.4 (AA).
 * - Done, deload, and error colors on all surfaces: 5.8 or more (AA).
 * Light theme: accent #C2182B on the background: 5.7; white on the accent: 6.1.
 */

/** Spacing steps. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
}

/** Sizes of the compact layout. Touch targets stay at least [touch]. */
object Sizes {
    /** The smallest touch target. */
    val touch = 48.dp
    /** One set row in the set table. It is also a touch target. */
    val setRow = 48.dp
    /** The visible height of a number field. Its touch area is the row. */
    val field = 40.dp
    /** The width of the weight and reps fields. */
    val fieldWidth = 72.dp
    /** The done button: a circle in a [touch] target. */
    val doneButton = 32.dp
    val cardPadding = 12.dp
    val cardRadius = 12.dp
    val fieldRadius = 8.dp
    val progressBar = 4.dp
    val gridCell = 36.dp
}

/** Colors for the state of a day, a set, or a question. They are not the accent color. */
@Immutable
data class StatusColors(
    val done: Color,
    val onDone: Color,
    val current: Color,
    val onCurrent: Color,
    val deload: Color,
    val onDeload: Color,
    val future: Color,
    val warning: Color,
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val AccentRed = Color(0xFFFF5A68)
private val OnAccentRed = Color(0xFF16080A)

private val Dark = darkColorScheme(
    primary = AccentRed,
    onPrimary = OnAccentRed,
    primaryContainer = Color(0xFF3A1518),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFB9BEC5),
    onSecondary = Color(0xFF16181B),
    secondaryContainer = Color(0xFF2C3036),
    onSecondaryContainer = Color(0xFFE3E5E8),
    tertiary = Color(0xFF8C9EFF),
    onTertiary = Color(0xFF0E1433),
    tertiaryContainer = Color(0xFF232A4D),
    onTertiaryContainer = Color(0xFFDDE2FF),
    error = Color(0xFFFFAD47),
    onError = Color(0xFF2B1700),
    errorContainer = Color(0xFF3D2A10),
    onErrorContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0F1012),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF0F1012),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF1E2024),
    onSurfaceVariant = Color(0xFFA2A7AE),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF141619),
    surfaceContainer = Color(0xFF17191C),
    surfaceContainerHigh = Color(0xFF1E2024),
    surfaceContainerHighest = Color(0xFF272A2F),
    outline = Color(0xFF3A3F45),
    outlineVariant = Color(0xFF2A2E33),
)

private val DarkStatus = StatusColors(
    done = Color(0xFF3DD68C),
    onDone = Color(0xFF04210F),
    current = AccentRed,
    onCurrent = OnAccentRed,
    deload = Color(0xFF8C9EFF),
    onDeload = Color(0xFF0E1433),
    future = Color(0xFF272A2F),
    warning = Color(0xFFFFAD47),
)

private val LightAccent = Color(0xFFC2182B)

private val Light = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADC),
    onPrimaryContainer = Color(0xFF40000A),
    secondary = Color(0xFF5A5F66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E6E9),
    onSecondaryContainer = Color(0xFF1B1D20),
    tertiary = Color(0xFF3F51B5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE1FF),
    onTertiaryContainer = Color(0xFF0E1433),
    error = Color(0xFFA15C00),
    onError = Color.White,
    errorContainer = Color(0xFFFFDDB3),
    onErrorContainer = Color(0xFF2B1700),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF16181B),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF16181B),
    surfaceVariant = Color(0xFFE8E9EC),
    onSurfaceVariant = Color(0xFF51565D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F2F4),
    surfaceContainer = Color(0xFFECEDF0),
    surfaceContainerHigh = Color(0xFFE5E7EA),
    surfaceContainerHighest = Color(0xFFDEE0E4),
    outline = Color(0xFFB9BDC3),
    outlineVariant = Color(0xFFD3D6DA),
)

private val LightStatus = StatusColors(
    done = Color(0xFF14804A),
    onDone = Color.White,
    current = LightAccent,
    onCurrent = Color.White,
    deload = Color(0xFF3F51B5),
    onDeload = Color.White,
    future = Color(0xFFDEE0E4),
    warning = Color(0xFFA15C00),
)

/** The type scale. Sizes are in sp, so text follows the system font size. */
private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun OpenFitnessTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}

/** The status colors of the current theme. */
val statusColors: StatusColors
    @Composable get() = LocalStatusColors.current
