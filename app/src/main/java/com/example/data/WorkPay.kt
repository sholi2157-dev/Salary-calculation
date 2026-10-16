package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The single place that answers "how much did this shift pay?".
 *
 * Since 2.0 a shift may have no known rate yet (or a job no known amount). Then
 * `totalEarnings` is only a 0 placeholder: it is never shown as pay and never summed.
 * Every total, summary, report and export goes through these helpers.
 */
object WorkPay {
    fun isJob(e: WorkEntry) = e.kind == WorkEntry.KIND_JOB

    /** Real pay, or null while the rate/amount is missing. */
    fun pay(e: WorkEntry): Double? = if (e.payKnown) e.totalEarnings else null

    fun known(entries: List<WorkEntry>) = entries.filter { it.payKnown }
    fun pending(entries: List<WorkEntry>) = entries.filter { !it.payKnown }

    /** Same rounding the app has always stored, so new rows match historical ones. */
    fun amount(hours: Double, rate: Double, bonus: Double = 0.0): Double = Math.round((hours * rate + bonus) * 100.0) / 100.0

    /** "תעריף חסר" for a shift, "סכום חסר" for a job. */
    fun missingLabel(e: WorkEntry) = if (isJob(e)) "סכום חסר" else "תעריף חסר"

    /** Per-currency totals of known pay only. A currency appears only if it has known pay. */
    fun totals(entries: List<WorkEntry>): Map<String, Double> = known(entries).groupBy { it.currency }
        .mapValues { (_, rows) -> rows.sumOf { it.totalEarnings } }

    // ---- Group shift workers (stored in groupWorkersJson; unknown keys are kept as they are) ----

    data class Worker(val name: String, val hours: Double, val isPaid: Boolean, val bonus: Double, val workerRate: Double?, val employerRate: Double?, val source: JSONObject)

    fun workers(e: WorkEntry): List<Worker> {
        if (e.groupWorkersJson.isBlank()) return emptyList()
        return try {
            val array = JSONArray(e.groupWorkersJson)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Worker(o.optString("name", ""), o.optDouble("hours", 0.0).takeIf { it.isFinite() } ?: 0.0, o.optBoolean("isPaid", false),
                    o.optDouble("bonus", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0,
                    if (o.has("workerRate") && !o.isNull("workerRate")) o.getDouble("workerRate") else null,
                    if (o.has("employerRate") && !o.isNull("employerRate")) o.getDouble("employerRate") else null, o)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** A worker without their own rate follows the shift rate, so it is unknown while that is. */
    fun workerRate(w: Worker, e: WorkEntry): Double? = w.workerRate ?: e.workerRate ?: e.hourlyRate.takeIf { e.payKnown }
    fun employerRate(w: Worker, e: WorkEntry): Double? = w.employerRate ?: e.employerRate ?: e.hourlyRate.takeIf { e.payKnown }

    /** What the worker should receive: their hours at the worker rate plus their own bonus. */
    fun workerPay(w: Worker, e: WorkEntry): Double? = workerRate(w, e)?.let { amount(w.hours, it, w.bonus) }
    /** What the employer pays for this worker. A worker's bonus is passed through, not profit. */
    fun employerPay(w: Worker, e: WorkEntry): Double? = employerRate(w, e)?.let { amount(w.hours, it, w.bonus) }

    data class GroupSummary(val employerTotal: Double, val profit: Double, val myNet: Double)

    /** Null while any part is unknown: a partial group total would be misleading. */
    fun group(e: WorkEntry): GroupSummary? {
        val own = pay(e) ?: return null
        var employer = 0.0
        var profit = 0.0
        for (w in workers(e)) {
            val er = employerRate(w, e) ?: return null
            val wr = workerRate(w, e) ?: return null
            employer += amount(w.hours, er, w.bonus)
            profit += w.hours * (er - wr)
        }
        fun r(v: Double) = Math.round(v * 100.0) / 100.0
        return GroupSummary(r(own + employer), r(profit), r(own + profit))
    }

    fun encodeWorkers(workers: List<Worker>): String = JSONArray().apply {
        workers.forEach { w ->
            val o = JSONObject(w.source.toString())
            o.put("name", w.name); o.put("hours", w.hours); o.put("isPaid", w.isPaid)
            if (w.bonus > 0) o.put("bonus", w.bonus) else o.remove("bonus")
            put(o)
        }
    }.toString()
}
