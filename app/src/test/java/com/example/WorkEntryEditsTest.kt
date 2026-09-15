package com.example

import com.example.data.WorkEntry
import com.example.data.WorkEntryEdits
import org.junit.Assert.*
import org.junit.Test

class WorkEntryEditsTest {
    private val original = WorkEntry(id = 7, category = "test", date = 123L,
        isTimeRange = true, startTime = "22:00", endTime = "06:00", hours = 7.375,
        hourlyRate = 50.125, totalEarnings = 360.12, createdAt = 99L, currency = "$",
        employerRate = 60.375, workerRate = 40.125)

    @Test fun notesAndPaymentEditPreservesImportedFinancialValuesAndIdentity() {
        val saved = WorkEntryEdits.apply(original, original.copy(notes = "updated", isPaid = true, createdAt = 456L))
        assertEquals(original.copy(notes = "updated", isPaid = true), saved)
    }

    @Test fun unchangedClockPreservesNetHoursExactly() {
        assertEquals(37.5, WorkEntryEdits.breakMinutes(original), 0.0)
        assertEquals(original.hours, WorkEntryEdits.netHours(original, "22:00", "06:00", 37.5), 0.0)
    }

    @Test fun editedClockAndBreakUseOvernightNetHours() {
        val hours = WorkEntryEdits.netHours(original, "22:00", "07:00", 30.0)
        val saved = WorkEntryEdits.apply(original, original.copy(endTime = "07:00", hours = hours))
        assertEquals(8.5, saved.hours, 0.0)
        assertEquals(426.06, saved.totalEarnings, 0.0)
        assertEquals(99L, saved.createdAt)
    }

    @Test fun fractionalManualHoursAndRatesArePreserved() {
        val manual = original.copy(isTimeRange = false, startTime = null, endTime = null, hours = 1.23456789)
        assertEquals(manual, WorkEntryEdits.apply(manual, manual.copy(hourlyRate = manual.hourlyRate.toString().toDouble(), hours = manual.hours.toString().toDouble())))
        assertEquals(74.07, WorkEntryEdits.apply(manual, manual.copy(hourlyRate = 60.0)).totalEarnings, 0.0)
    }
}
