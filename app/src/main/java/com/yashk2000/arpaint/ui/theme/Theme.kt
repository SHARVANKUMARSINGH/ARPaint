package com.yashk2000.arpaint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ARDarkBackground = Color(0xCC121212)
val ARGlassSurface = Color(0xB31E1E1E)
val ARPrimary = Color(0xFF64B5F6)
val ARSecondary = Color(0xFFFF4081)
val ARError = Color(0xFFFF5252)
val ARSuccess = Color(0xFF4CAF50)
val ARWarning = Color(0xFFFFB74D)

private val DarkColorScheme = darkColorScheme(
    primary = ARPrimary,
    secondary = ARSecondary,
    background = Color.Transparent,
    surface = ARGlassSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ARError
)

@Composable
fun ARPaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
