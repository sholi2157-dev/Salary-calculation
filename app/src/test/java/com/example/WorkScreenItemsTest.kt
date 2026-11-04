package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M-46 (R-28, R-31, R-33 on Android): the pending flag is a full-width row, a place can be moved without a long press
 * (TalkBack custom actions), and the editor's break chips wrap instead of being cut. Source checks; named Work* so CI runs it.
 */
class WorkScreenItemsTest {
    private val kis = listOf("src/main/java/com/example/ui/kis", "app/src/main/java/com/example/ui/kis").map(::File).first { it.isDirectory }
    private fun src(name: String) = File(kis, name).readText()

    @Test fun pendingFlagIsAFullWidthRow() {
        val flag = src("KisCards.kt").substringAfter("fun PendingFlag(").substringBefore("\n}\n")
        assertTrue(flag.contains("fillMaxWidth()"))
        assertTrue(!flag.contains("RoundedCornerShape(99.dp)"))
    }

    @Test fun aPlaceMovesWithoutALongPress() {
        val s = src("KisSettingsScreen.kt")
        assertTrue(s.contains("CustomAccessibilityAction(\"הזזה למעלה\")") && s.contains("CustomAccessibilityAction(\"הזזה למטה\")"))
        assertTrue(s.contains("settings.updatePlaceOrder(order); return true"))
    }

    @Test fun breakChipsWrap() {
        val block = src("KisEditor.kt").substringAfter("testTag(\"break_block\")").substringBefore("tag = \"editor_break\"")
        assertTrue(block.contains("FlowRowCompat {"))
        assertTrue(!block.contains("horizontalScroll"))
    }
}
