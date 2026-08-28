package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.WorkEntry
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Robust non-destructive Cloud Firestore Data Sync layer.
 * Manages user-scoped data under `users/{userId}/shifts/{shiftId}` with
 * offline caching, real-time snapshot listeners, and complete preview/emulator safeguards.
 */
object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private const val COLLECTION_USERS = "users"
    private const val COLLECTION_SHIFTS = "shifts"

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            FirebaseSafeInitializer.init(context)
            val firestore = getFirestoreSafely()
            if (firestore != null) {
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestore.firestoreSettings = settings
                    Log.d(TAG, "Firestore offline persistent cache enabled successfully.")
                } catch (settingsError: Throwable) {
                    Log.d(TAG, "Firestore settings configured with default cache: ${settingsError.localizedMessage}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firestore safe init intercepted: ${t.localizedMessage}", t)
        }
    }

    fun getFirestoreSafely(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseFirestore.getInstance() threw: ${t.localizedMessage}")
            null
        }
    }

    fun shiftToFirestoreMap(entry: WorkEntry): Map<String, Any?> {
        return mapOf(
            "id" to entry.id,
            "category" to entry.category,
            "date" to entry.date,
            "startTime" to entry.startTime,
            "endTime" to entry.endTime,
            "hours" to entry.hours,
            "breakDuration" to 0.0,
            "hourlyRate" to entry.hourlyRate,
            "totalEarned" to entry.totalEarnings,
            "totalEarnings" to entry.totalEarnings,
            "notes" to entry.notes,
            "isGroup" to entry.isGroupShift,
            "isGroupShift" to entry.isGroupShift,
            "groupWorkersJson" to entry.groupWorkersJson,
            "currencySymbol" to entry.currency,
            "currency" to entry.currency,
            "isPaid" to entry.isPaid,
            "isTimeRange" to entry.isTimeRange,
            "employerRate" to entry.employerRate,
            "workerRate" to entry.workerRate,
            "createdAt" to entry.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    fun documentToWorkEntry(doc: DocumentSnapshot): WorkEntry? {
        return try {
            val idVal = doc.get("id")
            val id = when (idVal) {
                is Number -> idVal.toInt()
                is String -> idVal.toIntOrNull() ?: doc.id.toIntOrNull() ?: 0
                else -> doc.id.toIntOrNull() ?: 0
            }
            val category = doc.getString("category") ?: "כללי"
            val date = doc.getLong("date") ?: (doc.get("date") as? Number)?.toLong() ?: System.currentTimeMillis()
            val startTime = doc.getString("startTime")
            val endTime = doc.getString("endTime")
            val isTimeRange = doc.getBoolean("isTimeRange") ?: (startTime != null && endTime != null)
            val hourlyRate = doc.getDouble("hourlyRate") ?: (doc.get("hourlyRate") as? Number)?.toDouble() ?: 40.0
            val hours = doc.getDouble("hours") ?: (doc.get("hours") as? Number)?.toDouble() ?: 0.0
            val totalEarnings = doc.getDouble("totalEarned")
                ?: doc.getDouble("totalEarnings")
                ?: (doc.get("totalEarned") as? Number)?.toDouble()
                ?: (doc.get("totalEarnings") as? Number)?.toDouble()
                ?: (hours * hourlyRate)
            val isPaid = doc.getBoolean("isPaid") ?: false
            val notes = doc.getString("notes") ?: ""
            val isGroup = doc.getBoolean("isGroup")
                ?: doc.getBoolean("isGroupShift")
                ?: false
            val groupWorkersJson = doc.getString("groupWorkersJson") ?: ""
            val currencySymbol = doc.getString("currencySymbol")
                ?: doc.getString("currency")
                ?: "₪"
            val employerRate = doc.getDouble("employerRate") ?: (doc.get("employerRate") as? Number)?.toDouble()
            val workerRate = doc.getDouble("workerRate") ?: (doc.get("workerRate") as? Number)?.toDouble()
            val createdAt = doc.getLong("createdAt") ?: (doc.get("createdAt") as? Number)?.toLong() ?: date

            WorkEntry(
                id = id,
                category = category,
                date = date,
                isTimeRange = isTimeRange,
                startTime = startTime,
                endTime = endTime,
                hours = hours,
                hourlyRate = hourlyRate,
                totalEarnings = totalEarnings,
                isPaid = isPaid,
                notes = notes,
                createdAt = createdAt,
                isGroupShift = isGroup,
                employerRate = employerRate,
                workerRate = workerRate,
                groupWorkersJson = groupWorkersJson,
                currency = currencySymbol
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing Firestore shift document ${doc.id}: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Subscribes to real-time snapshots under `users/{userId}/shifts`.
     */
    fun listenToUserShifts(
        userId: String,
        onShiftsChanged: (List<WorkEntry>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        if (userId.isBlank()) return null
        return try {
            val firestore = getFirestoreSafely() ?: return null
            val shiftsCollection = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SHIFTS)

            shiftsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore snapshot listener error: ${error.localizedMessage}")
                    onError?.invoke(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val entries = snapshot.documents.mapNotNull { doc ->
                        documentToWorkEntry(doc)
                    }
                    onShiftsChanged(entries)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register snapshot listener for user $userId: ${t.localizedMessage}", t)
            null
        }
    }

    /**
     * Flow-based real-time shift stream for user.
     */
    fun userShiftsFlow(userId: String): Flow<List<WorkEntry>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            val firestore = getFirestoreSafely()
            if (firestore != null) {
                registration = firestore.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(COLLECTION_SHIFTS)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Firestore flow snapshot error: ${error.localizedMessage}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val entries = snapshot.documents.mapNotNull { doc ->
                                documentToWorkEntry(doc)
                            }
                            trySend(entries)
                        }
                    }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Flow subscription error: ${t.localizedMessage}", t)
        }

        awaitClose {
            try {
                registration?.remove()
            } catch (t: Throwable) {
                Log.w(TAG, "Error removing listener registration: ${t.localizedMessage}")
            }
        }
    }

    /**
     * Save or update shift in `users/{userId}/shifts/{shiftId}`.
     */
    fun saveShift(
        userId: String,
        entry: WorkEntry,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (userId.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        try {
            val firestore = getFirestoreSafely()
            if (firestore == null) {
                onComplete?.invoke(false)
                return
            }

            val docId = entry.id.toString()
            val shiftMap = shiftToFirestoreMap(entry)

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SHIFTS)
                .document(docId)
                .set(shiftMap, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Shift $docId saved to Firestore for user $userId")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed saving shift $docId to Firestore: ${e.localizedMessage}")
                    onComplete?.invoke(false)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Safeguard caught saveShift exception: ${t.localizedMessage}", t)
            onComplete?.invoke(false)
        }
    }

    /**
     * Delete shift from `users/{userId}/shifts/{shiftId}`.
     */
    fun deleteShift(
        userId: String,
        shiftId: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (userId.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        try {
            val firestore = getFirestoreSafely()
            if (firestore == null) {
                onComplete?.invoke(false)
                return
            }

            val docId = shiftId.toString()
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SHIFTS)
                .document(docId)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Shift $docId deleted from Firestore for user $userId")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed deleting shift $docId from Firestore: ${e.localizedMessage}")
                    onComplete?.invoke(false)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Safeguard caught deleteShift exception: ${t.localizedMessage}", t)
            onComplete?.invoke(false)
        }
    }

    /**
     * Update payment status in `users/{userId}/shifts/{shiftId}`.
     */
    fun updatePaymentStatus(
        userId: String,
        shiftId: Int,
        isPaid: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (userId.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        try {
            val firestore = getFirestoreSafely()
            if (firestore == null) {
                onComplete?.invoke(false)
                return
            }

            val docId = shiftId.toString()
            val updates = mapOf(
                "isPaid" to isPaid,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SHIFTS)
                .document(docId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Payment status for shift $docId updated to $isPaid in Firestore")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed updating payment status for shift $docId in Firestore: ${e.localizedMessage}")
                    onComplete?.invoke(false)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Safeguard caught updatePaymentStatus exception: ${t.localizedMessage}", t)
            onComplete?.invoke(false)
        }
    }
}
