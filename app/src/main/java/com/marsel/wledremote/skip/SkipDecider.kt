package com.marsel.wledremote.skip

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Числа и проверки, которые защищают от кривых данных. */
object SkipRules {
    const val COUNTDOWN_REF_MS = 3_000L     // отсчёт, если у отрезка есть эталон длины серии
    const val COUNTDOWN_NO_REF_MS = 5_000L  // отсчёт без эталона: тайм-коды могли сместиться
    const val SETTLE_MS = 2_500L            // после смены серии позицию не трогаем: первое состояние может быть от прошлой серии
    const val QUIET_MS = 2_500L             // после нашего прыжка не показываем ничего: плеер ещё присылает старую позицию
    const val MIN_REMAINING_MS = 3_000L     // если до конца отрезка меньше, прыгать уже незачем
    const val TRUSTED_VOTES = 2             // столько отметок хватает, чтобы считать отрезок надёжным
    const val MAX_INTRO_MS = 300_000L       // заставка или пересказ дольше 5 минут - явная ошибка в данных
    const val END_MARGIN_MS = 1_000L
    const val RESTART_BELOW_MS = 5_000L     // серию запустили с начала
    const val RESTART_AFTER_MS = 60_000L    // ...хотя до этого мы были уже далеко
    const val SYNC_TOLERANCE_MS = 2_000L    // разница длины файла и эталона до этого - считаем совпадением
    const val MAX_SHIFT_MS = 30_000L        // больше этого - другая версия серии, не пропускаем

    fun isUsable(seg: SkipSegment, durationMs: Long): Boolean {
        if (seg.startMs < 0) return false
        val end = seg.endMs
        return when (seg.kind) {
            SkipKind.INTRO, SkipKind.RECAP -> {
                if (end == null || end <= seg.startMs) {
                    false
                } else if (end - seg.startMs > MAX_INTRO_MS) {
                    false
                } else {
                    durationMs <= 0 || seg.startMs <= durationMs / 2
                }
            }
            SkipKind.CREDITS -> {
                if (durationMs <= 0) {
                    false
                } else if (end != null && end <= seg.startMs) {
                    false
                } else {
                    seg.startMs >= durationMs / 2
                }
            }
        }
    }

    /**
     * Подгоняет отрезок под длину файла. Если у отрезка есть эталон, считаем разницу:
     * совпало - как есть, файл длиннее на вставку (обычно студия озвучки в начале) - двигаем на разницу,
     * иначе отрезок не подходит. null - использовать нельзя.
     */
    fun fit(seg: SkipSegment, durationMs: Long): SkipSegment? {
        val ref = seg.refDurationMs
        val moved = if (ref == null || ref <= 0) {
            seg
        } else {
            if (durationMs <= 0) return null
            val delta = durationMs - ref
            if (abs(delta) <= SYNC_TOLERANCE_MS) {
                seg
            } else if (delta in (SYNC_TOLERANCE_MS + 1)..MAX_SHIFT_MS) {
                seg.copy(startMs = seg.startMs + delta, endMs = seg.endMs?.plus(delta))
            } else {
                return null
            }
        }
        return if (isUsable(moved, durationMs)) moved else null
    }

    fun countdownFor(seg: SkipSegment): Long {
        val ref = seg.refDurationMs
        return if (ref != null && ref > 0) COUNTDOWN_REF_MS else COUNTDOWN_NO_REF_MS
    }
}

/**
 * Снимок того, что сейчас играет. nowMs - монотонное время телефона.
 * allowUncertain: пропускать ли отрезки, у которых только одна отметка.
 */
data class SkipSnapshot(
    val episodeKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val connected: Boolean,
    val hasNextEpisode: Boolean,
    val nowMs: Long,
    val allowUncertain: Boolean = true
)

/**
 * Решает, что показать и когда прыгать. Без Android, чтобы проверить обычными тестами.
 * Каждый отрезок срабатывает один раз за серию; повторный просмотр с начала это сбрасывает.
 */
class SkipDecider {
    private class Active(val seg: SkipSegment, val countdownMs: Long)

    private var key: String? = null
    private var timeline: SkipTimeline? = null
    private var keyChangedAt = 0L
    private var quietUntil = 0L
    private var maxPosition = 0L
    private val done = HashSet<SkipKind>()
    private var countdownKind: SkipKind? = null
    private var countdownStart = 0L
    private var offeredKind: SkipKind? = null

    /** Данные для серии готовы (или их нет: timeline = null). Ответ для чужой серии игнорируем. */
    fun setTimeline(forKey: String, timeline: SkipTimeline?) {
        if (forKey == key) this.timeline = timeline
    }

    fun update(s: SkipSnapshot): SkipStep {
        if (s.episodeKey != key) reset(s)
        if (!s.connected) return idle()
        val tl = timeline ?: return idle()
        if (s.nowMs - keyChangedAt < SkipRules.SETTLE_MS) return idle()
        if (s.nowMs < quietUntil) return idle()

        if (s.positionMs < SkipRules.RESTART_BELOW_MS && maxPosition > SkipRules.RESTART_AFTER_MS) {
            done.clear()
            maxPosition = 0L
        }
        maxPosition = max(maxPosition, s.positionMs)

        val act = activeSegment(tl, s) ?: return idle()
        val seg = act.seg
        offeredKind = seg.kind

        if (!s.isPlaying) {
            // Пауза: кнопка остаётся, отсчёт обнуляется и пойдёт заново после продолжения
            countdownKind = null
            return SkipStep(SkipOffer(seg.kind, 0f, secondsLeft(act.countdownMs)), null)
        }
        if (countdownKind != seg.kind) {
            countdownKind = seg.kind
            countdownStart = s.nowMs
        }
        val elapsed = s.nowMs - countdownStart
        if (elapsed < act.countdownMs) {
            val progress = (elapsed.toFloat() / act.countdownMs).coerceIn(0f, 1f)
            return SkipStep(SkipOffer(seg.kind, progress, secondsLeft(act.countdownMs - elapsed)), null)
        }
        return SkipStep(null, fire(seg, tl, s))
    }

    /** Человек нажал кнопку. Возвращает команду, если сейчас есть что пропускать. */
    fun skipNow(s: SkipSnapshot): SkipFire? {
        if (s.episodeKey != key || !s.connected) return null
        val tl = timeline ?: return null
        val act = activeSegment(tl, s) ?: return null
        return fire(act.seg, tl, s)
    }

    /** Человек нажал ✕: этот отрезок в этой серии больше не предлагаем. */
    fun cancel() {
        offeredKind?.let { done.add(it) }
        countdownKind = null
        offeredKind = null
    }

    private fun secondsLeft(ms: Long): Int = max(1, ceil(ms / 1000.0).toInt())

    private fun reset(s: SkipSnapshot) {
        key = s.episodeKey
        timeline = null
        keyChangedAt = s.nowMs
        quietUntil = 0L
        maxPosition = 0L
        done.clear()
        countdownKind = null
        offeredKind = null
    }

    private fun idle(): SkipStep {
        countdownKind = null
        offeredKind = null
        return SkipStep(null, null)
    }

    private fun activeSegment(tl: SkipTimeline, s: SkipSnapshot): Active? {
        for (kind in SkipKind.values()) {
            if (kind in done) continue
            // На последней серии титры не пропускаем: непонятно, что сделает ТВ в конце плейлиста
            if (kind == SkipKind.CREDITS && !s.hasNextEpisode) continue
            val seg = tl.segments.asSequence()
                .filter { it.kind == kind }
                .mapNotNull { SkipRules.fit(it, s.durationMs) }
                .firstOrNull() ?: continue
            if (seg.votes < SkipRules.TRUSTED_VOTES && !s.allowUncertain) continue
            val end = effectiveEnd(seg, s.durationMs) ?: continue
            if (s.positionMs >= seg.startMs && end - s.positionMs > SkipRules.MIN_REMAINING_MS) {
                return Active(seg, SkipRules.countdownFor(seg))
            }
        }
        return null
    }

    private fun effectiveEnd(seg: SkipSegment, durationMs: Long): Long? {
        val end = seg.endMs ?: return if (durationMs > 0) durationMs else null
        return if (durationMs > 0) min(end, durationMs) else end
    }

    private fun fire(seg: SkipSegment, tl: SkipTimeline, s: SkipSnapshot): SkipFire {
        done.add(seg.kind)
        countdownKind = null
        offeredKind = null
        quietUntil = s.nowMs + SkipRules.QUIET_MS
        return when (seg.kind) {
            SkipKind.INTRO, SkipKind.RECAP ->
                SkipFire(seg.kind, SkipAction.SeekTo(seg.endMs ?: s.positionMs), seg.startMs)
            SkipKind.CREDITS -> creditsFire(seg, tl, s)
        }
    }

    private fun creditsFire(seg: SkipSegment, tl: SkipTimeline, s: SkipSnapshot): SkipFire {
        var target = seg.endMs
        val post = tl.postCreditsStartMs
        // Сцену после титров не перепрыгиваем: останавливаемся у её начала
        if (post != null && post > s.positionMs && (target == null || target > post)) target = post
        if (target != null) return SkipFire(seg.kind, SkipAction.SeekTo(target), seg.startMs)
        if (s.hasNextEpisode) return SkipFire(seg.kind, SkipAction.NextEpisode, null)
        val last = max(s.positionMs, s.durationMs - SkipRules.END_MARGIN_MS)
        return SkipFire(seg.kind, SkipAction.SeekTo(last), seg.startMs)
    }
}
