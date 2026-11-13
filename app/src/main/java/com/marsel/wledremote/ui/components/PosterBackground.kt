package com.marsel.wledremote.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Размытый постер на весь экран за интерфейсом - мягкое цветное свечение, как в музыкальных плеерах.
 * Сверху лежит тонировка цветом темы, чтобы текст и кнопки читались.
 * Если постера нет (не нашёлся или ничего не играет) - пустота, виден обычный фон.
 *
 * Размытие работает на Android 12 и новее. На старых телефонах постер остаётся чётким,
 * поэтому там тонировку делаем темнее.
 *
 * [extraTint] - насколько ещё затемнить фон (0..1). На экране настроек много мелкого текста,
 * поэтому там постер приглушён сильнее, чем на «Пульте».
 */
@Composable
fun PosterBackground(
    look: PosterLook?,
    modifier: Modifier = Modifier,
    extraTint: Float = 0f
) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val extraDark = if (canBlur) 0f else 0.12f

    Crossfade(
        targetState = look,
        modifier = modifier.fillMaxSize(),
        animationSpec = tween(700),
        label = "poster"
    ) { current ->
        if (current != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = current.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // Чуть увеличиваем, чтобы размытые края не светились по бокам
                        .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
                        .blur(36.dp)
                )
                // Сверху постер виднее, к низу (где кнопки и бегунок) темнее
                val tint = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    tint.copy(alpha = (0.30f + extraDark + extraTint).coerceAtMost(0.95f)),
                                    tint.copy(alpha = (0.52f + extraDark + extraTint).coerceAtMost(0.95f)),
                                    tint.copy(alpha = (0.88f + extraTint).coerceAtMost(0.95f))
                                )
                            )
                        )
                )
            }
        }
    }
}
