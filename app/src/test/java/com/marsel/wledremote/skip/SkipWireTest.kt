package com.marsel.wledremote.skip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipWireTest {
    @Test
    fun `пустое состояние`() {
        val s = SkipWire.parse(JSONObject("""{"type":"skipState","offer":null,"undo":null,"seasonChoice":null}"""))
        assertNull(s.offer)
        assertNull(s.undo)
        assertNull(s.seasonChoice)
    }

    @Test
    fun `отсчёт`() {
        val s = SkipWire.parse(JSONObject("""{"type":"skipState","offer":{"kind":"INTRO","progress":0.46,"secondsLeft":2,"manual":false}}"""))
        val o = s.offer
        assertNotNull(o)
        assertEquals(SkipKind.INTRO, o!!.kind)
        assertEquals(0.46f, o.progress, 0.001f)
        assertEquals(2, o.secondsLeft)
        assertFalse(o.manual)
    }

    @Test
    fun `кнопка без отсчёта, вернуть и выбор сезона`() {
        val s = SkipWire.parse(
            JSONObject(
                """{"offer":{"kind":"CREDITS","progress":0,"secondsLeft":0,"manual":true},
                   "undo":{"kind":"RECAP"},"seasonChoice":{"seasons":[1,2,3],"selected":null}}"""
            )
        )
        assertTrue(s.offer!!.manual)
        assertEquals(SkipKind.RECAP, s.undo!!.kind)
        assertEquals(listOf(1, 2, 3), s.seasonChoice!!.seasons)
        assertNull(s.seasonChoice!!.selected)
    }

    @Test
    fun `выбранный сезон и неизвестный тип не ломают разбор`() {
        val s = SkipWire.parse(JSONObject("""{"offer":{"kind":"ЧТО-ТО"},"seasonChoice":{"seasons":[2,4],"selected":4}}"""))
        assertNull(s.offer)
        assertEquals(4, s.seasonChoice!!.selected)
    }
}
