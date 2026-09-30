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
        assertTrue("History must reclaim footer space during scrolling",
            list.fetchSemanticsNode().boundsInRoot.bottom > restingBottom)
        list.performTouchInput { up() }
        ui.waitForIdle()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        ui.onNodeWithTag("tab_0").performClick()
        ui.onNodeWithTag("live_shift_fab").assertIsDisplayed().performClick()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertIsDisplayed()
        Espresso.pressBack()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        snapshot("rc8-final-home")
    }
}
