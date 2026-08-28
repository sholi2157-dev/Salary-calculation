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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class WorkViewModel(
    application: Application,
    private val repository: WorkRepository
) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_RATE = 40.0
        const val APP_NAME = "שכר עבודות אישי"
    }

    private val SERVICE_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("service_notification_enabled")
    private val GEMINI_MODEL_KEY = stringPreferencesKey("gemini_model")
    private val DEFAULT_CURRENCY_KEY = stringPreferencesKey("default_currency")

    val defaultCurrency: StateFlow<String> = application.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_CURRENCY_KEY] ?: "₪"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "₪"
        )

    val geminiModel: StateFlow<String> = application.dataStore.data
        .map { preferences ->
            preferences[GEMINI_MODEL_KEY] ?: "Gemini 3.5 Flash"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Gemini 3.5 Flash"
        )

    val serviceNotificationEnabled: StateFlow<Boolean> = application.dataStore.data
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
        return currentUserSession.value?.uid
            ?: com.example.api.FirebaseSafeInitializer.currentUser.value?.uid
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
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[DEFAULT_CURRENCY_KEY] = currency
            }
        }
    }

    fun updateServiceNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[SERVICE_NOTIFICATION_ENABLED_KEY] = enabled
            }
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

    fun updateGeminiModel(model: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[GEMINI_MODEL_KEY] = model
            }
        }
    }

    fun updateDefaultCurrency(currency: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[DEFAULT_CURRENCY_KEY] = currency
            }
        }
    }

    fun performAutoBackup() {
        viewModelScope.launch {
            try {
                val json = exportDataToString()
                if (json.isEmpty()) return@launch
                
                val context = getApplication<Application>()
                val backupDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) 
                    ?: File(context.filesDir, "Documents")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFile = File(backupDir, "backup_work_entries_$timeStamp.json")
                backupFile.writeText(json)
                android.util.Log.d("WorkViewModel", "Auto-backup saved successfully to: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Auto-backup failed: ${e.localizedMessage}")
            }
        }
    }

    val runCountAnimationTrigger = androidx.compose.runtime.mutableStateOf(0)

    val currentUserSession: StateFlow<com.example.api.AuthManager.UserSession?> = com.example.api.AuthManager.currentUser

    fun signOut(context: Context) {
        com.example.api.AuthManager.signOut(context)
    }

    init {
        try {
            com.example.api.FirebaseSafeInitializer.init(application)
            com.example.api.AuthManager.init(application)
            com.example.api.FirestoreSyncManager.init(application)
        } catch (e: Throwable) {
            android.util.Log.w("WorkViewModel", "FirebaseSafeInitializer / AuthManager / FirestoreSyncManager failed: ${e.localizedMessage}")
        }
        ShiftStateManager.init(application)
        runCountAnimationTrigger.value = runCountAnimationTrigger.value + 1

        // Subscribe to real-time Firestore snapshots for user
        viewModelScope.launch {
            currentUserSession.collect { session ->
                val uid = session?.uid ?: com.example.api.FirebaseSafeInitializer.currentUser.value?.uid
                if (!uid.isNullOrBlank()) {
                    com.example.api.FirestoreSyncManager.listenToUserShifts(
                        userId = uid,
                        onShiftsChanged = { remoteList ->
                            if (remoteList.isNotEmpty()) {
                                viewModelScope.launch {
                                    repository.syncRemoteEntries(remoteList)
                                }
                            }
                        }
                    )
                }
            }
        }
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
    val activeShiftStartTime: StateFlow<Long?> = ShiftStateManager.activeShiftStartTime
    val activeShiftCategory: StateFlow<String> = ShiftStateManager.activeShiftCategory
    val activeShiftRate: StateFlow<Double> = ShiftStateManager.activeShiftRate

    fun startActiveShift(category: String, rate: Double) {
        val startTime = System.currentTimeMillis()
        ShiftStateManager.start(getApplication(), category, rate, startTime)
        
        // Start Foreground Service only if enabled
        if (serviceNotificationEnabled.value) {
            val intent = Intent(getApplication(), ShiftForegroundService::class.java).apply {
                action = "START"
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        }
    }

    fun stopActiveShift() {
        val context = getApplication<Application>()
        ShiftStateManager.clear(context)
        
        // Stop Foreground Service
        val intent = Intent(context, ShiftForegroundService::class.java)
        context.stopService(intent)
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
            val finalHours = if (isTimeRange) {
                calculateHoursDiff(startTime ?: "00:00", endTime ?: "00:00")
            } else {
                hours
            }
            val entry = WorkEntry(
                id = id,
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
            repository.updateEntry(entry, uid)
            
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
            val root = JSONObject()

            // Map categories to list
            val catsJson = JSONArray()
            for (cat in categories.value) {
                catsJson.put(JSONObject().apply {
                    put("name", cat.name)
                })
            }
            root.put("categories", catsJson)

            // Map work entries to list
            val entriesJson = JSONArray()
            for (entry in entries.value) {
                entriesJson.put(JSONObject().apply {
                    put("category", entry.category)
                    put("date", entry.date)
                    put("isTimeRange", entry.isTimeRange)
                    put("startTime", entry.startTime)
                    put("endTime", entry.endTime)
                    put("hours", entry.hours)
                    put("hourlyRate", entry.hourlyRate)
                    put("totalEarnings", entry.totalEarnings)
                    put("isPaid", entry.isPaid)
                    put("notes", entry.notes)
                })
            }
            root.put("entries", entriesJson)

            root.toString(2)
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
        
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val root = JSONObject(trimmed)

                viewModelScope.launch {
                    // 1. Process categories
                    if (root.has("categories")) {
                        val catsArray = root.getJSONArray("categories")
                        for (i in 0 until catsArray.length()) {
                            val catObj = catsArray.getJSONObject(i)
                            val name = catObj.optString("name", "").trim()
                            if (name.isNotEmpty()) {
                                // Deduplicate before insert
                                val currentList = categories.value
                                val exists = currentList.any { it.name.trim().lowercase() == name.lowercase() }
                                if (!exists) {
                                    repository.insertCategory(WorkCategory(name = name))
                                }
                            }
                        }
                    }

                    // 2. Process entries
                    if (root.has("entries")) {
                        val entriesArray = root.getJSONArray("entries")
                        for (i in 0 until entriesArray.length()) {
                            val entryObj = entriesArray.getJSONObject(i)
                            val category = entryObj.optString("category", "כללי")
                            val date = entryObj.optLong("date", System.currentTimeMillis())
                            val isTimeRange = entryObj.optBoolean("isTimeRange", false)
                            val startTime = if (entryObj.has("startTime")) entryObj.optString("startTime") else null
                            val endTime = if (entryObj.has("endTime")) entryObj.optString("endTime") else null
                            val hours = entryObj.optDouble("hours", 0.0)
                            val rate = entryObj.optDouble("hourlyRate", DEFAULT_RATE)
                            val earnings = entryObj.optDouble("totalEarnings", hours * rate)
                            val isPaid = entryObj.optBoolean("isPaid", false)
                            val notes = entryObj.optString("notes", "")

                            val uid = getActiveUserId()
                            val entry = WorkEntry(
                                category = category,
                                date = date,
                                isTimeRange = isTimeRange,
                                startTime = startTime,
                                endTime = endTime,
                                hours = hours,
                                hourlyRate = rate,
                                totalEarnings = earnings,
                                isPaid = isPaid,
                                notes = notes
                            )
                            repository.insertEntry(entry, uid)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } else {
            // Excel/CSV import
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val lines = trimmed.split("\n")
                var successCount = 0
                var totalAttempted = 0
                
                for (line in lines) {
                    val cleanLine = line.trim()
                    if (cleanLine.isBlank()) continue
                    
                    // Check for headers
                    if (cleanLine.contains("קטגוריה") || cleanLine.contains("תאריך")) {
                        continue
                    }
                    totalAttempted++
                    
                    try {
                        val delimiter = if (cleanLine.contains("\t")) "\t" else if (cleanLine.contains("|")) "|" else ","
                        val rawCells = cleanLine.split(delimiter)
                        val cells = rawCells.map { it.trim() }
                        
                        if (cells.size < 9) { // At least need up to Total/PaidStatus
                            throw IllegalArgumentException("Not enough columns in row")
                        }
                        
                        // a) Map Column 0 to Date
                        val dateStr = cells[0]
                        val parsedDate = parseDateStr(dateStr)
                        
                        // Column 1: Category
                        val categoryStr = cells[1].ifBlank { "כללי" }
                        
                        // Column 2: Type
                        val shiftType = cells[2]
                        val isTimeRange = shiftType != "ידני"
                        
                        // b) Map Column 3 & 4 to Start/End Times
                        var startTimeStr: String? = null
                        var endTimeStr: String? = null
                        if (isTimeRange) {
                            startTimeStr = cells[3].takeIf { it.isNotBlank() }
                            endTimeStr = cells[4].takeIf { it.isNotBlank() }
                        }
                        
                        // c) Map Column 6 (Duration), Column 7 (Rate), and Column 8 (Total) to Double, handling comma-to-dot
                        val durationStr = cells.getOrNull(6)?.replace(",", ".") ?: "0"
                        val duration = durationStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Duration")
                        
                        val rateStr = cells.getOrNull(7)?.replace(",", ".") ?: "0"
                        val rate = rateStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: DEFAULT_RATE
                        
                        val totalStr = cells.getOrNull(8)?.replace(",", ".") ?: "0"
                        val totalEarnings = totalStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: (duration * rate)
                        
                        // d) Map Column 9 (PaidStatus)
                        val paidStr = cells.getOrNull(9)?.trim() ?: ""
                        val isPaid = !(paidStr == "לא" || paidStr.isEmpty())
                        
                        // e) Map Column 10 to Notes
                        val notesStr = cells.getOrNull(10)?.trim() ?: ""
                        
                        val cleanCategory = categoryStr.trim()
                        val existingCategory = repository.getCategoryByName(cleanCategory)
                        if (existingCategory == null) {
                            repository.insertCategory(WorkCategory(name = cleanCategory, defaultRate = rate))
                        }
                        
                        val uid = getActiveUserId()
                        val entry = WorkEntry(
                            category = cleanCategory,
                            date = parsedDate,
                            isTimeRange = isTimeRange,
                            startTime = startTimeStr,
                            endTime = endTimeStr,
                            hours = duration,
                            hourlyRate = rate,
                            totalEarnings = totalEarnings,
                            isPaid = isPaid,
                            notes = notesStr
                        )
                        repository.insertEntry(entry, uid)
                        successCount++
                    } catch (e: Exception) {
                        android.util.Log.w("Import", "Skipping malformed row: $cleanLine", e)
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "יובאו $successCount מתוך $totalAttempted שורות בהצלחה!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            return true
        }
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
            csvBuilder.append("מזהה,מעסיק/קטגוריה,תאריך,שעות,תעריף שעתי,סה\"כ רווח,סטטוס תשלום,סוג דיווח,שעת כניסה,שעת יציאה,הערות\n")
            
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
                
                csvBuilder.append("${entry.id},$escapedCategory,$formattedDate,$hoursStr,$rateStr,$earningsStr,$statusStr,$reportTypeStr,$startTimeStr,$endTimeStr,$escapedNotes\n")
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
class WorkViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkViewModel::class.java)) {
            val db = WorkDatabase.getDatabase(application, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
            val repository = WorkRepository(db.workDao())
            @Suppress("UNCHECKED_CAST")
            return WorkViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
