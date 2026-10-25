package com.example.data

import java.security.MessageDigest

/**
 * The account sync wire format, shared with the website (web/sync/protocol.js) and the assistant server.
 * One Firestore document per item at users/{uid}/records_v1/{syncId}:
 *   { schema: 1, type, version, operation, deleted, payload, updatedAt }
 * payload is a backup file (WorkBackup.encode) holding exactly one item of `type`, or null for a deleted item.
 * Entries, places and workers get a random syncId (the journal's). Plans, plan days and settings have a natural
 * key, so their syncId is the first 32 hex digits of SHA-256("type|key") and every device reaches the same document.
 */
object WorkSyncProtocol {
    val ROW_TYPES = setOf("entry", "category", "worker")
    val KEYED_TYPES = setOf("series", "exception", "preference")
    val TYPES = ROW_TYPES + KEYED_TYPES

    fun keyedId(type: String, key: String): String {
        require(type in KEYED_TYPES)
        return MessageDigest.getInstance("SHA-256").digest("$type|$key".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    fun exceptionKey(seriesId: String, date: String) = "$seriesId@$date"

    /** Checks that the payload holds exactly one item of the type (the same rules as an import). */
    fun decode(type: String, payload: String): WorkBackup.Contents {
        require(payload.toByteArray().size <= 500_000)
        val d = WorkBackup.decode(payload)
        require(d.entries.size == if (type == "entry") 1 else 0)
        require(d.categories.size == if (type == "category") 1 else 0)
        require(d.workers.size == if (type == "worker") 1 else 0)
        require(d.series.size == if (type == "series") 1 else 0)
        require(d.exceptions.size == if (type == "exception") 1 else 0)
        require(d.localPreferences.size == if (type == "preference") 1 else 0)
        return d
    }

    /** The natural key of a keyed item in a payload. */
    fun keyOf(type: String, payload: String): String {
        val d = decode(type, payload)
        return when (type) {
            "series" -> d.series.single().id
            "exception" -> d.exceptions.single().let { exceptionKey(it.seriesId, it.date) }
            "preference" -> d.localPreferences.keys.single()
            else -> error("Not a keyed type")
        }
    }

    /** Equal exactly when two payloads mean the same item, whatever the formatting or which device wrote it. */
    fun canonical(type: String, payload: String?): String {
        if (payload == null) return "deleted"
        val d = decode(type, payload)
        return when (type) {
            "entry" -> d.entries.single().copy(id = 0).toString()
            "category" -> d.categories.single().copy(id = 0).toString()
            "worker" -> d.workers.single().copy(id = 0).toString()
            "series" -> d.series.single().toString()
            "exception" -> d.exceptions.single().toString()
            else -> d.localPreferences.entries.single().toString()
        }
    }

    /** The same item typed on two devices before either synced: a shift by everything it holds, a place or worker by name. */
    fun sameIdentity(type: String, a: String, b: String): Boolean = when (type) {
        "category" -> decode(type, a).categories.single().name == decode(type, b).categories.single().name
        "worker" -> decode(type, a).workers.single().name == decode(type, b).workers.single().name
        else -> canonical(type, a) == canonical(type, b)
    }

    fun encodeSeries(s: ShiftSeries) = WorkBackup.encode(emptyList(), emptyList(), emptyList(), series = listOf(s))
    fun encodeException(x: SeriesException) = WorkBackup.encode(emptyList(), emptyList(), emptyList(), exceptions = listOf(x))
    fun encodePreference(name: String, value: String) = WorkBackup.encode(emptyList(), emptyList(), emptyList(), mapOf(name to value))
}
