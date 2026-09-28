package com.example

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only on disposable emulator, against the real non-debuggable release package. */
@RunWith(AndroidJUnit4::class)
class DistributionUpdateTest {
    @Test fun retainedAcrossRealPackageUpdate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("com.aistudio.worktracker.qztvdw.distribution", context.packageName)
        assertTrue(BuildConfig.LOCAL_DISTRIBUTION)
        assertFalse(BuildConfig.ACCOUNTS_ENABLED || BuildConfig.CLOUD_SYNC_ENABLED || BuildConfig.VERSIONED_SYNC_ENABLED)
        assertEquals(0, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
        @Suppress("DEPRECATION") val installedCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        val stage = InstrumentationRegistry.getArguments().getString("stage")!!
        val db = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        val dao = db.workDao()
        val evidence = context.getSharedPreferences("update_fixture_evidence", 0)
        if (stage == "seed") {
            assertTrue(dao.getEntriesList().isEmpty())
            dao.insertCategory(WorkCategory(name = "ILS category", defaultRate = 40.0))
            dao.insertCategory(WorkCategory(name = "USD category", defaultRate = 45.75))
            dao.insertWorker(WorkerDirectory(name = "Synthetic worker"))
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "USD category"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "$"))
            dao.setLocalPreference(WorkLocalPreference("categoryCurrency:USD category", "$"))
            dao.insertEntry(WorkEntry(category = "ILS category", date = 1700000000000, isTimeRange = false,
                hours = 2.0, hourlyRate = 40.0, totalEarnings = 79.97, notes = "saved imported amount", isPaid = true))
            dao.insertEntry(WorkEntry(category = "USD category", date = 1700100000000, isTimeRange = true,
                startTime = "22:00", endTime = "06:00", hours = 7.5, hourlyRate = 45.75, totalEarnings = 343.12,
                currency = "$", notes = "overnight with break"))
            dao.insertEntry(WorkEntry(category = "USD category", date = 1700200000000, isTimeRange = false,
                hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0, currency = "$", notes = "group",
                isGroupShift = true, employerRate = 65.0, workerRate = 30.0,
                groupWorkersJson = """[{"name":"Synthetic worker","hours":3,"isPaid":false,"workerRate":31.25,"employerRate":67.5}]"""))
            assertTrue(evidence.edit().putString("snapshot", dao.exportSnapshot())
                .putString("identities", db.syncDao().pending().toString()).putInt("versionA", installedCode).commit())
        } else {
            assertEquals(evidence.getString("snapshot", null), dao.exportSnapshot())
            assertEquals(evidence.getString("identities", null), db.syncDao().pending().toString())
            assertEquals(3, dao.getEntriesList().size)
            val old = WorkBackup.decode(evidence.getString("snapshot", null)!!)
            assertEquals(0, dao.importBackup(old))
            assertEquals(evidence.getString("snapshot", null), dao.exportSnapshot())
            if (stage == "verify") {
                assertTrue(installedCode > evidence.getInt("versionA", Int.MAX_VALUE))
                val original = dao.getEntriesList().first()
                dao.updateEntry(WorkEntryEdits.apply(original, original.copy(notes = "edited after update")))
                assertEquals(original.totalEarnings, dao.getEntryById(original.id)!!.totalEarnings, 0.0)
                val exported = WorkBackup.decode(dao.exportSnapshot())
                assertEquals(3, exported.entries.size)
                assertEquals("$", exported.localPreferences["default_currency"])
                assertTrue(exported.entries.any { it.notes == "edited after update" })
                assertEquals(mapOf("₪" to 79.97, "$" to 443.12), WorkMoney.totals(exported.entries))
            } else assertEquals("restart", stage)
        }
    }
}
