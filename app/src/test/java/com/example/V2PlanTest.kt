package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SeriesException
import com.example.data.ShiftSeries
import com.example.data.WorkBackup
import com.example.data.WorkDatabase
import com.example.data.WorkEntry
import com.example.domain.V2Plan
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/** Weekly plans: shared expected results (v2-plan.json) plus the database guarantees (never twice, undo, backup). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class V2PlanTest {
    private val doc by lazy { JSONObject(javaClass.classLoader!!.getResourceAsStream("v2-plan.json").bufferedReader().readText()) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.rate(key: String = "rate"): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun local(text: String): Long { val (d, t) = text.split(" "); return V2Plan.startMillis(d, t) }

    private fun series(o: JSONObject) = ShiftSeries(o.getString("id"), o.getString("category"),
        V2Plan.mask((0 until o.getJSONArray("days").length()).map { o.getJSONArray("days").getInt(it) }.toSet()),
        o.getString("start"), o.getString("end"), o.rate(), o.getString("currency"), o.getString("from"), o.getString("until"), 0L)
    private fun exception(o: JSONObject) = SeriesException(o.getString("seriesId"), o.getString("date"), o.optBoolean("cancelled"), o.optBoolean("hold"),
        o.optString("start").ifEmpty { null }, o.optString("end").ifEmpty { null }, o.optBoolean("rateOverridden"), o.rate())
    private fun occurrencesOf(c: JSONObject): List<V2Plan.Occurrence> = V2Plan.occurrences(c.getJSONArray("series").objects().map(::series),
        c.getJSONArray("exceptions").objects().map(::exception), (0 until c.getJSONArray("confirmed").length()).map { c.getJSONArray("confirmed").getString(it) }.toSet(),
        c.getJSONArray("range").getString(0), c.getJSONArray("range").getString(1), local(c.getString("now")))

    @Test fun occurrencesMatchTheContract() {
        doc.getJSONArray("occurrences").objects().forEach { c ->
            val got = occurrencesOf(c)
            val want = c.getJSONArray("expected").objects()
            assertEquals(c.getString("name"), want.size, got.size)
            want.zip(got).forEach { (w, g) ->
                assertEquals(c.getString("name"), w.getString("key"), g.key)
                assertEquals(w.getString("start"), g.start); assertEquals(w.getString("end"), g.end)
                assertEquals(w.rate(), g.rate); assertEquals(w.getString("status"), g.status.name); assertEquals(w.getBoolean("edited"), g.edited)
            }
        }
        doc.getJSONArray("toConfirm").objects().forEach { c ->
            val want = (0 until c.getJSONArray("expected").length()).map { c.getJSONArray("expected").getString(it) }
            assertEquals(c.getString("name"), want, V2Plan.toConfirm(occurrencesOf(c)).map { it.key })
        }
    }

    @Test fun recordedShiftsMatchTheContract() {
        doc.getJSONArray("toEntry").objects().forEach { c ->
            val o = c.getJSONObject("occurrence")
            val e = V2Plan.toEntry(V2Plan.Occurrence(o.getString("key"), o.getString("seriesId"), o.getString("category"), o.getString("date"), o.getString("start"),
                o.getString("end"), o.rate(), o.getString("currency"), edited = false, hold = false, status = V2Plan.Status.AWAITING), c.getBoolean("auto"))
            val w = c.getJSONObject("expected")
            assertEquals(w.getDouble("hours"), e.hours, 1e-9); assertEquals(w.getDouble("hourlyRate"), e.hourlyRate, 0.0)
            assertEquals(w.getDouble("totalEarnings"), e.totalEarnings, 0.0); assertEquals(w.getBoolean("payKnown"), e.payKnown)
            assertEquals(w.getString("seriesKey"), e.seriesKey); assertEquals(w.getBoolean("autoConfirmed"), e.autoConfirmed)
            assertEquals(w.getString("startTime"), e.startTime); assertEquals(w.getString("endTime"), e.endTime)
            assertEquals(w.getString("currency"), e.currency); assertEquals(local(w.getString("dateLocal")), e.date)
        }
    }

    @Test fun previewSplitAndRateMatchTheContract() {
        doc.getJSONArray("preview").objects().forEach { c ->
            val saved = c.getJSONArray("saved").objects().map { WorkEntry(category = "x", date = local(it.getString("dateLocal")), isTimeRange = true,
                startTime = it.getString("start"), endTime = it.getString("end"), hours = 1.0, hourlyRate = 1.0, totalEarnings = 1.0) }
            val p = V2Plan.preview((0 until c.getJSONArray("days").length()).map { c.getJSONArray("days").getInt(it) }.toSet(), c.getString("from"), c.getString("until"),
                c.getString("start"), c.getString("end"), saved, emptyList())
            val w = c.getJSONObject("expected")
            assertEquals((0 until w.getJSONArray("dates").length()).map { w.getJSONArray("dates").getString(it) }, p.dates)
            assertEquals((0 until w.getJSONArray("clashes").length()).map { w.getJSONArray("clashes").getString(it) }, p.clashes)
        }
        doc.getJSONArray("split").objects().forEach { c ->
            val r = V2Plan.split(series(c.getJSONObject("series")), c.getJSONArray("exceptions").objects().map(::exception), c.getString("date"), c.getString("newId"),
                start = c.getString("start"), end = c.getString("end"), now = 0L)
            val w = c.getJSONObject("expected")
            assertEquals(c.getString("name"), if (w.isNull("oldUntil")) null else w.getString("oldUntil"), r.old?.untilDate)
            assertEquals(w.getString("newFrom"), r.new.fromDate); assertEquals(w.getString("newStart"), r.new.startTime); assertEquals(w.getString("newEnd"), r.new.endTime)
            assertEquals(w.getJSONArray("moved").objects().map { Triple(it.getString("seriesId"), it.getString("date"), it.getBoolean("cancelled")) },
                r.moved.map { Triple(it.seriesId, it.date, it.cancelled) })
            assertEquals((0 until w.getJSONArray("dropped").length()).map { w.getJSONArray("dropped").getString(it) }, r.dropped.map { it.date })
        }
        doc.getJSONArray("applyRate").objects().forEach { c ->
            val s = series(c.getJSONObject("series"))
            val waiting = (0 until c.getJSONArray("waiting").length()).map { d -> c.getJSONArray("waiting").getString(d) }.map { d ->
                V2Plan.Occurrence(V2Plan.key(s.id, d), s.id, s.category, d, s.startTime, s.endTime, s.hourlyRate, s.currency, false, false, V2Plan.Status.AWAITING) }
            val (updated, changes) = V2Plan.applyRateFromToday(s, c.getJSONArray("exceptions").objects().map(::exception), c.rate(), c.getString("today"), waiting)
            val w = c.getJSONObject("expected")
            assertEquals(w.rate(), updated.hourlyRate)
            assertEquals(w.getJSONArray("changes").objects().map { Triple(it.getString("date"), it.getBoolean("rateOverridden"), it.rate()) },
                changes.map { Triple(it.date, it.rateOverridden, it.hourlyRate) })
        }
    }

    @Test fun overnightEndIsWallClockAcrossDaylightSaving() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"))
            for (date in listOf("2026-03-26", "2026-10-24")) {
                val end = Calendar.getInstance().apply { timeInMillis = V2Plan.endMillis(date, "22:00", "06:00") }
                assertEquals(6, end.get(Calendar.HOUR_OF_DAY)); assertEquals(0, end.get(Calendar.MINUTE))
                assertEquals(V2Plan.dateOf(V2Plan.startMillis(date, "12:00") + 86_400_000L), V2Plan.dateOf(end.timeInMillis))
            }
        } finally { TimeZone.setDefault(saved) }
    }

    private fun db(): WorkDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WorkDatabase::class.java).allowMainThreadQueries().build()

    @Test fun recordingTwiceAtOnceKeepsOneRowAndUndoHoldsTheDay() = runBlocking {
        val database = db()
        try {
            val dao = database.workDao()
            val s = ShiftSeries("P", "מסעדה", V2Plan.mask(setOf(0, 1, 2, 3, 4, 5, 6)), "09:00", "10:00", 50.0, "₪", "2026-10-01", "2026-10-03", 0L)
            dao.saveSeries(s, emptyList())
            val now = V2Plan.startMillis("2026-10-10", "00:00")
            val occ = V2Plan.toConfirm(V2Plan.occurrences(dao.getSeries(), dao.getExceptions(), dao.confirmedKeys().toSet(), "2026-10-01", "2026-10-10", now))
            // The background job and the app record the same finished days at the same moment.
            val first = dao.confirmOccurrences(occ.map { V2Plan.toEntry(it, true) })
            val second = dao.confirmOccurrences(occ.map { V2Plan.toEntry(it, true) })
            assertEquals(3, first.size); assertTrue(second.isEmpty()); assertEquals(3, dao.getEntriesList().size)
            assertTrue(dao.getEntriesList().all { it.autoConfirmed && it.payKnown && it.totalEarnings == 50.0 })
            // Undo: rows go, the days wait for a manual "עבדתי" and are never recorded by themselves again.
            dao.undoAutoConfirm(first.map { it.toInt() })
            assertEquals(0, dao.getEntriesList().size)
            val again = V2Plan.occurrences(dao.getSeries(), dao.getExceptions(), dao.confirmedKeys().toSet(), "2026-10-01", "2026-10-10", now)
            assertEquals(3, again.size); assertTrue(V2Plan.toConfirm(again).isEmpty())
        } finally { database.close() }
    }

    @Test fun backupCarriesPlansAndStaysVersion2WithoutThem() = runBlocking {
        val database = db()
        try {
            val dao = database.workDao()
            dao.insertCategory(com.example.data.WorkCategory(name = "מסעדה", defaultRate = 50.0))
            dao.insertEntry(WorkEntry(category = "מסעדה", date = 1_760_000_000_000L, isTimeRange = false, hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0))
            assertEquals(2, JSONObject(dao.exportSnapshot()).getInt("formatVersion"))
            assertFalse(JSONObject(dao.exportSnapshot()).has("series"))
            dao.saveSeries(ShiftSeries("P", "מסעדה", V2Plan.mask(setOf(1)), "09:00", "17:00", null, "$", "2026-10-01", "2026-12-31", 5L), listOf("2026-10-05"))
            val text = dao.exportSnapshot()
            assertEquals(3, JSONObject(text).getInt("formatVersion"))
            val back = WorkBackup.decode(text)
            assertEquals(dao.getSeries(), back.series); assertEquals(dao.getExceptions(), back.exceptions)
            // Importing the same backup again adds nothing.
            assertEquals(0, dao.importBackup(back)); assertEquals(1, dao.getSeries().size); assertEquals(1, dao.getExceptions().size)
            // Deleting the workplace moves its plan to the remaining one; nothing is deleted.
            dao.insertCategory(com.example.data.WorkCategory(name = "אחר", defaultRate = 40.0))
            dao.removeCategorySafely(dao.getCategoryByName("מסעדה")!!)
            assertEquals("אחר", dao.getSeries().single().category)
        } finally { database.close() }
    }

    @Test fun splittingOnTheFirstDayReplacesThePlan() = runBlocking {
        val database = db()
        try {
            val dao = database.workDao()
            val s = ShiftSeries("P", "מסעדה", V2Plan.mask(setOf(1)), "09:00", "17:00", 50.0, "₪", "2026-10-05", "2026-10-31", 0L)
            dao.saveSeries(s, listOf("2026-10-12"))
            dao.applySplit(V2Plan.split(s, dao.getExceptions(), "2026-10-05", "N", start = "08:00", end = "16:00", now = 0L))
            val left = dao.getSeries().single()
            assertEquals("N", left.id); assertEquals("08:00", left.startTime)
            assertEquals(listOf(SeriesException("N", "2026-10-12", cancelled = true)), dao.getExceptions())
            assertNull(dao.getSeries().firstOrNull { it.id == "P" })
        } finally { database.close() }
    }

    @Test fun recordedOrDeletedDaysAreNeverRecordedAgain() = runBlocking {
        val database = db()
        try {
            val dao = database.workDao()
            val s = ShiftSeries("P", "מסעדה", V2Plan.mask(setOf(1)), "09:00", "17:00", 50.0, "₪", "2026-10-05", "2026-10-31", 0L)
            dao.saveSeries(s, emptyList())
            val now = V2Plan.startMillis("2026-10-20", "00:00")
            fun open() = V2Plan.occurrences(runBlocking { dao.getSeries() }, runBlocking { dao.getExceptions() }, runBlocking { dao.confirmedKeys() }.toSet(), "2026-10-01", "2026-10-31", now)
            // Monday 12th was recorded ahead with "עבדתי"; new hours from the 5th on must not bring it back under the new plan.
            dao.confirmOccurrences(listOf(V2Plan.toEntry(open().first { it.date == "2026-10-12" }, auto = false)))
            dao.applySplit(V2Plan.split(s, dao.getExceptions(), "2026-10-05", "N", start = "08:00", end = "16:00", now = 0L, recorded = dao.confirmedKeys().toSet()))
            assertTrue(open().none { it.date == "2026-10-12" })
            // A recorded day the user deletes stays skipped; undoing the delete clears that again.
            val five = dao.confirmOccurrences(listOf(V2Plan.toEntry(open().first { it.date == "2026-10-05" }, auto = true))).single().toInt()
            val row = dao.getEntriesList().first { it.id == five }
            dao.deleteEntry(row); dao.markPlanDays(listOf(row.seriesKey!!), skipped = true)
            assertTrue(V2Plan.toConfirm(open()).none { it.date == "2026-10-05" })
            dao.markPlanDays(listOf(row.seriesKey!!), skipped = false)
            assertTrue(open().any { it.date == "2026-10-05" })
            // An undo never removes a shift the user edited meanwhile (it is no longer automatic).
            val again = dao.confirmOccurrences(listOf(V2Plan.toEntry(open().first { it.date == "2026-10-05" }, auto = true))).single().toInt()
            dao.updateEntry(dao.getEntriesList().first { it.id == again }.copy(autoConfirmed = false, notes = "ערכתי"))
            dao.undoAutoConfirm(listOf(again))
            assertEquals("ערכתי", dao.getEntriesList().single { it.id == again }.notes)
        } finally { database.close() }
    }
}
