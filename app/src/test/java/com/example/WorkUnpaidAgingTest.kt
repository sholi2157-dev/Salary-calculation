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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 2.4 task 07 (idea C): "N משמרות לא שולמו מעל 30 יום · ₪X" per workplace and currency. Display only: the amount is the plain
 * sum of the saved per-shift amounts, unknown is counted apart (never ₪0), ₪ and $ apart, nothing stored. The same synthetic
 * fixture (22 shifts / 8 workplaces) is checked on web (web-tests/unpaid-aging.test.cjs, browser-tests/unpaid-aging.cjs).
 * Screenshots: /tmp/kis-aging-*.png (360dp, light and dark).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class WorkUnpaidAgingTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db by lazy { WorkAccountScope(null).database(app) }
    private val dao get() = db.workDao()
    @org.junit.Before fun clean() { runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() } }

    private val doc = JSONObject(javaClass.classLoader!!.getResource("unpaid-aging.json")!!.readText())
    private val zone = ZoneId.systemDefault()
    private fun at(day: String, time: String, shiftDays: Long = 0) =
        LocalDate.parse(day).plusDays(shiftDays).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
    private val categoryNames = (0 until doc.getJSONArray("categories").length()).map { doc.getJSONArray("categories").getString(it) }
    private val categories = categoryNames.mapIndexed { i, n -> WorkCategory(id = i + 1, name = n, defaultRate = 50.0) }
    private val now = doc.getJSONObject("now").let { at(it.getString("day"), it.getString("time")) }

    /** Fixture rows; ids 1..22 in order (fixture "a01".."a22"); [shiftDays] moves every day (for screens that use the real clock). */
    private fun entries(shiftDays: Long = 0): List<WorkEntry> {
        val a = doc.getJSONArray("entries")
        return (0 until a.length()).map { i -> val o = a.getJSONObject(i)
            WorkEntry(id = i + 1, category = o.getString("category"), date = at(o.getString("day"), o.getString("time"), shiftDays), isTimeRange = false,
                hours = o.getDouble("hours"), hourlyRate = o.getDouble("hourlyRate"), totalEarnings = o.getDouble("totalEarnings"), payKnown = o.getBoolean("payKnown"),
                isPaid = o.getBoolean("isPaid"), currency = o.getString("currency"), kind = o.getString("kind"), isGroupShift = o.getBoolean("isGroupShift"),
                employerRate = if (o.getBoolean("isGroupShift")) 60.0 else null, workerRate = if (o.getBoolean("isGroupShift")) 40.0 else null,
                groupWorkersJson = o.getString("groupWorkersJson"), createdAt = 1_000L + i)
        }
    }
    private fun expected(): List<JSONObject> = doc.getJSONArray("expected").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun cents(v: Double) = Math.round(v * 100)

    @Test fun theDatasetIsTheRealisticOne() {
        val e = entries()
        assertEquals(22, e.size); assertEquals(8, e.map { it.category }.toSet().size); assertEquals(setOf("₪", "$"), e.map { it.currency }.toSet())
        assertTrue(e.any { !it.payKnown } && e.any { it.isGroupShift } && e.any { it.kind == WorkEntry.KIND_JOB } && e.any { it.isPaid })
    }

    @Test fun linesMatchTheSharedFixture() {
        val got = WorkPay.unpaidAging(entries(), now, zone)
        assertEquals(expected().size, got.size)
        got.zip(expected()).forEach { (g, x) ->
            assertEquals(x.getString("category"), g.category); assertEquals(x.getString("currency"), g.currency)
            assertEquals(x.getInt("count"), g.count); assertEquals(x.getInt("unknown"), g.unknown)
            assertEquals(if (x.isNull("amount")) null else cents(x.getDouble("amount")), g.amount?.let(::cents))
            assertEquals(x.getString("from"), dayOf(g.from)); assertEquals(x.getString("to"), dayOf(g.to))
            assertEquals(x.getString("line"), WorkPay.agingLine(g, KisFormat::money))
        }
    }

    @Test fun eachAmountIsTheSumOfTheSavedAmountsOfExactlyTheOldUnpaidShifts() {
        val list = entries(); val got = WorkPay.unpaidAging(list, now, zone)
        val cutoff = LocalDate.parse(doc.getJSONObject("now").getString("day")).minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val old = list.filter { !it.isPaid && it.date < cutoff }
        assertEquals(old.map { it.id }.sorted(), got.flatMap { it.ids }.sorted())
        assertTrue(3 in got.flatMap { it.ids }); assertFalse(4 in got.flatMap { it.ids }) // 31 days counted, 30 not
        got.forEach { g ->
            val mine = old.filter { it.category == g.category && it.currency == g.currency }; val known = mine.filter { it.payKnown }
            assertEquals(if (known.isEmpty()) null else known.sumOf { cents(it.totalEarnings) }, g.amount?.let(::cents))
            assertEquals(mine.size - known.size, g.unknown)
            assertFalse(WorkPay.agingLine(g, KisFormat::money).contains(if (g.currency == "₪") "$" else "₪"))
        }
    }

    @Test fun nothingWhenNoUnpaidShiftIsOlderThan30Days() {
        val n = doc.getJSONObject("nothingOld").getJSONObject("now")
        assertEquals(emptyList<WorkPay.Aging>(), WorkPay.unpaidAging(entries(), at(n.getString("day"), n.getString("time")), zone))
        assertEquals(emptyList<WorkPay.Aging>(), WorkPay.unpaidAging(entries().map { it.copy(isPaid = true) }, now, zone))
    }

    @Test fun homeShowsTheLinesAndATapOpensHistoryFilteredToThem() = homeFlow(false)
    @Test fun homeShowsTheLinesDark() = homeFlow(true)

    private fun homeFlow(dark: Boolean) {
        // Home uses the real clock: move the fixture so its "now" is today (every age stays the same).
        val shift = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(doc.getJSONObject("now").getString("day")), LocalDate.now(zone))
        runBlocking { entries(shift).forEach { dao.insertEntry(it) } }
        val stored = runBlocking { dao.getEntriesList() }
        val vm = WorkViewModel(app, WorkRepository(dao))
        val state = HistoryUi()
        var page by mutableStateOf(0)
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = dark) { Box(Modifier.fillMaxSize().background(Kis.colors.bg)) {
                    if (page == 0) KisHome(stored, categories, null, 80.dp, {}, {}, {}, {}, {}, {}, {}, {}, onAging = { g ->
                        state.showFilter = HistoryFilter(period = Period.RANGE, from = EntryDraft.startOfDay(g.from), to = EntryDraft.startOfDay(g.to),
                            pay = PayFilter.UNPAID, category = g.category, currency = g.currency); page = 1 })
                    else KisHistory(state, true, stored, categories, vm, 80.dp, onOpen = {}, onDeleted = {}, onMessage = {})
                } }
            }
        }
        ui.onNodeWithTag("aging_lines").performScrollTo()
        val lines = ui.onAllNodesWithTag("aging_line").fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") }
        assertEquals(expected().map { it.getString("line") }, lines)
        expected().forEach { ui.onNodeWithTag("aging_${it.getString("category")}_${it.getString("currency")}").assertExists() }
        ui.onNodeWithTag("aging_מסעדה_₪").performScrollTo()
        ui.onRoot().captureRoboImage(filePath = "/tmp/kis-aging-home-360-${if (dark) "dark" else "light"}.png")
        if (dark) return
        ui.onNodeWithTag("aging_מסעדה_₪").performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("history_total").assertTextContains("₪775.50", substring = true)
        for (id in 1..3) ui.onNodeWithTag("history_scroll_container").performScrollToNode(hasTestTag("work_entry_card_$id"))
        ui.onAllNodesWithTag("work_entry_card_4").assertCountEquals(0)
        ui.onAllNodesWithTag("work_entry_card_5").assertCountEquals(0)
        ui.onNodeWithTag("history_scroll_container").performScrollToIndex(0)
        ui.onRoot().captureRoboImage(filePath = "/tmp/kis-aging-history-360-light.png")
        // Display only: nothing stored changed.
        assertEquals(stored, runBlocking { dao.getEntriesList() })
    }
}
