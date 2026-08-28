package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
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
