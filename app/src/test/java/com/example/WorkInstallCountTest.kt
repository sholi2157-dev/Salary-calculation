package com.example

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkInstallCountTest {
    @Test fun recordCarriesOnlyIdentifierVersionKindAndServerTime() {
        val write = JSONObject(InstallCount.body("a".repeat(32), "2.0.7", "update")).getJSONArray("writes").getJSONObject(0)
        val update = write.getJSONObject("update")
        assertEquals("projects/workshiftsapp/databases/(default)/documents/installs/" + "a".repeat(32), update.getString("name"))
        assertEquals(setOf("version", "kind"), update.getJSONObject("fields").keys().asSequence().toSet())
        assertFalse(write.getJSONObject("currentDocument").getBoolean("exists"))
        assertEquals("REQUEST_TIME", write.getJSONArray("updateTransforms").getJSONObject(0).getString("setToServerValue"))
    }
    @Test fun alreadyCountedDeviceIsNotRetriedButRefusalsAre() {
        assertTrue(InstallCount.accepted(200, "{}"))
        assertTrue(InstallCount.accepted(409, "ALREADY_EXISTS"))
        assertTrue(InstallCount.accepted(400, "FAILED_PRECONDITION"))
        assertFalse(InstallCount.accepted(403, "PERMISSION_DENIED"))
        assertFalse(InstallCount.accepted(500, ""))
    }
    @Test fun identifierIsStableAndNotSentOutsideDistributedBuilds() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = InstallCount.id(context)
        assertTrue(id.matches(Regex("^[a-f0-9]{32}$")))
        assertEquals(id, InstallCount.id(context))
        InstallCount.schedule(context) // Debug build: no key, nothing is scheduled or sent.
        assertFalse(InstallCount.sent(context))
    }
}
