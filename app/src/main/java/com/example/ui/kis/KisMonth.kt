package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.PlanSettings
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.data.WorkShareText

/** Month key (HistoryFilter.monthKey) of a "YYYY-MM" string, or null. */
fun monthKeyOf(month: String): Int? = runCatching { val (y, m) = month.split("-").map { it.toInt() }; y * 12 + m - 1 }.getOrNull()

/**
 * "החודש שלך": a short summary of one month of done shifts (planned ones never count). Amounts stay per currency.
 * Opened from the monthly reminder, from "התראות אחרונות" and from History's month view.
 */
@Composable
fun MonthSummarySheet(month: Int, entries: List<WorkEntry>, categories: List<WorkCategory>, onHistory: () -> Unit, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    val done = remember(entries) { KisHistoryLogic.done(entries) }
    val rows = remember(done, month) { done.filter { HistoryFilter.monthKey(it.date) == month } }
    val before = remember(done, month) { done.filter { HistoryFilter.monthKey(it.date) == month - 1 } }
    val totals = WorkPay.totals(rows)
    val hours = rows.sumOf { it.hours }
    val lead = rows.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.hours } }.maxByOrNull { it.value }
    KisSheet(onDismiss = onDismiss, tag = "month_summary_sheet") {
        Text("החודש שלך · ${HistoryFilter.monthName(month)}", style = KisType.title, color = c.ink)
        if (rows.isEmpty()) Text("לא נרשמו משמרות בחודש הזה.", style = KisType.body, color = c.muted)
        else Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.bg).padding(16.dp).semantics(mergeDescendants = true) {}.testTag("month_summary_money"),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (totals.isEmpty()) Text("עוד חסר תעריף", style = KisType.amount, color = c.ink)
                else totals.forEach { (cur, v) -> MoneyText(v, cur, KisType.hero.copy(fontSize = 40.sp, lineHeight = 44.sp), color = c.ink) }
                Text("${rows.size} משמרות · ${KisFormat.hours(hours)} שעות", style = KisType.label, color = c.muted, modifier = Modifier.testTag("month_summary_counts"))
                val pending = WorkPay.pending(rows).size
                if (pending > 0) Text(if (pending == 1) "משמרת אחת עוד בלי תעריף (לא בסכום)" else "$pending משמרות עוד בלי תעריף (לא בסכום)", style = KisType.caption, color = c.pending)
            }
            lead?.let { (name, h) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(name, workplaceOf(name, categories), size = 36.dp)
                    Column {
                        Text("הכי הרבה ב$name", style = KisType.label, color = c.ink)
                        Text("${KisFormat.hours(h)} שעות", style = KisType.caption, color = c.muted)
                    }
                }
            }
            if (before.isNotEmpty()) {
                val diff = rows.size - before.size
                val hDiff = hours - before.sumOf { it.hours }
                Text("לעומת ${HistoryFilter.monthName(month - 1)}: " +
                    (if (diff == 0) "אותו מספר משמרות" else if (diff > 0) "$diff משמרות יותר" else "${-diff} משמרות פחות") +
                    " · " + (if (kotlin.math.abs(hDiff) < 0.01) "אותן שעות" else "${if (hDiff > 0) "+" else "−"}${KisFormat.hours(kotlin.math.abs(hDiff))} שעות"),
                    style = KisType.body, color = c.ink, modifier = Modifier.testTag("month_summary_compare"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("לפרטים בהיסטוריה", onHistory, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "month_summary_history")
            KisButton("שיתוף", { KisShare.shareOrCopy(context, WorkShareText.summary(rows), "הסיכום של ${HistoryFilter.monthName(month)}", onMessage) },
                Modifier.weight(1f), height = 48.dp, enabled = rows.isNotEmpty(), tag = "month_summary_share")
        }
    }
}

/** "התראות אחרונות": what was sent, newest first; the monthly one opens its summary again. */
@Composable
fun RecentNotesSheet(recent: List<PlanSettings.Sent>, onMonth: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Kis.colors
    KisSheet(onDismiss = onDismiss, tag = "recent_notes_sheet") {
        Text("התראות אחרונות", style = KisType.title, color = c.ink)
        if (recent.isEmpty()) Text("עוד לא נשלחו התראות. אפשר להפעיל סיכום חודשי ותזכורת לתעריף חסר כאן בהגדרות.", style = KisType.body, color = c.muted)
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEachIndexed { i, n ->
                val month = if (n.kind == "monthly") monthKeyOf(n.month) else null
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(c.bg)
                    .then(if (month != null) Modifier.kisClick { onMonth(month) } else Modifier).testTag("recent_note_$i").padding(12.dp)) {
                    Text(n.title, style = KisType.label, color = c.ink)
                    Text(n.text, style = KisType.caption, color = c.muted)
                    Text(KisShare.shortDate(n.time) + if (month != null) " · נגיעה לסיכום" else "", style = KisType.micro, color = c.muted)
                }
            }
        }
    }
}
