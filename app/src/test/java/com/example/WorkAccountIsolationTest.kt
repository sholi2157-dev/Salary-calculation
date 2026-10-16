package com.example

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
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
class WorkAccountIsolationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private val memory = mutableListOf<WorkDatabase>()
    private fun scope() = WorkAccountScope("synthetic-${UUID.randomUUID()}")
    private fun entry(note: String = "synthetic") = WorkEntry(category = "synthetic", date = 1000,
        isTimeRange = false, hours = 7.5, hourlyRate = 50.25, totalEarnings = 376.87,
        createdAt = 1234, currency = "₪", notes = note)
    private fun model(owner: WorkAccountScope): WorkViewModel =
        ViewModelProvider(models, WorkViewModelFactory(app, owner)).get(owner.storageKey, WorkViewModel::class.java)
    private fun database() = Room.inMemoryDatabaseBuilder(app, WorkDatabase::class.java)
        .addCallback(WorkDatabase.DatabaseCallback()).build().also { memory.add(it) }

    @Before fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() {
        models.viewModelStore.clear()
        memory.forEach { it.close() }
        Dispatchers.resetMain()
    }

    @Test fun factoryScopesRowsUndoAndDelayedImportToOriginalOwner() = runBlocking {
        val a = scope(); val b = scope()
        val dbA = a.database(app); val dbB = b.database(app)
        val idA = dbA.workDao().insertEntry(entry("A")).toInt()
        val idB = dbB.workDao().insertEntry(entry("B")).toInt()
        assertEquals(idA, idB) // Installation-local IDs can collide, owners cannot.
        val vmA = model(a)
        vmA.lastAddedEntryId = idA
        val vmB = model(b)
        assertNotSame(vmA, vmB)
        assertNull(vmB.lastAddedEntryId)
        vmA.undoLastAddedEntry() // A delayed callback after B has become visible.
        withTimeout(5000) { dbA.workDao().getAllEntries().first { it.isEmpty() } }
        assertEquals("B", dbB.workDao().getEntriesList().single().notes)
        assertSame(vmA, model(a))
        val delayedSource = WorkRepository(dbA.workDao())
        delayedSource.importBackup(WorkBackup.Contents(emptyList(), listOf(entry("late A")), emptyList()))
        assertEquals("late A", WorkBackup.decode(delayedSource.exportSnapshot()).entries.single().notes)
        assertEquals("B", WorkBackup.decode(WorkRepository(dbB.workDao()).exportSnapshot()).entries.single().notes)
    }

    @Test fun preferencesArePerOwnerAndLegacyNamesArePreserved() = runBlocking {
        val a = scope(); val b = scope(); val guest = WorkAccountScope(null)
        assertEquals("user_settings", guest.settingsName)
        assertEquals("active_shift_prefs", guest.timerName)
        assertFalse(a.settingsName.contains(a.uid!!))
        val key = stringPreferencesKey("default_currency")
        val prefA = WorkAccountPreferences.get(app, a)
        val prefB = WorkAccountPreferences.get(app, b)
        prefA.edit { it[key] = "$" }
        assertNull(prefB.data.first()[key])
        assertEquals("$", prefA.data.first()[key])
        assertSame(prefA, WorkAccountPreferences.get(app, a))
    }

    @Test fun timerOwnerPrecisionRestartAndStaleClearAreSafe() {
        val a = scope(); val b = scope()
        val timerA = ShiftStateManager.forAccount(app, a)
        val timerB = ShiftStateManager.forAccount(app, b)
        assertTrue(timerA.start("A", 50.123456789, 1000, "$"))
        assertFalse(timerB.start("B", 20.0, 2000))
        assertNull(timerB.activeShiftStartTime.value)
        assertEquals(a, ShiftStateManager.activeOwner(app))
        val reopened = WorkShiftState(app, a)
        assertEquals(50.123456789, reopened.activeShiftRate.value, 0.0)
        assertEquals("$", reopened.activeShiftCurrency.value)
        assertFalse(timerA.clear(999))
        assertTrue(timerA.clear(1000))
        assertTrue(timerB.start("B", 20.0, 2000))
        assertFalse(timerA.clear(1000))
        assertEquals(2000L, timerB.activeShiftStartTime.value)
        assertTrue(timerB.clear(2000))
    }

    @Test fun timerCompletionIsAtomicAndRepeatedSaveCannotDuplicateOrResurrect() = runBlocking {
        val db = database(); val dao = db.workDao()
        val id = dao.finishTimer(1000, entry())
        assertEquals(id, dao.finishTimer(1000, entry("second")))
        assertEquals(1, dao.getEntriesList().size)
        assertNotNull(db.syncDao().find("entry", id.toInt()))
        dao.deleteEntryById(id.toInt())
        assertEquals(id, dao.finishTimer(1000, entry()))
        assertTrue(dao.getEntriesList().isEmpty())
        assertEquals(0L, dao.finishTimer(2000, null)) // Explicit discard wins over stale notification.
        assertEquals(0L, dao.finishTimer(2000, entry()))
        assertTrue(dao.getEntriesList().isEmpty())
        try { db.withTransaction { dao.finishTimer(3000, entry()); error("synthetic rollback") } }
        catch (_: IllegalStateException) {}
        assertNull(dao.receipt("timer:3000"))
        assertTrue(dao.getEntriesList().isEmpty())
    }

    @Test fun reviewedLegacyCopyKeepsGuestAndIsOneTimeEvenAfterDeletion() = runBlocking {
        val source = database(); val target = database()
        repeat(22) { source.workDao().insertEntry(entry()) }
        val before = source.workDao().getEntriesList()
        val reviewed = WorkLegacyAdoption.snapshot(source)
        source.workDao().insertEntry(entry("after review"))
        assertEquals(22, WorkLegacyAdoption.confirm(target, reviewed))
        assertEquals(22, target.workDao().getEntriesList().size)
        assertTrue(target.workDao().getEntriesList().all { it.totalEarnings == 376.87 && it.createdAt == 1234L })
        assertEquals(before, source.workDao().getEntriesList().filter { it.notes != "after review" })
        assertEquals(23, source.workDao().getEntriesList().size)
        target.workDao().getEntriesList().forEach { target.workDao().deleteEntry(it) }
        assertEquals(0, WorkLegacyAdoption.confirm(target, reviewed))
        assertTrue(target.workDao().getEntriesList().isEmpty())
        assertEquals(23, source.workDao().getEntriesList().size)
    }

    @Test fun failedLegacyCopyRollsBackRowsAndReceiptTogether() = runBlocking {
        val target = database()
        val reviewed = WorkBackup.Contents(emptyList(), listOf(entry(), entry()), emptyList())
        try { target.withTransaction { WorkLegacyAdoption.confirm(target, reviewed); error("rollback") } }
        catch (_: IllegalStateException) {}
        assertNull(target.workDao().receipt("legacy-guest-v1"))
        assertTrue(target.workDao().getEntriesList().isEmpty())
        assertEquals(2, WorkLegacyAdoption.confirm(target, reviewed))
    }
}
