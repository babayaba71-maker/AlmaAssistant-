package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AlmaDarkColorScheme =
  darkColorScheme(
    primary = CyanHolo,
    secondary = CyanAccent,
    tertiary = CyanHoloDark,
    background = AlmaDarkBackground,
    surface = AlmaDarkSurface,
    onBackground = AlmaOnDark,
    onSurface = AlmaOnDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black
  )

@Composable
fun AlmaTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = AlmaDarkColorScheme,
    typography = Typography,
    content = content
  )
}
