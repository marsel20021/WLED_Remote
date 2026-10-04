package com.marsel.wledremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WledDarkScheme = darkColorScheme(
    primary = AmberGlow,
    onPrimary = Color(0xFF241200),
    secondary = AmberGlowDim,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = SurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor
)

// Приложение задумано как "пульт для тёмной комнаты" - светлая тема сознательно
// не поддерживается, чтобы не размывать характер интерфейса.
@Composable
fun WLEDRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WledDarkScheme,
        typography = WledTypography,
        content = content
    )
}