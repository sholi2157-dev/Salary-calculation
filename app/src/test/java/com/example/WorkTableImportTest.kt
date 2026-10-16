package com.example

import com.example.data.WorkTableImport
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class WorkTableImportTest {
    private fun rejected(text: String) {
        try {
            WorkTableImport.decode(text)
            fail("Malformed or ambiguous input accepted: $text")
        } catch (_: IllegalArgumentException) {
            // Row validation rejected the entire import.
        } catch (_: IllegalStateException) {
            // Structural validation rejected the entire table.
        }
    }
    @Test fun expandedHebrewHeadersPreserveExplicitAmountAndTimeRange() {
        val entry = WorkTableImport.decode(
            "שם מעסיק\tתאריך משמרת\tמספר שעות\tתעריף לשעה\tסכום\tשעת התחלה\tשעת סיום\n" +
                "עבודה\t2026-09-07\t2\t50\t101\t08:00\t10:00"
        ).entries.single()
        assertEquals("עבודה", entry.category)
        assertEquals(50.0, entry.hourlyRate, 0.0)
        assertEquals(101.0, entry.totalEarnings, 0.0)
        assertEquals("08:00", entry.startTime)
        assertTrue(entry.isTimeRange)
    }
    @Test fun excelSeparatorDeclarationsSupportAllKnownDelimiters() {
        for (delimiter in listOf('\t', ',', ';', '|')) {
            val text = "\uFEFFSeP=$delimiter\r\n" +
                listOf("employer", "work date", "work hours", "hourly rate").joinToString("$delimiter") +
                "\r\n" + listOf("Work", "2026-09-07", "2", "50").joinToString("$delimiter")
            assertEquals(100.0, WorkTableImport.decode(text).entries.single().totalEarnings, 0.0)
        }
        rejected("sep=;\ncategory,date,hours,rate\nWork,2026-09-07,2,50")
    }
    @Test fun trimmedOptionalFinalCellsAndExtraEmptyCellsAreAccepted() {
        val header = "category\tdate\thours\trate\tnotes\tpaid"
        val entries = WorkTableImport.decode("$header\nWork\t2026-09-07\t2\t50\nWork\t2026-09-08\t3\t60\t\t\t\t").entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.notes.isEmpty() && !it.isPaid })
        assertEquals(180.0, entries[1].totalEarnings, 0.0)
        rejected("$header\nWork\t2026-09-07\t2")
        rejected("$header\nWork\t2026-09-07\t2\t50\t\t\tlost data")
    }
    @Test fun excelClockFractionsNormalizeOnlyClockColumns() {
        val e = WorkTableImport.decode(
            "category;date;hours;rate;start time;end time\nWork;45000,0;0,5;50;0,5;0.520833333333333"
        ).entries.single()
        assertEquals(0.5, e.hours, 0.0)
        assertEquals(25.0, e.totalEarnings, 0.0)
        assertEquals("12:00", e.startTime)
        assertEquals("12:30", e.endTime)
        assertEquals("2023-03-15", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(e.date))
    }
    @Test fun meridiemAndZeroSecondsNormalizeIncludingMidnightAndNoon() {
        val header = "category,date,hours,rate,start,end"
        val entries = WorkTableImport.decode(
            "$header\nWork,2026-09-07,12,50,12:00 AM,12:00 PM\nWork,2026-09-08,2,50,9:05:00 pm,11:05pm"
        ).entries
        assertEquals("00:00", entries[0].startTime)
        assertEquals("12:00", entries[0].endTime)
        assertEquals("21:05", entries[1].startTime)
        assertEquals("23:05", entries[1].endTime)
    }
    @Test fun durationSecondsArePreservedAndDurationsMayExceedOneDay() {
        val entries = WorkTableImport.decode(
            "category,date,hours,rate\nWork,2026-09-07,2:30:30,3600\nWork,2026-09-08,25:00:00,50"
        ).entries
        assertEquals(2.5 + 30.0 / 3600, entries[0].hours, 1e-12)
        assertEquals(9030.0, entries[0].totalEarnings, 1e-8)
        assertEquals(25.0, entries[1].hours, 0.0)
    }
    @Test fun malformedClockOrSubminutePrecisionRequiresCorrection() {
        for (clock in listOf("24:00", "00:00 AM", "13:00 PM", "12:60", "12:30:01", "1", "-0.5", "0.5001", "$0.5", "0.5USD")) {
            rejected("category;date;hours;rate;start;end\nWork;2026-09-07;2;50;$clock;14:00")
        }
        rejected("category,date,hours,rate,start,end\nWork,2026-09-07,2,50,12:00")
    }
    @Test fun malformedDurationAndFractionalDateAreNotGuessed() {
        for (duration in listOf("2:60", "2:30:60", "-2:30", "2:3", "NaN", "Infinity")) {
            rejected("category,date,hours,rate\nWork,2026-09-07,$duration,50")
        }
        rejected("category,date,hours,rate\nWork,45000.5,2,50")
        rejected("category,date,hours,rate\nWork,09/07/26,2,50")
        rejected("category;date;hours;rate\nWork;2026-09-07;2;1,234")
    }
    @Test fun duplicateAliasesAndConflictingCurrencyAreRejected() {
        rejected("category,date,hours,rate,hourly rate\nWork,2026-09-07,2,50,60")
        rejected("category,date,hours,rate,total,currency\nWork,2026-09-07,2,$50,100,ILS")
    }
    @Test fun malformedQuotingCannotSilentlyChangeNotes() {
        rejected("category,date,hours,rate,notes\nWork,2026-09-07,2,50,\"unfinished")
        rejected("category,date,hours,rate,notes\nWork,2026-09-07,2,50,\"first\"second")
        val e = WorkTableImport.decode(
            "category,date,hours,rate,notes\n\"Work\" ,2026-09-07,2,50,\"quoted note\" "
        ).entries.single()
        assertEquals("Work", e.category)
        assertEquals("quoted note", e.notes)
    }
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
