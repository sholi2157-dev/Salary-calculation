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
    // Addressed in the plural, as Hebrew does for everyone (the same words on the website).
    val PERSONAS = mapOf(
        "owl" to Persona("ינשוף לילה", "כשכולם הולכים לישון, אתם רק מתחממים. הקפה שלכם עובד משמרת כפולה."),
        "bird" to Persona("ציפור בוקר", "השעון המעורר מפחד מכם. עד שכולם קמים, אתם כבר באמצע היום."),
        "weekend" to Persona("האנרגיה של סופ״ש", "שישי-שבת? אצלכם זה פשוט עוד יומיים בלוח."),
        "marathon" to Persona("ריצת מרתון", "משמרת ממוצעת של # שעות. המילה \"הפסקה\" כנראה לא במילון שלכם."),
        "loyal" to Persona("נאמנות מלאה", "מקום אחד בלב, ועוד יותר בלוח המשמרות."),
        "juggler" to Persona("להטוטנות", "# מקומות עבודה, וכולם באוויר בבת אחת."),
    )
    val FUN = mapOf("flights" to "טיסות לניו יורק וחזרה", "episodes" to "פרקים של \"חברים\"", "movies" to "סרטים באורך מלא",
        "coffees" to "כוסות קפה שהספיקו להתקרר")

    /**
     * The story slides that have something real to say, in order (the same on the website, checked by the shared
     * expected results): a type needs 4 shifts, the weekday rhythm and the busiest hour 3, the longest shift 2, the
     * places at least two places. A thin month gets a short story rather than a silly one.
     */
    fun slides(s: Summary): List<String> = buildList {
        add("intro"); add("hours")
        if (s.places.size >= 2) add("places")
        if (s.persona != null && s.count >= 4) add("persona")
        if (s.longest != null && s.count >= 2) add("longest")
        if (s.bestDay != null && s.count >= 3 && s.days.count { it > 0 } >= 2) add("days")
        if (s.peakHour != null && s.count >= 3) add("hour")
        add("money"); add("card")
    }

    /*
     * When a recap exists (the website's KisSummary.recap*, checked by src/test/resources/v2-recap.json). A month's recap
     * opens on its 30th (the last day of a shorter month) and is new for that day and 4 more; after that it waits in the
     * archive. The year's recap opens with December's. Nothing before FIRST_RECAP is ever shown (no spoiler of the first).
     */
    const val FIRST_RECAP = "2026-10"
    const val RECAP_NEW_DAYS = 5
    /** "YYYY-MM-DD" of the day a month's recap opens. */
    fun recapDay(ym: String): String = java.time.YearMonth.parse(ym).let { it.atDay(minOf(30, it.lengthOfMonth())).toString() }
    private fun recapMonth(scope: String) = if (scope.length == 4) "$scope-12" else scope
    /** True once the recap of [scope] ("YYYY-MM" or "YYYY") may be seen. */
    fun recapOpen(scope: String, today: String): Boolean { val m = recapMonth(scope); return m >= FIRST_RECAP && today >= recapDay(m) }
    data class RecapNew(val month: String?, val year: String?)
    /** The recaps that are new today. */
    fun recapNew(today: String): RecapNew {
        val ym = today.take(7)
        val day = LocalDate.parse(today).toEpochDay()
        val month = listOf(ym, prevMonth(ym)).firstOrNull { recapOpen(it, today) && day - LocalDate.parse(recapDay(it)).toEpochDay() < RECAP_NEW_DAYS }
        return RecapNew(month, month?.takeIf { it.endsWith("-12") }?.take(4))
    }
    /** Every recap that has opened, newest first; a year comes right before its December. */
    fun recapArchive(today: String): List<String> = buildList {
        var ym = today.take(7)
        while (ym >= FIRST_RECAP) {
            if (recapOpen(ym, today)) { if (ym.endsWith("-12")) add(ym.take(4)); add(ym) }
            ym = prevMonth(ym)
        }
    }

    /*
     * The story's words vary between people and between recaps: each line is picked from its own list by a stable hash
     * of the person's own seed, the scope and the line's key. A year has its own lists (the same words on the website).
     */
    val VOICE: Map<String, List<String>> = linkedMapOf(
        "intro" to listOf("{whose} שלך\nבכיס.", "{whose} שלך,\nבמספרים.", "ככה נראה\n{whose} שלך."),
        "introLine" to listOf("מוכנים? יש כאן כמה מספרים שעוד לא ידעתם על עצמכם.", "חודש שלם נכנס לכמה מסכים. בואו נראה מה יצא.",
            "לפני שמתחילים חודש חדש, רגע אחד על מה שהיה.", "הכנו לכם את החודש בכמה מספרים. חלקם עשויים להפתיע."),
        "introLineYear" to listOf("שנה שלמה של משמרות, בכמה דקות. מוכנים?", "כל השעות, כל המקומות, שנה אחת. בואו נתחיל.",
            "עוד שנה נסגרת. הנה מה שהיא השאירה בכיס.", "לפני שמתחילים שנה חדשה, הצצה אחת אחורה."),
        "hoursKick" to listOf("הזמן שלכם", "כמה עבדתם", "השעון לא משקר"),
        "hoursKickYear" to listOf("השנה שלכם בשעות", "כל השעות של השנה", "הזמן שלכם"),
        "funTail" to listOf("סתם שתדעו.", "רק אומרים.", "לא שמישהו סופר."),
        "placesKick" to listOf("המקומות המובילים שלכם", "איפה הייתם", "מי קיבל את רוב הזמן"),
        "placesLine" to listOf("{p}% מהזמן הלכו ל{name}. שיהיה ברור מי האהוב.", "ל{name} הלכו {p}% מהזמן. אין ספק מי במקום הראשון.", "{p}% מהשעות היו ב{name}. אהבה גדולה."),
        "personaKick" to listOf("טיפוס העבודה שלכם", "אז מי אתם בעבודה?", "הדמות שלכם"),
        "personaKickYear" to listOf("טיפוס העבודה של השנה", "הדמות שלכם השנה", "אז מי הייתם השנה?"),
        "longestKick" to listOf("המשמרת הארוכה", "הכי ארוכה שהייתה", "שיא האורך"),
        "longestTail" to listOf("מגיע לכם קפה. אולי שניים.", "מקווים שהייתה הפסקה באמצע.", "הרגליים עוד זוכרות את זה."),
        "daysKick" to listOf("הקצב שלכם", "היום החזק", "לוח השבוע"),
        "daysLine" to listOf("יום {day} הוא היום שלכם.", "ביום {day} הכי הרבה עבודה.", "יום {day}, בלי מתחרים."),
        "hourKick" to listOf("השעה הכי פעילה שלכם", "שעת השיא", "מתי אתם בשיא"),
        "moneyKick" to listOf("השורה התחתונה", "ומה נכנס לכיס", "הרגע שחיכיתם לו"),
        "moneyKickYear" to listOf("השורה התחתונה של השנה", "כל השנה בכיס", "ומה נכנס השנה"),
    )
    /** FNV-1a over UTF-16 code units, unsigned (the same numbers as the website). */
    fun hash(text: String): Long {
        var h = 0x811c9dc5.toInt()
        for (ch in text) { h = h xor ch.code; h *= 0x01000193 }
        return h.toLong() and 0xffffffffL
    }
    /** The words of one recap: every VOICE line (a year uses the "…Year" list where there is one), templates unfilled. */
    fun voice(scope: String, seed: String): Map<String, String> {
        val year = scope.length == 4
        return VOICE.keys.filter { !it.endsWith("Year") }.associateWith { key ->
            val list = (if (year) VOICE[key + "Year"] else null) ?: VOICE.getValue(key)
            list[(hash("$seed|$scope|$key") % list.size).toInt()]
        }
    }
    fun fill(text: String, values: Map<String, String>): String = Regex("\\{(\\w+)\\}").replace(text) { values[it.groupValues[1]] ?: it.value }

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
