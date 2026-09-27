package com.jonny.r5monitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed dark palette, dimmer than a phone usually is: this sits next to an EVF on set, and a bright
 * UI around the picture throws off how exposure reads.
 */
private val MonitorColors = darkColorScheme(
    primary = Color(0xFFFFB45C),
    onPrimary = Color(0xFF3A2200),
    primaryContainer = Color(0xFF3A2A14),
    onPrimaryContainer = Color(0xFFFFDDB5),
    secondary = Color(0xFF9CC7FF),
    onSecondary = Color(0xFF002B55),
    secondaryContainer = Color(0xFF1A2B40),
    onSecondaryContainer = Color(0xFFD2E4FF),
    background = Color(0xFF050507),
    onBackground = Color(0xFFE6E4EC),
    surface = Color(0xFF0E0E13),
    onSurface = Color(0xFFE6E4EC),
    surfaceVariant = Color(0xFF1A1A22),
    onSurfaceVariant = Color(0xFFA3A1AF),
    surfaceContainer = Color(0xFF14141B),
    surfaceContainerHigh = Color(0xFF1B1B24),
    outline = Color(0xFF3A3947),
    outlineVariant = Color(0xFF2A2934),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0010)
)

val RecordRed = Color(0xFFFF3B3B)

@Composable
fun MonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonitorColors, content = content)
}
