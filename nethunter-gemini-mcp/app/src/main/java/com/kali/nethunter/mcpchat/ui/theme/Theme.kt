package com.kali.nethunter.mcpchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val darkScheme = darkColorScheme(
    primary = Color(0xFFE8C66D),
    secondary = Color(0xFF8FA8D0),
    tertiary = Color(0xFF4FC3D4),
    background = Color(0xFF0A1628),
    surface = Color(0xFF111D35),
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF1A3A6E),
    secondary = Color(0xFFC9A227),
    tertiary = Color(0xFF006978),
)

@Composable
fun AnubisTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDark) darkScheme else lightScheme,
        content = content,
    )
}
