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

data class WorkSyncMutation(val record: WorkSyncRecord, val payload: String?) {
    // Repeated delivery of this revision must be idempotent at the receiver.
    val operationId: String get() = "${record.syncId}:${record.revision}"
}

@Dao
interface WorkSyncDao {
    @Query("SELECT * FROM work_sync_journal WHERE revision > acknowledgedRevision AND conflictPayload IS NULL ORDER BY entityType, localId")
    suspend fun pending(): List<WorkSyncRecord>

    @Query("SELECT * FROM work_sync_journal WHERE entityType = :type AND localId = :localId")
    suspend fun find(type: String, localId: Int): WorkSyncRecord?

    @Query("SELECT * FROM work_sync_journal WHERE conflictPayload IS NOT NULL ORDER BY entityType, localId")
    suspend fun conflicts(): List<WorkSyncRecord>

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

/** Same read transaction for payload and revision: never acknowledge a mixed snapshot. */
class WorkSyncJournal(private val database: WorkDatabase) {
    suspend fun pendingMutations(): List<WorkSyncMutation> = database.withTransaction {
        val dao = database.workDao()
        database.syncDao().pending().map { record ->
            val payload = if (record.deleted) null else when (record.entityType) {
                "entry" -> WorkBackup.encode(emptyList(),
                    listOf(checkNotNull(dao.getEntryById(record.localId))), emptyList())
                "category" -> WorkBackup.encode(
                    listOf(checkNotNull(dao.getCategoriesList().find { it.id == record.localId })),
                    emptyList(), emptyList())
                "worker" -> WorkBackup.encode(emptyList(), emptyList(),
                    listOf(checkNotNull(dao.getWorkersList().find { it.id == record.localId })))
                else -> error("Unknown sync entity type")
            }
            WorkSyncMutation(record, payload)
        }
    }
}
