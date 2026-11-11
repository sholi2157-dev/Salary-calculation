package com.example

import com.example.data.*
import com.example.ui.kis.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 2.4 task 07 (idea C): "N משמרות לא שולמו מעל 30 יום · ₪X" per workplace and currency. Display only: the amount is the plain
 * sum of the saved per-shift amounts, unknown is counted apart (never ₪0), ₪ and $ apart, nothing stored. The same synthetic
 * fixture (22 shifts / 8 workplaces) is checked on web (web-tests/unpaid-aging.test.cjs, browser-tests/unpaid-aging.cjs).
 * The Home rows and the tap into History are checked in KisUiTest (Compose needs the Debug test activity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkUnpaidAgingTest {
    private val f = AgingFixture()
    private fun cents(v: Double) = Math.round(v * 100)

    @Test fun theDatasetIsTheRealisticOne() {
        val e = f.entries()
        assertEquals(22, e.size); assertEquals(8, e.map { it.category }.toSet().size); assertEquals(setOf("₪", "$"), e.map { it.currency }.toSet())
        assertTrue(e.any { !it.payKnown } && e.any { it.isGroupShift } && e.any { it.kind == WorkEntry.KIND_JOB } && e.any { it.isPaid })
    }

    @Test fun linesMatchTheSharedFixture() {
        val got = WorkPay.unpaidAging(f.entries(), f.now, f.zone)
        assertEquals(f.expected().size, got.size)
        got.zip(f.expected()).forEach { (g, x) ->
            assertEquals(x.getString("category"), g.category); assertEquals(x.getString("currency"), g.currency)
            assertEquals(x.getInt("count"), g.count); assertEquals(x.getInt("unknown"), g.unknown)
            assertEquals(if (x.isNull("amount")) null else cents(x.getDouble("amount")), g.amount?.let(::cents))
            assertEquals(x.getString("from"), f.dayOf(g.from)); assertEquals(x.getString("to"), f.dayOf(g.to))
            assertEquals(x.getString("line"), WorkPay.agingLine(g, KisFormat::money))
        }
    }

    @Test fun eachAmountIsTheSumOfTheSavedAmountsOfExactlyTheOldUnpaidShifts() {
        val list = f.entries(); val got = WorkPay.unpaidAging(list, f.now, f.zone)
        val cutoff = LocalDate.parse(f.doc.getJSONObject("now").getString("day")).minusDays(30).atStartOfDay(f.zone).toInstant().toEpochMilli()
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
        val n = f.doc.getJSONObject("nothingOld").getJSONObject("now")
        assertEquals(emptyList<WorkPay.Aging>(), WorkPay.unpaidAging(f.entries(), f.at(n.getString("day"), n.getString("time")), f.zone))
        assertEquals(emptyList<WorkPay.Aging>(), WorkPay.unpaidAging(f.entries().map { it.copy(isPaid = true) }, f.now, f.zone))
    }

    @Test fun displayOnlyTheRowsAreNotChanged() {
        val list = f.entries(); val before = list.toList()
        WorkPay.unpaidAging(list, f.now, f.zone)
        assertEquals(before, list)
    }
}

/** The shared synthetic fixture (also web-tests/fixtures/unpaid-aging.json) as WorkEntry rows with ids 1..22 ("a01".."a22"). */
class AgingFixture {
    val doc = JSONObject(javaClass.classLoader!!.getResource("unpaid-aging.json")!!.readText())
    val zone: ZoneId = ZoneId.systemDefault()
    fun at(day: String, time: String, shiftDays: Long = 0) =
        LocalDate.parse(day).plusDays(shiftDays).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
    val categories = doc.getJSONArray("categories").let { a -> (0 until a.length()).map { WorkCategory(id = it + 1, name = a.getString(it), defaultRate = 50.0) } }
    val now = doc.getJSONObject("now").let { at(it.getString("day"), it.getString("time")) }
    /** Days to add so the fixture's "now" is today (for screens that use the real clock; every age stays the same). */
    fun shiftToToday() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(doc.getJSONObject("now").getString("day")), LocalDate.now(zone))
    fun expected(): List<JSONObject> = doc.getJSONArray("expected").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    fun entries(shiftDays: Long = 0): List<WorkEntry> {
        val a = doc.getJSONArray("entries")
        return (0 until a.length()).map { i -> val o = a.getJSONObject(i); val team = o.getBoolean("isGroupShift")
            WorkEntry(id = i + 1, category = o.getString("category"), date = at(o.getString("day"), o.getString("time"), shiftDays), isTimeRange = false,
                hours = o.getDouble("hours"), hourlyRate = o.getDouble("hourlyRate"), totalEarnings = o.getDouble("totalEarnings"), payKnown = o.getBoolean("payKnown"),
                isPaid = o.getBoolean("isPaid"), currency = o.getString("currency"), kind = o.getString("kind"), isGroupShift = team,
                employerRate = if (team) 60.0 else null, workerRate = if (team) 40.0 else null, groupWorkersJson = o.getString("groupWorkersJson"), createdAt = 1_000L + i)
        }
    }
}
