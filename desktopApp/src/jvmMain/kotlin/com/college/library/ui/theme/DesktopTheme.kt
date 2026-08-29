package com.college.library.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColors(
    primary = Color(0xFF1E3A8A), // Professional Deep Blue
    primaryVariant = Color(0xFF172554),
    secondary = Color(0xFFD4AF37), // Professional Gold
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    error = Color(0xFFDC2626)
)

private val DarkColorPalette = darkColors(
    primary = Color(0xFF3B82F6), // Brighter Blue for Dark Mode
    primaryVariant = Color(0xFF1E3A8A),
    secondary = Color(0xFFFFD700), // Brighter Gold for Dark Mode
    background = Color(0xFF111827), // Deep Dark Gray/Blue
    surface = Color(0xFF1F2937), // Slightly lighter surface
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF9FAFB),
    onSurface = Color(0xFFF9FAFB),
    error = Color(0xFFEF4444)
)

@Composable
fun DesktopTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}
