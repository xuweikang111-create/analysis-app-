package com.lobsterai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LobsterRed = Color(0xFFB92F42)
private val LobsterRedDark = Color(0xFFFFB2BA)
private val Ink = Color(0xFF1D1A1B)
private val WarmSurface = Color(0xFFFFF8F7)
private val WarmSurfaceVariant = Color(0xFFF7ECEC)

private val LightColors = lightColorScheme(
    primary = LobsterRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADD),
    onPrimaryContainer = Color(0xFF3F0010),
    secondary = Color(0xFF76565A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDADD),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF7B5734),
    background = WarmSurface,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = Color(0xFF5A5152),
    outline = Color(0xFF8C7477)
)

private val DarkColors = darkColorScheme(
    primary = LobsterRedDark,
    onPrimary = Color(0xFF68001E),
    primaryContainer = Color(0xFF8D1730),
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Color(0xFFE7BDC1),
    onSecondary = Color(0xFF43282C),
    secondaryContainer = Color(0xFF5C3E42),
    onSecondaryContainer = Color(0xFFFFDADD),
    tertiary = Color(0xFFEABF8E),
    background = Color(0xFF151112),
    onBackground = Color(0xFFF0DEE0),
    surface = Color(0xFF151112),
    onSurface = Color(0xFFF0DEE0),
    surfaceVariant = Color(0xFF514346),
    onSurfaceVariant = Color(0xFFD8C1C4),
    outline = Color(0xFFA28C8F)
)

@Composable
fun LobsterAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}