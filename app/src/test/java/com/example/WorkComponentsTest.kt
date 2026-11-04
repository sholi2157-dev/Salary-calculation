package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.ui.kis.KisButtonHeight
import com.example.ui.kis.KisDark
import com.example.ui.kis.KisLight
import com.example.ui.kis.KisRadius
import com.example.ui.kis.disabledBg
import com.example.ui.kis.disabledInk
import com.example.ui.kis.onDangerFill
import com.example.ui.kis.selectedBg
import com.example.ui.kis.selectedEdge
import com.example.ui.kis.selectedInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M-30 (D-3 components): one selected recipe, 3 button heights, 4 radii, disabled colours, filled delete. Work* so CI runs it. */
class WorkComponentsTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
    private val kis = listOf("src/main/java/com/example/ui/kis", "app/src/main/java/com/example/ui/kis").map(::File).first { it.isDirectory }

    @Test fun selectedIsOneReadableRecipeAndNeverASolidWhiteTileInDark() {
        for (c in listOf(KisLight, KisDark)) {
            assertTrue(ratio(c.selectedInk, c.selectedBg) >= 4.5)
            assertTrue(ratio(c.selectedEdge, c.bg) >= 3.0)
        }
        assertEquals(Color(0xFF424853), KisDark.selectedBg)
        assertNotEquals(KisDark.ink, KisDark.selectedBg)
    }

    @Test fun threeButtonHeightsNoneBelowTheTouchTarget() {
        assertEquals(listOf(56.dp, 48.dp, 44.dp), listOf(KisButtonHeight.large, KisButtonHeight.medium, KisButtonHeight.small))
        assertEquals(listOf(8.dp, 12.dp, 16.dp, 24.dp, 99.dp), listOf(KisRadius.tag, KisRadius.small, KisRadius.medium, KisRadius.large, KisRadius.pill))
    }

    @Test fun deleteIsFilledAndReadableDisabledHasItsOwnColours() {
        for (c in listOf(KisLight, KisDark)) {
            assertTrue(ratio(c.onDangerFill, c.danger) >= 4.5)
            assertNotEquals(c.disabledBg, c.disabledInk)
            assertTrue(ratio(c.disabledInk, c.disabledBg) < 4.5) // reads as off, unlike an enabled button
        }
        val kit = File(kis, "KisKit.kt").readText()
        assertFalse("opacity for disabled", kit.contains("alpha = if (enabled) 1f else 0.38f"))
        assertTrue(kit.contains("kind == KisButtonKind.DANGER -> c.danger to c.onDangerFill"))
    }

    @Test fun onlyFourRadiiAndPillsInTheInterface() {
        val allowed = setOf(2, 3, 8, 12, 16, 24, 99)
        val used = kis.listFiles { f -> f.name.endsWith(".kt") }!!.flatMap { f ->
            Regex("""RoundedCornerShape\((\d+)\.dp\)""").findAll(f.readText()).map { it.groupValues[1].toInt() }.toList()
        }.toSet()
        assertTrue("radii in use: $used", allowed.containsAll(used))
    }
}
