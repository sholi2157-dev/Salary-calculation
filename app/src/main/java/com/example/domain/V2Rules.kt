package com.example.domain

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Pure calculation rules for Salary Calculation 2.0.
 *
 * No Android, storage or UI dependencies. The same rules live in the approved prototype engine;
 * `src/test/resources/v2-rules.json` is generated from it and both platforms must match it exactly.
 * Times are "HH:MM" strings and minutes-of-day, dates are "YYYY-MM-DD" (no java.time: minSdk 24).
 */
object V2Rules {
    const val LONG_SHIFT_MINUTES = 16 * 60

    fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    fun fromMinutes(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    }

    /** Suggested end time: the current time rounded down to the previous 30-minute boundary. */
    fun roundedEnd(nowMinutes: Int): String = fromMinutes(nowMinutes / 30 * 30)

    /** Two-decimal rounding with the same epsilon guard the existing app and web use. */
    fun round2(value: Double): Double = (value + Math.ulp(1.0)).times(100).let { floor(it + 0.5) } / 100.0

    data class Span(val minutes: Int, val overnight: Boolean, val invalid: Boolean, val long: Boolean)

    /** An end earlier than the start means the shift crosses midnight; the date stays the start day. */
    fun span(start: String, end: String, breakMinutes: Int = 0): Span {
        val s = toMinutes(start)
        var e = toMinutes(end)
        if (e == s) return Span(0, overnight = false, invalid = true, long = false)
        val overnight = e < s
        if (overnight) e += 1440
        val minutes = e - s - breakMinutes
        if (minutes <= 0) return Span(0, overnight, invalid = true, long = false)
        return Span(minutes, overnight, invalid = false, long = minutes > LONG_SHIFT_MINUTES)
    }

    enum class Mode { CLOCK, HOURS, JOB, GROUP }
    enum class BonusTarget { ALL, ME, OTHERS }

    /** What the user has typed so far. Strings mirror form fields; blank means "not entered". */
    data class Draft(
        val mode: Mode,
        val start: String? = null,
        val end: String? = null,
        val breakMinutes: Int = 0,
        val hours: String = "",
        val rate: Double? = null,
        val rateUnknown: Boolean = false,
        val bonus: Double = 0.0,
        val bonusTarget: BonusTarget = BonusTarget.ME,
        val amount: String = "",
        val amountUnknown: Boolean = false,
    )

    data class Evaluation(val errors: List<String>, val minutes: Int?, val bonus: Double, val total: Double?) {
        val canSave get() = errors.isEmpty()
    }

    private fun number(text: String): Double = text.trim().ifEmpty { "0" }.toDoubleOrNull() ?: Double.NaN

    /**
     * Unknown pay is never zero: total stays null until the rate (or job amount) is known.
     * A bonus is the user's own and is added on top of hours × rate.
     */
    fun evaluate(d: Draft): Evaluation {
        val errors = mutableListOf<String>()
        if (d.mode == Mode.JOB) {
            val amount = if (d.amountUnknown) null else number(d.amount)
            val h = number(d.hours).let { if (it.isNaN()) 0.0 else it }
            if (!d.amountUnknown && !(amount != null && amount > 0)) errors += "הזן סכום, או סמן \"לא יודע עדיין\""
            if (h > 24) errors += "יותר מ-24 שעות ביום"
            val minutes = if (h > 0) (h * 60).roundToLong().toInt() else null
            val total = if (amount != null && amount > 0) round2(amount) else null
            return Evaluation(errors, minutes, 0.0, total)
        }
        var minutes: Int? = null
        var invalid = false
        if (d.mode == Mode.HOURS) {
            val h = number(d.hours)
            if (!(h > 0)) errors += "הזן מספר שעות" else if (h > 24) errors += "יותר מ-24 שעות ביום"
            if (h > 0) minutes = (h * 60).roundToLong().toInt()
        } else {
            if (d.start == null) errors += "בחר שעת התחלה"
            if (d.start != null && d.end != null) {
                val sp = span(d.start, d.end, d.breakMinutes)
                minutes = sp.minutes
                invalid = sp.invalid
                if (sp.invalid) errors += if (sp.minutes == 0 && toMinutes(d.start) == toMinutes(d.end)) "שעת ההתחלה והסיום זהות" else "ההפסקה ארוכה מהמשמרת"
            }
        }
        val rate = if (d.rateUnknown) null else d.rate
        if (!d.rateUnknown && !(rate != null && rate >= 0)) errors += "הזן תעריף, או סמן \"תעריף חסר\""
        val myBonus = if (d.mode == Mode.GROUP && d.bonusTarget == BonusTarget.OTHERS) 0.0 else d.bonus
        val base = if (minutes != null && !invalid && rate != null) minutes / 60.0 * rate else null
        return Evaluation(errors, minutes, myBonus, base?.let { round2(it + myBonus) })
    }

    /** Quiet hours: a notification due inside the window waits until it ends; it is never dropped. */
    fun quietDelay(atMinutes: Int, enabled: Boolean, from: String, to: String): String? {
        if (!enabled) return null
        val f = toMinutes(from)
        val t = toMinutes(to)
        val inside = when {
            f == t -> false
            f < t -> atMinutes in f until t
            else -> atMinutes >= f || atMinutes < t
        }
        return if (inside) to else null
    }

    // ---- Dates (proleptic Gregorian, days since 1970-01-01) ----

    fun daysFromCivil(date: String): Long {
        val (yy, mm, dd) = date.split("-").map { it.toInt() }
        val y = if (mm <= 2) yy - 1 else yy
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (mm + (if (mm > 2) -3 else 9)) + 2) / 5 + dd - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun civilFromDays(days: Long): String {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + (if (mp < 10) 3 else -9)
        val y = yoe + era * 400 + (if (m <= 2) 1 else 0)
        return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)
    }

    /** 0 = Sunday … 6 = Saturday, matching the app's existing weekday order. */
    fun weekday(date: String): Int = (((daysFromCivil(date) + 4) % 7 + 7) % 7).toInt()

    /** The dates a weekly series produces between [from] and [until], inclusive. */
    fun seriesDates(days: Set<Int>, from: String, until: String): List<String> {
        val out = mutableListOf<String>()
        var d = daysFromCivil(from)
        val last = daysFromCivil(until)
        while (d <= last) {
            val date = civilFromDays(d)
            if (weekday(date) in days) out += date
            d++
        }
        return out
    }

    // ---- Bulk rate completion ----

    data class PayRecord(
        val id: String,
        val isJob: Boolean,
        val start: String?,
        val end: String?,
        val breakMinutes: Int,
        val hours: Double?,
        val rate: Double?,
        val bonus: Double,
    )

    fun recordMinutes(r: PayRecord): Int =
        if (r.start != null && r.end != null) span(r.start, r.end, r.breakMinutes).minutes
        else ((r.hours ?: 0.0) * 60).roundToLong().toInt()

    data class BulkResult(val updated: List<Pair<String, Double>>, val skipped: Int)

    /** Writes only to shifts whose rate is missing. Known amounts and fixed-price jobs never change. */
    fun bulkRate(records: List<PayRecord>, rate: Double): BulkResult {
        val targets = records.filter { it.rate == null && !it.isJob }
        return BulkResult(targets.map { it.id to round2(recordMinutes(it) / 60.0 * rate + it.bonus) }, records.size - targets.size)
    }
}
