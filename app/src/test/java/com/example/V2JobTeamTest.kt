package com.example

import com.example.data.WorkBackup
import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.data.WorkShareText
import com.example.ui.kis.EntryDraft
import com.example.ui.kis.EntryMode
import com.example.ui.kis.WorkerDraft
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A job (קבלנות) with a team: the amount the user types is their own pay; each worker gets a sum of their own on top of it,
 * and the employer owes all of it. Money passed through (car, materials) is owed by the employer but is nobody's income.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2JobTeamTest {
    private fun draft(vararg workers: WorkerDraft, amount: String = "75", same: Boolean = false) =
        EntryDraft(mode = EntryMode.JOB, category = "שיפוצים", amount = amount, workers = workers.toList(), teamSameRate = same)

    @Test fun theUsersAmountIsTheirsAndTheTeamIsPaidOnTop() {
        val d = draft(WorkerDraft("דני", amount = "50"), WorkerDraft("יוסי", amount = "50"))
        assertTrue(d.check().errors.toString(), d.check().canSave)
        assertEquals(75.0, d.check().total!!, 0.0)
        val e = d.toEntry()
        assertEquals(WorkEntry.KIND_JOB, e.kind); assertTrue(e.isGroupShift); assertTrue(WorkPay.isTeamJob(e))
        assertEquals(75.0, e.totalEarnings, 0.0)
        assertEquals(mapOf("₪" to 75.0), WorkPay.totals(listOf(e)))
        assertEquals(100.0, WorkPay.teamPay(e)!!, 0.0)
        val g = WorkPay.group(e)!!
        assertEquals(175.0, g.employerTotal, 0.0); assertEquals(75.0, g.myNet, 0.0)
        // Only a fixed sum is stored per worker, never hours.
        val json = JSONArray(e.groupWorkersJson).getJSONObject(0)
        assertTrue(json.has("amount")); assertFalse(json.has("workerRate")); assertEquals(0.0, json.getDouble("hours"), 0.0)
    }

    @Test fun oneAmountForAllFollowsTheUsersAmount() {
        val d = draft(WorkerDraft("דני"), WorkerDraft("יוסי", amount = "10"), same = true)
        val e = d.toEntry()
        assertEquals(listOf(75.0, 75.0), WorkPay.workers(e).map { WorkPay.workerPay(it, e)!! })
        assertEquals(225.0, WorkPay.group(e)!!.employerTotal, 0.0)
        // Changing the user's amount changes everyone's.
        val back = EntryDraft.from(e.copy(id = 3))
        assertTrue(back.teamSameRate)
        val raised = back.copy(amount = "80").toEntry(e.copy(id = 3))
        assertEquals(listOf(80.0, 80.0), WorkPay.workers(raised).map { WorkPay.workerPay(it, raised)!! })
        // An unknown amount has nothing to copy: each worker needs their own.
        assertTrue(d.copy(amount = "", amountUnknown = true).check().errors.contains("חסר כמה מקבל כל עובד"))
    }

    @Test fun theFirstWorkersAmountCarriesToTheOthersUntilChanged() {
        var d = draft(WorkerDraft("דני"))
        d = d.withJobWorkerAmount(0, "50").withJobWorker("יוסי").withJobWorker("משה")
        assertEquals(listOf("50", "50", "50"), d.workers.map { it.amount })
        d = d.withJobWorkerAmount(2, "40").withJobWorkerAmount(0, "55")
        assertEquals(listOf("55", "55", "40"), d.workers.map { it.amount })
        // With one amount for all, a new worker needs nothing typed.
        assertEquals("", draft(same = true).withJobWorker("דני").workers.single().amount)
    }

    @Test fun reopeningShowsTheUsersAmountAndUntouchedEditsKeepEverything() {
        val e = draft(WorkerDraft("דני", amount = "300"), amount = "700").toEntry().copy(id = 4, createdAt = 9)
        val back = EntryDraft.from(e)
        assertEquals(EntryMode.JOB, back.mode)
        assertEquals("700", back.amount); assertFalse(back.teamSameRate)
        assertEquals("300", back.workers.single().amount)
        assertEquals(e.copy(notes = "מטבח"), back.copy(notes = "מטבח").toEntry(e))
        // Changing a worker's sum never touches the user's pay.
        val raised = back.copy(workers = listOf(back.workers.single().copy(amount = "400"))).toEntry(e)
        assertEquals(700.0, raised.totalEarnings, 0.0); assertEquals(1100.0, WorkPay.group(raised)!!.employerTotal, 0.0)
    }

    @Test fun aWorkerSavedAsHoursTimesRateOpensWithThatPay() {
        val old = WorkEntry(category = "שיפוצים", date = 1_760_000_000_000L, isTimeRange = false, hours = 0.0, hourlyRate = 0.0, totalEarnings = 370.0,
            kind = WorkEntry.KIND_JOB, isGroupShift = true, groupWorkersJson = """[{"name":"יוסי","hours":5.5,"isPaid":false,"workerRate":60.0}]""", id = 7)
        val back = EntryDraft.from(old)
        assertEquals("330", back.workers.single().amount)
        // Opening and saving without a change keeps the stored team exactly.
        assertEquals(old.groupWorkersJson, back.toEntry(old).groupWorkersJson)
    }

    @Test fun everyPayMustBeKnownAndAnUnknownAmountStillRecordsTheTeam() {
        assertTrue(draft(WorkerDraft("דני")).check().errors.contains("חסר כמה מקבל כל עובד"))
        // A team may be paid more than the user (no "negative" share any more).
        assertTrue(draft(WorkerDraft("דני", amount = "1200")).check().canSave)
        val unknown = draft(WorkerDraft("דני", amount = "200"), amount = "").copy(amountUnknown = true).toEntry()
        assertFalse(unknown.payKnown); assertEquals(200.0, WorkPay.teamPay(unknown)!!, 0.0); assertNull(WorkPay.group(unknown))
    }

    @Test fun passThroughIsOwedByTheEmployerButIsNotIncome() {
        val solo = EntryDraft(mode = EntryMode.JOB, category = "שיפוצים", amount = "25", passThrough = "15").toEntry()
        assertEquals(25.0, solo.totalEarnings, 0.0); assertEquals(15.0, solo.passThrough, 0.0)
        assertEquals(mapOf("₪" to 25.0), WorkPay.totals(listOf(solo)))
        assertEquals(40.0, WorkPay.employerDue(solo)!!, 0.0)
        val shift = EntryDraft(mode = EntryMode.HOURS, category = "מסעדה", hours = "4", rate = "50", passThrough = "20").toEntry()
        assertEquals(200.0, shift.totalEarnings, 0.0); assertEquals(220.0, WorkPay.employerDue(shift)!!, 0.0)
        val team = draft(WorkerDraft("דני", amount = "50"), amount = "75").copy(passThrough = "30").toEntry()
        assertEquals(155.0, WorkPay.group(team)!!.employerTotal, 0.0)
        // It travels in backups (and so in sync), and a file without it is unchanged.
        assertEquals(15.0, WorkBackup.decode(WorkBackup.encode(emptyList(), listOf(solo), emptyList())).entries.single().passThrough, 0.0)
        assertFalse(WorkBackup.encode(emptyList(), listOf(solo.copy(passThrough = 0.0)), emptyList()).contains("passThrough"))
        // Shared texts say it is not part of the amount; a worker's own text never shows it.
        assertTrue(WorkShareText.personal(solo).contains("החזר הוצאות: ₪15.00 (לא כלול בסכום)"))
        val report = WorkShareText.employer(team, "שולי")
        assertTrue(report, report.contains("החזר הוצאות: ₪30.00")); assertTrue(report, report.contains("סכום הצוות: ₪155.00"))
        assertFalse(WorkShareText.worker(team, WorkPay.workers(team).single()).contains("החזר"))
        // An edit that does not touch it keeps it exactly.
        assertEquals(15.0, EntryDraft.from(solo.copy(id = 2)).copy(notes = "x").toEntry(solo.copy(id = 2)).passThrough, 0.0)
    }

    @Test fun soloJobsAndGroupShiftsAreUnchanged() {
        val solo = EntryDraft(mode = EntryMode.JOB, category = "שיפוצים", amount = "450").toEntry()
        assertFalse(solo.isGroupShift); assertEquals("", solo.groupWorkersJson); assertEquals(450.0, solo.totalEarnings, 0.0)
        // A group shift worker without a rate still follows the shift rate.
        val shift = WorkEntry(category = "מסעדה", date = 1_760_000_000_000L, isTimeRange = false, hours = 4.0, hourlyRate = 50.0, totalEarnings = 200.0,
            isGroupShift = true, groupWorkersJson = """[{"name":"נועה","hours":4.0,"isPaid":false}]""")
        assertEquals(200.0, WorkPay.workerPay(WorkPay.workers(shift).single(), shift)!!, 0.0)
    }

    @Test fun sharingNamesTheFixedSumAndTheWorkersOwnPay() {
        val e = draft(WorkerDraft("דני", amount = "300"), amount = "700").toEntry()
        val report = WorkShareText.employer(e, "שולי")
        assertTrue(report, report.contains("דני: סכום קבוע | ₪300.00"))
        assertTrue(report, report.contains("שולי: ₪700.00"))
        assertTrue(report, report.contains("סכום הצוות: ₪1,000.00"))
        val worker = WorkShareText.worker(e, WorkPay.workers(e).single())
        assertTrue(worker, worker.contains("₪300.00")); assertFalse(worker, worker.contains("₪700"))
    }

    @Test fun draftSurvivesSavingAsJson() {
        val d = draft(WorkerDraft("יוסי", amount = "70")).copy(passThrough = "12")
        assertEquals(d, EntryDraft.fromJson(d.toJson()))
    }
}
