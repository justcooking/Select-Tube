package com.selecttube.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    // Surfaces
    background       = Surface0,
    surface          = Surface1,
    surfaceVariant   = Surface2,

    // Primary (accent)
    primary          = Accent,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF1A3A6B),   // dark cobalt tint for containers
    onPrimaryContainer = TextPrimary,

    // Secondary (neutral warm)
    secondary        = TextSecondary,
    onSecondary      = Surface0,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,

    // Tertiary (amber – used for pending/warning states)
    tertiary         = WarmAmber,
    onTertiary       = Surface0,
    tertiaryContainer = Color(0xFF3D2C0A),
    onTertiaryContainer = WarmAmber,

    // Text
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,

    // Error
    error            = DestructiveRed,
    onError          = Color.White,
    errorContainer   = Color(0xFF3D0F0F),
    onErrorContainer = Color(0xFFFFB3B3),

    // Outline
    outline          = Hairline,
    outlineVariant   = Hairline,
)

@Composable
fun SelectTubeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content,
    )
}
