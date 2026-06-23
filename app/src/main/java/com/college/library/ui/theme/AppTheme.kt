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
    dynamicColor: Boolean = true,
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Apply user‑specified font scaling via LocalDensity
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// --- Colour palette --------------------------------------------------------
val DeepNavy = Color(0xFF1A237E)
val GoldLight = Color(0xFFFFD700)
val GoldDark = Color(0xFFD4AF37)
val GreenAvailable = Color(0xFF4CAF50)
val RedIssued = Color(0xFFD32F2F)
val BackgroundLight = Color(0xFFF5F5F5)
val BackgroundDark = Color(0xFF121212)

val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    secondary = GoldLight,
    onSecondary = Color.Black,
    tertiary = GreenAvailable,
    error = RedIssued,
    background = BackgroundLight,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF5C6BC0), // Lighter navy for readability in dark mode
    onPrimary = Color.White,
    secondary = GoldDark,
    onSecondary = Color.Black,
    tertiary = Color(0xFF81C784),
    error = Color(0xFFE57373),
    background = BackgroundDark,
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White
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
