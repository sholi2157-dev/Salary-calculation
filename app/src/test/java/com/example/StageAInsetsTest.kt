package com.example

import android.app.Application
import android.graphics.Insets
import android.view.WindowInsets as AndroidInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WorkViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dispatches platform IME/gesture insets; does not claim a real device keyboard test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class StageAInsetsTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun insets(ime: Boolean) {
        ui.runOnUiThread {
            val density = ui.activity.resources.displayMetrics.density
            val nav = Insets.of(0, 0, 0, (24 * density).toInt())
            val value = AndroidInsets.Builder().setInsets(AndroidInsets.Type.navigationBars(), nav)
                .setInsets(AndroidInsets.Type.systemGestures(), nav)
                .setVisible(AndroidInsets.Type.navigationBars(), true)
                .setInsets(AndroidInsets.Type.ime(), Insets.of(0, 0, 0, if (ime) (300 * density).toInt() else 0))
                .setVisible(AndroidInsets.Type.ime(), ime).build()
            ui.activity.window.decorView.dispatchApplyWindowInsets(value)
        }
        ui.waitForIdle()
    }
    @Test fun keyboardAndGestureInsets360() = checkInsets()
    @Test @Config(qualifiers = "w390dp-h800dp-xhdpi") fun keyboardAndGestureInsets390() = checkInsets()
    private fun checkInsets() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = WorkViewModel(app, WorkRepository(WorkAccountScope(null).database(app).workDao()))
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        DashboardScreen(vm, WorkViewModel.StatsSummary(), listOf(WorkCategory(name = "עצמאי", defaultRate = 40.0)), emptyList(), null, "", 40.0, 1f,
                            { _, _, _ -> }, { _, _, _ -> }, emptyList(), {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, { _, _ -> }, {},
                            bottomNavigation = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("test_navigation")) { Text("ראשי / היסטוריה") } })
                    }
                }
            }
        }
        insets(false)
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        for (mode in listOf("שעון", "ידני", "קבוצה")) {
            ui.onNodeWithText(mode, useUnmergedTree = true).performScrollTo().performClick()
            ui.onNodeWithTag("notes_input").performScrollTo().performTextReplacement("הערה ארוכה\nשורה שנייה\nשורה שלישית\nשורה רביעית")
            insets(true)
            ui.onNodeWithTag("notes_input").performScrollTo().performClick().assertIsFocused()
            ui.onNodeWithTag("dashboard_scroll_container").performTouchInput { swipeUp() }
            ui.onNodeWithTag("notes_input").assertIsFocused().assertTextContains("שורה רביעית", substring = true)
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
            ui.onNodeWithTag("test_navigation").assertDoesNotExist()
            ui.onNodeWithTag("live_shift_fab").assertDoesNotExist()
            val viewport = ui.onNodeWithTag("dashboard_scroll_container").getUnclippedBoundsInRoot()
            val save = ui.onNodeWithTag("save_shift_button").getUnclippedBoundsInRoot()
            assertTrue(viewport.bottom <= save.top)
            if (mode == "קבוצה") ui.onNodeWithTag("worker_hours_input").performScrollTo().assertIsDisplayed()
            ui.onRoot().captureRoboImage(filePath = "/tmp/stage-ime-$mode-${ui.activity.resources.configuration.screenWidthDp}.png")
            ui.onNodeWithTag("notes_input").performScrollTo().performImeAction()
            insets(false)
            ui.onNodeWithTag("test_navigation").assertIsDisplayed()
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
        }
        ui.onNodeWithText("Ai", useUnmergedTree = true).performScrollTo().performClick()
        insets(true)
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().performTextReplacement("תיאור קולי שניתן לערוך\nתיאור נוסף")
        ui.onNodeWithTag("ai_parse_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        ui.onNodeWithTag("ai_free_text_input").assertIsFocused()
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().performImeAction()
        insets(false)
        ui.onNodeWithTag("test_navigation").assertIsDisplayed()
    }
}
