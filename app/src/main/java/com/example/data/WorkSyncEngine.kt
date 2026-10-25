package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest

/** Wire IDs are installation-independent; amounts are transferred, never recalculated. */
data class WorkRemoteRecord(
    val syncId: String, val type: String, val version: Long,
    val operation: String, val deleted: Boolean, val payload: String?
) {
    /** The same rules as the website (web/sync/protocol.js validate). */
    fun validate(): WorkRemoteRecord {
        require(syncId.matches(Regex("[a-f0-9]{32}")))
        require(type in WorkSyncProtocol.TYPES && version > 0)
        require(operation.isNotBlank() && operation.length <= 256)
        if (deleted) require(payload == null) else {
            WorkSyncProtocol.decode(type, checkNotNull(payload))
            if (type in WorkSyncProtocol.KEYED_TYPES) require(WorkSyncProtocol.keyedId(type, WorkSyncProtocol.keyOf(type, payload)) == syncId)
        }
        return this
    }
    fun encode(): String = JSONObject().put("syncId", syncId).put("type", type)
        .put("version", version).put("operation", operation).put("deleted", deleted)
        .put("payload", payload ?: JSONObject.NULL).toString()
    companion object {
        fun decode(text: String): WorkRemoteRecord {
            val o = JSONObject(text)
            return WorkRemoteRecord(o.getString("syncId"), o.getString("type"), o.getLong("version"),
                o.getString("operation"), o.getBoolean("deleted"),
                if (o.isNull("payload")) null else o.getString("payload")).validate()
        }
    }
}

/** Records changed at or after the cursor, and the cursor for the next read. */
data class WorkRemotePage(val records: List<WorkRemoteRecord>, val cursor: Long?)

interface WorkSyncTransport {
    /** cursor null reads everything (the first sync). */
    suspend fun read(uid: String, cursor: Long?): WorkRemotePage
    /** Atomic compare-and-set. Returns the accepted record or the current conflict. */
    suspend fun exchange(uid: String, baseVersion: Long, proposed: WorkRemoteRecord): WorkRemoteRecord
}

/** Where the engine keeps its read position and the time of the last full sync (per database and account). */
interface WorkSyncState {
    var cursor: Long?
    var lastSync: Long?
}

/** One conflict, both sides as payloads (null = deleted on that side), for the user to choose. */
data class WorkSyncConflict(val syncId: String, val type: String, val mine: String?, val theirs: String?)

/**
 * One engine per immutable owner, the same rules as the website (web/sync/engine.js):
 * read first, so an item that already exists in the account is joined instead of sent twice; then send, each write a
 * compare-and-set on the item's version, a lost answer retried with the same operation. An item changed here and in
 * the account since the last sync is never overwritten: both stay until the user picks one. Settings are the exception
 * before their first sync: the account's value wins. A failed run leaves the durable outbox intact.
 */
class WorkSyncEngine(
    private val database: WorkDatabase,
    private val uid: String,
    private val deviceId: String,
    private val transport: WorkSyncTransport,
    private val state: WorkSyncState,
    private val currentUid: () -> String?
) {
    private val mutex = Mutex()
    private val dao get() = database.syncDao()
    private val journal = WorkSyncJournal(database)
    init { require(uid.isNotBlank() && deviceId.isNotBlank()) }
    private fun checkOwner() { check(currentUid() == uid) { "Account changed" } }
    private fun operation(syncId: String, base: Long, revision: Long, deleted: Boolean, payload: String?): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$deviceId|$base|$revision|$deleted|$payload".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$syncId:${digest.take(40)}"
    }
    private fun same(type: String, a: String?, b: String?) = WorkSyncProtocol.canonical(type, a) == WorkSyncProtocol.canonical(type, b)

    suspend fun synchronize(): Int = mutex.withLock {
        checkOwner()
        // 1. Read what changed in the account since last time. Places and plans before what refers to them.
        val first = state.cursor == null
        val page = transport.read(uid, state.cursor)
        checkOwner()
        for (r in page.records.sortedBy { ORDER.indexOf(it.type) }) {
            checkOwner()
            val valid = runCatching { r.validate() }.getOrNull() ?: continue
            if (valid.type in WorkSyncProtocol.KEYED_TYPES) receiveKeyed(valid) else receive(valid)
        }
        if (first && page.records.any { it.type == "category" && !it.deleted }) dropUnusedSeed()
        checkOwner()
        state.cursor = page.cursor ?: state.cursor
        // 2. Send what changed here.
        for (m in journal.pendingMutations()) {
            checkOwner()
            val r = m.record
            // Never reached the account: nothing to delete there.
            val gone = r.deleted || m.payload == null
            if (gone && r.remoteVersion == 0L) { dao.forget(r.syncId); continue }
            val proposed = WorkRemoteRecord(r.syncId, r.entityType, r.remoteVersion + 1,
                operation(r.syncId, r.remoteVersion, r.revision, gone, m.payload), gone, m.payload).validate()
            val accepted = transport.exchange(uid, r.remoteVersion, proposed).validate()
            checkOwner()
            require(accepted.syncId == r.syncId && accepted.type == r.entityType)
            if (accepted.operation == proposed.operation) dao.acknowledge(r.syncId, r.revision, r.remoteVersion, accepted.version)
            else receive(accepted)
        }
        for (m in journal.pendingKeyed()) {
            checkOwner()
            val r = m.record
            if (m.payload == null && r.remoteVersion == 0L) { dao.forgetKeyed(r.entityType, r.naturalKey); continue }
            val syncId = WorkSyncProtocol.keyedId(r.entityType, r.naturalKey)
            val proposed = WorkRemoteRecord(syncId, r.entityType, r.remoteVersion + 1,
                operation(syncId, r.remoteVersion, r.revision, m.payload == null, m.payload), m.payload == null, m.payload).validate()
            val accepted = transport.exchange(uid, r.remoteVersion, proposed).validate()
            checkOwner()
            require(accepted.syncId == syncId && accepted.type == r.entityType)
            if (accepted.operation == proposed.operation) dao.acknowledgeKeyed(r.entityType, r.naturalKey, r.revision, r.remoteVersion, accepted.version, proposed.deleted)
            else receiveKeyed(accepted)
        }
        checkOwner()
        state.lastSync = System.currentTimeMillis()
        conflicts().size
    }

    /** One received entry, place or worker: joined, applied, ignored as old, or kept beside the local change. */
    private suspend fun receive(remote: WorkRemoteRecord) = database.withTransaction {
        val local = dao.bySyncId(remote.syncId)
        if (local != null) {
            require(local.entityType == remote.type)
            if (remote.version <= local.remoteVersion) return@withTransaction
            if (local.revision > local.acknowledgedRevision || local.conflictPayload != null) {
                // Changed here too. The same content on both sides is simply agreed; otherwise the user chooses.
                if (same(remote.type, journal.payload(local), remote.payload)) dao.put(local.copy(acknowledgedRevision = local.revision,
                    remoteVersion = remote.version, deleted = remote.deleted, conflictPayload = null))
                else dao.put(local.copy(conflictPayload = remote.encode()))
                return@withTransaction
            }
            applyRemote(remote, local)
            return@withTransaction
        }
        if (!remote.deleted) {
            // The same item typed on two devices before either synced: join it instead of doubling it.
            val match = dao.unsent(remote.type).firstOrNull { WorkSyncProtocol.sameIdentity(remote.type, checkNotNull(journal.payload(it)), remote.payload!!) }
            if (match != null) {
                val joined = match.copy(syncId = remote.syncId)
                dao.adopt(remote.type, match.localId, remote.syncId)
                if (same(remote.type, journal.payload(match), remote.payload)) dao.put(joined.copy(acknowledgedRevision = joined.revision, remoteVersion = remote.version))
                else applyRemote(remote, joined)
                return@withTransaction
            }
        }
        applyRemote(remote, null)
    }

    /** One received plan, plan day or setting, found by its natural key. */
    private suspend fun receiveKeyed(remote: WorkRemoteRecord) = database.withTransaction {
        val key = if (!remote.deleted) WorkSyncProtocol.keyOf(remote.type, remote.payload!!)
            else keyedIndex()[remote.syncId]?.second ?: return@withTransaction
        val local = dao.keyed(remote.type, key)
        if (local != null) {
            if (remote.version <= local.remoteVersion) return@withTransaction
            if (local.revision > local.acknowledgedRevision || local.conflictPayload != null) {
                val mine = journal.keyedPayload(remote.type, key)
                when {
                    same(remote.type, mine, remote.payload) -> dao.putKeyed(local.copy(acknowledgedRevision = local.revision,
                        remoteVersion = remote.version, deleted = remote.deleted, conflictPayload = null))
                    // A setting never synced takes the account's value.
                    local.remoteVersion == 0L && remote.type == "preference" -> applyKeyed(remote, key)
                    else -> dao.putKeyed(local.copy(conflictPayload = remote.encode()))
                }
                return@withTransaction
            }
        }
        applyKeyed(remote, key)
    }

    private suspend fun keyedIndex(): Map<String, Pair<String, String>> =
        dao.keyed().associate { WorkSyncProtocol.keyedId(it.entityType, it.naturalKey) to (it.entityType to it.naturalKey) }

    /** The starter place of a new install goes when the account already has places and nothing here uses it. */
    private suspend fun dropUnusedSeed() = database.withTransaction {
        val work = database.workDao()
        val seed = work.getCategoriesList().firstOrNull { it.name == SEED && it.defaultRate == 40.0 } ?: return@withTransaction
        val record = dao.find("category", seed.id) ?: return@withTransaction
        if (record.remoteVersion != 0L || record.conflictPayload != null) return@withTransaction
        if (work.getEntriesList().any { it.category == SEED } || work.getSeries().any { it.category == SEED }) return@withTransaction
        work.deleteCategoryById(seed.id)
        dao.forget(record.syncId)
    }

    fun conflictsFlow() = dao.observeConflicts()

    suspend fun conflicts(): List<WorkSyncConflict> = database.withTransaction {
        dao.conflicts().map { WorkSyncConflict(it.syncId, it.entityType, journal.payload(it), WorkRemoteRecord.decode(it.conflictPayload!!).payload) } +
            dao.keyed().filter { it.conflictPayload != null }.map {
                WorkSyncConflict(WorkSyncProtocol.keyedId(it.entityType, it.naturalKey), it.entityType,
                    journal.keyedPayload(it.entityType, it.naturalKey), WorkRemoteRecord.decode(it.conflictPayload!!).payload)
            }
    }

    /** The user's choice; both sides remain intact until this transaction. Keeping this device's version sends it next. */
    suspend fun resolve(syncId: String, keepLocal: Boolean) = mutex.withLock {
        checkOwner()
        database.withTransaction {
            val local = dao.bySyncId(syncId)
            if (local != null) {
                val remote = WorkRemoteRecord.decode(checkNotNull(local.conflictPayload))
                if (keepLocal) dao.put(local.copy(remoteVersion = remote.version, revision = local.revision + 1, conflictPayload = null))
                else applyRemote(remote, local)
                return@withTransaction
            }
            val (type, key) = checkNotNull(keyedIndex()[syncId])
            val record = checkNotNull(dao.keyed(type, key))
            val remote = WorkRemoteRecord.decode(checkNotNull(record.conflictPayload))
            if (keepLocal) dao.putKeyed(record.copy(remoteVersion = remote.version, revision = record.revision + 1, conflictPayload = null))
            else applyKeyed(remote, key)
        }
    }

    private suspend fun applyKeyed(remote: WorkRemoteRecord, key: String) {
        val work = database.workDao()
        val data = remote.payload?.let { WorkBackup.decode(it) }
        when (remote.type) {
            "series" -> if (data == null) work.deleteSeriesRow(key) else work.upsertSeries(data.series.single())
            "exception" -> if (data == null) work.deleteException(key.substringBeforeLast('@'), key.substringAfterLast('@'))
                else work.upsertException(data.exceptions.single())
            "preference" -> if (data == null) work.removeLocalPreference(key)
                else data.localPreferences.entries.single().let { work.setLocalPreference(WorkLocalPreference(it.key, it.value)) }
            else -> error("Not a keyed type")
        }
        // The triggers ran in this transaction. Mark only this received revision as agreed.
        val revision = dao.keyed(remote.type, key)?.revision ?: 0
        dao.putKeyed(WorkSyncKeyedRecord(remote.type, key, revision, revision, remote.version, remote.deleted))
    }

    private suspend fun applyRemote(remote: WorkRemoteRecord, local: WorkSyncRecord?) {
        val dao = database.workDao()
        var id = local?.localId ?: 0
        if (remote.deleted) {
            if (local != null) when (remote.type) {
                "entry" -> dao.deleteEntryById(id)
                "category" -> dao.deleteCategoryById(id)
                "worker" -> dao.deleteWorkerById(id)
            }
            // Keep unseen tombstones too; reserve a negative journal-only local ID.
            if (local == null) {
                val sql = database.openHelper.writableDatabase
                sql.query("SELECT MIN(localId) FROM work_sync_journal WHERE entityType = ?", arrayOf(remote.type)).use {
                    it.moveToFirst(); id = minOf(0, if (it.isNull(0)) 0 else it.getInt(0)) - 1
                }
            }
        } else {
            val data = WorkBackup.decode(checkNotNull(remote.payload))
            // A tombstone's reserved negative ID must not become a business primary key.
            if (id < 0) id = 0
            id = when (remote.type) {
                "entry" -> dao.insertEntry(data.entries.single().copy(id = id)).toInt()
                "category" -> dao.insertCategory(data.categories.single().copy(id = id)).toInt()
                "worker" -> if (id == 0) dao.insertWorker(data.workers.single()).toInt()
                    else { dao.updateWorkerName(id, data.workers.single().name); id }
                else -> error("Unsupported type")
            }
            require(id > 0)
        }
        // SQLite business triggers ran atomically. Mark only this received revision clean.
        val triggered = database.syncDao().find(remote.type, id)
        val revision = maxOf(local?.revision ?: 0, triggered?.revision ?: 0)
        if (local != null && local.localId != id) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM work_sync_journal WHERE syncId = ?", arrayOf(remote.syncId))
        }
        database.syncDao().put(WorkSyncRecord(remote.type, id, remote.syncId, revision, revision,
            remote.version, remote.deleted))
    }

    private companion object {
        val ORDER = listOf("category", "worker", "series", "exception", "entry", "preference")
        const val SEED = "עצמאי"
    }
}
