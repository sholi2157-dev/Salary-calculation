package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Immutable owner, captured before any asynchronous work starts. [uid] null is the phone's own data (the guest
 * database). [cloudUid] is the Google account it syncs with: the first account to sign in on this phone takes the
 * phone's own data (see [WorkAccountBinding]); any other account gets a database of its own.
 */
data class WorkAccountScope(val uid: String?, val cloudUid: String? = uid) {
    init { require(uid == null || uid.isNotBlank()); require(cloudUid == null || cloudUid.isNotBlank()); require(uid == null || uid == cloudUid) }
    /** One ViewModel per owner and account (the same database may be seen before and after sign-in). */
    val modelKey: String get() = "$storageKey|${cloudUid.orEmpty()}"
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

/** Which Google account the phone's own data belongs to: the first one to sign in here, for good. */
object WorkAccountBinding {
    private const val PREFS = "work_account_binding"
    /** Only the app's own device checks set this (the website has the same switch for its automated checks). */
    fun signInSkipped(context: Context): Boolean = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("skip_signin", false)
    fun skipSignIn(context: Context, skip: Boolean) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("skip_signin", skip).commit() }
    fun bound(context: Context): String? = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("bound", null)

    fun scopeFor(context: Context, uid: String?): WorkAccountScope {
        if (uid == null) return WorkAccountScope(null)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bound = prefs.getString("bound", null) ?: run {
            // A test build may already hold this account's own database (before 2.1): it keeps using it.
            if (context.getDatabasePath(WorkAccountStorage.databaseName(uid)).exists()) return WorkAccountScope(uid)
            check(prefs.edit().putString("bound", uid).commit()); uid
        }
        return if (bound == uid) WorkAccountScope(null, uid) else WorkAccountScope(uid)
    }
}
