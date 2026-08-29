package com.godseye.view.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF), secondary = Color(0xFF0A0E1A),
    background = Color(0xFF060A14), surface = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE8EEF8)
)
@Composable fun GodEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
