package com.example.api

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Robust non-destructive authentication manager with complete preview & emulator safeguards.
 */
object AuthManager {
    private const val TAG = "AuthManager"

    data class UserSession(
        val uid: String,
        val displayName: String?,
        val email: String?,
        val photoUrl: String? = null
    )

    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            FirebaseSafeInitializer.init(context)
            val auth = getFirebaseAuthSafely()
            if (auth != null) {
                updateUserFromFirebase(auth.currentUser)
                val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                    try {
                        updateUserFromFirebase(fbAuth.currentUser)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Safeguard caught exception in AuthStateListener: ${t.localizedMessage}")
                    }
                }
                auth.addAuthStateListener(listener)
                authListener = listener
            } else {
                Log.d(TAG, "FirebaseAuth not available on startup; emulator fallback mode ready.")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Auth initialization safeguard intercepted: ${t.localizedMessage}", t)
        }
    }

    fun getFirebaseAuthSafely(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseAuth.getInstance() threw: ${t.localizedMessage}")
            null
        }
    }

    private fun updateUserFromFirebase(firebaseUser: com.google.firebase.auth.FirebaseUser?) {
        if (firebaseUser != null) {
            _currentUser.value = UserSession(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: firebaseUser.email ?: "משתמש Google",
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString()
            )
        } else {
            _currentUser.value = null
        }
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient? {
        if (!com.example.BuildConfig.CLOUD_SYNC_ENABLED) return null
        if (getFirebaseAuthSafely() == null) return null
        return try {
            val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resourceId == 0) return null
            val webClientId = context.getString(resourceId)
            if (webClientId.isBlank()) return null
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (t: Throwable) {
            Log.w(TAG, "GoogleSignInClient creation failed: ${t.localizedMessage}", t)
            null
        }
    }

    fun handleGoogleSignInResult(data: Intent?, onComplete: (Boolean, String?) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val idToken = account.idToken
                val auth = getFirebaseAuthSafely()
                if (auth != null && !idToken.isNullOrBlank()) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                updateUserFromFirebase(auth.currentUser)
                                onComplete(true, null)
                            } else {
                                onComplete(false, "ההתחברות נכשלה. הנתונים נשארו במכשיר.")
                            }
                        }
                } else {
                    onComplete(false, "החיבור לחשבון עדיין לא הוגדר.")
                }
            } else {
                onComplete(false, "לא התקבל חשבון Google")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Google Sign-In API code: ${e.statusCode} (${e.localizedMessage})")
            onComplete(false, "ההתחברות בוטלה או נכשלה.")
        } catch (t: Throwable) {
            Log.w(TAG, "Google Sign-In exception: ${t.localizedMessage}", t)
            onComplete(false, "לא ניתן להתחבר כעת.")
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        try {
            getFirebaseAuthSafely()?.signOut()
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseAuth.signOut() failed: ${t.localizedMessage}")
        }

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
        } catch (t: Throwable) {
            Log.w(TAG, "GoogleSignIn.signOut() failed: ${t.localizedMessage}")
        }

        _currentUser.value = null
        onComplete()
    }
}
