package com.example

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

/** A job (קבלנות) with a team: each worker gets a fixed sum or hours × their own rate, and only the rest is the user's income. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2JobTeamTest {
    private fun draft(vararg workers: WorkerDraft, amount: String = "1000") =
        EntryDraft(mode = EntryMode.JOB, category = "שיפוצים", amount = amount, workers = workers.toList())

    @Test fun storesOnlyTheUsersShareAndEachWorkersPay() {
        val d = draft(WorkerDraft("דני", amount = "300"), WorkerDraft("יוסי", byHours = true, hours = "5:30", rate = "60"))
        assertTrue(d.check().errors.toString(), d.check().canSave)
        assertEquals(370.0, d.check().total!!, 0.0)
        val e = d.toEntry()
        assertEquals(WorkEntry.KIND_JOB, e.kind); assertTrue(e.isGroupShift); assertTrue(WorkPay.isTeamJob(e))
        assertEquals(370.0, e.totalEarnings, 0.0)
        assertEquals(mapOf("₪" to 370.0), WorkPay.totals(listOf(e)))
        val ws = WorkPay.workers(e)
        assertEquals(300.0, WorkPay.workerPay(ws[0], e)!!, 0.0)
        assertEquals(330.0, WorkPay.workerPay(ws[1], e)!!, 0.0)
        val g = WorkPay.group(e)!!
        assertEquals(1000.0, g.employerTotal, 0.0); assertEquals(370.0, g.myNet, 0.0)
        // Exactly one way of paying is stored per worker.
        val json = JSONArray(e.groupWorkersJson)
        assertTrue(json.getJSONObject(0).has("amount")); assertFalse(json.getJSONObject(0).has("workerRate"))
        assertTrue(json.getJSONObject(1).has("workerRate")); assertFalse(json.getJSONObject(1).has("amount"))
    }

    @Test fun reopeningShowsTheFullPriceAndUntouchedEditsKeepEverything() {
        val e = draft(WorkerDraft("דני", amount = "300")).toEntry().copy(id = 4, createdAt = 9)
        val back = EntryDraft.from(e)
        assertEquals(EntryMode.JOB, back.mode)
        assertEquals("1000", back.amount)
        assertEquals("300", back.workers.single().amount); assertFalse(back.workers.single().byHours)
        assertEquals(e.copy(notes = "מטבח"), back.copy(notes = "מטבח").toEntry(e))
        // Changing a worker's sum moves the user's share, never the price.
        val raised = back.copy(workers = listOf(back.workers.single().copy(amount = "400"))).toEntry(e)
        assertEquals(600.0, raised.totalEarnings, 0.0)
    }

    @Test fun teamCannotTakeMoreThanThePriceAndEveryPayMustBeKnown() {
        assertTrue(draft(WorkerDraft("דני", amount = "1200")).check().errors.contains("הצוות מקבל יותר מסכום העבודה"))
        assertTrue(draft(WorkerDraft("דני")).check().errors.contains("חסר כמה מקבל כל עובד"))
        assertTrue(draft(WorkerDraft("דני", byHours = true, hours = "3")).check().errors.contains("חסר כמה מקבל כל עובד"))
        // The whole price may go to the team.
        val all = draft(WorkerDraft("דני", amount = "1000"))
        assertTrue(all.check().canSave); assertEquals(0.0, all.toEntry().totalEarnings, 0.0)
        // An unknown price still records the team.
        val unknown = draft(WorkerDraft("דני", amount = "200"), amount = "").copy(amountUnknown = true).toEntry()
        assertFalse(unknown.payKnown); assertEquals(200.0, WorkPay.teamPay(unknown)!!, 0.0); assertNull(WorkPay.group(unknown))
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
        val e = draft(WorkerDraft("דני", amount = "300")).toEntry()
        val report = WorkShareText.employer(e, "שולי")
        assertTrue(report, report.contains("דני: סכום קבוע | ₪300.00"))
        assertTrue(report, report.contains("שולי: ₪700.00"))
        assertTrue(report, report.contains("סכום הצוות: ₪1,000.00"))
        val worker = WorkShareText.worker(e, WorkPay.workers(e).single())
        assertTrue(worker, worker.contains("₪300.00")); assertFalse(worker, worker.contains("₪700"))
    }

    @Test fun draftSurvivesSavingAsJson() {
        val d = draft(WorkerDraft("יוסי", byHours = true, hours = "2:15", rate = "70"))
        assertEquals(d, EntryDraft.fromJson(d.toJson()))
    }
}
