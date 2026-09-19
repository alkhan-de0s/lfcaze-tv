package com.lfcaze.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LfcRed,
    secondary = LfcGold,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = TextPrimary,
    onSecondary = BgDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun LFCAZETVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
