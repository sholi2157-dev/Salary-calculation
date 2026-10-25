package com.example.api

import com.example.data.WorkRemotePage
import com.example.data.WorkRemoteRecord
import com.example.data.WorkSyncTransport
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Versioned documents never share paths with the retired integer-ID writer. The same documents as the website
 * (web/sync/firestore.js): every write stamps updatedAt with the server time, so a device reads only what changed.
 * A document that fails validation is skipped, never applied.
 */
class WorkFirestoreTransport(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : WorkSyncTransport {
    private fun records(uid: String) = firestore.collection("users").document(uid).collection("records_v1")
    private fun checkOwner(uid: String) { check(auth.currentUser?.uid == uid) { "Account changed" } }
    override suspend fun read(uid: String, cursor: Long?): WorkRemotePage {
        checkOwner(uid)
        val query = if (cursor == null) records(uid) else records(uid).whereGreaterThanOrEqualTo("updatedAt", Timestamp(java.util.Date(cursor)))
        // A cache-only snapshot cannot declare a fresh sync successful.
        val snapshot = query.get(Source.SERVER).awaitResult()
        checkOwner(uid)
        var next = cursor
        val out = snapshot.documents.mapNotNull { doc ->
            doc.getTimestamp("updatedAt")?.let { next = maxOf(next ?: 0L, it.toDate().time) }
            runCatching { decode(doc) }.onFailure { android.util.Log.w("WorkSync", "Skipped an unreadable sync record ${doc.id}") }.getOrNull()
        }
        return WorkRemotePage(out, next)
    }
    /** Calls back when another device, the website or an assistant writes; remove() stops it. */
    fun listen(uid: String, since: Long, onChange: () -> Unit) =
        records(uid).whereGreaterThanOrEqualTo("updatedAt", Timestamp(java.util.Date(since))).addSnapshotListener { snap, error ->
            if (error == null && snap != null && !snap.metadata.hasPendingWrites() && snap.documentChanges.isNotEmpty()) onChange()
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
                    "deleted" to proposed.deleted, "payload" to proposed.payload, "updatedAt" to FieldValue.serverTimestamp()))
                proposed
            }
        }.awaitResult().also { checkOwner(uid) }
    }
    // ---- personal AI assistants (lib/server/assistant.cjs on the website writes these, as the person) ----
    private fun me(uid: String) = firestore.collection("users").document(uid)

    /** Assistants allowed in now; an expired one is already refused by the server and is not shown. */
    suspend fun assistants(uid: String): List<AssistantGrant> {
        checkOwner(uid)
        val now = System.currentTimeMillis()
        return me(uid).collection("assistants").get(Source.SERVER).awaitResult().documents.mapNotNull { d ->
            val until = d.getString("until")?.let(::isoMillis)
            if (until != null && until <= now) null
            else AssistantGrant(d.id, d.getString("name").orEmpty(), (d.get("scopes") as? List<*>)?.filterIsInstance<String>() ?: emptyList(), until)
        }
    }

    suspend fun assistantActions(uid: String, limit: Long = 20): List<AssistantAction> {
        checkOwner(uid)
        return me(uid).collection("assistant_log").orderBy("at", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(limit)
            .get(Source.SERVER).awaitResult().documents.mapNotNull { d ->
                AssistantAction(d.id, d.getString("client").orEmpty(), d.getString("tool").orEmpty(), d.getString("syncId") ?: return@mapNotNull null,
                    d.getString("type") ?: "entry", d.getString("before"), d.getString("after"), d.getString("at")?.let(::isoMillis) ?: 0L, d.getBoolean("undone") == true)
            }
    }

    /** Removing the grant cuts the assistant off at once: the server checks it on every call. */
    suspend fun cutOff(uid: String, grantId: String) { checkOwner(uid); me(uid).collection("assistants").document(grantId).delete().awaitResult() }

    /**
     * Puts the item back as it was before the assistant's action, as a new version that reaches every device by sync.
     * Only while it is still what the assistant left: a later change by the person wins. Same rules as the website.
     */
    suspend fun undo(uid: String, action: AssistantAction): Boolean {
        checkOwner(uid)
        val doc = records(uid).document(action.syncId).get(Source.SERVER).awaitResult()
        val version = if (doc.exists()) doc.getLong("version") ?: 0L else 0L
        if ((if (doc.exists()) doc.getString("payload") else null) != action.after) return false
        val proposed = WorkRemoteRecord(action.syncId, action.type, version + 1, action.syncId + ":undo-" + java.util.UUID.randomUUID().toString().replace("-", ""),
            action.before == null, action.before)
        if (exchange(uid, version, proposed).operation != proposed.operation) return false
        me(uid).collection("assistant_log").document(action.id).update("undone", true).awaitResult()
        return true
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

data class AssistantGrant(val id: String, val name: String, val scopes: List<String>, val until: Long?)
data class AssistantAction(val id: String, val client: String, val tool: String, val syncId: String, val type: String,
                           val before: String?, val after: String?, val at: Long, val undone: Boolean)

/** The website writes ISO times such as 2026-10-25T15:45:46.123Z. */
internal fun isoMillis(text: String): Long? = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull {
    runCatching { java.text.SimpleDateFormat(it, java.util.Locale.ROOT).parse(text)?.time }.getOrNull()
}
