package com.college.library.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One palette, shared with the desktop app (gdc_desktop/ui/theme.py) and the
 * web portal (web-app/src/app/globals.css).
 *
 * The rule is the same on all three: neutrals do the layout, [Accent] marks the
 * one interactive thing on a screen, and [Positive]/[Warning]/[Danger] mark
 * state and nothing else. A card is not coloured because it is a card — when
 * every card is coloured, the overdue one stops standing out, and that is the
 * only one anybody needs to see at a glance.
 *
 * The historical names below ([Gold], [CardBlue], [CardGreen], …) are kept
 * because roughly sixty call sites across thirty screens use them. They are
 * aliases now, mapped by the role each was actually playing rather than by the
 * hue its name claims: [Gold] was the accent, [CardGreen] meant "available",
 * [CardOrange] meant "needs attention". Prefer the semantic names in new code.
 */

// ── Neutrals ────────────────────────────────────────────────────────────────
val Ink = Color(0xFF0F172A)         // headings, figures
val BodyText = Color(0xFF334155)
val Muted = Color(0xFF64748B)       // labels, captions
val Line = Color(0xFFE2E8F0)
val LineStrong = Color(0xFFCBD5E1)
val Surface = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFF1F5F9)
val AppBackground = Color(0xFFF8FAFC)

val InkDark = Color(0xFFF1F5F9)
val BodyTextDark = Color(0xFFCBD5E1)
val MutedDark = Color(0xFF94A3B8)
val LineDark = Color(0xFF334155)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceAltDark = Color(0xFF172033)
val AppBackgroundDark = Color(0xFF0F172A)

// ── Accent ──────────────────────────────────────────────────────────────────
// Two values, because one colour cannot both read as small text on a dark
// surface and carry white text on top of itself.
val Accent = Color(0xFF1D4ED8)      // fills, primary buttons
val AccentOn = Color(0xFFFFFFFF)
val AccentText = Color(0xFF1D4ED8)  // accent used as type or an icon tint
val AccentTextDark = Color(0xFF60A5FA)
val AccentSoft = Color(0xFFEFF6FF)
val AccentSoftDark = Color(0xFF1E3A5F)

// ── State. These three, and only these three, carry meaning. ────────────────
val Positive = Color(0xFF15803D)
val PositiveSoft = Color(0xFFF0FDF4)
val Warning = Color(0xFFB45309)
val WarningSoft = Color(0xFFFFFBEB)
val Danger = Color(0xFFB91C1C)
val DangerSoft = Color(0xFFFEF2F2)

val PositiveDark = Color(0xFF4ADE80)
val WarningDark = Color(0xFFFBBF24)
val DangerDark = Color(0xFFF87171)

// ── Legacy aliases ──────────────────────────────────────────────────────────
val NavyBlue = Ink
val LightNavy = BodyText
// Was #FFD700, chosen because it read on the navy app bar. The app bars are
// now surface-coloured (matching the web portal's header), so what this needs
// to be is the accent.
val Gold = AccentText
val SoftGold = AccentSoftDark
val CardBlue = Accent
val CardGreen = Positive
val CardOrange = Warning
val CardPurple = Accent
val DangerRed = Danger
val DangerLight = DangerSoft
