package com.example

import com.example.ui.kis.dayLabel
import com.example.ui.kis.dayLabelLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

/**
 * M-38 (D-9, D-12): one neutral voice on screen ("מוסיפים", "בוחרים"), not singular commands or plural address; "דוח"
 * without gershayim; "לא שולם", not "ממתין", for unpaid; one date format ("יום ד׳ 4.11"). Shared texts and recaps
 * (KisShare, WorkShareText, V2Summary, V2Share, WorkMoney, imports) follow the shared fixtures and are not checked here.
 * Named Work* so CI runs it.
 */
class WorkWordingTest {
    private val main = listOf("src/main/java/com/example", "app/src/main/java/com/example").map(::File).first { it.isDirectory }
    private val screens = File(main, "ui/kis").listFiles { f -> f.name.endsWith(".kt") && f.name != "KisShare.kt" }!!.toList() +
        listOf("ui/WorkOnboarding.kt", "ui/WorkRefinements.kt", "domain/V2Rules.kt").map { File(main, it) }
    private val banned = listOf(
        Regex("דו״ח|דו\\\\\"ח") to "דוח without gershayim",
        Regex("(^|[^א-ת])(הזן|הוסף|בחר|סמן|לחץ|נסה|הקלד) ") to "singular command",
        Regex("(^|[^א-ת])(אתם|סיימתם|שלכם|בקשו|הרווחתם)([^א-ת]|$)") to "plural address",
        Regex("(^|[^א-ת])(אתה|תרצה|שתנתק|כשתתחבר)([^א-ת]|$)") to "singular address",
        Regex("UNPAID\\(\"ממתין\"\\)") to "\"לא שולם\" for unpaid",
    )

    @Test fun oneNeutralVoiceOnScreen() {
        for (f in screens) {
            val text = f.readLines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")
            for ((re, why) in banned) {
                val m = re.find(text)
                assertNull("${f.name}: $why: …${m?.let { text.substring(maxOf(0, it.range.first - 30), minOf(text.length, it.range.last + 30)) }}…", m)
            }
        }
    }

    @Test fun oneDateFormat() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -10) }
        assertTrue(Regex("^יום [אבגדהוש]׳ \\d{1,2}\\.\\d{1,2}$").matches(dayLabel(cal.timeInMillis)))
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        assertTrue(dayLabelLong(lastYear.timeInMillis).endsWith("." + lastYear.get(Calendar.YEAR)))
        assertEquals("היום", dayLabel(System.currentTimeMillis()))
    }
}
