package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.WorkEntry
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
class WorkInterfacePolishTest {
    @get:Rule val ui = createComposeRule()
    @Test fun groupActionsAndPaymentReadable360() = checkActions()
    @Test @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun groupActionsAndPaymentReadable390() = checkActions()

    private fun checkActions() {
        val entry = WorkEntry(id = 71, category = "קבוצה עם שם ארוך במיוחד", date = 1700200000000,
            isTimeRange = false, hours = 2.0, hourlyRate = 999.0, totalEarnings = 87.25,
            currency = "$", isGroupShift = true, employerRate = 65.0, workerRate = 30.0,
            groupWorkersJson = """[{"name":"עובד עם שם ארוך","hours":3,"isPaid":false,"workerRate":31.25,"employerRate":67.5}]""")
        var paid = 0
        var edited = 0
        var deleted = 0
        var workerUpdate: WorkEntry? = null
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
                        WorkEntryRowCard(entry, onTogglePaid = { paid++ }, onEdit = { edited++ },
                            onDelete = { deleted++ }, onUpdateDirect = { workerUpdate = it })
                        Spacer(Modifier.height(12.dp))
                        RecentShiftCompactCard(entry.copy(id = 72, isPaid = true), onTogglePaid = {})
                    }
                }
            }
        }
        ui.onNodeWithTag("payment_status_71", useUnmergedTree = true).assertTextEquals("ממתין").assertIsDisplayed()
        ui.onNodeWithTag("recent_payment_status_72", useUnmergedTree = true).assertTextEquals("שולם").assertIsDisplayed()
        ui.onNodeWithTag("work_entry_card_71").performClick()
        ui.onNodeWithTag("entry_actions_71", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val card = ui.onNodeWithTag("work_entry_card_71").getUnclippedBoundsInRoot()
        val tags = listOf("global_share_btn_71", "edit_entry_btn_71", "delete_entry_btn_71")
        val bounds = tags.map { tag ->
            val node = ui.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            node.getUnclippedBoundsInRoot().also { assertTrue("action stays inside card", it.left >= card.left && it.right <= card.right) }
        }
        bounds.zipWithNext().forEach { (a, b) -> assertTrue("distinct action hit areas", a.left >= b.right || b.left >= a.right) }
        ui.onNodeWithContentDescription("שתף סיכום לקבלן").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        ui.onNodeWithContentDescription("שתף פרטי משמרת לעובד").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("toggle_payment_badge_71").assertHeightIsAtLeast(48.dp).performTouchInput { click() }
        assertEquals(1, paid)
        ui.onNodeWithTag("edit_entry_btn_71").performTouchInput { click() }
        ui.onNodeWithTag("delete_entry_btn_71").performTouchInput { click() }
        assertEquals(1, edited)
        assertEquals(1, deleted)
        ui.onNodeWithText("עובד עם שם ארוך").performClick()
        assertEquals(entry.totalEarnings, workerUpdate!!.totalEarnings, 0.0)
        assertEquals(entry.currency, workerUpdate!!.currency)
        assertTrue(org.json.JSONArray(workerUpdate!!.groupWorkersJson).getJSONObject(0).getBoolean("isPaid"))
        ui.onRoot().captureRoboImage(filePath = "/tmp/stage-interface-polish-${card.right.value.toInt()}.png")
    }
}
