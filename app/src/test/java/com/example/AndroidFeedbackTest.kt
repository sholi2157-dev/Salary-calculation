package com.example

import android.content.Intent
import com.example.ui.SalaryFeedback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidFeedbackTest {
    private fun report(message: String = "תיאור\nשורה נוספת", contact: String = "") =
        FeedbackReport.build("תקלה", message, "2.3.0", 50, "he-IL", 1793700000000L, contact)

    @Test fun relayAndMailDraftUseOnlyExplicitTextAndRequiredContext() {
        val payload = report()
        assertEquals(setOf("schema", "kind", "message", "sentAt", "screen", "language", "app"), payload.keys().asSequence().toSet())
        assertEquals("תיאור\nשורה נוספת", payload.getString("message"))
        assertEquals("settings/feedback", payload.getString("screen"))
        assertEquals("he-IL", payload.getString("language"))
        assertEquals(1793700000000L, payload.getLong("sentAt"))
        val app = payload.getJSONObject("app")
        assertEquals(setOf("platform", "version", "code", "buildType"), app.keys().asSequence().toSet())
        assertEquals("android", app.getString("platform"))
        assertEquals("2.3.0", app.getString("version"))
        assertEquals(50L, app.getLong("code"))
        assertEquals(BuildConfig.BUILD_TYPE, app.getString("buildType"))
        val body = JSONObject(SalaryFeedback.draft(payload).body)
        assertEquals(payload.toString(), body.toString())
        listOf("token", "state", "events", "device", "screenshot", "entries", "wages", "shifts").forEach { assertFalse(body.has(it)) }
        assertEquals("typed@example.com", report(contact = "typed@example.com").getString("contactEmail"))
        assertFalse(report(contact = " \r\u0000").has("contactEmail"))
    }

    @Test fun recipientIsFixedAndHeaderInjectionStaysInsideJsonMessage() {
        val attack = "hello\r\nBcc: extra@example.com\n&to=extra@example.com?subject=injected\u0000"
        val payload = report(attack, "reply@example.com\r\nBcc: other@example.com")
        val draft = SalaryFeedback.draft(payload)
        val intent = SalaryFeedback.intent(draft)
        val uri = intent.data!!
        assertEquals("israelshimon1313-vzptcg@mail.grokbot.com", SalaryFeedback.RECIPIENT)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", uri.scheme)
        val query = android.net.Uri.parse("https://local/?" + uri.encodedSchemeSpecificPart.substringAfter('?'))
        assertEquals(setOf("subject", "body"), query.queryParameterNames)
        assertEquals(SalaryFeedback.RECIPIENT, uri.schemeSpecificPart.substringBefore('?'))
        assertEquals("Android feedback", query.getQueryParameter("subject"))
        assertEquals(payload.toString(), JSONObject(query.getQueryParameter("body")!!).toString())
        assertFalse(payload.getString("message").contains('\r'))
        assertFalse(payload.getString("message").contains('\u0000'))
        assertFalse(payload.getString("contactEmail").contains('\n'))
        assertNull(intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM))
    }

    @Test fun inputIsBoundedAndControlsAreRemovedWithoutBreakingMultilineText() {
        assertEquals(4000, report("x".repeat(5000)).getString("message").length)
        assertEquals(254, report(contact = "x".repeat(500)).getString("contactEmail").length)
        assertEquals("a\nb", FeedbackReport.clean("a\r\n\u0000\t\u0085\u202eb", 4000))
    }

    @Test fun relayAcknowledgementMustBeExplicit() {
        assertTrue(FeedbackReport.accepted("{\"ok\":true}"))
        assertFalse(FeedbackReport.accepted("{\"ok\":false,\"note\":\"limit\"}"))
        assertFalse(FeedbackReport.accepted("<html>"))
    }
}
