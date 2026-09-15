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
    private class Server : WorkSyncTransport {
        val accounts = mutableMapOf<String, MutableMap<String, WorkRemoteRecord>>()
        var online = true
        var loseAck = false
        override suspend fun readAll(uid: String): List<WorkRemoteRecord> {
            check(online); return accounts[uid]?.values?.toList() ?: emptyList()
        }
        override suspend fun exchange(uid: String, baseVersion: Long, proposed: WorkRemoteRecord): WorkRemoteRecord {
            check(online)
            val records = accounts.getOrPut(uid) { mutableMapOf() }
            val old = records[proposed.syncId]
            val result = if (old?.operation == proposed.operation || (old?.version ?: 0) != baseVersion)
                checkNotNull(old) else proposed.also { records[it.syncId] = it }
            if (loseAck) { loseAck = false; error("Lost response after server commit") }
            return result
        }
    }
    private fun engine(db: WorkDatabase, server: Server, device: String, uid: String = "owner") =
        WorkSyncEngine(db, uid, device, server) { uid }

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
        assertEquals(1, server.accounts.getValue("owner").values.count { it.type == "entry" })
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
        val switched = WorkSyncEngine(a, "A", "phone", server) { active }
        active = "B"
        try { switched.synchronize(); fail() } catch (_: IllegalStateException) { }
    }
}
