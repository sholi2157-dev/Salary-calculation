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
import kotlinx.coroutines.flow.first
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
    private val localDao = owner.database(application).workDao()
    val localPreferences = localDao.observeLocalPreferences().map { rows -> rows.associate { it.name to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun addDefaultCategory(name: String, rate: Double) { viewModelScope.launch {
        if (name.isBlank() || !rate.isFinite() || rate < 0 || repository.getCategoryByName(name) != null) return@launch
        repository.insertCategory(com.example.data.WorkCategory(name = name, defaultRate = rate))
        localDao.setLocalPreference(com.example.data.WorkLocalPreference("categoryCurrency:$name", defaultCurrency.value))
        localDao.setLocalPreference(com.example.data.WorkLocalPreference("defaultCategory", name))
    } }
    fun setDefaultCategory(name: String) { viewModelScope.launch {
        if (repository.getCategoryByName(name) != null) localDao.setLocalPreference(com.example.data.WorkLocalPreference("defaultCategory", name))
    } }
    fun categoryCurrency(name: String): String = localPreferences.value["categoryCurrency:$name"] ?: defaultCurrency.value
    fun editCategory(category: WorkCategory, name: String, rate: Double, currency: String) { viewModelScope.launch {
        try { localDao.editCategorySafely(category, name, rate, currency); performAutoBackup() }
        catch (_: IllegalArgumentException) { Toast.makeText(getApplication(), "שם כפול או ערכים לא תקינים", Toast.LENGTH_LONG).show() }
    } }
    private val settings = com.example.data.WorkAccountPreferences.get(application, owner)
    private val shiftState = ShiftStateManager.forAccount(application, owner)

    companion object {
        const val DEFAULT_RATE = 40.0
        const val APP_NAME = "שכר עבודות אישי"
    }

    private val SERVICE_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("service_notification_enabled")
    private val DEFAULT_CURRENCY_KEY = stringPreferencesKey("default_currency")

    val defaultCurrency: StateFlow<String> = kotlinx.coroutines.flow.combine(settings.data, localPreferences) { preferences, local ->
            local["default_currency"] ?: preferences[DEFAULT_CURRENCY_KEY] ?: "₪"
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
            // Consume before suspension: a delayed undo must not erase a newer save's undo state.
            lastAddedEntryIds = emptyList()
            lastAddedEntryId = null
            viewModelScope.launch {
                try {
                    repository.deleteEntriesById(ids, uid)
                    performAutoBackup()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) {
                    Toast.makeText(getApplication(), "הביטול לא הושלם. יש לבדוק את ההיסטוריה", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun getCategoriesList(): List<WorkCategory> {
        return repository.getCategoriesList()
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            require(currency in listOf("₪", "$"))
            localDao.setLocalPreference(com.example.data.WorkLocalPreference("default_currency", currency))
            settings.edit { preferences -> preferences[DEFAULT_CURRENCY_KEY] = currency }
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
            require(currency in listOf("₪", "$"))
            localDao.setLocalPreference(com.example.data.WorkLocalPreference("default_currency", currency))
            settings.edit { preferences -> preferences[DEFAULT_CURRENCY_KEY] = currency }
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val temporary = File(backupDir, backupFile.name + ".tmp")
                    temporary.writeText(json)
                    check(temporary.renameTo(backupFile))
                    backupDir.listFiles { file -> file.name.startsWith("backup_work_entries_") && file.extension == "json" }
                        ?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
                }
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
                    "${it.category} · ${if (it.payKnown) "${it.totalEarnings} ${it.currency}" else com.example.data.WorkPay.missingLabel(it)}\n${it.notes}"
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
        val isPaid: Boolean = false,
        val sourceJson: String = ""
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
                        isPaid = obj.optBoolean("isPaid", false),
                        sourceJson = obj.toString()
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
            val obj = if (w.sourceJson.isBlank()) JSONObject() else JSONObject(w.sourceJson)
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
    val activeShiftCurrency: StateFlow<String> = shiftState.activeShiftCurrency
    fun startDefaultActiveShift() { viewModelScope.launch {
        val all = repository.getCategoriesList()
        val selected = localDao.getLocalPreferences().firstOrNull { it.name == "defaultCategory" }?.value
        val category = all.firstOrNull { it.name == selected } ?: all.firstOrNull() ?: return@launch
        startActiveShift(category.name, category.defaultRate)
    } }

    fun startActiveShiftWithSavedRate(category: String) {
        viewModelScope.launch {
            val rate = repository.getCategoryByName(category)?.defaultRate ?: DEFAULT_RATE
            startActiveShift(category, rate)
        }
    }

    fun startActiveShift(category: String, rate: Double, currency: String = categoryCurrency(category)) {
        if (com.example.api.AuthManager.currentUser.value?.uid != owner.uid) return
        val startTime = System.currentTimeMillis()
        if (!shiftState.start(category, rate, startTime, currency)) {
            Toast.makeText(getApplication(), "כבר קיימת משמרת פעילה. יש לסיים אותה תחילה", Toast.LENGTH_LONG).show()
            return
        }
        
        // Start Foreground Service only if enabled. Read the stored choice itself: the StateFlow is only
        // collected by the settings page and would still hold its default (on) right after a cold start.
        viewModelScope.launch {
        val enabled = runCatching { settings.data.first()[SERVICE_NOTIFICATION_ENABLED_KEY] }.getOrNull() ?: true
        if (enabled) {
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

    fun updateActiveShiftCurrency(currency: String, expectedStart: Long?) {
        if (com.example.api.AuthManager.currentUser.value?.uid != owner.uid) return
        if (!shiftState.updateCurrency(currency, expectedStart)) {
            Toast.makeText(getApplication(), "המטבע לא עודכן. יש לנסות שוב", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelActiveShift(expectedStart: Long?) {
        if (expectedStart == null || activeShiftStartTime.value != expectedStart) return
        finishActiveShift(save = false, expectedStart = expectedStart)
    }

    fun stopActiveShift() {
        finishActiveShift(save = false)
    }

    fun finishActiveShift(save: Boolean = true, expectedStart: Long? = activeShiftStartTime.value, onSaved: () -> Unit = {}) {
        val start = expectedStart ?: return
        // "Undo" may only ever remove what this save adds.
        if (save) { lastAddedEntryIds = emptyList(); lastAddedEntryId = null }
        if (activeShiftStartTime.value != start) return
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
                if (id > 0) {
                    lastAddedEntryIds = listOf(id.toInt())
                    lastAddedEntryId = id.toInt()
                    onSaved()
                }
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
            val totals = com.example.data.WorkPay.totals(reviewed.entries).map { (currency, sum) ->
                "$currency: ${String.format(Locale.US, "%.2f", sum)}"
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
        val unpaidEarnings: Double,
        val money: Map<String, Double> = emptyMap(),
        val paidMoney: Map<String, Double> = emptyMap(),
        val unpaidMoney: Map<String, Double> = emptyMap()
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
            if (!hours.isFinite() || hours <= 0 || !rate.isFinite() || rate < 0 || currency !in listOf("₪", "$")) return@launch
            val finalHours = hours
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
            lastAddedEntryIds = listOf(insertedId.toInt())
            lastAddedEntryId = insertedId.toInt()
            
            performAutoBackup()
        }
    }

    fun addShifts(shifts: List<com.example.api.GeminiParser.ParsedShift>, onComplete: (Boolean) -> Unit = {}) {
        if (shifts.isEmpty()) { onComplete(false); return }
        viewModelScope.launch {
            try {
                val uid = getActiveUserId()
                val proposed = shifts.map { shift ->
                    require(shift.category.isNotBlank() && shift.date >= 0 && shift.hours.isFinite() && shift.hours > 0 &&
                        shift.hourlyRate.isFinite() && shift.hourlyRate >= 0 && (shift.hours * shift.hourlyRate).isFinite() &&
                        shift.currency in listOf("₪", "$"))
                    require(!shift.isGroup || shift.groupMembers.all { it.isNotBlank() })
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
                    entry
                }
                val insertedIds = repository.insertEntries(proposed, uid).map { it.toInt() }
                lastAddedEntryIds = insertedIds
                lastAddedEntryId = insertedIds.lastOrNull()

                performAutoBackup()
                runCountAnimationTrigger.value = runCountAnimationTrigger.value + 1
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                Toast.makeText(getApplication(), "השמירה לא הושלמה. התיאור נשמר; אפשר לתקן ולנסות שוב", Toast.LENGTH_LONG).show()
                onComplete(false)
                return@launch
            }
            onComplete(true)
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

    // ---- 2.0 entry operations (Kis UI). Amounts follow WorkPay; unknown pay is never 0. ----

    private fun valid(e: WorkEntry): Boolean {
        if (e.category.isBlank() || e.currency !in listOf("₪", "$") || !e.hours.isFinite() || e.hours < 0) return false
        if (!e.bonus.isFinite() || e.bonus < 0 || !e.hourlyRate.isFinite() || e.hourlyRate < 0 || !e.totalEarnings.isFinite() || e.totalEarnings < 0) return false
        if (e.kind == WorkEntry.KIND_JOB) return !e.payKnown || e.totalEarnings > 0
        if (e.kind != WorkEntry.KIND_SHIFT || e.hours <= 0) return false
        if (e.isGroupShift && com.example.data.WorkPay.workers(e).any { it.name.isBlank() || !it.hours.isFinite() || it.hours < 0 || it.bonus < 0 }) return false
        return listOfNotNull(e.employerRate, e.workerRate).all { it.isFinite() && it >= 0 }
    }

    private suspend fun rememberWorkers(e: WorkEntry) {
        val known = workersDirectory.value.map { it.name.trim() }.toSet()
        com.example.data.WorkPay.workers(e).map { it.name.trim() }.filter { it.isNotEmpty() && it !in known }.distinct().forEach { repository.insertWorker(it) }
    }

    /** Saves a new entry built by the editor. The stored total is computed here, never trusted from the UI for hourly pay. */
    fun saveNewEntry(draft: WorkEntry, onSaved: (Int) -> Unit = {}, onFailed: () -> Unit = {}) {
        val entry = when {
            !draft.payKnown -> draft.copy(hourlyRate = 0.0, totalEarnings = 0.0)
            draft.kind == WorkEntry.KIND_JOB -> draft.copy(hourlyRate = 0.0, totalEarnings = Math.round(draft.totalEarnings * 100.0) / 100.0)
            else -> draft.copy(totalEarnings = com.example.data.WorkPay.amount(draft.hours, draft.hourlyRate, draft.bonus))
        }.copy(id = 0)
        if (!valid(entry)) { onFailed(); return }
        viewModelScope.launch {
            try {
                val id = repository.insertEntry(entry, getActiveUserId()).toInt()
                rememberWorkers(entry)
                lastAddedEntryIds = listOf(id)
                lastAddedEntryId = id
                performAutoBackup()
                onSaved(id)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Saving a new entry failed", e)
                Toast.makeText(getApplication(), "המשמרת לא נשמרה. הפרטים נשארו בטופס; אפשר לנסות שוב", Toast.LENGTH_LONG).show()
                onFailed()
            }
        }
    }

    /** Applies an edit; the saved amount is kept unless hours, rate, bonus or kind changed (WorkEntryEdits). */
    fun saveEditedEntry(id: Int, proposed: WorkEntry, onSaved: () -> Unit = {}, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val original = repository.getEntryById(id) ?: run { android.util.Log.w("WorkViewModel", "Edit target $id is gone"); onFailed(); return@launch }
                val applied = com.example.data.WorkEntryEdits.apply(original, proposed)
                // A changed job amount is stored to the cent, as on creation.
                val updated = if (applied.kind == WorkEntry.KIND_JOB && applied.payKnown && applied.totalEarnings != original.totalEarnings)
                    applied.copy(totalEarnings = Math.round(applied.totalEarnings * 100.0) / 100.0) else applied
                if (!valid(updated)) { android.util.Log.w("WorkViewModel", "Edit rejected as invalid: $updated"); onFailed(); return@launch }
                repository.updateEntry(updated, getActiveUserId())
                rememberWorkers(updated)
                performAutoBackup()
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Saving an edit failed", e)
                Toast.makeText(getApplication(), "השינוי לא נשמר. אפשר לנסות שוב", Toast.LENGTH_LONG).show()
                onFailed()
            }
        }
    }

    fun setPaid(ids: Collection<Int>, paid: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch { localDao.setPaymentStatusFor(ids.toList(), paid); performAutoBackup() }
    }

    /** Fills the rate only where it is missing; returns the number of shifts completed. */
    fun completeMissingRates(ids: Collection<Int>, rate: Double, onDone: (Int) -> Unit = {}) {
        if (!rate.isFinite() || rate < 0) return
        viewModelScope.launch {
            val n = localDao.completeMissingRates(ids.toList(), rate)
            performAutoBackup()
            onDone(n)
        }
    }

    /** Deletes and returns the exact rows, so the UI can offer undo. */
    fun deleteEntries(entries: List<WorkEntry>, onDone: (List<WorkEntry>) -> Unit = {}) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val uid = getActiveUserId()
            entries.forEach { repository.deleteEntry(it, uid) }
            skipPlanDays(entries, true)
            performAutoBackup()
            onDone(entries)
        }
    }

    /** Undo of a delete: the same rows come back with their ids, amounts and dates. */
    fun restoreEntries(entries: List<WorkEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val uid = getActiveUserId()
            entries.forEach { repository.insertEntry(it, uid) }
            skipPlanDays(entries, false)
            performAutoBackup()
        }
    }

    /** A deleted shift that came from a weekly plan keeps that day skipped, so it is not recorded again by itself. */
    private suspend fun skipPlanDays(rows: List<WorkEntry>, skipped: Boolean) {
        val keys = rows.mapNotNull { it.seriesKey }
        if (keys.isNotEmpty()) runCatching { localDao.markPlanDays(keys, skipped) }
    }

    /** Group workers' payment marks and bonuses live in the group JSON; nothing else changes. */
    /** Forgets a saved worker name (suggestion list only). History keeps every worker it recorded. */
    fun forgetWorker(name: String) { viewModelScope.launch { repository.forgetWorker(name) } }

    // ---------------- Weekly plans (2.0 stage C) ----------------

    val planSettings = com.example.PlanSettings(application)
    val series: StateFlow<List<com.example.data.ShiftSeries>> = localDao.observeSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seriesExceptions: StateFlow<List<com.example.data.SeriesException>> = localDao.observeExceptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val planClock = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())

    /** Occurrences from two months back (still waiting to be recorded) to four weeks ahead. Never stored, never summed. */
    val occurrences: StateFlow<List<com.example.domain.V2Plan.Occurrence>> = kotlinx.coroutines.flow.combine(series, seriesExceptions, entries, planClock) { s, x, e, now ->
        if (s.isEmpty()) emptyList() else {
            val today = com.example.domain.V2Plan.dateOf(now)
            val days = com.example.domain.V2Rules.daysFromCivil(today)
            com.example.domain.V2Plan.occurrences(s, x, e.mapNotNull { it.seriesKey }.toSet(),
                com.example.domain.V2Rules.civilFromDays(days - com.example.PlanJobs.LOOK_BACK_DAYS), com.example.domain.V2Rules.civilFromDays(days + 28), now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun tickPlans() { planClock.value = System.currentTimeMillis() }

    /** On open/resume: records finished plans (if enabled) and reports the new rows so the screen can offer undo. */
    fun catchUpPlans(onRecorded: (List<Int>) -> Unit = {}) {
        tickPlans()
        viewModelScope.launch {
            val ids = runCatching { com.example.PlanJobs.catchUp(localDao, planSettings) }.getOrDefault(emptyList()).map { it.toInt() }
            if (ids.isNotEmpty()) { lastAddedEntryIds = ids; lastAddedEntryId = ids.last(); performAutoBackup(); onRecorded(ids) }
        }
    }

    fun undoAutoConfirm(ids: List<Int>) { viewModelScope.launch { localDao.undoAutoConfirm(ids); performAutoBackup() } }
    /** Undo of a manual "עבדתי": that day waits again (held, so it is not recorded by itself right away). */
    fun undoWorked(id: Int) { viewModelScope.launch { localDao.undoAutoConfirm(listOf(id), onlyAuto = false); performAutoBackup() } }

    /** Clashes over the whole new range, against saved shifts and every other plan (not only the weeks on screen). */
    fun planPreview(days: Set<Int>, from: String, until: String, start: String, end: String): com.example.domain.V2Plan.Preview =
        planPreview(days, from, until, start, end, entries.value, series.value, seriesExceptions.value)

    private fun planPreview(days: Set<Int>, from: String, until: String, start: String, end: String, saved: List<WorkEntry>,
                            plans: List<com.example.data.ShiftSeries>, exceptions: List<com.example.data.SeriesException>): com.example.domain.V2Plan.Preview {
        val others = if (from > until) emptyList() else
            com.example.domain.V2Plan.occurrences(plans, exceptions, saved.mapNotNull { it.seriesKey }.toSet(), from, until, System.currentTimeMillis())
        return com.example.domain.V2Plan.preview(days, from, until, start, end, saved, others)
    }

    /** Creates a weekly plan from the editor; overlapping days are not created. Past days are recorded right away. */
    fun createSeries(category: String, days: Set<Int>, from: String, until: String, start: String, end: String, rate: Double?, currency: String,
                     onFailed: () -> Unit = {}, onDone: (created: Int, recorded: List<Int>) -> Unit) {
        val s = com.example.data.ShiftSeries(java.util.UUID.randomUUID().toString(), category, com.example.domain.V2Plan.mask(days), start, end, rate, currency, from, until)
        viewModelScope.launch {
            try {
                // Fresh from the database: what is saved now decides which days are created.
                val preview = planPreview(days, from, until, start, end, localDao.getEntriesList(), localDao.getSeries(), localDao.getExceptions())
                localDao.saveSeries(s, preview.clashes)
                val ids = com.example.PlanJobs.catchUp(localDao, planSettings).map { it.toInt() }
                if (ids.isNotEmpty()) { lastAddedEntryIds = ids; lastAddedEntryId = ids.last() }
                performAutoBackup()
                tickPlans()
                onDone(preview.created.size, ids)
            } catch (e: Exception) {
                android.util.Log.e("WorkViewModel", "Saving a plan failed", e)
                Toast.makeText(getApplication(), "המשמרת הקבועה לא נשמרה. אפשר לנסות שוב", Toast.LENGTH_LONG).show()
                onFailed()
            }
        }
    }

    /** "עבדתי": records one occurrence now, exactly as planned (never twice). */
    fun confirmOccurrence(o: com.example.domain.V2Plan.Occurrence, onDone: (Int?) -> Unit = {}) {
        viewModelScope.launch {
            val id = localDao.confirmOccurrences(listOf(com.example.domain.V2Plan.toEntry(o, auto = false))).firstOrNull()?.toInt()
            if (id != null) { lastAddedEntryIds = listOf(id); lastAddedEntryId = id; performAutoBackup() }
            onDone(id)
        }
    }

    private suspend fun exceptionFor(o: com.example.domain.V2Plan.Occurrence): com.example.data.SeriesException =
        localDao.getExceptions().firstOrNull { it.seriesId == o.seriesId && it.date == o.date } ?: com.example.data.SeriesException(o.seriesId, o.date)

    /** "לא עבדתי": that day is skipped (and can be restored). */
    fun cancelOccurrence(o: com.example.domain.V2Plan.Occurrence) {
        viewModelScope.launch { localDao.upsertException(exceptionFor(o).copy(cancelled = true)); performAutoBackup() }
    }
    fun restoreOccurrence(o: com.example.domain.V2Plan.Occurrence) {
        viewModelScope.launch { localDao.upsertException(exceptionFor(o).copy(cancelled = false)); performAutoBackup() }
    }

    /** New hours for just this day, or from this day on (the plan is split there). */
    fun editOccurrence(o: com.example.domain.V2Plan.Occurrence, start: String, end: String, fromNowOn: Boolean) {
        viewModelScope.launch {
            if (!fromNowOn) localDao.upsertException(exceptionFor(o).copy(startTime = start, endTime = end))
            else {
                val s = localDao.getSeries().firstOrNull { it.id == o.seriesId } ?: return@launch
                localDao.applySplit(com.example.domain.V2Plan.split(s, localDao.getExceptions(), o.date, java.util.UUID.randomUUID().toString(), start = start, end = end,
                    recorded = localDao.confirmedKeys().toSet()))
            }
            performAutoBackup()
        }
    }

    /** A new rate from today on; days that already ended keep the rate they were planned with, recorded shifts never change. */
    fun applySeriesRate(seriesId: String, rate: Double?) {
        viewModelScope.launch {
            val s = localDao.getSeries().firstOrNull { it.id == seriesId } ?: return@launch
            val today = com.example.domain.V2Plan.dateOf(System.currentTimeMillis())
            val exceptions = localDao.getExceptions()
            // Every ended day still waiting (fresh, not only the weeks on screen) keeps the rate it was planned with.
            val now = System.currentTimeMillis()
            val waiting = com.example.domain.V2Plan.occurrences(listOf(s), exceptions, localDao.confirmedKeys().toSet(), s.fromDate, today, now)
                .filter { it.status == com.example.domain.V2Plan.Status.AWAITING }
            val (updated, changes) = com.example.domain.V2Plan.applyRateFromToday(s, exceptions, rate, today, waiting)
            localDao.upsertSeries(updated); changes.forEach { localDao.upsertException(it) }
            performAutoBackup()
        }
    }

    /** Ends a plan after today (recorded shifts stay). */
    fun endSeries(seriesId: String) {
        viewModelScope.launch {
            val s = localDao.getSeries().firstOrNull { it.id == seriesId } ?: return@launch
            val today = com.example.domain.V2Plan.dateOf(System.currentTimeMillis())
            if (s.fromDate > today) localDao.deleteSeries(s.id) else localDao.upsertSeries(s.copy(untilDate = minOf(s.untilDate, today)))
            performAutoBackup()
        }
    }

    /** Toggles one worker's paid mark on the row as stored now, so quick taps on two workers never undo each other. */
    fun toggleWorkerPaid(entryId: Int, index: Int) {
        viewModelScope.launch {
            val current = repository.getEntryById(entryId) ?: return@launch
            val workers = com.example.data.WorkPay.workers(current)
            if (index !in workers.indices) return@launch
            repository.updateEntry(current.copy(groupWorkersJson = com.example.data.WorkPay.encodeWorkers(
                workers.mapIndexed { j, w -> if (j == index) w.copy(isPaid = !w.isPaid) else w })), getActiveUserId())
            performAutoBackup()
        }
    }

    fun updateGroupWorkers(entry: WorkEntry, workers: List<com.example.data.WorkPay.Worker>) {
        viewModelScope.launch {
            val current = repository.getEntryById(entry.id) ?: return@launch
            repository.updateEntry(current.copy(groupWorkersJson = com.example.data.WorkPay.encodeWorkers(workers)), getActiveUserId())
            performAutoBackup()
        }
    }

    fun deleteEntry(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.deleteEntry(entry, uid)
            skipPlanDays(listOf(entry), true)
        }
    }

    fun restoreEntry(entry: WorkEntry) {
        viewModelScope.launch {
            val uid = getActiveUserId()
            repository.insertEntry(entry, uid)
            skipPlanDays(listOf(entry), false)
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
                localDao.setLocalPreference(com.example.data.WorkLocalPreference("categoryCurrency:${name.trim()}", defaultCurrency.value))
            }
        }
    }

    /** A new workplace with its own default rate and currency (from the editor or settings). */
    fun addWorkplace(name: String, defaultRate: Double, currency: String, makeDefault: Boolean = false) {
        val clean = name.trim()
        if (clean.isBlank() || !defaultRate.isFinite() || defaultRate < 0 || currency !in listOf("₪", "$")) return
        viewModelScope.launch {
            if (categories.value.any { it.name.trim().equals(clean, ignoreCase = true) } || repository.getCategoryByName(clean) != null) return@launch
            repository.insertCategory(WorkCategory(name = clean, defaultRate = defaultRate))
            localDao.setLocalPreference(com.example.data.WorkLocalPreference("categoryCurrency:$clean", currency))
            if (makeDefault) localDao.setLocalPreference(com.example.data.WorkLocalPreference("defaultCategory", clean))
            performAutoBackup()
        }
    }

    fun deleteCategory(category: WorkCategory) {
        viewModelScope.launch {
            if (!localDao.removeCategorySafely(category)) Toast.makeText(getApplication(), "יש להשאיר לפחות קטגוריה אחת", Toast.LENGTH_LONG).show()
            performAutoBackup()
        }
    }

    fun updateCategoryRate(category: WorkCategory, newRate: Double) {
        viewModelScope.launch {
            localDao.updateCategory(category.copy(defaultRate = newRate))
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
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val week = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
        val month = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        fun period(label: String, rows: List<WorkEntry>): PeriodStats {
            val paid = rows.filter { it.isPaid }; val unpaid = rows.filter { !it.isPaid }
            // Legacy scalar money is meaningful only for a single currency. UI uses the maps.
            fun single(list: List<WorkEntry>) = com.example.data.WorkMoney.totals(list).values.singleOrNull() ?: 0.0
            return PeriodStats(label, rows.sumOf { it.hours }, single(rows), paid.sumOf { it.hours }, single(paid),
                unpaid.sumOf { it.hours }, single(unpaid), com.example.data.WorkMoney.totals(rows),
                com.example.data.WorkMoney.totals(paid), com.example.data.WorkMoney.totals(unpaid))
        }
        return StatsSummary(period("היום", entryList.filter { it.date >= today.timeInMillis && it.date < tomorrow }),
            period("השבוע", entryList.filter { it.date >= week.timeInMillis && it.date < tomorrow }),
            period("החודש", entryList.filter { it.date >= month.timeInMillis && it.date < tomorrow }), period("סה״כ", entryList))
    }

    // Atomic DB snapshots also include local category preferences. Never export uncollected UI state.
    fun copyExportToClipboard(context: Context) { viewModelScope.launch {
        try {
            val json = repository.exportSnapshot()
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Work Tracker Data", json))
            Toast.makeText(context, "הגיבוי הועתק", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(context, "הגיבוי נכשל", Toast.LENGTH_LONG).show() }
    } }
    fun saveBackup(context: Context, uri: android.net.Uri) { viewModelScope.launch {
        try {
            val json = repository.exportSnapshot()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            Toast.makeText(context, "הגיבוי נשמר", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(context, "הגיבוי לא נשמר", Toast.LENGTH_LONG).show() }
    } }
    fun shareExportData(context: Context) { viewModelScope.launch {
        try {
            val json = repository.exportSnapshot()
            val file = File(context.cacheDir, "work-backup-${System.currentTimeMillis()}.json")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.writeText(json) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "שמירת גיבוי מחוץ לאפליקציה"))
        } catch (_: Exception) { Toast.makeText(context, "הגיבוי נכשל", Toast.LENGTH_LONG).show() }
    } }

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
        val totals = backup.entries.groupBy { it.currency }.map { (currency, all) ->
            val rows = all.filter { it.payKnown }
            val pending = all.size - rows.size
            "$currency: סך הכול ${amount(rows.sumOf { it.totalEarnings })}, שולם ${amount(rows.filter { it.isPaid }.sumOf { it.totalEarnings })}, לא שולם ${amount(rows.filter { !it.isPaid }.sumOf { it.totalEarnings })}" +
                if (pending > 0) " · $pending בלי תעריף (לא נספרו)" else ""
        }.joinToString("\n")
        val categoryCount = (backup.categories.map { it.name } + backup.entries.map { it.category }).distinct().size
        val summary = "נקראו ${backup.entries.size} משמרות ו־$categoryCount קטגוריות.\nסך שעות: ${amount(backup.entries.sumOf { it.hours })}\nסכומי הקובץ לפי מצב תשלום המשמרת:\n$totals\nהשווה לסיכום המקורי לפני האישור.\nרשומות שכבר קיימות לא יתווספו שוב.\n\n" +
            backup.entries.take(20).joinToString("\n") { entry ->
                "${entry.category} | ${SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(entry.date))} | ${entry.hours} שעות | ${if (entry.payKnown) "${entry.totalEarnings} ${entry.currency}" else com.example.data.WorkPay.missingLabel(entry)}"
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
        val entryList = com.example.ui.kis.KisHistoryLogic.done(entries.value)
        if (entryList.isEmpty()) {
            Toast.makeText(context, "אין משמרות לייצוא", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvText = com.example.data.WorkShareText.csv(entryList)

            // Write to local cache file
            val cacheDir = context.cacheDir
            // unique file name to avoid collision
            val csvFile = File(cacheDir, "work_history_${System.currentTimeMillis()}.csv")
            FileOutputStream(csvFile).use { writer ->
                writer.write(csvText.toByteArray(Charsets.UTF_8))
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
                putExtra(Intent.EXTRA_SUBJECT, "מעקב שעות עבודה")
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
