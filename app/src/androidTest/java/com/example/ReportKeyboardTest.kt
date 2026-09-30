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

/** Real IME and pointer events against the permanently signed Release, after the update gate. */
class ReportKeyboardTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun imeVisible(): Boolean {
        var visible = false
        ui.runOnUiThread { visible = ui.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        return visible
    }
    private fun awaitIme(visible: Boolean) {
        ui.waitUntil(5_000) { imeVisible() == visible }
        ui.waitForIdle()
    }
    private fun mode(name: String) {
        ui.onNodeWithText(name, useUnmergedTree = true).performScrollTo().performClick()
    }
    private fun focus(tag: String) {
        ui.onNodeWithTag(tag).performScrollTo().performClick()
        awaitIme(true)
        ui.onNodeWithTag(tag).assertIsFocused()
    }
    private fun saveVisible() {
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        val save = ui.onNodeWithTag("save_shift_button").fetchSemanticsNode().boundsInRoot
        val viewport = ui.onNodeWithTag("dashboard_scroll_container").fetchSemanticsNode().boundsInRoot
        assertTrue("Save must have its own measured space below the form", viewport.bottom <= save.top)
    }
    private fun snapshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    @Test fun keyboardScrollDoneResumeAndExactlyOneSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val original = runBlocking { dao.getEntriesList() }
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        for (name in listOf("שעון", "ידני")) {
            mode(name)
            repeat(3) {
                focus("notes_input")
                ui.onNodeWithTag("notes_input").performTextReplacement("RC8 $name $it")
                ui.onNodeWithTag("dashboard_scroll_container").performTouchInput { swipeUp() }
                ui.waitForIdle()
                assertTrue("Scrolling must not close IME", imeVisible())
                ui.onNodeWithTag("notes_input").assertIsFocused()
                ui.onNodeWithTag("live_shift_fab").assertDoesNotExist()
                ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
                saveVisible()
                snapshot("rc8-$name-keyboard")
                ui.onNodeWithTag("notes_input").performScrollTo().performImeAction()
                awaitIme(false)
                ui.onNodeWithTag("notes_input").assertTextContains("RC8 $name $it")
                saveVisible()
                ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
            }
        }
        mode("קבוצה")
        for (tag in listOf("worker_name_input", "worker_hours_input")) {
            focus(tag)
            ui.onNodeWithTag(tag).performTextReplacement(if (tag == "worker_name_input") "Synthetic RC8" else "2")
            ui.onNodeWithTag("dashboard_scroll_container").performTouchInput { swipeUp() }
            ui.waitForIdle()
            ui.onNodeWithTag(tag).assertIsFocused()
            assertTrue(imeVisible())
            saveVisible()
            Espresso.closeSoftKeyboard()
            awaitIme(false)
        }
        mode("Ai")
        focus("ai_free_text_input")
        ui.onNodeWithTag("ai_free_text_input").performTextReplacement("טיוטה לבדיקה בלבד")
        ui.onNodeWithTag("dashboard_scroll_container").performTouchInput { swipeUp() }
        ui.onNodeWithTag("ai_free_text_input").assertIsFocused()
        assertTrue(imeVisible())
        ui.onNodeWithTag("ai_free_text_input").performImeAction()
        awaitIme(false)
        ui.onNodeWithTag("ai_free_text_input").assertTextContains("טיוטה לבדיקה בלבד")
        mode("ידני")
        focus("notes_input")
        ui.onNodeWithTag("notes_input").performTextReplacement("RC8 one real save")
        // Send the Activity to background and resume without recreating the form.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            automation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        shell("am start -W -a android.settings.SETTINGS")
        shell("input keyevent KEYCODE_BACK")
        ui.waitForIdle()
        ui.onNodeWithTag("notes_input").assertTextContains("RC8 one real save")
        focus("notes_input")
        saveVisible()
        snapshot("rc8-save-with-ime")
        // A pointer tap while the IME is still open must save, with no preceding blur tap.
        ui.onNodeWithTag("save_shift_button").performTouchInput { click() }
        ui.waitUntil(5_000) { runBlocking { dao.getEntriesList().size } == original.size + 1 }
        awaitIme(false)
        ui.onNodeWithTag("save_shift_button").assertDoesNotExist()
        ui.waitForIdle()
        val after = runBlocking { dao.getEntriesList() }
        assertEquals(original.size + 1, after.size)
        assertEquals(1, after.count { it.notes == "RC8 one real save" })
        assertTrue(after.containsAll(original))
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        saveVisible()
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        ui.onNodeWithTag("save_shift_button").assertDoesNotExist()
        // Exercise clock and group saves as well; rapid double tap still means one entry.
        for (name in listOf("שעון", "קבוצה")) {
            val before = runBlocking { dao.getEntriesList().size }
            ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
            mode(name)
            focus("notes_input")
            ui.onNodeWithTag("notes_input").performTextReplacement("RC8 single $name")
            saveVisible()
            ui.onNodeWithTag("save_shift_button").performTouchInput { doubleClick() }
            awaitIme(false)
            ui.waitUntil(5_000) { runBlocking { dao.getEntriesList().size } == before + 1 }
            ui.waitForIdle()
            assertEquals(before + 1, runBlocking { dao.getEntriesList().size })
            assertEquals(1, runBlocking { dao.getEntriesList().count { it.notes == "RC8 single $name" } })
        }
        // Compact History can fit the six existing fixtures: create real scrollable
        // content before asserting scroll-driven navigation, only after update preservation.
        runBlocking {
            repeat(20) { i ->
                dao.insertEntry(com.example.data.WorkEntry(category = "ILS category",
                    date = 1800000000000L + i, isTimeRange = false, hours = 1.0,
                    hourlyRate = 40.0, totalEarnings = 40.0, notes = "RC9 scroll fixture $i"))
            }
        }
        ui.onNodeWithTag("tab_1").performClick()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        val list = ui.onNodeWithTag("history_scroll_container")
        val restingBottom = list.fetchSemanticsNode().boundsInRoot.bottom
        // Keep a real drag in progress while observing the collapsed footer.
        list.performTouchInput {
            down(center)
            moveTo(androidx.compose.ui.geometry.Offset(center.x, center.y - 120f), 300)
        }
        ui.waitForIdle()
        ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        assertEquals("History retains its stable viewport while navigation collapses",
            restingBottom, list.fetchSemanticsNode().boundsInRoot.bottom, 1f)
        list.performTouchInput { up() }
        ui.waitForIdle()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("work_entry_card_1"))
        list.performTouchInput { swipeUp() }
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        val finalCard = ui.onNodeWithTag("work_entry_card_1").fetchSemanticsNode().boundsInRoot
        val returnedNav = ui.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Full final card stays above returned navigation", finalCard.bottom <= returnedNav.top)
        snapshot("rc9-history-final")
        ui.onNodeWithTag("work_entry_card_1").performTouchInput { longClick() }
        ui.onNodeWithText("נבחרו 1 משמרות").assertIsDisplayed()
        Espresso.pressBack()
        ui.onNodeWithTag("tab_0").performClick()
        ui.onNodeWithTag("live_shift_fab").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertIsDisplayed()
        ui.onNodeWithText("אישור").performClick()
        ui.onNodeWithText("סיים משמרת פעילה").assertIsDisplayed()
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        ui.onNodeWithText("ביטול").performClick()
        ui.onNodeWithText("סיים משמרת פעילה").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        snapshot("rc8-final-home")
    }
}
