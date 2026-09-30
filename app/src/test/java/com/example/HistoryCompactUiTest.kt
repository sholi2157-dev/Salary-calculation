package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.WorkEntry
import com.example.ui.HistoryToolbar
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
class HistoryCompactUiTest {
    @get:Rule val ui = createComposeRule()

    private val entries = listOf(
        WorkEntry(id = 1, category = "חיידר אידיש", date = 1, isTimeRange = false, hours = 2.0, hourlyRate = 999.0, totalEarnings = 320.0),
        WorkEntry(id = 2, category = "קטגוריה עם שם ארוך במיוחד", date = 1, isTimeRange = false, hours = 2.0, hourlyRate = 999.0, totalEarnings = 87.25, currency = "$"),
        WorkEntry(id = 3, category = "קבוצה", date = 1, isTimeRange = false, hours = 2.0, hourlyRate = 999.0, totalEarnings = 55.5, isGroupShift = true, employerRate = 888.0, workerRate = 777.0)
    )

    @Test fun compactHistoryAt360dp() = checkLayoutAndActions()

    @Test @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun compactHistoryAt390dp() = checkLayoutAndActions()

    private fun checkLayoutAndActions() {
        val currency = mutableStateOf("הכל")
        var filters = false
        var sort = ""
        var excel = false
        var whatsApp = false
        var toggled = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        HistoryToolbar(entries, currency.value, 2, { filters = true }, { currency.value = it }, { sort = it }, { excel = true }, { whatsApp = true })
                        entries.forEach { entry ->
                            WorkEntryRowCard(entry, isMultiSelectMode = true, isSelected = entry.id == 1,
                                onToggleSelect = { toggled = entry.id }, onTogglePaid = {}, onEdit = {}, onDelete = {})
                        }
                    }
                }
            }
        }
        ui.onNodeWithText("יומן עבודה", substring = true).assertDoesNotExist()
        ui.onNodeWithTag("history_total").assertTextContains("₪375.50", substring = true).assertTextContains("$87.25", substring = true)
        val toolbar = ui.onNodeWithTag("history_toolbar").getUnclippedBoundsInRoot()
        // A single compact control area, including both currencies.
        assertTrue("toolbar height: ${toolbar.bottom - toolbar.top}", toolbar.bottom - toolbar.top <= 64.dp)
        val total = ui.onNodeWithTag("history_total").getUnclippedBoundsInRoot()
        val filter = ui.onNodeWithTag("history_filters").getUnclippedBoundsInRoot()
        assertTrue("RTL total precedes controls", total.left >= filter.right)
        listOf("history_filters", "copy_menu_btn", "history_sort", "currency_filter_btn").forEach { tag ->
            ui.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= toolbar.left && bounds.right <= toolbar.right)
        }
        listOf("₪320.00", "$87.25", "₪55.50").forEachIndexed { i, amount ->
            ui.onNodeWithTag("selection_amount_${i + 1}", useUnmergedTree = true).assertTextEquals(amount).assertIsDisplayed()
            val card = ui.onNodeWithTag("work_entry_card_${i + 1}").getUnclippedBoundsInRoot()
            val money = ui.onNodeWithTag("selection_amount_${i + 1}", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(money.left >= card.left && money.right <= card.right)
        }
        ui.onRoot().captureRoboImage(filePath = "/tmp/salary-history-${(toolbar.right - toolbar.left).value.toInt()}.png")
        ui.onNodeWithTag("work_entry_card_2").performClick()
        assertEquals(2, toggled)
        ui.onNodeWithTag("history_filters").performClick()
        assertTrue(filters)
        listOf("₪", "$", "הכל").forEach { expected ->
            ui.onNodeWithTag("currency_filter_btn").performClick()
            assertEquals(expected, currency.value)
        }
        listOf("oldest" to "תאריך: מהישן לחדש", "latest_added" to "נוסף לאחרונה", "newest" to "תאריך: מהחדש לישן").forEach { (value, label) ->
            ui.onNodeWithTag("history_sort").performClick()
            ui.onNodeWithText(label).performClick()
            assertEquals(value, sort)
        }
        ui.onNodeWithTag("copy_menu_btn").performClick()
        ui.onNodeWithText("Excel (ייצוא)").performClick()
        assertTrue(excel)
        ui.onNodeWithTag("copy_menu_btn").performClick()
        ui.onNodeWithText("WhatsApp").performClick()
        assertTrue(whatsApp)
    }
}
