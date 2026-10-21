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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(workplace.brush).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(workplace.on.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(entry.category.take(1), style = KisType.title, color = workplace.on)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.category, style = KisType.label.copy(fontSize = 16.sp), color = workplace.on, maxLines = 1)
                Text(dayLabelLong(entry.date) + if (entry.autoConfirmed) " · נרשמה לבד בסוף הזמן המתוכנן" else "", style = KisType.caption, color = workplace.on.copy(alpha = 0.85f))
            }
            if (pay != null) MoneyText(pay, entry.currency, KisType.amount.copy(fontSize = 30.sp), color = workplace.on, modifier = Modifier.testTag("detail_amount"))
            else Text(WorkPay.missingLabel(entry), style = KisType.label, color = workplace.on, modifier = Modifier.testTag("detail_amount"))
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
                fact("זמן") { value(if (entry.hours > 0) "${KisFormat.hours(entry.hours)} שעות" else "לא צוין") }
                if (pay == null) fact("סכום") { link("להשלמה ←", "detail_fill_amount") }
            } else {
                val overnight = entry.isTimeRange && entry.startTime != null && entry.endTime != null && entry.endTime <= entry.startTime
                fact("שעות") { value(timeLabel(entry) + (if (overnight) " · למחרת" else "") + if (entry.isTimeRange) " · ${KisFormat.hours(entry.hours)} ש׳" else "", "detail_hours") }
                fact("תעריף") {
                    if (!entry.payKnown) link("להשלמה ←", "detail_fill_rate")
                    else value("${KisFormat.money(entry.hourlyRate, entry.currency)} לשעה")
                }
                if (entry.bonus > 0) fact("בונוס") { value(KisFormat.money(entry.bonus, entry.currency)) }
                val breakMin = Math.round(WorkEntryEdits.breakMinutes(entry)).toInt()
                if (breakMin > 0) fact("הפסקה") { value("$breakMin דק׳") }
            }
            if (entry.notes.isNotBlank() && !WorkPay.isJob(entry)) fact("הערה") { value(entry.notes) }
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
            KisAction(if (entry.isGroupShift) "החלק שלי" else "שיתוף", Icons.Rounded.Share, onShare, Modifier.weight(1f), tag = "share_entry_btn_${entry.id}")
            KisAction("העתקה", Icons.Rounded.ContentCopy, onCopy, Modifier.weight(1f), tag = "copy_entry_btn_${entry.id}")
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp).testTag("group_detail"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("הצוות", style = KisType.label.copy(fontSize = 16.sp), color = c.ink)
        if (summary != null) {
            Row { Text("סה״כ לתשלום (כולל כולם)", style = KisType.caption, color = c.muted, modifier = Modifier.weight(1f)); MoneyText(summary.employerTotal, entry.currency, KisType.label) }
            Row { Text("ההכנסה שלי (כולל עמלה)", style = KisType.caption, color = c.muted, modifier = Modifier.weight(1f)); MoneyText(summary.myNet, entry.currency, KisType.label) }
        } else Text("הסכומים יופיעו אחרי שיושלם התעריף.", style = KisType.caption, color = c.muted)
        workers.forEachIndexed { i, w ->
            val wPay = WorkPay.workerPay(w, entry)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                .kisClick { viewModel.toggleWorkerPaid(entry.id, i) }
                .padding(horizontal = 12.dp, vertical = 8.dp).testTag("group_worker_$i"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (w.isPaid) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (w.isPaid) "שולם" else "לא שולם", tint = if (w.isPaid) c.paid else c.muted)
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = KisType.label, color = c.ink)
                    Text("${KisFormat.hours(w.hours)} שעות · ${wPay?.let { KisFormat.money(it, entry.currency) } ?: "תעריף חסר"}${if (w.bonus > 0) " · בונוס ${KisFormat.money(w.bonus, entry.currency)}" else ""}",
                        style = KisType.caption.copy(textDirection = TextDirection.Rtl), color = c.muted)
                }
                Icon(Icons.Rounded.ContentCopy, "העתק פרטי משמרת לעובד", tint = c.ink, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .kisClick { KisShare.copy(context, WorkShareText.worker(entry, w, technical = true)); onMessage("הועתק ✓") }.padding(11.dp))
                Icon(Icons.Rounded.Share, "שתף פרטי משמרת לעובד", tint = c.ink, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .kisClick { KisShare.shareOrCopy(context, WorkShareText.worker(entry, w), "שליחה ל${w.name}", onMessage) }.padding(11.dp))
            }
        }
        KisButton("העתקת דוח הצוות", { KisShare.copy(context, WorkShareText.employer(entry, myName, technical = true)); onMessage("הדוח הועתק ✓") }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "group_summary_copy")
        KisButton("דו״ח לקבלן (כל הקבוצה)", { KisShare.shareOrCopy(context, WorkShareText.employer(entry, myName), "סיכום לקבלן", onMessage) }, Modifier.fillMaxWidth(),
            KisButtonKind.GHOST, icon = Icons.Rounded.ReceiptLong, height = 48.dp, tag = "group_summary_share")
    }
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
                    if (rate != null) MoneyText(WorkPay.amount(e.hours, rate, e.bonus), e.currency, KisType.label.copy(fontSize = 16.sp))
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
