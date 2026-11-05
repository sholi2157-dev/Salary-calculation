package com.example

import com.example.data.WorkEntry
import com.example.data.WorkPay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M-06: corrupt stored team rates produce unknown derived pay, preserving saved history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkM06StoredRateTest {
    private fun entry(worker: String = "30", employer: String = "65") = WorkEntry(
        category = "אירועים", date = 1_760_000_000_000L, isTimeRange = false,
        hours = 2.0, hourlyRate = 50.0, totalEarnings = 123.45, isGroupShift = true, passThrough = 7.25,
        groupWorkersJson = """[{"name":"נועה","hours":2,"bonus":5,"workerRate":$worker,"employerRate":$employer,"future":{"keep":true}}]""")

    @Test fun storedNonFiniteWorkerAndEmployerRatesAreUnknown() {
        for (invalid in listOf("1e400", "-1e400", "\"Infinity\"", "\"-Infinity\"", "\"NaN\"")) {
            for (workerInvalid in listOf(true, false)) {
                val e = if (workerInvalid) entry(worker = invalid) else entry(employer = invalid)
                val before = e.copy()
                val w = WorkPay.workers(e).single() // Do not discard the worker or its unknown metadata.
                assertTrue(w.source.getJSONObject("future").getBoolean("keep"))
                if (workerInvalid) {
                    assertNull(WorkPay.workerPay(w, e))
                    assertEquals(135.0, WorkPay.employerPay(w, e)!!, 0.0)
                    assertNull(WorkPay.teamPay(e))
                } else {
                    assertNull(WorkPay.employerPay(w, e))
                    assertEquals(65.0, WorkPay.workerPay(w, e)!!, 0.0)
                }
                assertNull(WorkPay.group(e))
                assertNull(WorkPay.employerDue(e))
                assertEquals(123.45, WorkPay.pay(e)!!, 0.0)
                assertEquals(before, e)
            }
        }
    }

    @Test fun nonFiniteInheritedRatesAreUnknownAndDoNotFallBackToKnownShiftRate() {
        val base = entry().copy(groupWorkersJson = """[{"name":"נועה","hours":2}]""")
        for (invalid in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            val e = base.copy(workerRate = invalid, employerRate = invalid)
            val w = WorkPay.workers(e).single()
            assertNull(WorkPay.workerPay(w, e)); assertNull(WorkPay.employerPay(w, e))
            assertNull(WorkPay.group(e)); assertEquals(123.45, WorkPay.pay(e)!!, 0.0)
            val fallback = base.copy(hourlyRate = invalid)
            assertNull(WorkPay.workerPay(w, fallback)); assertNull(WorkPay.employerPay(w, fallback))
        }
    }

    @Test fun jobTeamWithNonFiniteWorkerRateIsUnknownButFixedAmountStillWins() {
        val e = entry(worker = "\"Infinity\"").copy(kind = WorkEntry.KIND_JOB)
        val w = WorkPay.workers(e).single()
        assertNull(WorkPay.workerPay(w, e)); assertNull(WorkPay.employerPay(w, e))
        assertNull(WorkPay.teamPay(e)); assertNull(WorkPay.group(e)); assertNull(WorkPay.employerDue(e))
        assertEquals(123.45, WorkPay.pay(e)!!, 0.0)
        assertEquals(85.0, WorkPay.workerPay(w.copy(amount = 80.0), e)!!, 0.0)
    }

    @Test fun finiteRatesKeepExactRoundingAndStoredOwnAmount() {
        val e = entry(worker = "33.33", employer = "40.01")
        val w = WorkPay.workers(e).single().copy(hours = 50.0 / 60)
        assertEquals(32.78, WorkPay.workerPay(w, e)!!, 0.0)
        assertEquals(38.34, WorkPay.employerPay(w, e)!!, 0.0)
        assertEquals(WorkPay.GroupSummary(215.72, 13.36, 136.81), WorkPay.group(e))
        assertEquals(123.45, WorkPay.pay(e)!!, 0.0)
        val zero = entry(worker = "0", employer = "0")
        assertEquals(5.0, WorkPay.workerPay(WorkPay.workers(zero).single(), zero)!!, 0.0)
        val inherited = e.copy(groupWorkersJson = """[{"name":"נועה","hours":2}]""")
        assertEquals(100.0, WorkPay.workerPay(WorkPay.workers(inherited).single(), inherited)!!, 0.0)
    }
}
