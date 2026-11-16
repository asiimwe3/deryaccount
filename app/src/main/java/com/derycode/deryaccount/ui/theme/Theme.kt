package com.derycode.deryaccount.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DeryAccount palette — clean white, modelled on derycode.online.
// Names are unchanged from the original palette so every screen picks up the
// new look without touching its code.
val DaBlack = Color(0xFFF6F8FC)          // page background
val DaSurface = Color(0xFFFFFFFF)        // cards
val DaSurface2 = Color(0xFFF1F5FD)       // chips, table headers, soft panels
val DaHeader = Color(0xFFFFFFFF)         // top bar / bottom nav
val DaGreen = Color(0xFF16A34A)          // money in, confirmed
val DaGreenDeep = Color(0xFF128746)
val DaRed = Color(0xFFDC2626)            // expenses, money out
val DaBlue = Color(0xFF1553C7)           // primary action, links
val DaAmber = Color(0xFFB45309)          // warnings, balances due
val DaTextPrimary = Color(0xFF0F1B2D)    // headings and body
val DaTextMuted = Color(0xFF667085)      // labels, secondary text
val DaOutline = Color(0xFFE4E9F2)        // hairlines and borders

private val DaColors = lightColorScheme(
    primary = DaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF),
    onPrimaryContainer = DaBlue,
    secondary = DaGreen,
    onSecondary = Color.White,
    background = DaBlack,
    onBackground = DaTextPrimary,
    surface = DaSurface,
    onSurface = DaTextPrimary,
    surfaceVariant = DaSurface2,
    onSurfaceVariant = DaTextMuted,
    outline = DaOutline,
    error = DaRed
)

/** DeryAccount now uses the clean white DeryCode look. */
@Composable
fun DeryAccountTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DaColors, content = content)
}
