package com.selecttube.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF2979FF),
    secondary        = Brand500,
    tertiary         = Accent,
    background       = Brand900,
    surface          = Brand700,
    surfaceVariant   = Brand500,
    onPrimary        = Color.White,
    onSecondary      = Color.White,
    onBackground     = Color.White,
    onSurface        = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA),
)

private val LightColorScheme = DarkColorScheme

@Composable
fun SelectTubeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
