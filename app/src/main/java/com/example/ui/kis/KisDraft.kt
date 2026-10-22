package com.example.ui.kis

import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkEntryEdits
import com.example.data.WorkPay
import com.example.domain.V2Rules
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** UI durations use H:MM; old decimal drafts and stored hours remain decimal internally. */
object DurationInput {
    fun digits(text: String): String = display(text).takeIf { Regex("\\d{1,2}:\\d{2}").matches(it) }?.replace(":", "")?.padStart(4, '0') ?: "····"
    fun parseDigits(digits: String): String? {
        val s = digits.replace('·', '0')
        if (s.length != 4 || s.any { !it.isDigit() }) return null
        val h = s.take(2).toInt(); val m = s.drop(2).toInt()
        return if (m < 60 && h * 60 + m <= 1440) "%d:%02d".format(java.util.Locale.ROOT, h, m) else null
    }
    fun edit(text: String): String = text.replace('.', ':').replace(',', ':')
    fun hours(text: String): Double? {
        val s = text.trim()
        if (!s.contains(':')) return s.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        if (!Regex("\\d{1,2}:\\d{2}").matches(s)) return null
        val (h, m) = s.split(':').map { it.toInt() }
        return if (m < 60) (h * 60 + m) / 60.0 else null
    }
    fun display(text: String): String {
        if (text.isBlank() || text.contains(':')) return text
        val h = hours(text) ?: return text
        val minutes = Math.round(h * 60).toInt()
        return "%d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)
    }
}

enum class EntryMode(val label: String) { CLOCK("שעון"), HOURS("ידני"), JOB("קבלנות"), GROUP("קבוצה") }
enum class BonusTarget(val label: String) { ALL("כולם"), ME("רק אני"), OTHERS("רק הצוות") }

data class WorkerDraft(val name: String, val hours: String = "", val bonus: String = "", val isPaid: Boolean = false, val source: String = "{}")

/**
 * Everything the add/edit page holds. Plain values only, so it survives rotation and process death
 * (saved as JSON) and can be tested without Android.
 */
data class EntryDraft(
    val id: Int? = null,
    val mode: EntryMode = EntryMode.CLOCK,
    val category: String = "",
    val day: Long = startOfDay(System.currentTimeMillis()),
    val start: String? = null,
    val end: String = V2Rules.roundedEnd(nowMinutes()),
    val breakMin: Int = 0,
    val hours: String = "",
    val rate: String = "",
    val rateUnknown: Boolean = false,
    val currency: String = "₪",
    val bonus: String = "",
    val bonusTarget: BonusTarget = BonusTarget.ME,
    val amount: String = "",
    val amountUnknown: Boolean = false,
    val notes: String = "",
    val workers: List<WorkerDraft> = emptyList(),
    val separateRates: Boolean = false,
    val employerRate: String = "",
    val workerRate: String = "",
    val isPaid: Boolean = false,
) {
    val editing get() = id != null

    fun toJson(): String = JSONObject().apply {
        put("id", id ?: JSONObject.NULL); put("mode", mode.name); put("category", category); put("day", day)
        put("start", start ?: JSONObject.NULL); put("end", end); put("breakMin", breakMin); put("hours", hours); put("rate", rate)
        put("rateUnknown", rateUnknown); put("currency", currency); put("bonus", bonus); put("bonusTarget", bonusTarget.name)
        put("amount", amount); put("amountUnknown", amountUnknown); put("notes", notes); put("separateRates", separateRates)
        put("employerRate", employerRate); put("workerRate", workerRate); put("isPaid", isPaid)
        put("workers", JSONArray().apply { workers.forEach { w -> put(JSONObject().put("name", w.name).put("hours", w.hours).put("bonus", w.bonus).put("isPaid", w.isPaid).put("source", w.source)) } })
    }.toString()

    companion object {
        fun fromJson(s: String): EntryDraft {
            val o = JSONObject(s)
            val ws = o.optJSONArray("workers") ?: JSONArray()
            return EntryDraft(
                id = if (o.isNull("id")) null else o.getInt("id"), mode = EntryMode.valueOf(o.getString("mode")), category = o.getString("category"),
                day = o.getLong("day"), start = if (o.isNull("start")) null else o.getString("start"), end = o.getString("end"), breakMin = o.optInt("breakMin"),
                hours = o.optString("hours"), rate = o.optString("rate"), rateUnknown = o.optBoolean("rateUnknown"), currency = o.optString("currency", "₪"),
                bonus = o.optString("bonus"), bonusTarget = BonusTarget.valueOf(o.optString("bonusTarget", "ME")), amount = o.optString("amount"),
                amountUnknown = o.optBoolean("amountUnknown"), notes = o.optString("notes"), separateRates = o.optBoolean("separateRates"),
                employerRate = o.optString("employerRate"), workerRate = o.optString("workerRate"), isPaid = o.optBoolean("isPaid"),
                workers = (0 until ws.length()).map { i -> ws.getJSONObject(i).let { WorkerDraft(it.getString("name"), it.optString("hours"), it.optString("bonus"), it.optBoolean("isPaid"), it.optString("source", "{}")) } },
            )
        }

        fun nowMinutes(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun clean(v: Double) = KisFormat.hours(v)

        /** A new shift's length until the user moves it: both times are always shown, never an empty start. */
        const val DEFAULT_MINUTES = 4 * 60

        /** [DEFAULT_MINUTES] before [end]. */
        fun defaultStart(end: String): String = V2Rules.fromMinutes(V2Rules.toMinutes(end) - DEFAULT_MINUTES)

        /** A fresh draft for [category] with its saved rate and currency, ending now (rounded) and starting [DEFAULT_MINUTES] earlier. */
        fun new(category: WorkCategory?, currency: String, day: Long = startOfDay(System.currentTimeMillis())): EntryDraft {
            val end = V2Rules.roundedEnd(nowMinutes())
            return EntryDraft(category = category?.name ?: "", day = day, rate = category?.defaultRate?.let(::clean) ?: "", currency = currency,
                start = defaultStart(end), end = end)
        }

        /** The editable form of a saved entry. Break minutes are inferred exactly as before (range − saved hours). */
        fun from(e: WorkEntry): EntryDraft {
            val job = WorkPay.isJob(e)
            // Only well-formed times open as a clock; anything else (old imports) opens as hours, never crashes.
            val clock = e.isTimeRange && V2Rules.isTime(e.startTime) && V2Rules.isTime(e.endTime)
            val mode = when { job -> EntryMode.JOB; e.isGroupShift -> EntryMode.GROUP; clock -> EntryMode.CLOCK; else -> EntryMode.HOURS }
            val breakMin = Math.round(WorkEntryEdits.breakMinutes(e)).toInt()
            return EntryDraft(
                id = e.id, mode = mode, category = e.category, day = startOfDay(e.date), start = if (clock) e.startTime else null,
                end = if (clock) e.endTime!! else V2Rules.roundedEnd(nowMinutes()), breakMin = breakMin,
                hours = clean(e.hours),
                rate = if (!job && e.payKnown) clean(e.hourlyRate) else "", rateUnknown = !job && !e.payKnown, currency = e.currency,
                bonus = if (e.bonus > 0) clean(e.bonus) else "", amount = if (job && e.payKnown) clean(e.totalEarnings) else "",
                amountUnknown = job && !e.payKnown, notes = e.notes,
                workers = WorkPay.workers(e).map { WorkerDraft(it.name, clean(it.hours), if (it.bonus > 0) clean(it.bonus) else "", it.isPaid, it.source.toString()) },
                separateRates = e.employerRate != null || e.workerRate != null,
                employerRate = e.employerRate?.let(::clean) ?: "", workerRate = e.workerRate?.let(::clean) ?: "", isPaid = e.isPaid,
            )
        }

        data class StartSuggestion(val start: String?, val chips: List<String>)

        /** Same as the prototype: a start is pre-filled only when it is clearly the habit (3+ times on this weekday). */
        fun suggestStart(category: String, day: Long, history: List<WorkEntry>): StartSuggestion {
            fun weekday(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
            fun ranked(list: List<WorkEntry>) = list.mapNotNull { e -> e.startTime?.takeIf { e.isTimeRange } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val sameDay = ranked(history.filter { it.category == category && weekday(it.date) == weekday(day) })
            val chips = ranked(history.filter { it.category == category }).take(3).map { it.key }
                .ifEmpty { ranked(history).take(3).map { it.key } }
            return StartSuggestion(sameDay.firstOrNull()?.takeIf { it.value >= 3 }?.key, chips)
        }
    }

    // ---------------------------------------------------------------- rules

    private fun num(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    fun rateValue(): Double? = if (rateUnknown) null else num(rate)
    fun bonusValue(): Double = num(bonus)?.takeIf { it > 0 } ?: 0.0

    /** A group shift may be entered by clock (start–end) or, like before 2.0, by hours only. */
    val groupByHours get() = mode == EntryMode.GROUP && start == null

    fun ruleDraft(): V2Rules.Draft = V2Rules.Draft(
        mode = when {
            mode == EntryMode.CLOCK -> V2Rules.Mode.CLOCK
            mode == EntryMode.HOURS || groupByHours -> V2Rules.Mode.HOURS
            mode == EntryMode.JOB -> V2Rules.Mode.JOB
            else -> V2Rules.Mode.GROUP
        },
        start = start, end = end, breakMinutes = breakMin, hours = DurationInput.hours(hours)?.toString() ?: hours,
        rate = rateValue(), rateUnknown = rateUnknown,
        bonus = if (groupByHours && bonusTarget == BonusTarget.OTHERS) 0.0 else bonusValue(),
        bonusTarget = when (bonusTarget) { BonusTarget.ALL -> V2Rules.BonusTarget.ALL; BonusTarget.ME -> V2Rules.BonusTarget.ME; BonusTarget.OTHERS -> V2Rules.BonusTarget.OTHERS },
        amount = amount, amountUnknown = amountUnknown,
    )

    data class Check(val errors: List<String>, val warnings: List<String>, val minutes: Int?, val total: Double?, val overnight: Boolean) {
        val canSave get() = errors.isEmpty()
    }

    fun check(): Check {
        val ev = V2Rules.evaluate(ruleDraft())
        val errors = ev.errors.toMutableList()
        val warnings = mutableListOf<String>()
        if (category.isBlank()) errors.add(0, "בחר מקום עבודה")
        var overnight = false
        if ((mode == EntryMode.CLOCK || mode == EntryMode.GROUP) && start != null) {
            val sp = V2Rules.span(start, end, breakMin)
            overnight = sp.overnight
            if (sp.long) warnings += "משמרת של יותר מ-16 שעות. לבדוק שהשעות נכונות?"
        }
        if (mode == EntryMode.GROUP) {
            if (workers.any { it.name.isBlank() }) errors += "לכל עובד צריך שם"
            if (workers.any { w -> w.hours.isNotBlank() && (DurationInput.hours(w.hours)?.let { it < 0 || it > 24 } != false) }) errors += "שעות העובד לא תקינות"
            if (separateRates && listOf(employerRate, workerRate).any { it.isNotBlank() && num(it) == null }) errors += "תעריף לא תקין"
        }
        if (mode == EntryMode.JOB && hours.isNotBlank() && (DurationInput.hours(hours)?.let { it < 0 || it > 24 } != false)) errors += "משך לא תקין"
        // Persist decimal hours; the duration UI converts hours and minutes at this boundary.
        val payHours = if (mode == EntryMode.HOURS || groupByHours) DurationInput.hours(hours) ?: 0.0 else (ev.minutes ?: 0) / 60.0
        val total = if (mode == EntryMode.JOB) ev.total else ev.total?.let { WorkPay.amount(payHours, rateValue() ?: 0.0, ev.bonus) }
        return Check(errors, warnings, ev.minutes, total, overnight)
    }

    /**
     * The entry to store. On an edit, a field the user did not touch keeps its exact stored value
     * (hours, rate, bonus, date), so editing a note never re-rounds or recalculates anything.
     */
    fun toEntry(base: WorkEntry? = null): WorkEntry {
        val was = base?.let { from(it) }
        val c = check()
        val clock = (mode == EntryMode.CLOCK || mode == EntryMode.GROUP) && start != null
        val job = mode == EntryMode.JOB
        val sameTimes = was != null && was.mode == mode && was.start == start && (start == null || was.end == end) && was.breakMin == breakMin && was.hours == hours
        val hoursValue = when {
            sameTimes -> base!!.hours
            job || !clock -> DurationInput.hours(hours) ?: 0.0
            else -> (c.minutes ?: 0) / 60.0
        }
        val rateNumber = if (was != null && !was.rateUnknown && was.rate == rate) base!!.hourlyRate else rateValue() ?: 0.0
        val bonusNumber = if (was != null && was.bonus == bonus) base!!.bonus else bonusValue()
        val myBonus = if (job || mode == EntryMode.GROUP && bonusTarget == BonusTarget.OTHERS) 0.0 else bonusNumber
        val payKnown = if (job) !amountUnknown else !rateUnknown
        val date = when {
            base != null && startOfDay(base.date) == day -> base.date
            // Wall-clock time on that day (Calendar handles a 23/25-hour daylight-saving day).
            clock -> Calendar.getInstance().apply { timeInMillis = day; val m = V2Rules.toMinutes(start!!); set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60) }.timeInMillis
            else -> day + 12 * 60 * 60_000L
        }
        val amountNumber = if (was != null && was.amount == amount && !was.amountUnknown) base!!.totalEarnings else num(amount) ?: 0.0
        val groupJson = if (mode == EntryMode.GROUP) WorkPay.encodeWorkers(workers.map { w ->
            val sourceHours = JSONObject(w.source).optDouble("hours", Double.NaN)
            val originalHours = sourceHours.takeIf { it.isFinite() && clean(it) == w.hours }
            WorkPay.Worker(w.name.trim(), originalHours ?: DurationInput.hours(w.hours) ?: 0.0, w.isPaid, num(w.bonus)?.takeIf { it > 0 } ?: 0.0, null, null, JSONObject(w.source))
        }) else ""
        val proto = base ?: WorkEntry(category = category, date = date, isTimeRange = clock, hours = 0.0, hourlyRate = 0.0, totalEarnings = 0.0)
        return proto.copy(
            category = category, date = date, isTimeRange = clock, startTime = if (clock) start else null, endTime = if (clock) end else null,
            hours = hoursValue, hourlyRate = if (job || !payKnown) 0.0 else rateNumber,
            totalEarnings = when {
                !payKnown -> 0.0
                job -> amountNumber
                // Nothing that defines the amount changed: keep the stored (possibly imported) amount exactly.
                base != null && base.payKnown && base.kind == WorkEntry.KIND_SHIFT && hoursValue == base.hours && rateNumber == base.hourlyRate && myBonus == base.bonus -> base.totalEarnings
                else -> WorkPay.amount(hoursValue, rateNumber, myBonus)
            },
            notes = notes.trim(), currency = currency, kind = if (job) WorkEntry.KIND_JOB else WorkEntry.KIND_SHIFT, payKnown = payKnown, bonus = myBonus,
            isGroupShift = mode == EntryMode.GROUP, groupWorkersJson = if (mode == EntryMode.GROUP && was != null && was.workers == workers) base!!.groupWorkersJson else groupJson,
            employerRate = if (mode == EntryMode.GROUP && separateRates) (if (was != null && was.employerRate == employerRate) base!!.employerRate else num(employerRate)) else null,
            workerRate = if (mode == EntryMode.GROUP && separateRates) (if (was != null && was.workerRate == workerRate) base!!.workerRate else num(workerRate)) else null,
            isPaid = isPaid,
        )
    }

    /** Changing the mode keeps everything the user typed that still applies. */
    fun withMode(m: EntryMode): EntryDraft {
        val clock = mode == EntryMode.CLOCK || mode == EntryMode.GROUP && !groupByHours
        if (m == EntryMode.HOURS && clock) return copy(mode = m, hours = check().minutes?.takeIf { it > 0 }?.let { DurationInput.display((it / 60.0).toString()) } ?: "")
        if (m == EntryMode.GROUP && mode == EntryMode.HOURS) return copy(mode = m, start = null)
        if (m == EntryMode.CLOCK && (mode == EntryMode.HOURS || groupByHours)) {
            val minutes = DurationInput.hours(hours)?.let { Math.round(it * 60).toInt() }
            return copy(mode = m, start = minutes?.takeIf { it in 1..1440 }?.let { V2Rules.fromMinutes(V2Rules.toMinutes(end) - it) } ?: start ?: defaultStart(end), breakMin = 0)
        }
        return copy(mode = m)
    }

    /** A different workplace brings its rate and currency on a new entry, never on an edit. The times stay as they are. */
    fun withCategory(c: WorkCategory, currencyOf: (String) -> String): EntryDraft =
        if (editing) copy(category = c.name)
        else copy(category = c.name, rate = clean(c.defaultRate), rateUnknown = false, currency = currencyOf(c.name))

    /** Spreads the bonus to the team according to the target; each worker's field stays editable. */
    fun withBonusTarget(t: BonusTarget): EntryDraft = copy(bonusTarget = t, workers = workers.map {
        when (t) { BonusTarget.ME -> it.copy(bonus = ""); else -> it.copy(bonus = if (it.bonus.isBlank()) bonus else it.bonus) }
    })
}

// ---------------------------------------------------------------- typing a time digit by digit

/** "17:30" → "1730"; no time yet → "····" (four unset digits). */
fun timeDigits(time: String?): String = if (V2Rules.isTime(time)) V2Rules.fromMinutes(V2Rules.toMinutes(time!!)).replace(":", "") else "····"

/** Four digits (unset ones count as 0) → "HH:MM", or null when it is not a time of day. */
fun parseTime(digits: String): String? {
    val s = digits.replace('·', '0')
    if (s.length != 4 || s.any { !it.isDigit() }) return null
    val h = s.take(2).toInt(); val m = s.drop(2).toInt()
    return if (h > 23 || m > 59) null else V2Rules.fromMinutes(h * 60 + m)
}

/**
 * One key on a time being edited in place: the digit at [cursor] (0–1 hours, 2–3 minutes) is replaced and the cursor
 * moves on; 4 means the value is complete. So 05:30 → 05:35 is "tap the last digit, type 5", never the whole time again.
 * A first digit that can only start a one-digit hour (3–9) becomes 0X and jumps to the minutes; likewise 6–9 for minutes.
 */
fun typeTime(digits: String, cursor: Int, key: String, maxHour: Int = 23): Pair<String, Int> {
    val b = digits.padEnd(4, '·').take(4).toCharArray()
    var at = if (cursor in 0..3) cursor else 0
    if (key == "⌫") { if (at > 0) { at--; b[at] = '·' }; return String(b) to at }
    for (k in key) {
        if (!k.isDigit()) continue
        if (at >= 4) break
        when (at) {
            0 -> if (k > '2') { b[0] = '0'; b[1] = k; at = 2 } else { b[0] = k; if (k == '2' && b[1].isDigit() && b[1].digitToInt() > maxHour % 10) b[1] = '0'; at = 1 }
            2 -> if (k > '5') { b[2] = '0'; b[3] = k; at = 4 } else { b[2] = k; at = 3 }
            else -> { b[at] = k; at++ }
        }
    }
    return String(b) to at
}
