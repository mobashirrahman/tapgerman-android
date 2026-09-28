package com.lingodeck.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFF7B32B)
private val Ink = Color(0xFF1F2937)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Amber,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF6B7280),
    surface = Color(0xFFFDFCF9),
    onSurface = Ink,
    background = Color(0xFFFDFCF9),
    onBackground = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A3222),
    onPrimaryContainer = Amber,
    secondary = Color(0xFF9CA3AF),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFEDE9E0),
    background = Color(0xFF101216),
    onBackground = Color(0xFFEDE9E0),
)

@Composable
fun LingoDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
