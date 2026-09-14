package com.example.data

import java.security.MessageDigest

/** No raw UID in filenames and no account can address the legacy guest database. */
object WorkAccountStorage {
    const val GUEST_DATABASE = "sholi_work_tracker_db"

    fun databaseName(uid: String): String {
        require(uid.isNotBlank()) { "An authenticated account UID is required" }
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "sholi_account_$hex"
    }
}
