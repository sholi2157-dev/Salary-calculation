package com.example.ui.kis

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkPay

/** One shift in a list: workplace initial, day, what/when, and the amount (or what is missing). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShiftCard(entry: WorkEntry, categories: List<WorkCategory>, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
              selected: Boolean = false, modifier: Modifier = Modifier, tagPrefix: String = "work_entry_card_") {
    val c = Kis.colors
    val settings = Kis.settings
    val workplace = workplaceOf(entry.category, categories)
    val pay = WorkPay.pay(entry)
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(if (selected) c.sunk else c.surface)
        .border(1.5.dp, if (selected) c.ink else Color.Transparent, shape)
        .combinedClickable(onClick = { settings.haptic(KisSettings.Haptic.TAP); onClick() },
            onLongClick = onLongClick?.let { lc -> { settings.haptic(KisSettings.Haptic.SELECT); lc() } })
        .semantics { this.selected = selected }
        .testTag("$tagPrefix${entry.id}")
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // A shift on a later day is planned: dashed avatar, "מתוכנן", muted amount (it is not earned yet).
        val planned = KisHistoryLogic.upcoming(entry)
        Avatar(entry.category, workplace, planned = planned)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dayLabel(entry.date), style = KisType.label.copy(fontSize = 15.sp), color = c.ink, maxLines = 1)
                if (planned) Tag("מתוכנן", modifier = Modifier.testTag("planned_tag_${entry.id}"))
                if (entry.autoConfirmed) Tag("אוטו׳")
                if (!entry.isPaid && !planned) Tag("לא שולם")
            }
            Text(subtitle(entry), style = KisType.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pay != null) MoneyText(pay, entry.currency, KisType.amount.copy(fontSize = 19.sp), modifier = Modifier.testTag("entry_amount_${entry.id}"),
            color = if (planned) c.muted else androidx.compose.ui.graphics.Color.Unspecified)
        else Tag(WorkPay.missingLabel(entry), warn = true, modifier = Modifier.testTag("entry_amount_${entry.id}"))
    }
}

/** "17:00–22:30 · למחרת · 5.5 ש׳ · מסעדה · בונוס ₪50 · 3 אנשים" */
fun subtitle(e: WorkEntry): String {
    val parts = mutableListOf<String>()
    if (WorkPay.isJob(e)) {
        parts += "קבלנות: ${e.notes.ifBlank { e.category }}"
        if (e.hours > 0) parts += "${KisFormat.hours(e.hours)} ש׳"
    } else {
        parts += timeLabel(e)
        if (e.isTimeRange && e.startTime != null && e.endTime != null) {
            if (e.endTime <= e.startTime) parts += "למחרת"
            parts += "${KisFormat.hours(e.hours)} ש׳"
        }
        parts += e.category
    }
    if (e.bonus > 0) parts += "בונוס ${KisFormat.money(e.bonus, e.currency)}"
    if (e.passThrough > 0) parts += "החזר ${KisFormat.money(e.passThrough, e.currency)}"
    if (e.isGroupShift) WorkPay.workers(e).size.takeIf { it > 0 }?.let { parts += "${it + 1} אנשים" }
    return parts.joinToString(" · ")
}

/** "N משמרות מחכות לתעריף ←" */
@Composable
fun PendingFlag(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kis.colors
    Row(modifier.clip(RoundedCornerShape(99.dp)).background(c.pendingSoft).kisClick(onClick = onClick).testTag("pending_flag")
        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (count == 1) "משמרת אחת מחכה לתעריף ←" else "$count משמרות מחכות לתעריף ←", style = KisType.label, color = c.pending, fontWeight = FontWeight.SemiBold)
    }
}
