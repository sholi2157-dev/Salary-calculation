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
import kotlinx.coroutines.launch

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
    private var appContext: Context? = null
    private var emailRequestRunning = false

    fun init(context: Context) {
        if (com.example.BuildConfig.LOCAL_DISTRIBUTION && !com.example.BuildConfig.ACCOUNTS_ENABLED) return
        appContext = context.applicationContext
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
        if (!com.example.BuildConfig.ACCOUNTS_ENABLED) {
            _currentUser.value = null
            return
        }
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
        if (!com.example.BuildConfig.GOOGLE_SIGN_IN_ENABLED) return null
        if (!com.example.BuildConfig.ACCOUNTS_ENABLED) return null
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

    /**
     * 2.1 sign-in: Google's account picker through Android's Credential Manager, then Firebase with the ID token.
     * Returns null when signed in, "" when the person closed the picker, otherwise the message to show.
     */
    suspend fun signInWithGoogle(activity: android.app.Activity): String? {
        if (!com.example.BuildConfig.GOOGLE_SIGN_IN_ENABLED) return "ההתחברות עם Google עוד לא זמינה בגרסה הזו"
        accountBlockReason()?.let { return it }
        val auth = getFirebaseAuthSafely() ?: return "לא ניתן להתחבר כעת"
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (id == 0) return "לא ניתן להתחבר כעת"
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(activity.getString(id)).build()
        val request = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = androidx.credentials.CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is androidx.credentials.CustomCredential ||
                credential.type != com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return "לא התקבל חשבון Google"
            val token = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data).idToken
            kotlinx.coroutines.suspendCancellableCoroutine<String?> { done ->
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).addOnCompleteListener { task ->
                    if (task.isSuccessful) updateUserFromFirebase(auth.currentUser)
                    if (done.isActive) done.resumeWith(Result.success(if (task.isSuccessful) null else "ההתחברות לא הושלמה. בדוק את החיבור לאינטרנט ונסה שוב."))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) { "" }
        catch (_: androidx.credentials.exceptions.NoCredentialException) { "לא נמצא חשבון Google בטלפון. אפשר להוסיף חשבון בהגדרות הטלפון." }
        catch (t: Throwable) { Log.w(TAG, "Google sign-in failed: ${t.javaClass.simpleName}"); "לא ניתן להתחבר כעת. בדוק את החיבור לאינטרנט ונסה שוב." }
    }

    fun handleGoogleSignInResult(data: Intent?, onComplete: (Boolean, String?) -> Unit) {
        if (!com.example.BuildConfig.GOOGLE_SIGN_IN_ENABLED) {
            onComplete(false, "התחברות Google ממתינה להתאמת חתימת האפליקציה. אפשר להתחבר בדוא״ל וסיסמה.")
            return
        }
        accountBlockReason()?.let { onComplete(false, it); return }
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
        if (com.example.ShiftStateManager.hasActiveShift(context)) return
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
        // The next sign-in shows the account picker again instead of choosing the same account by itself.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { androidx.credentials.CredentialManager.create(context.applicationContext).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
        }

        _currentUser.value = null
        onComplete()
    }

    fun accountBlockReason(): String? = when {
        !com.example.BuildConfig.ACCOUNTS_ENABLED -> "ההתחברות עדיין בהכנה. הנתונים נשמרים במכשיר; סנכרון הענן טרם הופעל."
        appContext?.let { com.example.ShiftStateManager.hasActiveShift(it) } == true ->
            "יש לסיים את המשמרת הפעילה לפני החלפת חשבון"
        getFirebaseAuthSafely() == null -> "קובץ החיבור ל־Firebase חסר או אינו מתאים לאפליקציה"
        else -> null
    }

    /** No password persistence/logging, automatic registration, or simulated success. */
    fun submitEmail(email: String, password: String, register: Boolean, onComplete: (Boolean, String?) -> Unit) {
        accountBlockReason()?.let { onComplete(false, it); return }
        if (emailRequestRunning) { onComplete(false, "בקשת התחברות כבר מתבצעת"); return }
        val cleanEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() || password.isEmpty()) {
            onComplete(false, "יש להזין כתובת דוא״ל וסיסמה"); return
        }
        val auth = getFirebaseAuthSafely() ?: run { onComplete(false, "ההתחברות אינה זמינה"); return }
        emailRequestRunning = true
        try {
            val task = if (register) auth.createUserWithEmailAndPassword(cleanEmail, password)
                else auth.signInWithEmailAndPassword(cleanEmail, password)
            task.addOnCompleteListener {
                emailRequestRunning = false
                val success = it.isSuccessful && auth.currentUser != null
                if (success) updateUserFromFirebase(auth.currentUser)
                onComplete(success, if (success) null else "ההתחברות לא הושלמה. בדוק את הפרטים והחיבור ונסה שוב.")
            }
        } catch (_: Exception) {
            emailRequestRunning = false
            onComplete(false, "לא ניתן להתחבר כעת. הנתונים הקיימים נשמרו.")
        }
    }

    fun resetPassword(email: String, onComplete: (String) -> Unit) {
        accountBlockReason()?.let { onComplete(it); return }
        val cleanEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onComplete("יש להזין כתובת דוא״ל תקינה"); return
        }
        val auth = getFirebaseAuthSafely() ?: run { onComplete("ההתחברות אינה זמינה"); return }
        try {
            auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener {
                onComplete(if (it.isSuccessful) "אם ניתן לאפס סיסמה לכתובת הזו, יישלח אליה קישור. בדוק גם בדואר הזבל."
                    else "שליחת בקשת האיפוס לא הושלמה. אפשר לנסות שוב מאוחר יותר.")
            }
        } catch (_: Exception) { onComplete("לא ניתן לשלוח בקשת איפוס כעת") }
    }
}
