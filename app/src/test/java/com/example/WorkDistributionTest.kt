package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WorkUpdates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkDistributionTest {
    private fun row(currency: String, note: String = "saved") = WorkEntry(category = "old", date = 1234567890000,
        isTimeRange = true, startTime = "22:00", endTime = "06:00", hours = 7.5,
        hourlyRate = 40.0, totalEarnings = 299.97, currency = currency, notes = note, createdAt = 100)
    @Test fun mixedCurrenciesNeverBecomeOneAmount() {
        val rows = listOf(row("₪"), row("$"))
        assertEquals(mapOf("₪" to 299.97, "$" to 299.97), WorkMoney.totals(rows))
        assertFalse(WorkMoney.report(rows).contains("599.94"))
        assertTrue(WorkMoney.report(rows).contains("$299.97"))
    }
    @Test fun workerReportUsesWorkersSavedHoursRatesAndPayment() {
        val entry = row("$").copy(isGroupShift = true, workerRate = 20.0, employerRate = 60.0,
            groupWorkersJson = """[{"name":"worker","hours":2,"workerRate":25.5,"employerRate":65,"isPaid":true}]""")
        val text = WorkMoney.report(listOf(entry), "worker")
        assertTrue(text.contains("$51.00")); assertTrue(text.contains("שולם")); assertFalse(text.contains("299.97"))
    }
    @Test fun categoryChangesBackupRestartAndReimportPreserveSavedFinancials() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "distribution-test-${java.util.UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, WorkDatabase::class.java, name).addCallback(WorkDatabase.DatabaseCallback()).build()
        var db = open()
        try {
            val dao = db.workDao()
            val oldId = dao.insertCategory(WorkCategory(name = "old", defaultRate = 40.0)).toInt()
            dao.insertCategory(WorkCategory(name = "new", defaultRate = 99.0))
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "new"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "$"))
            dao.setLocalPreference(WorkLocalPreference("categoryCurrency:new", "$"))
            dao.insertEntry(row("₪")); dao.insertEntry(row("$", "unpaid"))
            val before = dao.getEntriesList()
            val category = dao.getCategoriesList().first { it.id == oldId }
            dao.editCategorySafely(category, "renamed", 100.0, "$")
            assertEquals(before.map { it.copy(category = "renamed") }, dao.getEntriesList())
            assertTrue(dao.removeCategorySafely(category.copy(name = "renamed")))
            val expected = before.map { it.copy(category = "new") }
            assertEquals(expected, dao.getEntriesList())
            val backup = dao.exportSnapshot()
            val decoded = WorkBackup.decode(backup)
            assertEquals(0, dao.importBackup(decoded))
            db.close(); db = open()
            assertEquals(expected, db.workDao().getEntriesList())
            assertEquals("$", db.workDao().getLocalPreferences().first { it.name == "default_currency" }.value)
            assertEquals(backup, db.workDao().exportSnapshot())
            assertEquals(30.0, WorkEntryEdits.breakMinutes(expected.first()), 0.0)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidGroupBackupRejectedBeforeImport() {
        WorkBackup.decode(WorkBackup.encode(emptyList(), listOf(row("$").copy(groupWorkersJson = """[{"name":"x","hours":-2}]""")), emptyList()))
    }
    @Test fun manifestRequiresTrustedApkAndVersionCode() {
        val hash = "a".repeat(64)
        fun manifest(url: String) = """{"versionCode":8,"versionName":"1.5","apkUrl":"$url","sha256":"$hash"}"""
        assertEquals(8L, WorkUpdates.parse(manifest(WorkUpdates.BASE + "download/v1.5/salary.apk")).code)
        try { WorkUpdates.parse(manifest("https://evil.example/salary.apk")); fail("untrusted source") } catch (_: IllegalArgumentException) { }
    }
}
