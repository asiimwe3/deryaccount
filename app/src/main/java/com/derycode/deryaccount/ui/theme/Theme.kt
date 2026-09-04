package com.derycode.deryaccount.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DeryCode/DeryAccount palette — forest green + deep navy, UGX-friendly
val Forest = Color(0xFF1A6B3C)
val ForestDark = Color(0xFF0A3D1F)
val Mint = Color(0xFF4ADE80)
val Navy = Color(0xFF0F172A)
val Amber = Color(0xFFF5C842)
val Danger = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = ForestDark,
    secondary = Navy,
    surface = Color(0xFFF8FAFC),
    background = Color(0xFFF8FAFC),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = ForestDark,
    primaryContainer = ForestDark,
    onPrimaryContainer = Mint,
    secondary = Color(0xFF94A3B8),
    surface = Color(0xFF111827),
    background = Color(0xFF0B1220),
    error = Color(0xFFF87171)
)

@Composable
fun DeryAccountTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
