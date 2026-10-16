package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 2.0 screens on the signed Release after the real update; read-only: nothing here saves. */
class DistributionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun snap(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun portraitScreensDraftsHistoryAndSettings() {
        ui.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ui.activity.requestedOrientation)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, ui.activity.resources.configuration.orientation)
        ui.onNodeWithTag("home_hero").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        snap("v2-home")
        // Add page: every mode, a typed note survives mode changes and an Activity recreation.
        ui.onNodeWithTag("add_entry_button").performClick()
        ui.onNodeWithTag("add_page").assertIsDisplayed()
        ui.onNodeWithTag("editor_more").performScrollTo().performClick()
        ui.onNodeWithTag("notes_input").performScrollTo().performTextReplacement("בדיקת טיוטה")
        for (mode in listOf("hours", "job", "group", "clock")) {
            ui.onNodeWithTag("mode_$mode").performClick()
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
            snap("v2-add-$mode")
        }
        ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("בדיקת טיוטה")
        Espresso.closeSoftKeyboard()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("בדיקת טיוטה")
        Espresso.pressBack()
        ui.onNodeWithTag("leave_sheet").assertIsDisplayed()
        ui.onNodeWithTag("leave_confirm").performClick()
        ui.onNodeWithTag("add_page").assertDoesNotExist()
        // History: search has separate clear and close; the system shortcut opens it.
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        ui.activity.intentActionFlow.value = "com.example.ACTION_OPEN_HISTORY_SEARCH"
        ui.waitForIdle()
        ui.onNodeWithTag("history_search_input").performTextInput("USD category")
        ui.onNodeWithTag("history_search_clear").performClick()
        ui.onNodeWithTag("history_search_input").assertExists()
        ui.onNodeWithTag("history_search_close").performClick()
        ui.onNodeWithTag("history_search_input").assertDoesNotExist()
        // Fixtures are from 2023: show all dates.
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("period_ALL").performClick()
        ui.onNodeWithTag("filters_apply").performClick()
        // Long-press selection: Back, tab change and swipe all leave it.
        val card = ui.onAllNodes(hasTestTagPrefix("work_entry_card_") and hasAnyAncestor(hasTestTag("history_scroll_container"))).onFirst()
        fun select() { card.performScrollTo().performTouchInput { longClick() }; ui.onNodeWithTag("selection_count").assertTextEquals("נבחרו 1") }
        select(); Espresso.pressBack(); ui.onNodeWithTag("selection_bar").assertDoesNotExist()
        select(); snap("v2-history-selection"); ui.onNodeWithTag("selection_cancel").performClick(); ui.onNodeWithTag("selection_bar").assertDoesNotExist()
        select(); ui.onNodeWithTag("main_screen_pager").performTouchInput { swipeLeft() }; ui.waitForIdle()
        ui.onNodeWithTag("tab_0").assertIsSelected()
        ui.onNodeWithTag("tab_1").performClick(); ui.onNodeWithTag("selection_bar").assertDoesNotExist()
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("history_status_UNPAID").performClick()
        ui.onNodeWithTag("filters_apply").performClick()
        ui.onNodeWithTag("history_sort").performClick()
        ui.onNodeWithText("תאריך: מהישן לחדש", substring = true).performClick()
        snap("v2-history")
        card.performScrollTo().performClick()
        ui.onNodeWithTag("shift_detail").assertIsDisplayed()
        snap("v2-detail")
        Espresso.pressBack()
        // Settings from the import shortcut; replay is an overlay and keeps the feedback draft.
        ui.activity.intentActionFlow.value = "com.example.ACTION_IMPORT_EXCEL"
        ui.waitForIdle()
        ui.onNodeWithTag("settings_root").assertIsDisplayed()
        for (section in listOf("עבודה", "ברירות מחדל", "מראה", "גיבוי ונתונים", "משוב ותקלות")) ui.onNodeWithTag("settings_section_$section").performScrollTo().assertExists()
        snap("v2-settings")
        ui.onNodeWithTag("feedback_message").performScrollTo().performTextInput("Synthetic feedback draft")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("replay_onboarding").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding_progress").assertTextEquals("1 מתוך 9")
        ui.onNodeWithTag("onboarding_skip").performClick()
        ui.onNodeWithTag("feedback_message").performScrollTo().assertTextContains("Synthetic feedback draft")
        ui.onNodeWithTag("settings_close").performClick()
        ui.onNodeWithTag("settings_root").assertDoesNotExist()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        kotlinx.coroutines.runBlocking {
            val db = com.example.data.WorkDatabase.getDatabase(target, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
            assertEquals(java.io.File(target.getExternalFilesDir(null), "verify-after-edit.json").readText(), db.workDao().exportSnapshot())
        }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("TestTag starts with $prefix") { node ->
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
}
