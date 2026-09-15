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
    fun validate(): WorkRemoteRecord {
        require(syncId.matches(Regex("[a-f0-9]{32}")))
        require(type in setOf("entry", "category", "worker") && version > 0)
        require(operation.isNotBlank() && operation.length <= 256)
        if (deleted) require(payload == null) else {
            require(payload != null && payload.toByteArray().size <= 500_000)
            val decoded = WorkBackup.decode(payload)
            require(decoded.entries.size == if (type == "entry") 1 else 0)
            require(decoded.categories.size == if (type == "category") 1 else 0)
            require(decoded.workers.size == if (type == "worker") 1 else 0)
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

interface WorkSyncTransport {
    suspend fun readAll(uid: String): List<WorkRemoteRecord>
    /** Atomic compare-and-set. Returns the accepted record or the current conflict. */
    suspend fun exchange(uid: String, baseVersion: Long, proposed: WorkRemoteRecord): WorkRemoteRecord
}

/** One engine per immutable owner. A failed run leaves the durable outbox intact. */
class WorkSyncEngine(
    private val database: WorkDatabase,
    private val uid: String,
    private val deviceId: String,
    private val transport: WorkSyncTransport,
    private val currentUid: () -> String?
) {
    private val mutex = Mutex()
    init { require(uid.isNotBlank() && deviceId.isNotBlank()) }
    private fun checkOwner() { check(currentUid() == uid) { "Account changed" } }
    private fun operation(m: WorkSyncMutation): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$deviceId|${m.record.remoteVersion}|${m.record.revision}|${m.record.deleted}|${m.payload}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "${m.record.syncId}:$digest"
    }

    suspend fun synchronize(): Int = mutex.withLock {
        checkOwner()
        // Push first: a lost acknowledgement is retried with the same operation ID.
        for (m in WorkSyncJournal(database).pendingMutations()) {
            checkOwner()
            val r = m.record
            val proposed = WorkRemoteRecord(r.syncId, r.entityType, r.remoteVersion + 1,
                operation(m), r.deleted, m.payload).validate()
            val accepted = transport.exchange(uid, r.remoteVersion, proposed).validate()
            checkOwner()
            require(accepted.syncId == r.syncId && accepted.type == r.entityType)
            if (accepted.operation == proposed.operation) {
                database.syncDao().acknowledge(r.syncId, r.revision, r.remoteVersion, accepted.version)
            } else receive(accepted)
        }
        checkOwner()
        val remote = transport.readAll(uid)
        checkOwner()
        for (r in remote) { checkOwner(); receive(r.validate()) }
        database.syncDao().conflicts().size
    }

    private suspend fun receive(remote: WorkRemoteRecord) = database.withTransaction {
        val local = database.syncDao().bySyncId(remote.syncId)
        if (local != null) {
            require(local.entityType == remote.type)
            if (remote.version <= local.remoteVersion) return@withTransaction
            if (local.revision > local.acknowledgedRevision) {
                // Refresh the remote candidate without touching any local business data.
                database.syncDao().put(local.copy(conflictPayload = remote.encode()))
                return@withTransaction
            }
        }
        applyRemote(remote, local)
    }

    /** User chooses one side explicitly; both sides remain intact until this transaction. */
    suspend fun resolve(syncId: String, keepLocal: Boolean) = mutex.withLock {
        checkOwner()
        database.withTransaction {
            val local = checkNotNull(database.syncDao().bySyncId(syncId))
            val remote = WorkRemoteRecord.decode(checkNotNull(local.conflictPayload))
            if (keepLocal) database.syncDao().put(local.copy(remoteVersion = remote.version,
                revision = local.revision + 1, conflictPayload = null))
            else applyRemote(remote, local)
        }
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
}
