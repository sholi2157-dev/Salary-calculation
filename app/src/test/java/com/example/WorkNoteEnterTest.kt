package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WorkViewModel
import com.example.ui.kis.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FB-01c (decided Oct 9): Enter in a shift's note is a new line. It never closes the note or the editor, and a
 * note-only edit keeps the saved amount exactly as it was. Same check as web browser-tests/note-enter.cjs.
 * Synthetic data only.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class WorkNoteEnterTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db by lazy { WorkAccountScope(null).database(app) }
    private val dao get() = db.workDao()

    @org.junit.Before fun clean() { runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() } }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) = ui.waitUntil(timeoutMs) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        condition()
    }

    @Test fun enterInTheShiftNoteIsANewLineAndKeepsTheSavedAmount() {
        val vm = WorkViewModel(app, WorkRepository(dao))
        val categories = listOf(WorkCategory(id = 1, name = "מסעדה", defaultRate = 50.0))
        val day = EntryDraft.startOfDay(System.currentTimeMillis()) - 86_400_000L
        runBlocking { dao.insertEntry(WorkEntry(category = "מסעדה", date = day + 17 * 3_600_000L, isTimeRange = true, startTime = "17:00",
            endTime = "22:30", hours = 5.5, hourlyRate = 52.0, totalEarnings = 299.97, notes = "שורה ראשונה")) }
        val base = runBlocking { dao.getEntriesList() }.single()
        var open by mutableStateOf(true)
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = false) { Box(Modifier.fillMaxSize().background(Kis.colors.bg)) {
                    if (open) KisEditorPage(vm, EntryDraft.from(base), base, categories, listOf(base), emptyList(),
                        onClose = { open = false }, onSaved = { _, _ -> }, onDeleted = {})
                } }
            }
        }
        // A saved note opens the note field by itself; the cursor goes to its end.
        ui.onNodeWithTag("notes_input").performScrollTo().performClick()
        ui.onNodeWithTag("notes_input").performTextInputSelection(androidx.compose.ui.text.TextRange("שורה ראשונה".length))
        ui.onNodeWithTag("notes_input").performKeyInput { pressKey(Key.Enter) }
        ui.waitForIdle()
        // Still open, the editor and its note field.
        assertTrue(open)
        ui.onNodeWithTag("notes_input").assertExists()
        ui.onNodeWithTag("notes_input").performTextInput("שורה שנייה")
        ui.onNodeWithTag("notes_input").assertTextContains("שורה ראשונה\nשורה שנייה", substring = true)
        ui.onNodeWithTag("save_shift_button").performScrollTo().assertIsEnabled().performClick()
        waitFor(5_000) { runBlocking { dao.getEntriesList() }.single().notes == "שורה ראשונה\nשורה שנייה" }
        val kept = runBlocking { dao.getEntriesList() }.single()
        // Only the note changed: the stored amount, hours, rate and date are exactly as saved before.
        assertEquals(299.97, kept.totalEarnings, 0.0)
        assertEquals(5.5, kept.hours, 0.0); assertEquals(52.0, kept.hourlyRate, 0.0)
        assertEquals(base.date, kept.date); assertEquals("17:00", kept.startTime); assertEquals("22:30", kept.endTime)
        assertEquals(base.currency, kept.currency); assertEquals(base.payKnown, kept.payKnown)
    }
}
