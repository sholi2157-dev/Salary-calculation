package com.example.data

import java.util.Locale

object WorkMoney {
    fun format(amount: Double, currency: String): String = String.format(Locale.US, "%s%,.2f", currency, amount)
    /** Known pay only: a shift waiting for its rate is never counted as 0. */
    fun totals(entries: List<WorkEntry>): Map<String, Double> = WorkPay.totals(entries)
    fun summary(entries: List<WorkEntry>): String = totals(entries).entries.joinToString("\n") { format(it.value, it.key) }.ifEmpty { "0.00" }
    fun workerRate(worker: org.json.JSONObject, entry: WorkEntry): Double =
        if (worker.has("workerRate") && !worker.isNull("workerRate")) worker.getDouble("workerRate")
        else entry.workerRate ?: entry.hourlyRate
    fun employerRate(worker: org.json.JSONObject, entry: WorkEntry): Double =
        if (worker.has("employerRate") && !worker.isNull("employerRate")) worker.getDouble("employerRate")
        else entry.employerRate ?: entry.hourlyRate
    private fun pendingLine(rows: List<WorkEntry>): String {
        val n = WorkPay.pending(rows).size
        return if (n == 0) "" else "\n" + if (n == 1) "משמרת אחת עוד בלי תעריף (לא נספרה)" else "$n משמרות עוד בלי תעריף (לא נספרו)"
    }
    fun report(entries: List<WorkEntry>, workerName: String? = null): String {
        if (workerName == null) return entries.groupBy { it.category }.entries.joinToString("\n\n") { (name, rows) ->
            "$name | ${rows.sumOf { it.hours }} שעות\n${summary(rows)}${pendingLine(rows)}"
        }
        val rows = entries.flatMap { entry ->
            WorkPay.workers(entry).filter { it.name == workerName }.map {
                val pay = WorkPay.workerPay(it, entry)
                entry.copy(hours = it.hours, totalEarnings = pay ?: 0.0, isPaid = it.isPaid, payKnown = pay != null)
            }
        }
        return "$workerName | ${rows.sumOf { it.hours }} שעות\n${summary(rows)}\n" +
            rows.joinToString("\n") { "${it.category}: ${if (it.payKnown) format(it.totalEarnings, it.currency) else "תעריף חסר"} — ${if (it.isPaid) "שולם" else "ממתין"}" }
    }
}
