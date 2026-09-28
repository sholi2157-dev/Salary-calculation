package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_local_preferences")
    fun observeLocalPreferences(): Flow<List<WorkLocalPreference>>
    @Query("SELECT * FROM work_local_preferences")
    suspend fun getLocalPreferences(): List<WorkLocalPreference>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLocalPreference(preference: WorkLocalPreference)
    @Query("DELETE FROM work_local_preferences WHERE name = :name")
    suspend fun removeLocalPreference(name: String)
    @Update
    suspend fun updateCategory(category: WorkCategory)

    @Transaction
    suspend fun removeCategorySafely(category: WorkCategory): Boolean {
        val categories = getCategoriesList()
        if (categories.none { it.id == category.id } || categories.size <= 1) return false
        val chosen = getLocalPreferences().firstOrNull { it.name == "defaultCategory" }?.value
        val fallback = categories.firstOrNull { it.name == chosen && it.id != category.id }
            ?: categories.first { it.id != category.id }
        updateCategoryForEntries(category.name, fallback.name)
        if (chosen == category.name || chosen == null) setLocalPreference(WorkLocalPreference("defaultCategory", fallback.name))
        removeLocalPreference("categoryCurrency:${category.name}")
        deleteCategoryById(category.id)
        return true
    }

    @Transaction
    suspend fun editCategorySafely(category: WorkCategory, name: String, rate: Double, currency: String) {
        require(name.isNotBlank() && rate.isFinite() && rate >= 0 && currency in listOf("₪", "$"))
        require(getCategoriesList().none { it.id != category.id && it.name.equals(name.trim(), true) })
        updateCategory(category.copy(name = name.trim(), defaultRate = rate))
        updateCategoryForEntries(category.name, name.trim())
        if (getLocalPreferences().any { it.name == "defaultCategory" && it.value == category.name })
            setLocalPreference(WorkLocalPreference("defaultCategory", name.trim()))
        removeLocalPreference("categoryCurrency:${category.name}")
        setLocalPreference(WorkLocalPreference("categoryCurrency:${name.trim()}", currency))
    }

    @Query("SELECT * FROM work_local_receipts WHERE operation = :operation")
    suspend fun receipt(operation: String): WorkLocalReceipt?

    @Insert
    suspend fun insertReceipt(receipt: WorkLocalReceipt)

    @Transaction
    suspend fun finishTimer(startTime: Long, entry: WorkEntry?): Long {
        val key = "timer:$startTime"
        receipt(key)?.let { return it.result }
        val id = entry?.let { insertEntry(it) } ?: 0L
        insertReceipt(WorkLocalReceipt(key, id))
        return id
    }

    @Transaction
    suspend fun adoptLegacy(backup: WorkBackup.Contents): Int {
        val key = "legacy-guest-v1"
        if (receipt(key) != null) return 0
        val added = importBackup(backup)
        insertReceipt(WorkLocalReceipt(key, added.toLong()))
        return added
    }

    @Transaction
    suspend fun exportSnapshot(): String = WorkBackup.encode(getCategoriesList(), getEntriesList(), getWorkersList(), getLocalPreferences().associate { it.name to it.value })

    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getEntriesList(): List<WorkEntry>

    @Query("SELECT * FROM worker_directory")
    suspend fun getWorkersList(): List<WorkerDirectory>

    @Transaction
    suspend fun importBackup(backup: WorkBackup.Contents): Int {
        val wasEmpty = getEntriesList().isEmpty()
        val categoryNames = getCategoriesList().map { it.name.trim().lowercase(java.util.Locale.ROOT) }.toMutableSet()
        for (category in backup.categories) {
            if (categoryNames.add(category.name.trim().lowercase(java.util.Locale.ROOT))) insertCategory(category.copy(id = 0))
        }
        val workerNames = getWorkersList().map { it.name.trim() }.toMutableSet()
        for (worker in backup.workers) {
            if (workerNames.add(worker.name.trim())) insertWorker(worker.copy(id = 0))
        }
        val missing = WorkBackup.missingEntries(getEntriesList(), backup.entries)
        for (entry in missing) {
            if (categoryNames.add(entry.category.trim().lowercase(java.util.Locale.ROOT))) insertCategory(WorkCategory(name = entry.category))
            insertEntry(entry.copy(id = 0))
        }
        // Existing settings win; migration into an empty app can adopt reviewed preferences.
        val existingKeys = getLocalPreferences().map { it.name }.toSet()
        for ((key, value) in backup.localPreferences) {
            if (key !in existingKeys && (wasEmpty || key.startsWith("categoryCurrency:")))
                setLocalPreference(WorkLocalPreference(key, value))
        }
        return missing.size
    }
    // Work Entries
    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<WorkEntry>>

    @Query("SELECT * FROM work_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): WorkEntry?

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

    @Query("DELETE FROM worker_directory WHERE id = :id")
    suspend fun deleteWorkerById(id: Int)

    @Query("UPDATE worker_directory SET name = :name WHERE id = :id")
    suspend fun updateWorkerName(id: Int, name: String)
}
