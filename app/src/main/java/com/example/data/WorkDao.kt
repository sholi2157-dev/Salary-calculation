package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getEntriesList(): List<WorkEntry>

    @Query("SELECT * FROM worker_directory")
    suspend fun getWorkersList(): List<WorkerDirectory>

    @Transaction
    suspend fun importBackup(backup: WorkBackup.Contents): Int {
        val categoryNames = getCategoriesList().map { it.name.trim().lowercase(java.util.Locale.ROOT) }.toMutableSet()
        for (category in backup.categories) {
            if (categoryNames.add(category.name.trim().lowercase(java.util.Locale.ROOT))) insertCategory(category.copy(id = 0))
        }
        val workerNames = getWorkersList().map { it.name.trim() }.toMutableSet()
        for (worker in backup.workers) {
            if (workerNames.add(worker.name.trim())) insertWorker(worker.copy(id = 0))
        }
        val missing = WorkBackup.missingEntries(getEntriesList(), backup.entries)
        for (entry in missing) insertEntry(entry.copy(id = 0))
        return missing.size
    }
    // Work Entries
    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<WorkEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorkEntry): Long

    @Update
    suspend fun updateEntry(entry: WorkEntry)

    @Delete
    suspend fun deleteEntry(entry: WorkEntry)

    @Query("DELETE FROM work_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("UPDATE work_entries SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryForEntries(oldName: String, newName: String)

    // Work Categories
    @Query("SELECT * FROM work_categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<WorkCategory>>

    @Query("SELECT * FROM work_categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCategoryByName(name: String): WorkCategory?

    @Query("SELECT * FROM work_categories")
    suspend fun getCategoriesList(): List<WorkCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: WorkCategory): Long

    @Delete
    suspend fun deleteCategory(category: WorkCategory)

    @Query("DELETE FROM work_categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)

    // Worker Directory
    @Query("SELECT * FROM worker_directory ORDER BY name ASC")
    fun getAllWorkers(): Flow<List<WorkerDirectory>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorker(worker: WorkerDirectory): Long
}
