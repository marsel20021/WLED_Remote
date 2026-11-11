package com.marsel.wledremote.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.marsel.wledremote.data.PosterPalette
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.PosterTitle
import com.marsel.wledremote.model.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Всё, что приложение берёт из постера: сама картинка, главный цвет (акцент интерфейса)
 * и ряд цветов для полоски. Считается один раз и раздаётся всем экранам.
 */
@Immutable
class PosterLook(
    val image: ImageBitmap,
    val accent: Color,
    val strip: List<Color>
)

private const val SAMPLE_WIDTH = 32   // постер уменьшаем до 32x48 точек - для подсчёта цветов хватает
private const val SAMPLE_HEIGHT = 48
private const val STRIP_COUNT = 24    // сколько «диодов» рисуем в полоске

/**
 * Ищет постер для того, что сейчас играет, и разбирает его на цвета.
 * Пока ищется или если не нашёлся, возвращает null (на экране остаётся обычный фон).
 */
@Composable
fun rememberPosterLook(posters: PosterRepository, playback: PlaybackUiState): PosterLook? {
    val rawTitle = playback.movieTitle?.takeIf { it.isNotBlank() } ?: playback.title
    val query = remember(rawTitle) { PosterTitle.parse(rawTitle) }
    // Пока ключ тот же (идёт та же серия или соседняя), заново ничего не ищем
    val key = query?.let { "${it.title}|${it.year}" }

    val look by produceState<PosterLook?>(initialValue = null, key) {
        value = if (key == null) null else posters.load(rawTitle)?.let { decodeLook(it) }
    }
    return look
}

private suspend fun decodeLook(bytes: ByteArray): PosterLook? = withContext(Dispatchers.Default) {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null

    val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
    val pixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
    small.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
    if (small !== bitmap) small.recycle()

    PosterLook(
        image = bitmap.asImageBitmap(),
        accent = Color(PosterPalette.dominantAccent(pixels)),
        strip = PosterPalette.stripColors(pixels, SAMPLE_WIDTH, SAMPLE_HEIGHT, STRIP_COUNT).map { Color(it) }
    )
}
