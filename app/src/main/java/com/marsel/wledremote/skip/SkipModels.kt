package com.marsel.wledremote.skip

enum class SkipKind { INTRO, RECAP, CREDITS }

/** AUTO: кнопка с отсчётом и автопрыжком. MANUAL: только кнопка, нажимает человек. */
enum class SkipMode { AUTO, MANUAL }

/** Отрезок, который можно пропустить. endMs = null значит "до конца серии". votes - сколько людей это разметили. */
data class SkipSegment(
    val kind: SkipKind,
    val startMs: Long,
    val endMs: Long?,
    val votes: Int,
    val source: String,
    /** Длина серии в базе (эталон). null - база её не отдаёт. */
    val refDurationMs: Long? = null
)

/**
 * Данные для одной серии.
 * postCreditsStartMs: где начинается сцена после титров (если её кто-то разметил), титры её не перепрыгивают.
 * autoAllowed = false, если номер серии мы не прочитали из текста, а пересчитали (догадка): тогда только кнопка.
 */
data class SkipTimeline(
    val segments: List<SkipSegment>,
    val postCreditsStartMs: Long? = null,
    val autoAllowed: Boolean = true
)

/** Что нужно сделать на ТВ. */
sealed interface SkipAction {
    data class SeekTo(val ms: Long) : SkipAction
    data object NextEpisode : SkipAction
}

/** Что показать на экране. progress 0..1 только для AUTO. */
data class SkipOffer(val kind: SkipKind, val mode: SkipMode, val progress: Float)

/** Команда на прыжок. returnToMs: куда вернуть по кнопке "Вернуть" (null - вернуть нельзя). */
data class SkipFire(val kind: SkipKind, val action: SkipAction, val returnToMs: Long?)

data class SkipStep(val offer: SkipOffer?, val fire: SkipFire?)
