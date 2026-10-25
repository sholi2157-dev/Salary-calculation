package com.example.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Technical copy and recipient-ready sharing. Mirrored in web/v2/share.js; shared exact fixtures. */
object WorkShareText {
    fun date(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
    fun money(v: Double, c: String): String = WorkMoney.format(v, c)
    fun hours(h: Double): String = BigDecimal(h).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    private fun note(e: WorkEntry) = e.notes.trim()
    private fun amount(e: WorkEntry) = WorkPay.pay(e)?.let { money(it, e.currency) } ?: "ממתין להשלמה"
    private fun time(e: WorkEntry): String = if (!e.isTimeRange || e.startTime == null || e.endTime == null) "" else
        "${e.startTime}–${e.endTime}" + if (e.endTime <= e.startTime) " (למחרת)" else ""
    private fun pause(e: WorkEntry): Long? = if (!WorkPay.isJob(e) && time(e).isNotEmpty())
        maxOf(0L, Math.round(com.example.domain.V2Rules.span(e.startTime!!, e.endTime!!).minutes - e.hours * 60)) else null
    private fun details(e: WorkEntry): List<String> = buildList {
        if (time(e).isNotEmpty()) add("שעות: ${time(e)}")
        pause(e)?.takeIf { it > 0 }?.let { add("הפסקה: $it דקות") }
        if (!WorkPay.isJob(e)) add("תעריף לשעה: " + (if (WorkPay.pay(e) == null) "עדיין לא ידוע" else money(e.hourlyRate, e.currency)))
        if (e.bonus > 0) add("בונוס: ${money(e.bonus, e.currency)} (כלול בסכום)")
        if (note(e).isNotEmpty()) add("הערה: ${note(e)}")
    }
    private fun amounts(rows: List<WorkEntry>): List<String> = buildList {
        WorkPay.totals(rows).forEach { (c, total) ->
            add("סכום: ${money(total, c)}")
            add("שולם: ${money(WorkPay.totals(rows.filter { it.isPaid })[c] ?: 0.0, c)}")
            add("יתרה לתשלום: ${money(WorkPay.totals(rows.filter { !it.isPaid })[c] ?: 0.0, c)}")
        }
        val missing = WorkPay.pending(rows).size
        if (missing > 0) add("סכום חסר: $missing (לא נכלל בסכומים)")
    }
    private fun humanAmounts(rows: List<WorkEntry>, worker: Boolean): List<String> = buildList {
        WorkPay.totals(rows).forEach { (c, total) ->
            val paid = WorkPay.totals(rows.filter { it.isPaid })[c] ?: 0.0
            val unpaid = WorkPay.totals(rows.filter { !it.isPaid })[c] ?: 0.0
            if (rows.none { it.currency == c && !it.isPaid && WorkPay.pay(it) != null }) add("השכר בסך ${money(total,c)} שולם במלואו.")
            else if (paid == 0.0) add((if (worker) "מגיע לך: " else "השכר לתשלום: ") + money(total,c) + ".")
            else add("השכר הכולל: ${money(total,c)}. מתוכו שולם ${money(paid,c)}, ונותר לתשלום ${money(unpaid,c)}.")
        }
        val missing = WorkPay.pending(rows).size
        if (missing > 0) add(if (missing == 1) "סכום חסר עבור ${if (rows.all { WorkPay.isJob(it) }) "עבודה אחת" else "משמרת אחת"}; היא לא נכללה בסיכום הכספי." else "סכום חסר עבור $missing משמרות; הן לא נכללו בסיכום הכספי.")
    }
    private fun dates(rows: List<WorkEntry>) = rows.sortedBy { it.date }.map { date(it.date) }.distinct().joinToString(", ")
    fun workerName(entries: List<WorkEntry>, query: String): String? = query.trim().takeIf { it.isNotEmpty() }?.let { q ->
        entries.flatMap { WorkPay.workers(it).map { w -> w.name } }.distinct().firstOrNull { it.equals(q, ignoreCase = true) }
    }
    private fun workerRows(entries: List<WorkEntry>, name: String): List<WorkEntry> = entries.flatMap { e ->
        WorkPay.workers(e).filter { it.name == name }.map { w ->
            val p = WorkPay.workerPay(w, e)
            // Owner time range and break are not the worker's facts; the shift note is attached.
            e.copy(hours = w.hours, hourlyRate = WorkPay.workerRate(w, e) ?: 0.0, totalEarnings = p ?: 0.0,
                payKnown = p != null, isPaid = w.isPaid, bonus = w.bonus, isTimeRange = false,
                startTime = null, endTime = null, isGroupShift = false)
        }
    }
    fun copy(entries: List<WorkEntry>, name: String? = null): String {
        val rows = if (name != null) workerRows(entries, name) else entries
        if (rows.isEmpty()) return "אין משמרות להצגה"
        val text = rows.sortedBy { it.date }.groupBy { it.category }.map { (place, es) ->
            buildList {
                add("עבודה: $place"); add("כמות משמרות: ${es.size}"); add("כמות שעות: ${hours(es.sumOf { it.hours })}")
                addAll(amounts(es)); add("תאריכים: ${dates(es)}")
                if (es.size == 1) addAll(details(es.single()))
                else { add("פירוט:"); es.forEach { e ->
                    add("${date(e.date)} | ${hours(e.hours)} שעות | ${amount(e)} | ${if (e.isPaid) "שולם" else "ממתין"}")
                    addAll(details(e))
                } }
            }.joinToString("\n")
        }.joinToString("\n\n")
        return (if (name != null) "עובד: $name\n" else "") + text
    }
    fun summary(entries: List<WorkEntry>, name: String? = null): String {
        val rows = if (name != null) workerRows(entries, name) else entries
        if (rows.isEmpty()) return "אין משמרות להצגה"
        if (rows.map { it.category }.distinct().size > 1) return copy(entries, name)
        val place = rows.first().category; val n = rows.size
        val intro = if (name != null) "היי $name, סיכמתי פה את ${if (n == 1) "המשמרת שלך" else "המשמרות שלך"} ב$place:"
            else "היי, סיכמתי פה את ${if (n == 1) (if (WorkPay.isJob(rows.first())) "העבודה שלי" else "המשמרת שלי") else "$n המשמרות שלי"} ב$place:"
        return buildList {
            add(intro)
            val who = if (name != null) "עבדת" else "עבדתי"
            if (n == 1) add("בתאריך ${dates(rows)}" + if (rows.any { !WorkPay.isJob(it) || it.hours > 0 }) " $who ${hours(rows.sumOf { it.hours })} שעות." else ".")
            else { add("בתאריכים ${dates(rows)}."); add("סה״כ $who ${hours(rows.sumOf { it.hours })} שעות.") }
            addAll(humanAmounts(rows, name != null))
            if (n == 1) addAll(details(rows.single()))
            else { add("פירוט המשמרות:"); rows.sortedBy { it.date }.forEach { e ->
                add("${date(e.date)} | ${hours(e.hours)} שעות | ${amount(e)} | ${if (e.isPaid) "שולם" else "ממתין"}")
                addAll(details(e))
            } }
            add("תודה!")
        }.joinToString("\n")
    }
    fun personal(e: WorkEntry) = summary(listOf(e))
    fun job(e: WorkEntry) = personal(e)
    fun employer(e: WorkEntry, ownName: String? = null, technical: Boolean = false, future: Boolean = false): String {
        val own = WorkPay.pay(e); val ws = WorkPay.workers(e); val pays = ws.map { WorkPay.employerPay(it, e) }
        val total = if (own == null || pays.any { it == null }) null else Math.round((own + pays.sumOf { it!! }) * 100) / 100.0
        val text = buildList {
            if (technical) { add("עבודה: ${e.category}"); add("כמות משמרות: 1"); add("כמות שעות: ${hours(e.hours + ws.sumOf { it.hours })}") }
            else add("היי, סיכמתי פה את עבודת הצוות ב${e.category}:")
            add("תאריכים: ${date(e.date)}")
            add("סכום הצוות: " + (total?.let { money(it, e.currency) } ?: "ממתין להשלמת תעריף"))
            add("פירוט:")
            add("${ownName?.trim()?.takeIf { it.isNotEmpty() } ?: "החלק שלי"}: ${if (WorkPay.isJob(e) && e.hours == 0.0) "" else "${hours(e.hours)} שעות | "}${own?.let { money(it, e.currency) } ?: if (WorkPay.isJob(e)) "סכום חסר" else "תעריף חסר"}")
            // On a job team a fixed sum has no hours to show.
            ws.forEachIndexed { i, w -> add("${w.name}: ${if (WorkPay.isJob(e) && w.amount != null && w.hours == 0.0) "סכום קבוע" else "${hours(w.hours)} שעות"} | ${pays[i]?.let { money(it, e.currency) } ?: "תעריף חסר"}") }
            if (note(e).isNotEmpty()) add("הערה: ${note(e)}")
            if (!technical) add("תודה!")
        }.joinToString("\n")
        return if (future) plannedText(text) else text
    }
    fun worker(e: WorkEntry, w: WorkPay.Worker, technical: Boolean = false, future: Boolean = false): String {
        val single = e.copy(groupWorkersJson = org.json.JSONArray().put(w.source).toString())
        val text = if (technical) copy(listOf(single), w.name) else summary(listOf(single), w.name)
        return if (future) plannedText(text) else text
    }
    fun planned(o: com.example.domain.V2Plan.Occurrence, technical: Boolean = false): String {
        val h = com.example.domain.V2Rules.span(o.start, o.end).minutes / 60.0
        return buildList {
            if (technical) { add("עבודה: ${o.category}"); add("כמות משמרות: 1") }
            else add("היי, סיכמתי פה את המשמרת המתוכננת שלי ב${o.category}:")
            add("מצב: מתוכנן — עוד לא בוצע")
            add("תאריכים: ${date(com.example.domain.V2Plan.startMillis(o.date, "12:00"))}")
            add("שעות: ${o.start}–${o.end}" + if (o.end <= o.start) " (למחרת)" else "")
            add("כמות שעות: ${hours(h)}")
            add("תעריף לשעה: " + (o.rate?.let { money(it, o.currency) } ?: "עדיין לא ידוע"))
            add("צפי בלבד: " + (o.rate?.let { money(WorkPay.amount(h, it), o.currency) } ?: "ממתין להשלמת תעריף"))
            if (!technical) add("תודה!")
        }.joinToString("\n")
    }
    fun entry(e: WorkEntry, technical: Boolean = false, future: Boolean = false): String {
        val text = if (technical) copy(listOf(e)) else personal(e)
        if (!future) return text
        return plannedText(text)
    }
    private fun plannedText(text: String) = "מתוכנן — עוד לא בוצע\n" + text.replace("יתרה לתשלום:", "צפי שטרם בוצע:").replace("שולם:", "סומן בתכנון כשולם:").replace("סכום:", "צפי:").replace("סכום הצוות:", "צפי לצוות:").replace("השכר לתשלום:", "צפי השכר:").replace("מגיע לך:", "צפי השכר:").replace("נותר לתשלום", "צפי שטרם בוצע").replace("השכר הכולל:", "צפי השכר הכולל:").replace("השכר בסך", "צפי השכר בסך").replace(" שולם במלואו.", " סומן בתכנון כשולם.").replace("מתוכו שולם", "סומן בתכנון כשולם")
    private val excelHeaders = listOf("עבודה", "תאריך", "שעת התחלה", "שעת סיום", "שעות עבודה", "הפסקה בדקות", "תעריף שעתי", "סכום", "מטבע", "סטטוס תשלום", "סוג", "בונוס", "הערה", "שכר ידוע")
    private fun textCell(v: String) = if (Regex("^[\\s]*[=+\\-@]").containsMatchIn(v)) "'$v" else v
    private fun table(entries: List<WorkEntry>): List<List<String>> {
        fun n(v: Double) = String.format(Locale.US, "%.2f", v)
        return listOf(excelHeaders) + entries.sortedBy { it.date }.map { e -> listOf(
            textCell(e.category), date(e.date), e.startTime ?: "", e.endTime ?: "", n(e.hours), pause(e)?.toString() ?: "",
            if (!e.payKnown || WorkPay.isJob(e)) "" else n(e.hourlyRate), if (e.payKnown) n(e.totalEarnings) else "", textCell(e.currency),
            if (e.isPaid) "שולם" else "ממתין", if (WorkPay.isJob(e)) "קבלנות" else if (e.isGroupShift) "משמרת קבוצתית" else "משמרת",
            if (e.bonus > 0) n(e.bonus) else "", textCell(e.notes), if (e.payKnown) "כן" else "חסר"
        ) }
    }
    private fun quote(s: String, d: Char) = if (s.contains(d) || s.any { it == '\n' || it == '\r' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    fun excel(entries: List<WorkEntry>) = table(entries).joinToString("\n") { row -> row.joinToString("\t") { quote(it, '\t') } }
    fun csv(entries: List<WorkEntry>) = "\ufeff" + table(entries).joinToString("\r\n") { row -> row.joinToString(",") { quote(it, ',') } }
}
