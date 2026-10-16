package com.example

import com.example.api.WorkCloudAccess
import org.junit.Assert.*
import org.junit.Test

class WorkCloudAccessTest {
    @Test fun disabledSyncBlocksEvenAuthenticatedUser() {
        assertFalse(WorkCloudAccess.allowed(false, "user-a", "user-a"))
    }
    @Test fun guestAndDifferentAccountsCannotUseCloudPath() {
        assertFalse(WorkCloudAccess.allowed(true, "local_offline_user_id", null))
        assertFalse(WorkCloudAccess.allowed(true, "user-a", "user-b"))
        assertFalse(WorkCloudAccess.allowed(true, "", ""))
    }
    @Test fun explicitlyEnabledMatchingAccountPassesLocalGate() {
        assertTrue(WorkCloudAccess.allowed(true, "user-a", "user-a"))
    }
}
