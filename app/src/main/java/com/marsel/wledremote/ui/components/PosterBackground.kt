package com.marsel.wledremote.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.PosterTitle
import com.marsel.wledremote.model.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Постер фильма на весь экран за интерфейсом, слегка затонированный цветом темы,
 * чтобы текст и кнопки читались. Если постер не нашёлся или ничего не играет, рисуется пустота
 * (виден обычный фон приложения). Смена постера идёт плавно.
 *
 * Берёт постер из того же кэша, что и плашка в уведомлениях, поэтому второй раз в сеть не ходит.
 */
@Composable
fun PosterBackground(
    posters: PosterRepository,
    playback: PlaybackUiState,
    modifier: Modifier = Modifier
) {
    val rawTitle = playback.movieTitle?.takeIf { it.isNotBlank() } ?: playback.title
    val query = remember(rawTitle) { PosterTitle.parse(rawTitle) }
    // Ключ поиска: пока он тот же (идёт та же серия или соседняя), заново ничего не ищем
    val key = query?.let { "${it.title}|${it.year}" }

    val poster by produceState<ImageBitmap?>(initialValue = null, key) {
        val bytes = if (key != null) posters.load(rawTitle) else null
        value = bytes?.let {
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
        }
    }

    Crossfade(
        targetState = poster,
        modifier = modifier.fillMaxSize(),
        animationSpec = tween(700),
        label = "poster"
    ) { bitmap ->
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Тонировка: сверху постер виднее, к низу (где кнопки и бегунок) темнее
                val tint = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    tint.copy(alpha = 0.45f),
                                    tint.copy(alpha = 0.65f),
                                    tint.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
            }
        }
    }
}
