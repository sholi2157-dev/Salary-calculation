package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkSyncEngineTest {
    private val databases = mutableListOf<WorkDatabase>()
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
        WorkDatabase::class.java).addCallback(WorkDatabase.DatabaseCallback()).build().also { databases.add(it) }
    @After fun close() { databases.forEach { it.close() } }
    private fun entry(note: String) = WorkEntry(category = "test", date = 1234, isTimeRange = false,
        hours = 1.5, hourlyRate = 40.25, totalEarnings = 60.38, notes = note, createdAt = 1234)
    /** Firestore stand-in: compare-and-set per document, and an update counter as the read cursor. */
    private class Server : WorkSyncTransport {
        val accounts = mutableMapOf<String, MutableMap<String, Pair<WorkRemoteRecord, Long>>>()
        var online = true
        var loseAck = false
        var seq = 0L
        fun live(uid: String) = accounts[uid]?.values?.map { it.first }?.filter { !it.deleted } ?: emptyList()
        override suspend fun read(uid: String, cursor: Long?): WorkRemotePage {
            check(online)
            val all = accounts[uid]?.values?.filter { cursor == null || it.second >= cursor }?.map { it.first } ?: emptyList()
            return WorkRemotePage(all, seq)
        }
        override suspend fun exchange(uid: String, baseVersion: Long, proposed: WorkRemoteRecord): WorkRemoteRecord {
            check(online)
            val records = accounts.getOrPut(uid) { mutableMapOf() }
            val old = records[proposed.syncId]?.first
            val result = if (old?.operation == proposed.operation || (old?.version ?: 0) != baseVersion)
                checkNotNull(old) else proposed.also { records[it.syncId] = it to ++seq }
            if (loseAck) { loseAck = false; error("Lost response after server commit") }
            return result
        }
    }
    private class State : WorkSyncState { override var cursor: Long? = null; override var lastSync: Long? = null }
    private fun engine(db: WorkDatabase, server: Server, device: String, uid: String = "owner") =
        WorkSyncEngine(db, uid, device, server, State()) { uid }

    @Test fun twoDevicesExchangeEditsDeletesAndPreserveExactAmounts() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        a.workDao().insertEntry(entry("original")); ea.synchronize(); eb.synchronize()
        val copied = b.workDao().getEntriesList().single()
        assertEquals(60.38, copied.totalEarnings, 0.0)
        b.workDao().updateEntry(copied.copy(notes = "edited")); eb.synchronize(); ea.synchronize()
        assertEquals("edited", a.workDao().getEntriesList().single().notes)
        b.workDao().deleteEntry(copied); eb.synchronize(); ea.synchronize(); eb.synchronize()
        assertTrue(a.workDao().getEntriesList().isEmpty())
        assertTrue(b.workDao().getEntriesList().isEmpty())
        assertTrue(a.syncDao().pending().isEmpty())
    }

    @Test fun offlineAndLostAcknowledgementRetryWithoutDuplicates() = runBlocking {
        val a = db(); val server = Server(); val ea = engine(a, server, "A")
        a.workDao().insertEntry(entry("offline")); server.online = false
        try { ea.synchronize(); fail() } catch (_: IllegalStateException) { }
        assertTrue(a.syncDao().pending().isNotEmpty())
        server.online = true; server.loseAck = true
        try { ea.synchronize(); fail() } catch (_: IllegalStateException) { }
        ea.synchronize(); ea.synchronize()
        assertEquals(1, server.live("owner").count { it.type == "entry" })
        assertTrue(a.syncDao().pending().isEmpty())
    }

    @Test fun simultaneousEditsRemainForExplicitChoice() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        a.workDao().insertEntry(entry("base")); ea.synchronize(); eb.synchronize()
        a.workDao().updateEntry(a.workDao().getEntriesList().single().copy(notes = "A edit"))
        b.workDao().updateEntry(b.workDao().getEntriesList().single().copy(notes = "B edit"))
        ea.synchronize(); assertEquals(1, eb.synchronize())
        assertEquals("B edit", b.workDao().getEntriesList().single().notes)
        val conflict = b.syncDao().conflicts().single()
        assertTrue(conflict.conflictPayload!!.contains("A edit"))
        assertTrue(eb.conflicts().single().theirs!!.contains("A edit"))
        eb.resolve(conflict.syncId, keepLocal = true); eb.synchronize(); ea.synchronize()
        assertEquals("B edit", a.workDao().getEntriesList().single().notes)
        assertTrue(b.syncDao().conflicts().isEmpty())
    }

    @Test fun remoteDeletionCannotSilentlyEraseOfflineEdit() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        a.workDao().insertEntry(entry("base")); ea.synchronize(); eb.synchronize()
        a.workDao().deleteEntry(a.workDao().getEntriesList().single()); ea.synchronize()
        b.workDao().updateEntry(b.workDao().getEntriesList().single().copy(notes = "unsent"))
        assertEquals(1, eb.synchronize())
        assertEquals("unsent", b.workDao().getEntriesList().single().notes)
        eb.resolve(b.syncDao().conflicts().single().syncId, keepLocal = false)
        assertTrue(b.workDao().getEntriesList().isEmpty())
        assertTrue(b.syncDao().pending().isEmpty())
    }

    @Test fun ownersAndCollidingLocalIdsStaySeparate() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        a.workDao().insertEntry(entry("owner A")); b.workDao().insertEntry(entry("owner B"))
        engine(a, server, "phone", "A").synchronize()
        engine(b, server, "phone", "B").synchronize()
        assertEquals("owner A", a.workDao().getEntriesList().single().notes)
        assertEquals("owner B", b.workDao().getEntriesList().single().notes)
        var active = "A"
        val switched = WorkSyncEngine(a, "A", "phone", server, State()) { active }
        active = "B"
        try { switched.synchronize(); fail() } catch (_: IllegalStateException) { }
    }

    @Test fun firstSyncOnTwoDevicesWithTheSameDataJoinsInsteadOfDoubling() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        for (d in listOf(a, b)) {
            d.workDao().insertCategory(WorkCategory(name = "מסעדה", defaultRate = 40.25))
            d.workDao().insertWorker(WorkerDirectory(name = "דני"))
            d.workDao().insertEntry(entry("same")); d.workDao().insertEntry(entry("same").copy(createdAt = 99))
        }
        b.workDao().insertEntry(entry("only on b"))
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        ea.synchronize(); eb.synchronize(); ea.synchronize()
        assertEquals(3, server.live("owner").count { it.type == "entry" })
        assertEquals(1, server.live("owner").count { it.type == "worker" })
        // Both seeded "עצמאי" and the same place: one each in the account.
        assertEquals(listOf("מסעדה", "עצמאי"), server.live("owner").filter { it.type == "category" }
            .map { WorkBackup.decode(it.payload!!).categories.single().name }.sorted())
        assertEquals(3, a.workDao().getEntriesList().size); assertEquals(3, b.workDao().getEntriesList().size)
        assertTrue(a.syncDao().pending().isEmpty()); assertTrue(b.syncDao().pending().isEmpty())
    }

    @Test fun twoGenuinelyRepeatedShiftsStayTwo() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        a.workDao().insertEntry(entry("twice")); a.workDao().insertEntry(entry("twice"))
        engine(a, server, "A").synchronize(); engine(b, server, "B").synchronize()
        assertEquals(2, b.workDao().getEntriesList().size)
    }

    @Test fun aShiftAddedAndDeletedBeforeSyncNeverReachesTheAccount() = runBlocking {
        val a = db(); val server = Server(); val ea = engine(a, server, "A")
        val id = a.workDao().insertEntry(entry("gone")).toInt(); a.workDao().deleteEntryById(id)
        ea.synchronize()
        assertEquals(0, server.accounts["owner"]?.values?.count { it.first.type == "entry" } ?: 0)
        assertTrue(a.syncDao().pending().isEmpty())
    }

    @Test fun theStarterPlaceGoesWhenTheAccountAlreadyHasPlaces() = runBlocking {
        val web = db(); val phone = db(); val server = Server()
        web.workDao().deleteCategoryById(web.workDao().getCategoriesList().single().id)
        web.workDao().insertCategory(WorkCategory(name = "מסעדה", defaultRate = 40.25))
        engine(web, server, "web").synchronize()
        engine(phone, server, "phone").synchronize()
        assertEquals(listOf("מסעדה"), phone.workDao().getCategoriesList().map { it.name })
        assertEquals(listOf("מסעדה"), server.live("owner").filter { it.type == "category" }.map { WorkBackup.decode(it.payload!!).categories.single().name })
    }

    @Test fun plansPlanDaysAndSettingsSyncAndSettingsTakeTheAccountValueFirst() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        val plan = ShiftSeries("P1", "עצמאי", 2, "09:00", "17:00", 40.25, "₪", "2026-10-01", "2026-12-31", 5)
        a.workDao().saveSeries(plan, listOf("2026-10-05"))
        a.workDao().setLocalPreference(WorkLocalPreference("default_currency", "$"))
        b.workDao().setLocalPreference(WorkLocalPreference("default_currency", "₪"))
        ea.synchronize(); eb.synchronize()
        assertEquals(listOf(plan), b.workDao().getSeries())
        assertEquals("2026-10-05", b.workDao().getExceptions().single().date)
        assertEquals("$", b.workDao().getLocalPreferences().single { it.name == "default_currency" }.value)
        assertTrue(eb.conflicts().isEmpty())
        // A deleted plan disappears everywhere; a changed setting follows.
        b.workDao().deleteSeries("P1"); b.workDao().setLocalPreference(WorkLocalPreference("default_currency", "₪"))
        println("DEBUG1 b before: " + b.syncDao().keyed())
        eb.synchronize()
        println("DEBUG1 b after: " + b.syncDao().keyed())
        println("DEBUG1 server: " + server.accounts["owner"]?.values?.map { it.first.type + " v" + it.first.version + " " + it.first.payload?.take(160)?.replace("\n", " ") })
        println("DEBUG1 a before: " + a.syncDao().keyed())
        ea.synchronize()
        println("DEBUG1 a after: " + a.syncDao().keyed() + " prefs " + a.workDao().getLocalPreferences())
        assertTrue(a.workDao().getSeries().isEmpty()); assertTrue(a.workDao().getExceptions().isEmpty())
        assertEquals("₪", a.workDao().getLocalPreferences().single { it.name == "default_currency" }.value)
        assertTrue(a.syncDao().keyed().none { it.revision > it.acknowledgedRevision })
    }

    @Test fun aPlanChangedOnBothDevicesWaitsForTheUser() = runBlocking {
        val a = db(); val b = db(); val server = Server()
        val ea = engine(a, server, "A"); val eb = engine(b, server, "B")
        val plan = ShiftSeries("P1", "עצמאי", 2, "09:00", "17:00", 40.25, "₪", "2026-10-01", "2026-12-31", 5)
        a.workDao().upsertSeries(plan); ea.synchronize(); eb.synchronize()
        a.workDao().upsertSeries(plan.copy(endTime = "18:00")); b.workDao().upsertSeries(plan.copy(endTime = "16:00"))
        println("DEBUG2 a before: " + a.syncDao().keyed())
        ea.synchronize()
        println("DEBUG2 a after: " + a.syncDao().keyed())
        println("DEBUG2 server: " + server.accounts["owner"]?.values?.filter { it.first.type == "series" }?.map { "v" + it.first.version + " " + it.first.payload?.replace("\n", " ")?.take(400) })
        assertEquals(1, eb.synchronize())
        println("DEBUG2 b: " + b.syncDao().keyed())
        assertEquals("16:00", b.workDao().getSeries().single().endTime)
        val c = eb.conflicts().single(); assertEquals("series", c.type)
        eb.resolve(c.syncId, keepLocal = false)
        assertEquals("18:00", b.workDao().getSeries().single().endTime)
        eb.synchronize(); assertTrue(eb.conflicts().isEmpty())
    }
}
