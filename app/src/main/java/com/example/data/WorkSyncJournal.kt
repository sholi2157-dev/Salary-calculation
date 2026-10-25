package com.example.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Durable local outbox. SQLite triggers cover every writer, including imports and
 * the foreground service, in the same transaction as the business row.
 * This is not a cloud transport: unacknowledged work stays on the device.
 */
@Entity(
    tableName = "work_sync_journal",
    primaryKeys = ["entityType", "localId"],
    indices = [Index(value = ["syncId"], unique = true)]
)
data class WorkSyncRecord(
    val entityType: String,
    val localId: Int,
    val syncId: String,
    val revision: Long,
    val acknowledgedRevision: Long,
    val remoteVersion: Long,
    val deleted: Boolean,
    val conflictPayload: String? = null
)

/**
 * The outbox for items with a natural key (weekly plans, plan days, settings). Their syncId is derived from the key
 * (WorkSyncProtocol.keyedId), so the same plan on two devices is one document.
 */
@Entity(tableName = "work_sync_keyed", primaryKeys = ["entityType", "naturalKey"])
data class WorkSyncKeyedRecord(
    val entityType: String,
    val naturalKey: String,
    val revision: Long,
    val acknowledgedRevision: Long,
    val remoteVersion: Long,
    val deleted: Boolean,
    val conflictPayload: String? = null
)

data class WorkSyncMutation(val record: WorkSyncRecord, val payload: String?) {
    // Repeated delivery of this revision must be idempotent at the receiver.
    val operationId: String get() = "${record.syncId}:${record.revision}"
}

@Dao
interface WorkSyncDao {
    @Query("SELECT * FROM work_sync_journal WHERE syncId = :syncId")
    suspend fun bySyncId(syncId: String): WorkSyncRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(record: WorkSyncRecord)

    @Query("SELECT * FROM work_sync_journal WHERE revision > acknowledgedRevision AND conflictPayload IS NULL ORDER BY entityType, localId")
    suspend fun pending(): List<WorkSyncRecord>

    @Query("SELECT * FROM work_sync_journal WHERE entityType = :type AND localId = :localId")
    suspend fun find(type: String, localId: Int): WorkSyncRecord?

    @Query("SELECT * FROM work_sync_journal WHERE conflictPayload IS NOT NULL ORDER BY entityType, localId")
    suspend fun conflicts(): List<WorkSyncRecord>

    @Query("SELECT * FROM work_sync_journal WHERE entityType = :type AND remoteVersion = 0 AND deleted = 0 AND conflictPayload IS NULL")
    suspend fun unsent(type: String): List<WorkSyncRecord>

    @Query("UPDATE work_sync_journal SET syncId = :syncId WHERE entityType = :type AND localId = :localId")
    suspend fun adopt(type: String, localId: Int, syncId: String)

    @Query("DELETE FROM work_sync_journal WHERE syncId = :syncId")
    suspend fun forget(syncId: String)

    // ---- items with a natural key ----
    @Query("""
        UPDATE work_sync_keyed SET acknowledgedRevision = :sentRevision, remoteVersion = :serverVersion, deleted = :deleted
        WHERE entityType = :type AND naturalKey = :key AND remoteVersion = :baseVersion AND :serverVersion > remoteVersion
          AND :sentRevision > acknowledgedRevision AND :sentRevision <= revision AND conflictPayload IS NULL
    """)
    suspend fun acknowledgeKeyed(type: String, key: String, sentRevision: Long, baseVersion: Long, serverVersion: Long, deleted: Boolean): Int

    @Query("SELECT * FROM work_sync_keyed")
    suspend fun keyed(): List<WorkSyncKeyedRecord>

    @Query("SELECT * FROM work_sync_keyed WHERE entityType = :type AND naturalKey = :key")
    suspend fun keyed(type: String, key: String): WorkSyncKeyedRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKeyed(record: WorkSyncKeyedRecord)

    @Query("DELETE FROM work_sync_keyed WHERE entityType = :type AND naturalKey = :key")
    suspend fun forgetKeyed(type: String, key: String)

    /** Changes not sent yet (both outboxes), for scheduling a sync after a local change. */
    @Query("SELECT (SELECT COUNT(*) FROM work_sync_journal WHERE revision > acknowledgedRevision AND conflictPayload IS NULL) + (SELECT COUNT(*) FROM work_sync_keyed WHERE revision > acknowledgedRevision AND conflictPayload IS NULL)")
    fun observePending(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT (SELECT COUNT(*) FROM work_sync_journal WHERE conflictPayload IS NOT NULL) + (SELECT COUNT(*) FROM work_sync_keyed WHERE conflictPayload IS NOT NULL)")
    fun observeConflicts(): kotlinx.coroutines.flow.Flow<Int>

    // An acknowledgement of revision N never clears a newer local edit N+1.
    // Remote version compare-and-set also rejects a stale or reordered response.
    @Query("""
        UPDATE work_sync_journal
        SET acknowledgedRevision = :sentRevision, remoteVersion = :serverVersion
        WHERE syncId = :syncId AND remoteVersion = :baseVersion
          AND :serverVersion > remoteVersion
          AND :sentRevision > acknowledgedRevision AND :sentRevision <= revision
          AND conflictPayload IS NULL
    """)
    suspend fun acknowledge(syncId: String, sentRevision: Long, baseVersion: Long, serverVersion: Long): Int

    // Keep both versions for review; do not overwrite an unsent local edit.
    @Query("""
        UPDATE work_sync_journal SET conflictPayload = :remotePayload
        WHERE syncId = :syncId AND remoteVersion = :baseVersion
          AND revision > acknowledgedRevision AND conflictPayload IS NULL
    """)
    suspend fun recordConflict(syncId: String, baseVersion: Long, remotePayload: String): Int
}

internal object WorkSyncSchema {
    val tables = linkedMapOf(
        "entry" to "work_entries",
        "category" to "work_categories",
        "worker" to "worker_directory"
    )

    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS work_sync_journal (
            entityType TEXT NOT NULL, localId INTEGER NOT NULL, syncId TEXT NOT NULL,
            revision INTEGER NOT NULL, acknowledgedRevision INTEGER NOT NULL,
            remoteVersion INTEGER NOT NULL, deleted INTEGER NOT NULL, conflictPayload TEXT,
            PRIMARY KEY(entityType, localId))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_sync_journal_syncId ON work_sync_journal(syncId)")
        installTriggers(db)
        // One-time metadata backfill only. Existing business rows are not rewritten.
        tables.forEach { (type, table) ->
            db.execSQL("""INSERT OR IGNORE INTO work_sync_journal
                (entityType, localId, syncId, revision, acknowledgedRevision, remoteVersion, deleted, conflictPayload)
                SELECT '$type', id, lower(hex(randomblob(16))), 1, 0, 0, 0, NULL FROM $table""")
        }
    }

    /** Natural key per keyed table, as SQL over NEW/OLD. */
    private val keyedTables = linkedMapOf(
        "series" to ("shift_series" to "%s.id"),
        "exception" to ("series_exceptions" to "%s.seriesId || '@' || %s.date"),
        "preference" to ("work_local_preferences" to "%s.name")
    )

    /** 10: the keyed outbox. Existing plans and settings are queued once, so the first sync sends them. */
    fun createKeyed(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS work_sync_keyed (entityType TEXT NOT NULL, naturalKey TEXT NOT NULL,
            revision INTEGER NOT NULL, acknowledgedRevision INTEGER NOT NULL, remoteVersion INTEGER NOT NULL,
            deleted INTEGER NOT NULL, conflictPayload TEXT, PRIMARY KEY(entityType, naturalKey))""")
        installKeyedTriggers(db)
        keyedTables.forEach { (type, pair) ->
            val (table, key) = pair
            db.execSQL("""INSERT OR IGNORE INTO work_sync_keyed (entityType, naturalKey, revision, acknowledgedRevision, remoteVersion, deleted, conflictPayload)
                SELECT '$type', ${key.replace("%s.", "")}, 1, 0, 0, 0, NULL FROM $table""")
        }
    }

    fun installKeyedTriggers(db: SupportSQLiteDatabase) {
        keyedTables.forEach { (type, pair) ->
            val (table, key) = pair
            val new = key.replace("%s", "NEW"); val old = key.replace("%s", "OLD")
            val touch = """INSERT OR IGNORE INTO work_sync_keyed (entityType, naturalKey, revision, acknowledgedRevision, remoteVersion, deleted, conflictPayload)
                    VALUES ('$type', $new, 0, 0, 0, 0, NULL);
                    UPDATE work_sync_keyed SET revision = revision + 1, deleted = 0 WHERE entityType = '$type' AND naturalKey = $new;"""
            db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_${type}_insert AFTER INSERT ON $table BEGIN $touch END")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${type}_update AFTER UPDATE ON $table BEGIN
                    UPDATE work_sync_keyed SET revision = revision + 1, deleted = 1 WHERE entityType = '$type' AND naturalKey = $old AND $old <> $new;
                    $touch END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${type}_delete AFTER DELETE ON $table BEGIN
                    UPDATE work_sync_keyed SET revision = revision + 1, deleted = 1 WHERE entityType = '$type' AND naturalKey = $old;
                END""")
        }
    }

    fun installTriggers(db: SupportSQLiteDatabase) {
        tables.forEach { (type, table) ->
            // INSERT OR REPLACE may replace a category/entry with its existing ID.
            // Keep the existing stable identity and advance its revision.
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${type}_insert AFTER INSERT ON $table
                BEGIN
                    INSERT OR IGNORE INTO work_sync_journal
                    (entityType, localId, syncId, revision, acknowledgedRevision, remoteVersion, deleted, conflictPayload)
                    SELECT '$type', NEW.id, lower(hex(randomblob(16))), 0, 0, 0, 0, NULL
                    WHERE NOT EXISTS (SELECT 1 FROM work_sync_journal WHERE entityType = '$type' AND localId = NEW.id);
                    UPDATE work_sync_journal SET revision = revision + 1, deleted = 0
                    WHERE entityType = '$type' AND localId = NEW.id;
                END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${type}_update AFTER UPDATE ON $table
                BEGIN
                    UPDATE work_sync_journal SET revision = revision + 1, deleted = 0
                    WHERE entityType = '$type' AND localId = NEW.id;
                END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${type}_delete AFTER DELETE ON $table
                BEGIN
                    UPDATE work_sync_journal SET revision = revision + 1, deleted = 1
                    WHERE entityType = '$type' AND localId = OLD.id;
                END""")
        }
    }
}

data class WorkSyncKeyedMutation(val record: WorkSyncKeyedRecord, val payload: String?)

/** Same read transaction for payload and revision: never acknowledge a mixed snapshot. */
class WorkSyncJournal(private val database: WorkDatabase) {
    suspend fun pendingMutations(): List<WorkSyncMutation> = database.withTransaction {
        database.syncDao().pending().map { WorkSyncMutation(it, payload(it)) }
    }

    /** Plans, plan days and settings not sent yet. A missing row is a deletion. */
    suspend fun pendingKeyed(): List<WorkSyncKeyedMutation> = database.withTransaction {
        database.syncDao().keyed().filter { it.revision > it.acknowledgedRevision && it.conflictPayload == null }
            .sortedBy { listOf("series", "exception", "preference").indexOf(it.entityType) }
            .map { WorkSyncKeyedMutation(it, keyedPayload(it.entityType, it.naturalKey)) }
    }

    /** The item as it is here now, as a one-item backup; null when deleted. */
    suspend fun payload(record: WorkSyncRecord): String? {
        if (record.deleted || record.localId <= 0) return null
        val dao = database.workDao()
        return when (record.entityType) {
            "entry" -> dao.getEntryById(record.localId)?.let { WorkBackup.encode(emptyList(), listOf(it), emptyList()) }
            "category" -> dao.getCategoriesList().find { it.id == record.localId }?.let { WorkBackup.encode(listOf(it), emptyList(), emptyList()) }
            "worker" -> dao.getWorkersList().find { it.id == record.localId }?.let { WorkBackup.encode(emptyList(), emptyList(), listOf(it)) }
            else -> error("Unknown sync entity type")
        }
    }

    suspend fun keyedPayload(type: String, key: String): String? {
        val dao = database.workDao()
        return when (type) {
            "series" -> dao.getSeries().find { it.id == key }?.let { WorkSyncProtocol.encodeSeries(it) }
            "exception" -> dao.getExceptions().find { WorkSyncProtocol.exceptionKey(it.seriesId, it.date) == key }?.let { WorkSyncProtocol.encodeException(it) }
            "preference" -> dao.getLocalPreferences().find { it.name == key }?.let { WorkSyncProtocol.encodePreference(it.name, it.value) }
            else -> error("Not a keyed type")
        }
    }
}
