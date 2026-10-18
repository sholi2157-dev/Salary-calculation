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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

/** One line per insight (V2Summary), in the words both platforms use. */
fun insightText(i: com.example.domain.V2Summary.Insight): String = when (i) {
    is com.example.domain.V2Summary.Insight.Unpaid -> i.amounts.joinToString(" · ") { (cur, v) -> KisFormat.money(v, cur) } + " עדיין לא סומנו כשולמו"
    is com.example.domain.V2Summary.Insight.Pending -> if (i.count == 1) "משמרת אחת ממתינה לתעריף" else "${i.count} משמרות ממתינות לתעריף"
    is com.example.domain.V2Summary.Insight.Trend -> "${kotlin.math.abs(i.percent)}% ${if (i.percent > 0) "יותר" else "פחות"} שעות מ${KisFormat.MONTHS[(monthKeyOf(i.previousMonth) ?: 0) % 12]}"
    is com.example.domain.V2Summary.Insight.Day -> "יום ${KisFormat.DAY_LONG[i.day]} הוא היום העמוס שלך"
}

/**
 * "החודש שלך": one month of done shifts (planned ones never count), amounts per currency, the hourly average,
 * paid and unpaid, a few insights and the busy days. Opens the story recap of the month or the year.
 * Opened from Home, from History's month view, from the bell and from the monthly reminder.
 */
@Composable
fun MonthSummarySheet(month: Int, entries: List<WorkEntry>, categories: List<WorkCategory>, onHistory: () -> Unit, onDismiss: () -> Unit, onMessage: (String) -> Unit,
                      onStory: (String) -> Unit = {}) {
    val c = Kis.colors
    val context = LocalContext.current
    val done = remember(entries) { KisHistoryLogic.done(entries) }
    val rows = remember(done, month) { done.filter { HistoryFilter.monthKey(it.date) == month } }
    val scope = com.example.domain.V2Summary.monthScope(month)
    val s = remember(done, month) { com.example.domain.V2Summary.summary(done.map { com.example.domain.V2Summary.row(it) }, scope, com.example.domain.V2Summary.today()) }
    KisSheet(onDismiss = onDismiss, tag = "month_summary_sheet") {
        Text("החודש שלך · ${HistoryFilter.monthName(month)}", style = KisType.title, color = c.ink)
        if (rows.isEmpty()) Text("לא נרשמו משמרות בחודש הזה.", style = KisType.body, color = c.muted)
        else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The story recap, the most fun way in.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Workplaces[0].from, Workplaces[4].from)))
                .kisClick { onStory(scope) }.testTag("month_story").padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${KisFormat.MONTHS[month % 12]} שלך בכיס", style = KisType.title, color = Color.White)
                    Text("הסיכום החודשי", style = KisType.caption, color = Color.White.copy(alpha = .85f))
                }
                KisLogo(Modifier.size(34.dp))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.bg).padding(16.dp).semantics(mergeDescendants = true) {}.testTag("month_summary_money"),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.currencies.isEmpty()) Text("עוד חסר תעריף", style = KisType.amount, color = c.ink)
                else s.currencies.forEach { MoneyText(it.total, it.currency, KisType.hero.copy(fontSize = 40.sp, lineHeight = 44.sp), color = c.ink) }
                Text("${rows.size} משמרות · ${KisFormat.hours(s.hours)} שעות", style = KisType.label, color = c.muted, modifier = Modifier.testTag("month_summary_counts"))
            }
            // Per currency: the hourly average (jobs excluded) and what is still unpaid.
            s.currencies.forEach { cur ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("ממוצע לשעה", cur.perHour?.let { KisFormat.money(it, cur.currency) } ?: "—", Modifier.weight(1f), "month_rate_${cur.currency}")
                    Tile("לא שולם", KisFormat.money(cur.unpaid, cur.currency), Modifier.weight(1f), "month_unpaid_${cur.currency}")
                }
            }
            s.insights.forEachIndexed { i, ins ->
                Text(insightText(ins), style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 16.dp, vertical = 12.dp).testTag("month_insight_$i"))
            }
            s.bestDay?.let { best -> WeekBars(s.days, best) }
            KisButton("${scope.take(4)} שלך בכיס", { onStory(scope.take(4)) }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "year_story")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("להיסטוריה", onHistory, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "month_summary_history")
            KisButton("שיתוף", { KisShare.shareOrCopy(context, WorkShareText.summary(rows), "הסיכום של ${HistoryFilter.monthName(month)}", onMessage) },
                Modifier.weight(1f), height = 48.dp, enabled = rows.isNotEmpty(), tag = "month_summary_share")
        }
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier, tag: String) {
    val c = Kis.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}.testTag(tag)) {
        Text(title, style = KisType.caption, color = c.muted)
        Text(value, style = KisType.amount.copy(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 20.sp), color = c.ink, maxLines = 1)
    }
}

/** Hours per weekday; the busiest one in the app's purple. */
@Composable
private fun WeekBars(days: List<Double>, best: Int) {
    val c = Kis.colors
    val max = days.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(120.dp).testTag("month_days"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, h ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 34.dp).fillMaxWidth().fillMaxHeight(((h / max).toFloat() * .8f).coerceAtLeast(.04f)).clip(RoundedCornerShape(10.dp))
                    .background(if (i == best) Workplaces[0].accent(c.dark) else c.sunk))
                Text(KisFormat.DAY_SHORT[i], style = KisType.caption, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
