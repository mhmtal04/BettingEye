package com.aibettingeye.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink = Color(0xFF0E1320)
    val Panel = Color(0xFF161D2E)
    val PanelHigh = Color(0xFF1F2840)
    val Amber = Color(0xFFF2B84B)
    val Teal = Color(0xFF5FD0C6)
    val Text = Color(0xFFE8ECF4)
    val TextDim = Color(0xFF9AA5BC)
    val Warn = Color(0xFFE5736A)
}

@Composable
fun BettingEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Amber,
            onPrimary = Palette.Ink,
            secondary = Palette.Teal,
            onSecondary = Palette.Ink,
            background = Palette.Ink,
            onBackground = Palette.Text,
            surface = Palette.Panel,
            onSurface = Palette.Text,
            surfaceVariant = Palette.PanelHigh,
            onSurfaceVariant = Palette.TextDim,
            error = Palette.Warn
        ),
        content = content
    )
}
