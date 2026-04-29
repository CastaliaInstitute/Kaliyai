package com.kali.nethunter.mcpchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme with cyan accent - Kaliyai cyberpunk aesthetic
private val darkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),           // Cyan primary
    onPrimary = Color(0xFF000000),          // Black on cyan
    primaryContainer = Color(0xFF00363D),   // Dark cyan container
    onPrimaryContainer = Color(0xFF00E5FF), // Cyan text

    secondary = Color(0xFF00B8D4),        // Darker cyan
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF002D36),
    onSecondaryContainer = Color(0xFF00B8D4),

    tertiary = Color(0xFF64FFDA),         // Teal accent
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF004D40),
    onTertiaryContainer = Color(0xFF64FFDA),

    background = Color(0xFF0D0D0D),        // Deep black background
    onBackground = Color(0xFF00E5FF),      // Cyan on black

    surface = Color(0xFF1A1A1A),          // Dark gray surface
    onSurface = Color(0xFF00E5FF),        // Cyan text
    surfaceVariant = Color(0xFF252525),    // Slightly lighter surface
    onSurfaceVariant = Color(0xFF80DEEA), // Light cyan

    error = Color(0xFFFF5252),            // Red error
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF3D0000),
    onErrorContainer = Color(0xFFFF5252),

    outline = Color(0xFF00B8D4),          // Cyan outline
    outlineVariant = Color(0xFF005F6B),   // Dark cyan outline
    scrim = Color(0xFF000000),            // Black scrim
)

@Composable
fun KaliyaiTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkScheme,
        typography = KaliyaiTypography,
        content = content,
    )
}
