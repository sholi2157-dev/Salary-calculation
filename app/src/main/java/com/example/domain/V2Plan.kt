package com.example.domain

import com.example.data.SeriesException
import com.example.data.ShiftSeries
import com.example.data.WorkEntry
import com.example.data.WorkPay
import java.util.Calendar

/**
 * Weekly plans (2.0 stage C). Planned occurrences are computed from a series and its exceptions and are never
 * stored as shifts; confirming one writes a normal shift whose `seriesKey` ("seriesId@YYYY-MM-DD") is unique, so
 * the same occurrence can never be recorded twice. Ported from the approved prototype engine
 * (design/v2-prototypes/shared/core.js) with its known gaps fixed (see docs/v2-stage-c.md).
 * Expected results shared with the website: src/test/resources/v2-plan.json.
 */
object V2Plan {
    enum class Status { PLANNED, AWAITING }

    data class Occurrence(
        val key: String, val seriesId: String, val category: String, val date: String,
        val start: String, val end: String, val rate: Double?, val currency: String,
        val edited: Boolean, val hold: Boolean, val status: Status,
    )

    fun key(seriesId: String, date: String) = "$seriesId@$date"
    fun dateOfKey(key: String) = key.substringAfterLast('@')

    fun days(mask: Int): Set<Int> = (0..6).filter { mask and (1 shl it) != 0 }.toSet()
    fun mask(days: Set<Int>): Int = days.filter { it in 0..6 }.fold(0) { m, d -> m or (1 shl d) }

    /** Local calendar day of a moment, "YYYY-MM-DD". */
    fun dateOf(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Wall-clock time on a local date (Calendar handles 23/25-hour daylight-saving days). */
    fun startMillis(date: String, start: String): Long {
        val (y, m, d) = date.split("-").map { it.toInt() }
        val minutes = V2Rules.toMinutes(start)
        return Calendar.getInstance().apply { clear(); set(y, m - 1, d, minutes / 60, minutes % 60) }.timeInMillis
    }

    /** The end of an occurrence; an end at or before the start is on the next day. */
    fun endMillis(date: String, start: String, end: String): Long {
        val next = V2Rules.toMinutes(end) <= V2Rules.toMinutes(start)
        val day = if (next) V2Rules.civilFromDays(V2Rules.daysFromCivil(date) + 1) else date
        return startMillis(day, end)
    }

    /** Every occurrence between [from] and [until] (inclusive) that is neither cancelled nor already recorded. */
    fun occurrences(series: List<ShiftSeries>, exceptions: List<SeriesException>, confirmed: Set<String>, from: String, until: String, now: Long): List<Occurrence> {
        val byKey = exceptions.associateBy { key(it.seriesId, it.date) }
        return series.flatMap { s ->
            val lo = maxOf(from, s.fromDate)
            val hi = minOf(until, s.untilDate)
            if (lo > hi) emptyList() else V2Rules.seriesDates(days(s.daysMask), lo, hi).mapNotNull { date ->
                val k = key(s.id, date)
                val ex = byKey[k]
                if (k in confirmed || ex?.cancelled == true) return@mapNotNull null
                val start = ex?.startTime ?: s.startTime
                val end = ex?.endTime ?: s.endTime
                val rate = if (ex?.rateOverridden == true) ex.hourlyRate else s.hourlyRate
                Occurrence(k, s.id, s.category, date, start, end, rate, s.currency,
                    edited = ex != null && (ex.startTime != null || ex.endTime != null || ex.rateOverridden), hold = ex?.hold == true,
                    status = if (endMillis(date, start, end) <= now) Status.AWAITING else Status.PLANNED)
            }
        }.sortedWith(compareBy<Occurrence> { it.date }.thenBy { it.start })
    }

    /** Occurrences that ended and may be recorded by themselves (not held after an undo). */
    fun toConfirm(occurrences: List<Occurrence>) = occurrences.filter { it.status == Status.AWAITING && !it.hold }

    /** The shift an occurrence becomes. An unknown rate stays unknown (never ₪0). */
    fun toEntry(o: Occurrence, auto: Boolean): WorkEntry {
        val minutes = V2Rules.span(o.start, o.end).minutes
        val hours = minutes / 60.0
        return WorkEntry(category = o.category, date = startMillis(o.date, o.start), isTimeRange = true, startTime = o.start, endTime = o.end,
            hours = hours, hourlyRate = o.rate ?: 0.0, totalEarnings = o.rate?.let { WorkPay.amount(hours, it) } ?: 0.0,
            payKnown = o.rate != null, currency = o.currency, seriesKey = o.key, autoConfirmed = auto)
    }

    /** Minute ranges on the same day overlap (an overnight end runs into the next day). */
    fun overlaps(s1: String, e1: String, s2: String, e2: String): Boolean {
        fun range(s: String, e: String): Pair<Int, Int> { val a = V2Rules.toMinutes(s); var b = V2Rules.toMinutes(e); if (b <= a) b += 1440; return a to b }
        val (a1, b1) = range(s1, e1); val (a2, b2) = range(s2, e2)
        return a1 < b2 && a2 < b1
    }

    data class Preview(val dates: List<String>, val clashes: List<String>) { val created get() = dates - clashes.toSet() }

    /** The dates a new series would create; dates that overlap a saved timed shift or another plan are not created. */
    fun preview(days: Set<Int>, from: String, until: String, start: String, end: String, saved: List<WorkEntry>, planned: List<Occurrence>): Preview {
        val dates = if (from <= until) V2Rules.seriesDates(days, from, until) else emptyList()
        val timed = saved.filter { it.isTimeRange && V2Rules.isTime(it.startTime) && V2Rules.isTime(it.endTime) }.groupBy { dateOf(it.date) }
        val plans = planned.groupBy { it.date }
        val clashes = dates.filter { d ->
            timed[d].orEmpty().any { overlaps(start, end, it.startTime!!, it.endTime!!) } || plans[d].orEmpty().any { overlaps(start, end, it.start, it.end) }
        }
        return Preview(dates, clashes)
    }

    data class Split(val oldId: String, val old: ShiftSeries?, val new: ShiftSeries, val moved: List<SeriesException>, val dropped: List<SeriesException>)

    /**
     * "From now on": the old series ends the day before [date] (or disappears when [date] is its first day) and a new
     * one starts there with the changes; later exceptions move to the new series so nothing is orphaned.
     */
    fun split(s: ShiftSeries, exceptions: List<SeriesException>, date: String, newId: String, start: String = s.startTime, end: String = s.endTime,
              rate: Double? = s.hourlyRate, now: Long = System.currentTimeMillis(), recorded: Set<String> = emptySet()): Split {
        val dayBefore = V2Rules.civilFromDays(V2Rules.daysFromCivil(date) - 1)
        val old = if (date <= s.fromDate) null else s.copy(untilDate = dayBefore)
        val new = s.copy(id = newId, startTime = start, endTime = end, hourlyRate = rate, fromDate = maxOf(date, s.fromDate), createdAt = now)
        val later = exceptions.filter { it.seriesId == s.id && it.date >= date }
        // Hours changed from now on: per-day hour overrides would hide the change, so only cancellations/holds move.
        val kept = later.map { it.copy(seriesId = newId, startTime = null, endTime = null) }.filter { it.cancelled || it.hold || it.rateOverridden }.associateBy { it.date }
        // Days of the old plan that were already recorded must not come back under the new plan's keys.
        val done = recorded.filter { it.substringBeforeLast('@') == s.id }.map { dateOfKey(it) }.filter { it >= date }
        val moved = kept + done.associateWith { d -> (kept[d] ?: SeriesException(newId, d)).copy(cancelled = true) }
        return Split(s.id, old, new, moved.values.sortedBy { it.date }, later)
    }

    /**
     * A new rate for a series from [today] on. Occurrences that already ended and wait to be recorded keep the old
     * rate (pinned per day), and recorded shifts never change. Returns the updated series and exception changes.
     */
    fun applyRateFromToday(s: ShiftSeries, exceptions: List<SeriesException>, rate: Double?, today: String, waiting: List<Occurrence>): Pair<ShiftSeries, List<SeriesException>> {
        val mine = exceptions.filter { it.seriesId == s.id }.associateBy { it.date }
        val pins = waiting.filter { it.seriesId == s.id && it.date < today }.map { o ->
            (mine[o.date] ?: SeriesException(s.id, o.date)).let { if (it.rateOverridden) it else it.copy(rateOverridden = true, hourlyRate = s.hourlyRate) }
        }
        // Planned days that had their own rate now follow the new one, as the prototype does.
        val cleared = mine.values.filter { it.date >= today && it.rateOverridden }.map { it.copy(rateOverridden = false, hourlyRate = null) }
        return s.copy(hourlyRate = rate) to (pins + cleared)
    }
}
