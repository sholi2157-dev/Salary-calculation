package com.example.ui.kis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShiftSeries
import com.example.data.WorkCategory
import com.example.data.WorkPay
import com.example.domain.V2Plan
import com.example.domain.V2Rules
import java.util.Calendar

/*
 * Weekly plans in the existing screens (stage C): "הבא בתור" on Home, the planned view in History,
 * "חוזרת כל שבוע" in the editor and the plan list in Settings. Planned shifts are never counted as earned.
 */

/** What the screens can do with a planned occurrence (wired in KisApp). */
class PlanActions(
    val worked: (V2Plan.Occurrence) -> Unit,
    val skip: (V2Plan.Occurrence) -> Unit,
    val hours: (V2Plan.Occurrence) -> Unit,
    val share: (V2Plan.Occurrence) -> Unit,
)

private fun planMillis(o: V2Plan.Occurrence) = V2Plan.startMillis(o.date, o.start)

/** A planned (or ended, waiting) occurrence: dashed avatar, muted amount, and its actions when opened. */
@Composable
fun PlannedCard(o: V2Plan.Occurrence, categories: List<WorkCategory>, onWorked: () -> Unit, onSkip: () -> Unit, onHours: () -> Unit, onShare: () -> Unit) {
    val c = Kis.colors
    var open by rememberSaveable(o.key) { mutableStateOf(false) }
    val workplace = workplaceOf(o.category, categories)
    val waiting = o.status == V2Plan.Status.AWAITING
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).kisClick { open = !open }
        .semantics { stateDescription = if (open) "פתוח" else "סגור" }.testTag("planned_card_${o.key}").padding(horizontal = 14.dp, vertical = 12.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(o.category, workplace, planned = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dayLabel(planMillis(o)), style = KisType.label.copy(fontSize = 15.sp), color = c.ink, maxLines = 1)
                    Tag(if (waiting) "ממתין לאישור" else "מתוכנן", warn = waiting)
                }
                val overnight = V2Rules.toMinutes(o.end) <= V2Rules.toMinutes(o.start)
                Text("${o.start}–${o.end}${if (overnight) " · למחרת" else ""} · ${o.category}", style = KisType.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val minutes = V2Rules.span(o.start, o.end).minutes
            if (o.rate != null) MoneyText(WorkPay.amount(minutes / 60.0, o.rate), o.currency, KisType.amount.copy(fontSize = 17.sp), color = c.muted)
            else Text("?", style = KisType.amount.copy(fontSize = 17.sp), color = c.muted)
        }
        // Two rows of two: every label stays whole on a narrow phone and each button is a full 48dp target.
        if (open) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KisButton("עבדתי", onWorked, Modifier.weight(1f), height = 48.dp, tag = "plan_worked_${o.key}")
                KisButton("לא עבדתי", onSkip, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "plan_skip_${o.key}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KisButton("שינוי שעות", onHours, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "plan_hours_${o.key}")
                KisButton("שיתוף", onShare, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "plan_share_${o.key}")
            }
        }
    }
}

/** Home: what ended and waits to be recorded, then the next few planned shifts. */
@Composable
fun NextUp(occurrences: List<V2Plan.Occurrence>, categories: List<WorkCategory>, onWorked: (V2Plan.Occurrence) -> Unit, onSkip: (V2Plan.Occurrence) -> Unit,
           onHours: (V2Plan.Occurrence) -> Unit, onShare: (V2Plan.Occurrence) -> Unit, onAll: () -> Unit) {
    // A short list: the rest is one tap away in History ("הכול").
    val waiting = occurrences.filter { it.status == V2Plan.Status.AWAITING }.takeLast(3)
    val next = occurrences.filter { it.status == V2Plan.Status.PLANNED }.take(3)
    if (waiting.isEmpty() && next.isEmpty()) return
    Column(Modifier.testTag("next_up")) {
        SectionTitle("הבא בתור", action = "הכול", onAction = onAll, actionTag = "next_up_all")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (waiting + next).forEach { o -> PlannedCard(o, categories, { onWorked(o) }, { onSkip(o) }, { onHours(o) }, { onShare(o) }) }
        }
    }
}

/** New hours for one occurrence: just this time, or from now on. */
@Composable
fun PlanHoursSheet(o: V2Plan.Occurrence, onDismiss: () -> Unit, onSave: (start: String, end: String, fromNowOn: Boolean) -> Unit) {
    val c = Kis.colors
    var start by rememberSaveable { mutableStateOf(o.start) }
    var end by rememberSaveable { mutableStateOf(o.end) }
    KisSheet(onDismiss = onDismiss, tag = "plan_hours_sheet") {
        Text("שעות ל${dayLabelLong(planMillis(o))}", style = KisType.title, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeChip("התחלה", start, Modifier.weight(1f), "plan_start") { start = it }
            TimeChip("סיום", end, Modifier.weight(1f), "plan_end") { end = it }
        }
        val valid = !V2Rules.span(start, end).invalid
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("רק הפעם", { onSave(start, end, false) }, Modifier.weight(1f), enabled = valid, tag = "plan_hours_once")
            KisButton("מעכשיו והלאה", { onSave(start, end, true) }, Modifier.weight(1f), KisButtonKind.GHOST, enabled = valid, tag = "plan_hours_on")
        }
    }
}

/** A time chip that opens the system 24-hour time picker. */
@Composable
fun TimeChip(label: String, value: String, modifier: Modifier, tag: String, onPick: (String) -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.sunk).kisClick {
        val m = V2Rules.toMinutes(value)
        TimePickerDialog(context, { _, h, mm -> onPick(V2Rules.fromMinutes(h * 60 + mm)) }, m / 60, m % 60, true).show()
    }.testTag(tag).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = KisType.caption, color = c.muted)
        Text(value, style = KisType.amount.copy(fontSize = 22.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), color = c.ink)
    }
}

/** Editor block: weekdays and an end date for "חוזרת כל שבוע", with an honest preview of what will be created. */
@Composable
fun RepeatBlock(days: Set<Int>, until: String, preview: V2Plan.Preview?, onDays: (Set<Int>) -> Unit, onUntil: (String) -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("repeat_block")) {
        Text("בימים", style = KisType.label, color = c.ink)
        // All seven days share the width, so none is pushed off a narrow screen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { d ->
                val on = d in days
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).background(if (on) c.ink else c.surface)
                    .border(1.5.dp, if (on) c.ink else c.line, shape).kisClick { onDays(if (on) days - d else days + d) }
                    .semantics { selected = on }.testTag("repeat_day_$d"), contentAlignment = Alignment.Center) {
                    Text(KisFormat.DAY_SHORT[d], style = KisType.label, color = if (on) c.onInk else c.ink, maxLines = 1)
                }
            }
        }
        KisChip("עד ${KisShare.shortDate(V2Plan.startMillis(until, "12:00"))}", tag = "repeat_until", onClick = {
            val (y, m, d) = until.split("-").map { it.toInt() }
            DatePickerDialog(context, { _, yy, mm, dd -> onUntil(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", yy, mm + 1, dd)) }, y, m - 1, d).show()
        })
        if (preview != null) Text(
            if (preview.dates.isEmpty()) "אין ימים בטווח שנבחר" else buildString {
                append(if (preview.created.size == 1) "משמרת אחת תיכנס ללוח" else "${preview.created.size} משמרות ייכנסו ללוח")
                if (preview.clashes.isNotEmpty()) append(" · ${preview.clashes.size} חופפות למשמרת קיימת ולא ייווצרו")
            }, style = KisType.caption, color = c.muted, modifier = Modifier.testTag("repeat_preview"))
    }
}

fun daysLabel(mask: Int): String = V2Plan.days(mask).sorted().joinToString(" ") { KisFormat.DAY_SHORT[it] }

/** Settings: every weekly plan with what is left of it; change its rate from today, or end it. */
@Composable
fun SeriesSheet(series: List<ShiftSeries>, occurrences: List<V2Plan.Occurrence>, categories: List<WorkCategory>, autoConfirm: Boolean, onAuto: (Boolean) -> Unit,
                onRate: (ShiftSeries, Double?) -> Unit, onEnd: (ShiftSeries) -> Unit, onDismiss: () -> Unit) {
    val c = Kis.colors
    var rateFor by remember { mutableStateOf<ShiftSeries?>(null) }
    var ending by remember { mutableStateOf<ShiftSeries?>(null) }
    KisSheet(onDismiss = onDismiss, tag = "series_sheet") {
        Text("משמרות קבועות", style = KisType.title, color = c.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("רישום אוטומטי", style = KisType.label, color = c.ink)
                Text("כשהזמן נגמר המשמרת נרשמת לבד. תמיד אפשר לבטל.", style = KisType.caption, color = c.muted)
            }
            KisSwitch(autoConfirm, onAuto, Modifier.testTag("auto_confirm_switch"), label = "רישום אוטומטי")
        }
        KisDivider()
        if (series.isEmpty()) Text("עוד אין משמרות קבועות. יוצרים אותן במסך ההוספה: \"חוזרת כל שבוע\".", style = KisType.body, color = c.muted)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            series.forEach { s ->
                val left = occurrences.count { it.seriesId == s.id && it.status == V2Plan.Status.PLANNED }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(12.dp).testTag("series_${s.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(s.category, workplaceOf(s.category, categories), size = 36.dp, planned = true)
                        Column(Modifier.weight(1f)) {
                            Text(s.category, style = KisType.label, color = c.ink)
                            Text("${daysLabel(s.daysMask)} · ${s.startTime}–${s.endTime} · עד ${KisShare.shortDate(V2Plan.startMillis(s.untilDate, "12:00"))}" +
                                if (left > 0) " · עוד $left" else "", style = KisType.caption, color = c.muted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KisChip(s.hourlyRate?.let { "${KisFormat.money(it, s.currency)} לשעה" } ?: "תעריף חסר", warn = s.hourlyRate == null, tag = "series_rate_${s.id}",
                            onClick = { rateFor = s })
                        KisChip("סיום הסדרה", tag = "series_end_${s.id}", onClick = { ending = s })
                    }
                }
            }
        }
    }
    rateFor?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.hourlyRate?.let { KisFormat.hours(it) } ?: "") }
        val n = occurrences.count { it.seriesId == s.id && it.status == V2Plan.Status.PLANNED }
        KisSheet(onDismiss = { rateFor = null }, tag = "series_rate_sheet") {
            Text("תעריף חדש לסדרה", style = KisType.title, color = c.ink)
            Text(if (n == 1) "יחול על המשמרת הבאה. מה שכבר נרשם לא משתנה." else "יחול על $n המשמרות הבאות. מה שכבר נרשם לא משתנה.", style = KisType.body, color = c.muted)
            KisTextField(text, { v -> text = v.filter { it.isDigit() || it == '.' } }, "לשעה (${s.currency})", Modifier.fillMaxWidth().testTag("series_rate_input"),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            val rate = text.toDoubleOrNull()?.takeIf { it >= 0 }
            KisButton("החלה", { onRate(s, rate); rateFor = null }, Modifier.fillMaxWidth(), enabled = rate != null, tag = "series_rate_apply")
        }
    }
    ending?.let { s ->
        KisSheet(onDismiss = { ending = null }, tag = "series_end_sheet") {
            Text("לסיים את הסדרה?", style = KisType.title, color = c.ink)
            Text("משמרות מתוכננות מעכשיו לא יופיעו יותר. מה שכבר נרשם נשאר כמו שהוא.", style = KisType.body, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("ביטול", { ending = null }, Modifier.weight(1f), KisButtonKind.GHOST)
                KisButton("סיום", { onEnd(s); ending = null }, Modifier.weight(1f), KisButtonKind.DANGER, tag = "series_end_confirm")
            }
        }
    }
}

/** Today as "YYYY-MM-DD" plus a number of days. */
fun isoDayPlus(base: Long, days: Int): String = V2Rules.civilFromDays(V2Rules.daysFromCivil(V2Plan.dateOf(base)) + days)
fun weekdayOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) - 1
