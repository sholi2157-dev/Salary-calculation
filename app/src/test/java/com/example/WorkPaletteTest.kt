package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.kis.KisColors
import com.example.ui.kis.KisDark
import com.example.ui.kis.KisLight
import com.example.ui.kis.Workplaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-28 (Kis 2 palette, D-1/D-4): the light and dark tokens pass contrast and keep their roles. Named Work* so CI runs it. */
class WorkPaletteTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun textPairs(c: KisColors) = mapOf(
        "ink/bg" to (c.ink to c.bg), "ink/surface" to (c.ink to c.surface), "ink/sunk" to (c.ink to c.sunk),
        "muted/surface" to (c.muted to c.surface), "muted/bg" to (c.muted to c.bg),
        "paid/bg" to (c.paid to c.bg), "paid/surface" to (c.paid to c.surface), "pending/surface" to (c.pending to c.surface),
        "pending/soft" to (c.pending to c.pendingSoft), "danger/surface" to (c.danger to c.surface), "danger/bg" to (c.danger to c.bg),
        "onDanger/danger" to (c.onDanger to c.danger), "onPaid/paid" to (c.onPaid to c.paid), "accentInk/accent" to (c.accentInk to c.accent),
        "onInk/ink" to (c.onInk to c.ink), "knob/controlOn" to (c.controlKnob to c.controlOn), "onBar/bar(dark)" to (c.onBar to if (c.dark) c.bar else Color.White),
        "barAction/bar" to (c.barAction to if (c.dark) c.bar else Color.White),
    )

    @Test fun everyTextPairReachesAA() {
        for (c in listOf(KisLight, KisDark)) for ((name, pair) in textPairs(c)) {
            val r = ratio(pair.first, pair.second)
            assertTrue("${if (c.dark) "dark" else "light"} $name = $r", r >= 4.5)
        }
    }

    @Test fun controlEdgesReachThreeToOne() {
        for (c in listOf(KisLight, KisDark)) for (bg in listOf(c.bg, c.surface)) assertTrue(ratio(c.lineStrong, bg) >= 3.0)
    }

    @Test fun darkPrimaryButtonIsSoftWhiteWithDarkText() { // D-4
        assertEquals(Color(0xFFEDF0F6), KisDark.ink)
        assertEquals(Color(0xFF0F1216), KisDark.onInk)
    }

    @Test fun switchIsInkNotMoneyGreen() {
        for (c in listOf(KisLight, KisDark)) { assertEquals(c.ink, c.controlOn); assertNotEquals(c.paid, c.controlOn) }
    }

    @Test fun workplacesKeepOrderAndReadableTextAndAreNotTheBrand() {
        assertEquals(listOf("אינדיגו", "ירוק", "ורד", "חרדל", "סגול", "תכלת", "טורקיז", "בורדו"), Workplaces.map { it.name })
        for (w in Workplaces) {
            assertTrue(w.name, ratio(w.on, w.to) >= 4.5)
            assertTrue(w.name, ratio(w.accentDarkInk, w.accentDark) >= 4.5)
            assertNotEquals(KisLight.accent, w.from)
            assertNotEquals(KisDark.accent, w.accentDark)
        }
    }
}
