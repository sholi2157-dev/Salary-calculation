package com.example.ui.kis

import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkEntryEdits
import com.example.data.WorkPay
import com.example.domain.V2Rules
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

        /** A fresh draft for [category] with its saved rate and currency and a start suggested from history. */
        fun new(category: WorkCategory?, currency: String, history: List<WorkEntry>, day: Long = startOfDay(System.currentTimeMillis())): EntryDraft {
            val name = category?.name ?: ""
            return EntryDraft(category = name, day = day, rate = category?.defaultRate?.let(::clean) ?: "", currency = currency,
                start = suggestStart(name, day, history).start)
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
        start = start, end = end, breakMinutes = breakMin, hours = hours,
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
            if (workers.any { w -> num(w.hours)?.let { it < 0 || it > 24 } == true }) errors += "שעות העובד לא תקינות"
            if (separateRates && listOf(employerRate, workerRate).any { it.isNotBlank() && num(it) == null }) errors += "תעריף לא תקין"
        }
        val total = if (mode == EntryMode.JOB) ev.total else ev.total?.let { WorkPay.amount((ev.minutes ?: 0) / 60.0, rateValue() ?: 0.0, ev.bonus) }
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
            job || !clock -> num(hours) ?: 0.0
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
            WorkPay.Worker(w.name.trim(), num(w.hours) ?: 0.0, w.isPaid, num(w.bonus)?.takeIf { it > 0 } ?: 0.0, null, null, JSONObject(w.source))
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
    fun withMode(m: EntryMode): EntryDraft = copy(mode = m)

    /** A different workplace brings its rate and currency on a new entry, never on an edit. */
    fun withCategory(c: WorkCategory, currencyOf: (String) -> String, history: List<WorkEntry>): EntryDraft =
        if (editing) copy(category = c.name)
        else copy(category = c.name, rate = clean(c.defaultRate), rateUnknown = false, currency = currencyOf(c.name),
            start = suggestStart(c.name, day, history).start ?: start)

    /** Spreads the bonus to the team according to the target; each worker's field stays editable. */
    fun withBonusTarget(t: BonusTarget): EntryDraft = copy(bonusTarget = t, workers = workers.map {
        when (t) { BonusTarget.ME -> it.copy(bonus = ""); else -> it.copy(bonus = if (it.bonus.isBlank()) bonus else it.bonus) }
    })
}
