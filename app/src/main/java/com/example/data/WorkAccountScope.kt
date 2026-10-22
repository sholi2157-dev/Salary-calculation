package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Immutable owner, captured before any asynchronous work starts. null is legacy guest. */
data class WorkAccountScope(val uid: String?) {
    init { require(uid == null || uid.isNotBlank()) }
    val storageKey: String = uid?.let(WorkAccountStorage::databaseName) ?: WorkAccountStorage.GUEST_DATABASE
    val settingsName: String = if (uid == null) "user_settings" else "${storageKey}_settings"
    val timerName: String = if (uid == null) "active_shift_prefs" else "${storageKey}_timer"

    fun database(context: Context): WorkDatabase = uid?.let { WorkDatabase.getAccountDatabase(context, it) }
        ?: WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
}

/** Exactly one DataStore instance per file, also across Activity recreation. */
object WorkAccountPreferences {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    @Synchronized fun get(context: Context, owner: WorkAccountScope): DataStore<Preferences> =
        stores.getOrPut(owner.settingsName) {
            val app = context.applicationContext
            PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                app.preferencesDataStoreFile(owner.settingsName)
            }
        }
}
