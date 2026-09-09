package com.example.data

import android.util.Log
import com.example.api.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow

class WorkRepository(private val workDao: WorkDao) {
    suspend fun importBackup(backup: WorkBackup.Contents): Int = workDao.importBackup(backup)
    val allEntries: Flow<List<WorkEntry>> = workDao.getAllEntries()
    val allCategories: Flow<List<WorkCategory>> = workDao.getAllCategories()

    suspend fun insertEntry(entry: WorkEntry, userId: String? = null): Long {
        val insertedId = workDao.insertEntry(entry)
        val finalEntry = if (entry.id == 0) entry.copy(id = insertedId.toInt()) else entry
        if (!userId.isNullOrBlank()) {
            FirestoreSyncManager.saveShift(userId, finalEntry)
        }
        return insertedId
    }

    suspend fun updateEntry(entry: WorkEntry, userId: String? = null) {
        workDao.updateEntry(entry)
        if (!userId.isNullOrBlank()) {
            FirestoreSyncManager.saveShift(userId, entry)
        }
    }

    suspend fun deleteEntry(entry: WorkEntry, userId: String? = null) {
        workDao.deleteEntry(entry)
        if (!userId.isNullOrBlank()) {
            FirestoreSyncManager.deleteShift(userId, entry.id)
        }
    }

    suspend fun deleteEntryById(id: Int, userId: String? = null) {
        workDao.deleteEntryById(id)
        if (!userId.isNullOrBlank()) {
            FirestoreSyncManager.deleteShift(userId, id)
        }
    }

    suspend fun togglePaymentStatus(entry: WorkEntry, userId: String? = null) {
        val updated = entry.copy(isPaid = !entry.isPaid)
        workDao.updateEntry(updated)
        if (!userId.isNullOrBlank()) {
            FirestoreSyncManager.updatePaymentStatus(userId, entry.id, updated.isPaid)
        }
    }

    suspend fun syncRemoteEntries(remoteEntries: List<WorkEntry>) {
        try {
            for (entry in remoteEntries) {
                workDao.insertEntry(entry)
            }
        } catch (t: Throwable) {
            Log.w("WorkRepository", "Error syncing remote entries into Room: ${t.localizedMessage}")
        }
    }

    suspend fun insertCategory(category: WorkCategory) = workDao.insertCategory(category)

    suspend fun getCategoryByName(name: String): WorkCategory? = workDao.getCategoryByName(name)

    suspend fun getCategoriesList(): List<WorkCategory> = workDao.getCategoriesList()

    suspend fun deleteCategory(category: WorkCategory) = workDao.deleteCategory(category)

    suspend fun deleteCategoryById(id: Int) = workDao.deleteCategoryById(id)

    suspend fun updateCategoryForEntries(oldName: String, newName: String) = workDao.updateCategoryForEntries(oldName, newName)

    val allWorkers: Flow<List<WorkerDirectory>> = workDao.getAllWorkers()
    
    suspend fun insertWorker(name: String) {
        val worker = WorkerDirectory(name = name)
        workDao.insertWorker(worker)
    }
}
