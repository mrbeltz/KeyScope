package com.jonny.keyscope.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fixed dark palette rather than dynamic colour: this gets used in dark rooms next to other DJ
 * tools, and the readout needs the same contrast every time.
 */
private val KeyScopeColors = darkColorScheme(
    primary = Color(0xFF7FE0FF),
    onPrimary = Color(0xFF00293A),
    primaryContainer = Color(0xFF17384A),
    onPrimaryContainer = Color(0xFFCBEFFF),
    secondary = Color(0xFFC2B4FF),
    onSecondary = Color(0xFF241B4D),
    secondaryContainer = Color(0xFF2C2450),
    onSecondaryContainer = Color(0xFFE3DCFF),
    tertiary = Color(0xFFFFB68C),
    onTertiary = Color(0xFF4A2000),
    background = Color(0xFF08070F),
    onBackground = Color(0xFFE8E6F2),
    surface = Color(0xFF0F0E1A),
    onSurface = Color(0xFFE8E6F2),
    surfaceVariant = Color(0xFF1A1828),
    onSurfaceVariant = Color(0xFFA9A5BD),
    outline = Color(0xFF3A3651),
    error = Color(0xFFFF9E9E),
    onError = Color(0xFF4A0010)
)

private val KeyScopeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 88.sp,
        lineHeight = 92.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun KeyScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyScopeColors,
        typography = KeyScopeTypography,
        content = content
    )
}
