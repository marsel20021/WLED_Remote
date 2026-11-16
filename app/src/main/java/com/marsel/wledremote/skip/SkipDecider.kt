package com.marsel.wledremote.skip

import kotlin.math.max
import kotlin.math.min

/** Числа и проверки, которые защищают от кривых данных. */
object SkipRules {
    const val COUNTDOWN_MS = 3_000L
    const val SETTLE_MS = 2_500L          // после смены серии позицию не трогаем: первое состояние может быть от прошлой серии
    const val QUIET_MS = 2_500L           // после нашего прыжка не показываем ничего: плеер ещё присылает старую позицию
    const val MIN_REMAINING_MS = 3_000L   // если до конца отрезка меньше, прыгать уже незачем
    const val AUTO_MIN_VOTES = 2          // автопрыжок только если отрезок разметили минимум двое
    const val MAX_INTRO_MS = 300_000L     // заставка или пересказ дольше 5 минут - явная ошибка в данных
    const val END_MARGIN_MS = 1_000L
    const val RESTART_BELOW_MS = 5_000L   // серию запустили с начала
    const val RESTART_AFTER_MS = 60_000L  // ...хотя до этого мы были уже далеко

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
}

/** Снимок того, что сейчас играет. nowMs - монотонное время телефона. */
data class SkipSnapshot(
    val episodeKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val connected: Boolean,
    val hasNextEpisode: Boolean,
    val nowMs: Long
)

/**
 * Решает, что показать и когда прыгать. Без Android, чтобы проверить обычными тестами.
 * Каждый отрезок срабатывает один раз за серию; повторный просмотр с начала это сбрасывает.
 */
class SkipDecider {
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

        val seg = activeSegment(tl, s) ?: return idle()
        offeredKind = seg.kind

        val auto = tl.autoAllowed && seg.votes >= SkipRules.AUTO_MIN_VOTES
        if (!auto) {
            countdownKind = null
            return SkipStep(SkipOffer(seg.kind, SkipMode.MANUAL, 0f), null)
        }
        if (!s.isPlaying) {
            // Пауза: кнопка остаётся, отсчёт обнуляется и пойдёт заново после продолжения
            countdownKind = null
            return SkipStep(SkipOffer(seg.kind, SkipMode.AUTO, 0f), null)
        }
        if (countdownKind != seg.kind) {
            countdownKind = seg.kind
            countdownStart = s.nowMs
        }
        val progress = (s.nowMs - countdownStart).toFloat() / SkipRules.COUNTDOWN_MS
        if (progress < 1f) {
            return SkipStep(SkipOffer(seg.kind, SkipMode.AUTO, progress.coerceIn(0f, 1f)), null)
        }
        return SkipStep(null, fire(seg, tl, s))
    }

    /** Человек нажал кнопку. Возвращает команду, если сейчас есть что пропускать. */
    fun skipNow(s: SkipSnapshot): SkipFire? {
        if (s.episodeKey != key || !s.connected) return null
        val tl = timeline ?: return null
        val seg = activeSegment(tl, s) ?: return null
        return fire(seg, tl, s)
    }

    /** Человек нажал ✕: этот отрезок в этой серии больше не предлагаем. */
    fun cancel() {
        offeredKind?.let { done.add(it) }
        countdownKind = null
        offeredKind = null
    }

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

    private fun activeSegment(tl: SkipTimeline, s: SkipSnapshot): SkipSegment? {
        for (kind in SkipKind.values()) {
            if (kind in done) continue
            val seg = tl.segments.firstOrNull { it.kind == kind && SkipRules.isUsable(it, s.durationMs) } ?: continue
            val end = effectiveEnd(seg, s.durationMs) ?: continue
            if (s.positionMs >= seg.startMs && end - s.positionMs > SkipRules.MIN_REMAINING_MS) return seg
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
