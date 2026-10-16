package com.example.ui.kis

import com.example.data.WorkEntry
import com.example.data.WorkPay
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

enum class Period(val label: String) { MONTH("חודש"), ALL("הכול"), RANGE("טווח") }
enum class PayFilter(val label: String) { ALL("הכול"), UNPAID("ממתין"), PAID("שולם"), PENDING("בלי תעריף") }
enum class SortOrder(val label: String) { NEWEST("תאריך: מהחדש לישן"), OLDEST("תאריך: מהישן לחדש"), ADDED("נוסף לאחרונה") }

/** History filters. Plain values so they survive rotation and can be tested. */
data class HistoryFilter(
    val period: Period = Period.MONTH,
    val month: Int = monthKey(System.currentTimeMillis()),
    val from: Long = EntryDraft.startOfDay(System.currentTimeMillis() - 7 * 86_400_000L),
    val to: Long = EntryDraft.startOfDay(System.currentTimeMillis()),
    val pay: PayFilter = PayFilter.ALL,
    val category: String? = null,
    val currency: String? = null,
    val sort: SortOrder = SortOrder.NEWEST,
) {
    /** Filters beyond the period shown in the header. */
    val activeCount get() = listOf(pay != PayFilter.ALL, category != null, currency != null, period == Period.RANGE).count { it }

    fun toJson(): String = JSONObject().put("period", period.name).put("month", month).put("from", from).put("to", to).put("pay", pay.name)
        .put("category", category ?: JSONObject.NULL).put("currency", currency ?: JSONObject.NULL).put("sort", sort.name).toString()

    companion object {
        fun monthKey(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
        fun monthName(key: Int): String = "${KisFormat.MONTHS[key % 12]} ${key / 12}"
        fun fromJson(s: String): HistoryFilter = JSONObject(s).let { o ->
            HistoryFilter(Period.valueOf(o.getString("period")), o.getInt("month"), o.getLong("from"), o.getLong("to"), PayFilter.valueOf(o.getString("pay")),
                if (o.isNull("category")) null else o.getString("category"), if (o.isNull("currency")) null else o.getString("currency"), SortOrder.valueOf(o.getString("sort")))
        }
    }
}

object KisHistoryLogic {
    fun matchesQuery(e: WorkEntry, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (e.category.contains(q, true) || e.notes.contains(q, true)) return true
        if (WorkPay.workers(e).any { it.name.contains(q, true) }) return true
        val pay = WorkPay.pay(e) ?: return false
        return String.format(Locale.US, "%.2f", pay).contains(q) || String.format(Locale.US, "%,.2f", pay).contains(q) || pay.toString().contains(q)
    }

    fun apply(entries: List<WorkEntry>, f: HistoryFilter, query: String): List<WorkEntry> {
        val toEnd = f.to + 86_400_000L
        val rows = entries.filter { e ->
            val inPeriod = when (f.period) {
                Period.ALL -> true
                Period.MONTH -> HistoryFilter.monthKey(e.date) == f.month
                Period.RANGE -> EntryDraft.startOfDay(e.date).let { it >= f.from && it < toEnd }
            }
            val payOk = when (f.pay) {
                PayFilter.ALL -> true
                PayFilter.UNPAID -> !e.isPaid
                PayFilter.PAID -> e.isPaid
                PayFilter.PENDING -> !e.payKnown
            }
            inPeriod && payOk && (f.category == null || e.category == f.category) && (f.currency == null || e.currency == f.currency) && matchesQuery(e, query)
        }
        return when (f.sort) {
            SortOrder.NEWEST -> rows.sortedWith(compareByDescending<WorkEntry> { it.date }.thenByDescending { it.createdAt })
            SortOrder.OLDEST -> rows.sortedWith(compareBy<WorkEntry> { it.date }.thenBy { it.createdAt })
            SortOrder.ADDED -> rows.sortedByDescending { it.id }
        }
    }

    /** Sunday-start week of a date (the app's existing week). */
    fun weekStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = EntryDraft.startOfDay(millis)
        add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
    }.timeInMillis

    fun weekLabel(start: Long): String =
        if (start == weekStart(System.currentTimeMillis())) "השבוע" else "שבוע של ${KisShare.shortDate(start)}"

    /** Groups consecutive rows by week, keeping the given order. */
    fun byWeek(rows: List<WorkEntry>): List<Pair<Long, List<WorkEntry>>> {
        val out = mutableListOf<Pair<Long, MutableList<WorkEntry>>>()
        rows.forEach { e ->
            val w = weekStart(e.date)
            if (out.lastOrNull()?.first == w) out.last().second.add(e) else out.add(w to mutableListOf(e))
        }
        return out
    }
}
