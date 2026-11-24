package com.example.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Technical copy and recipient-ready sharing. Mirrored in web/v2/share.js; shared exact fixtures. */
object WorkShareText {
    /** The user's date choice for shared texts (owner feedback 2.3.1): GREG "08/11/2026", HEB "כ״ח חשוון תשפ״ז", BOTH "כ״ח חשוון (8/11)".
     *  The app sets it from Settings; the default keeps the shared fixtures civil. Spreadsheets always use [civilDate]. */
    @Volatile var dateMode: String = HebrewDate.GREG
    /** What the user receives money with (from the profile): a report to a worker asks for the same kinds. Empty: all three. */
    @Volatile var askKinds: List<String> = emptyList()
    fun civilDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
    fun date(ms: Long): String = when (dateMode) {
        HebrewDate.HEB -> HebrewDate.day(ms) + " " + HebrewDate.gematria(HebrewDate.fromMillis(ms).first)
        HebrewDate.BOTH -> HebrewDate.day(ms) + " (" + HebrewDate.civilShort(ms, false) + ")"
        else -> civilDate(ms)
    }
    fun money(v: Double, c: String): String = WorkMoney.format(v, c)
    fun hours(h: Double): String = BigDecimal(h).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    // Owner request 2026-11-24: every shared text says what all shifts have in common once (place, rate, year, paid or not),
    // and each shift line carries only what differs. Copy is the same text without the greeting and the thanks.
    private fun note(e: WorkEntry) = e.notes.trim()
    private fun time(e: WorkEntry): String = if (!e.isTimeRange || e.startTime == null || e.endTime == null) "" else
        "${e.startTime}–${e.endTime}" + if (e.endTime <= e.startTime) " (למחרת)" else ""
    private fun pause(e: WorkEntry): Long? = if (!WorkPay.isJob(e) && time(e).isNotEmpty())
        maxOf(0L, Math.round(com.example.domain.V2Rules.span(e.startTime!!, e.endTime!!).minutes - e.hours * 60)) else null
    private fun yearOf(ms: Long): Int = if (dateMode == HebrewDate.HEB) HebrewDate.fromMillis(ms).first
        else java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.YEAR)
    /** A date without its year, for lines under a heading that already names the year. */
    fun shortDate(ms: Long): String = when (dateMode) {
        HebrewDate.HEB -> HebrewDate.day(ms)
        HebrewDate.BOTH -> date(ms)
        else -> SimpleDateFormat("dd/MM", Locale.US).format(Date(ms))
    }
    private fun sameYear(rows: List<WorkEntry>) = rows.map { yearOf(it.date) }.distinct().size == 1
    private fun range(rows: List<WorkEntry>): String {
        val a = rows.first().date; val b = rows.last().date
        if (date(a) == date(b)) return date(a)
        return (if (sameYear(rows)) shortDate(a) else date(a)) + "–" + date(b)
    }
    /** " (01/10–30/10/2026)" after a heading; a range that has its own brackets (both calendars) follows a comma instead. */
    private fun rangeNote(rows: List<WorkEntry>): String = range(rows).let { if (it.contains('(')) ", $it" else " ($it)" }
    private fun rateText(e: WorkEntry) = if (WorkPay.pay(e) == null) "תעריף לא ידוע" else money(e.hourlyRate, e.currency) + " לשעה"
    /** The hourly rate when every shift with a known hourly rate has the same one (and currency); otherwise null. */
    private fun commonRate(rows: List<WorkEntry>): String? {
        val r = rows.filter { !WorkPay.isJob(it) && WorkPay.pay(it) != null }; if (r.isEmpty()) return null
        return if (r.all { it.hourlyRate == r[0].hourlyRate && it.currency == r[0].currency }) money(r[0].hourlyRate, r[0].currency) else null
    }
    private fun hasHours(e: WorkEntry) = !WorkPay.isJob(e) || e.hours > 0
    /** One line per currency: the total and whether it was paid; "שולם"/"לא שולם" once when it is true of all of it. */
    private fun moneyLines(rows: List<WorkEntry>, future: Boolean): List<String> = buildList {
        val tot = WorkPay.totals(rows).entries.toList(); val known = rows.filter { WorkPay.pay(it) != null }
        // Several currencies with one state: one line ("₪100.00 ו-$10.00 · לא שולם"); the amounts are never added together.
        if (tot.size > 1 && (future || known.all { it.isPaid } || known.all { !it.isPaid }))
            add((if (future) "צפי: " else "סכום: ") + tot.joinToString(" ו-") { money(it.value, it.key) } + if (future) "" else if (known[0].isPaid) " · שולם" else " · לא שולם")
        else tot.forEach { (c, total) ->
            if (future) { add("צפי: " + money(total, c)); return@forEach }
            val k = known.filter { it.currency == c }
            val paid = WorkPay.totals(k.filter { it.isPaid })[c] ?: 0.0; val unpaid = WorkPay.totals(k.filter { !it.isPaid })[c] ?: 0.0
            add("סכום: " + money(total, c) + when { k.all { it.isPaid } -> " · שולם"; k.all { !it.isPaid } -> " · לא שולם"
                else -> " · שולם ${money(paid, c)} · לא שולם ${money(unpaid, c)}" })
        }
        val missing = WorkPay.pending(rows)
        if (missing.isNotEmpty()) add(when {
            rows.size == 1 -> "הסכום עוד לא ידוע."
            missing.size == 1 -> "חסר סכום ל" + (if (WorkPay.isJob(missing[0])) "עבודה אחת" else "משמרת אחת") + "; היא לא נכללה בסכום."
            else -> "חסר סכום ל-${missing.size} משמרות; הן לא נכללו בסכום."
        })
    }
    /** Bonus, money passed through and the note of one shift on lines of their own. */
    private fun extras(e: WorkEntry): List<String> = buildList {
        if (e.bonus > 0) add("בונוס: ${money(e.bonus, e.currency)} (כלול בסכום)")
        if (e.passThrough > 0) add("החזר הוצאות: ${money(e.passThrough, e.currency)} (לא כלול בסכום)")
        if (note(e).isNotEmpty()) add("הערה: ${note(e)}")
    }
    private class LineStyle(val single: Boolean = false, val rate: String? = null, val short: Boolean = false, val mixed: Boolean = false)
    /** The facts of one shift that are not shared with the others: date, time, break, hours (× rate), amount, paid, extras. */
    private fun shiftLine(e: WorkEntry, o: LineStyle): String {
        val p = mutableListOf(if (o.short) shortDate(e.date) else date(e.date))
        if (time(e).isNotEmpty()) p += time(e)
        pause(e)?.takeIf { it > 0 }?.let { p += "הפסקה $it דקות" }
        if (o.single) { if (hasHours(e)) p += "${hours(e.hours)} שעות"; if (!WorkPay.isJob(e)) p += rateText(e); return p.joinToString(" · ") }
        val showRate = o.rate == null && !WorkPay.isJob(e) && WorkPay.pay(e) != null
        if (hasHours(e)) p += "${hours(e.hours)} שעות" + if (showRate) " × " + money(e.hourlyRate, e.currency) else ""
        p += WorkPay.pay(e)?.let { money(it, e.currency) } ?: if (WorkPay.isJob(e)) "סכום לא ידוע" else "תעריף לא ידוע"
        if (o.mixed) p += if (e.isPaid) "שולם" else "לא שולם"
        if (e.bonus > 0) p += "כולל בונוס " + money(e.bonus, e.currency)
        if (e.passThrough > 0) p += "החזר הוצאות " + money(e.passThrough, e.currency) + " (לא כלול)"
        if (note(e).isNotEmpty()) p += "הערה: " + note(e)
        return p.joinToString(" · ")
    }
    /** The lines of one workplace (rows sorted by date), after its heading. */
    private fun body(rows: List<WorkEntry>, future: Boolean): List<String> {
        if (rows.size == 1) return listOf(shiftLine(rows[0], LineStyle(single = true))) + moneyLines(rows, future) + extras(rows[0])
        val rate = commonRate(rows)
        return buildList {
            if (rows.any { hasHours(it) }) add("סה״כ ${hours(rows.sumOf { it.hours })} שעות" + (if (rate != null) ", $rate לשעה" else "") + ".")
            addAll(moneyLines(rows, future)); add("פירוט:")
            val o = LineStyle(rate = rate, short = sameYear(rows), mixed = !future && rows.any { it.isPaid } && rows.any { !it.isPaid })
            rows.forEach { add(shiftLine(it, o)) }
        }
    }
    /** "מסעדה · 21 משמרות (01/10–30/10/2026)": the heading of a workplace in a copy or in a report of several workplaces. */
    private fun placeTitle(place: String, es: List<WorkEntry>, future: Boolean) =
        place + (if (future) " · מתוכנן" else "") + if (es.size > 1) " · ${es.size} משמרות" + rangeNote(es) else ""
    fun workerName(entries: List<WorkEntry>, query: String): String? = query.trim().takeIf { it.isNotEmpty() }?.let { q ->
        entries.flatMap { WorkPay.workers(it).map { w -> w.name } }.distinct().firstOrNull { it.equals(q, ignoreCase = true) }
    }
    private fun workerRows(entries: List<WorkEntry>, name: String): List<WorkEntry> = entries.flatMap { e ->
        WorkPay.workers(e).filter { it.name == name }.map { w ->
            val p = WorkPay.workerPay(w, e)
            // Owner time range and break are not the worker's facts; the shift note is attached.
            e.copy(hours = w.hours, hourlyRate = WorkPay.workerRate(w, e) ?: 0.0, totalEarnings = p ?: 0.0,
                payKnown = p != null, isPaid = w.isPaid, bonus = w.bonus, passThrough = 0.0, isTimeRange = false,
                startTime = null, endTime = null, isGroupShift = false)
        }
    }
    /** The report text: [share] adds the greeting, the request for payment details (to a worker) and the thanks. */
    private fun report(entries: List<WorkEntry>, name: String?, share: Boolean, future: Boolean): String {
        val rows = if (name != null) workerRows(entries, name) else entries
        if (rows.isEmpty()) return "אין משמרות להצגה"
        val places = rows.sortedBy { it.date }.groupBy { it.category }.toList()
        val out = mutableListOf<String>()
        val hi = if (name != null) "היי $name" else "היי"
        if (places.size == 1 && share) {
            val (place, es) = places[0]; val n = es.size; val mine = if (name != null) "שלך" else "שלי"
            val what = if (n == 1) (if (WorkPay.isJob(es[0])) "העבודה" else "המשמרת") + (if (future) " המתוכננת " else " ") + mine else "$n המשמרות $mine"
            out += "$hi, סיכמתי פה את $what ב$place" + (if (n > 1) rangeNote(es) else "") + ":"
            out += body(es, future)
        } else {
            if (share) out += "$hi, סיכמתי פה את המשמרות ${if (name != null) "שלך" else "שלי"}:"
            else if (name != null) out += "עובד: $name"
            places.forEachIndexed { i, (place, es) -> if (i > 0) out += ""; out += placeTitle(place, es, future); out += body(es, future) }
        }
        if (!share) return out.joinToString("\n")
        // A report to someone the user pays asks for where to send the money.
        if (name != null && !future && rows.any { !it.isPaid && WorkPay.pay(it) != null }) out += askPayment(askKinds)
        out += "תודה!"
        return out.joinToString("\n")
    }
    fun summary(entries: List<WorkEntry>, name: String? = null, future: Boolean = false) = report(entries, name, true, future)
    fun copy(entries: List<WorkEntry>, name: String? = null, future: Boolean = false) = report(entries, name, false, future)
    const val ASK_PAYMENT = "אפשר לשלוח לי את ה-Zelle, הביט או פרטי החשבון שלך, ואעביר את התשלום."
    /** "אפשר לשלוח לי את ה-Zelle שלך…" for the kinds the user filled in ("zelle", "bit", "bank"); none or all three: [ASK_PAYMENT]. */
    fun askPayment(kinds: List<String>): String {
        val names = listOf("zelle" to "ה-Zelle", "bit" to "הביט", "bank" to "פרטי החשבון").filter { it.first in kinds }.map { it.second }
        if (names.isEmpty() || names.size == 3) return ASK_PAYMENT
        val what = if (names.size == 1) names[0] else names[0] + " או " + names[1]
        return "אפשר לשלוח לי את $what שלך, ואעביר את התשלום."
    }
    /** The user's own payment details (Zelle, Bit, bank), when they choose to add them to what they share. */
    fun paymentLines(zelle: String, bit: String, bank: String): List<String> = listOfNotNull(
        zelle.trim().takeIf { it.isNotEmpty() }?.let { "Zelle: $it" },
        bit.trim().takeIf { it.isNotEmpty() }?.let { "ביט: $it" },
        bank.trim().takeIf { it.isNotEmpty() }?.let { "חשבון בנק: $it" })
    /** [text] with the payment details before its closing "תודה!" (or at its end). */
    fun withPayment(text: String, lines: List<String>): String {
        if (lines.isEmpty()) return text
        val block = (listOf("לתשלום:") + lines).joinToString("\n")
        return if (text.endsWith("\nתודה!")) text.removeSuffix("תודה!") + block + "\nתודה!" else "$text\n$block"
    }
    fun personal(e: WorkEntry) = summary(listOf(e))
    fun job(e: WorkEntry) = personal(e)
    /** The team report of a group shift: what the employer pays in all, once, then each person's hours and amount. */
    fun employer(e: WorkEntry, ownName: String? = null, technical: Boolean = false, future: Boolean = false): String {
        val own = WorkPay.pay(e); val ws = WorkPay.workers(e); val pays = ws.map { WorkPay.employerPay(it, e) }; val isJob = WorkPay.isJob(e)
        // Everything the employer pays: the user, each worker and any money passed through.
        val total = if (own == null || pays.any { it == null }) null else Math.round((own + pays.sumOf { it!! } + e.passThrough) * 100) / 100.0
        return buildList {
            add(if (technical) "${e.category} · ${date(e.date)}" + (if (future) " · מתוכנן" else "")
                else "היי, סיכמתי פה את העבודה" + (if (future) " המתוכננת" else "") + " ב${e.category} ב-${date(e.date)}:")
            add((if (future) "צפי לצוות: " else "סכום הצוות: ") + (total?.let { money(it, e.currency) + if (future) "" else if (e.isPaid) " · שולם" else " · לא שולם" } ?: "עוד לא ידוע (חסר תעריף)"))
            add("פירוט:")
            add("${ownName?.trim()?.takeIf { it.isNotEmpty() } ?: "החלק שלי"}: ${if (isJob && e.hours == 0.0) "" else "${hours(e.hours)} שעות · "}${own?.let { money(it, e.currency) } ?: if (isJob) "סכום לא ידוע" else "תעריף לא ידוע"}")
            // On a job team a fixed sum has no hours to show.
            ws.forEachIndexed { i, w -> add("${w.name}: ${if (isJob && w.amount != null && w.hours == 0.0) "" else "${hours(w.hours)} שעות · "}${pays[i]?.let { money(it, e.currency) } ?: "תעריף לא ידוע"}") }
            if (e.passThrough > 0) add("החזר הוצאות: ${money(e.passThrough, e.currency)}")
            if (note(e).isNotEmpty()) add("הערה: ${note(e)}")
            if (!technical) add("תודה!")
        }.joinToString("\n")
    }
    fun worker(e: WorkEntry, w: WorkPay.Worker, technical: Boolean = false, future: Boolean = false): String {
        val single = e.copy(groupWorkersJson = org.json.JSONArray().put(w.source).toString())
        return report(listOf(single), w.name, !technical, future)
    }
    /** A weekly-plan day: the same shape as a saved planned shift. */
    fun planned(o: com.example.domain.V2Plan.Occurrence, technical: Boolean = false): String {
        val h = com.example.domain.V2Rules.span(o.start, o.end).minutes / 60.0
        return buildList {
            add(if (technical) "${o.category} · מתוכנן" else "היי, סיכמתי פה את המשמרת המתוכננת שלי ב${o.category}:")
            add(listOf(date(com.example.domain.V2Plan.startMillis(o.date, "12:00")), "${o.start}–${o.end}" + (if (o.end <= o.start) " (למחרת)" else ""),
                "${hours(h)} שעות", o.rate?.let { money(it, o.currency) + " לשעה" } ?: "תעריף לא ידוע").joinToString(" · "))
            add(o.rate?.let { "צפי: " + money(WorkPay.money(h, it), o.currency) } ?: "הסכום עוד לא ידוע.")
            if (!technical) add("תודה!")
        }.joinToString("\n")
    }
    /** Saved future entries are also estimates, even though they are not weekly-plan occurrences. */
    fun entry(e: WorkEntry, technical: Boolean = false, future: Boolean = false): String = report(listOf(e), null, !technical, future)
    private val excelHeaders = listOf("עבודה", "תאריך", "שעת התחלה", "שעת סיום", "שעות עבודה", "הפסקה בדקות", "תעריף שעתי", "סכום", "מטבע", "סטטוס תשלום", "סוג", "בונוס", "הערה", "שכר ידוע")
    private fun textCell(v: String) = if (Regex("^[\\s]*[=+\\-@]").containsMatchIn(v)) "'$v" else v
    /** The exact duration, not hours rounded to 2 places (audit M-43, same as web KisShare.exactDuration): 1:40 stays 1:40, a timer's seconds stay (8:00:12). */
    fun exactDuration(hours: Double): String {
        val t = Math.round(hours * 3600); val s = t % 60; val m = (t / 60) % 60
        return "${t / 3600}:" + String.format(Locale.US, "%02d", m) + if (s != 0L) ":" + String.format(Locale.US, "%02d", s) else ""
    }
    private fun table(entries: List<WorkEntry>): List<List<String>> {
        // Format the stored value with the existing money contract; do not recompute hours × rate.
        fun n(v: Double) = String.format(Locale.US, "%.2f", WorkPay.money(1.0, v))
        return listOf(excelHeaders) + entries.sortedBy { it.date }.map { e -> listOf(
            textCell(e.category), civilDate(e.date), e.startTime ?: "", e.endTime ?: "", if (e.hours.isFinite() && e.hours >= 0) exactDuration(e.hours) else n(e.hours), pause(e)?.toString() ?: "",
            if (!e.payKnown || WorkPay.isJob(e)) "" else n(e.hourlyRate), if (e.payKnown) n(e.totalEarnings) else "", textCell(e.currency),
            if (e.isPaid) "שולם" else "ממתין", if (WorkPay.isJob(e)) "קבלנות" else if (e.isGroupShift) "משמרת קבוצתית" else "משמרת",
            if (e.bonus > 0) n(e.bonus) else "", textCell(e.notes), if (e.payKnown) "כן" else "חסר"
        ) }
    }
    private fun quote(s: String, d: Char) = if (s.contains(d) || s.any { it == '\n' || it == '\r' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    fun excel(entries: List<WorkEntry>) = table(entries).joinToString("\n") { row -> row.joinToString("\t") { quote(it, '\t') } }
    fun csv(entries: List<WorkEntry>) = "\ufeff" + table(entries).joinToString("\r\n") { row -> row.joinToString(",") { quote(it, ',') } }
}
