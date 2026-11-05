package com.example

import com.example.ui.kis.rulerLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-33 (android): a night shift on the editor's ruler is one span, not two pieces at the edges. A shift that crosses
 * midnight inside noon→noon is drawn on a noon-to-noon window (ticks 12 18 00 06 12); a day shift keeps 00→24.
 * Same cases as web-tests/r33-ruler.test.cjs. Only the drawing changes; the times are the same.
 */
class WorkNightRulerTest {
    private fun labels(start: String?, end: String) = rulerLayout(start, end).ticks.joinToString(" ") { it.second }

    @Test fun nightShiftIsOneSpanOnNoonToNoon() {
        val l = rulerLayout("20:30", "00:30")
        assertEquals(720, l.origin)
        assertEquals(listOf(510 to 750), l.spans)
        assertEquals("12 18 00 06 12", labels("20:30", "00:30"))
        assertEquals("midnight sits in the middle", 720, l.at(0))
    }

    @Test fun longerNightsAreOneSpanToo() {
        assertEquals(listOf(600 to 1080), rulerLayout("22:00", "06:00").spans)
        assertEquals(listOf(0 to 1425), rulerLayout("12:00", "11:45").spans)
    }

    @Test fun dayShiftKeepsTheMidnightToMidnightRuler() {
        val l = rulerLayout("09:00", "17:30")
        assertEquals(0, l.origin)
        assertEquals(listOf(540 to 1050), l.spans)
        assertEquals("00 06 12 18 24", labels("09:00", "17:30"))
    }

    @Test fun aCrossingThatDoesNotFitNoonToNoonKeepsTwoPieces() {
        assertEquals(0, rulerLayout("08:00", "02:00").origin)
        assertEquals(listOf(480 to 1440, 0 to 120), rulerLayout("08:00", "02:00").spans)
        assertEquals(0, rulerLayout("22:00", "13:00").origin)
        assertEquals("start = end is still a full day", listOf(1320 to 1440, 0 to 1320), rulerLayout("22:00", "22:00").spans)
    }

    @Test fun noStartTimeShowsTheEndHandleAlone() {
        val l = rulerLayout(null, "02:00")
        assertEquals(0, l.origin)
        assertEquals(emptyList<Pair<Int, Int>>(), l.spans)
    }

    @Test fun aDragOnTheNoonToNoonRulerGivesTheRealTimeBack() {
        val l = rulerLayout("20:30", "00:30")
        assertEquals(720, l.fromWindow(0)); assertEquals(0, l.fromWindow(720)); assertEquals(705, l.fromWindow(1425))
        for (m in listOf(0, 30, 510, 750, 1425)) assertEquals(m, l.at(l.fromWindow(m)))
    }

    @Test fun theWindowIsKeptForTheWholeDrag() {
        // Dragging end from 23:45 to 00:00 on a day ruler: the window stays 00→24 until the finger lifts.
        assertEquals(0, rulerLayout("22:00", "00:00", keepOrigin = 0).origin)
        assertEquals(720, rulerLayout("22:00", "00:00").origin)
    }
}
