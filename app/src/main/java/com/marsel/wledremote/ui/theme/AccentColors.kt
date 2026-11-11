package com.marsel.wledremote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkOnAccent = Color(0xFF16100A)

/**
 * Цвет значка или текста поверх акцентного цвета: на светлом акценте тёмный,
 * на тёмном - белый. Так кнопка читается при любом цвете постера.
 */
fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.30f) DarkOnAccent else Color.White
