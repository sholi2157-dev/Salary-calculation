package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkBackupTest {
    private fun entry(currency: String = "₪") = WorkEntry(
        id = 9, category = "עבודה", date = 1774524193142, createdAt = 1774524193142,
        isTimeRange = false, hours = 5.8, hourlyRate = 40.0,
        totalEarnings = 233.33, isPaid = true, currency = currency
    )

    @Test fun legacyImportPreservesSavedAmountAndDate() {
        val text = """{"categories":[{"name":"עבודה"}],"entries":[{"category":"עבודה","date":1774524193142,"hours":5.8,"hourlyRate":40,"totalEarnings":233.33,"isPaid":true}]}"""
        val decoded = WorkBackup.decode(text)
        assertEquals(entry().copy(id = 0), decoded.entries.single())
        assertEquals(40.0, decoded.categories.single().defaultRate, 0.0)
    }

    @Test fun dollarGroupShiftSurvivesExportAndImport() {
        val original = entry("$").copy(isGroupShift = true, employerRate = 60.5, workerRate = 40.25,
            notes = "שורה ראשונה\nשורה שנייה", groupWorkersJson = """[{"name":"עובד","hours":2,"isPaid":true}]""")
        val categories = listOf(WorkCategory(name = "עבודה", defaultRate = 60.5))
        val workers = listOf(WorkerDirectory(name = "עובד"))
        val decoded = WorkBackup.decode(WorkBackup.encode(categories, listOf(original), workers))
        assertEquals(original.copy(id = 0), decoded.entries.single())
        assertEquals(categories, decoded.categories)
        assertEquals(workers, decoded.workers)
    }

    @Test fun reimportIsIdempotentWithoutDiscardingLegitimateDuplicates() {
        val existing = listOf(entry())
        val incoming = listOf(entry().copy(id = 0), entry().copy(id = 0))
        val added = WorkBackup.missingEntries(existing, incoming)
        assertEquals(1, added.size)
        assertTrue(WorkBackup.missingEntries(existing + added, incoming).isEmpty())
    }

    @Test fun currencyIsPartOfDuplicateIdentity() {
        assertEquals(1, WorkBackup.missingEntries(listOf(entry()), listOf(entry("$"))).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownCurrencyCannotSilentlyBecomeShekels() {
        WorkBackup.decode(WorkBackup.encode(emptyList(), listOf(entry("EUR")), emptyList()))
    }
}
