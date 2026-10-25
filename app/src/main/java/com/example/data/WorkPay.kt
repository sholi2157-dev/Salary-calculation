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

    /** [amount] is a fixed sum for this worker; it is used only on a job team (קבלנות), where it replaces hours × rate. */
    data class Worker(val name: String, val hours: Double, val isPaid: Boolean, val bonus: Double, val workerRate: Double?, val employerRate: Double?, val source: JSONObject,
                      val amount: Double? = null)

    fun workers(e: WorkEntry): List<Worker> {
        if (e.groupWorkersJson.isBlank()) return emptyList()
        return try {
            val array = JSONArray(e.groupWorkersJson)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Worker(o.optString("name", ""), o.optDouble("hours", 0.0).takeIf { it.isFinite() } ?: 0.0, o.optBoolean("isPaid", false),
                    o.optDouble("bonus", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0,
                    if (o.has("workerRate") && !o.isNull("workerRate")) o.getDouble("workerRate") else null,
                    if (o.has("employerRate") && !o.isNull("employerRate")) o.getDouble("employerRate") else null, o,
                    if (o.has("amount") && !o.isNull("amount")) o.optDouble("amount").takeIf { it.isFinite() } else null)
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * A job with a team. Its totalEarnings is what is left for the user after the team is paid, so every
     * total and summary counts only the user's own income; the job's full price is that plus the team's pay.
     */
    fun isTeamJob(e: WorkEntry) = isJob(e) && e.isGroupShift

    /** A worker without their own rate follows the shift rate, so it is unknown while that is. A job has no hourly rate to follow. */
    fun workerRate(w: Worker, e: WorkEntry): Double? = if (isJob(e)) w.workerRate ?: e.workerRate else w.workerRate ?: e.workerRate ?: e.hourlyRate.takeIf { e.payKnown }
    fun employerRate(w: Worker, e: WorkEntry): Double? = w.employerRate ?: e.employerRate ?: e.hourlyRate.takeIf { e.payKnown }

    /** What the worker should receive: a fixed sum on a job team, otherwise their hours at the worker rate plus their own bonus. */
    fun workerPay(w: Worker, e: WorkEntry): Double? = if (isJob(e) && w.amount != null) Math.round((w.amount + w.bonus) * 100.0) / 100.0
        else workerRate(w, e)?.let { amount(w.hours, it, w.bonus) }
    /** What the employer pays for this worker. A worker's bonus is passed through, not profit. On a job the client pays one price, so it is the worker's pay. */
    fun employerPay(w: Worker, e: WorkEntry): Double? = if (isJob(e)) workerPay(w, e) else employerRate(w, e)?.let { amount(w.hours, it, w.bonus) }

    /** The team's pay on a job, or null while any worker's pay is missing. */
    fun teamPay(e: WorkEntry): Double? {
        var sum = 0.0
        for (w in workers(e)) sum += workerPay(w, e) ?: return null
        return Math.round(sum * 100.0) / 100.0
    }

    data class GroupSummary(val employerTotal: Double, val profit: Double, val myNet: Double)

    /** Null while any part is unknown: a partial group total would be misleading. */
    fun group(e: WorkEntry): GroupSummary? {
        val own = pay(e) ?: return null
        // A job: the client pays one price; the team's part comes out of it and the rest is the user's.
        if (isJob(e)) { val team = teamPay(e) ?: return null; return GroupSummary(Math.round((own + team) * 100.0) / 100.0, 0.0, own) }
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

    /** On a [job] team each worker's pay is either a fixed amount or hours × their own rate, so exactly one of the two is written. */
    fun encodeWorkers(workers: List<Worker>, job: Boolean = false): String = JSONArray().apply {
        workers.forEach { w ->
            val o = JSONObject(w.source.toString())
            o.put("name", w.name); o.put("hours", w.hours); o.put("isPaid", w.isPaid)
            if (w.bonus > 0) o.put("bonus", w.bonus) else o.remove("bonus")
            if (job) {
                if (w.amount != null) { o.put("amount", w.amount); o.remove("workerRate") } else { o.remove("amount"); o.put("workerRate", w.workerRate ?: JSONObject.NULL) }
            }
            put(o)
        }
    }.toString()
}
