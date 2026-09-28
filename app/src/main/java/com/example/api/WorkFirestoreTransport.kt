package com.example.api

import com.example.data.WorkRemoteRecord
import com.example.data.WorkSyncTransport
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Versioned documents never share paths with the retired integer-ID writer. */
class WorkFirestoreTransport(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : WorkSyncTransport {
    private fun records(uid: String) = firestore.collection("users").document(uid).collection("records_v1")
    private fun checkOwner(uid: String) { check(auth.currentUser?.uid == uid) { "Account changed" } }
    override suspend fun readAll(uid: String): List<WorkRemoteRecord> {
        checkOwner(uid)
        // A cache-only snapshot cannot declare a fresh sync successful.
        val snapshot = records(uid).get(Source.SERVER).awaitResult()
        checkOwner(uid)
        return snapshot.documents.map { decode(it) }
    }
    override suspend fun exchange(uid: String, baseVersion: Long, proposed: WorkRemoteRecord): WorkRemoteRecord {
        checkOwner(uid)
        proposed.validate()
        require(proposed.version == baseVersion + 1)
        val ref = records(uid).document(proposed.syncId)
        return firestore.runTransaction { transaction ->
            checkOwner(uid)
            val snapshot = transaction.get(ref)
            val current = if (snapshot.exists()) decode(snapshot) else null
            if (current?.operation == proposed.operation) current
            else if ((current?.version ?: 0) != baseVersion) {
                checkNotNull(current) { "Remote record removed unexpectedly" }
            } else {
                transaction.set(ref, mapOf("schema" to 1, "type" to proposed.type,
                    "version" to proposed.version, "operation" to proposed.operation,
                    "deleted" to proposed.deleted, "payload" to proposed.payload))
                proposed
            }
        }.awaitResult().also { checkOwner(uid) }
    }
    private fun decode(doc: DocumentSnapshot): WorkRemoteRecord {
        require(doc.getLong("schema") == 1L)
        return WorkRemoteRecord(doc.id, checkNotNull(doc.getString("type")),
            checkNotNull(doc.getLong("version")), checkNotNull(doc.getString("operation")),
            checkNotNull(doc.getBoolean("deleted")), doc.getString("payload")).validate()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed"))
        }
    }
}
