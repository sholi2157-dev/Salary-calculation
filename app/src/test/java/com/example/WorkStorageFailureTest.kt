package com.example

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.OnboardingController
import com.example.ui.WorkOnboardingStore
import com.example.ui.WorkViewModel
import com.example.ui.kis.KisSettings
import com.example.ui.kis.ThemeChoice
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real Room transactions plus injected disk failures; only synthetic stores are opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkStorageFailureTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val owner = WorkAccountScope("synthetic-storage-${UUID.randomUUID()}")
    private val db get() = owner.database(app)
    private val dao get() = db.workDao()
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private fun model(settings: DataStore<Preferences> = WorkAccountPreferences.get(app, owner)): WorkViewModel =
        ViewModelProvider(models, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                WorkViewModel(app, WorkRepository(dao), owner, settings) as T
        })[WorkViewModel::class.java]

    @After fun close() { models.viewModelStore.clear(); WorkStorage.dismissFailure() }

    private fun awaitFailure(message: String = WorkStorage.SAVE_FAILED) {
        val until = System.nanoTime() + 5_000_000_000L
        while (WorkStorage.failure.value == null && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(message, WorkStorage.failure.value)
    }

    private fun entry() = WorkEntry(category = "synthetic", date = 1000L, isTimeRange = false,
        hours = 2.0, hourlyRate = 40.0, totalEarnings = 79.13, currency = "$")

    @Test fun failedPaidWriteReportsBackupAdviceAndKeepsStoredMoney() {
        val id = runBlocking { dao.insertEntry(entry()) }.toInt()
        val before = runBlocking { dao.exportSnapshot() }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_paid BEFORE UPDATE ON work_entries BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        model().setPaid(listOf(id), true)
        awaitFailure()
        assertEquals(before, runBlocking { dao.exportSnapshot() })
        // The same operation succeeds after the external storage problem is resolved.
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_paid")
        WorkStorage.dismissFailure()
        runBlocking { dao.setPaymentStatusFor(listOf(id), true) }
        assertEquals(entry().copy(id = id, isPaid = true).totalEarnings, runBlocking { dao.getEntryById(id)!! }.totalEarnings, 0.0)
    }

    @Test fun failedWorkerWriteRollsBackNewEntryAndDoesNotCallSuccess() {
        val before = runBlocking { dao.exportSnapshot() }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_worker BEFORE INSERT ON worker_directory BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        var success = false
        var failed = false
        model().saveNewEntry(entry().copy(isGroupShift = true,
            groupWorkersJson = """[{"name":"synthetic worker","hours":1,"isPaid":false}]"""),
            onSaved = { success = true }, onFailed = { failed = true })
        awaitFailure()
        assertTrue(failed)
        assertFalse(success)
        assertEquals(before, runBlocking { dao.exportSnapshot() })
    }

    @Test fun failedEditKeepsOriginalAndAllowsTheFormToRetry() {
        val id = runBlocking { dao.insertEntry(entry()) }.toInt()
        val before = runBlocking { dao.getEntryById(id)!! }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_edit BEFORE UPDATE ON work_entries BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        var failed = false
        var saved = false
        model().saveEditedEntry(id, before.copy(notes = "synthetic edit"), { saved = true }, { failed = true })
        awaitFailure()
        assertTrue(failed)
        assertFalse(saved)
        assertEquals(before, runBlocking { dao.getEntryById(id) })
    }

    @Test fun failedRepositoryBatchPropagatesAndRollsBackEveryRow() {
        val before = runBlocking { dao.exportSnapshot() }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON work_entries WHEN NEW.id = 202 BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        try {
            runBlocking { WorkRepository(dao).syncRemoteEntries(listOf(entry().copy(id = 201), entry().copy(id = 202))) }
            fail("The caller must receive the persistence failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, runBlocking { dao.exportSnapshot() })
    }

    @Test fun roomCannotOpenAtStartupReportsFailureWithoutDeletingAnything() {
        val path = app.getDatabasePath(owner.storageKey)
        path.parentFile!!.mkdirs()
        path.mkdir()
        val retained = path.resolve("synthetic retained data").apply { writeText("unchanged") }
        model()
        awaitFailure()
        assertEquals("unchanged", retained.readText())
    }

    @Test fun failedAutomaticBackupIsVisibleAndNeverDeletesPreviousCopies() {
        val vm = model()
        val root = app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)!!
        root.mkdirs()
        // A file where this synthetic account needs a directory causes a real file IOException.
        val blocked = root.resolve(owner.storageKey).apply { writeText("synthetic previous backup") }
        val before = runBlocking { dao.exportSnapshot() }
        vm.performAutoBackup()
        awaitFailure(WorkStorage.BACKUP_FAILED)
        assertEquals("synthetic previous backup", blocked.readText())
        assertEquals(before, runBlocking { dao.exportSnapshot() })
    }

    private fun failingPreferences(): Context {
        val real = app.getSharedPreferences("synthetic-failed-preferences", Context.MODE_PRIVATE)
        val failed = object : SharedPreferences by real {
            override fun edit(): SharedPreferences.Editor {
                val actual = real.edit()
                return object : SharedPreferences.Editor by actual {
                    // Model Android: commit first changes its memory map, even if the disk write fails.
                    override fun commit(): Boolean { actual.commit(); return false }
                }
            }
        }
        return object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = failed
        }
    }

    @Test fun failedPreferenceApplyRestoresMemoryAndAppearance() {
        val context = failingPreferences()
        val settings = KisSettings(context)
        settings.updateTheme(ThemeChoice.DARK)
        awaitFailure()
        assertEquals(ThemeChoice.SYSTEM, settings.theme)
        assertEquals(ThemeChoice.SYSTEM, KisSettings(context).theme)
        val prefs = WorkStorage.preferences(context, "synthetic")
        prefs.edit().putBoolean("synthetic flag", true).apply()
        assertFalse(prefs.contains("synthetic flag"))
    }

    @Test fun failedStartupClassificationAndTourCompletionNeverCrashOrPretendSuccess() {
        val store = WorkOnboardingStore(failingPreferences())
        assertFalse(store.initialize())
        awaitFailure()
        assertFalse(store.complete())
        assertFalse(store.launched())
        assertTrue(store.launchPending)
        assertFalse(store.claimAccount("synthetic account", 1000, 1000))
        val controller = OnboardingController(store, 0)
        controller.finish()
        assertTrue(controller.active)
    }

    @Test fun failedAccountBindingDoesNotOpenAnotherDatabaseOrChangeOwnership() {
        val context = failingPreferences()
        val uid = "synthetic binding account"
        val accountPath = app.getDatabasePath(WorkAccountStorage.databaseName(uid))
        assertFalse(accountPath.exists())
        try {
            WorkAccountBinding.scopeFor(context, uid)
            fail("A failed binding must stop before a model/database is selected")
        } catch (_: IOException) { }
        awaitFailure()
        assertNull(WorkAccountBinding.bound(context))
        assertFalse(accountPath.exists())
    }

    @Test fun dataStoreReadFailureAtStartupIsReportedWithoutCrashing() {
        val failed = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("synthetic read failure") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException()
        }
        val vm = model(failed)
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            vm.serviceNotificationEnabled.collect { }
        }
        try { awaitFailure() } finally { collector.cancel() }
    }

    @Test fun dataStoreWriteFailureKeepsPreviousPreference() {
        val failed = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("synthetic full disk")
        }
        val vm = model(failed)
        vm.updateServiceNotificationEnabled(false)
        awaitFailure()
        assertTrue(vm.serviceNotificationEnabled.value)
    }
}
