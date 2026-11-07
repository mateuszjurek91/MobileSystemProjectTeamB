package com.example.focushydrate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HydrationColors = lightColorScheme(
    primary = Color(0xFF286E81),
    onPrimary = Color.White,
    secondary = Color(0xFF4C95AA),
    secondaryContainer = Color(0xFFCEEFFF),
    onSecondaryContainer = Color(0xFF145368),
    surfaceContainerLow = Color(0xFFE7F5FC),
    surfaceContainerHigh = Color(0xFFD9F0FC),
    surfaceContainerHighest = Color(0xFFCDEEFF),
    surfaceContainer = Color(0xFFD9F0FC),
    background = Color(0xFFADDEFC),
    onBackground = Color(0xFF145368),
    surface = Color(0xFFADDEFC),
    onSurface = Color(0xFF145368),
    surfaceVariant = Color(0xFFCDEEFF),
    onSurfaceVariant = Color(0xFF285E73)
)

@Composable
fun FocusHydrateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HydrationColors, typography = Typography, content = content)
}
