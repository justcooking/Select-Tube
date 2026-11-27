package com.selecttube.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Cinema-dark surface ramp ───────────────────────────────────────────────
val Surface0  = Color(0xFF0F0F10)   // page background
val Surface1  = Color(0xFF18181B)   // card / list surface
val Surface2  = Color(0xFF242428)   // raised / input / active row
val Hairline  = Color(0x14FFFFFF)   // white @ 8%

// ── Text ──────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFEDEDED)
val TextSecondary = Color(0xFFA1A1A6)
val TextMuted     = Color(0xFF85858B)

// ── Accent (single, cobalt) ───────────────────────────────────────────────
val Accent = Color(0xFF2979FF)

// ── Semantic ─────────────────────────────────────────────────────────────
val DestructiveRed = Color(0xFFFF5252)
val WarmAmber      = Color(0xFFFFB74D)
val SuccessGreen   = Color(0xFF00C853)

// ── Legacy aliases kept for external import sites still using them ─────────
// (ChannelScreen, OnboardingScreen — will be replaced below, but keep
//  Brand500/700/900 so the compiler doesn't explode during the transition)
val Brand900 = Surface0
val Brand700 = Surface1
val Brand500 = Accent
