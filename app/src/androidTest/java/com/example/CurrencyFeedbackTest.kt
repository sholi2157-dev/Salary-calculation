package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Live timer currency, cancel without saving, currency-only edit, multi-line feedback and default workplace. */
class CurrencyFeedbackTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun defaultsLiveCurrencyConfirmedDiscardEditAndEnter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = WorkAccountScope(null).database(context).workDao()
        val state = ShiftStateManager.forAccount(context, WorkAccountScope(null))
        val before = runBlocking { dao.getEntriesList() }
        runBlocking {
            dao.insertCategory(WorkCategory(name = "RC14 default currency", defaultRate = 37.25))
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "RC14 default currency"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "$"))
        }
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("quick_shift_currency_$").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("quick_shift_currency_$").assertIsSelected()
        ui.onNodeWithTag("live_start_confirm").performClick()
        ui.waitUntil(5000) { state.activeShiftStartTime.value != null }
        val start = state.activeShiftStartTime.value!!
        assertEquals("RC14 default currency", state.activeShiftCategory.value)
        assertEquals("$", state.activeShiftCurrency.value)
        ui.onNodeWithTag("active_shift_currency_₪").performScrollTo().performClick()
        ui.waitUntil(5000) { WorkShiftState(context, WorkAccountScope(null)).activeShiftCurrency.value == "₪" }
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

        // Changing only the currency of a saved shift changes nothing else (amount, hours, date, notes).
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        val original = before.first { !it.isGroupShift && !it.isTimeRange }
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("period_ALL").performClick()
        ui.onNodeWithTag("filters_apply").performClick()
        ui.onNodeWithTag("history_scroll_container").performScrollToNode(hasTestTag("work_entry_card_${original.id}"))
        ui.onNodeWithTag("work_entry_card_${original.id}").performClick()
        ui.onNodeWithTag("edit_entry_btn_${original.id}").performClick()
        val target = if (original.currency == "₪") "$" else "₪"
        ui.onNodeWithTag("editor_currency_$target").performScrollTo().performClick()
        ui.onNodeWithTag("save_shift_button").performClick()
        ui.waitUntil(5000) { runBlocking { dao.getEntryById(original.id)?.currency == target } }
        assertEquals(original.copy(currency = target), runBlocking { dao.getEntryById(original.id) })
        runBlocking {
            dao.updateEntry(original)
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "ILS category"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "₪"))
        }
        // Feedback keeps Enter as a new line.
        ui.onNodeWithTag("settings_button").performClick()
        ui.onNodeWithTag("feedback_message").performScrollTo().performClick().performTextInput("first line")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("input keyevent KEYCODE_ENTER").use { d -> android.os.ParcelFileDescriptor.AutoCloseInputStream(d).use { it.readBytes() } }
        ui.waitForIdle()
        ui.onNodeWithTag("settings_root").assertExists()
        ui.onNodeWithTag("feedback_message").performTextInput("second line")
        ui.onNodeWithTag("feedback_message").assertTextContains("first line\nsecond line")
        automation.takeScreenshot().let { b -> java.io.File(context.getExternalFilesDir(null), "v2-feedback.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        // Default workplace: one current choice, change it, and add-and-select from the same picker.
        ui.onNodeWithTag("default_category_picker").performScrollTo().assertTextContains("ILS category", substring = true)
        ui.onNodeWithTag("default_category_picker").performClick()
        ui.onNodeWithTag("default_category_option_USD category").performClick()
        ui.waitUntil(5000) { runBlocking { dao.getLocalPreferences() }.any { it.name == "defaultCategory" && it.value == "USD category" } }
        ui.onNodeWithTag("default_category_picker").assertTextContains("USD category", substring = true).performClick()
        ui.onNodeWithTag("default_category_add").performClick()
        ui.onNodeWithTag("category_input").performTextInput("RC15 new default")
        ui.onNodeWithTag("category_add_confirm").performClick()
        ui.waitUntil(5000) { runBlocking { dao.getLocalPreferences() }.any { it.name == "defaultCategory" && it.value == "RC15 new default" } }
        ui.onNodeWithTag("default_category_picker").assertTextContains("RC15 new default", substring = true)
        assertEquals("₪", runBlocking { dao.getLocalPreferences() }.first { it.name == "categoryCurrency:RC15 new default" }.value)
        assertEquals(before, runBlocking { dao.getEntriesList() })
        runBlocking { dao.setLocalPreference(WorkLocalPreference("defaultCategory", "ILS category")) }
        ui.onNodeWithTag("settings_close").performClick()
    }
}
