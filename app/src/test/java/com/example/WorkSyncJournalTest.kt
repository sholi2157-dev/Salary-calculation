package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkSyncJournalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<WorkDatabase>()
    private val names = mutableListOf<String>()

    private fun memory(): WorkDatabase = Room.inMemoryDatabaseBuilder(context, WorkDatabase::class.java)
        .addCallback(WorkDatabase.DatabaseCallback())
        .build().also { opened.add(it) }

    private fun entry(note: String = "synthetic") = WorkEntry(
        category = "synthetic", date = 1000, isTimeRange = false,
        hours = 7.5, hourlyRate = 50.25, totalEarnings = 376.87,
        notes = note, createdAt = 1234, currency = "₪",
        isGroupShift = true, employerRate = 50.25, workerRate = 20.125,
        groupWorkersJson = """[{"name":"synthetic worker","hours":7.5,"isPaid":false}]"""
    )

    @After fun close() {
        opened.forEach { it.close() }
        names.forEach { context.deleteDatabase(it) }
    }

    @Test fun insertsEditsPaymentsAndDeletionStayQueued() = runBlocking {
        val db = memory()
        val dao = db.workDao()
        val id = dao.insertEntry(entry()).toInt()
        val first = db.syncDao().find("entry", id)!!
        assertEquals(1L, first.revision)
        assertTrue(first.syncId.matches(Regex("[a-f0-9]{32}")))
        val original = dao.getEntryById(id)!!
        dao.updateEntry(original.copy(notes = "edited", isPaid = true))
        val edited = db.syncDao().find("entry", id)!!
        assertEquals(first.syncId, edited.syncId)
        assertEquals(2L, edited.revision)
        // Response to the older upload arrives after the payment edit.
        assertEquals(1, db.syncDao().acknowledge(first.syncId, 1, 0, 1))
        val pending = WorkSyncJournal(db).pendingMutations().single { it.record.entityType == "entry" }
        assertEquals(2L, pending.record.revision)
        val decoded = WorkBackup.decode(pending.payload!!).entries.single()
        assertTrue(decoded.isPaid)
        assertEquals(376.87, decoded.totalEarnings, 0.0)
        assertEquals(original.groupWorkersJson, decoded.groupWorkersJson)
        assertEquals(0, db.syncDao().acknowledge(first.syncId, 1, 0, 1))
        dao.deleteEntryById(id)
        val tombstone = WorkSyncJournal(db).pendingMutations().single { it.record.entityType == "entry" }
        assertTrue(tombstone.record.deleted)
        assertNull(tombstone.payload)
        assertEquals(first.syncId, tombstone.record.syncId)
        assertEquals(3L, tombstone.record.revision)
        assertEquals(1, db.syncDao().acknowledge(first.syncId, 2, 1, 2))
        assertTrue(db.syncDao().pending().any { it.syncId == first.syncId })
        assertEquals(1, db.syncDao().acknowledge(first.syncId, 3, 2, 3))
        assertFalse(db.syncDao().pending().any { it.syncId == first.syncId })
        // Retain tombstone identity even after acknowledgement.
        assertTrue(db.syncDao().find("entry", id)!!.deleted)
    }

    @Test fun rollbackCannotLeaveBusinessRowsWithoutJournalOrViceVersa() = runBlocking {
        val db = memory()
        try {
            db.withTransaction {
                db.workDao().insertEntry(entry())
                error("synthetic interruption")
            }
        } catch (_: IllegalStateException) {}
        assertTrue(db.workDao().getEntriesList().isEmpty())
        assertTrue(db.syncDao().pending().none { it.entityType == "entry" })
    }

    @Test fun replaceAndBulkCategoryChangesKeepStableIdentity() = runBlocking {
        val db = memory()
        val dao = db.workDao()
        val catId = dao.insertCategory(WorkCategory(name = "synthetic", defaultRate = 50.25)).toInt()
        val cat = db.syncDao().find("category", catId)!!
        dao.insertCategory(WorkCategory(id = catId, name = "synthetic", defaultRate = 60.125))
        assertEquals(cat.syncId, db.syncDao().find("category", catId)!!.syncId)
        assertTrue(db.syncDao().find("category", catId)!!.revision > cat.revision)
        val id = dao.insertEntry(entry()).toInt()
        val before = db.syncDao().find("entry", id)!!
        dao.updateCategoryForEntries("synthetic", "renamed")
        assertEquals(before.revision + 1, db.syncDao().find("entry", id)!!.revision)
        assertEquals(376.87, dao.getEntryById(id)!!.totalEarnings, 0.0)
        val worker = dao.insertWorker(WorkerDirectory(name = "worker")).toInt()
        assertNotNull(db.syncDao().find("worker", worker))
    }

    @Test fun repeatedImportKeepsGenuineDuplicatesAndDoesNotQueueDuplicates() = runBlocking {
        val db = memory()
        val fixture = WorkBackup.Contents(
            listOf(WorkCategory(name = "synthetic")), listOf(entry(), entry()), emptyList())
        assertEquals(2, db.workDao().importBackup(fixture))
        val first = db.syncDao().pending()
        assertEquals(0, db.workDao().importBackup(fixture))
        assertEquals(first, db.syncDao().pending())
        val records = first.filter { it.entityType == "entry" }
        assertEquals(2, records.map { it.syncId }.distinct().size)
    }

    @Test fun conflictPreservesLocalDataAndRemoteCandidate() = runBlocking {
        val db = memory()
        val id = db.workDao().insertEntry(entry()).toInt()
        val before = db.syncDao().find("entry", id)!!
        assertEquals(1, db.syncDao().recordConflict(before.syncId, 0, "synthetic remote candidate"))
        assertEquals("synthetic remote candidate", db.syncDao().conflicts().single().conflictPayload)
        assertEquals("synthetic", db.workDao().getEntryById(id)!!.notes)
        assertEquals(0, db.syncDao().acknowledge(before.syncId, 1, 0, 1))
        assertTrue(db.syncDao().pending().none { it.syncId == before.syncId })
        // Further edits remain preserved while review is outstanding.
        db.workDao().updateEntry(db.workDao().getEntryById(id)!!.copy(notes = "newer local"))
        assertEquals("synthetic remote candidate", db.syncDao().conflicts().single().conflictPayload)
    }

    @Test fun versionFiveMigrationPreservesTwentyTwoRowsAndEightCategories() = runBlocking {
        val name = "migration-${UUID.randomUUID()}"
        names.add(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("""CREATE TABLE work_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, date INTEGER NOT NULL,
                isTimeRange INTEGER NOT NULL, startTime TEXT, endTime TEXT, hours REAL NOT NULL,
                hourlyRate REAL NOT NULL, totalEarnings REAL NOT NULL, isPaid INTEGER NOT NULL,
                notes TEXT NOT NULL, createdAt INTEGER NOT NULL, isGroupShift INTEGER NOT NULL,
                employerRate REAL, workerRate REAL, groupWorkersJson TEXT NOT NULL, currency TEXT NOT NULL)""")
            old.execSQL("CREATE TABLE work_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, defaultRate REAL NOT NULL)")
            old.execSQL("CREATE TABLE worker_directory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            repeat(8) { old.execSQL("INSERT INTO work_categories VALUES (?, ?, ?)", arrayOf(it + 1, "category $it", 50.25)) }
            repeat(22) {
                old.execSQL("""INSERT INTO work_entries VALUES (?, ?, 1000, 0, NULL, NULL, 7.5, 50.25, 376.87,
                    0, 'kept', 1234, 1, 50.25, 20.125, '[]', '₪')""", arrayOf(it + 1, "category ${it % 8}"))
            }
            old.version = 5
        }
        fun open(): WorkDatabase = Room.databaseBuilder(context, WorkDatabase::class.java, name)
            .addMigrations(WorkDatabase.MIGRATION_5_6)
            .addCallback(WorkDatabase.DatabaseCallback()).build().also { opened.add(it) }
        val db = open()
        val rows = db.workDao().getEntriesList()
        assertEquals(22, rows.size)
        assertEquals(8, db.workDao().getCategoriesList().size)
        assertTrue(rows.all { it.notes == "kept" && it.totalEarnings == 376.87 && it.createdAt == 1234L && it.currency == "₪" })
        assertEquals((1..22).toSet(), rows.map { it.id }.toSet())
        val identities = db.syncDao().pending()
        assertEquals(30, identities.size)
        assertEquals(30, identities.map { it.syncId }.distinct().size)
        db.close()
        val reopened = open()
        assertEquals(identities, reopened.syncDao().pending())
        // A new direct DAO writer (also used by the timer service) is journaled.
        val id = reopened.workDao().insertEntry(entry()).toInt()
        assertNotNull(reopened.syncDao().find("entry", id))
    }

    @Test fun accountDatabaseNamesAreIsolatedAndNeverContainRawUid() = runBlocking {
        val a = WorkAccountStorage.databaseName("synthetic/account-a")
        val b = WorkAccountStorage.databaseName("synthetic/account-b")
        assertNotEquals(a, b)
        assertNotEquals(a, WorkAccountStorage.GUEST_DATABASE)
        assertFalse(a.contains("synthetic"))
        assertEquals(a, WorkAccountStorage.databaseName("synthetic/account-a"))
        try { WorkAccountStorage.databaseName(" "); fail("blank UID accepted") }
        catch (_: IllegalArgumentException) {}
        // Production account factory opens independent Room databases and default categories.
        val uidA = "test-a-${UUID.randomUUID()}"
        val uidB = "test-b-${UUID.randomUUID()}"
        names.add(WorkAccountStorage.databaseName(uidA))
        names.add(WorkAccountStorage.databaseName(uidB))
        val dbA = WorkDatabase.getAccountDatabase(context, uidA).also { opened.add(it) }
        val dbB = WorkDatabase.getAccountDatabase(context, uidB).also { opened.add(it) }
        dbA.workDao().insertEntry(entry())
        assertEquals(1, dbA.workDao().getEntriesList().size)
        assertTrue(dbB.workDao().getEntriesList().isEmpty())
        assertEquals("עצמאי", dbB.workDao().getCategoriesList().single().name)
        assertTrue(dbB.syncDao().pending().none { it.entityType == "entry" })
    }
}
