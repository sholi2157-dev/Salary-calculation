package com.example
import com.example.ui.*
import com.example.data.WorkEntry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class WorkRefinementTest {
    private fun row(c:String)=WorkEntry(category="private category",date=1,isTimeRange=false,hours=2.0,hourlyRate=40.0,totalEarnings=80.0,currency=c,notes="private note")
    @Test fun summaryPagesAreDynamicAndTotalsStaySeparate() {
        assertEquals(emptyList<String>(),SummaryPages.currencies(emptyList()))
        assertEquals(listOf("$"),SummaryPages.currencies(listOf(row("$"))))
        assertEquals(listOf("₪","$"),SummaryPages.currencies(listOf(row("$"),row("₪"))))
        val text=SummaryPages.compact(listOf(row("$"),row("₪")))
        assertFalse(text.contains("\n"));assertFalse(text.contains("160"));assertTrue(text.contains("$80.00"));assertTrue(text.contains("₪80.00"))
    }
    @Test fun feedbackOnlyIncludesExplicitTextAndAllowlistedMetadata() {
        val draft=SalaryFeedback.draft("תקלה","test message","1.5-rc2",8,"15","Synthetic","Phone")
        val intent=SalaryFeedback.intent(draft)
        assertEquals(android.content.Intent.ACTION_SENDTO,intent.action)
        assertEquals("mailto",intent.data!!.scheme)
        assertTrue(intent.data!!.schemeSpecificPart.startsWith(SalaryFeedback.RECIPIENT+"?"))
        assertEquals("[תקלה] שכר עבודות 1.5-rc2",draft.subject)
        assertEquals("סוג: תקלה\nגרסה: 1.5-rc2 (8)\nAndroid: 15\nמכשיר: Synthetic Phone\n\ntest message",draft.body)
        assertNull(intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM))
        assertFalse(draft.body.contains(row("₪").category));assertFalse(draft.body.contains(row("₪").notes))
    }
    @Test fun feedbackReportCarriesOnlyTheAllowlist() {
        val device=FeedbackReport.Device("Synthetic","Phone","15",35,"he-IL","Asia/Jerusalem",400,880,1f,false)
        val events=org.json.JSONArray().put(org.json.JSONObject().put("at",1).put("e","page").put("d","settings"))
        val r=FeedbackReport.build("תקלה","  test message ","2.1.1",43,device,mapOf("entries" to 3,"signedIn" to true),events,99)
        assertEquals(FeedbackReport.TOKEN,r.getString("token"))
        assertEquals("test message",r.getString("message"))
        assertEquals("android",r.getJSONObject("app").getString("platform"))
        assertEquals(43,r.getJSONObject("app").getLong("code"))
        assertEquals("400x880",r.getJSONObject("device").getString("screenDp"))
        assertEquals(3,r.getJSONObject("state").getInt("entries"))
        assertEquals("settings",r.getJSONArray("events").getJSONObject(0).getString("d"))
        assertFalse(r.has("screenshot"))
        assertFalse(r.toString().contains(row("₪").category));assertFalse(r.toString().contains(row("₪").notes))
        assertTrue(FeedbackReport.accepted("{\"ok\":true,\"note\":\"\"}"))
        assertFalse(FeedbackReport.accepted("{\"ok\":false,\"note\":\"limit\"}"))
        assertFalse(FeedbackReport.accepted("<html>"))
    }
    @Test fun crashDetailDropsTheMessage() {
        val detail=DiagnosticLog.crashDetail(IllegalStateException("private note 123"))
        assertTrue(detail.startsWith("java.lang.IllegalStateException"))
        assertFalse(detail.contains("private note"))
    }
}
