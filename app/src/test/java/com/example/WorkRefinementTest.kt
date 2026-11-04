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
    @Test fun crashDetailDropsTheMessage() {
        val detail=DiagnosticLog.crashDetail(IllegalStateException("private note 123"))
        assertTrue(detail.startsWith("java.lang.IllegalStateException"))
        assertFalse(detail.contains("private note"))
    }
}
