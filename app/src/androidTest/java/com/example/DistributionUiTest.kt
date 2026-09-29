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
        ui.onNodeWithTag("summary_pager").performTouchInput { swipeRight() }
        ui.onNodeWithText("סיכום בשקלים").assertIsDisplayed()
        ui.onNodeWithTag("summary_pager").performTouchInput { swipeRight() }
        ui.onNodeWithText("סיכום בדולרים").assertIsDisplayed()
        snap("rc2-currency")
        ui.onNodeWithTag("live_shift_fab").performClick()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertIsDisplayed()
        Espresso.pressBack()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithTag("live_shift_fab").performClick()
        // Exercise a real pointer tap on the dialog scrim, above the centered card.
        // This avoids the system-gesture edge where emulator-injected touches can be swallowed.
        ui.onNodeWithTag("quick_shift_scrim", useUnmergedTree = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, 20f))
        }
        ui.waitForIdle()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithText("התחל משמרת פעילה").assertExists()
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        for (mode in listOf("שעון", "ידני")) {
            ui.onNodeWithText(mode, useUnmergedTree = true).performScrollTo().performClick()
            ui.onNodeWithTag("save_shift_button").performScrollTo().assertIsDisplayed()
        }
        ui.onNodeWithText("Ai", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("קבוצה", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("add_rate_input").performScrollTo().performClick().performTextReplacement("73.25")
        ui.onNodeWithTag("save_shift_button").performScrollTo().assertIsDisplayed()
        snap("rc2-keyboard")
        Espresso.closeSoftKeyboard()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("add_rate_input").performScrollTo().assertTextContains("73.25")
        ui.onNodeWithTag("save_shift_button").performScrollTo().assertIsDisplayed()
        val saveBounds = ui.onNodeWithTag("save_shift_button").fetchSemanticsNode().boundsInRoot
        val liveBounds = ui.onNodeWithTag("live_shift_fab").fetchSemanticsNode().boundsInRoot
        assertTrue("Save action must be above active shift control", saveBounds.bottom <= liveBounds.top)
        snap("rc2-group")
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
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
        ui.onNodeWithText("סינון · 1 פעילים").assertExists()
        snap("rc2-history")
        ui.onNodeWithContentDescription("מיון").performClick()
        ui.onNodeWithText("תאריך: מהישן לחדש").performClick()
        ui.onNodeWithContentDescription("ניהול וקטגוריות").performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("settings_section_עדכונים", useUnmergedTree = true).assertExists()
        val titleBounds = ui.onNodeWithTag("settings_title_עדכונים", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrowBounds = ui.onNodeWithTag("settings_chevron_עדכונים", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Hebrew label must be to the right of its trailing chevron", titleBounds.right > arrowBounds.right)
        ui.onNodeWithTag("settings_section_משוב ודיווח על תקלה", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("שמור קובץ גיבוי").assertDoesNotExist()
        snap("rc2-settings")
        ui.onNodeWithTag("settings_section_משוב ודיווח על תקלה", useUnmergedTree = true).performClick()
        ui.onNodeWithTag("feedback_message").performScrollTo().performTextInput("Synthetic feedback draft")
        ui.onNodeWithTag("feedback_message").assertTextContains("Synthetic feedback draft")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        kotlinx.coroutines.runBlocking {
            val db = com.example.data.WorkDatabase.getDatabase(target, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
            assertEquals(java.io.File(target.getExternalFilesDir(null), "verify-after-edit.json").readText(), db.workDao().exportSnapshot())
        }
    }
}
