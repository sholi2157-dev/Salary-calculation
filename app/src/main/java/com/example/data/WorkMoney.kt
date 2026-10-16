package com.example.data

import java.util.Locale
import org.json.JSONArray

object WorkMoney {
    fun format(amount: Double, currency: String): String = String.format(Locale.US, "%s%,.2f", currency, amount)
    fun totals(entries: List<WorkEntry>): Map<String, Double> = entries.groupBy { it.currency }
        .mapValues { (_, rows) -> rows.sumOf { it.totalEarnings } }
    fun summary(entries: List<WorkEntry>): String = totals(entries).entries.joinToString("\n") { format(it.value, it.key) }.ifEmpty { "0.00" }
    fun workerRate(worker: org.json.JSONObject, entry: WorkEntry): Double =
        if (worker.has("workerRate") && !worker.isNull("workerRate")) worker.getDouble("workerRate")
        else entry.workerRate ?: entry.hourlyRate
    fun employerRate(worker: org.json.JSONObject, entry: WorkEntry): Double =
        if (worker.has("employerRate") && !worker.isNull("employerRate")) worker.getDouble("employerRate")
        else entry.employerRate ?: entry.hourlyRate
    fun report(entries: List<WorkEntry>, workerName: String? = null): String {
        if (workerName == null) return entries.groupBy { it.category }.entries.joinToString("\n\n") { (name, rows) ->
            "$name | ${rows.sumOf { it.hours }} שעות\n${summary(rows)}"
        }
        val rows = entries.flatMap { entry ->
            val workers = if (entry.groupWorkersJson.isBlank()) JSONArray() else JSONArray(entry.groupWorkersJson)
            (0 until workers.length()).map { workers.getJSONObject(it) }.filter { it.optString("name") == workerName }.map {
                entry.copy(hours = it.getDouble("hours"), totalEarnings = it.getDouble("hours") * workerRate(it, entry), isPaid = it.optBoolean("isPaid", false))
            }
        }
        return "$workerName | ${rows.sumOf { it.hours }} שעות\n${summary(rows)}\n" +
            rows.joinToString("\n") { "${it.category}: ${format(it.totalEarnings, it.currency)} — ${if (it.isPaid) "שולם" else "ממתין"}" }
    }
}
