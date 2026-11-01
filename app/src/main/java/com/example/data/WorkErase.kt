package com.example.data

import androidx.room.withTransaction

/** The local part of the two M-13 delete actions (same rules as web/sync/erase.js). */
object WorkErase {
    const val STARTER = "עצמאי"

    /**
     * Empties this database's work data in one transaction, as a new install starts (with the starter place).
     * [forgetSync] true ("מחיקה מהמכשיר הזה"): the outbox goes in the same transaction, so the deletions can never reach
     * the account; the caller also forgets the read cursor so the next sign-in downloads everything again.
     * false ("מחיקה מהחשבון"): the deletions stay in the outbox and are sent as ordinary tombstones.
     */
    suspend fun wipeLocal(db: WorkDatabase, forgetSync: Boolean, starterRate: Double = 40.0) = db.withTransaction {
        val w = db.workDao()
        w.deleteEntriesById(w.getEntriesList().map { it.id })
        w.getSeries().forEach { w.deleteSeries(it.id) }
        w.getWorkersList().forEach { w.deleteWorkerById(it.id) }
        w.getCategoriesList().forEach { w.deleteCategoryById(it.id) }
        w.getLocalPreferences().forEach { w.removeLocalPreference(it.name) }
        if (forgetSync) db.openHelper.writableDatabase.apply { execSQL("DELETE FROM work_sync_journal"); execSQL("DELETE FROM work_sync_keyed") }
        w.insertCategory(WorkCategory(name = STARTER, defaultRate = starterRate))
    }
}
