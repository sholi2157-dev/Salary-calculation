package com.example.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Hebrew dates and the weekly portion (owner feedback 2.3): "כ״ח חשוון (8/11)" everywhere, "שבוע פרשת וירא" in History.
 * The fixed arithmetic calendar (Reingold & Dershowitz), by civil day (no sunset rule). Same code in web/v2/hebdate.js;
 * both are checked against the shared fixture hebrew-dates.json (made with @hebcal/core).
 */
object HebrewDate {
    private const val EPOCH = -1373427L
    private const val UNIX_RD = 719163L

    fun isLeap(y: Int): Boolean = (7 * y + 1).mod(19) < 7
    private fun elapsed(y: Int): Long {
        val m = Math.floorDiv(235L * y - 234, 19L)
        val p = 12084L + 13753L * m
        val d = 29L * m + Math.floorDiv(p, 25920L)
        return if ((3 * (d + 1)).mod(7L) < 3) d + 1 else d
    }
    private fun correction(y: Int): Int {
        val a = elapsed(y - 1); val b = elapsed(y); val c = elapsed(y + 1)
        return if (c - b == 356L) 2 else if (b - a == 382L) 1 else 0
    }
    private fun newYear(y: Int): Long = EPOCH + elapsed(y) + correction(y)
    private fun yearDays(y: Int): Long = newYear(y + 1) - newYear(y)
    /** Months as in the books: 1 ניסן … 6 אלול, 7 תשרי … 12 אדר (אדר א׳ in a leap year), 13 אדר ב׳. */
    private fun monthDays(y: Int, m: Int): Int = when {
        m in setOf(2, 4, 6, 10, 13) -> 29
        m == 12 -> if (isLeap(y)) 30 else 29
        m == 8 -> if (yearDays(y) == 355L || yearDays(y) == 385L) 30 else 29
        m == 9 -> if (yearDays(y) == 353L || yearDays(y) == 383L) 29 else 30
        else -> 30
    }
    private fun last(y: Int) = if (isLeap(y)) 13 else 12
    private fun toRd(y: Int, m: Int, d: Int): Long {
        var r = newYear(y) + d - 1
        if (m < 7) { for (i in 7..last(y)) r += monthDays(y, i); for (i in 1 until m) r += monthDays(y, i) }
        else for (i in 7 until m) r += monthDays(y, i)
        return r
    }
    private fun civil(millis: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    /** Days since 1970-01-01 of a civil date (no java.time: it needs API 26). */
    private fun epochDay(year: Int, m: Int, d: Int): Long {
        val y = (if (m <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L); val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** Year, month and day of the civil date of [millis] (device time zone). */
    fun fromMillis(millis: Long): Triple<Int, Int, Int> {
        val (cy, cm, cd) = civil(millis)
        val rd = epochDay(cy, cm, cd) + UNIX_RD
        var y = Math.floorDiv((rd - EPOCH) * 98496L, 35975351L).toInt() - 1
        while (newYear(y + 1) <= rd) y++
        var m = if (rd < toRd(y, 1, 1)) 7 else 1
        while (rd > toRd(y, m, monthDays(y, m))) m++
        return Triple(y, m, (rd - toRd(y, m, 1) + 1).toInt())
    }

    private val ONES = listOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val TENS = listOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val HUND = listOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")
    /** 28 → "כ״ח", 15 → "ט״ו", 5787 → "תשפ״ז". */
    fun gematria(value: Int): String {
        val n = value % 1000; val r = n % 100
        val s = HUND[n / 100] + when (r) { 15 -> "טו"; 16 -> "טז"; else -> TENS[r / 10] + ONES[r % 10] }
        return if (s.length > 1) s.dropLast(1) + "\u05F4" + s.last() else s + "\u05F3"
    }
    private val MONTHS = listOf("", "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול", "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳")
    fun monthName(y: Int, m: Int): String = if (m == 12 && isLeap(y)) "אדר א׳" else MONTHS[m]
    /** "כ״ח חשוון". */
    fun day(millis: Long): String { val (y, m, d) = fromMillis(millis); return gematria(d) + " " + monthName(y, m) }
    /** "כ״ח חשוון (8/11)"; another year adds both years: "כ״ח חשוון תשפ״ו (8/11/2025)". */
    fun label(millis: Long, withYear: Boolean = civil(millis).first != civil(System.currentTimeMillis()).first): String {
        val (cy, cm, cd) = civil(millis)
        val hy = fromMillis(millis).first
        return day(millis) + (if (withYear) " " + gematria(hy) else "") + " ($cd/$cm" + (if (withYear) "/$cy" else "") + ")"
    }

    private const val ALPHA = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#%&"
    private val NAMES = listOf("פרשת שמות", "פרשת וארא", "פרשת בא", "פרשת בשלח", "פרשת יתרו", "פרשת משפטים", "פרשת תרומה", "פרשת תצווה", "פרשת כי תשא", "פרשת ויקהל", "פרשת פקודי", "פרשת ויקרא", "פרשת צו", "פרשת שמיני", "פרשת תזריע", "פרשת מצורע", "פסח", "פרשת אחרי מות", "פרשת קדשים", "פרשת אמור", "פרשת בהר", "פרשת בחוקותי", "פרשת במדבר", "פרשת נשא", "פרשת בהעלותך", "פרשת שלח לך", "פרשת קורח", "פרשת חוקת", "פרשת בלק", "פרשת פינחס", "פרשת מטות-מסעי", "פרשת דברים", "פרשת ואתחנן", "פרשת עקב", "פרשת ראה", "פרשת שופטים", "פרשת כי תצא", "פרשת כי תבוא", "פרשת נצבים-וילך", "פרשת האזינו", "יום כיפור", "סוכות", "פרשת בראשית", "פרשת נח", "פרשת לך לך", "פרשת וירא", "פרשת חיי שרה", "פרשת תולדות", "פרשת ויצא", "פרשת וישלח", "פרשת וישב", "פרשת מקץ", "פרשת ויגש", "פרשת ויחי", "פרשת תזריע-מצורע", "פרשת אחרי מות-קדשים", "פרשת בהר-בחוקותי", "פרשת נצבים", "פרשת וילך", "פרשת ויקהל-פקודי", "שבועות", "פרשת חוקת-בלק", "ראש השנה", "פרשת מטות", "פרשת מסעי")
    private const val DIASPORA = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMyNOPQzTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMyNOPQzTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGGHIJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmno"
    private const val ISRAEL = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRST#%VWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmno"
    private val FIRST = epochDay(2024, 1, 6)
    fun inIsrael(): Boolean = TimeZone.getDefault().id == "Asia/Jerusalem"
    /** The portion read on the Shabbat that ends the week starting [weekStart] (Sunday): "פרשת וירא", "פסח". Null outside the table. */
    fun portion(weekStart: Long, israel: Boolean = inIsrael()): String? {
        val (y, m, d) = civil(weekStart)
        val i = Math.floorDiv(epochDay(y, m, d) + 6 - FIRST, 7L).toInt()
        val s = if (israel) ISRAEL else DIASPORA
        return if (i in s.indices) NAMES[ALPHA.indexOf(s[i])] else null
    }
}
