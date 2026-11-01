package com.example

import com.example.data.WorkEntry
import com.example.data.WorkPay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2.3.0 money contract (audit CAL-02, CAL-05): the same fixture as web web-tests/fixtures/money-contract.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkMoneyContractTest {
    private val doc = JSONObject(javaClass.classLoader!!.getResourceAsStream("money-contract.json").bufferedReader().readText())

    @Test fun everyNewAmountFollowsTheSharedContract() {
        val cases = doc.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.toString(), c.getDouble("expected"), WorkPay.money(c.getDouble("hours"), c.getDouble("rate"), c.getDouble("bonus")), 0.0)
        }
    }

    /** CAL-03: team amounts reconcile in whole agorot, the same fixture as web web-tests/fixtures/group-reconcile.json. */
    @Test fun teamAmountsMatchTheSharedFixtureAndAddUp() {
        val cases = JSONObject(javaClass.classLoader!!.getResourceAsStream("group-reconcile.json").bufferedReader().readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val j = c.getJSONObject("entry"); val x = c.getJSONObject("expected"); val name = c.getString("name")
            fun opt(k: String) = if (j.isNull(k)) null else j.getDouble(k)
            val e = WorkEntry(category = j.getString("category"), date = 0L, isTimeRange = false, hours = j.getDouble("hours"), hourlyRate = j.getDouble("hourlyRate"),
                totalEarnings = j.getDouble("totalEarnings"), isGroupShift = true, employerRate = opt("employerRate"), workerRate = opt("workerRate"),
                groupWorkersJson = j.getString("groupWorkersJson"), currency = j.getString("currency"), kind = j.getString("kind"),
                payKnown = j.getBoolean("payKnown"), passThrough = j.getDouble("passThrough"))
            val g = WorkPay.group(e)!!
            assertEquals(name, x.getDouble("employerTotal"), g.employerTotal, 0.0)
            assertEquals(name, x.getDouble("profit"), g.profit, 0.0)
            assertEquals(name, x.getDouble("myNet"), g.myNet, 0.0)
            val ws = WorkPay.workers(e); val xw = x.getJSONArray("workers")
            for (k in ws.indices) {
                assertEquals(name, xw.getJSONObject(k).getDouble("worker"), WorkPay.workerPay(ws[k], e)!!, 0.0)
                assertEquals(name, xw.getJSONObject(k).getDouble("employer"), WorkPay.employerPay(ws[k], e)!!, 0.0)
            }
            assertEquals("the saved amount is used as saved", j.getDouble("totalEarnings"), e.totalEarnings, 0.0)
        }
    }

    @Test fun timerSecondsAreTheDifferenceTruncatedOnce() {
        assertEquals(0L, WorkPay.elapsedSeconds(1_000_999L, 1_001_000L))
        assertEquals(1L, WorkPay.elapsedSeconds(1_000_000L, 1_001_999L))
        assertEquals(0L, WorkPay.elapsedSeconds(5_000L, 1_000L))
    }
}
