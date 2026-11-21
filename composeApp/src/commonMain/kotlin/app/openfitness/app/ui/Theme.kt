package app.openfitness.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F6B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EFD6),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4C6359),
    secondaryContainer = Color(0xFFCEE9DB),
    onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF9A4A1F),
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF6FBF7),
    surface = Color(0xFFF6FBF7),
    surfaceContainer = Color(0xFFEAF0EC),
    surfaceContainerHigh = Color(0xFFE4EAE6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9BD3BA),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFB7EFD6),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B42),
    onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFFFB691),
    tertiaryContainer = Color(0xFF7B3308),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
)

@Composable
fun OpenFitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
