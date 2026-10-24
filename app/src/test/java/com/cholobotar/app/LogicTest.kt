package com.cholobotar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test
    fun parserHidesMarkersAndKeepsPosition() {
        val p = parseScript("Hello [BG:dark] world [SFX:Boom]")
        assertEquals(listOf("Hello", "world"), p.words)
        assertEquals(2, p.markers.size)
        assertEquals(1, p.markers[0].wordIndex)
        assertEquals("DARK", p.markers[0].value)
        assertEquals(2, p.markers[1].wordIndex)
    }

    @Test
    fun videoSizes() {
        assertEquals(720, videoSizeFor(720, 0).w)
        assertEquals(1280, videoSizeFor(720, 0).h)
        assertEquals(1280, videoSizeFor(720, 1).w)
        assertEquals(1080, videoSizeFor(1080, 2).h)
        assertEquals(0, videoSizeFor(480, 0).h % 2)
    }

    @Test
    fun frameFitsInsideStage() {
        val f = fitFrame(360f, 600f, 9f / 16f)
        assertTrue(f.w <= 360f && f.h <= 600f)
        assertEquals(9f / 16f, f.w / f.h, 0.001f)
    }

    @Test
    fun gateOpensOnLoudAndClosesAfterHold() {
        val g = VoiceGate()
        g.process(ShortArray(512) { 12000 }, 512, 0L)
        assertTrue(g.isOpen)
        g.process(ShortArray(512), 512, 500L)
        assertFalse(g.isOpen)
    }
}
