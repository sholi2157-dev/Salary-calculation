package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class DistributionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun portraitDialogsHistoryAndSettings() {
        ui.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ui.activity.requestedOrientation)
        assertEquals(Configuration.ORIENTATION_PORTRAIT,ui.activity.resources.configuration.orientation)
        val device=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun snap(name: String) {
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            device.takeScreenshot().let { bitmap -> java.io.File(target.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
        snap("rc2-home")
        device.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
        ui.waitForIdle()
        assertEquals(Configuration.ORIENTATION_PORTRAIT,ui.activity.resources.configuration.orientation)
        device.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        val generalHeight = ui.onNodeWithTag("summary_page_0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        ui.onNodeWithText("יעד חודשי").assertDoesNotExist()
        ui.onNodeWithTag("summary_pager").performTouchInput { swipeRight() }
        ui.onNodeWithText("סיכום בשקלים").assertIsDisplayed()
        val ilsHeight = ui.onNodeWithTag("summary_page_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertEquals(generalHeight, ilsHeight, 1f)
        ui.onNodeWithTag("summary_pager").performTouchInput { swipeRight() }
        ui.onNodeWithText("סיכום בדולרים").assertIsDisplayed()
        val usdHeight = ui.onNodeWithTag("summary_page_2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertEquals(generalHeight, usdHeight, 1f)
        ui.onNodeWithText("יעד חודשי").assertDoesNotExist()
        snap("rc2-currency")
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertIsDisplayed()
        Espresso.pressBack()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithTag("live_shift_fab").performScrollTo().performClick()
        // Exercise a real pointer tap on the dialog scrim, above the centered card.
        // This avoids the system-gesture edge where emulator-injected touches can be swallowed.
        ui.onNodeWithTag("quick_shift_scrim", useUnmergedTree = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, 20f))
        }
        ui.waitForIdle()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithText("התחל משמרת פעילה").assertExists()
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        // Regression for the physical-phone failure where the IME + collapsing bottom
        // controls made the Save action jump or become unreachable while scrolling.
        ui.onNodeWithTag("notes_input").performScrollTo().performClick().performTextReplacement("בדיקת מקלדת")
        ui.waitForIdle()
        ui.onNodeWithTag("notes_input").assertIsFocused()
        ui.onNodeWithTag("dashboard_scroll_container").performTouchInput { swipeUp() }
        ui.waitForIdle()
        ui.onNodeWithTag("notes_input").assertIsFocused()
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        ui.onNodeWithTag("notes_input").performScrollTo().performImeAction()
        ui.waitForIdle()
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        for (mode in listOf("שעון", "ידני")) {
            ui.onNodeWithText(mode, useUnmergedTree = true).performScrollTo().performClick()
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        }
        ui.onNodeWithText("Ai", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("קבוצה", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("add_rate_input").performScrollTo().performClick().performTextReplacement("73.25")
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        snap("rc2-keyboard")
        Espresso.closeSoftKeyboard()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("add_rate_input").performScrollTo().assertTextContains("73.25")
        ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        ui.onNodeWithTag("live_shift_fab").assertDoesNotExist()
        val saveBounds = ui.onNodeWithTag("save_shift_button").fetchSemanticsNode().boundsInRoot
        val viewport = ui.onNodeWithTag("dashboard_scroll_container").fetchSemanticsNode().boundsInRoot
        assertTrue("Save keeps separate space below compact report", viewport.bottom <= saveBounds.top)
        snap("rc2-group")
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        fun openHistorySearch() {
            ui.activity.intentActionFlow.value = "com.example.ACTION_OPEN_HISTORY_SEARCH"
            ui.waitForIdle()
            ui.onNodeWithTag("history_search_input").assertExists()
        }
        openHistorySearch()
        ui.onNodeWithTag("history_search_input").performTextInput("USD category")
        ui.onNodeWithTag("history_search_input").performImeAction()
        ui.onNodeWithTag("history_search_input").assertDoesNotExist()
        openHistorySearch()
        ui.onNodeWithTag("history_search_input").assertTextContains("USD category")
        ui.onNodeWithText("הצג תוצאות").performClick()
        ui.onNodeWithTag("history_search_input").assertDoesNotExist()
        openHistorySearch()
        ui.onNodeWithTag("history_search_input").assertTextContains("USD category")
        ui.onNodeWithTag("history_search_clear", useUnmergedTree = true).performClick()
        ui.onNodeWithTag("history_search_clear", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithText("הצג תוצאות").performClick()
        fun select() { ui.onNodeWithTag("work_entry_card_3").performScrollTo().performTouchInput { longClick() };ui.onNodeWithText("נבחרו 1 משמרות").assertExists() }
        select();Espresso.pressBack();ui.onNodeWithText("נבחרו 1 משמרות").assertDoesNotExist()
        select();ui.onNodeWithTag("tab_0").performClick();ui.onNodeWithTag("tab_1").performClick();ui.onNodeWithText("נבחרו 1 משמרות").assertDoesNotExist()
        select();ui.onNodeWithTag("main_screen_pager").performTouchInput { swipeLeft() };ui.waitForIdle()
        ui.onNodeWithTag("tab_0").assertIsSelected()
        ui.onNodeWithTag("tab_1").performClick();ui.onNodeWithText("נבחרו 1 משמרות").assertDoesNotExist()
        ui.onNodeWithTag("history_filters").performScrollTo().performClick()
        ui.onNodeWithText("סינון היסטוריה").assertIsDisplayed()
        ui.onNodeWithTag("history_status_ממתין").performClick()
        ui.onNodeWithText("הצג תוצאות").performScrollTo().performClick()
        ui.onNodeWithTag("history_filters").assertExists()
        snap("rc2-history")
        ui.onNodeWithContentDescription("מיון").performClick()
        ui.onNodeWithText("תאריך: מהישן לחדש").performClick()
        ui.onNodeWithTag("settings_button", useUnmergedTree = true).assertHasClickAction()
        // The toolbar is intentionally collapsible on history; exercise the stable in-app
        // settings entry used by the import action instead of invoking an off-screen node.
        ui.activity.intentActionFlow.value = "com.example.ACTION_IMPORT_EXCEL"
        ui.waitForIdle()
        ui.onNodeWithTag("settings_root", useUnmergedTree = true).assertExists()
        for (section in listOf("עבודה וקטגוריות", "מטבע וברירות מחדל", "מערכת ומשוב", "גיבוי ונתונים")) {
            ui.onNodeWithTag("settings_section_$section", useUnmergedTree = true).assertExists()
        }
        ui.onNodeWithTag("settings_section_עדכונים", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithTag("settings_section_משוב ודיווח על תקלה", useUnmergedTree = true).assertDoesNotExist()
        val titleBounds = ui.onNodeWithTag("settings_title_מערכת ומשוב", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrowBounds = ui.onNodeWithTag("settings_chevron_מערכת ומשוב", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Hebrew label must be to the right of its trailing chevron", titleBounds.right > arrowBounds.right)
        ui.onNodeWithText("שמור קובץ גיבוי").assertDoesNotExist()
        snap("rc2-settings")
        ui.onNodeWithTag("settings_section_מערכת ומשוב", useUnmergedTree = true).performClick()
        ui.onNodeWithText("עדכונים").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("משוב ודיווח על תקלה").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("feedback_message").performScrollTo().performTextInput("Synthetic feedback draft")
        ui.onNodeWithTag("feedback_message").assertTextContains("Synthetic feedback draft")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        kotlinx.coroutines.runBlocking {
            val db = com.example.data.WorkDatabase.getDatabase(target, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
            assertEquals(java.io.File(target.getExternalFilesDir(null), "verify-after-edit.json").readText(), db.workDao().exportSnapshot())
        }
    }
}
