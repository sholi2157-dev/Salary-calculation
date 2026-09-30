package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class StageALayoutTest {
    @get:Rule val ui = createComposeRule()
    private fun model(): WorkViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return WorkViewModel(app, WorkRepository(WorkAccountScope(null).database(app).workDao()))
    }
    private val entries = (1..25).map { WorkEntry(id = it, category = "מעסיק $it", date = (26 - it).toLong(), isTimeRange = false, hours = 8.0, hourlyRate = 40.0, totalEarnings = 320.0, createdAt = it.toLong()) }
    @Test fun homeFormsAndDraftAt360() = homeForms()
    @Test @Config(qualifiers = "w390dp-h800dp-xhdpi") fun homeFormsAndDraftAt390() = homeForms()
    private fun homeForms() {
        val vm = model()
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    DashboardScreen(vm, WorkViewModel.StatsSummary(), listOf(WorkCategory(name = "עצמאי", defaultRate = 40.0)), emptyList(), null, "", 40.0, 1f,
                        { _, _ -> }, { _, _, _ -> }, entries.take(3), {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, { _, _ -> }, {},
                        bottomNavigation = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("test_navigation")) { Text("ראשי / היסטוריה", color = Color.White) } })
                }
            }
        }
        ui.onNodeWithTag("recent_shift_card_1").performScrollTo().assertIsDisplayed()
        val card = ui.onNodeWithTag("recent_shift_card_1").getUnclippedBoundsInRoot()
        val nav = ui.onNodeWithTag("test_navigation").getUnclippedBoundsInRoot()
        assertTrue("last recent card above navigation", card.bottom <= nav.top)
        ui.onNodeWithText("דיווח חדש").performScrollTo().performClick()
        ui.onNodeWithTag("live_shift_fab").assertDoesNotExist()
        ui.onNodeWithTag("report_date").performScrollTo()
        val fields = ui.onNodeWithTag("compact_report_fields").getUnclippedBoundsInRoot()
        assertTrue("compact form footprint ${fields.bottom - fields.top}", fields.bottom - fields.top < 350.dp)
        val date = ui.onNodeWithTag("report_date").getUnclippedBoundsInRoot()
        val start = ui.onNodeWithTag("report_start").getUnclippedBoundsInRoot()
        val end = ui.onNodeWithTag("report_end").getUnclippedBoundsInRoot()
        assertEquals(date.top, start.top); assertEquals(start.top, end.top)
        assertTrue("RTL date/start/end", date.left >= start.right && start.left >= end.right)
        ui.onNodeWithTag("notes_input").performScrollTo().performTextReplacement("טיוטה שנשמרת במעבר")
        for (mode in listOf("ידני", "קבוצה", "שעון")) {
            ui.onNodeWithText(mode, useUnmergedTree = true).performScrollTo().performClick()
            ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("טיוטה שנשמרת במעבר")
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
            val viewport = ui.onNodeWithTag("dashboard_scroll_container").getUnclippedBoundsInRoot()
            val save = ui.onNodeWithTag("save_shift_button").getUnclippedBoundsInRoot()
            assertTrue("Save has its own viewport space", viewport.bottom <= save.top)
            if (mode == "קבוצה") ui.onNodeWithTag("worker_hours_input").performScrollTo().assertIsDisplayed()
            ui.onRoot().captureRoboImage(filePath = "/tmp/stage-a-$mode-${nav.right.value.toInt()}.png")
        }
        ui.onNodeWithTag("add_rate_input").performScrollTo().performTextReplacement("25.5")
        ui.onNodeWithTag("add_rate_input").assertIsFocused()
        ui.onNodeWithText("Ai", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().performTextReplacement("טקסט לפענוח בלי מפתח — נשאר לעריכה")
        ui.onNodeWithTag("ai_parse_button").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("ai_inline_error").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("ai_free_text_input").assertTextContains("טקסט לפענוח בלי מפתח — נשאר לעריכה")
        ui.onNodeWithText("ידני", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("טיוטה שנשמרת במעבר")
        ui.onNodeWithText("Ai", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().assertTextContains("טקסט לפענוח בלי מפתח — נשאר לעריכה")
        ui.onRoot().captureRoboImage(filePath = "/tmp/stage-ai-dashboard-${nav.right.value.toInt()}.png")
    }
    @Test fun historyTopOverscrollAndFinalCard() {
        val vm = model()
        val movements = mutableListOf<Boolean>()
        var hidden by mutableStateOf(false)
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    Box(Modifier.fillMaxSize()) {
                        ShiftsScreen(navigationBottomInset = 64.dp, viewModel = vm, entries = entries, categories = emptyList(), searchQuery = "", onSearchQueryChange = {}, onTogglePaid = {}, onEdit = {}, onDelete = {}, onScrollStateChanged = { hidden = it; movements.add(it) })
                        if (!hidden) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp).background(Color.DarkGray).testTag("test_navigation"))
                    }
                }
            }
        }
        ui.onNodeWithTag("history_scroll_container").performTouchInput { swipeDown() }
        assertFalse("top overscroll must not hide navigation", movements.contains(true))
        ui.onNodeWithTag("test_navigation").assertIsDisplayed()
        ui.onNodeWithTag("history_scroll_container").performTouchInput { swipeUp() }
        assertTrue("real movement hides navigation", movements.contains(true))
        ui.onNodeWithTag("history_scroll_container").performScrollToNode(hasTestTag("work_entry_card_25"))
        ui.onNodeWithTag("history_scroll_container").performTouchInput { swipeUp() }
        ui.onNodeWithTag("test_navigation").assertIsDisplayed()
        val final = ui.onNodeWithTag("work_entry_card_25").getUnclippedBoundsInRoot()
        val nav = ui.onNodeWithTag("test_navigation").getUnclippedBoundsInRoot()
        assertTrue("complete final card is above returned navigation", final.bottom <= nav.top)
        ui.onNodeWithTag("work_entry_card_25").performTouchInput { longClick() }
        ui.onNodeWithText("נבחרו 1 משמרות").assertIsDisplayed()
    }
}
