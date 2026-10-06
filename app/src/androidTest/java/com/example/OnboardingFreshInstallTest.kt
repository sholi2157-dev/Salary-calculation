package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.*
import com.example.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Runs against the signed RC13 on a separate freshly created disposable emulator. */
class OnboardingFreshInstallTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun completedColdLaunchStaysHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(ONBOARDING_VERSION, WorkOnboardingStore(context).completedVersion)
        ui.onNodeWithTag("onboarding_card").assertDoesNotExist()
        ui.onNodeWithTag("settings_button").assertIsDisplayed()
    }
    @Test fun firstLaunchCompletionReplaySkipAndNoSideEffects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = InstrumentationRegistry.getInstrumentation().uiAutomation
        val db = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        ui.onNodeWithTag("onboarding_progress").assertTextEquals("1 מתוך 9")
        val snapshot = runBlocking { db.workDao().exportSnapshot() }
        val timer = context.getSharedPreferences("active_shift_prefs", 0).all.toMap()
        for ((index, step) in workOnboardingSteps.withIndex()) {
            ui.onNodeWithTag("onboarding_progress").assertTextEquals("${index + 1} מתוך 9")
            if (step.target != null) {
                ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("onboarding_next") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                // Compose idleness does not wait for Android window presentation or the
                // delayed bring-into-view. Inspect/capture the settled device frame.
                Thread.sleep(600)
                ui.waitForIdle()
                ui.onNodeWithTag("onboarding_next").assertIsEnabled()
                val target = ui.onNode(SemanticsMatcher.expectValue(CoachTargetKey, step.target), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val card = ui.onNodeWithTag("onboarding_card").fetchSemanticsNode().boundsInRoot
                assertTrue("visible spotlight target must not be obscured by card: ${step.target}", target.bottom <= card.top + 8f || target.top >= card.bottom - 8f)
                if (step.target == CoachTarget.HISTORY) {
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!
                        .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                    val nav = ui.onNode(SemanticsMatcher.expectValue(CoachTargetKey, step.target), useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInWindow
                    assertTrue("whole History navigation must fit above Android navigation bar", nav.bottom <= ui.activity.window.decorView.height - insets.bottom + 1f)
                    ui.onNodeWithText("היסטוריה", useUnmergedTree = true).assertIsDisplayed()
                }
                if (step.target == CoachTarget.API) {
                    val actions = ui.onNodeWithTag("settings_action_bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue("API key must not be behind sticky settings actions", target.bottom <= actions.top)
                }
                ui.onNodeWithTag("onboarding_root").performTouchInput { click(target.center) }
                ui.onNodeWithTag("onboarding_progress").assertTextEquals("${index + 1} מתוך 9")
            }
            val file = File(context.getExternalFilesDir(null), "onboarding-device-$index.png")
            device.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            ui.onNodeWithTag("onboarding_next").assertIsEnabled().performClick()
        }
        ui.onNodeWithTag("onboarding_card").assertDoesNotExist()
        assertEquals(ONBOARDING_VERSION, WorkOnboardingStore(context).completedVersion)
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("onboarding_card").assertDoesNotExist()
        ui.onNodeWithTag("settings_button").performClick()
        ui.onNodeWithTag("replay_onboarding").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding_progress").assertTextEquals("1 מתוך 9")
        ui.onNodeWithTag("onboarding_skip").performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("onboarding_card").assertDoesNotExist()
        assertEquals(snapshot, runBlocking { db.workDao().exportSnapshot() })
        assertEquals(timer, context.getSharedPreferences("active_shift_prefs", 0).all)
        assertFalse(WorkOnboardingStore(context).initialize())
    }
}
