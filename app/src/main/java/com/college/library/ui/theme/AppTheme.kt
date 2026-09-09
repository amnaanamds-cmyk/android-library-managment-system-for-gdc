package com.college.library.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.college.library.R

/**
 * AppTheme applies dynamic theming based on user preferences.
 * Supports dark mode toggle and dynamic font scaling.
 */
@Composable
fun AppTheme(
    // User selected dark mode, null falls back to system setting
    darkModeEnabled: Boolean? = null,
    // Font scaling factor from settings (default 1.0 = normal size)
    fontScale: Float = 1f,
    /**
     * Material You wallpaper colours. Off by default, deliberately.
     *
     * With this on, every Android 12+ phone derived the whole palette from the
     * user's wallpaper, so the app looked different on every device and matched
     * neither the desktop client nor the web portal. For a system deployed
     * across ~300 government colleges, looking like one product matters more
     * than matching someone's home screen.
     */
    dynamicColor: Boolean = false,
    /**
     * Reading direction for the active language. Urdu is written right-to-left,
     * and an Urdu UI laid out left-to-right is not merely untidy — labels sit on
     * the wrong side of their values and every row reads backwards.
     */
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Determine dark theme: explicit user preference overrides system value
    val darkTheme = when (darkModeEnabled) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }
    // Choose appropriate color scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Update status bar colours to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The surface, not the accent: a status bar painted in the primary
            // colour turned pale blue the moment `primary` stopped being navy.
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Apply user‑specified font scaling via LocalDensity
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = fontScale
        ),
        LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// --- Colour scheme ---------------------------------------------------------
//
// Built from the shared palette in Color.kt. Deliberately restrained: `primary`
// is the one accent, and `error` is the only other colour Material will reach
// for on its own.

val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = AccentOn,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    secondary = BodyText,
    onSecondary = Color.White,
    secondaryContainer = SurfaceAlt,
    onSecondaryContainer = Ink,
    tertiary = Positive,
    onTertiary = Color.White,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
    background = AppBackground,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = Muted,
    outline = LineStrong,
    outlineVariant = Line,
)

val DarkColors = darkColorScheme(
    // A step lighter than the light scheme's accent: #1D4ED8 on #1E293B fails
    // contrast for anything smaller than a heading.
    primary = AccentTextDark,
    onPrimary = Ink,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = InkDark,
    secondary = BodyTextDark,
    onSecondary = Ink,
    secondaryContainer = SurfaceAltDark,
    onSecondaryContainer = InkDark,
    tertiary = PositiveDark,
    onTertiary = Ink,
    error = DangerDark,
    onError = Ink,
    errorContainer = Color(0xFF3B1D1D),
    onErrorContainer = DangerDark,
    background = AppBackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    outlineVariant = LineDark,
)

// --- Font resources --------------------------------------------------------
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val PlayfairDisplay = FontFamily(
    Font(googleFont = GoogleFont("Playfair Display"), fontProvider = provider)
)

val Lato = FontFamily(
    Font(googleFont = GoogleFont("Lato"), fontProvider = provider)
)

// --- Typography ------------------------------------------------------------
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
