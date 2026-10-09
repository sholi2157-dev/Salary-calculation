package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CurrencyFeedbackTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun defaultsLiveCurrencyConfirmedDiscardEditAndEnter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = WorkAccountScope(null).database(context).workDao()
        val state = ShiftStateManager.forAccount(context, WorkAccountScope(null))
        val before = runBlocking { dao.getEntriesList() }
        runBlocking {
            dao.insertCategory(WorkCategory(name="RC14 default currency", defaultRate=37.25))
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "RC14 default currency"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "$"))
        }
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("quick_shift_currency_$").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("quick_shift_currency_$").assertIsSelected()
        ui.onNodeWithText("אישור").performClick()
        ui.waitUntil(5000) { state.activeShiftStartTime.value != null }
        val start = state.activeShiftStartTime.value!!
        assertEquals("$", state.activeShiftCurrency.value)
        ui.onNodeWithTag("active_shift_currency_₪").performScrollTo().performClick()
        assertEquals("₪", WorkShiftState(context, WorkAccountScope(null)).activeShiftCurrency.value)
        assertEquals(start, state.activeShiftStartTime.value)
        ui.onNodeWithTag("cancel_active_shift").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_cancel_active_shift").assertExists()
        assertEquals(start, state.activeShiftStartTime.value)
        ui.onNodeWithText("המשך במשמרת").performClick()
        assertEquals(start, state.activeShiftStartTime.value)
        ui.onNodeWithTag("cancel_active_shift").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_cancel_active_shift").performClick()
        ui.waitUntil(5000) { state.activeShiftStartTime.value == null }
        assertEquals(before, runBlocking { dao.getEntriesList() })

        ui.onNodeWithTag("tab_1").performClick()
        val original = before.first { !it.isGroupShift && !it.isTimeRange }
        ui.onNodeWithTag("history_scroll_container").performScrollToNode(hasTestTag("work_entry_card_${original.id}"))
        ui.onNodeWithTag("work_entry_card_${original.id}").performClick()
        ui.onNodeWithTag("edit_entry_btn_${original.id}").performScrollTo().performClick()
        val target = if (original.currency == "₪") "$" else "₪"
        ui.onNodeWithTag("edit_shift_currency_$target").performScrollTo().performClick()
        ui.onNodeWithTag("submit_edited_entry_btn").performScrollTo().performClick()
        ui.waitUntil(5000) { runBlocking { dao.getEntryById(original.id)?.currency == target } }
        assertEquals(original.copy(currency=target), runBlocking { dao.getEntryById(original.id) })
        // Keep later keyboard/history gates' synthetic fixtures and defaults intact.
        runBlocking {
            dao.updateEntry(original)
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "ILS category"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "₪"))
        }
        ui.onNodeWithContentDescription("ניהול וקטגוריות").performClick()
        ui.onNodeWithTag("settings_section_מערכת ומשוב", useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithTag("feedback_message").performScrollTo().performClick().performTextInput("first line")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("input keyevent KEYCODE_ENTER").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        ui.waitForIdle()
        ui.onNodeWithTag("settings_root", useUnmergedTree=true).assertExists()
        ui.onNodeWithTag("feedback_message").assertExists().performTextInput("second line")
        ui.onNodeWithTag("feedback_message").assertTextContains("first line\nsecond line")
        val bitmap = automation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), "rc14-feedback.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        Espresso.pressBack()
    }
}
