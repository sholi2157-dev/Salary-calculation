package com.example

import android.view.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.WorkDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real IME and pointer events on the 2.0 add page, against the permanently signed Release after the update gate. */
class ReportKeyboardTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun imeVisible(): Boolean {
        var visible = false
        ui.runOnUiThread { visible = ui.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        return visible
    }
    private fun awaitIme(visible: Boolean) { ui.waitUntil(5_000) { imeVisible() == visible }; ui.waitForIdle() }
    private fun focus(tag: String) {
        ui.onNodeWithTag(tag).performScrollTo().performClick()
        awaitIme(true)
        ui.onNodeWithTag(tag).assertIsFocused()
    }
    /** Save stays on screen, in its own space below the scrolling form, above the keyboard. */
    private fun saveVisible() {
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        val save = ui.onNodeWithTag("save_shift_button").fetchSemanticsNode().boundsInRoot
        val form = ui.onNodeWithTag("editor_scroll").fetchSemanticsNode().boundsInRoot
        assertTrue("Save has its own space below the form", form.bottom <= save.top + 1f)
    }
    private fun snapshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun openAdd() { ui.onNodeWithTag("add_entry_button").performClick(); ui.onNodeWithTag("add_page").assertIsDisplayed() }

    @Test fun keyboardScrollResumeAndExactlyOneSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val original = runBlocking { dao.getEntriesList() }
        openAdd()
        ui.onNodeWithTag("clock_start").performClick()
        listOf("0", "9", "0", "0").forEach { ui.onNodeWithTag("key_$it").performClick() }
        ui.onNodeWithTag("keypad_done").performClick()
        ui.onNodeWithTag("editor_more").performScrollTo().performClick()
        for (mode in listOf("clock", "hours")) {
            ui.onNodeWithTag("mode_$mode").performClick()
            repeat(2) {
                focus("notes_input")
                ui.onNodeWithTag("notes_input").performTextReplacement("V2 $mode $it")
                ui.onNodeWithTag("editor_scroll").performTouchInput { swipeUp() }
                ui.waitForIdle()
                assertTrue("Scrolling must not close the keyboard", imeVisible())
                ui.onNodeWithTag("notes_input").assertIsFocused()
                saveVisible()
                snapshot("v2-$mode-keyboard")
                Espresso.closeSoftKeyboard()
                awaitIme(false)
                ui.onNodeWithTag("notes_input").assertTextContains("V2 $mode $it")
                saveVisible()
            }
        }
        ui.onNodeWithTag("quick_hours_4").performClick()
        ui.onNodeWithTag("mode_group").performClick()
        focus("worker_name_input")
        ui.onNodeWithTag("worker_name_input").performTextReplacement("Synthetic worker")
        ui.onNodeWithTag("editor_scroll").performTouchInput { swipeUp() }
        ui.onNodeWithTag("worker_name_input").assertIsFocused()
        saveVisible()
        Espresso.closeSoftKeyboard(); awaitIme(false)
        ui.onNodeWithTag("worker_name_input").performTextReplacement("")
        ui.onNodeWithTag("mode_hours").performClick()
        focus("notes_input")
        ui.onNodeWithTag("notes_input").performTextReplacement("V2 one real save")
        // Background and resume without losing the form.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { d -> android.os.ParcelFileDescriptor.AutoCloseInputStream(d).use { it.readBytes() } }
        shell("am start -W -a android.settings.SETTINGS")
        shell("input keyevent KEYCODE_BACK")
        ui.waitForIdle()
        ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("V2 one real save")
        focus("notes_input")
        saveVisible()
        snapshot("v2-save-with-ime")
        // A tap on Save while the keyboard is still open saves exactly once, even when doubled.
        ui.onNodeWithTag("save_shift_button").performTouchInput { doubleClick() }
        ui.waitUntil(5_000) { runBlocking { dao.getEntriesList().size } == original.size + 1 }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("add_page").fetchSemanticsNodes().isEmpty() }
        Thread.sleep(500); ui.waitForIdle()
        val after = runBlocking { dao.getEntriesList() }
        assertEquals(original.size + 1, after.size)
        assertEquals(1, after.count { it.notes == "V2 one real save" })
        assertTrue(after.containsAll(original))
        val saved = after.first { it.notes == "V2 one real save" }
        assertEquals(4.0, saved.hours, 0.0); assertFalse(saved.isTimeRange)
        // The AI page keeps its text through the keyboard.
        openAdd()
        ui.onNodeWithTag("editor_ai").performClick()
        focus("ai_free_text_input")
        ui.onNodeWithTag("ai_free_text_input").performTextReplacement("טיוטה לבדיקה בלבד")
        Espresso.closeSoftKeyboard(); awaitIme(false)
        ui.onNodeWithTag("ai_free_text_input").assertTextContains("טיוטה לבדיקה בלבד")
        ui.onNodeWithTag("editor_close").performClick()
        ui.waitForIdle()
        // History: the last card is never hidden behind the floating navigation.
        runBlocking {
            repeat(20) { i ->
                dao.insertEntry(com.example.data.WorkEntry(category = "ILS category", date = System.currentTimeMillis() - i * 3_600_000L, isTimeRange = false,
                    hours = 1.0, hourlyRate = 40.0, totalEarnings = 40.0, notes = "V2 scroll fixture $i"))
            }
        }
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        val list = ui.onNodeWithTag("history_scroll_container")
        list.performTouchInput { repeat(6) { swipeUp() } }
        ui.waitForIdle()
        val lastFixture = ui.onAllNodes(hasText("V2 scroll fixture", substring = true) and hasAnyAncestor(hasTestTag("history_scroll_container")), useUnmergedTree = true)
        assertTrue(lastFixture.fetchSemanticsNodes().isNotEmpty())
        val nav = ui.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        val hint = ui.onNodeWithText("נגיעה לפרטים", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The end of the list scrolls above the navigation", hint.bottom <= nav.top)
        snapshot("v2-history-final")
        runBlocking { dao.deleteEntriesById(dao.getEntriesList().filter { it.notes.startsWith("V2 scroll fixture") || it.notes == "V2 one real save" }.map { it.id }) }
        assertEquals(original, runBlocking { dao.getEntriesList() })
        // Live shift: start and cancel without saving.
        ui.onNodeWithTag("tab_0").performClick()
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        ui.onNodeWithTag("live_start_confirm").performClick()
        ui.onNodeWithTag("live_shift_stop").assertIsDisplayed()
        snapshot("v2-live")
        ui.onNodeWithTag("cancel_active_shift").performClick()
        ui.onNodeWithTag("confirm_cancel_active_shift").performClick()
        ui.onNodeWithTag("live_shift_fab").assertIsDisplayed()
        assertEquals(original, runBlocking { dao.getEntriesList() })
    }
}
