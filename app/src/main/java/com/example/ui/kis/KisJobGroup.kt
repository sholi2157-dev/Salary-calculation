package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkerDirectory

/**
 * The team on a job (קבלנות). Each person gets a fixed sum or hours at their own rate; the job's price
 * minus the team's pay is what is left for the user, and only that is stored as the user's income.
 * Numbers use the device keyboard.
 */
@Composable
internal fun JobTeamBlock(d: EntryDraft, directory: List<WorkerDirectory>, onForget: (String) -> Unit, onDraft: (EntryDraft) -> Unit) {
    val c = Kis.colors
    var newName by rememberSaveable { mutableStateOf("") }
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }
    fun set(i: Int, w: WorkerDraft) = onDraft(d.copy(workers = d.workers.toMutableList().also { it[i] = w }))
    fun add(name: String) = onDraft(d.copy(workers = d.workers + WorkerDraft(name)))
    forgetting?.let { name ->
        KisSheet(onDismiss = { forgetting = null }, tag = "forget_worker_sheet") {
            Text("להסיר את $name מהרשימה?", style = KisType.title, color = c.ink)
            Text("השם לא יוצע יותר. עבודות קודמות שבהן הוא עבד לא משתנות.", style = KisType.body, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("ביטול", { forgetting = null }, Modifier.weight(1f), KisButtonKind.GHOST)
                KisButton("הסרה", { onForget(name); forgetting = null }, Modifier.weight(1f), KisButtonKind.DANGER, tag = "forget_worker_confirm")
            }
        }
    }
    // A solo job stays as simple as before: the team opens only when asked for (or when it already has people).
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open && d.workers.isEmpty()) {
        KisChip("+ עבדו איתי עוד אנשים", dashed = true, leading = Icons.Rounded.PersonAdd, onClick = { open = true }, tag = "job_team_open")
        return
    }
    KisCard(padding = PaddingValues(14.dp), radius = 18.dp, modifier = Modifier.testTag("job_team_block")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מי עבד איתך", style = KisType.label.copy(fontSize = 16.sp), color = c.ink, modifier = Modifier.weight(1f))
                InfoDot("עבודה עם צוות", "לכל אחד בוחרים סכום קבוע או שעות לפי תעריף משלו. מה שנשאר מסכום העבודה אחרי הצוות הוא ההכנסה שלך, ורק הוא נספר בסיכומים.")
            }
            d.workers.forEachIndexed { i, w ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(10.dp).testTag("job_worker_$i"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(w.name, style = KisType.label, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        KisChip("סכום", selected = !w.byHours, onClick = { set(i, w.copy(byHours = false)) }, tag = "job_worker_fixed_$i", minHeight = 36.dp)
                        KisChip("לפי שעה", selected = w.byHours, onClick = { set(i, w.copy(byHours = true)) }, tag = "job_worker_hourly_$i", minHeight = 36.dp)
                        Icon(Icons.Rounded.Close, "הסרת ${w.name}", tint = c.muted, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .kisClick { onDraft(d.copy(workers = d.workers.filterIndexed { j, _ -> j != i })) }.padding(7.dp))
                    }
                    if (w.byHours) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(DurationInput.display(w.hours), "שעות:דקות", Modifier.weight(1f).testTag("job_worker_hours_$i"), duration = true) { set(i, w.copy(hours = DurationInput.edit(it))) }
                        NumberField(w.rate, "לשעה (${d.currency})", Modifier.weight(1f).testTag("job_worker_rate_$i")) { set(i, w.copy(rate = it)) }
                    } else NumberField(w.amount, "כמה הוא מקבל (${d.currency})", Modifier.fillMaxWidth().testTag("job_worker_amount_$i")) { set(i, w.copy(amount = it)) }
                    if (w.byHours) d.jobWorkerPay(w)?.let { Text("מקבל ${KisFormat.money(it, d.currency)}", style = KisType.caption, color = c.muted) }
                }
            }
            val suggestions = directory.map { it.name.trim() }.filter { n -> n.isNotEmpty() && d.workers.none { it.name == n } }.distinct()
            if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { n -> KisChip("+ $n", onClick = { add(n) }, tag = "job_add_worker_$n", onLongClick = { forgetting = n }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisTextField(newName, { newName = it }, "עובד חדש", Modifier.weight(1f).testTag("job_worker_name_input"))
                Icon(Icons.Rounded.PersonAdd, "הוספת עובד", tint = c.onInk, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.ink)
                    .kisClick(enabled = newName.isNotBlank()) { add(newName.trim()); newName = "" }.padding(12.dp).testTag("job_worker_add"))
            }
            if (d.workers.isNotEmpty()) JobTeamTotals(d, onDraft)
        }
    }
}

@Composable
private fun JobTeamTotals(d: EntryDraft, onDraft: (EntryDraft) -> Unit) {
    val c = Kis.colors
    val team = d.teamPayValue()
    val price = d.amount.trim().replace(',', '.').toDoubleOrNull()
    val share = d.myShare()
    Column(Modifier.fillMaxWidth().testTag("job_team_totals"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KisDivider()
        @Composable fun line(k: String, v: Double?, tag: String) = Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k, style = KisType.caption, color = c.muted, modifier = Modifier.weight(1f))
            if (v != null) MoneyText(v, d.currency, KisType.label, modifier = Modifier.testTag(tag)) else Text("—", style = KisType.label, color = c.muted)
        }
        if (!d.amountUnknown) line("סכום העבודה", price, "job_price")
        line("לצוות", team, "job_team_pay")
        if (!d.amountUnknown) line("נשאר לך", share, "job_my_share")
        d.check().errors.firstOrNull { it == "חסר כמה מקבל כל עובד" || it == "הצוות מקבל יותר מסכום העבודה" }?.let {
            Text(it, style = KisType.caption, color = c.pending, modifier = Modifier.testTag("job_team_error"))
        }
        // One tap splits the price evenly between the user and everyone on a fixed sum.
        if (price != null && price > 0 && d.workers.none { it.byHours }) Text("חלוקה שווה (${d.workers.size + 1} אנשים)", style = KisType.label, color = c.ink,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).kisClick {
                val each = Math.floor(price / (d.workers.size + 1) * 100.0) / 100.0
                onDraft(d.copy(workers = d.workers.map { it.copy(amount = KisFormat.hours(each)) }))
            }.padding(vertical = 6.dp).testTag("job_split_even"))
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, duration: Boolean = false, onValue: (String) -> Unit) {
    KisTextField(value, { v -> val f = v.filter { it.isDigit() || it == '.' || duration && (it == ':' || it == ',') }; if (f.count { it == '.' || it == ':' || it == ',' } <= 1) onValue(f) }, label, modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
