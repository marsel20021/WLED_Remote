package com.marsel.wledremote.skip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipDeciderTest {
    private val duration = 1_440_000L // 24 минуты
    private val intro = SkipSegment(SkipKind.INTRO, 80_000, 170_000, votes = 3, source = "T")
    private val credits = SkipSegment(SkipKind.CREDITS, 1_332_000, 1_422_000, votes = 3, source = "T")

    private fun snap(
        pos: Long,
        now: Long,
        key: String = "e1",
        playing: Boolean = true,
        connected: Boolean = true,
        next: Boolean = true,
        dur: Long = duration
    ) = SkipSnapshot(key, pos, dur, playing, connected, next, now)

    /** Серия началась в момент 0, данные пришли, ждём пока пройдёт защитная пауза. */
    private fun ready(tl: SkipTimeline): SkipDecider {
        val d = SkipDecider()
        d.update(snap(0, 0))
        d.setTimeline("e1", tl)
        return d
    }

    @Test
    fun `без данных ничего не показываем`() {
        val d = SkipDecider()
        assertNull(d.update(snap(100_000, 10_000)).offer)
    }

    @Test
    fun `сразу после смены серии позицию не трогаем`() {
        val d = ready(SkipTimeline(listOf(intro)))
        // позиция попала в заставку, но прошло меньше защитной паузы
        assertNull(d.update(snap(100_000, 1_000)).offer)
        assertNotNull(d.update(snap(100_000, 3_000)).offer)
    }

    @Test
    fun `автоотсчёт идёт три секунды и потом прыгает на конец заставки`() {
        val d = ready(SkipTimeline(listOf(intro)))
        val a = d.update(snap(100_000, 3_000))
        assertEquals(SkipMode.AUTO, a.offer!!.mode)
        assertEquals(0f, a.offer!!.progress, 0.001f)
        val b = d.update(snap(101_500, 4_500))
        assertEquals(0.5f, b.offer!!.progress, 0.001f)
        val c = d.update(snap(103_000, 6_000))
        assertNull(c.offer)
        assertEquals(SkipAction.SeekTo(170_000), c.fire!!.action)
        assertEquals(80_000L, c.fire!!.returnToMs)
    }

    @Test
    fun `до начала заставки кнопки нет, как только дошли появляется`() {
        val d = ready(SkipTimeline(listOf(intro)))
        assertNull(d.update(snap(50_000, 3_000)).offer)
        assertNull(d.update(snap(79_000, 4_000)).offer)
        assertNotNull(d.update(snap(80_000, 5_000)).offer)
    }

    @Test
    fun `если до конца отрезка меньше трёх секунд, кнопки нет`() {
        val d = ready(SkipTimeline(listOf(intro)))
        assertNull(d.update(snap(167_500, 3_000)).offer)
    }

    @Test
    fun `мало отметок или догадка по номеру - только кнопка без прыжка`() {
        val few = ready(SkipTimeline(listOf(intro.copy(votes = 1))))
        val s1 = few.update(snap(100_000, 3_000))
        assertEquals(SkipMode.MANUAL, s1.offer!!.mode)
        assertNull(few.update(snap(110_000, 30_000)).fire)

        val guess = ready(SkipTimeline(listOf(intro), autoAllowed = false))
        assertEquals(SkipMode.MANUAL, guess.update(snap(100_000, 3_000)).offer!!.mode)
        assertNull(guess.update(snap(110_000, 30_000)).fire)
    }

    @Test
    fun `ручное нажатие пропускает сразу`() {
        val d = ready(SkipTimeline(listOf(intro.copy(votes = 1))))
        d.update(snap(100_000, 3_000))
        val fire = d.skipNow(snap(101_000, 4_000))
        assertEquals(SkipAction.SeekTo(170_000), fire!!.action)
        assertNull(d.skipNow(snap(171_000, 5_000))) // уже сработало
    }

    @Test
    fun `пауза останавливает отсчёт, после продолжения он идёт заново`() {
        val d = ready(SkipTimeline(listOf(intro)))
        d.update(snap(100_000, 3_000))
        val paused = d.update(snap(101_000, 5_000, playing = false))
        assertEquals(0f, paused.offer!!.progress, 0.001f)
        assertNull(paused.fire)
        val resumed = d.update(snap(101_000, 8_000))
        assertEquals(0f, resumed.offer!!.progress, 0.001f)
        assertNull(resumed.fire)
    }

    @Test
    fun `нет связи с ТВ - ничего`() {
        val d = ready(SkipTimeline(listOf(intro)))
        assertNull(d.update(snap(100_000, 3_000, connected = false)).offer)
    }

    @Test
    fun `крестик отменяет отрезок на эту серию, остальные работают`() {
        val d = ready(SkipTimeline(listOf(intro, credits)))
        d.update(snap(100_000, 3_000))
        d.cancel()
        assertNull(d.update(snap(105_000, 4_000)).offer)
        assertNull(d.update(snap(100_000, 5_000)).offer) // отмотали назад: всё равно не предлагаем
        assertEquals(SkipKind.CREDITS, d.update(snap(1_340_000, 6_000)).offer!!.kind)
    }

    @Test
    fun `после прыжка тихо ещё пару секунд и повторно не срабатывает`() {
        val d = ready(SkipTimeline(listOf(intro)))
        d.update(snap(100_000, 3_000))
        val fire = d.update(snap(103_000, 6_000))
        assertNotNull(fire.fire)
        assertNull(d.update(snap(103_000, 7_000)).offer) // плеер ещё присылает старую позицию
        assertNull(d.update(snap(100_000, 9_000)).offer) // тишина кончилась, но отрезок уже сработал
    }

    @Test
    fun `перезапуск серии с начала снова даёт заставку`() {
        val d = ready(SkipTimeline(listOf(intro)))
        d.update(snap(100_000, 3_000))
        d.update(snap(103_000, 6_000)) // сработало
        d.update(snap(600_000, 20_000))
        assertNull(d.update(snap(2_000, 30_000)).offer)
        assertNotNull(d.update(snap(90_000, 40_000)).offer)
    }

    @Test
    fun `данные от другой серии игнорируются`() {
        val d = SkipDecider()
        d.update(snap(0, 0, key = "e2"))
        d.setTimeline("e1", SkipTimeline(listOf(intro)))
        assertNull(d.update(snap(100_000, 5_000, key = "e2")).offer)
    }

    @Test
    fun `при смене серии всё сбрасывается`() {
        val d = ready(SkipTimeline(listOf(intro)))
        d.update(snap(100_000, 3_000))
        d.update(snap(100_000, 4_000, key = "e2"))
        assertNull(d.update(snap(100_000, 9_000, key = "e2")).offer) // у новой серии данных ещё нет
    }

    @Test
    fun `кривые отрезки не используются`() {
        val long = SkipSegment(SkipKind.INTRO, 0, 400_000, 5, "T")
        assertNull(ready(SkipTimeline(listOf(long))).update(snap(10_000, 3_000)).offer)

        val reversed = SkipSegment(SkipKind.INTRO, 100_000, 90_000, 5, "T")
        assertNull(ready(SkipTimeline(listOf(reversed))).update(snap(95_000, 3_000)).offer)

        val lateIntro = SkipSegment(SkipKind.INTRO, 1_000_000, 1_090_000, 5, "T")
        assertNull(ready(SkipTimeline(listOf(lateIntro))).update(snap(1_010_000, 3_000)).offer)

        val earlyCredits = SkipSegment(SkipKind.CREDITS, 100_000, 190_000, 5, "T")
        assertNull(ready(SkipTimeline(listOf(earlyCredits))).update(snap(110_000, 3_000)).offer)
    }

    @Test
    fun `титры без длины серии не используются`() {
        val d = ready(SkipTimeline(listOf(credits)))
        assertNull(d.update(snap(1_340_000, 3_000, dur = 0)).offer)
    }

    @Test
    fun `титры с известным концом прыгают на конец`() {
        val d = ready(SkipTimeline(listOf(credits)))
        d.update(snap(1_340_000, 3_000))
        val f = d.update(snap(1_343_000, 6_000)).fire!!
        assertEquals(SkipAction.SeekTo(1_422_000), f.action)
    }

    @Test
    fun `титры до конца серии - следующая серия, а на последней прыжок к концу`() {
        val open = credits.copy(endMs = null)
        val d = ready(SkipTimeline(listOf(open)))
        d.update(snap(1_340_000, 3_000))
        val f = d.update(snap(1_343_000, 6_000)).fire!!
        assertEquals(SkipAction.NextEpisode, f.action)
        assertNull(f.returnToMs)

        val last = ready(SkipTimeline(listOf(open)))
        last.update(snap(1_340_000, 3_000, next = false))
        val f2 = last.update(snap(1_343_000, 6_000, next = false)).fire!!
        assertEquals(SkipAction.SeekTo(duration - 1_000), f2.action)
    }

    @Test
    fun `сцена после титров не перепрыгивается`() {
        val open = credits.copy(endMs = null)
        val d = ready(SkipTimeline(listOf(open), postCreditsStartMs = 1_400_000))
        d.update(snap(1_340_000, 3_000))
        val f = d.update(snap(1_343_000, 6_000)).fire!!
        assertEquals(SkipAction.SeekTo(1_400_000), f.action)
        assertTrue(f.returnToMs != null)
    }
}
