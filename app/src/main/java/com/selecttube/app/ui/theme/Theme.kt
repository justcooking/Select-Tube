package com.selecttube.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary         = Purple80,
    secondary       = PurpleGrey80,
    tertiary        = Pink80,
    background      = Brand900,
    surface         = Brand700,
)

private val LightColorScheme = lightColorScheme(
    primary         = Purple40,
    secondary       = PurpleGrey40,
    tertiary        = Pink40,
)

@Composable
fun SelectTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
