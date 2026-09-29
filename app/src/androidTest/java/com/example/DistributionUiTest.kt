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
        device.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
        ui.waitForIdle()
        assertEquals(Configuration.ORIENTATION_PORTRAIT,ui.activity.resources.configuration.orientation)
        device.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        ui.onNodeWithTag("live_shift_fab").performClick()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertIsDisplayed()
        Espresso.pressBack()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithTag("live_shift_fab").performClick()
        // A real outside touch at the corner of the dialog window.
        val down=android.os.SystemClock.uptimeMillis()
        device.injectInputEvent(android.view.MotionEvent.obtain(down,down,android.view.MotionEvent.ACTION_DOWN,2f,2f,0),true)
        device.injectInputEvent(android.view.MotionEvent.obtain(down,down+50,android.view.MotionEvent.ACTION_UP,2f,2f,0),true)
        ui.waitForIdle()
        ui.onNodeWithText("הגדרת משמרת פעילה").assertDoesNotExist()
        ui.onNodeWithText("התחל משמרת פעילה").assertExists()
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
        ui.onNodeWithText("ממתין",useUnmergedTree=true).performClick()
        ui.onNodeWithText("הצג תוצאות").performScrollTo().performClick()
        ui.onNodeWithText("סינון · 1 פעילים").assertExists()
        ui.onNodeWithContentDescription("מיון").performClick()
        ui.onNodeWithText("תאריך: מהישן לחדש").performClick()
        ui.onNodeWithContentDescription("ניהול וקטגוריות").performClick()
        ui.onNodeWithText("עדכונים").assertExists()
        ui.onNodeWithText("משוב ודיווח על תקלה").assertExists()
        ui.onNodeWithText("שמור קובץ גיבוי").assertDoesNotExist()
        ui.onNodeWithText("משוב ודיווח על תקלה").performClick()
        ui.onNodeWithTag("feedback_message").performScrollTo().performTextInput("Synthetic feedback draft")
        ui.onNodeWithTag("feedback_message").assertTextContains("Synthetic feedback draft")
    }
}
