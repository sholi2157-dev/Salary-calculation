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
    is com.example.domain.V2Summary.Insight.Day -> "יום ${KisFormat.DAY_LONG[i.day]} הוא היום העמוס שלכם"
}

/**
 * "החודש שלך": one month of done shifts (planned ones never count), amounts per currency, the hourly average,
 * paid and unpaid, a few insights and the busy days. Opens the story recap of the month or the year.
 * Opened from Home, from History's month view, from the bell and from the monthly reminder.
 */
@Composable
fun MonthSummarySheet(month: Int, entries: List<WorkEntry>, categories: List<WorkCategory>, onHistory: () -> Unit, onDismiss: () -> Unit, onMessage: (String) -> Unit,
                      onStory: (String) -> Unit = {}, today: String = com.example.domain.V2Summary.today()) {
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
            // The story recap, the most fun way in, once it has opened (the 30th; no spoilers before).
            if (com.example.domain.V2Summary.recapOpen(scope, today)) RecapEntry("${KisFormat.MONTHS[month % 12]} שלך בכיס", "הסיכום החודשי", "month_story") { onStory(scope) }
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
            // "The busy day" says something only with a few shifts on different days (the story's rule).
            val rhythm = "days" in com.example.domain.V2Summary.slides(s)
            s.insights.filter { rhythm || it !is com.example.domain.V2Summary.Insight.Day }.forEachIndexed { i, ins ->
                Text(insightText(ins), style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 16.dp, vertical = 12.dp).testTag("month_insight_$i"))
            }
            s.bestDay?.takeIf { rhythm }?.let { best -> WeekBars(s.days, best) }
            if (com.example.domain.V2Summary.recapOpen(scope.take(4), today))
                KisButton("${scope.take(4)} שלך בכיס", { onStory(scope.take(4)) }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "year_story")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("להיסטוריה", onHistory, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "month_summary_history")
            KisButton("העתקה", { KisShare.copy(context, WorkShareText.copy(rows)); onMessage("הועתק ✓") }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, enabled = rows.isNotEmpty(), tag = "month_summary_copy")
            KisButton("שיתוף", { KisShare.shareOrCopy(context, WorkShareText.summary(rows), "הסיכום של ${HistoryFilter.monthName(month)}", onMessage) },
                Modifier.weight(1f), height = 48.dp, enabled = rows.isNotEmpty(), tag = "month_summary_share")
        }
    }
}

/** The way into a recap: the app's purple card with its name and the logo. */
@Composable
fun RecapEntry(title: String, subtitle: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Workplaces[0].from, Workplaces[4].from)))
        .kisClick(onClick = onClick).testTag(tag).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KisType.title, color = Color.White)
            Text(subtitle, style = KisType.caption, color = Color.White.copy(alpha = .85f))
        }
        KisLogo(Modifier.size(34.dp))
    }
}

private fun hasShifts(done: List<WorkEntry>, scope: String) = done.any { com.example.domain.V2Summary.row(it).date.startsWith(scope) }
fun recapTitle(scope: String) = if (scope.length == 4) "השנה $scope בכיס" else "${KisFormat.MONTHS[(monthKeyOf(scope) ?: 0) % 12]} ${scope.take(4)} בכיס"

/** The recaps that are new today and have shifts (Home shows them from the 30th for 4 more days). */
fun freshRecaps(entries: List<WorkEntry>, today: String = com.example.domain.V2Summary.today()): List<String> {
    val done = KisHistoryLogic.done(entries)
    val n = com.example.domain.V2Summary.recapNew(today)
    return listOfNotNull(n.year, n.month).filter { hasShifts(done, it) }
}

/** "הסיכומים שלי" (Settings): every recap that has opened and has shifts, newest first. */
@Composable
fun RecapArchiveSheet(entries: List<WorkEntry>, onStory: (String) -> Unit, onDismiss: () -> Unit, today: String = com.example.domain.V2Summary.today()) {
    val c = Kis.colors
    val done = remember(entries) { KisHistoryLogic.done(entries) }
    val list = remember(done, today) { com.example.domain.V2Summary.recapArchive(today).filter { hasShifts(done, it) } }
    KisSheet(onDismiss = onDismiss, tag = "recaps_sheet") {
        Text("הסיכומים שלי", style = KisType.title, color = c.ink)
        if (list.isEmpty()) {
            val first = com.example.domain.V2Summary.FIRST_RECAP
            Text("הסיכום הראשון יגיע ב-${com.example.domain.V2Summary.recapDay(first).takeLast(2).toInt()} ב${KisFormat.MONTHS[(monthKeyOf(first) ?: 0) % 12]}.",
                style = KisType.body, color = c.muted, modifier = Modifier.testTag("recaps_empty"))
        } else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { scope ->
                val s = remember(done, scope) { com.example.domain.V2Summary.summary(done.map { com.example.domain.V2Summary.row(it) }, scope, today) }
                RecapEntry(recapTitle(scope), "${if (s.count == 1) "משמרת אחת" else "${s.count} משמרות"} · ${KisFormat.hours(s.hours)} שעות", "recap_$scope") { onStory(scope) }
            }
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
