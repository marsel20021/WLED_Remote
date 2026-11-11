package com.marsel.wledremote.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Цвета из постера. Работает с уменьшенной картинкой, заданной массивом пикселей ARGB.
 * Никакого Android внутри, поэтому логику легко проверять отдельно от телефона.
 *
 * dominantAccent - главный цвет постера, подогнанный под тёмный интерфейс (для акцента).
 */
object PosterPalette {

    /** Если постер серый и ярких цветов в нём нет, берём привычный янтарный. */
    const val FALLBACK_ACCENT: Int = 0xFFF2A65A.toInt()

    private const val HUE_BUCKETS = 12        // круг оттенков делим на 12 «корзин» по 30 градусов
    private const val MIN_SATURATION = 0.25f  // серые и блёклые пиксели в счёт не идут
    private const val MIN_VALUE = 0.25f       // и совсем тёмные тоже
    private const val MIN_SHARE = 0.01f       // ярких пикселей должно быть хотя бы ~1% картинки
    private const val MIN_LUMINANCE = 0.22f   // акцент не должен быть слишком тёмным на тёмном фоне

    /**
     * Ищем самый «весомый» оттенок: каждый пиксель кладём в корзину по оттенку,
     * вес пикселя = насыщенность * яркость (яркие сочные цвета важнее тусклых).
     * Побеждает корзина с наибольшим весом, из неё собираем итоговый цвет.
     */
    fun dominantAccent(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FALLBACK_ACCENT

        val weight = FloatArray(HUE_BUCKETS)
        val sinSum = FloatArray(HUE_BUCKETS)
        val cosSum = FloatArray(HUE_BUCKETS)
        val satSum = FloatArray(HUE_BUCKETS)
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            rgbToHsv(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            if (sat < MIN_SATURATION || value < MIN_VALUE) continue

            val w = sat * value
            val bucket = ((hue / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            // Оттенок - это угол на круге, поэтому усредняем через синус и косинус
            // (иначе красный возле 0 и 360 градусов "склеился" бы в зелёный)
            val rad = hue * (PI.toFloat() / 180f)
            weight[bucket] += w
            sinSum[bucket] += sin(rad) * w
            cosSum[bucket] += cos(rad) * w
            satSum[bucket] += sat * w
        }

        var best = -1
        var bestWeight = 0f
        for (i in 0 until HUE_BUCKETS) {
            if (weight[i] > bestWeight) {
                best = i
                bestWeight = weight[i]
            }
        }
        if (best < 0 || bestWeight < pixels.size * MIN_SHARE) return FALLBACK_ACCENT

        var hue = atan2(sinSum[best], cosSum[best]) * (180f / PI.toFloat())
        if (hue < 0f) hue += 360f
        // Для тёмного интерфейса: цвет сочный (насыщенность 0.55-0.9) и светлый (яркость 0.96)
        val saturation = (satSum[best] / bestWeight).coerceIn(0.55f, 0.90f)
        return readable(hsvToRgb(hue, saturation, 0.96f))
    }

    // ---------- цвет: переводы и проверки ----------

    private fun rgbToHsv(argb: Int, out: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val d = maxC - minC

        var h = when {
            d == 0f -> 0f
            maxC == r -> 60f * (((g - b) / d) % 6f)
            maxC == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f

        out[0] = h
        out[1] = if (maxC == 0f) 0f else d / maxC
        out[2] = maxC
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val hp = (((hue % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val m = value - c

        val r1: Float
        val g1: Float
        val b1: Float
        when (hp.toInt()) {
            0 -> { r1 = c; g1 = x; b1 = 0f }
            1 -> { r1 = x; g1 = c; b1 = 0f }
            2 -> { r1 = 0f; g1 = c; b1 = x }
            3 -> { r1 = 0f; g1 = x; b1 = c }
            4 -> { r1 = x; g1 = 0f; b1 = c }
            else -> { r1 = c; g1 = 0f; b1 = x }
        }

        val r = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Относительная яркость цвета как её понимает глаз (формула из стандарта доступности WCAG). */
    private fun luminance(argb: Int): Float {
        fun channel(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel((argb shr 16) and 0xFF) +
            0.7152f * channel((argb shr 8) and 0xFF) +
            0.0722f * channel(argb and 0xFF)
    }

    /** Синие и фиолетовые оттенки получаются тёмными: подмешиваем белый, пока цвет не станет читаемым. */
    private fun readable(argb: Int): Int {
        var color = argb
        repeat(3) {
            if (luminance(color) < MIN_LUMINANCE) color = mixWithWhite(color, 0.18f)
        }
        return color
    }

    private fun mixWithWhite(argb: Int, amount: Float): Int {
        fun mix(v: Int): Int = (v + (255 - v) * amount + 0.5f).toInt().coerceIn(0, 255)
        val r = mix((argb shr 16) and 0xFF)
        val g = mix((argb shr 8) and 0xFF)
        val b = mix(argb and 0xFF)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
