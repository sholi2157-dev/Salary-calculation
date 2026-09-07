package com.example

import com.example.data.WorkTableImport
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class WorkTableImportTest {
    @Test fun finalEmptyNotesColumnSurvivesClipboardImport() {
        val e = WorkTableImport.decode("category\tdate\thours\trate\tnotes\nWork\t2026-09-07\t2\t50\t").entries.single()
        assertEquals("", e.notes)
    }
    @Test(expected=IllegalArgumentException::class) fun duplicateFinancialHeadersRequireReview() {
        WorkTableImport.decode("category,date,hours,rate,rate\nWork,2026-09-07,2,50,60")
    }
    @Test fun originalSevenColumnClipboardImportsSavedAmount() {
        val text = "קטגוריה\tתאריך\tשעות\tתעריף שעתי\tשכר לתשלום\tסטטוס\tהערות\nעבודה\t26/03/2026\t5.8\t40\t233.33\tשולם\tהערה"
        val e = WorkTableImport.decode(text).entries.single()
        assertEquals(233.33,e.totalEarnings,0.0)
        assertEquals("₪",e.currency)
        assertTrue(e.isPaid)
        assertEquals("2026-03-26",SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(e.date))
    }
    @Test fun reorderedEnglishCsvPreservesQuotedMultilineNotesAndDollars() {
        val text = "notes,date,category,hours,rate,total,paid,currency\r\n\"first, line\nsecond \"\"quoted\"\"\",2026-09-07,Work,4,$50,$200,false,USD"
        val e = WorkTableImport.decode(text).entries.single()
        assertEquals("first, line\nsecond \"quoted\"",e.notes)
        assertEquals("$",e.currency)
        assertFalse(e.isPaid)
    }
    @Test fun originalElevenColumnCsvHeaderAcceptsUnquotedHebrewQuote() {
        val text = "מזהה,מעסיק/קטגוריה,תאריך,שעות,תעריף שעתי,סה\"כ רווח,סטטוס תשלום,סוג דיווח,שעת כניסה,שעת יציאה,הערות\n9,עבודה,07/09/2026,2,50,100,ממתין,טווח שעות,08:00,10:00,בדיקה"
        val e=WorkTableImport.decode(text).entries.single()
        assertTrue(e.isTimeRange); assertEquals("08:00",e.startTime)
    }
    @Test fun semicolonDecimalCommaAndExcelDate() {
        val e=WorkTableImport.decode("category;date;hours;rate;total\nWork;45000;2:30;50,5;126,25").entries.single()
        assertEquals(2.5,e.hours,0.0);assertEquals(50.5,e.hourlyRate,0.0)
    }
    @Test fun invalidRowPreventsPartialImportAndReportsPosition() {
        try {
            WorkTableImport.decode("category\tdate\thours\trate\nWork\t2026-09-07\t2\t50\nWork\t31/02/2026\t2\t50")
            fail("Invalid date accepted")
        } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("שורה 3")) }
    }
    @Test(expected=IllegalArgumentException::class) fun ambiguousNumberRequiresClarification() { WorkTableImport.number("1,234") }
    @Test(expected=IllegalArgumentException::class) fun negativeAmountsCannotBecomePositive() { WorkTableImport.number("-50") }
    @Test(expected=IllegalArgumentException::class) fun unknownStatusCannotBecomePaid() {
        WorkTableImport.decode("category,date,hours,rate,paid\nWork,2026-09-07,2,50,maybe")
    }
}
