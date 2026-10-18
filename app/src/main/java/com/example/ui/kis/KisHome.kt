package com.example.ui.kis

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.ui.CoachTarget
import com.example.ui.WorkViewModel
import com.example.ui.coachTarget
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** Live shift as the home screen sees it. */
data class LiveShift(val start: Long, val category: String, val rate: Double, val currency: String)

@Composable
fun KisHome(
    entries: List<WorkEntry>,
    categories: List<WorkCategory>,
    live: LiveShift?,
    bottomInset: Dp,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onCancelLive: () -> Unit,
    onLiveCurrency: (String) -> Unit,
    onOpen: (WorkEntry) -> Unit,
    onPending: () -> Unit,
    onAllHistory: () -> Unit,
    onSettings: () -> Unit,
    plans: List<com.example.domain.V2Plan.Occurrence> = emptyList(),
    planActions: PlanActions? = null,
    onAllPlans: () -> Unit = {},
    unread: Boolean = false,
    onBell: () -> Unit = {},
) {
    val c = Kis.colors
    // A clock that moves on by itself, so "today" and "this week" roll over without new data.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val done = remember(entries, now) { KisHistoryLogic.done(entries, now) }
    val week = remember(done, now) { val from = KisHistoryLogic.weekStart(now); done.filter { it.date >= from } }
    val today = remember(done, now) { val from = EntryDraft.startOfDay(now); done.filter { it.date >= from } }
    val month = remember(done, now) { val key = HistoryFilter.monthKey(now); done.filter { HistoryFilter.monthKey(it.date) == key } }
    val hoursByCat = remember(week) { week.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.hours } }.toList().sortedByDescending { it.second } }
    Box(Modifier.fillMaxSize()) {
        KisGlow(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = bottomInset).testTag("dashboard_scroll_container")) {
            Hello(unread, onBell)
            Hero(week, today, done, hoursByCat, categories, onPending,
                plannedThisWeek = remember(plans, now) { val from = com.example.domain.V2Plan.dateOf(KisHistoryLogic.weekStart(now)); val to = isoDayPlus(KisHistoryLogic.weekStart(now), 6)
                    plans.count { it.status == com.example.domain.V2Plan.Status.PLANNED && it.date in from..to } })
            NowRow(live, onStartLive, onStopLive, onCancelLive, onLiveCurrency)
            val upcoming = remember(entries, now) { entries.filter { KisHistoryLogic.upcoming(it, now) } }
            NextUp(plans, upcoming, categories, planActions, onOpen, onAllPlans)
            val recent = remember(entries) { entries.sortedByDescending { it.createdAt }.take(3) }
            if (recent.isNotEmpty()) {
                SectionTitle("אחרונות", action = "הכול", onAction = onAllHistory, actionTag = "home_all_history")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { recent.forEach { e -> ShiftCard(e, categories, onClick = { onOpen(e) }, tagPrefix = "recent_shift_card_") } }
            }
            if (categories.isNotEmpty() && month.isNotEmpty()) {
                SectionTitle("הכיס של החודש")
                Wallet(month, categories, onOpen)
            }
            if (entries.isEmpty()) EmptyHome()
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "בוקר טוב"; in 12..16 -> "צהריים טובים"; in 17..20 -> "ערב טוב"; else -> "לילה טוב"
}

@Composable
private fun Hello(unread: Boolean, onBell: () -> Unit) {
    val c = Kis.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KisLogo()
        Column(Modifier.weight(1f)) {
            val name = Kis.settings.name.trim()
            Text(if (name.isEmpty()) greeting() else "${greeting()}, $name", style = KisType.caption, color = c.muted, maxLines = 1,
                modifier = Modifier.testTag("home_greeting"))
            Text(stringResource(R.string.app_name), style = KisType.label.copy(fontSize = 16.sp), color = c.ink)
        }
        // Top-left (RTL end): what arrived.
        HeaderActions(unread, onBell)
    }
}

@Composable
private fun Hero(week: List<WorkEntry>, today: List<WorkEntry>, all: List<WorkEntry>, hoursByCat: List<Pair<String, Double>>, categories: List<WorkCategory>, onPending: () -> Unit,
                 plannedThisWeek: Int = 0) {
    val c = Kis.colors
    val totals = WorkPay.totals(week).toList().sortedByDescending { it.second }
    val pending = WorkPay.pending(all).count { !WorkPay.isJob(it) }
    Column(Modifier.padding(top = 22.dp, bottom = 4.dp).testTag("home_hero")) {
        Text("השבוע", style = KisType.label.copy(fontWeight = FontWeight.Medium), color = c.muted)
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // Unknown pay is never shown as 0: a week with only rate-less shifts says so instead.
            if (totals.isEmpty() && week.isNotEmpty()) Text("עוד לא ידוע", style = KisType.hero.copy(fontSize = 40.sp), color = c.pending, modifier = Modifier.testTag("home_total"))
            else if (totals.isEmpty()) MoneyText(0.0, "₪", KisType.hero, modifier = Modifier.testTag("home_total"), color = c.ink)
            totals.forEachIndexed { i, (cur, v) ->
                MoneyText(v, cur, if (i == 0) KisType.hero else KisType.hero.copy(fontSize = 30.sp), color = if (i == 0) c.ink else c.muted,
                    modifier = Modifier.testTag("home_total_$cur"))
            }
        }
        val total = hoursByCat.sumOf { it.second }
        if (total > 0) Row(Modifier.padding(top = 16.dp, bottom = 10.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            hoursByCat.forEach { (cat, h) ->
                Box(Modifier.weight((h / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(workplaceOf(cat, categories).accent(c.dark)))
            }
        }
        Row(Modifier.padding(top = if (total > 0) 0.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (week.size == 1) "משמרת אחת השבוע" else "${week.size} משמרות השבוע", style = KisType.caption, color = c.muted, modifier = Modifier.testTag("home_week_count"))
            // Planned shifts are shown, never added to what was earned.
            if (plannedThisWeek > 0) Text("עוד $plannedThisWeek מתוכננות", style = KisType.caption, color = c.muted, modifier = Modifier.testTag("home_week_planned"))
            hoursByCat.take(3).forEach { (cat, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(workplaceOf(cat, categories).accent(c.dark)))
                    Text(cat, style = KisType.caption, color = c.muted, maxLines = 1)
                }
            }
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("היום", today, null, Modifier.weight(1f).fillMaxHeight(), "home_today")
            StatTile("מכל הזמנים", all, if (all.size == 1) "משמרת אחת" else "${all.size} משמרות", Modifier.weight(1f).fillMaxHeight(), "home_all")
        }
        if (pending > 0) PendingFlag(pending, onPending, Modifier.padding(top = 12.dp))
    }
}

/** A small total: one line per currency (₪ and $ are never added together). */
@Composable
private fun StatTile(label: String, rows: List<WorkEntry>, caption: String?, modifier: Modifier, tag: String) {
    val c = Kis.colors
    val totals = WorkPay.totals(rows).toList().sortedByDescending { it.second }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}.testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = KisType.caption, color = c.muted)
        if (totals.isEmpty() && rows.isNotEmpty()) Text("עוד לא ידוע", style = KisType.title, color = c.pending, modifier = Modifier.testTag("${tag}_pending"))
        else if (totals.isEmpty()) MoneyText(0.0, "₪", KisType.title, modifier = Modifier.testTag("${tag}_₪"), color = c.ink)
        totals.forEach { (cur, v) -> MoneyText(v, cur, KisType.title, modifier = Modifier.testTag("${tag}_$cur"), color = c.ink) }
        if (caption != null) Text(caption, style = KisType.caption, color = c.muted, modifier = Modifier.testTag("${tag}_count"))
    }
}

@Composable
private fun NowRow(live: LiveShift?, onStart: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onCurrency: (String) -> Unit) {
    val c = Kis.colors
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(live?.start) { confirmCancel = false }
    var confirmShort by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(live?.start) { confirmShort = false }
    Column(Modifier.padding(top = 22.dp).coachTarget(CoachTarget.SHIFT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live == null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.5.dp, c.line, RoundedCornerShape(18.dp))
                .kisClick(onClick = onStart).testTag("live_shift_fab"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Workplaces[1].accent(c.dark)))
                Spacer(Modifier.width(10.dp))
                Text("אני מתחיל עכשיו", style = KisType.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            }
        } else if (confirmShort) {
            // A timer stopped within its first minute is almost always a mis-tap: ask before saving a 0-hour shift.
            Text("המשמרת נמשכה פחות מדקה. לשמור בכל זאת?", style = KisType.body, color = c.ink, modifier = Modifier.testTag("short_live_question"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("ביטול בלי שמירה", { confirmShort = false; onCancel() }, Modifier.weight(1f), KisButtonKind.GHOST, height = 56.dp, tag = "short_live_discard")
                KisButton("שמירה", { confirmShort = false; onStop() }, Modifier.weight(1f), height = 56.dp, tag = "short_live_save")
            }
        } else if (confirmCancel) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("לבטל בלי לשמור", { confirmCancel = false; onCancel() }, Modifier.weight(1f), KisButtonKind.DANGER, height = 56.dp, haptic = KisSettings.Haptic.WARN, tag = "confirm_cancel_active_shift")
                KisButton("המשך במשמרת", { confirmCancel = false }, Modifier.weight(1f), KisButtonKind.GHOST, height = 56.dp)
            }
        } else {
            var seconds by remember(live.start) { mutableLongStateOf(0L) }
            LaunchedEffect(live.start) { while (true) { seconds = ((System.currentTimeMillis() - live.start) / 1000).coerceAtLeast(0); delay(1000) } }
            val pulse = rememberInfiniteTransition(label = "live")
            val a by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dot")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(c.ink).kisClick(haptic = KisSettings.Haptic.SUCCESS) {
                    if (System.currentTimeMillis() - live.start < 60_000L) confirmShort = true else onStop()
                }
                    .testTag("live_shift_stop").padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Workplaces[1].accent(true).copy(alpha = a)))
                    Spacer(Modifier.width(8.dp))
                    Text(live.category, style = KisType.label, color = c.onInk, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60), style = KisType.body.copy(fontFeatureSettings = "tnum",
                        textDirection = TextDirection.Ltr, fontWeight = FontWeight.SemiBold), color = c.onInk, modifier = Modifier.testTag("active_shift_timer").padding(horizontal = 10.dp))
                    Text("סיום ושמירה", style = KisType.label, color = c.onInk)
                }
                KisButton("ביטול", { confirmCancel = true }, Modifier, KisButtonKind.OUTLINE, height = 56.dp, tag = "cancel_active_shift")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("נצבר עד עכשיו:", style = KisType.caption, color = c.muted)
                MoneyText(seconds * live.rate / 3600.0, live.currency, KisType.label.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                CurrencyToggle(live.currency, onCurrency, "active_shift_currency")
            }
        }
    }
}

@Composable
private fun Wallet(month: List<WorkEntry>, categories: List<WorkCategory>, onOpen: (WorkEntry) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val names = categories.map { it.name }.filter { n -> month.any { it.category == n } } + month.map { it.category }.filter { n -> categories.none { it.name == n } }.distinct()
    Column(Modifier.testTag("wallet")) {
        names.forEachIndexed { i, name ->
            val list = month.filter { it.category == name }.sortedByDescending { it.date }
            Pass(name, list, categories, expanded = open == name, last = i == names.lastIndex, onToggle = { open = if (open == name) null else name }, onOpen = onOpen)
        }
    }
}

@Composable
private fun Pass(name: String, list: List<WorkEntry>, categories: List<WorkCategory>, expanded: Boolean, last: Boolean, onToggle: () -> Unit, onOpen: (WorkEntry) -> Unit) {
    val w = workplaceOf(name, categories)
    val totals = WorkPay.totals(list)
    val pending = WorkPay.pending(list).size
    // Collapsed passes tuck 14dp under the next one, like cards in a wallet.
    Column(Modifier.fillMaxWidth().then(if (expanded || last) Modifier.padding(bottom = 10.dp) else Modifier.overlapNext(14.dp)).clip(RoundedCornerShape(22.dp)).background(w.brush)
        .kisClick(onClick = onToggle).semantics { stateDescription = if (expanded) "פתוח" else "סגור" }.testTag("pass_$name")
        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = if (expanded || last) 18.dp else 30.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = KisType.label.copy(fontSize = 16.sp), color = w.on, maxLines = 1)
                Text("${KisFormat.hours(list.sumOf { it.hours })} שעות · ${list.size} משמרות${if (pending > 0) " · $pending בלי תעריף" else ""}",
                    style = KisType.caption, color = w.on.copy(alpha = 0.85f))
            }
            if (totals.isEmpty()) Text(if (pending > 0) "?" else "—", style = KisType.amount, color = w.on)
            else Column(horizontalAlignment = Alignment.End) { totals.forEach { (cur, v) -> MoneyText(v, cur, KisType.amount, color = w.on) } }
        }
        if (expanded) {
            Box(Modifier.padding(top = 12.dp, bottom = 6.dp).fillMaxWidth().height(1.dp).background(w.on.copy(alpha = 0.25f)))
            list.take(4).forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).kisClick { onOpen(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${dayLabel(e.date)} · ${timeLabel(e)}", style = KisType.caption.copy(fontSize = 14.sp), color = w.on, modifier = Modifier.weight(1f), maxLines = 1)
                    val pay = WorkPay.pay(e)
                    if (pay != null) MoneyText(pay, e.currency, KisType.label, color = w.on) else Text(WorkPay.missingLabel(e), style = KisType.caption, color = w.on)
                }
            }
        }
    }
}

private fun Modifier.overlapNext(by: Dp) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - by.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
}

@Composable
private fun EmptyHome() {
    val c = Kis.colors
    KisCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("ברוך הבא", style = KisType.title, color = c.ink)
        Text("לחיצה על + מוסיפה משמרת. השעה מוצעת לבד, והסכום מחושב מיד. אפשר גם להתחיל טיימר כשמגיעים לעבודה.", style = KisType.body, color = c.muted)
    }
}

/** "Where are you working?" before the live timer starts: one tap with the default workplace. */
@Composable
fun StartLiveSheet(categories: List<WorkCategory>, defaultCategory: String, currencyOf: (String) -> String, onDismiss: () -> Unit, onStart: (String, Double, String) -> Unit) {
    val c = Kis.colors
    var cat by rememberSaveable { mutableStateOf(categories.firstOrNull { it.name == defaultCategory }?.name ?: categories.firstOrNull()?.name ?: "") }
    val category = categories.firstOrNull { it.name == cat }
    var rate by rememberSaveable(cat) { mutableStateOf(category?.defaultRate?.let(KisFormat::hours) ?: "") }
    var currency by rememberSaveable(cat) { mutableStateOf(currencyOf(cat)) }
    KisSheet(onDismiss = onDismiss, tag = "start_live_sheet") {
        Text("איפה עובדים עכשיו?", style = KisType.title, color = c.ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { k -> KisChip(k.name, selected = k.name == cat, onClick = { cat = k.name }, tag = "live_category_${k.name}") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' } }, "תעריף לשעה", Modifier.weight(1f).testTag("live_rate"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            CurrencyToggle(currency, { currency = it }, "quick_shift_currency")
        }
        KisButton("התחלה", { onStart(cat, rate.toDoubleOrNull() ?: category?.defaultRate ?: 40.0, currency) }, Modifier.fillMaxWidth(),
            enabled = cat.isNotBlank(), haptic = KisSettings.Haptic.SUCCESS, tag = "live_start_confirm")
    }
}
