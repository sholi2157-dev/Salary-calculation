package com.example

import com.example.data.ShiftSeries
import com.example.data.WorkEntry
import com.example.domain.V2Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.3.0 (audit AND-C04, CAL-04/CW-07): ending a series today and overlaps across midnight. Same cases as web plan-time-edges. */
class V2PlanEdgesTest {
    private val s = ShiftSeries("s", "מסעדה", 127, "18:00", "23:00", 50.0, "₪", "2026-03-01", "2026-12-31", 0L)
    private fun at(date: String, hm: String) = V2Plan.startMillis(date, hm)

    @Test fun endingBeforeTodaysShiftStartsDropsToday() {
        assertEquals("2026-03-09", V2Plan.endUntil(s, at("2026-03-10", "12:00")))
        assertEquals("2026-03-10", V2Plan.endUntil(s, at("2026-03-10", "18:30")))
        assertNull(V2Plan.endUntil(s.copy(fromDate = "2026-03-10"), at("2026-03-10", "12:00")))
        assertEquals("2026-03-05", V2Plan.endUntil(s.copy(untilDate = "2026-03-05"), at("2026-03-10", "12:00")))
    }

    /** 2.3.0 (audit CW-05): same cases as web plan-review.test.cjs. */
    @Test fun onlyAFewDaysThatJustEndedAreRecordedByThemselves() {
        fun d(date: String) = V2Plan.Occurrence("s@$date", "s", "מסעדה", date, "09:00", "17:00", 50.0, "₪", false, false, V2Plan.Status.AWAITING)
        val now = at("2026-03-10", "20:00")
        val three = listOf(d("2026-03-08"), d("2026-03-09"), d("2026-03-10"))
        assertEquals(three, V2Plan.splitCatchUp(three, now).auto)
        val four = listOf(d("2026-03-07")) + three
        assertEquals(0, V2Plan.splitCatchUp(four, now).auto.size); assertEquals(4, V2Plan.splitCatchUp(four, now).review.size)
        assertEquals(1, V2Plan.splitCatchUp(listOf(d("2026-03-01")), now).review.size)
    }

    @Test fun anOvernightShiftFromTheEveningBeforeBlocksAnEarlyPlan() {
        val saved = listOf(WorkEntry(category = "מסעדה", date = at("2026-03-09", "22:00"), isTimeRange = true, startTime = "22:00", endTime = "06:00",
            hours = 8.0, hourlyRate = 50.0, totalEarnings = 400.0))
        assertEquals(listOf("2026-03-10"), V2Plan.preview(setOf(2), "2026-03-10", "2026-03-10", "05:00", "09:00", saved, emptyList()).clashes)
        assertEquals(emptyList<String>(), V2Plan.preview(setOf(2), "2026-03-10", "2026-03-10", "06:00", "09:00", saved, emptyList()).clashes)
    }
}
