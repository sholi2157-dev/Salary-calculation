package com.example

import com.example.data.WorkEntry
import com.example.ui.kis.HistoryFilter
import com.example.ui.kis.KisHistoryLogic
import com.example.ui.kis.Period
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/** Synthetic shifts through the real History filter, including 23- and 25-hour days. */
class V2HistoryRangeDstTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private lateinit var previousZone: TimeZone

    @Before fun setZone() {
        previousZone = TimeZone.getDefault()
        // EntryDraft.startOfDay also uses the system timezone; restore it after each test.
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After fun restoreZone() {
        TimeZone.setDefault(previousZone)
    }

    private fun midnight(day: String): Long = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun shift(id: Int, day: String, time: String) = WorkEntry(
        id = id, category = "בדיקה סינתטית",
        date = LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli(),
        isTimeRange = false, hours = 1.0, hourlyRate = 60.0, totalEarnings = 60.0, createdAt = 0L,
    )

    private fun assertRange(from: String, to: String, previous: String, next: String) {
        val entries = listOf(shift(1, previous, "23:30"), shift(2, from, "00:30"),
            shift(3, to, "23:30"), shift(4, next, "00:30"))
        val filter = HistoryFilter(period = Period.RANGE, from = midnight(from), to = midnight(to))
        assertEquals(listOf(3, 2), KisHistoryLogic.apply(entries, filter, "", zone).map { it.id })
        assertEquals(listOf(3, 2), KisHistoryLogic.apply(entries, filter, "").map { it.id })
    }

    @Test fun springDstDay() = assertRange("2026-03-27", "2026-03-27", "2026-03-26", "2026-03-28")
    @Test fun autumnDstDay() = assertRange("2026-10-25", "2026-10-25", "2026-10-24", "2026-10-26")
    @Test fun normalDay() = assertRange("2026-02-10", "2026-02-10", "2026-02-09", "2026-02-11")
    @Test fun lastDayOfMonth() = assertRange("2026-04-30", "2026-04-30", "2026-04-29", "2026-05-01")
    @Test fun lastDayOfYear() = assertRange("2026-12-31", "2026-12-31", "2026-12-30", "2027-01-01")
    @Test fun multiDayRangeEndingOnSpringDst() = assertRange("2026-03-25", "2026-03-27", "2026-03-24", "2026-03-28")
    @Test fun multiDayRangeEndingOnAutumnDst() = assertRange("2026-10-23", "2026-10-25", "2026-10-22", "2026-10-26")
}
