package com.example.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Startup safeguard for Firebase Auth and Cloud Firestore.
 * Catches missing google-services.json / Web Emulator initialization errors
 * and gracefully falls back to Offline Mock Mode without crashing.
 */
object FirebaseSafeInitializer {
    private const val TAG = "FirebaseSafeFallback"

    data class MockUser(
        val uid: String = "local_offline_user_id",
        val displayName: String = "משתמש מקומי",
        val email: String = "offline_user@local.mock",
        val isAnonymous: Boolean = false
    )

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    private val _isMockModeActive = MutableStateFlow(true)
    val isMockModeActive: StateFlow<Boolean> = _isMockModeActive.asStateFlow()

    private val _currentUser = MutableStateFlow<MockUser?>(MockUser())
    val currentUser: StateFlow<MockUser?> = _currentUser.asStateFlow()

    fun init(context: Context) {
        try {
            // Attempt dynamic reflection or safe initialization for FirebaseApp / FirebaseAuth / Firestore
            val firebaseAppClass = try {
                Class.forName("com.google.firebase.FirebaseApp")
            } catch (e: Throwable) {
                null
            }

            if (firebaseAppClass != null) {
                val getAppsMethod = firebaseAppClass.getMethod("getApps", Context::class.java)
                val apps = getAppsMethod.invoke(null, context) as? List<*>
                if (apps.isNullOrEmpty()) {
                    val initMethod = firebaseAppClass.getMethod("initializeApp", Context::class.java)
                    initMethod.invoke(null, context)
                }
                _isFirebaseAvailable.value = true
                _isMockModeActive.value = false
                Log.d(TAG, "Firebase initialized safely.")
            } else {
                enableOfflineMockMode("FirebaseApp class not found in classpath")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase unavailable on startup: ${t.localizedMessage}. Gracefully falling back to Offline Mock Mode.", t)
            enableOfflineMockMode(t.localizedMessage ?: "Unknown initialization failure")
        }
    }

    private fun enableOfflineMockMode(reason: String) {
        _isFirebaseAvailable.value = false
        _isMockModeActive.value = true
        _currentUser.value = MockUser()
        Log.i(TAG, "Running in robust Offline Mock Mode ($reason). UI and local data operations remain 100% active.")
    }

    fun safeExecuteAuth(action: () -> Unit, fallback: (() -> Unit)? = null) {
        try {
            if (_isFirebaseAvailable.value) {
                action()
            } else {
                fallback?.invoke()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Auth action intercepted: ${t.localizedMessage}", t)
            fallback?.invoke()
        }
    }

    fun safeExecuteFirestore(action: () -> Unit, fallback: (() -> Unit)? = null) {
        try {
            if (_isFirebaseAvailable.value) {
                action()
            } else {
                fallback?.invoke()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firestore action intercepted: ${t.localizedMessage}", t)
            fallback?.invoke()
        }
    }
}
