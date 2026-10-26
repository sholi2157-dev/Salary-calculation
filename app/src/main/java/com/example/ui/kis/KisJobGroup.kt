package com.example.ui.kis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkerDirectory

/**
 * The team on a job (קבלנות). The amount at the top is the user's own; each person on the team gets a sum of their own
 * (the same as the user's, or their own when the user gets more), and the employer owes all of it. Only the user's
 * amount is counted as income. No hours here: a job is paid by the job.
 * Typing a name and "הבא" adds the person and moves straight to their amount; "הבא" there goes back to a new name.
 */
@Composable
internal fun JobTeamBlock(d: EntryDraft, directory: List<WorkerDirectory>, onForget: (String) -> Unit, onDraft: (EntryDraft) -> Unit) {
    val c = Kis.colors
    val focus = LocalFocusManager.current
    var newName by rememberSaveable { mutableStateOf("") }
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }
    // The worker whose amount takes the focus once their row exists (-1: none).
    var focusWorker by remember { mutableIntStateOf(-1) }
    val nameFocus = remember { FocusRequester() }
    fun add(name: String) {
        onDraft(d.withJobWorker(name))
        if (!d.sameRate) focusWorker = d.workers.size
    }
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
    KisCard(padding = PaddingValues(14.dp), radius = 18.dp, modifier = Modifier.testTag("job_team_block")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מי עבד איתך", style = KisType.label.copy(fontSize = 16.sp), color = c.ink, modifier = Modifier.weight(1f))
                InfoDot("עבודה עם צוות", "הסכום למעלה הוא רק שלך. כל אחד מהצוות מקבל סכום משלו, והמעסיק משלם את כולם. בסיכומים נספר רק הסכום שלך.")
            }
            // With an unknown amount there is nothing to copy, so each person has their own amount.
            if (!d.amountUnknown) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KisChip("אותו סכום לכולם", selected = d.teamSameRate, onClick = { onDraft(d.copy(teamSameRate = true)) }, modifier = Modifier.weight(1f), tag = "job_rate_same", minHeight = 40.dp)
                KisChip("אני מקבל יותר", selected = !d.teamSameRate, onClick = {
                    if (d.teamSameRate) {
                        onDraft(d.copy(teamSameRate = false))
                        // The first amount is the one the rest follow: start there.
                        if (d.workers.isNotEmpty() && d.workers[0].amount.isBlank()) focusWorker = 0
                    }
                }, modifier = Modifier.weight(1f), tag = "job_rate_mine_more", minHeight = 40.dp)
            }
            d.workers.forEachIndexed { i, w ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag("job_worker_$i"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(w.name, style = KisType.label, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    if (d.sameRate) {
                        val each = d.jobWorkerPay(w)
                        if (each != null) MoneyText(each, d.currency, KisType.label, color = c.muted, modifier = Modifier.testTag("job_worker_same_$i"))
                        else Text("כמו שלך", style = KisType.caption, color = c.muted)
                    } else {
                        val fr = remember { FocusRequester() }
                        LaunchedEffect(focusWorker, d.workers.size) { if (focusWorker == i) { runCatching { fr.requestFocus() }; focusWorker = -1 } }
                        KisTextField(w.amount, { v -> val f = v.replace(',', '.').filter { it.isDigit() || it == '.' }; if (f.count { it == '.' } <= 1) onDraft(d.withJobWorkerAmount(i, f)) },
                            "מקבל (${d.currency})", Modifier.width(130.dp).focusRequester(fr).testTag("job_worker_amount_$i"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { runCatching { nameFocus.requestFocus() } }), selectAllOnFocus = true)
                    }
                    Icon(Icons.Rounded.Close, "הסרת ${w.name}", tint = c.muted, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .kisClick { onDraft(d.copy(workers = d.workers.filterIndexed { j, _ -> j != i })) }.padding(9.dp))
                }
            }
            val suggestions = directory.map { it.name.trim() }.filter { n -> n.isNotEmpty() && d.workers.none { it.name == n } }.distinct()
            if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { n -> KisChip("+ $n", onClick = { add(n) }, tag = "job_add_worker_$n", onLongClick = { forgetting = n }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisTextField(newName, { newName = it }, "עובד חדש", Modifier.weight(1f).focusRequester(nameFocus).testTag("job_worker_name_input"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    // "הבא" adds the name and goes to their amount (or, with one amount for all, stays for the next name); empty closes the keyboard.
                    keyboardActions = KeyboardActions(onNext = { if (newName.isNotBlank()) { add(newName.trim()); newName = "" } else focus.clearFocus() }))
                Icon(Icons.Rounded.PersonAdd, "הוספת עובד", tint = c.onInk, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.ink)
                    .kisClick(enabled = newName.isNotBlank()) { add(newName.trim()); newName = "" }.padding(12.dp).testTag("job_worker_add"))
            }
            // A name typed but not added yet would be lost on save: say how to add it.
            if (newName.isNotBlank()) Text("לחיצה על + מוסיפה את ${newName.trim()} לצוות", style = KisType.caption, color = c.pending, modifier = Modifier.testTag("job_worker_pending"))
            if (d.workers.isNotEmpty()) JobTeamTotals(d)
        }
    }
}

@Composable
private fun JobTeamTotals(d: EntryDraft) {
    val c = Kis.colors
    val team = d.teamPayValue()
    val mine = if (d.amountUnknown) null else d.amount.trim().replace(',', '.').toDoubleOrNull()
    Column(Modifier.fillMaxWidth().testTag("job_team_totals"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KisDivider()
        @Composable fun line(k: String, v: Double?, tag: String, strong: Boolean = false) = Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k, style = if (strong) KisType.label else KisType.caption, color = if (strong) c.ink else c.muted, modifier = Modifier.weight(1f))
            if (v != null) MoneyText(v, d.currency, if (strong) KisType.label.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold) else KisType.label, modifier = Modifier.testTag(tag))
            else Text("—", style = KisType.label, color = c.muted, modifier = Modifier.testTag(tag))
        }
        line("שלך", mine, "job_mine")
        line("לצוות (${d.workers.size})", team, "job_team_pay")
        if (d.passThroughValue() > 0) line("החזר הוצאות", d.passThroughValue(), "job_pass_through")
        line("לקבל מהמעסיק", d.employerDue(mine, team), "job_employer_due", strong = true)
        d.check().errors.firstOrNull { it == "חסר כמה מקבל כל עובד" }?.let {
            Text(it, style = KisType.caption, color = c.pending, modifier = Modifier.testTag("job_team_error"))
        }
    }
}
