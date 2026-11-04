package com.example

import com.example.data.WorkEntry
import com.example.data.WorkShareText
import com.example.data.WorkTableImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** 2.3.0 (audit M-43, D-13), same as web web-tests/csv-roundtrip.test.cjs: the table for Excel keeps the exact time. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkTableExactTimeTest {
    private fun day(d: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, d, 12, 0) }.timeInMillis

    @Test fun theHoursColumnIsTheExactDuration() {
        assertEquals("1:40", WorkShareText.exactDuration(100 / 60.0))
        assertEquals("8:00:12", WorkShareText.exactDuration(28812 / 3600.0))
        assertEquals("0:00", WorkShareText.exactDuration(0.0))
        assertEquals("7:30", WorkShareText.exactDuration(7.5))
    }

    @Test fun exportAndImportKeepEverySecond() {
        val rows = listOf(
            WorkEntry(category = "מסעדה", date = day(1), isTimeRange = false, hours = 100 / 60.0, hourlyRate = 33.33, totalEarnings = 55.55),
            WorkEntry(category = "מחסן", date = day(2), isTimeRange = false, hours = 28812 / 3600.0, hourlyRate = 45.0, totalEarnings = 360.15),
            WorkEntry(category = "מסעדה", date = day(3), isTimeRange = true, startTime = "08:00", endTime = "08:50", hours = 50 / 60.0, hourlyRate = 33.33, totalEarnings = 27.78))
        for (text in listOf(WorkShareText.csv(rows), WorkShareText.excel(rows))) {
            assertTrue(text.contains("1:40") && text.contains("8:00:12") && text.contains("0:50"))
            assertFalse(text.contains("1.67"))
            val imported = WorkTableImport.decode(text).entries
            rows.forEachIndexed { i, e -> assertEquals(Math.round(e.hours * 3600), Math.round(imported[i].hours * 3600)) }
        }
    }
}
