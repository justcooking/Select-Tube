package com.selecttube.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Inter / system-ui — calm, precise, technical.
// fontFeatureSettings = "tnum" on numeric styles enables tabular figures.
private val SystemSansSerif = FontFamily.Default

val Typography = Typography(
    // ── Display ──────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.W300,
        fontSize      = 57.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.W300,
        fontSize      = 45.sp,
        lineHeight    = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 36.sp,
        lineHeight    = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline ─────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title ────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Medium,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── Body ─────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily    = SystemSansSerif,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.sp,
    ),

    // ── Label (tabular nums via fontFeatureSettings) ──────────────────────
    labelLarge = TextStyle(
        fontFamily          = SystemSansSerif,
        fontWeight          = FontWeight.Medium,
        fontSize            = 14.sp,
        lineHeight          = 20.sp,
        letterSpacing       = 0.1.sp,
        fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(
        fontFamily          = SystemSansSerif,
        fontWeight          = FontWeight.Medium,
        fontSize            = 12.sp,
        lineHeight          = 16.sp,
        letterSpacing       = 0.5.sp,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontFamily          = SystemSansSerif,
        fontWeight          = FontWeight.Medium,
        fontSize            = 11.sp,
        lineHeight          = 16.sp,
        letterSpacing       = 0.5.sp,
        fontFeatureSettings = "tnum",
    ),
)
