package com.example.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every text the app shares or copies (docs/v2-sharing.md; expected outputs in app/src/test/resources/v2-share-fixtures.json).
 * The RC16 wording is the base. Amounts are always the stored ones (WorkPay), never recalculated here,
 * and a missing rate or amount is said in words, never shown as 0.
 */
object WorkShareText {
    fun date(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(millis))
    fun money(amount: Double, currency: String): String = currency + String.format(Locale.US, "%.2f", amount)
    /** 8 → "8", 7.5 → "7.5", 7.300000000000001 → "7.3". */
    fun hours(h: Double): String = BigDecimal(h).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private const val TOTAL = "סה\"כ לתשלום"

    /** One personal shift, or my own part of a group shift (nothing about the others). */
    fun personal(e: WorkEntry): String {
        if (WorkPay.isJob(e)) return job(e)
        val pay = WorkPay.pay(e)
        return buildList {
            add("היי, להלן פרטי המשמרת שלי מיום ${date(e.date)}:")
            add("קטגוריה: ${e.category}")
            add("שעות עבודה: ${hours(e.hours)} שעות")
            add("תעריף שעתי: " + if (pay == null) "עדיין לא ידוע" else money(e.hourlyRate, e.currency))
            if (e.bonus > 0) add("בונוס: ${money(e.bonus, e.currency)}")
            add("$TOTAL: " + (pay?.let { money(it, e.currency) } ?: "ממתין להשלמת תעריף"))
        }.joinToString("\n")
    }

    /** A fixed-price job: no hourly rate; the working time only if it was entered. */
    fun job(e: WorkEntry): String {
        val pay = WorkPay.pay(e)
        return buildList {
            add("היי, להלן פרטי העבודה שלי מיום ${date(e.date)}:")
            if (e.notes.isNotBlank()) add("עבודה: ${e.notes.trim()}")
            add("קטגוריה: ${e.category}")
            if (e.hours > 0) add("זמן עבודה: ${hours(e.hours)} שעות")
            if (e.bonus > 0) add("בונוס: ${money(e.bonus, e.currency)}")
            add("$TOTAL: " + (pay?.let { money(it, e.currency) } ?: "ממתין להשלמה"))
        }.joinToString("\n")
    }

    /**
     * For the employer of a group shift: my part and every worker at the employer rate (a team bonus
     * is passed through, as in WorkPay.group). Never the worker rate or my margin.
     */
    fun employer(e: WorkEntry, ownName: String? = null): String {
        val own = WorkPay.pay(e)
        val workers = WorkPay.workers(e)
        val lines = workers.map { w -> w.name to WorkPay.employerPay(w, e) }
        val total = if (own == null || lines.any { it.second == null }) null else Math.round((own + lines.sumOf { it.second!! }) * 100.0) / 100.0
        return buildList {
            add("היי, להלן סיכום שעות עבודה ליום ${date(e.date)}:")
            add("**$TOTAL (כולל כולם): ${total?.let { money(it, e.currency) } ?: "עוד חסר תעריף"}**")
            add("---")
            add("פירוט:")
            // With a profile name the report lists me by name like everyone else ("שולי: …"); otherwise "החלק שלי".
            add("${ownName?.trim()?.takeIf { it.isNotEmpty() } ?: "החלק שלי"}: ${hours(e.hours)} שעות (${own?.let { money(it, e.currency) } ?: "תעריף חסר"})")
            workers.zip(lines).forEach { (w, l) -> add("${w.name}: ${hours(w.hours)} שעות (${l.second?.let { money(it, e.currency) } ?: "תעריף חסר"})") }
            add("---")
        }.joinToString("\n")
    }

    /** For one worker only: their hours, their pay at the worker rate (with their bonus) and whether it was paid. */
    fun worker(e: WorkEntry, w: WorkPay.Worker): String {
        val pay = WorkPay.workerPay(w, e)
        return buildList {
            add("היי ${w.name}, להלן פירוט שעות עבודה שלך מיום ${date(e.date)}:")
            add("עבדת ${hours(w.hours)} שעות. מגיע לך: " + (pay?.let { money(it, e.currency) + (if (w.bonus > 0) " (כולל בונוס ${money(w.bonus, e.currency)})" else "") } ?: "עוד לא ידוע") + ".")
            add("סטטוס תשלום: ${if (w.isPaid) "שולם" else "ממתין"}")
        }.joinToString("\n")
    }

    /** A planned shift (stage C): clearly marked as planned; the amount is only an estimate, never "to pay". */
    fun planned(o: com.example.domain.V2Plan.Occurrence): String {
        val h = com.example.domain.V2Rules.span(o.start, o.end).minutes / 60.0
        return buildList {
            add("היי, להלן פרטי משמרת מתוכננת ליום ${date(com.example.domain.V2Plan.startMillis(o.date, "12:00"))}:")
            add("קטגוריה: ${o.category}")
            add("שעות: ${o.start}–${o.end} (${hours(h)} שעות)")
            add("תעריף שעתי: " + (o.rate?.let { money(it, o.currency) } ?: "עדיין לא ידוע"))
            add("צפי (עוד לא בוצעה): " + (o.rate?.let { money(WorkPay.amount(h, it), o.currency) } ?: "ממתין להשלמת תעריף"))
        }.joinToString("\n")
    }

    /** WhatsApp summary of exactly the given rows: per category, hours and one amount per currency. */
    fun summary(entries: List<WorkEntry>, workerName: String? = null): String = WorkMoney.report(entries, workerName)

    private val excelHeaders = listOf("קטגוריה", "תאריך", "שעות", "תעריף שעתי", "שכר לתשלום", "סטטוס", "הערות", "מטבע", "סוג", "שכר ידוע", "בונוס")

    /** Tab-separated table for Excel / Google Sheets. RC16 columns first, 2.0 columns only appended. */
    fun excel(entries: List<WorkEntry>): String {
        fun n(v: Double) = String.format(Locale.US, "%.2f", v)
        fun cell(s: String) = if (s.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val rows = entries.map { e ->
            listOf(
                e.category, date(e.date), n(e.hours),
                if (!e.payKnown || WorkPay.isJob(e)) "" else n(e.hourlyRate),
                if (!e.payKnown) "" else n(e.totalEarnings),
                if (e.isPaid) "שולם" else "ממתין",
                e.notes, e.currency,
                when { WorkPay.isJob(e) -> "קבלנות"; e.isGroupShift -> "משמרת קבוצתית"; else -> "משמרת" },
                if (e.payKnown) "כן" else "חסר",
                if (e.bonus > 0) n(e.bonus) else ""
            ).joinToString("\t", transform = ::cell)
        }
        return (listOf(excelHeaders.joinToString("\t")) + rows).joinToString("\n")
    }
}
