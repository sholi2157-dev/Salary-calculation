package com.example.domain

import com.example.data.WorkEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Summaries (2.0 stage D): "החודש שלך" and the story recap "החודש/השנה שלך בכיס". Same rules as the website's
 * web/v2/summary.js; expected results shared with it: src/test/resources/v2-summary.json.
 * Amounts stay per currency and are never added across currencies. Jobs count toward income, not the hourly average.
 */
object V2Summary {
    /** One done shift as the summary sees it. [date] is "YYYY-MM-DD"; times are "HH:mm" or null. */
    data class Row(
        val date: String, val start: String?, val end: String?, val hours: Double, val job: Boolean,
        val payKnown: Boolean, val total: Double, val currency: String, val paid: Boolean, val category: String,
    )
    data class Currency(val currency: String, val total: Double, val paid: Double, val unpaid: Double, val perHour: Double?)
    data class Place(val name: String, val hours: Double)
    data class Longest(val date: String, val start: String?, val end: String?, val hours: Double)
    data class Fun(val key: String, val count: Long)
    sealed interface Insight {
        data class Unpaid(val amounts: List<Pair<String, Double>>) : Insight
        data class Pending(val count: Int) : Insight
        data class Trend(val percent: Int, val previousMonth: String) : Insight
        data class Day(val day: Int) : Insight
    }
    data class Summary(
        val scope: String, val count: Int, val hours: Double, val currencies: List<Currency>, val pending: Int,
        val days: List<Double>, val hourMinutes: List<Int>, val places: List<Place>, val longest: Longest?, val nights: Int,
        val bestDay: Int?, val peakHour: Int?, val previousHours: Double?, val trend: Int?, val persona: String?, val comparison: Fun?,
        val insights: List<Insight>,
    )

    data class Persona(val name: String, val line: String)
    val PERSONAS = mapOf(
        "owl" to Persona("ינשוף לילה", "כשכולם הולכים לישון, אתה רק מתחמם. הקפה שלך עובד משמרת כפולה."),
        "bird" to Persona("ציפור בוקר", "השעון המעורר שלך מפחד ממך. עד שכולם קמים, אתה כבר באמצע היום."),
        "weekend" to Persona("לוחם סוף שבוע", "שישי-שבת? בשבילך זה פשוט עוד יומיים בלוח."),
        "marathon" to Persona("המרתוניסט", "משמרת ממוצעת של # שעות. מישהו פה לא מכיר את המילה \"הפסקה\"."),
        "loyal" to Persona("הנאמן", "יש לך מקום אחד בלב, ועוד יותר בלוח המשמרות."),
        "juggler" to Persona("הלהטוטן", "# מקומות עבודה, ואתה מחזיק את כולם באוויר."),
    )
    val FUN = mapOf("flights" to "טיסות לניו יורק וחזרה", "episodes" to "פרקים של \"חברים\"", "movies" to "סרטים באורך מלא",
        "coffees" to "כוסות קפה שהספיקו להתקרר")

    fun row(e: WorkEntry, zone: ZoneId = ZoneId.systemDefault()) = Row(
        Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate().toString(),
        e.startTime.takeIf { e.isTimeRange && !e.endTime.isNullOrBlank() }, e.endTime.takeIf { e.isTimeRange && !e.startTime.isNullOrBlank() },
        e.hours, e.kind == WorkEntry.KIND_JOB, e.payKnown, e.totalEarnings, e.currency, e.isPaid, e.category,
    )
    fun monthScope(monthKey: Int) = "%04d-%02d".format(monthKey / 12, monthKey % 12 + 1)
    fun today(zone: ZoneId = ZoneId.systemDefault()) = LocalDate.now(zone).toString()

    private fun r2(v: Double) = Math.round(v * 100.0) / 100.0
    private fun toMin(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
    /** 0 = Sunday … 6 = Saturday. */
    fun weekday(date: String) = LocalDate.parse(date).dayOfWeek.value % 7
    fun prevMonth(ym: String): String { var (y, m) = ym.split("-").map { it.toInt() }; m--; if (m == 0) { m = 12; y-- }; return "%04d-%02d".format(y, m) }
    private fun timed(r: Row) = r.start != null && r.end != null
    private fun overnight(r: Row) = timed(r) && toMin(r.end!!) <= toMin(r.start!!)

    /** Minutes of each timed row in each clock hour (0–23), following the shift across midnight. */
    fun hourProfile(rows: List<Row>): List<Int> {
        val out = IntArray(24)
        for (r in rows) if (timed(r)) {
            val a = toMin(r.start!!); var b = toMin(r.end!!); if (b <= a) b += 1440
            var t = a
            while (t < b) { val next = minOf(b, (t / 60 + 1) * 60); out[(t / 60) % 24] += next - t; t = next }
        }
        return out.toList()
    }

    private fun persona(list: List<Row>, hours: Double, places: List<Place>): String? {
        if (hours == 0.0) return null
        fun share(pred: (Row) -> Boolean) = list.filter(pred).fold(0.0) { a, r -> a + r.hours } / hours
        val late = share { timed(it) && (overnight(it) || toMin(it.end!!) >= 22 * 60) }
        val early = share { timed(it) && toMin(it.start!!) < 9 * 60 }
        val weekend = share { weekday(it.date) in setOf(5, 6) }
        val avg = hours / list.size
        val top = (places.firstOrNull()?.hours ?: 0.0) / hours
        return when {
            late >= 0.35 -> "owl"; early >= 0.35 -> "bird"; weekend >= 0.3 -> "weekend"
            avg >= 7 -> "marathon"; top >= 0.7 -> "loyal"; else -> "juggler"
        }
    }

    fun summary(rows: List<Row>, scope: String, today: String): Summary {
        val list = rows.filter { it.date.startsWith(scope) && it.date <= today }
        val hours = r2(list.fold(0.0) { a, r -> a + r.hours })
        class Acc { var total = 0.0; var paid = 0.0; var unpaid = 0.0; var rateTotal = 0.0; var rateHours = 0.0 }
        val byCur = LinkedHashMap<String, Acc>()
        for (r in list) if (r.payKnown) {
            val c = byCur.getOrPut(r.currency) { Acc() }
            c.total += r.total; if (r.paid) c.paid += r.total else c.unpaid += r.total
            if (!r.job) { c.rateTotal += r.total; c.rateHours += r.hours }
        }
        val currencies = byCur.entries.sortedWith(compareByDescending<Map.Entry<String, Acc>> { it.value.total }.thenBy { it.key }).map { (cur, c) ->
            Currency(cur, r2(c.total), r2(c.paid), r2(c.unpaid), if (c.rateHours > 0) r2(c.rateTotal / c.rateHours) else null)
        }
        val pending = list.count { !it.payKnown }
        val days = DoubleArray(7); for (r in list) days[weekday(r.date)] += r.hours
        val placeMap = LinkedHashMap<String, Double>(); for (r in list) placeMap[r.category] = (placeMap[r.category] ?: 0.0) + r.hours
        val places = placeMap.map { (n, h) -> Place(n, r2(h)) }.sortedWith(compareByDescending<Place> { it.hours }.thenBy { it.name })
        val longest = list.fold(null as Row?) { a, r -> if (a == null || r.hours > a.hours || (r.hours == a.hours && r.date < a.date)) r else a }
        val profile = hourProfile(list)
        val nights = list.count { timed(it) && (overnight(it) || toMin(it.end!!) >= 23 * 60) }
        val bestDay = if (hours > 0) days.indexOfFirst { it == days.max() } else null
        val peakHour = if (list.any(::timed)) profile.indexOf(profile.max()) else null
        var previous: Double? = null; var trend: Int? = null
        if (scope.length == 7) {
            val cutoff = if (scope == today.take(7)) today.substring(8).toInt() else 31
            val p = prevMonth(scope)
            previous = r2(rows.filter { it.date.startsWith(p) && it.date.substring(8).toInt() <= cutoff }.fold(0.0) { a, r -> a + r.hours })
            if (previous > 0 && hours > 0) trend = Math.round((hours - previous) / previous * 100).toInt()
        }
        val fun_ = listOf("flights" to hours / 23, "episodes" to hours * 60 / 22, "movies" to hours / 2.2, "coffees" to hours * 60 / 4).filter { it.second >= 2 }
        val pick = if (fun_.isEmpty()) null else fun_[(list.size * 7) % fun_.size]
        val insights = buildList {
            val unpaid = currencies.filter { it.unpaid > 0 }
            if (unpaid.isNotEmpty()) add(Insight.Unpaid(unpaid.map { it.currency to it.unpaid }))
            if (pending > 0) add(Insight.Pending(pending))
            if (trend != null && kotlin.math.abs(trend) >= 5) add(Insight.Trend(trend, prevMonth(scope)))
            if (bestDay != null) add(Insight.Day(bestDay))
        }
        return Summary(scope, list.size, hours, currencies, pending, days.map(::r2), profile, places.take(5),
            longest?.let { Longest(it.date, it.start, it.end, it.hours) }, nights, bestDay, peakHour, previous, trend,
            persona(list, hours, places), pick?.let { Fun(it.first, Math.round(it.second)) }, insights.take(3))
    }
}
