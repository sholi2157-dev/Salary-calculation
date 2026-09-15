package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WorkCategory
import com.example.data.WorkDatabase
import com.example.data.WorkEntry
import com.example.data.WorkRepository
import com.example.data.WorkerDirectory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.ShiftStateManager
import com.example.ShiftForegroundService

class WorkViewModel(
    application: Application,
    private val repository: WorkRepository,
    val owner: com.example.data.WorkAccountScope = com.example.data.WorkAccountScope(null)
) : AndroidViewModel(application) {
    private val settings = com.example.data.WorkAccountPreferences.get(application, owner)
    private val shiftState = ShiftStateManager.forAccount(application, owner)

    companion object {
        const val DEFAULT_RATE = 40.0
        const val APP_NAME = "שכר עבודות אישי"
    }

    private val SERVICE_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("service_notification_enabled")
    private val DEFAULT_CURRENCY_KEY = stringPreferencesKey("default_currency")

    val defaultCurrency: StateFlow<String> = settings.data
        .map { preferences ->
            preferences[DEFAULT_CURRENCY_KEY] ?: "₪"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "₪"
        )

    val serviceNotificationEnabled: StateFlow<Boolean> = settings.data
        .map { preferences ->
            preferences[SERVICE_NOTIFICATION_ENABLED_KEY] ?: true // Default ON
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    var lastAddedEntryIds: List<Int> = emptyList()
    var lastAddedEntryId: Int? = null

    private fun getActiveUserId(): String? {
        // Enable cloud writes only after account isolation and migration are verified.
        if (!com.example.BuildConfig.CLOUD_SYNC_ENABLED) return null
        return owner.uid?.takeIf { it == com.example.api.AuthManager.currentUser.value?.uid }
    }

    fun undoLastAddedEntry() {
        val ids = if (lastAddedEntryIds.isNotEmpty()) lastAddedEntryIds else listOfNotNull(lastAddedEntryId)
        if (ids.isNotEmpty()) {
            val uid = getActiveUserId()
            viewModelScope.launch {
                ids.forEach { id ->
                    repository.deleteEntryById(id, uid)
                }
                lastAddedEntryIds = emptyList()
                lastAddedEntryId = null
            }
        }
    }

    suspend fun getCategoriesList(): List<WorkCategory> {
        return repository.getCategoriesList()
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            settings.edit { preferences ->
                preferences[DEFAULT_CURRENCY_KEY] = currency
            }
        }
    }

    fun updateServiceNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.edit { preferences ->
                preferences[SERVICE_NOTIFICATION_ENABLED_KEY] = enabled
            }
            if (ShiftStateManager.activeOwner(getApplication()) != owner) return@launch
            if (!enabled) {
                // Immediately stop foreground service if active
                val intent = Intent(getApplication(), ShiftForegroundService::class.java)
                getApplication<Application>().stopService(intent)
            } else {
                // If enabled and shift is active, start the service
                val activeStart = activeShiftStartTime.value
                val category = activeShiftCategory.value
                val rate = activeShiftRate.value
                if (activeStart != null) {
                    val intent = Intent(getApplication(), ShiftForegroundService::class.java).apply {
                        action = "START"
                        putExtra(ShiftForegroundService.EXTRA_OWNER_UID, owner.uid)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        getApplication<Application>().startForegroundService(intent)
                    } else {
                        getApplication<Application>().startService(intent)
                    }
                }
            }
        }
    }

    fun updateDefaultCurrency(currency: String) {
        viewModelScope.launch {
            settings.edit { preferences ->
                preferences[DEFAULT_CURRENCY_KEY] = currency
            }
        }
    }

    fun performAutoBackup() {
        viewModelScope.launch {
            try {
                val json = repository.exportSnapshot()
                if (json.isEmpty()) return@launch
                
                val context = getApplication<Application>()
                val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: File(context.filesDir, "Documents")
                val backupDir = if (owner.uid == null) root else File(root, owner.storageKey)
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFile = File(backupDir, "backup_work_entries_${timeStamp}_${java.util.UUID.randomUUID()}.json")
                backupFile.writeText(json)
                android.util.Log.d("WorkViewModel", "Auto-backup saved successfully to: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Auto-backup failed: ${e.localizedMessage}")
            }
        }
    }

    val runCountAnimationTrigger = androidx.compose.runtime.mutableStateOf(0)

    val currentUserSession: StateFlow<com.example.api.AuthManager.UserSession?> = com.example.api.AuthManager.currentUser
    private var cloudListener: com.google.firebase.firestore.ListenerRegistration? = null
    val cloudStatus = kotlinx.coroutines.flow.MutableStateFlow("סנכרון הענן עדיין אינו פעיל")
    private val versionedSync by lazy {
        val uid = owner.uid ?: return@lazy null
        val auth = com.example.api.AuthManager.getFirebaseAuthSafely() ?: return@lazy null
        val firestore = com.example.api.FirestoreSyncManager.getFirestoreSafely() ?: return@lazy null
        val prefs = application.getSharedPreferences("sync_device_identity", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("id", null) ?: java.util.UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit())
        }
        com.example.data.WorkSyncEngine(owner.database(application), uid, deviceId,
            com.example.api.WorkFirestoreTransport(firestore, auth)) { auth.currentUser?.uid }
    }

    fun syncNow() {
        if (!com.example.BuildConfig.VERSIONED_SYNC_ENABLED || owner.uid == null) return
        viewModelScope.launch { performSync() }
    }

    private suspend fun performSync() {
        if (currentUserSession.value?.uid != owner.uid || owner.uid == null) return
        try {
            cloudStatus.value = "מסנכרן…"
            val count = versionedSync?.synchronize() ?: return
            cloudStatus.value = if (count == 0) "הסנכרון הושלם" else "$count שינויים דורשים בחירה"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { cloudStatus.value = "ממתין לחיבור. השינויים נשמרו במכשיר" }
    }

    fun reviewSyncConflicts(context: Context) {
        if (!com.example.BuildConfig.VERSIONED_SYNC_ENABLED || currentUserSession.value?.uid != owner.uid) return
        viewModelScope.launch {
            val row = owner.database(getApplication()).syncDao().conflicts().firstOrNull()
            if (row == null) { Toast.makeText(context, "אין שינויים מתנגשים", Toast.LENGTH_SHORT).show(); return@launch }
            val remote = com.example.data.WorkRemoteRecord.decode(checkNotNull(row.conflictPayload))
            val localDescription = when (row.entityType) {
                "entry" -> owner.database(getApplication()).workDao().getEntryById(row.localId)?.let {
                    "${it.category} · ${it.totalEarnings} ${it.currency}\n${it.notes}"
                } ?: "נמחק במכשיר"
                else -> "${row.entityType} #${row.localId}"
            }
            android.app.AlertDialog.Builder(context).setTitle("בחירת גרסה לשמירה")
                .setMessage("במכשיר:\n$localDescription\n\nבענן:\n${if (remote.deleted) "הרשומה נמחקה" else remote.payload}")
                .setNeutralButton("מאוחר יותר", null)
                .setNegativeButton("שמור מהמכשיר") { _, _ -> viewModelScope.launch {
                    versionedSync?.resolve(row.syncId, true); performSync()
                } }
                .setPositiveButton("קבל מהענן") { _, _ -> viewModelScope.launch {
                    versionedSync?.resolve(row.syncId, false); performSync()
                } }.show()
        }
    }

    fun signOut(context: Context): Boolean {
        if (com.example.api.AuthManager.currentUser.value?.uid != owner.uid) return false
        if (ShiftStateManager.hasActiveShift(context)) {
            Toast.makeText(context, "יש לסיים את המשמרת הפעילה לפני החלפת חשבון", Toast.LENGTH_LONG).show()
            return false
        }
        com.example.api.AuthManager.signOut(context)
        return true
    }

    init {
        try {
            com.example.api.FirebaseSafeInitializer.init(application)
            com.example.api.AuthManager.init(application)
            com.example.api.FirestoreSyncManager.init(application)
        } catch (e: Throwable) {
            android.util.Log.w("WorkViewModel", "FirebaseSafeInitializer / AuthManager / FirestoreSyncManager failed: ${e.localizedMessage}")
        }
        runCountAnimationTrigger.value = runCountAnimationTrigger.value + 1

        if (com.example.BuildConfig.VERSIONED_SYNC_ENABLED && owner.uid != null) {
            viewModelScope.launch {
                while (true) {
                    performSync()
                    kotlinx.coroutines.delay(60_000)
                }
            }
        }

        // Subscribe to real-time Firestore snapshots for user
        viewModelScope.launch {
            currentUserSession.collect { session ->
                cloudListener?.remove()
                cloudListener = null
                val uid = if (com.example.BuildConfig.CLOUD_SYNC_ENABLED && session?.uid == owner.uid) owner.uid else null
                if (!uid.isNullOrBlank()) {
                    cloudListener = com.example.api.FirestoreSyncManager.listenToUserShifts(
                        userId = uid,
                        onShiftsChanged = { remoteList ->
                            if (remoteList.isNotEmpty()) {
                                viewModelScope.launch {
                                    if (getActiveUserId() == uid) repository.syncRemoteEntries(remoteList)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        cloudListener?.remove()
        cloudListener = null
        super.onCleared()
    }

    // Observe Room DB entities
    val entries: StateFlow<List<WorkEntry>> = repository.allEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<WorkCategory>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val workersDirectory: StateFlow<List<WorkerDirectory>> = repository.allWorkers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    data class GroupWorkerState(
        val name: String = "",
        val hours: Double = 0.0,
        val isPaid: Boolean = false
    )

    fun parseGroupWorkers(json: String): List<GroupWorkerState> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<GroupWorkerState>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    GroupWorkerState(
                        name = obj.optString("name", ""),
                        hours = obj.optDouble("hours", 0.0),
                        isPaid = obj.optBoolean("isPaid", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun stringifyGroupWorkers(workers: List<GroupWorkerState>): String {
        val arr = JSONArray()
        for (w in workers) {
            val obj = JSONObject()
            obj.put("name", w.name)
            obj.put("hours", w.hours)
            obj.put("isPaid", w.isPaid)
            arr.put(obj)
        }
        return arr.toString()
    }

    // Active Shift Tracker Properties - routed to persistent state manager
    val activeShiftStartTime: StateFlow<Long?> = shiftState.activeShiftStartTime
    val activeShiftCategory: StateFlow<String> = shiftState.activeShiftCategory
    val activeShiftRate: StateFlow<Double> = shiftState.activeShiftRate

    fun startActiveShiftWithSavedRate(category: String) {
        viewModelScope.launch {
            val rate = repository.getCategoryByName(category)?.defaultRate ?: DEFAULT_RATE
            startActiveShift(category, rate)
        }
    }

    fun startActiveShift(category: String, rate: Double) {
        if (com.example.api.AuthManager.currentUser.value?.uid != owner.uid) return
        val startTime = System.currentTimeMillis()
        if (!shiftState.start(category, rate, startTime, defaultCurrency.value)) {
            Toast.makeText(getApplication(), "כבר קיימת משמרת פעילה. יש לסיים אותה תחילה", Toast.LENGTH_LONG).show()
            return
        }
        
        // Start Foreground Service only if enabled
        if (serviceNotificationEnabled.value) {
            val intent = Intent(getApplication(), ShiftForegroundService::class.java).apply {
                action = "START"
                putExtra(ShiftForegroundService.EXTRA_OWNER_UID, owner.uid)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        }
    }

    fun stopActiveShift() {
        finishActiveShift(save = false)
    }

    fun finishActiveShift(save: Boolean = true) {
        val start = activeShiftStartTime.value ?: return
        val rate = activeShiftRate.value
        val category = activeShiftCategory.value
        val currency = shiftState.activeShiftCurrency.value
        val elapsed = ((System.currentTimeMillis() / 1000L) - (start / 1000L)).coerceAtLeast(0L)
        viewModelScope.launch {
            try {
                val entry = if (save) WorkEntry(category = category, date = start, isTimeRange = false,
                    hours = elapsed / 3600.0, hourlyRate = rate,
                    totalEarnings = Math.round(elapsed * rate / 3600.0 * 100.0) / 100.0,
                    notes = "משמרת פעילה (טיימר החישוב)", currency = currency) else null
                val id = owner.database(getApplication()).workDao().finishTimer(start, entry)
                if (id > 0) lastAddedEntryId = id.toInt()
                if (shiftState.clear(start)) getApplication<Application>().stopService(
                    Intent(getApplication(), ShiftForegroundService::class.java))
                performAutoBackup()
            } catch (_: Exception) {
                Toast.makeText(getApplication(), "המשמרת לא נשמרה. הטיימר נשאר פעיל; אפשר לנסות שוב", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun reviewLegacyData(context: Context) {
        if (owner.uid == null) return
        viewModelScope.launch {
            val target = owner.database(context)
            if (target.workDao().receipt("legacy-guest-v1") != null) {
                Toast.makeText(context, "הנתונים המקומיים כבר הועתקו לחשבון זה", Toast.LENGTH_LONG).show()
                return@launch
            }
            val reviewed = com.example.data.WorkLegacyAdoption.snapshot(com.example.data.WorkAccountScope(null).database(context))
            val totals = reviewed.entries.groupBy { it.currency }.map { (currency, rows) ->
                "$currency: ${String.format(Locale.US, "%.2f", rows.sumOf { it.totalEarnings })}"
            }.joinToString("\n")
            android.app.AlertDialog.Builder(context)
                .setTitle("העתקת נתונים מקומיים לחשבון")
                .setMessage("${reviewed.entries.size} משמרות, ${reviewed.categories.size} קטגוריות\n$totals\nהמקור המקומי יישאר ללא שינוי. זהו עותק חד־פעמי לחשבון זה בלבד; אין סנכרון ענן פעיל.")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("אישור העתקה") { _, _ ->
                    if (com.example.api.AuthManager.currentUser.value?.uid != owner.uid) return@setPositiveButton
                    viewModelScope.launch {
                        try {
                            val added = com.example.data.WorkLegacyAdoption.confirm(target, reviewed)
                            Toast.makeText(context, "הועתקו $added משמרות", Toast.LENGTH_LONG).show()
                            performAutoBackup()
                        } catch (_: Exception) {
                            Toast.makeText(context, "ההעתקה לא הושלמה. נתוני המקור נשמרו", Toast.LENGTH_LONG).show()
                        }
                    }
                }.show()
        }
    }

    // Data structure for UI financial statistics
    data class PeriodStats(
        val label: String,
        val totalHours: Double,
        val totalEarnings: Double,
        val paidHours: Double,
        val paidEarnings: Double,
        val unpaidHours: Double,
        val unpaidEarnings: Double
    )

    // Reactive stats calculation covering: Today, This Week, This Month, and Total
    val stats: StateFlow<StatsSummary> = entries.map { entryList ->
        calculateStats(entryList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsSummary()
    )

    data class StatsSummary(
        val today: PeriodStats = PeriodStats("היום", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        val thisWeek: PeriodStats = PeriodStats("השבוע", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        val thisMonth: PeriodStats = PeriodStats("החודש", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        val total: PeriodStats = PeriodStats("סה\"כ", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    )

    // Work Entry Operations
    fun addEntry(
        category: String,
        dateMillis: Long,
        isTimeRange: Boolean,
        startTime: String?,
        endTime: String?,
        hours: Double,
        rate: Double,
        notes: String,
        isPaid: Boolean = false,
        isGroupShift: Boolean = false,
        employerRate: Double? = null,
        workerRate: Double? = null,
        groupWorkersJson: String = "",
        currency: String = defaultCurrency.value
    ) {
        viewModelScope.launch {
            val finalHours = if (isTimeRange) {
                calculateHoursDiff(startTime ?: "00:00", endTime ?: "00:00")
            } else {
                hours
            }
            val entry = WorkEntry(
                category = category,
                date = dateMillis,
                isTimeRange = isTimeRange,
                startTime = startTime,
                endTime = endTime,
                hours = finalHours,
                hourlyRate = rate,
                totalEarnings = Math.round((finalHours * rate) * 100.0) / 100.0,
                isPaid = isPaid,
                notes = notes,
                isGroupShift = isGroupShift,
                employerRate = employerRate,
                workerRate = workerRate,
                groupWorkersJson = groupWorkersJson,
                currency = currency
            )
            val uid = getActiveUserId()
            val insertedId = repository.insertEntry(entry, uid)
            lastAddedEntryId = insertedId.toInt()
            
            performAutoBackup()
        }
    }

    fun addShifts(shifts: List<com.example.api.GeminiParser.ParsedShift>) {
        if (shifts.isEmpty()) return
        viewModelScope.launch {
            try {
                val uid = getActiveUserId()
                val insertedIds = mutableListOf<Int>()
                for (shift in shifts) {
                    val groupJson = if (shift.isGroup && shift.groupMembers.isNotEmpty()) {
                        val groupWorkersList = shift.groupMembers.map { GroupWorkerState(name = it, hours = shift.hours, isPaid = false) }
                        stringifyGroupWorkers(groupWorkersList)
                    } else {
                        ""
                    }
                    val entry = WorkEntry(
                        category = shift.category,
                        date = shift.date,
                        isTimeRange = false,
                        startTime = null,
                        endTime = null,
                        hours = shift.hours,
                        hourlyRate = shift.hourlyRate,
                        totalEarnings = Math.round((shift.hours * shift.hourlyRate) * 100.0) / 100.0,
                        isPaid = false,
                        notes = shift.notes,
                        isGroupShift = shift.isGroup,
                        employerRate = if (shift.isGroup) shift.hourlyRate else null,
                        workerRate = if (shift.isGroup) shift.hourlyRate else null,
                        groupWorkersJson = groupJson,
                        currency = shift.currency
                    )
                    val insertedId = repository.insertEntry(entry, uid)
                    insertedIds.add(insertedId.toInt())
                }
                lastAddedEntryIds = insertedIds
                lastAddedEntryId = insertedIds.lastOrNull()

                performAutoBackup()
                runCountAnimationTrigger.value = runCountAnimationTrigger.value + 1
            } catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Failed to add shifts via AI bulk parser: ${e.localizedMessage}")
            }
        }
    }

    fun addShift(shift: com.example.api.GeminiParser.ParsedShift) {
        addShifts(listOf(shift))
    }

    fun editEntry(
        id: Int,
        category: String,
        dateMillis: Long,
        isTimeRange: Boolean,
        startTime: String?,
        endTime: String?,
        hours: Double,
        rate: Double,
        notes: String,
        isPaid: Boolean,
        isGroupShift: Boolean = false,
        employerRate: Double? = null,
        workerRate: Double? = null,
        groupWorkersJson: String = "",
        currency: String = "₪"
    ) {
        viewModelScope.launch {
            if (!hours.isFinite() || hours <= 0.0 || !rate.isFinite() || rate < 0.0 ||
                (employerRate != null && (!employerRate.isFinite() || employerRate < 0.0)) ||
                (workerRate != null && (!workerRate.isFinite() || workerRate < 0.0))) return@launch
            val original = repository.getEntryById(id) ?: return@launch
            val proposed = original.copy(
                category = category,
                date = dateMillis,
                isTimeRange = isTimeRange,
                startTime = startTime,
                endTime = endTime,
                hours = hours,
                hourlyRate = rate,
                isPaid = isPaid,
                notes = notes,
                isGroupShift = isGroupShift,
                employerRate = employerRate,
                workerRate = workerRate,
                groupWorkersJson = groupWorkersJson,
                currency = currency
            )
            val uid = getActiveUserId()
            repository.updateEntry(com.example.data.WorkEntryEdits.apply(original, proposed), uid)
            
            performAutoBackup()
        }
    }

    fun deleteEntry(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.deleteEntry(entry, uid)
        }
    }

    fun restoreEntry(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.insertEntry(entry, uid)
            performAutoBackup()
        }
    }

    fun updateEntryDirect(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.updateEntry(entry, uid)
            performAutoBackup()
        }
    }

    fun togglePaymentStatus(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.togglePaymentStatus(entry, uid)
        }
    }

    // Category Operations
    fun addCategory(name: String, defaultRate: Double = 40.0) {
        if (name.isBlank()) return
        viewModelScope.launch {
            // Check for duplicates
            val currentList = categories.value
            val exists = currentList.any { it.name.trim().lowercase() == name.trim().lowercase() }
            if (!exists) {
                repository.insertCategory(WorkCategory(name = name.trim(), defaultRate = defaultRate))
            }
        }
    }

    fun deleteCategory(category: WorkCategory) {
        viewModelScope.launch {
            // First check if 'עצמאי' exists, if not, create it
            val currentList = categories.value
            val fallbackName = "עצמאי"
            val fallbackExists = currentList.any { it.name.trim().lowercase() == fallbackName.lowercase() }
            if (!fallbackExists) {
                repository.insertCategory(WorkCategory(name = fallbackName, defaultRate = 40.0))
            }
            
            // Re-assign orphaned shifts to fallback category
            if (category.name != fallbackName) {
                repository.updateCategoryForEntries(oldName = category.name, newName = fallbackName)
            }
            
            // Now delete the category
            repository.deleteCategoryById(category.id)
        }
    }

    fun updateCategoryRate(category: WorkCategory, newRate: Double) {
        viewModelScope.launch {
            repository.insertCategory(category.copy(defaultRate = newRate))
        }
    }

    // Helper functions for hours & stats parsing
    fun calculateHoursDiff(start: String, end: String): Double {
        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startDate = sdf.parse(start) ?: return 0.0
            val endDate = sdf.parse(end) ?: return 0.0
            
            var diffMs = endDate.time - startDate.time
            if (diffMs < 0) {
                // Handle night shifts crossing midnight (e.g. 22:00 to 02:00)
                diffMs += 24 * 60 * 60 * 1000 // Add 24 hours in milliseconds
            }
            return diffMs.toDouble() / (60 * 60 * 1000)
        } catch (e: Exception) {
            return 0.0
        }
    }

    private fun calculateStats(entryList: List<WorkEntry>): StatsSummary {
        val now = Calendar.getInstance()
        val todayYear = now.get(Calendar.YEAR)
        val todayMonth = now.get(Calendar.MONTH)
        val todayDay = now.get(Calendar.DAY_OF_MONTH)

        // Set start of the current week (Sunday)
        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
        val endOfWeek = Calendar.getInstance().apply {
            time = startOfWeek.time
            add(Calendar.DAY_OF_WEEK, 7)
            add(Calendar.MILLISECOND, -1)
        }

        var todayH = 0.0; var todayE = 0.0; var todayPaidH = 0.0; var todayPaidE = 0.0; var todayUnpaidH = 0.0; var todayUnpaidE = 0.0
        var weekH = 0.0; var weekE = 0.0; var weekPaidH = 0.0; var weekPaidE = 0.0; var weekUnpaidH = 0.0; var weekUnpaidE = 0.0
        var monthH = 0.0; var monthE = 0.0; var monthPaidH = 0.0; var monthPaidE = 0.0; var monthUnpaidH = 0.0; var monthUnpaidE = 0.0
        var totalH = 0.0; var totalE = 0.0; var totalPaidH = 0.0; var totalPaidE = 0.0; var totalUnpaidH = 0.0; var totalUnpaidE = 0.0

        for (entry in entryList) {
            val itemCal = Calendar.getInstance().apply { timeInMillis = entry.date }
            val itemYear = itemCal.get(Calendar.YEAR)
            val itemMonth = itemCal.get(Calendar.MONTH)
            val itemDay = itemCal.get(Calendar.DAY_OF_MONTH)

            val hours = entry.hours
            val earnings = entry.totalEarnings
            val isPaid = entry.isPaid

            // Total Stats
            totalH += hours
            totalE += earnings
            if (isPaid) {
                totalPaidH += hours
                totalPaidE += earnings
            } else {
                totalUnpaidH += hours
                totalUnpaidE += earnings
            }

            // Today Stats
            if (itemYear == todayYear && itemMonth == todayMonth && itemDay == todayDay) {
                todayH += hours
                todayE += earnings
                if (isPaid) {
                    todayPaidH += hours
                    todayPaidE += earnings
                } else {
                    todayUnpaidH += hours
                    todayUnpaidE += earnings
                }
            }

            // This Week Stats (Sunday to Saturday)
            if (entry.date >= startOfWeek.timeInMillis && entry.date <= endOfWeek.timeInMillis) {
                weekH += hours
                weekE += earnings
                if (isPaid) {
                    weekPaidH += hours
                    weekPaidE += earnings
                } else {
                    weekUnpaidH += hours
                    weekUnpaidE += earnings
                }
            }

            // This Month Stats
            if (itemYear == todayYear && itemMonth == todayMonth) {
                monthH += hours
                monthE += earnings
                if (isPaid) {
                    monthPaidH += hours
                    monthPaidE += earnings
                } else {
                    monthUnpaidH += hours
                    monthUnpaidE += earnings
                }
            }
        }

        return StatsSummary(
            today = PeriodStats("היום", todayH, todayE, todayPaidH, todayPaidE, todayUnpaidH, todayUnpaidE),
            thisWeek = PeriodStats("השבוע", weekH, weekE, weekPaidH, weekPaidE, weekUnpaidH, weekUnpaidE),
            thisMonth = PeriodStats("החודש", monthH, monthE, monthPaidH, monthPaidE, monthUnpaidH, monthUnpaidE),
            total = PeriodStats("סה\"כ", totalH, totalE, totalPaidH, totalPaidE, totalUnpaidH, totalUnpaidE)
        )
    }

    // Export Data (JSON structure string)
    fun exportDataToString(): String {
        return try {
            com.example.data.WorkBackup.encode(categories.value, entries.value, workersDirectory.value)
        } catch (e: Exception) {
            ""
        }
    }

    // Export & Copy to clipboard
    fun copyExportToClipboard(context: Context) {
        val json = exportDataToString()
        if (json.isEmpty()) {
            Toast.makeText(context, "שגיאה בייצוא הנתונים", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Sholi Work Tracker Data", json)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "הנתונים הועתקו ללוח בהצלחה!", Toast.LENGTH_SHORT).show()
    }

    // Share JSON file / text adaptively
    fun shareExportData(context: Context) {
        val json = exportDataToString()
        if (json.isEmpty()) {
            Toast.makeText(context, "שגיאה בייצוא הנתונים לשיתוף", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Sholi Gratzman - גיבוי מעקב שעות")
            putExtra(Intent.EXTRA_TEXT, json)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "שיתוף קובץ גיבוי"))
    }

    private fun isValidDate(str: String): Boolean {
        val s = str.trim()
        if (s.isEmpty()) return false
        val regex = """\d{1,4}[/\.-]\d{1,2}[/\.-]\d{1,4}""".toRegex()
        return regex.containsMatchIn(s)
    }

    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd.MM.yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yy", Locale.US),
        SimpleDateFormat("dd.MM.yy", Locale.US)
    )

    private fun parseDateStr(str: String): Long {
        val clean = str.trim()
        for (format in dateFormats) {
            try {
                val date = format.parse(clean)
                if (date != null) return date.time
            } catch (e: Exception) {}
        }
        return System.currentTimeMillis()
    }

    // Import Data from JSON string or Excel/CSV rows
    fun importDataFromString(context: Context, jsonStr: String): Boolean {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return false
        
        val backup = try {
            if (trimmed.startsWith("{")) com.example.data.WorkBackup.decode(trimmed)
            else if (trimmed.startsWith("[")) com.example.data.WorkBackup.decode("{\"entries\":" + trimmed + "}")
            else com.example.data.WorkTableImport.decode(jsonStr)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "לא ניתן לקרוא את הנתונים", Toast.LENGTH_LONG).show()
            return false
        }
        fun amount(value: Double) = String.format(Locale.US, "%.2f", value)
        val totals = backup.entries.groupBy { it.currency }.map { (currency, rows) ->
            "$currency: סך הכול ${amount(rows.sumOf { it.totalEarnings })}, שולם ${amount(rows.filter { it.isPaid }.sumOf { it.totalEarnings })}, לא שולם ${amount(rows.filter { !it.isPaid }.sumOf { it.totalEarnings })}"
        }.joinToString("\n")
        val categoryCount = (backup.categories.map { it.name } + backup.entries.map { it.category }).distinct().size
        val summary = "נקראו ${backup.entries.size} משמרות ו־$categoryCount קטגוריות.\nסך שעות: ${amount(backup.entries.sumOf { it.hours })}\nסכומי הקובץ לפי מצב תשלום המשמרת:\n$totals\nהשווה לסיכום המקורי לפני האישור.\nרשומות שכבר קיימות לא יתווספו שוב.\n\n" +
            backup.entries.take(20).joinToString("\n") { entry ->
                "${entry.category} | ${SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(entry.date))} | ${entry.hours} שעות | ${entry.totalEarnings} ${entry.currency}"
            } + if (backup.entries.size > 20) "\nועוד ${backup.entries.size - 20} משמרות" else ""
        android.app.AlertDialog.Builder(context)
            .setTitle("בדיקת נתונים לפני ייבוא")
            .setMessage(summary)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("שמירת הנתונים") { _, _ ->
                viewModelScope.launch {
                    try {
                        val added = repository.importBackup(backup)
                        performAutoBackup()
                        Toast.makeText(context, "נוספו $added משמרות", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.util.Log.e("WorkViewModel", "Import failed", e)
                        Toast.makeText(context, "הייבוא לא הושלם. הנתונים הקיימים נשמרו.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
        return true
    }

    // Export Work History to CSV and Share
    fun exportToCsvAndShare(context: Context) {
        val entryList = entries.value
        if (entryList.isEmpty()) {
            Toast.makeText(context, "אין משמרות לייצוא", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvBuilder = StringBuilder()
            // Add BOM (Byte Order Mark) so Excel reads Hebrew UTF-8 correctly
            csvBuilder.append('\ufeff')
            
            // CSV Headers
            csvBuilder.append("מזהה,מעסיק/קטגוריה,תאריך,שעות,תעריף שעתי,סה\"כ רווח,סטטוס תשלום,סוג דיווח,שעת כניסה,שעת יציאה,הערות,מטבע\n")
            
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            for (entry in entryList) {
                val escapedCategory = escapeCsvField(entry.category)
                val formattedDate = sdf.format(Date(entry.date))
                val hoursStr = String.format(Locale.US, "%.2f", entry.hours)
                val rateStr = String.format(Locale.US, "%.2f", entry.hourlyRate)
                val earningsStr = String.format(Locale.US, "%.2f", entry.totalEarnings)
                val statusStr = if (entry.isPaid) "שולם" else "ממתין"
                val reportTypeStr = if (entry.isTimeRange) "טווח שעות" else "ידני"
                val startTimeStr = entry.startTime ?: ""
                val endTimeStr = entry.endTime ?: ""
                val escapedNotes = escapeCsvField(entry.notes)
                
                csvBuilder.append("${entry.id},$escapedCategory,$formattedDate,$hoursStr,$rateStr,$earningsStr,$statusStr,$reportTypeStr,$startTimeStr,$endTimeStr,$escapedNotes,${entry.currency}\n")
            }

            // Write to local cache file
            val cacheDir = context.cacheDir
            // unique file name to avoid collision
            val csvFile = File(cacheDir, "work_history_${System.currentTimeMillis()}.csv")
            FileOutputStream(csvFile).use { writer ->
                writer.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
            }

            // Exact Export Confirmation Toast
            Toast.makeText(context, "הקובץ נשמר בהצלחה!", Toast.LENGTH_SHORT).show()

            // Obtain shareable Uri using FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, csvFile)

            // Compose share intent with text/csv mime type
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "מעקב שעות עבודה - Sholi Gratzman")
                putExtra(Intent.EXTRA_TEXT, "מצורף קובץ CSV של שעות מעקב העבודה שלי.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Launch chooser
            val chooser = Intent.createChooser(shareIntent, "שתף קובץ שעות עבודה (CSV)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            
        } catch (e: Exception) {
            Toast.makeText(context, "שגיאה בייצוא קובץ ה-CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}

// ViewModel factory for initializing the Room Database Context
class WorkViewModelFactory(
    private val application: Application,
    private val owner: com.example.data.WorkAccountScope = com.example.data.WorkAccountScope(null)
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkViewModel::class.java)) {
            val db = owner.database(application)
            val repository = WorkRepository(db.workDao())
            @Suppress("UNCHECKED_CAST")
            return WorkViewModel(application, repository, owner) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
