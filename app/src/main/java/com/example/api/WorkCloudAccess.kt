package com.example.api

/** A local guest is never a cloud identity. Apply at every Firestore entry point. */
object WorkCloudAccess {
    fun allowed(enabled: Boolean, requestedUid: String, authenticatedUid: String?): Boolean =
        enabled && !requestedUid.isBlank() && requestedUid == authenticatedUid
}
