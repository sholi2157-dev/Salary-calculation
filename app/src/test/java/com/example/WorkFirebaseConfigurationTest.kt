package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks packaged resources only; never contacts Firebase or real user records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkFirebaseConfigurationTest {
    @Test fun configuredAccountsDoNotEnableUnverifiedCloudOrGoogle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedPackage = if (BuildConfig.BUILD_TYPE == "preview")
            "com.aistudio.worktracker.qztvdw.preview" else "com.aistudio.worktracker.qztvdw"
        assertEquals(expectedPackage, BuildConfig.APPLICATION_ID)
        assertFalse(BuildConfig.CLOUD_SYNC_ENABLED)
        assertFalse(BuildConfig.GOOGLE_SIGN_IN_ENABLED)
        val options = FirebaseOptions.fromResource(context)
        if (!BuildConfig.ACCOUNTS_ENABLED) {
            assertNull(options)
            return
        }
        assertNotNull(options)
        assertTrue(BuildConfig.VERSIONED_SYNC_ENABLED)
        assertEquals("workshiftsapp", options!!.projectId)
        assertFalse(options.apiKey.isBlank())
        assertTrue(options.applicationId.startsWith("1:998899976228:android:"))
        if (BuildConfig.BUILD_TYPE == "preview") {
            assertEquals("1:998899976228:android:3b5406564d6a5db6ffb0b1", options.applicationId)
        }
    }
}
