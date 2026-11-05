package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkEntryEdits
import com.example.data.WorkPay
import com.example.data.WorkShareText
import com.example.ui.CoachTarget
import com.example.ui.WorkViewModel
import com.example.ui.coachTarget

/**
 * Tapping a shift shows its facts (read-only) with one compact row of actions.
 * It is deliberately different from the edit page, which is ink and has a "before" banner.
 */
@Composable
fun ShiftDetailSheet(entry: WorkEntry, categories: List<WorkCategory>, viewModel: WorkViewModel, onDismiss: () -> Unit,
                     onEdit: (WorkEntry) -> Unit, onDeleted: (List<WorkEntry>) -> Unit, onMessage: (String) -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    KisSheet(onDismiss = onDismiss, tag = "shift_detail") {
        ShiftDetailContent(entry, categories,
            onPaid = { viewModel.setPaid(listOf(entry.id), it) },
            onEdit = { onEdit(entry) },
            onShare = { KisShare.shareOrCopy(context, WorkShareText.entry(entry, future = KisHistoryLogic.upcoming(entry)), "שיתוף משמרת", onMessage) },
            // Close the sheet so the confirmation is visible above it.
            onCopy = { KisShare.copy(context, WorkShareText.entry(entry, technical = true, future = KisHistoryLogic.upcoming(entry))); onDismiss(); onMessage("הועתק ✓ · מוכן להדבקה") },
            onDelete = { confirmDelete = true },
            onCompleteRate = { if (WorkPay.isJob(entry)) onEdit(entry) else completing = true },
            group = { GroupDetail(entry, viewModel, onMessage) })
    }
    if (confirmDelete) KisSheet(onDismiss = { confirmDelete = false }) {
        Text("למחוק את המשמרת?", style = KisType.title, color = c.ink)
        Text("אפשר לבטל מיד אחרי המחיקה.", style = KisType.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("ביטול", { confirmDelete = false }, Modifier.weight(1f), KisButtonKind.GHOST)
            KisButton("מחיקה", {
                confirmDelete = false
                settings.haptic(KisSettings.Haptic.WARN)
                viewModel.deleteEntries(listOf(entry)) { onDeleted(it) }
                onDismiss()
            }, Modifier.weight(1f), KisButtonKind.DANGER, haptic = null, tag = "confirm_delete")
        }
    }
    if (completing) RateCompletionSheet(listOf(entry), viewModel, onDismiss = { completing = false }) { n ->
        completing = false
        if (n > 0) { settings.haptic(KisSettings.Haptic.SUCCESS); onMessage("התעריף הושלם ✓") }
    }
}

/** The facts and actions of one shift; used by the detail sheet and by the guided tour. */
@Composable
fun ShiftDetailContent(entry: WorkEntry, categories: List<WorkCategory>, onPaid: (Boolean) -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
                       onDelete: () -> Unit, onCompleteRate: () -> Unit, group: @Composable () -> Unit) {
    val c = Kis.colors
    val workplace = workplaceOf(entry.category, categories)
    val pay = WorkPay.pay(entry)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // M-34 (TalkBack): the header is read as one card (place, day, amount); the place's first letter is decoration.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(workplace.brush).semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(workplace.on.copy(alpha = 0.18f)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text(entry.category.take(1), style = KisType.title, color = workplace.on)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.category, style = KisType.label.copy(fontSize = 16.sp), color = workplace.on, maxLines = 1)
                Text(dayLabelLong(entry.date) + if (entry.autoConfirmed) " · נרשמה לבד בסוף הזמן המתוכנן" else "", style = KisType.caption, color = workplace.on.copy(alpha = 0.85f))
            }
            // A group shift: the amount is the user's own part, the one the lists and totals show (design decision B).
            Column(horizontalAlignment = Alignment.End) {
                if (entry.isGroupShift) Text("החלק שלי", style = KisType.caption, color = workplace.on.copy(alpha = 0.85f), modifier = Modifier.testTag("detail_amount_label"))
                if (pay != null) MoneyText(pay, entry.currency, KisType.amount.copy(fontSize = 30.sp), color = workplace.on, modifier = Modifier.testTag("detail_amount"))
                else Text(WorkPay.missingLabel(entry), style = KisType.label, color = workplace.on, modifier = Modifier.testTag("detail_amount"))
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 14.dp)) {
            @Composable fun fact(k: String, v: @Composable () -> Unit) {
                Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = KisType.body.copy(fontSize = 14.sp), color = c.muted, modifier = Modifier.weight(1f)); v()
                }
                KisDivider()
            }
            @Composable fun value(t: String, tag: String? = null) = Text(t, style = KisType.body.copy(fontWeight = FontWeight.Medium, textDirection = TextDirection.Rtl), color = c.ink,
                modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
            @Composable fun link(t: String, tag: String) = Text(t, style = KisType.label, color = c.pending,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).kisClick(onClick = onCompleteRate).testTag(tag).padding(6.dp))
            if (WorkPay.isJob(entry)) {
                fact("סוג") { value("עבודה קבלנית") }
                fact("מה עשית") { value(entry.notes.ifBlank { "—" }) }
                fact("זמן") { value(if (entry.hours > 0) "${KisFormat.duration(entry.hours)} שעות" else "לא צוין") }
                if (pay == null) fact("סכום") { link("להשלמה ←", "detail_fill_amount") }
            } else {
                val overnight = entry.isTimeRange && entry.startTime != null && entry.endTime != null && entry.endTime <= entry.startTime
                fact("שעות") { value(timeLabel(entry) + (if (overnight) " · למחרת" else "") + if (entry.isTimeRange) " · ${KisFormat.duration(entry.hours)} ש׳" else "", "detail_hours") }
                fact("תעריף") {
                    if (!entry.payKnown) link("להשלמה ←", "detail_fill_rate")
                    else value("${KisFormat.money(entry.hourlyRate, entry.currency)} לשעה")
                }
                if (entry.bonus > 0) fact("בונוס") { value(KisFormat.money(entry.bonus, entry.currency)) }
                val breakMin = Math.round(WorkEntryEdits.breakMinutes(entry)).toInt()
                if (breakMin > 0) fact("הפסקה") { value("$breakMin דק׳") }
            }
            if (entry.notes.isNotBlank() && !WorkPay.isJob(entry)) fact("הערה") { value(entry.notes) }
            // Money passed through is owed by the employer but is not the user's: shown apart, with what the employer owes in all.
            if (entry.passThrough > 0 && !entry.isGroupShift) {
                fact("החזר הוצאות") { value(KisFormat.money(entry.passThrough, entry.currency) + " · לא שלך", "detail_pass_through") }
                if (!entry.isGroupShift) WorkPay.employerDue(entry)?.let { due -> fact("לקבל מהמעסיק") { value(KisFormat.money(due, entry.currency), "detail_employer_due") } }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("תשלום", style = KisType.body.copy(fontSize = 14.sp), color = c.muted, modifier = Modifier.weight(1f))
                Text(if (entry.isPaid) "שולם" else "עוד לא שולם", style = KisType.body.copy(fontWeight = FontWeight.Medium), color = if (entry.isPaid) c.paid else c.ink,
                    modifier = Modifier.padding(end = 10.dp).testTag("payment_status_${entry.id}"))
                KisSwitch(entry.isPaid, onPaid, Modifier.testTag("toggle_payment_${entry.id}"), label = "סימון תשלום")
            }
        }
        if (entry.isGroupShift) group()
        Row(Modifier.fillMaxWidth().coachTarget(CoachTarget.SHARE).testTag("entry_actions_${entry.id}"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisAction("עריכה", Icons.Rounded.Edit, onEdit, Modifier.weight(1f), primary = true, tag = "edit_entry_btn_${entry.id}")
            // A group shift has every report (its own part too) under the team's "שיתוף" (design decision B).
            if (!entry.isGroupShift) {
                KisAction("שיתוף", Icons.Rounded.Share, onShare, Modifier.weight(1f), tag = "share_entry_btn_${entry.id}")
                KisAction("העתקה", Icons.Rounded.ContentCopy, onCopy, Modifier.weight(1f), tag = "copy_entry_btn_${entry.id}")
            }
            KisAction("מחיקה", Icons.Rounded.DeleteOutline, onDelete, Modifier.weight(1f), danger = true, tag = "delete_entry_btn_${entry.id}")
        }
    }
}

@Composable
private fun GroupDetail(entry: WorkEntry, viewModel: WorkViewModel, onMessage: (String) -> Unit) {
    val c = Kis.colors
    val myName = Kis.settings.name
    val context = LocalContext.current
    val workers = WorkPay.workers(entry)
    val summary = WorkPay.group(entry)
    val job = WorkPay.isJob(entry)
    val future = KisHistoryLogic.upcoming(entry)
    // The team's pay, summed in whole agorot (null while any worker's pay is unknown).
    val team: Double? = if (job) WorkPay.teamPay(entry) else workers.fold(0L as Long?) { acc, w -> acc?.let { a -> WorkPay.workerPay(w, entry)?.let { a + Math.round(it * 100) } } }?.let { it / 100.0 }
    var reports by rememberSaveable { mutableStateOf(false) }
    var workerMenu by rememberSaveable { mutableStateOf<Int?>(null) }
    // Design decision B: one block with every total (all from WorkPay, reconciled in whole agorot), one "שיתוף" for the
    // reports, and one row per worker with its paid state and ⋯ for share, copy and paid.
    Column(Modifier.fillMaxWidth().testTag("group_detail"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (summary != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable fun line(k: String, v: Double, tag: String, strong: Boolean = false) = Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = if (strong) KisType.label else KisType.body.copy(fontSize = 15.sp), color = c.ink, modifier = Modifier.weight(1f))
                MoneyText(v, entry.currency, if (strong) KisType.label.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold) else KisType.label, modifier = Modifier.testTag(tag))
            }
            if (!job) { line("עמלה מהצוות", summary.profit, "group_profit"); line("ההכנסה שלי כולל עמלה", summary.myNet, "group_my_net") }
            if (team != null) line("לצוות (${workers.size})", team, "group_team_pay")
            if (entry.passThrough > 0) line("החזר הוצאות · לא שלך", entry.passThrough, "group_pass_through")
            KisDivider()
            line("לקבל מהמעסיק", summary.employerTotal, "group_employer_due", strong = true)
        } else Text(if (job) "הסכומים יופיעו אחרי שיושלם הסכום שלך." else "הסכומים יופיעו אחרי שיושלם התעריף.", style = KisType.caption, color = c.muted)
        KisButton("שיתוף", { reports = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, icon = Icons.Rounded.Share, height = 48.dp, tag = "group_summary_share")
        Text("${if (job) "הצוות" else "הקבוצה"} · ${workers.size}", style = KisType.label.copy(fontSize = 16.sp), color = c.ink, modifier = Modifier.padding(top = 4.dp))
        workers.forEachIndexed { i, w ->
            val wPay = WorkPay.workerPay(w, entry)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("group_worker_$i"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = KisType.label, color = c.ink, maxLines = 1)
                    val fixed = job && w.amount != null
                    val time = if (fixed) "סכום קבוע" else if (job) "${KisFormat.duration(w.hours)} שעות × ${WorkPay.workerRate(w, entry)?.let { KisFormat.money(it, entry.currency) } ?: "?"}" else "${KisFormat.duration(w.hours)} שעות"
                    Text(listOfNotNull(time, if (w.bonus > 0) "בונוס ${KisFormat.money(w.bonus, entry.currency)}" else null).joinToString(" · "),
                        style = KisType.caption.copy(textDirection = TextDirection.Rtl), color = c.muted)
                }
                Box(Modifier.heightIn(min = KisMinTouch).clip(RoundedCornerShape(99.dp)).kisClick { viewModel.toggleWorkerPaid(entry.id, i) }
                    .semantics { contentDescription = "${w.name}: ${if (w.isPaid) "שולם" else "לא שולם"}"; selected = w.isPaid }.testTag("group_worker_paid_$i"), contentAlignment = Alignment.Center) {
                    Text(if (w.isPaid) "שולם ✓" else "לא שולם", style = KisType.micro, color = if (w.isPaid) c.paid else c.muted,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (w.isPaid) c.paid.copy(alpha = 0.18f) else c.sunk).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                if (wPay != null) MoneyText(wPay, entry.currency, KisType.label, modifier = Modifier.testTag("group_worker_pay_$i"))
                else Text("שכר לא ידוע", style = KisType.caption, color = c.pending, modifier = Modifier.testTag("group_worker_pay_$i"))
                Icon(Icons.Rounded.MoreHoriz, "פעולות ל${w.name}", tint = c.ink, modifier = Modifier.size(KisMinTouch).clip(CircleShape).kisClick { workerMenu = i }.testTag("group_worker_more_$i").padding(10.dp))
            }
            if (i < workers.lastIndex) KisDivider()
        }
    }
    @Composable fun ActionsSheet(title: String, tag: String, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) = KisSheet(onDismiss = onDismiss, tag = tag) {
        Text(title, style = KisType.title, color = c.ink)
        items.forEachIndexed { i, (label, act) ->
            Text(label, style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                .kisClick { onDismiss(); act() }.testTag("${tag}_$i").wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 12.dp))
        }
    }
    if (reports) ActionsSheet("שיתוף", "group_reports", { reports = false }, listOf(
        "דוח למעסיק" to { KisShare.shareOrCopy(context, WorkShareText.employer(entry, myName, future = future), "סיכום לקבלן", onMessage) },
        "העתקת דוח הצוות" to { KisShare.copy(context, WorkShareText.employer(entry, myName, technical = true, future = future)); onMessage("הדוח הועתק ✓") },
        "החלק שלי" to { KisShare.shareOrCopy(context, WorkShareText.entry(entry, future = future), "שיתוף משמרת", onMessage) },
        "העתקת החלק שלי" to { KisShare.copy(context, WorkShareText.entry(entry, technical = true, future = future)); onMessage("הועתק ✓") }))
    workerMenu?.let { i -> workers.getOrNull(i)?.let { w -> ActionsSheet(w.name, "group_worker_menu", { workerMenu = null }, listOf(
        "שיתוף ל${w.name}" to { KisShare.shareOrCopy(context, WorkShareText.worker(entry, w, future = future), "שליחה ל${w.name}", onMessage) },
        "העתקה" to { KisShare.copy(context, WorkShareText.worker(entry, w, technical = true, future = future)); onMessage("הועתק ✓") },
        (if (w.isPaid) "סימון כלא שולם" else "סימון כשולם") to { viewModel.toggleWorkerPaid(entry.id, i) })) } }
}

/**
 * "כמה שילמו לשעה?" for one or many shifts. Only shifts whose rate is missing change;
 * the preview shows exactly what will be written before anything is saved.
 */
@Composable
fun RateCompletionSheet(selected: List<WorkEntry>, viewModel: WorkViewModel, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val c = Kis.colors
    val targets = selected.filter { !it.payKnown && !WorkPay.isJob(it) }
    val skipped = selected.size - targets.size
    var text by rememberSaveable { mutableStateOf("") }
    val rate = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
    val focus = remember { FocusRequester() }
    KisSheet(onDismiss = onDismiss, tag = "rate_sheet") {
        Text("כמה שילמו לשעה?", style = KisType.title, color = c.ink)
        Text("רק משמרות בלי תעריף יתעדכנו. סכומים שכבר נשמרו לא משתנים.", style = KisType.body, color = c.muted)
        KisTextField(text, { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' } }, "תעריף לשעה", Modifier.fillMaxWidth().focusRequester(focus).testTag("bulk_rate_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            targets.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.category} · ${dayLabel(e.date)}", style = KisType.label, color = c.ink, maxLines = 1)
                        Text(timeLabel(e), style = KisType.caption, color = c.muted)
                    }
                    if (rate != null) MoneyText(WorkPay.money(e.hours, rate, e.bonus), e.currency, KisType.label.copy(fontSize = 16.sp))
                    else Text("?", style = KisType.label, color = c.muted)
                }
            }
        }
        if (skipped > 0) Text(if (skipped == 1) "פריט אחד לא ישתנה: כבר יש לו סכום, או שהוא קבלנות" else "$skipped לא ישתנו: כבר יש להם סכום, או שהם קבלנות", style = KisType.caption, color = c.pending,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.pendingSoft).padding(10.dp))
        KisButton(if (targets.size > 1) "עדכון ${targets.size} משמרות" else "עדכון", {
            if (rate != null) viewModel.completeMissingRates(targets.map { it.id }, rate, onDone)
        }, Modifier.fillMaxWidth(), enabled = rate != null && targets.isNotEmpty(), haptic = null, tag = "bulk_rate_apply")
        if (targets.isEmpty()) Text("לכל המשמרות שנבחרו כבר יש סכום.", style = KisType.caption, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
