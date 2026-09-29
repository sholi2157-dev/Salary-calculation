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
}
