package com.example

import com.example.ui.kis.KisMessage
import com.example.ui.kis.KisNotices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M-37: one in-app message with undo, never a system Toast. */
class KisNoticesTest {
    @Test fun aPostedMessageWaitsForTheScreenAndKeepsItsUndo() = runBlocking {
        var undone = false
        KisNotices.post("סומן כשולם ✓", "ביטול") { undone = true }
        val m = withTimeout(2_000) { KisNotices.flow.first() }
        assertEquals("סומן כשולם ✓", m.text); assertEquals("ביטול", m.action)
        assertEquals(KisMessage.Kind.OK, m.kind)
        m.onAction!!.invoke(); assertTrue(undone)
        KisNotices.post("הגיבוי נכשל")
        assertEquals(KisMessage.Kind.ERROR, withTimeout(2_000) { KisNotices.flow.first() }.kind)
    }

    @Test fun noSystemToastIsLeftInTheApp() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val users = root.walkTopDown().filter { it.extension == "kt" && it.readText().contains("Toast.makeText") }.map { it.name }.toList()
        assertEquals(emptyList<String>(), users)
    }
}
