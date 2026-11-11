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

    /**
     * 2.3.0 money contract (audit CAL-02/CAL-05, M-08, decision D-5), identical to web KisRules.money: every NEW saved amount
     * and its preview is hours × rate + bonus in exact decimal arithmetic, rounded once, half up, to whole agorot/cents.
     * Saved amounts are never recalculated with it.
     *  - Hours that are whole seconds (every clock shift is minutes / 60, every timer is seconds / 3600) are taken as that
     *    exact fraction: seconds × rate / 3600, so 50 minutes × 33.33 = 27.775 → 27.78 (M-08). The tolerance (1e-6 s) is
     *    far below anything a person types and far above the binary error of minutes / 60.
     *  - Any other hours value (typed decimal hours such as 2.675) is fixed to 10 decimal places from its exact binary value.
     *  - The rate and bonus are always fixed to 10 decimal places.
     */
    fun money(hours: Double, rate: Double, bonus: Double = 0.0): Double {
        fun d(x: Double) = java.math.BigDecimal(x).setScale(10, java.math.RoundingMode.HALF_UP)
        val seconds = hours * 3600.0
        val whole = Math.round(seconds)
        if (seconds.isFinite() && kotlin.math.abs(seconds) < 1e15 && kotlin.math.abs(seconds - whole.toDouble()) <= SECONDS_TOLERANCE) {
            val perHour = java.math.BigDecimal(3600)
            return java.math.BigDecimal.valueOf(whole).multiply(d(rate)).add(d(bonus).multiply(perHour))
                .divide(perHour, 2, java.math.RoundingMode.HALF_UP).toDouble()
        }
        return d(hours).multiply(d(rate)).add(d(bonus)).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    }

    private const val SECONDS_TOLERANCE = 1e-6

    /**
     * FB-08, identical to web KisRules.retroRate: a workplace's rate changed with "שינוי רטרואקטיבי" on, so its existing
     * shifts that are still unpaid get the new amount from [money] (the same function as every new amount). Only a plain
     * shift of [place], unpaid, with no known rate or a rate exactly equal to [oldRate], and in the place's [currency] changes.
     * Paid shifts, shifts with a different known rate, fixed-price jobs and team shifts (whose
     * workers' pay follows the shift rate) never change. Returns (id, new total) for the rows whose rate changes.
     */
    fun retroRate(entries: List<WorkEntry>, place: String, oldRate: Double, newRate: Double, currency: String): List<Pair<Int, Double>> {
        if (!newRate.isFinite() || newRate < 0 || newRate == oldRate) return emptyList()
        return entries.filter { it.category == place && !it.isPaid && it.kind != WorkEntry.KIND_JOB && !it.isGroupShift
            && (!it.payKnown || it.hourlyRate == oldRate) && it.currency == currency }
            .map { it.id to money(it.hours, newRate, it.bonus) }
    }

    /** Whole seconds a timer ran: the difference first, then truncated, the same for the screen and the saved shift (audit AND-C07). */
    fun elapsedSeconds(startMillis: Long, nowMillis: Long): Long = ((nowMillis - startMillis) / 1000L).coerceAtLeast(0L)

    /** "תעריף חסר" for a shift, "סכום חסר" for a job. */
    fun missingLabel(e: WorkEntry) = if (isJob(e)) "סכום חסר" else "תעריף חסר"

    /** Per-currency totals of known pay only. A currency appears only if it has known pay. */
    fun totals(entries: List<WorkEntry>): Map<String, Double> = known(entries).groupBy { it.currency }
        .mapValues { (_, rows) -> rows.sumOf { it.totalEarnings } }

    // ---- 2.4 idea C: money not paid for more than 30 days (display only; same as web KisPay.unpaidAging) ----

    /** Shifts still marked "לא שולם" of one workplace in one currency, older than 30 days. [amount] is null when none is known. */
    data class Aging(val category: String, val currency: String, val count: Int, val amount: Double?, val unknown: Int, val from: Long, val to: Long, val ids: List<Int>)

    const val AGING_DAYS = 30

    /**
     * Per workplace and currency, the shifts not marked paid whose day is more than 30 calendar days before today. The amount is
     * the plain sum of the saved amounts; unknown amounts are counted, never summed as 0; ₪ and $ apart. Oldest debt first.
     * Nothing is stored or recalculated.
     */
    fun unpaidAging(entries: List<WorkEntry>, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Aging> {
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        val today = day(now)
        return entries.filter { !it.isPaid && today - day(it.date) > AGING_DAYS }
            .groupBy { it.category to it.currency }
            .map { (k, rows) ->
                val known = rows.filter { it.payKnown }
                Aging(k.first, k.second, rows.size, if (known.isEmpty()) null else known.fold(0.0) { s, e -> s + e.totalEarnings },
                    rows.size - known.size, rows.minOf { it.date }, rows.maxOf { it.date }, rows.map { it.id })
            }
            .sortedWith(compareBy<Aging>({ day(it.from) }, { it.category }, { it.currency }))
    }

    /** "3 משמרות לא שולמו מעל 30 יום · ₪1,200" (+ "· 1 בלי תעריף"; all unknown: "· הסכום עוד לא ידוע"). Same as web. */
    fun agingLine(g: Aging, money: (Double, String) -> String): String {
        val head = if (g.count == 1) "משמרת אחת לא שולמה מעל 30 יום" else "${g.count} משמרות לא שולמו מעל 30 יום"
        val amount = g.amount ?: return "$head · הסכום עוד לא ידוע"
        return "$head · ${money(amount, g.currency)}" + if (g.unknown > 0) " · ${g.unknown} בלי תעריף" else ""
    }

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
     * A job with a team. Its totalEarnings is the user's own pay, so every total and summary counts only the
     * user's income; the employer owes that plus the team's pay (plus any money passed through).
     */
    fun isTeamJob(e: WorkEntry) = isJob(e) && e.isGroupShift

    /** A worker without their own rate follows the shift rate, so it is unknown while that is. A job has no hourly rate to follow. */
    fun workerRate(w: Worker, e: WorkEntry): Double? = if (isJob(e)) w.workerRate ?: e.workerRate else w.workerRate ?: e.workerRate ?: e.hourlyRate.takeIf { e.payKnown }
    fun employerRate(w: Worker, e: WorkEntry): Double? = w.employerRate ?: e.employerRate ?: e.hourlyRate.takeIf { e.payKnown }

    // Corrupt stored rates (including overflow such as 1e400) mean unknown derived pay; never change saved own pay.
    /** What the worker should receive: a fixed sum on a job team, otherwise their hours at the worker rate plus their own bonus. */
    fun workerPay(w: Worker, e: WorkEntry): Double? = if (isJob(e) && w.amount != null) money(1.0, w.amount, w.bonus)
        else workerRate(w, e)?.takeIf { it.isFinite() }?.let { money(w.hours, it, w.bonus) }
    /** What the employer pays for this worker. A worker's bonus is passed through, not profit. On a job the client pays one price, so it is the worker's pay. */
    fun employerPay(w: Worker, e: WorkEntry): Double? = if (isJob(e)) workerPay(w, e) else employerRate(w, e)?.takeIf { it.isFinite() }?.let { money(w.hours, it, w.bonus) }

    /** The team's pay on a job, or null while any worker's pay is missing. */
    fun teamPay(e: WorkEntry): Double? {
        var sum = 0L
        for (w in workers(e)) sum += cents(workerPay(w, e) ?: return null)
        return sum / 100.0
    }

    /** [employerTotal] is everything the employer owes for the shift, money passed through included. */
    data class GroupSummary(val employerTotal: Double, val profit: Double, val myNet: Double)

    /** Whole agorot/cents of an amount, so sums of rounded amounts add up exactly. */
    private fun cents(v: Double): Long = Math.round(v * 100.0)

    /**
     * Null while any part is unknown: a partial group total would be misleading. Derived when shown, never stored (only the
     * user's own totalEarnings is saved, and it is used as saved). Since 2.3.0 (CAL-03, same as web) every part reconciles
     * exactly in whole agorot: profit = Σ employer pay − Σ worker pay (each rounded once), employerTotal = own + Σ employer
     * pay + money passed through, myNet = own + profit.
     */
    fun group(e: WorkEntry): GroupSummary? {
        val own = pay(e) ?: return null
        // A job: the user's pay and each worker's pay are separate; the employer owes all of them.
        if (isJob(e)) { val team = teamPay(e) ?: return null; return GroupSummary((cents(own) + cents(team) + cents(e.passThrough)) / 100.0, 0.0, own) }
        var employer = 0L
        var team = 0L
        for (w in workers(e)) {
            employer += cents(employerPay(w, e) ?: return null)
            team += cents(workerPay(w, e) ?: return null)
        }
        return GroupSummary((cents(own) + employer + cents(e.passThrough)) / 100.0, (employer - team) / 100.0, (cents(own) + employer - team) / 100.0)
    }

    /** What the employer owes for this entry (team and money passed through included), or null while any part is unknown. */
    fun employerDue(e: WorkEntry): Double? = if (e.isGroupShift) group(e)?.employerTotal else pay(e)?.let { (cents(it) + cents(e.passThrough)) / 100.0 }

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
