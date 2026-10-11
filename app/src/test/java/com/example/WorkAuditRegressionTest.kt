package com.example

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiParser
import com.example.data.*
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkAuditRegressionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private val databases = mutableListOf<WorkDatabase>()
    private fun database() = Room.inMemoryDatabaseBuilder(app, WorkDatabase::class.java)
        .addCallback(WorkDatabase.DatabaseCallback()).build().also { databases.add(it) }
    private fun row(category: String = "Work") = WorkEntry(category = category, date = 1000L,
        isTimeRange = false, hours = 2.0, hourlyRate = 40.0, totalEarnings = 79.13,
        currency = "$", createdAt = 500L, notes = "original")

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() {
        models.viewModelStore.clear()
        databases.forEach { it.close() }
        Dispatchers.resetMain()
    }

    @Test fun undoManualSaveAfterAiBatchKeepsEarlierAiRows() = runBlocking {
        val owner = WorkAccountScope("audit-${UUID.randomUUID()}")
        val vm = ViewModelProvider(models, WorkViewModelFactory(app, owner))
            .get(owner.storageKey, WorkViewModel::class.java)
        val dao = owner.database(app).workDao()
        vm.addShifts(listOf(
            GeminiParser.ParsedShift("Work", 1000L, 2.0, 40.0, "AI one"),
            GeminiParser.ParsedShift("Work", 2000L, 3.0, 40.0, "AI two")))
        withTimeout(5000) { while (vm.lastAddedEntryIds.size != 2) delay(10) }
        val aiRows = dao.getEntriesList()
        vm.addEntry("Work", 3000L, false, null, null, 4.0, 40.0, "manual")
        withTimeout(5000) { while (vm.lastAddedEntryId == aiRows.maxOf { it.id }) delay(10) }
        vm.undoLastAddedEntry()
        vm.undoLastAddedEntry() // A repeated tap cannot consume the previous batch.
        withTimeout(5000) { dao.getAllEntries().first { it.size < 3 } }
        assertEquals(aiRows, dao.getEntriesList())
    }

    @Test fun stalePaymentCardCannotOverwriteNewerFinancialAndGroupEdits() = runBlocking {
        val dao = database().workDao()
        val id = dao.insertEntry(row()).toInt()
        val stale = dao.getEntryById(id)!!
        val edited = stale.copy(hours = 3.25, totalEarnings = 129.13, currency = "₪",
            notes = "newer", isGroupShift = true,
            groupWorkersJson = """[{"name":"worker","hours":1,"isPaid":true,"workerRate":17.25}]""")
        dao.updateEntry(edited)
        WorkRepository(dao).togglePaymentStatus(stale)
        assertEquals(edited.copy(isPaid = true), dao.getEntryById(id))
    }

    @Test fun importedCategoryAliasesRemainFilterableIdempotentAndDeletable() = runBlocking {
        val dao = database().workDao()
        val workId = dao.insertCategory(WorkCategory(name = "Work", defaultRate = 44.0)).toInt()
        val incoming = row(" work ")
        val backup = WorkBackup.Contents(listOf(WorkCategory(name = "WORK")), listOf(incoming), emptyList(),
            mapOf("defaultCategory" to " work ", "categoryCurrency:WORK" to "$"))
        assertEquals(1, dao.importBackup(backup))
        val imported = dao.getEntriesList().single()
        assertEquals(incoming.copy(id = imported.id, category = "Work"), imported)
        assertEquals(mapOf("defaultCategory" to "Work", "categoryCurrency:Work" to "$"),
            dao.getLocalPreferences().associate { it.name to it.value })
        assertEquals(0, dao.importBackup(backup))
        assertEquals(2, dao.getCategoriesList().size)
        val work = dao.getCategoriesList().first { it.id == workId }
        assertEquals(44.0, work.defaultRate, 0.0)
        assertTrue(dao.removeCategorySafely(work))
        assertEquals(imported.copy(category = "עצמאי"), dao.getEntriesList().single())
    }

    @Test fun entryOnlyImportCanonicalizesNewCategoryAndKeepsExistingCurrencyPreference() = runBlocking {
        val dao = database().workDao()
        dao.setLocalPreference(WorkLocalPreference("categoryCurrency:Fresh", "₪"))
        val backup = WorkBackup.Contents(emptyList(), listOf(row(" Fresh "), row("fresh").copy(notes="second")), emptyList(),
            mapOf("categoryCurrency: FRESH " to "$"))
        assertEquals(2, dao.importBackup(backup))
        assertEquals(setOf("Fresh"), dao.getEntriesList().map { it.category }.toSet())
        assertEquals("₪", dao.getLocalPreferences().single().value)
        assertEquals(0, dao.importBackup(backup))
    }
}
