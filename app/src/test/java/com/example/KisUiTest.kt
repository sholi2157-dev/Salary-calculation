package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WorkViewModel
import com.example.ui.kis.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 2.0 ("כיס") screens with a real Room database and synthetic data.
 * Screenshots go to /tmp/kis-*.png for visual review (light and dark).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class KisUiTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db by lazy { WorkAccountScope(null).database(app) }
    private val dao get() = db.workDao()
    private fun model() = WorkViewModel(app, WorkRepository(dao))

    /** The account database is a process-wide singleton; every test starts from empty tables. */
    @org.junit.Before fun clean() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
    }
    private val categories = listOf(WorkCategory(id = 1, name = "מסעדה", defaultRate = 50.0), WorkCategory(id = 2, name = "שיעורים", defaultRate = 120.0), WorkCategory(id = 3, name = "הובלות", defaultRate = 60.0))
    private val day = EntryDraft.startOfDay(System.currentTimeMillis())
    /**
     * The 15th of this month, or of last month before the 15th: seeded rows (up to 4 days earlier) are always done
     * (a later day is planned and stays out of History's totals) and in one month, which History is told to show.
     */
    private val mid = java.util.Calendar.getInstance().apply {
        timeInMillis = day
        if (get(java.util.Calendar.DAY_OF_MONTH) < 15) add(java.util.Calendar.MONTH, -1)
        set(java.util.Calendar.DAY_OF_MONTH, 15)
    }.timeInMillis
    private fun seededHistory() = HistoryUi().apply { showMonth = HistoryFilter.monthKey(mid) }

    private fun host(dark: Boolean = false, content: @Composable () -> Unit) {
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = dark) { Box(Modifier.fillMaxSize().background(Kis.colors.bg)) { content() } }
            }
        }
    }
    private fun shot(name: String) = ui.onRoot().captureRoboImage(filePath = "/tmp/kis-$name.png")
    private fun rows() = runBlocking { dao.getEntriesList() }
    /** Robolectric's paused main looper must run the view-model continuations (Room resumes on Main) while we wait. */
    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) = ui.waitUntil(timeoutMs) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        condition()
    }

    @Test fun editorClockKeypadRulerDraftAndUnknownRate360() = editorFlow(false)
    @Test @Config(qualifiers = "w390dp-h844dp-xhdpi") fun editorClockKeypadRulerDraftAndUnknownRateDark390() = editorFlow(true)

    private fun editorFlow(dark: Boolean) {
        val vm = model()
        var saved = ""
        var closed = false
        val suffix = if (dark) "dark-390" else "light-360"
        host(dark) {
            KisEditorPage(vm, EntryDraft(category = "מסעדה", rate = "50", currency = "₪", start = null, end = "17:00", day = day), null, categories, emptyList(), emptyList(),
                onClose = { closed = true }, onSaved = { t, _ -> saved = t }, onDeleted = {})
        }
        // Nothing can be saved before a start time exists.
        ui.onNodeWithTag("save_hint").assertTextContains("שעת התחלה", substring = true)
        ui.onNodeWithTag("save_shift_button").assertIsNotEnabled()
        shot("editor-empty-$suffix")
        // Keypad: 0900 sets the start and moves on to the end; 1730 sets the end.
        ui.onNodeWithTag("clock_start").performClick()
        ui.onNodeWithTag("keypad").assertIsDisplayed()
        // The keypad shows which field is being typed and has no Save of its own (no accidental saves).
        ui.onNodeWithTag("keypad_display").assertTextContains("שעת התחלה", substring = true)
        ui.onNodeWithTag("save_shift_button").assertDoesNotExist()
        listOf("0", "9", "0", "0", "1", "7", "3", "0").forEach { ui.onNodeWithTag("key_$it").performClick() }
        ui.onNodeWithTag("clock_start").assertTextContains("09:00", substring = true)
        ui.onNodeWithTag("clock_end").assertTextContains("17:30", substring = true)
        ui.onNodeWithTag("editor_meta").assertTextContains("8:30 שעות", substring = true)
        ui.onNodeWithTag("editor_total", useUnmergedTree = true).assertTextEquals("₪425")
        shot("editor-keypad-$suffix")
        ui.onNodeWithTag("keypad_done").performClick()
        ui.onNodeWithTag("keypad").assertDoesNotExist()
        // Ruler and nudges change the selected handle (end) in 15-minute steps.
        ui.onNodeWithTag("ruler_start").assertExists()
        ui.onNodeWithTag("ruler_end").assertExists()
        ui.onNodeWithTag("nudge_15").performClick()
        ui.onNodeWithTag("clock_end").assertTextContains("17:45", substring = true)
        ui.onNodeWithTag("editor_total", useUnmergedTree = true).assertTextEquals("₪437.50")
        shot("editor-clock-$suffix")
        // A note survives every mode switch (draft is kept).
        ui.onNodeWithTag("editor_more").performScrollTo().performClick()
        ui.onNodeWithTag("notes_input").performScrollTo().performTextReplacement("טיוטה שנשמרת במעבר")
        for (mode in listOf("hours", "group", "clock")) {
            ui.onNodeWithTag("mode_$mode").performClick()
            ui.onNodeWithTag("notes_input").performScrollTo().assertTextContains("טיוטה שנשמרת במעבר")
            ui.onNodeWithTag("save_shift_button").assertIsDisplayed()
            if (mode == "group") ui.onNodeWithTag("group_block").performScrollTo().assertIsDisplayed()
            shot("editor-$mode-$suffix")
        }
        ui.onNodeWithTag("mode_job").performClick()
        ui.onNodeWithTag("job_note").performScrollTo().assertIsDisplayed()
        shot("editor-job-$suffix")
        ui.onNodeWithTag("mode_clock").performClick()
        // Unknown rate: saving is allowed and the total says what is missing instead of ₪0.
        // A break is one tap: 30 minutes off the shift, then back to none.
        ui.onNodeWithTag("editor_scroll").performScrollToNode(hasTestTag("break_30"))
        ui.waitForIdle()
        shot("editor-break-scrolled-$suffix")
        ui.onNodeWithTag("break_30").performClick()
        try { ui.onNodeWithTag("editor_meta").assertTextContains("8:15 שעות", substring = true) }
        catch (e: Throwable) { shot("editor-break-failed-$suffix"); throw e }
        shot("editor-break-$suffix")
        ui.onNodeWithTag("break_0").performScrollTo().performClick()
        ui.onNodeWithTag("editor_meta").assertTextContains("8:45 שעות", substring = true)
        ui.onNodeWithTag("editor_rate_unknown").performScrollTo().performClick()
        ui.onNodeWithTag("editor_meta").assertTextContains("תעריף חסר", substring = true)
        ui.onNodeWithTag("save_shift_button").assertIsEnabled()
        shot("editor-rate-unknown-$suffix")
        ui.onNodeWithTag("save_shift_button").performClick()
        waitFor(5_000) { rows().size == 1 }
        val e = rows().single()
        assertEquals("מסעדה", e.category)
        assertTrue(e.isTimeRange); assertEquals("09:00", e.startTime); assertEquals("17:45", e.endTime)
        assertEquals(8.75, e.hours, 1e-9)
        assertFalse(e.payKnown); assertEquals(0.0, e.hourlyRate, 0.0); assertEquals(0.0, e.totalEarnings, 0.0)
        assertEquals("טיוטה שנשמרת במעבר", e.notes)
        assertEquals(WorkEntry.KIND_SHIFT, e.kind)
        waitFor(10_000) { closed }
        assertTrue(saved.contains("התעריף"))
    }

    @Test fun editKeepsStoredAmountUntilHoursRateOrBonusChange() {
        val vm = model()
        val id = runBlocking { dao.insertEntry(WorkEntry(category = "מסעדה", date = day + 17 * 3_600_000L, isTimeRange = true, startTime = "17:00", endTime = "22:30",
            hours = 5.5, hourlyRate = 52.0, totalEarnings = 299.97, notes = "סכום שהוזן ידנית")).toInt() }
        var base by mutableStateOf(rows().single())
        var open by mutableStateOf(true)
        host {
            if (open) KisEditorPage(vm, EntryDraft.from(base), base, categories, listOf(base), emptyList(), onClose = { open = false }, onSaved = { _, _ -> }, onDeleted = {})
        }
        ui.onNodeWithTag("was_banner").assertIsDisplayed()
        ui.onNodeWithTag("save_shift_button").assertIsNotEnabled()
        shot("edit-unchanged")
        ui.onNodeWithTag("editor_total", useUnmergedTree = true).assertTextEquals("₪299.97")
        // A saved note opens the note drawer by itself.
        ui.onNodeWithTag("notes_input").performScrollTo().performTextReplacement("רק ההערה השתנתה")
        ui.onNodeWithTag("save_shift_button").assertIsEnabled().performClick()
        waitFor(5_000) { rows().single().notes == "רק ההערה השתנתה" }
        val kept = rows().single()
        assertEquals(299.97, kept.totalEarnings, 0.0)
        assertEquals(5.5, kept.hours, 0.0); assertEquals(52.0, kept.hourlyRate, 0.0); assertEquals(base.date, kept.date)
        // Now change the rate: the amount follows hours × rate.
        ui.runOnIdle { base = kept; open = true }
        ui.onNodeWithTag("editor_rate").performScrollTo().performClick()
        ui.onNodeWithTag("keypad_display").assertTextContains("תעריף לשעה", substring = true)
        ui.onNodeWithTag("keypad_value", useUnmergedTree = true).assertTextEquals("52")
        listOf("6", "0").forEach { ui.onNodeWithTag("key_$it").performClick() }
        ui.onNodeWithTag("keypad_value", useUnmergedTree = true).assertTextEquals("60")
        ui.onNodeWithTag("keypad_done").performClick()
        ui.onNodeWithTag("save_shift_button").performClick()
        waitFor(5_000) { rows().single().hourlyRate == 60.0 }
        assertEquals(330.0, rows().single().totalEarnings, 0.0)
        assertEquals(id, rows().single().id)
    }

    private fun seed(): List<WorkEntry> = runBlocking {
        listOf(
            WorkEntry(category = "מסעדה", date = mid + 17 * 3_600_000L, isTimeRange = true, startTime = "17:00", endTime = "23:00", hours = 6.0, hourlyRate = 50.0, totalEarnings = 300.0, isPaid = true),
            WorkEntry(category = "מסעדה", date = mid - 86_400_000L + 18 * 3_600_000L, isTimeRange = true, startTime = "18:00", endTime = "01:00", hours = 7.0, hourlyRate = 0.0, totalEarnings = 0.0, payKnown = false),
            WorkEntry(category = "שיעורים", date = mid - 2 * 86_400_000L, isTimeRange = false, hours = 2.5, hourlyRate = 0.0, totalEarnings = 0.0, payKnown = false, bonus = 20.0),
            WorkEntry(category = "הובלות", date = mid - 3 * 86_400_000L, isTimeRange = false, hours = 0.0, hourlyRate = 0.0, totalEarnings = 0.0, payKnown = false, kind = WorkEntry.KIND_JOB, notes = "פירוק ארון"),
            WorkEntry(category = "הובלות", date = mid - 4 * 86_400_000L, isTimeRange = false, hours = 3.0, hourlyRate = 0.0, totalEarnings = 180.0, kind = WorkEntry.KIND_JOB, notes = "הובלת מקרר", currency = "$"),
        ).forEach { dao.insertEntry(it) }
        dao.getEntriesList()
    }

    @Test fun historySelectionBulkRateNeverTouchesKnownOrJobs() {
        val vm = model()
        seed()
        val state = seededHistory()
        var entries by mutableStateOf(rows())
        host {
            KisHistory(state, true, entries, categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {})
        }
        ui.onNodeWithTag("history_total").assertTextContains("₪300", substring = true).assertTextContains("$180", substring = true)
        ui.onNodeWithTag("pending_flag").assertTextContains("2 משמרות", substring = true)
        shot("history")
        val pendingShift = entries.first { !it.payKnown && it.category == "מסעדה" }
        ui.onNodeWithTag("work_entry_card_${pendingShift.id}").performScrollTo().performTouchInput { longClick() }
        ui.onNodeWithTag("selection_count").assertTextEquals("נבחרו 1")
        // The tap action itself: a card scrolled to the list's edge can sit under the selection bar in Robolectric's layout.
        entries.filter { it.id != pendingShift.id }.forEach { ui.onNodeWithTag("work_entry_card_${it.id}").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
        ui.onNodeWithTag("selection_count").assertTextEquals("נבחרו 5")
        ui.onNodeWithTag("selection_bulk_rate").assertTextContains("2", substring = true)
        shot("history-selection")
        ui.onNodeWithTag("selection_bulk_rate").performClick()
        ui.onNodeWithTag("bulk_rate_input").performTextReplacement("40")
        ui.onNodeWithTag("rate_sheet").assertExists()
        shot("history-bulk-rate")
        ui.onNodeWithTag("bulk_rate_apply").performClick()
        waitFor(5_000) { rows().count { it.payKnown } == 4 }
        val after = rows().associateBy { it.id }
        val restaurant = after.getValue(pendingShift.id)
        assertEquals(280.0, restaurant.totalEarnings, 0.0); assertEquals(40.0, restaurant.hourlyRate, 0.0)
        val lesson = after.values.first { it.category == "שיעורים" }
        assertEquals(120.0, lesson.totalEarnings, 0.0) // 2.5 × 40 + 20 bonus
        assertEquals(300.0, after.values.first { it.isPaid }.totalEarnings, 0.0)
        assertFalse(after.values.first { it.notes == "פירוק ארון" }.payKnown)
        assertEquals(180.0, after.values.first { it.notes == "הובלת מקרר" }.totalEarnings, 0.0)
        ui.runOnIdle { entries = rows() }
        // Filters: "בלי תעריף" shows only what is still missing.
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("history_status_PENDING").performClick()
        ui.onNodeWithTag("filters_apply").performClick()
        ui.onNodeWithTag("work_entry_card_${pendingShift.id}").assertDoesNotExist()
        ui.onNodeWithText("קבלנות: פירוק ארון", substring = true).assertExists()
        // Sort and copy menus stay reachable.
        // Order lives in the same one panel.
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("sort_ADDED").performClick()
        ui.onNodeWithTag("filters_apply").performClick()
        ui.onNodeWithTag("history_scope").assertTextContains("לפי סדר ההוספה", substring = true)
        ui.onNodeWithTag("copy_menu_btn").performClick()
        ui.onNodeWithText("Excel (טבלה)").assertExists()
    }

    @Test fun longPressForgetsASavedWorkerButNotHistory() {
        val vm = model()
        val past = WorkEntry(category = "מסעדה", date = day - 86_400_000L, isTimeRange = false, hours = 4.0, hourlyRate = 50.0, totalEarnings = 200.0, isGroupShift = true,
            groupWorkersJson = """[{"name":"יוסי","hours":4,"isPaid":false}]""")
        runBlocking { dao.insertWorker(WorkerDirectory(name = "יוסי")); dao.insertEntry(past) }
        host {
            KisEditorPage(vm, EntryDraft(category = "מסעדה", rate = "50", currency = "₪", start = "09:00", end = "13:00", day = day), null, categories, rows(),
                runBlocking { dao.getWorkersList() }, onClose = {}, onSaved = { _, _ -> }, onDeleted = {})
        }
        ui.onNodeWithTag("mode_group").performClick()
        // The long-press action itself (the same one TalkBack uses); the gesture is covered on device.
        ui.onNodeWithTag("add_worker_יוסי").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        try { waitFor(5_000) { ui.onAllNodesWithTag("forget_worker_confirm").fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { shot("forget-worker-failed"); throw e }
        ui.onNodeWithTag("forget_worker_confirm").performClick()
        waitFor(5_000) { runBlocking { dao.getWorkersList() }.isEmpty() }
        // The past group shift still lists the worker.
        assertTrue(rows().single().groupWorkersJson.contains("יוסי"))
    }

    @Test fun searchHasSeparateClearAndClose() {
        val vm = model()
        seed()
        val state = seededHistory().apply { searching = true }
        host { KisHistory(state, true, rows(), categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {}) }
        ui.onNodeWithTag("history_search_input").performTextInput("ארון")
        ui.onNodeWithText("קבלנות: פירוק ארון", substring = true).assertExists()
        ui.onNodeWithTag("history_search_clear").performClick()
        ui.onNodeWithTag("history_search_input").assertExists()
        assertEquals("", state.query)
        ui.onNodeWithTag("history_search_close").performClick()
        ui.onNodeWithTag("history_search_input").assertDoesNotExist()
        ui.onNodeWithTag("history_query_chip").assertDoesNotExist()
        // A closed search that still filters stays visible as a chip: tap to edit, ✕ to clear.
        ui.runOnIdle { state.query = "ארון" }
        ui.onNodeWithTag("history_query_chip").onChildren().onFirst().assertTextContains("ארון", substring = true)
        shot("history-search-chip")
        ui.onNodeWithTag("history_query_chip").onChildren().onFirst().performClick()
        ui.onNodeWithTag("history_search_input").assertExists()
        ui.onNodeWithTag("history_search_close").performClick()
        ui.onNodeWithTag("history_query_clear").performClick()
        assertEquals("", state.query)
        ui.onNodeWithTag("history_query_chip").assertDoesNotExist()
    }

    @Test fun detailActionsAreLargeAndDistinct() {
        val entry = WorkEntry(id = 71, category = "מסעדה", date = day, isTimeRange = true, startTime = "22:00", endTime = "06:00", hours = 7.5, hourlyRate = 45.75,
            totalEarnings = 343.12, bonus = 0.0, currency = "$", notes = "לילה עם הפסקה")
        var paid = 0; var edited = 0; var shared = 0; var copied = 0; var deleted = 0
        host {
            ShiftDetailContent(entry, categories, onPaid = { paid++ }, onEdit = { edited++ }, onShare = { shared++ }, onCopy = { copied++ }, onDelete = { deleted++ }, onCompleteRate = {}, group = {})
        }
        ui.onNodeWithTag("detail_amount", useUnmergedTree = true).assertTextEquals("$343.12")
        ui.onNodeWithTag("detail_hours", useUnmergedTree = true).assertTextContains("למחרת", substring = true)
        val tags = listOf("edit_entry_btn_71", "share_entry_btn_71", "copy_entry_btn_71", "delete_entry_btn_71")
        val bounds = tags.map { ui.onNodeWithTag(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot() }
        bounds.zipWithNext().forEach { (a, b) -> assertTrue("one compact row, RTL", a.top == b.top && a.left >= b.right) }
        tags.forEach { ui.onNodeWithTag(it).performClick() }
        assertEquals(listOf(1, 1, 1, 1), listOf(edited, shared, copied, deleted))
        ui.onNodeWithTag("toggle_payment_71").performClick()
        assertEquals(1, paid)
        shot("detail")
    }

    @Test fun homeShowsTodayWeekAndAllTimeWithoutFutureOrUnknownPay() {
        val now = System.currentTimeMillis()
        val today = EntryDraft.startOfDay(now) + 60_000L
        runBlocking {
            dao.insertEntry(WorkEntry(category = "מסעדה", date = today, isTimeRange = false, hours = 6.0, hourlyRate = 50.0, totalEarnings = 300.0))
            dao.insertEntry(WorkEntry(category = "שיעורים", date = today, isTimeRange = false, hours = 2.0, hourlyRate = 0.0, totalEarnings = 0.0, payKnown = false))
            // Hours-only shifts are stored at noon: they count today even in the morning.
            dao.insertEntry(WorkEntry(category = "מסעדה", date = EntryDraft.startOfDay(now) + 12 * 3_600_000L, isTimeRange = false, hours = 1.0, hourlyRate = 50.0, totalEarnings = 50.0))
            dao.insertEntry(WorkEntry(category = "הובלות", date = now - 40 * 86_400_000L, isTimeRange = false, hours = 3.0, hourlyRate = 0.0, totalEarnings = 180.0, kind = WorkEntry.KIND_JOB, currency = "$"))
            dao.insertEntry(WorkEntry(category = "מסעדה", date = now - 50 * 86_400_000L, isTimeRange = false, hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0))
            // Planned for later: never counted as earned.
            dao.insertEntry(WorkEntry(category = "מסעדה", date = now + 3 * 86_400_000L, isTimeRange = false, hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0))
        }
        var dark by mutableStateOf(false)
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = dark) {
                    Box(Modifier.fillMaxSize().background(Kis.colors.bg)) { KisHome(rows(), categories, null, 80.dp, {}, {}, {}, {}, {}, {}, {}, {}) }
                }
            }
        }
        ui.onNodeWithTag("home_hero").assertIsDisplayed()
        ui.onNodeWithTag("home_total_₪", useUnmergedTree = true).assertTextEquals("₪350")
        ui.onNodeWithTag("home_week_count").assertTextEquals("3 משמרות השבוע")
        ui.onNodeWithTag("home_today_₪", useUnmergedTree = true).assertTextEquals("₪350")
        ui.onNodeWithTag("home_all_₪", useUnmergedTree = true).assertTextEquals("₪450")
        ui.onNodeWithTag("home_all_$", useUnmergedTree = true).assertTextEquals("$180")
        ui.onNodeWithTag("home_all_count", useUnmergedTree = true).assertTextEquals("5 משמרות")
        ui.onNodeWithTag("pending_flag").assertIsDisplayed()
        ui.onNodeWithTag("live_shift_fab").assertIsDisplayed()
        shot("home-light")
        ui.runOnIdle { dark = true }
        shot("home-dark")
    }

    @Test fun weeklyPlanFromEditorIsCreatedWithItsDaysAndPreview() {
        val vm = model()
        var closed = false
        host {
            KisEditorPage(vm, EntryDraft(category = "מסעדה", rate = "50", currency = "₪", start = "09:00", end = "17:00", day = day), null, categories, emptyList(), emptyList(),
                onClose = { closed = true }, onSaved = { _, _ -> }, onDeleted = {})
        }
        ui.onNodeWithTag("repeat_switch").performScrollTo().performClick()
        val weekday = weekdayOf(day)
        ui.onNodeWithTag("repeat_day_$weekday").performScrollTo().assertIsSelected()
        // Today plus eight weeks: nine days, nothing overlaps.
        ui.onNodeWithTag("repeat_preview").performScrollTo().assertTextEquals("9 משמרות ייכנסו ללוח")
        ui.onNodeWithTag("save_shift_button").assertTextContains("יצירת הסדרה", substring = true)
        shot("editor-repeat")
        ui.onNodeWithTag("save_shift_button").performClick()
        waitFor(5_000) { closed }
        val s = runBlocking { dao.getSeries() }.single()
        assertEquals("מסעדה", s.category); assertEquals(com.example.domain.V2Plan.mask(setOf(weekday)), s.daysMask)
        assertEquals("09:00", s.startTime); assertEquals("17:00", s.endTime); assertEquals(50.0, s.hourlyRate!!, 0.0)
        assertEquals(com.example.domain.V2Plan.dateOf(day), s.fromDate); assertEquals(isoDayPlus(day, 56), s.untilDate)
        // Only today can already have ended; it is then recorded once, as a normal shift.
        assertTrue(rows().size <= 1)
        rows().forEach { assertEquals("${s.id}@${s.fromDate}", it.seriesKey); assertTrue(it.autoConfirmed) }
    }

    @Test fun plannedShiftsShowOnHomeAndInTheirOwnHistoryViewOnly() {
        val vm = model()
        seed()
        val now = System.currentTimeMillis()
        val series = com.example.data.ShiftSeries("plan-1", "מסעדה", com.example.domain.V2Plan.mask((0..6).toSet()), "09:00", "17:00", 50.0, "₪",
            isoDayPlus(now, -2), isoDayPlus(now, 10))
        val plans = com.example.domain.V2Plan.occurrences(listOf(series), emptyList(), emptySet(), isoDayPlus(now, -2), isoDayPlus(now, 10), now)
        val worked = mutableListOf<String>(); val skipped = mutableListOf<String>()
        val actions = PlanActions(worked = { worked += it.key }, skip = { skipped += it.key }, hours = {}, share = {})
        val state = seededHistory()
        var page by mutableStateOf(0)
        host {
            if (page == 0) KisHome(rows(), categories, null, 80.dp, {}, {}, {}, {}, {}, {}, {}, {}, plans = plans, planActions = actions, onAllPlans = { state.planned = true; page = 1 })
            else KisHistory(state, true, rows(), categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {}, plans = plans, planActions = actions)
        }
        // Home: what ended waits first; the totals never include plans.
        ui.onNodeWithTag("next_up").performScrollTo().assertExists()
        val waiting = plans.first { it.status == com.example.domain.V2Plan.Status.AWAITING }
        ui.onNodeWithTag("planned_card_${waiting.key}").performScrollTo().performClick()
        ui.onNodeWithTag("plan_worked_${waiting.key}").performClick()
        ui.onNodeWithTag("plan_skip_${waiting.key}").performClick()
        assertEquals(listOf(waiting.key), worked); assertEquals(listOf(waiting.key), skipped)
        shot("home-planned")
        ui.onNodeWithTag("next_up_all").performScrollTo().performClick()
        // History: the planned view lists plans only, without totals, copy or Excel.
        ui.onNodeWithTag("history_planned").assertIsSelected()
        ui.onNodeWithTag("history_total").assertDoesNotExist()
        ui.onNodeWithTag("copy_menu_btn").assertDoesNotExist()
        ui.onNodeWithTag("history_scroll_container").performScrollToNode(hasTestTag("planned_card_${plans.last().key}"))
        shot("history-planned")
        ui.onNodeWithTag("history_scroll_container").performScrollToIndex(0)
        ui.onNodeWithTag("history_worked").performClick()
        ui.onNodeWithTag("history_total").assertTextContains("₪300", substring = true)
        ui.onAllNodesWithTag("planned_card_${plans.last().key}").assertCountEquals(0)
    }

    @Test fun futureShiftIsPlannedEverywhereAndNeverInTotals() {
        val vm = model()
        val now = System.currentTimeMillis()
        val todayRow = WorkEntry(category = "מסעדה", date = day + 60_000L, isTimeRange = false, hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0)
        val future = WorkEntry(category = "מסעדה", date = EntryDraft.startOfDay(now + 3 * 86_400_000L) + 12 * 3_600_000L, isTimeRange = false, hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0)
        runBlocking { dao.insertEntry(todayRow); dao.insertEntry(future) }
        val futureId = rows().first { it.totalEarnings == 250.0 }.id
        val state = HistoryUi()
        var page by mutableStateOf(0)
        host {
            if (page == 0) KisHome(rows(), categories, null, 80.dp, {}, {}, {}, {}, {}, {}, {}, {}, onAllPlans = { state.planned = true; page = 1 })
            else KisHistory(state, true, rows(), categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {})
        }
        // Home: the future shift waits under "הבא בתור", marked as planned, and is not in today's or the week's money.
        ui.onNodeWithTag("next_entry_$futureId").performScrollTo().assertExists()
        // Marked in "הבא בתור" and in "אחרונות" alike.
        ui.onAllNodesWithTag("planned_tag_$futureId", useUnmergedTree = true).assertCountEquals(2)
        ui.onNodeWithTag("home_today_₪", useUnmergedTree = true).assertTextEquals("₪100")
        shot("home-future")
        ui.onNodeWithTag("next_up_all").performScrollTo().performClick()
        // History: "מתוכנן" holds it; "עבדתי" and its total never include it.
        ui.onNodeWithTag("history_planned").assertIsSelected()
        ui.onNodeWithTag("planned_entry_$futureId").assertExists()
        shot("history-planned-future")
        ui.onNodeWithTag("history_worked").performClick()
        ui.onAllNodesWithTag("work_entry_card_$futureId").assertCountEquals(0)
        if (HistoryFilter.monthKey(future.date) == HistoryFilter.monthKey(now))
            ui.onNodeWithTag("history_total").assertTextContains("₪100", substring = true)
    }

    @Test fun historyShowsAllDatesByDefaultWithOnePanelButton() {
        val vm = model()
        val old = WorkEntry(category = "מסעדה", date = day - 120 * 86_400_000L, isTimeRange = false, hours = 3.0, hourlyRate = 50.0, totalEarnings = 150.0)
        val recent = WorkEntry(category = "מסעדה", date = day + 60_000L, isTimeRange = false, hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0)
        runBlocking { dao.insertEntry(old); dao.insertEntry(recent) }
        host { KisHistory(HistoryUi(), true, rows(), categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {}) }
        ui.onNodeWithTag("history_scope").assertTextContains("כל התאריכים", substring = true)
        ui.onNodeWithTag("history_total").assertTextContains("₪250", substring = true)
        ui.onAllNodesWithTag("history_sort").assertCountEquals(0)
        ui.onAllNodesWithTag("history_reset").assertCountEquals(0)
        // One panel: dates, order and filters, with the count it will show.
        ui.onNodeWithTag("history_filters").performClick()
        ui.onNodeWithTag("period_MONTH").performClick()
        ui.onNodeWithTag("filters_apply").assertTextContains("משמרת אחת", substring = true)
        shot("history-filters-panel")
        ui.onNodeWithTag("filters_apply").performClick()
        ui.onNodeWithTag("history_total").assertTextContains("₪100", substring = true)
        ui.onNodeWithTag("history_reset").performClick()
        ui.onNodeWithTag("history_total").assertTextContains("₪250", substring = true)
        shot("history-all-dates")
    }

    @Test fun plannedViewIsAlwaysThereAndExplainsWhenEmpty() {
        val vm = model()
        val state = HistoryUi()
        host { KisHistory(state, true, emptyList(), categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {}) }
        ui.onNodeWithTag("history_planned").assertHeightIsAtLeast(48.dp).performClick()
        ui.onNodeWithTag("planned_empty").assertExists()
        ui.onNodeWithText("חוזרת כל שבוע", substring = true).assertExists()
    }

    @Test fun monthSummaryCountsDoneShiftsPerCurrency() {
        seed()
        host { MonthSummarySheet(HistoryFilter.monthKey(mid), rows(), categories, onHistory = {}, onDismiss = {}, onMessage = {}) }
        ui.onNodeWithTag("month_summary_money").assertTextContains("₪300", substring = true).assertTextContains("$180", substring = true)
        ui.onNodeWithTag("month_summary_counts", useUnmergedTree = true).assertTextEquals("5 משמרות · 18.5 שעות")
        ui.onNodeWithTag("month_story").assertExists()
        ui.onNodeWithTag("month_insight_0").assertExists()
        shot("month-summary")
    }

    @Test fun storyRecapMovesOnHidesTheAmountAndSharesACardWithoutIt() {
        seed()
        var closed = false
        ui.mainClock.autoAdvance = false
        host { KisStory(com.example.domain.V2Summary.monthScope(HistoryFilter.monthKey(mid)), rows(), categories, onClose = { closed = true }, onMessage = {}) }
        ui.mainClock.advanceTimeBy(1_000)
        ui.onNodeWithTag("story_intro").assertExists()
        runCatching { ui.onNodeWithTag("story_root").captureRoboImage(filePath = "/tmp/kis-story-intro.png") }
        // It moves on by itself.
        ui.mainClock.advanceTimeBy(6_500)
        ui.onNodeWithTag("story_hours").assertExists()
        fun next() { ui.onNodeWithTag("story_next").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); ui.mainClock.advanceTimeBy(800) }
        repeat(10) { if (ui.onAllNodesWithTag("story_money").fetchSemanticsNodes().isEmpty()) next() }
        assertEquals(2, ui.onAllNodesWithTag("story_money_hidden", useUnmergedTree = true).fetchSemanticsNodes().size)
        ui.onNodeWithText("₪300", substring = true).assertDoesNotExist()
        ui.onNodeWithTag("story_reveal").performClick(); ui.mainClock.advanceTimeBy(100)
        ui.onNodeWithTag("story_money_₪", useUnmergedTree = true).assertExists()
        next()
        ui.onNodeWithTag("story_card").assertExists()
        ui.onNodeWithTag("story_poster").assert(hasText("₪", substring = true).not()).assert(hasText("$", substring = true).not())
        runCatching { ui.onNodeWithTag("story_root").captureRoboImage(filePath = "/tmp/kis-story-card.png") }
        // The card waits; going back works; forward from the card closes.
        ui.mainClock.advanceTimeBy(10_000)
        ui.onNodeWithTag("story_card").assertExists()
        ui.onNodeWithTag("story_prev").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); ui.mainClock.advanceTimeBy(800)
        ui.onNodeWithTag("story_money").assertExists()
        next(); next()
        assertTrue(closed)
    }

    @Test fun bellSheetListsRecentAndOpensTheirMonth() {
        val vm = model()
        val plan = vm.planSettings
        (1..25).forEach { plan.addRecent(PlanSettings.Sent("monthly", "סיכום $it", "טקסט", it.toLong(), "2026-09")) }
        assertEquals(PlanSettings.RECENT_MAX, plan.recent.size)
        assertEquals("סיכום 25", plan.recent.first().title)
        assertTrue(plan.unread)
        var opened: Int? = null
        var settings = false
        host {
            HeaderActions(unread = true, onBell = {})
            NotificationsSheet(vm, onMonth = { opened = it }, onSettings = { settings = true }, onDismiss = {})
        }
        ui.onNodeWithTag("home_bell_dot", useUnmergedTree = true).assertExists()
        assertFalse(plan.unread)
        ui.onNodeWithTag("notify_monthly_switch").assertDoesNotExist()
        ui.onNodeWithTag("recent_note_0").performScrollTo().performClick()
        assertEquals(2026 * 12 + 8, opened)
        ui.onNodeWithTag("notifications_settings").performClick()
        assertTrue(settings)
    }

    @Test fun launchShowsTheSloganThenTakesFirstAndLastName() {
        lateinit var settings: KisSettings
        var done = false
        ui.setContent {
            settings = rememberKisSettings()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(settings) { KisLaunch(onDone = { done = true }) }
            }
        }
        ui.onNodeWithTag("launch_line_1").assertTextEquals("עיצוב חדש.")
        ui.onNodeWithTag("launch_line_2").assertTextEquals("התחלה חדשה.")
        shot("launch")
        ui.onNodeWithTag("launch_next").performClick()
        ui.onNodeWithTag("launch_done").assertIsNotEnabled()
        ui.onNodeWithTag("launch_first").performTextReplacement("שולי")
        ui.onNodeWithTag("launch_last").performTextReplacement("כהן")
        shot("launch-name")
        ui.onNodeWithTag("launch_done").performClick()
        assertTrue(done)
        assertEquals("שולי", settings.name)
        assertEquals("כהן", settings.lastName)
    }

    @Test fun settingsSectionsAndThemeChoice() {
        val vm = model()
        lateinit var settings: KisSettings
        ui.setContent {
            settings = rememberKisSettings()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(settings) { KisSettingsPage(vm, categories, onClose = {}, onReplayTour = {}) }
            }
        }
        listOf("עבודה", "מראה ותחושה", "התראות", "עזרה וכלים", "גיבוי ונתונים", "משוב ותקלות", "פרופיל").forEach {
            ui.onNodeWithTag("settings_section_$it").performScrollTo().assertExists()
        }
        ui.onNodeWithTag("theme_LIGHT").performScrollTo().performClick()
        assertEquals(ThemeChoice.LIGHT, settings.theme)
        shot("settings-light")
        ui.onNodeWithTag("theme_DARK").performScrollTo().performClick()
        assertEquals(ThemeChoice.DARK, settings.theme)
        shot("settings-dark")
        ui.onNodeWithTag("haptics_switch").performScrollTo().performClick()
        assertFalse(settings.haptics)
        // Notification switches (device-only), and the profile at the bottom: first and last name, saved as typed.
        ui.onNodeWithTag("quiet_switch").performScrollTo().assertExists()
        ui.onNodeWithTag("notify_monthly_switch").performScrollTo().performClick()
        assertTrue(vm.planSettings.notifyMonthly)
        ui.onNodeWithTag("notify_monthly_switch").performClick()
        assertFalse(vm.planSettings.notifyMonthly)
        ui.onNodeWithTag("profile_name").performScrollTo().performTextReplacement("שולי")
        ui.onNodeWithTag("profile_last_name").performScrollTo().performTextReplacement("כהן")
        assertEquals("שולי", settings.name)
        assertEquals("כהן", settings.lastName)
        // Weekly plans live here (device-only settings).
        ui.onNodeWithTag("series_row").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        ui.onNodeWithTag("auto_confirm_switch").performClick()
        assertFalse(vm.planSettings.autoConfirm)
        ui.onNodeWithTag("auto_confirm_switch").performClick()
        assertTrue(vm.planSettings.autoConfirm)
    }
}
