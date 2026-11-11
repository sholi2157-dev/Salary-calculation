package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    data class ImportResult(val added: Int, val skipped: Int)

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
        val current = categories.firstOrNull { it.id == category.id } ?: return false
        if (categories.size <= 1 || defaultCategory()?.id == current.id) return false
        val chosen = getLocalPreferences().firstOrNull { it.name == "defaultCategory" }?.value
        val fallback = categoryFallback(current) ?: return false
        updateCategoryForEntries(current.name, fallback.name)
        updateCategoryForSeries(current.name, fallback.name)
        if (chosen == null) setLocalPreference(WorkLocalPreference("defaultCategory", fallback.name))
        removeLocalPreference("categoryCurrency:${current.name}")
        deleteCategoryById(current.id)
        return true
    }

    /** The same effective default as Settings: the saved valid choice, otherwise the first place. */
    suspend fun defaultCategory(): WorkCategory? {
        val categories = getCategoriesList()
        val chosen = getLocalPreferences().firstOrNull { it.name == "defaultCategory" }?.value
        return categories.firstOrNull { it.name == chosen } ?: categories.minByOrNull { it.id }
    }

    /** A non-default place's history always moves to the effective default. */
    suspend fun categoryFallback(category: WorkCategory): WorkCategory? = defaultCategory()?.takeIf { it.id != category.id }

    /** One shift changed by a retroactive rate change (FB-08): what it held before and what the change wrote. */
    data class RetroChange(val id: Int, val hourlyRate: Double, val totalEarnings: Double, val wroteRate: Double, val wroteTotal: Double, val payKnown: Boolean)

    /**
     * Renames the place and sets its rate and currency. With [retro] ("שינוי רטרואקטיבי") its unpaid shifts at the previous
     * rate or without a rate take the new rate and amount in the same transaction (WorkPay.retroRate); paid shifts never change.
     * Returns the shifts it changed, so the screen can offer undo.
     */
    @Transaction
    suspend fun editCategorySafely(category: WorkCategory, name: String, rate: Double, currency: String, retro: Boolean = false): List<RetroChange> {
        require(name.isNotBlank() && rate.isFinite() && rate >= 0 && currency in listOf("₪", "$"))
        require(getCategoriesList().none { it.id != category.id && it.name.equals(name.trim(), true) })
        // The previous rate as stored now, not as the screen last saw it.
        val oldRate = getCategoriesList().firstOrNull { it.id == category.id }?.defaultRate ?: category.defaultRate
        updateCategory(category.copy(name = name.trim(), defaultRate = rate))
        updateCategoryForEntries(category.name, name.trim())
        updateCategoryForSeries(category.name, name.trim())
        if (getLocalPreferences().any { it.name == "defaultCategory" && it.value == category.name })
            setLocalPreference(WorkLocalPreference("defaultCategory", name.trim()))
        removeLocalPreference("categoryCurrency:${category.name}")
        setLocalPreference(WorkLocalPreference("categoryCurrency:${name.trim()}", currency))
        if (!retro) return emptyList()
        val rows = getEntriesList().associateBy { it.id }
        return WorkPay.retroRate(rows.values.toList(), name.trim(), oldRate, rate, currency).map { (id, total) ->
            val e = rows.getValue(id)
            updateEntry(e.copy(hourlyRate = rate, totalEarnings = total, payKnown = true))
            RetroChange(id, e.hourlyRate, e.totalEarnings, rate, total, e.payKnown)
        }
    }

    /**
     * Undo of a retroactive rate change: a shift gets its previous rate and amount back only while it still holds exactly
     * what the change wrote and is still unpaid, so a shift edited or paid since then is never touched. Returns how many.
     */
    @Transaction
    suspend fun restoreRetroRate(changes: List<RetroChange>): Int {
        var n = 0
        for (c in changes) {
            val e = getEntryById(c.id) ?: continue
            if (e.isPaid || !e.payKnown || e.hourlyRate != c.wroteRate || e.totalEarnings != c.wroteTotal) continue
            updateEntry(e.copy(hourlyRate = c.hourlyRate, totalEarnings = c.totalEarnings, payKnown = c.payKnown)); n++
        }
        return n
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
        val added = importBackup(backup).added
        insertReceipt(WorkLocalReceipt(key, added.toLong()))
        return added
    }

    @Transaction
    suspend fun exportSnapshot(): String = WorkBackup.encode(getCategoriesList(), getEntriesList(), getWorkersList(), getLocalPreferences().associate { it.name to it.value },
        getSeries(), getExceptions())

    // ---- Weekly plans (2.0 stage C) ----

    @Query("SELECT * FROM shift_series ORDER BY fromDate ASC, startTime ASC")
    suspend fun getSeries(): List<ShiftSeries>
    @Query("SELECT * FROM shift_series ORDER BY fromDate ASC, startTime ASC")
    fun observeSeries(): Flow<List<ShiftSeries>>
    @Query("SELECT * FROM series_exceptions")
    suspend fun getExceptions(): List<SeriesException>
    @Query("SELECT * FROM series_exceptions")
    fun observeExceptions(): Flow<List<SeriesException>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: ShiftSeries)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertException(exception: SeriesException)
    @Query("DELETE FROM series_exceptions WHERE seriesId = :seriesId AND date = :date")
    suspend fun deleteException(seriesId: String, date: String)
    @Query("DELETE FROM shift_series WHERE id = :id")
    suspend fun deleteSeriesRow(id: String)
    @Query("DELETE FROM series_exceptions WHERE seriesId = :id")
    suspend fun deleteExceptionsOf(id: String)
    @Query("UPDATE shift_series SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryForSeries(oldName: String, newName: String)
    @Query("SELECT seriesKey FROM work_entries WHERE seriesKey IS NOT NULL")
    suspend fun confirmedKeys(): List<String>

    /** Recording a planned occurrence never replaces anything: a key that is already recorded is simply skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlanned(entry: WorkEntry): Long

    /** All-or-nothing; running twice (background job and app at once) still records each occurrence once. */
    @Transaction
    suspend fun confirmOccurrences(entries: List<WorkEntry>): List<Long> = entries.map { insertPlanned(it) }.filter { it > 0 }

    @Transaction
    suspend fun saveSeries(series: ShiftSeries, cancelledDates: List<String>) {
        upsertSeries(series)
        cancelledDates.forEach { upsertException(SeriesException(series.id, it, cancelled = true)) }
    }

    @Transaction
    suspend fun deleteSeries(id: String) { deleteExceptionsOf(id); deleteSeriesRow(id) }

    /** Undo of an automatic recording: the rows go and those days wait for a manual "עבדתי" instead. */
    @Transaction
    suspend fun undoAutoConfirm(ids: List<Int>, onlyAuto: Boolean = true) {
        // A shift edited meanwhile is the user's own now and is never removed by an old undo.
        val rows = getEntriesList().filter { it.id in ids && it.seriesKey != null && (!onlyAuto || it.autoConfirmed) }
        val existing = getExceptions().associateBy { "${it.seriesId}@${it.date}" }
        for (row in rows) {
            val key = row.seriesKey!!
            val seriesId = key.substringBeforeLast('@'); val date = key.substringAfterLast('@')
            upsertException((existing[key] ?: SeriesException(seriesId, date)).copy(hold = true))
        }
        deleteEntriesById(rows.map { it.id })
    }

    /** A deleted recorded plan day stays skipped (it would otherwise be recorded again); undoing the delete clears that. */
    @Transaction
    suspend fun markPlanDays(keys: List<String>, skipped: Boolean) {
        val existing = getExceptions().associateBy { "${it.seriesId}@${it.date}" }
        for (key in keys) {
            val seriesId = key.substringBeforeLast('@'); val date = key.substringAfterLast('@')
            upsertException((existing[key] ?: SeriesException(seriesId, date)).copy(cancelled = skipped))
        }
    }

    @Transaction
    suspend fun applySplit(split: com.example.domain.V2Plan.Split) {
        split.dropped.forEach { deleteException(it.seriesId, it.date) }
        // Split on the first day: the old plan has no days left and goes away entirely.
        if (split.old == null) deleteSeries(split.oldId) else upsertSeries(split.old)
        upsertSeries(split.new)
        split.moved.forEach { upsertException(it) }
    }

    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getEntriesList(): List<WorkEntry>

    @Query("SELECT * FROM worker_directory")
    suspend fun getWorkersList(): List<WorkerDirectory>

    @Transaction
    suspend fun importBackup(backup: WorkBackup.Contents): ImportResult {
        val wasEmpty = getEntriesList().isEmpty()
        fun categoryKey(name: String) = name.trim().lowercase(java.util.Locale.ROOT)
        val categoryNames = getCategoriesList().associate { categoryKey(it.name) to it.name }.toMutableMap()
        for (category in backup.categories) {
            val key = categoryKey(category.name)
            if (key !in categoryNames) {
                val name = category.name.trim()
                insertCategory(category.copy(id = 0, name = name))
                categoryNames[key] = name
            }
        }
        // Entries and preferences must use the exact stored category name. Otherwise
        // case/space-equivalent imports become invisible to exact-name filters/deletion.
        val incoming = backup.entries.map { entry ->
            val key = categoryKey(entry.category)
            val name = categoryNames.getOrPut(key) { entry.category.trim() }
            entry.copy(category = name)
        }
        for (name in categoryNames.values) {
            if (getCategoryByName(name) == null) insertCategory(WorkCategory(name = name))
        }
        val workerNames = getWorkersList().map { it.name.trim() }.toMutableSet()
        for (worker in backup.workers) {
            if (workerNames.add(worker.name.trim())) insertWorker(worker.copy(id = 0))
        }
        val existing = getEntriesList()
        val missing = if (backup.fromTable) WorkTableImport.missingEntries(existing, incoming)
            else WorkBackup.missingEntries(existing, incoming)
        // insertEntry REPLACEs on a unique-key clash; an imported series key must never
        // replace a row already recorded here, so a clashing key is dropped instead.
        val seriesKeys = getEntriesList().mapNotNull { it.seriesKey }.toMutableSet()
        var added = 0
        for (entry in missing) {
            // An occurrence that is already recorded here is the same shift: it is not added a second time.
            if (entry.seriesKey != null && !seriesKeys.add(entry.seriesKey)) continue
            if (insertEntry(entry.copy(id = 0)) > 0) added++
        }
        // Weekly plans: existing ones win; a plan already here (same id) is never duplicated or overwritten.
        val knownSeries = getSeries().map { it.id }.toMutableSet()
        val addedSeries = mutableSetOf<String>()
        for (s in backup.series) if (knownSeries.add(s.id)) {
            addedSeries += s.id
            val name = categoryNames.getOrPut(categoryKey(s.category)) { s.category.trim() }
            if (getCategoryByName(name) == null) insertCategory(WorkCategory(name = name))
            upsertSeries(s.copy(category = name))
        }
        val knownExceptions = getExceptions().map { "${it.seriesId}@${it.date}" }.toMutableSet()
        // Only plans this import added take their day changes; the user's live plans stay as they are.
        for (x in backup.exceptions) if (x.seriesId in addedSeries && knownExceptions.add("${x.seriesId}@${x.date}")) upsertException(x)
        // Existing settings win; migration into an empty app can adopt reviewed preferences.
        val existingKeys = getLocalPreferences().map { it.name }.toSet()
        val adoptedKeys = existingKeys.toMutableSet()
        for ((sourceKey, sourceValue) in backup.localPreferences) {
            val key = if (sourceKey.startsWith("categoryCurrency:")) {
                val name = categoryNames[categoryKey(sourceKey.removePrefix("categoryCurrency:"))] ?: continue
                "categoryCurrency:$name"
            } else sourceKey
            val value = if (key == "defaultCategory") categoryNames[categoryKey(sourceValue)] ?: continue else sourceValue
            if ((wasEmpty || key.startsWith("categoryCurrency:")) && adoptedKeys.add(key))
                setLocalPreference(WorkLocalPreference(key, value))
        }
        return ImportResult(added = added, skipped = incoming.size - added)
    }
    // Work Entries
    @Query("SELECT * FROM work_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<WorkEntry>>

    @Query("SELECT * FROM work_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): WorkEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorkEntry): Long

    @Transaction
    suspend fun insertEntriesAtomically(entries: List<WorkEntry>): List<Long> = entries.map { insertEntry(it) }

    @Update
    suspend fun updateEntry(entry: WorkEntry)

    @Delete
    suspend fun deleteEntry(entry: WorkEntry)

    @Query("UPDATE work_entries SET isPaid = :paid WHERE id = :id")
    suspend fun setPaymentStatus(id: Int, paid: Boolean)

    @Query("UPDATE work_entries SET isPaid = :paid WHERE id IN (:ids)")
    suspend fun setPaymentStatusFor(ids: List<Int>, paid: Boolean)

    /**
     * Bulk rate completion: writes only to selected shifts whose rate is still missing.
     * Known amounts and fixed-price jobs are never touched. Returns how many were completed.
     */
    @Transaction
    suspend fun completeMissingRates(ids: List<Int>, rate: Double): Int {
        require(rate.isFinite() && rate >= 0)
        var completed = 0
        for (id in ids.distinct()) {
            val entry = getEntryById(id) ?: continue
            if (entry.payKnown || entry.kind == WorkEntry.KIND_JOB) continue
            updateEntry(entry.copy(hourlyRate = rate, payKnown = true, totalEarnings = WorkPay.money(entry.hours, rate, entry.bonus)))
            completed++
        }
        return completed
    }

    @Query("DELETE FROM work_entries WHERE id IN (:ids)")
    suspend fun deleteEntriesById(ids: List<Int>)

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
