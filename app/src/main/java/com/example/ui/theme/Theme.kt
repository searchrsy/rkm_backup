package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RkmColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = BgLightGray,
    onPrimaryContainer = NavyPrimary,
    secondary = BlueSecondary,
    onSecondary = SurfaceWhite,
    secondaryContainer = BgLightGray,
    onSecondaryContainer = BlueSecondary,
    background = SurfaceWhite,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = BgLightGray,
    onSurfaceVariant = TextGray,
    outline = BorderGray,
    error = RedError,
    onError = SurfaceWhite
)

@Composable
fun RkmTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RkmColorScheme,
        typography = Typography,
        content = content
    )
}
