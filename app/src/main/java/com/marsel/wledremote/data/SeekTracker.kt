package com.marsel.wledremote.data

import kotlin.math.abs

/**
 * Считает, куда перематывать при нажатиях и удержании кнопок.
 *
 * Плеер присылает позицию раз в секунду, а при удержании кнопки шаги идут чаще. Поэтому серия шагов
 * подряд (пауза между ними меньше chainMs) считается от прошлой цели, а не от устаревшей позиции плеера.
 *
 * Без Android-классов, чтобы логику можно было проверить обычными тестами.
 */
class SeekTracker(private val chainMs: Long = CHAIN_MS) {

    data class Result(
        /** Куда перемотать (мс от начала). */
        val targetMs: Long,
        /** Сколько всего перемотано в текущей серии нажатий (мс, со знаком) - для подсказки "+45 с". */
        val accumulatedMs: Long
    )

    private var pendingTargetMs = 0L
    private var accumulatedMs = 0L
    private var lastSeekAtMs = NEVER

    fun seekBy(deltaMs: Long, currentPositionMs: Long, durationMs: Long, nowMs: Long): Result {
        val chained = lastSeekAtMs != NEVER && nowMs - lastSeekAtMs < chainMs
        val base = if (chained) pendingTargetMs else currentPositionMs
        if (!chained) accumulatedMs = 0L

        val target = if (deltaMs >= 0) {
            // Вперёд: не ближе чем за END_MARGIN_MS до конца, чтобы не оборвать воспроизведение
            val upper = if (durationMs > 0) (durationMs - END_MARGIN_MS).coerceAtLeast(0L) else Long.MAX_VALUE
            maxOf(base, minOf(base + deltaMs, upper))
        } else {
            (base + deltaMs).coerceAtLeast(0L)
        }

        accumulatedMs += target - base
        pendingTargetMs = target
        lastSeekAtMs = nowMs
        return Result(target, accumulatedMs)
    }

    companion object {
        const val CHAIN_MS = 1500L

        /** Шаги перемотки (как в YouTube): назад 5 с, вперёд 15 с. Одни и те же в приложении и в уведомлении. */
        const val BACK_MS = 5_000L
        const val FORWARD_MS = 15_000L
        const val END_MARGIN_MS = 1000L
        private const val NEVER = Long.MIN_VALUE

        /** "+15 с", "−5 с", "+1:30". */
        fun formatSigned(ms: Long): String {
            val sign = when {
                ms > 0 -> "+"
                ms < 0 -> "−"
                else -> ""
            }
            val total = abs(ms) / 1000
            return if (total < 60) "$sign$total с" else "$sign${total / 60}:${"%02d".format(total % 60)}"
        }
    }
}
