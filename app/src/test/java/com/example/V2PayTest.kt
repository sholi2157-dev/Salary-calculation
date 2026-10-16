package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.kis.EntryDraft
import com.example.ui.kis.EntryMode
import com.example.ui.kis.KisShare
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Unknown pay, bonus and fixed-price jobs everywhere money is summed, exported, imported or edited. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2PayTest {
    private fun shift(total: Double, currency: String = "₪", known: Boolean = true, hours: Double = 2.0, rate: Double = 50.0, bonus: Double = 0.0, paid: Boolean = false) =
        WorkEntry(category = "מסעדה", date = 1_760_000_000_000L, isTimeRange = false, hours = hours, hourlyRate = if (known) rate else 0.0,
            totalEarnings = if (known) total else 0.0, payKnown = known, bonus = bonus, currency = currency, isPaid = paid)

    @Test fun unknownPayIsNeverSummedOrShownAsZero() {
        val rows = listOf(shift(100.0), shift(0.0, known = false), shift(20.0, "$"), shift(0.0, "$", known = false))
        assertEquals(mapOf("₪" to 100.0, "$" to 20.0), WorkMoney.totals(rows))
        // A currency with only unknown pay does not appear as ₪0.
        assertEquals(emptyMap<String, Double>(), WorkMoney.totals(listOf(shift(0.0, known = false))))
        val report = WorkMoney.report(rows)
        assertTrue(report, report.contains("2 משמרות עוד בלי תעריף"))
        val share = KisShare.text(rows)
        assertTrue(share, share.contains("תעריף חסר"))
        assertTrue(share, share.contains("2 בלי תעריף"))
        assertFalse(share, share.contains("₪0"))
    }

    @Test fun editsKeepStoredAmountsUnlessPayInputsChange() {
        val imported = shift(299.97, hours = 5.5, rate = 52.0)
        assertEquals(299.97, WorkEntryEdits.apply(imported, imported.copy(notes = "רק הערה")).totalEarnings, 0.0)
        assertEquals(330.0, WorkEntryEdits.apply(imported, imported.copy(hourlyRate = 60.0)).totalEarnings, 0.0)
        assertEquals(336.0, WorkEntryEdits.apply(imported, imported.copy(hourlyRate = 60.0, bonus = 6.0)).totalEarnings, 0.0)
        val unknown = WorkEntryEdits.apply(imported, imported.copy(payKnown = false))
        assertEquals(0.0, unknown.totalEarnings, 0.0); assertEquals(0.0, unknown.hourlyRate, 0.0)
        val job = WorkEntryEdits.apply(imported, imported.copy(kind = WorkEntry.KIND_JOB, totalEarnings = 150.0))
        assertEquals(150.0, job.totalEarnings, 0.0); assertEquals(0.0, job.hourlyRate, 0.0)
    }

    @Test fun draftNotesOnlyEditKeepsEveryStoredValue() {
        val e = WorkEntry(id = 7, category = "מסעדה", date = 1_760_013_337_000L, isTimeRange = true, startTime = "17:00", endTime = "22:30",
            hours = 5.33333, hourlyRate = 33.333, totalEarnings = 177.77, bonus = 0.0, createdAt = 5)
        val edited = EntryDraft.from(e).copy(notes = "חדש").toEntry(e)
        assertEquals(e.copy(notes = "חדש"), edited)
        // Same for an hours-only entry and a group shift.
        val hours = e.copy(isTimeRange = false, startTime = null, endTime = null)
        assertEquals(hours.copy(notes = "x"), EntryDraft.from(hours).copy(notes = "x").toEntry(hours))
        val group = e.copy(isGroupShift = true, employerRate = 65.0, workerRate = 30.0, groupWorkersJson = """[{"name":"נועה","hours":3.33,"isPaid":true,"workerRate":31.25}]""")
        assertEquals(group.copy(notes = "y"), EntryDraft.from(group).copy(notes = "y").toEntry(group))
    }

    @Test fun draftBuildsJobsUnknownRatesAndBonuses() {
        val job = EntryDraft(mode = EntryMode.JOB, category = "הובלות", amount = "250", hours = "", notes = "פירוק ארון", day = EntryDraft.startOfDay(1_760_000_000_000L)).toEntry()
        assertEquals(WorkEntry.KIND_JOB, job.kind); assertTrue(job.payKnown); assertEquals(250.0, job.totalEarnings, 0.0); assertEquals(0.0, job.hours, 0.0)
        val unknownJob = EntryDraft(mode = EntryMode.JOB, category = "הובלות", amountUnknown = true).toEntry()
        assertFalse(unknownJob.payKnown); assertEquals(0.0, unknownJob.totalEarnings, 0.0)
        val bonus = EntryDraft(mode = EntryMode.CLOCK, category = "מסעדה", start = "18:00", end = "01:00", rate = "50", bonus = "40").toEntry()
        assertEquals(7.0, bonus.hours, 1e-9); assertEquals(390.0, bonus.totalEarnings, 0.0); assertEquals(40.0, bonus.bonus, 0.0)
        val missing = EntryDraft(mode = EntryMode.CLOCK, category = "מסעדה", start = "18:00", end = "01:00", rateUnknown = true, bonus = "40").toEntry()
        assertFalse(missing.payKnown); assertEquals(0.0, missing.totalEarnings, 0.0); assertEquals(40.0, missing.bonus, 0.0)
        assertNull(EntryDraft(mode = EntryMode.CLOCK, category = "מסעדה", start = "18:00", end = "01:00", rateUnknown = true).check().total)
    }

    @Test fun backupStaysVersion2UntilA2Feature() {
        val legacy = listOf(shift(100.0, paid = true), shift(87.25, "$"))
        val v2 = WorkBackup.encode(emptyList(), legacy, emptyList())
        assertEquals(2, JSONObject(v2).getInt("formatVersion"))
        assertFalse(v2.contains("payKnown"))
        val modern = legacy + shift(0.0, known = false, bonus = 10.0) + shift(180.0).copy(kind = WorkEntry.KIND_JOB, hourlyRate = 0.0)
        val v3 = WorkBackup.encode(emptyList(), modern, emptyList())
        assertEquals(3, JSONObject(v3).getInt("formatVersion"))
        val back = WorkBackup.decode(v3).entries
        assertEquals(modern.map { it.copy(id = 0) }, back)
        // A worker bonus alone also needs version 3, so an older app never silently drops it.
        val crew = shift(100.0).copy(isGroupShift = true, groupWorkersJson = """[{"name":"נועה","hours":2,"isPaid":false,"bonus":15}]""")
        assertTrue(WorkBackup.needsVersion3(listOf(crew)))
        assertEquals(15.0, WorkPay.workers(WorkBackup.decode(WorkBackup.encode(emptyList(), listOf(crew), emptyList())).entries.single()).single().bonus, 0.0)
    }

    @Test fun excelRoundTripKeepsUnknownRatesJobsAndBonuses() {
        val header = listOf("קטגוריה", "תאריך", "שעות", "תעריף שעתי", "שכר לתשלום", "סטטוס", "הערות", "מטבע", "סוג", "בונוס").joinToString("\t")
        val text = header + "\n" + listOf(
            listOf("מסעדה", "15/10/2026", "7.00", "", "", "ממתין", "", "₪", "משמרת", ""),
            listOf("שיעורים", "14/10/2026", "2.50", "40.00", "120.00", "שולם", "", "₪", "משמרת", "20.00"),
            listOf("הובלות", "13/10/2026", "3.00", "", "180.00", "ממתין", "הובלת מקרר", "$", "קבלנות", ""),
        ).joinToString("\n") { it.joinToString("\t") }
        val rows = WorkTableImport.decode(text).entries
        assertFalse(rows[0].payKnown); assertEquals(0.0, rows[0].totalEarnings, 0.0)
        assertTrue(rows[1].payKnown); assertEquals(120.0, rows[1].totalEarnings, 0.0); assertEquals(20.0, rows[1].bonus, 0.0)
        assertEquals(WorkEntry.KIND_JOB, rows[2].kind); assertEquals(180.0, rows[2].totalEarnings, 0.0); assertEquals("$", rows[2].currency)
    }

    @Test fun groupPayFollowsTheShiftRateAndPassesWorkerBonusThrough() {
        val e = shift(100.0, hours = 2.0, rate = 50.0).copy(isGroupShift = true, employerRate = 65.0, workerRate = 30.0,
            groupWorkersJson = """[{"name":"נועה","hours":3,"isPaid":false,"bonus":10},{"name":"דן","hours":2,"isPaid":true,"workerRate":35}]""")
        val ws = WorkPay.workers(e)
        assertEquals(100.0, WorkPay.workerPay(ws[0], e)!!, 0.0) // 3 × 30 + 10
        assertEquals(70.0, WorkPay.workerPay(ws[1], e)!!, 0.0)
        val g = WorkPay.group(e)!!
        assertEquals(100.0 + (3 * 65 + 10) + 2 * 65, g.employerTotal, 0.001)
        assertEquals(3 * 35.0 + 2 * 30.0, g.profit, 0.001)
        // While my own rate is missing, totals are not guessed.
        assertNull(WorkPay.group(e.copy(payKnown = false, totalEarnings = 0.0)))
        assertNull(WorkPay.workerPay(ws[0], e.copy(payKnown = false, workerRate = null, hourlyRate = 0.0)))
    }

    @Test fun bulkRateCompletionWritesOnlyMissingShiftRates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WorkDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.workDao()
            val known = dao.insertEntry(shift(300.0, hours = 6.0, rate = 50.0)).toInt()
            val missing = dao.insertEntry(shift(0.0, known = false, hours = 7.0)).toInt()
            val missingBonus = dao.insertEntry(shift(0.0, known = false, hours = 2.5, bonus = 20.0)).toInt()
            val job = dao.insertEntry(shift(0.0, known = false).copy(kind = WorkEntry.KIND_JOB, hours = 0.0)).toInt()
            assertEquals(2, dao.completeMissingRates(listOf(known, missing, missingBonus, job), 40.0))
            assertEquals(300.0, dao.getEntryById(known)!!.totalEarnings, 0.0)
            assertEquals(280.0, dao.getEntryById(missing)!!.totalEarnings, 0.0)
            assertEquals(120.0, dao.getEntryById(missingBonus)!!.totalEarnings, 0.0)
            assertFalse(dao.getEntryById(job)!!.payKnown)
            // Running it again changes nothing: completed rows are no longer missing.
            assertEquals(0, dao.completeMissingRates(listOf(known, missing, missingBonus, job), 99.0))
            assertEquals(280.0, dao.getEntryById(missing)!!.totalEarnings, 0.0)
        } finally { db.close() }
    }
}
