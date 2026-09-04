package com.derycode.deryaccount.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DeryAccount dark palette — near-black + forest green, matches app design
val DaBlack = Color(0xFF06100B)          // page background
val DaSurface = Color(0xFF0D1F15)        // cards
val DaSurface2 = Color(0xFF122A1C)       // slightly lighter cards / chips
val DaHeader = Color(0xFF0E3B22)         // top app bar / bottom nav
val DaGreen = Color(0xFF22C55E)          // primary accent, positive amounts
val DaGreenDeep = Color(0xFF16A34A)
val DaRed = Color(0xFFEF4444)            // expenses / payments / negative
val DaBlue = Color(0xFF3B82F6)
val DaAmber = Color(0xFFF59E0B)
val DaTextPrimary = Color(0xFFEAF3EC)
val DaTextMuted = Color(0xFFB9C6BE)
val DaOutline = Color(0xFF1E3A28)

private val DaColors = darkColorScheme(
    primary = DaGreen,
    onPrimary = Color(0xFF03150A),
    primaryContainer = DaSurface2,
    onPrimaryContainer = DaGreen,
    secondary = DaBlue,
    background = DaBlack,
    onBackground = DaTextPrimary,
    surface = DaSurface,
    onSurface = DaTextPrimary,
    surfaceVariant = DaSurface2,
    onSurfaceVariant = DaTextMuted,
    outline = DaOutline,
    error = DaRed
)

/** DeryAccount is always dark — matches the app's fixed enterprise POS look. */
@Composable
fun DeryAccountTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DaColors, content = content)
}
