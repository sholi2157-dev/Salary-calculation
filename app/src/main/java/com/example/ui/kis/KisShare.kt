package com.example.ui.kis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.example.data.WorkEntry
import com.example.data.WorkPay
import java.util.Calendar

/**
 * Text for WhatsApp, the Android share sheet and the clipboard.
 * Times and amounts are wrapped in bidi isolates (U+2066…U+2069) so they never flip inside Hebrew.
 */
object KisShare {
    private fun iso(t: String) = "⁦$t⁩"

    fun shortDate(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.DAY_OF_MONTH)}.${c.get(Calendar.MONTH) + 1}"
    }

    private fun line(e: WorkEntry): String {
        val pay = WorkPay.pay(e)
        val hours = KisFormat.hours(e.hours)
        return if (WorkPay.isJob(e)) {
            "${iso(shortDate(e.date))} · קבלנות: ${e.notes.ifBlank { e.category }}${if (e.hours > 0) " (${iso(hours)} ש׳)" else ""} · " +
                (pay?.let { iso(KisFormat.money(it, e.currency)) } ?: "סכום חסר")
        } else {
            val time = if (e.isTimeRange && e.startTime != null && e.endTime != null) "${iso("${e.startTime}–${e.endTime}")} (${iso(hours)} ש׳)" else "${iso(hours)} שעות"
            "${iso(shortDate(e.date))} · ${e.category} · $time · " + (pay?.let { iso(KisFormat.money(it, e.currency)) } ?: "תעריף חסר") +
                if (e.bonus > 0 && pay != null) " (כולל בונוס ${iso(KisFormat.money(e.bonus, e.currency))})" else ""
        }
    }

    /** Shifts in date order, then one total line. Unknown pay is listed, never counted. */
    fun text(entries: List<WorkEntry>): String {
        val rows = entries.sortedWith(compareBy<WorkEntry> { it.date }.thenBy { it.startTime ?: "" }).map(::line)
        val totals = WorkPay.totals(entries).entries.joinToString(" + ") { iso(KisFormat.money(it.value, it.key)) }.ifEmpty { "—" }
        val pending = WorkPay.pending(entries).size
        return (rows + "" + "סה״כ: ${iso(KisFormat.hours(entries.sumOf { it.hours }))} שעות · $totals${if (pending > 0) " · $pending בלי תעריף" else ""}").joinToString("\n")
    }

    /** For the employer of a group shift: everyone's hours and the total to pay. */
    fun groupSummary(e: WorkEntry): String {
        val g = WorkPay.group(e)
        val lines = StringBuilder("החלק שלי: ${iso(KisFormat.hours(e.hours))} שעות (${WorkPay.pay(e)?.let { iso(KisFormat.money(it, e.currency)) } ?: "תעריף חסר"})\n")
        WorkPay.workers(e).forEach { w ->
            lines.append("${w.name}: ${iso(KisFormat.hours(w.hours))} שעות (${WorkPay.employerPay(w, e)?.let { iso(KisFormat.money(it, e.currency)) } ?: "תעריף חסר"})\n")
        }
        return "היי, להלן סיכום שעות עבודה ליום ${iso(shortDate(e.date))}:\n" +
            "סה״כ לתשלום (כולל כולם): ${g?.let { iso(KisFormat.money(it.employerTotal, e.currency)) } ?: "עוד חסר תעריף"}\n---\nפירוט:\n$lines---"
    }

    /** For one worker of a group shift. */
    fun workerText(e: WorkEntry, w: WorkPay.Worker): String {
        val pay = WorkPay.workerPay(w, e)
        return "היי ${w.name}, להלן פירוט שעות העבודה שלך מיום ${iso(shortDate(e.date))}:\n" +
            "עבדת ${iso(KisFormat.hours(w.hours))} שעות. מגיע לך: ${pay?.let { iso(KisFormat.money(it, e.currency)) } ?: "עוד לא ידוע"}" +
            (if (w.bonus > 0) " (כולל בונוס ${iso(KisFormat.money(w.bonus, e.currency))})" else "") +
            ".\nסטטוס תשלום: ${if (w.isPaid) "שולם" else "ממתין"}"
    }

    /** The native Android share sheet (WhatsApp and every other installed target). */
    fun share(context: Context, text: String, title: String = "שיתוף") {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(context: Context, text: String, label: String = "משמרות") {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
