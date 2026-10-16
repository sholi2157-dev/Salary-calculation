package com.example.ui.kis

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkerDirectory
import com.example.domain.V2Rules
import com.example.ui.CoachTarget
import com.example.ui.WorkViewModel
import com.example.ui.coachTarget
import java.util.Calendar

/** Fields the numeric keypad can write to. */
enum class KeyTarget { START, END, HOURS, RATE, BONUS, AMOUNT, BREAK }

private val DraftSaver = Saver<EntryDraft, String>(save = { it.toJson() }, restore = { EntryDraft.fromJson(it) })

/**
 * Add / edit page. Adding is coloured by the workplace; editing is ink, shows "before", and only
 * saves when something changed. The custom keypad keeps the screen still (no system keyboard jump).
 */
@Composable
fun KisEditorPage(
    viewModel: WorkViewModel,
    initial: EntryDraft,
    base: WorkEntry?,
    categories: List<WorkCategory>,
    history: List<WorkEntry>,
    workersDirectory: List<WorkerDirectory>,
    onClose: () -> Unit,
    onSaved: (String, (() -> Unit)?) -> Unit,
    onDeleted: (List<WorkEntry>) -> Unit,
    startInAi: Boolean = false,
    /** False inside the guided tour: the tour owns Back. */
    interactive: Boolean = true,
) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var d by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    val original = remember(initial) { initial.toJson() }
    var target by rememberSaveable { mutableStateOf(if (initial.mode == EntryMode.JOB) KeyTarget.AMOUNT else if (initial.mode == EntryMode.HOURS) KeyTarget.HOURS else if (initial.start == null) KeyTarget.START else KeyTarget.END) }
    var buf by rememberSaveable { mutableStateOf("") }
    var pad by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(initial.breakMin > 0 || initial.notes.isNotBlank() && initial.mode != EntryMode.JOB) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingPlace by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var aiMode by rememberSaveable { mutableStateOf(startInAi && !initial.editing) }
    val changed = d.toJson() != original
    val check = d.check()
    val workplace = workplaceOf(d.category, categories)
    val topBg by animateColorAsState(if (d.editing) c.edit else workplace.from, tween(250), label = "top")
    val onTop = if (d.editing) c.editInk else workplace.on

    fun commitBuf(): Boolean {
        if (buf.isEmpty()) return true
        if (target == KeyTarget.START || target == KeyTarget.END) {
            val b = when (buf.length) { 1, 2 -> buf.padStart(2, '0') + "00"; 3 -> "0$buf"; else -> buf }
            val h = b.take(2).toInt(); val m = b.drop(2).toInt()
            if (h > 23 || m > 59) { Toast.makeText(context, "שעה לא תקינה", Toast.LENGTH_SHORT).show(); buf = ""; return false }
            val v = V2Rules.fromMinutes(h * 60 + m)
            d = if (target == KeyTarget.START) d.copy(start = v) else d.copy(end = v)
        }
        buf = ""
        return true
    }
    fun open(t: KeyTarget) { commitBuf(); focus.clearFocus(); target = t; buf = ""; pad = true }
    fun numberValue(t: KeyTarget) = when (t) { KeyTarget.HOURS -> d.hours; KeyTarget.RATE -> d.rate; KeyTarget.BONUS -> d.bonus; KeyTarget.AMOUNT -> d.amount; KeyTarget.BREAK -> if (d.breakMin > 0) d.breakMin.toString() else ""; else -> "" }
    fun setNumber(t: KeyTarget, v: String) {
        d = when (t) {
            KeyTarget.HOURS -> d.copy(hours = v); KeyTarget.RATE -> d.copy(rate = v, rateUnknown = false); KeyTarget.BONUS -> d.copy(bonus = v)
            KeyTarget.AMOUNT -> d.copy(amount = v, amountUnknown = false); KeyTarget.BREAK -> d.copy(breakMin = v.toIntOrNull()?.coerceIn(0, 600) ?: 0); else -> d
        }
    }
    fun key(k: String) {
        val time = target == KeyTarget.START || target == KeyTarget.END
        if (k == "⌫") { buf = buf.dropLast(1); if (!time) setNumber(target, buf); return }
        if (time) {
            buf = (buf + k).take(4)
            if (buf.length == 4 && commitBuf() && target == KeyTarget.START) target = KeyTarget.END
        } else {
            if (k == "." && (buf.contains('.') || target == KeyTarget.BREAK)) return
            buf = (buf + k).take(7)
            setNumber(target, buf)
        }
    }
    fun shown(t: KeyTarget): String {
        if (pad && target == t && buf.isNotEmpty()) {
            return if (t == KeyTarget.START || t == KeyTarget.END) buf.padEnd(4, '·').let { it.take(2) + ":" + it.drop(2) } else buf
        }
        return when (t) { KeyTarget.START -> d.start ?: "--:--"; KeyTarget.END -> d.end; else -> numberValue(t) }
    }
    fun close() {
        commitBuf()
        if (changed && !saving) confirmLeave = true else onClose()
    }
    fun save() {
        if (saving || !commitBuf()) return
        val now = d.check()
        if (!now.canSave) { settings.haptic(KisSettings.Haptic.WARN); return }
        if (d.editing && !changed) return
        saving = true
        val entry = d.toEntry(base)
        if (d.editing && base != null) {
            viewModel.saveEditedEntry(base.id, entry, onSaved = {
                settings.haptic(KisSettings.Haptic.SUCCESS); onSaved("השינויים נשמרו", null); onClose()
            }, onFailed = { saving = false })
        } else {
            viewModel.saveNewEntry(entry, onSaved = {
                settings.haptic(KisSettings.Haptic.SUCCESS)
                onSaved(if (entry.payKnown) "נשמר ✓" else "נשמר · ${if (entry.kind == WorkEntry.KIND_JOB) "הסכום" else "התעריף"} חסר, אפשר להשלים אחר כך") { viewModel.undoLastAddedEntry() }
                onClose()
            }, onFailed = { saving = false; settings.haptic(KisSettings.Haptic.WARN) })
        }
    }

    BackHandler(enabled = interactive) {
        when {
            confirmLeave -> confirmLeave = false
            pad -> { commitBuf(); pad = false }
            aiMode && !d.editing -> aiMode = false
            else -> close()
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().background(c.bg).imePadding().testTag(if (d.editing) "edit_page" else "add_page").semantics { contentDescription = if (d.editing) "עריכת משמרת" else "משמרת חדשה" }) {
        // ---------------- coloured top ----------------
        Column(Modifier.fillMaxWidth().background(if (d.editing) Brush.linearGradient(listOf(topBg, topBg)) else workplace.brush)
            .statusBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TopPill(if (d.editing) "סגירה" else "ביטול", onTop, tag = "editor_close") { close() }
                Text(when { d.editing -> "עריכת משמרת"; aiMode -> "משמרת במילים"; else -> "משמרת חדשה" }, Modifier.weight(1f).semantics { heading() },
                    color = onTop, style = KisType.label.copy(fontSize = 16.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (d.editing) TopPill("מחיקה", onTop, tag = "editor_delete") { confirmDelete = true }
                else TopPill(if (aiMode) "רישום רגיל" else "במילים", onTop, icon = if (aiMode) null else Icons.Rounded.AutoAwesome, tag = "editor_ai") {
                    commitBuf(); pad = false; aiMode = !aiMode
                }
            }
            if (!aiMode) {
                // While the system keyboard is open (note, worker name) only the top row and the total stay,
                // so the form and the Save bar keep their room on small phones.
                if (!imeOpen) {
                    if (d.editing && base != null) WasBanner(base, changed, onTop)
                    ModeTabs(d.mode, onTop, workplace.from.takeIf { !d.editing } ?: c.edit) { m ->
                        commitBuf()
                        d = d.withMode(m)
                        target = when (m) { EntryMode.JOB -> KeyTarget.AMOUNT; EntryMode.HOURS -> KeyTarget.HOURS; else -> if (d.start == null && m == EntryMode.CLOCK) KeyTarget.START else KeyTarget.END }
                        if (m == EntryMode.GROUP && d.start == null && !d.editing) d = d.copy(start = EntryDraft.suggestStart(d.category, d.day, history).start)
                    }
                    CategoryChips(categories, d.category, onTop, topBg, onPick = { cat -> commitBuf(); d = d.withCategory(cat, viewModel::categoryCurrency, history) }, onAdd = { addingPlace = true })
                    when {
                        d.mode == EntryMode.JOB -> JobAmount(d, shown(KeyTarget.AMOUNT), pad && target == KeyTarget.AMOUNT, onTop,
                            onOpen = { open(KeyTarget.AMOUNT) }, onUnknown = { pad = false; d = d.copy(amountUnknown = true, amount = "") }, onKnown = { d = d.copy(amountUnknown = false); open(KeyTarget.AMOUNT) },
                            hoursLabel = shown(KeyTarget.HOURS), onHours = { open(KeyTarget.HOURS) })
                        d.mode == EntryMode.HOURS || d.groupByHours -> HoursDisplay(shown(KeyTarget.HOURS), pad && target == KeyTarget.HOURS, onTop,
                            onOpen = { open(KeyTarget.HOURS) }, onQuick = { h -> d = d.copy(hours = h.toString()); buf = "" },
                            onUseClock = if (d.mode == EntryMode.GROUP) ({ d = d.copy(start = EntryDraft.suggestStart(d.category, d.day, history).chips.firstOrNull() ?: "09:00"); target = KeyTarget.START }) else null)
                        else -> {
                            Box(Modifier.coachTarget(CoachTarget.FIELDS)) {
                                ClockDisplay(shown(KeyTarget.START), shown(KeyTarget.END), d.start == null && !(pad && target == KeyTarget.START && buf.isNotEmpty()), target, onTop,
                                    onStart = { open(KeyTarget.START) }, onEnd = { open(KeyTarget.END) })
                            }
                            TimeRuler(d.start, d.end, if (target == KeyTarget.START && d.start != null) TimeHandle.START else TimeHandle.END, onTop, topBg,
                                onChange = { h, v -> buf = ""; d = if (h == TimeHandle.START) d.copy(start = v) else d.copy(end = v) },
                                onSelect = { h -> target = if (h == TimeHandle.START) KeyTarget.START else KeyTarget.END; buf = "" })
                            val nudgeStart = target == KeyTarget.START && d.start != null
                            TimeNudges(if (nudgeStart) "התחלה" else "סיום", onTop) { n ->
                                commitBuf()
                                d = if (nudgeStart) d.copy(start = nudge(d.start!!, n)) else d.copy(end = nudge(d.end, n))
                            }
                            if (d.mode == EntryMode.GROUP) Text("הזנת שעות במקום שעון", color = onTop.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).kisClick { d = d.copy(start = null); target = KeyTarget.HOURS }.padding(vertical = 4.dp))
                        }
                    }
                }
                // On an edit, show exactly what will be stored (an untouched imported amount stays as it is).
                val shownTotal = if (base != null && check.canSave) com.example.data.WorkPay.pay(com.example.data.WorkEntryEdits.apply(base, d.toEntry(base))) else check.total
                MetaLine(d, check.copy(total = shownTotal), onTop, topBg)
            }
        }
        if (aiMode) {
            KisAiEntry(viewModel, categories, Modifier.weight(1f), onSaved = { n ->
                settings.haptic(KisSettings.Haptic.SUCCESS)
                onSaved(if (n == 1) "המשמרת נשמרה ✓" else "$n משמרות נשמרו ✓") { viewModel.undoLastAddedEntry() }
                onClose()
            })
            return@Column
        }
        // ---------------- details ----------------
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp).testTag("editor_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.mode == EntryMode.JOB) JobNote(d.notes, history) { d = d.copy(notes = it) }
            if (d.start == null && d.mode == EntryMode.CLOCK) {
                val sug = remember(d.category, d.day, history) { EntryDraft.suggestStart(d.category, d.day, history) }
                if (sug.chips.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("התחלה נפוצה:", style = KisType.caption, color = c.muted)
                    sug.chips.forEach { h -> KisChip(h, onClick = { d = d.copy(start = h); target = KeyTarget.END; buf = "" }, dashed = true, tag = "start_chip_$h") }
                }
            }
            DayStrip(d.day) { d = d.copy(day = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KisChip(dayLabelLong(d.day).takeIf { !isWithinWeek(d.day) } ?: "תאריך אחר", leading = Icons.Rounded.CalendarMonth, tag = "editor_other_date", onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = d.day }
                    DatePickerDialog(context, { _, y, m, day -> d = d.copy(day = Calendar.getInstance().apply { clear(); set(y, m, day) }.timeInMillis) },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                })
                Spacer(Modifier.weight(1f))
                CurrencyToggle(d.currency, { d = d.copy(currency = it) }, "editor_currency")
            }
            FlowRowCompat {
                if (d.mode != EntryMode.JOB) {
                    if (d.rateUnknown) KisChip("תעריף חסר · להוספה", warn = true, tag = "editor_rate_known", onClick = { d = d.copy(rateUnknown = false); open(KeyTarget.RATE) })
                    else {
                        KisChip("${d.currency}${shown(KeyTarget.RATE).ifEmpty { "?" }} לשעה", selected = pad && target == KeyTarget.RATE, tag = "editor_rate", onClick = { open(KeyTarget.RATE) })
                        KisChip("לא יודע", tag = "editor_rate_unknown", onClick = { if (target == KeyTarget.RATE) { buf = ""; pad = false }; d = d.copy(rateUnknown = true) })
                    }
                    val b = shown(KeyTarget.BONUS)
                    KisChip(if (b.isNotEmpty() && b != "0") "בונוס ${d.currency}$b" else "+ בונוס", selected = pad && target == KeyTarget.BONUS, tag = "editor_bonus", onClick = { open(KeyTarget.BONUS) })
                }
                if (d.mode != EntryMode.JOB) KisChip(if (more) "פחות" else "הפסקה · הערה", tag = "editor_more", onClick = { more = !more })
            }
            check.warnings.forEach { Notice(it) }
            if (d.rateUnknown && d.mode != EntryMode.JOB) Text("אפשר לשמור עכשיו ולהשלים את התעריף אחר כך — גם לכמה משמרות ביחד מההיסטוריה. עד אז היא לא נספרת בסכומים.",
                style = KisType.caption, color = c.muted)
            if (d.mode == EntryMode.GROUP) GroupBlock(d, workersDirectory) { d = it }
            if (more && d.mode != EntryMode.JOB) KisCard(padding = PaddingValues(14.dp), radius = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.start != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("הפסקה", style = KisType.label, color = c.ink, modifier = Modifier.weight(1f))
                        KisChip(if (shown(KeyTarget.BREAK).isEmpty()) "בלי הפסקה" else "${shown(KeyTarget.BREAK)} דק׳", selected = pad && target == KeyTarget.BREAK, tag = "editor_break", onClick = { open(KeyTarget.BREAK) })
                    }
                    KisTextField(d.notes, { d = d.copy(notes = it) }, "הערה", Modifier.fillMaxWidth().testTag("notes_input"), placeholder = "לא חובה", singleLine = false)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // ---------------- keypad or save bar ----------------
        if (pad) Keypad(time = target == KeyTarget.START || target == KeyTarget.END, onKey = ::key,
            onDone = { commitBuf(); pad = false }, saveLabel = saveLabel(d, changed), canSave = check.canSave && (!d.editing || changed) && !saving, onSave = ::save)
        else SaveBar(check, saveLabel(d, changed), check.canSave && (!d.editing || changed) && !saving, ::save)
    }

    if (confirmLeave) KisSheet(onDismiss = { confirmLeave = false }, tag = "leave_sheet") {
        Text(if (d.editing) "לצאת בלי לשמור את השינויים?" else "לצאת בלי לשמור את המשמרת?", style = KisType.title, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("להמשיך לערוך", { confirmLeave = false }, Modifier.weight(1f), KisButtonKind.GHOST)
            KisButton("לצאת", { confirmLeave = false; onClose() }, Modifier.weight(1f), tag = "leave_confirm")
        }
    }
    if (confirmDelete && base != null) KisSheet(onDismiss = { confirmDelete = false }) {
        Text("למחוק את המשמרת?", style = KisType.title, color = c.ink)
        Text("אפשר לבטל מיד אחרי המחיקה.", style = KisType.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("ביטול", { confirmDelete = false }, Modifier.weight(1f), KisButtonKind.GHOST)
            KisButton("מחיקה", {
                confirmDelete = false
                settings.haptic(KisSettings.Haptic.WARN)
                viewModel.deleteEntries(listOf(base)) { onDeleted(it); onClose() }
            }, Modifier.weight(1f), KisButtonKind.DANGER, haptic = null, tag = "editor_delete_confirm")
        }
    }
    if (addingPlace) AddWorkplaceSheet(viewModel, categories, onDismiss = { addingPlace = false }) { name, rate, currency ->
        addingPlace = false
        d = if (d.editing) d.copy(category = name) else d.copy(category = name, rate = KisFormat.hours(rate), rateUnknown = false, currency = currency)
    }
}

private fun saveLabel(d: EntryDraft, changed: Boolean) = if (d.editing) (if (changed) "שמירת שינויים" else "אין שינויים") else "שמירה"

private fun isWithinWeek(day: Long): Boolean {
    val today = EntryDraft.startOfDay(System.currentTimeMillis())
    return day <= today && day > today - 7 * 86_400_000L - 3_600_000L
}

// ======================================================================= pieces

@Composable
private fun TopPill(text: String, ink: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tag: String, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(99.dp)).background(ink.copy(alpha = 0.18f)).kisClick(onClick = onClick).testTag(tag).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
        Text(text, color = ink, style = KisType.label)
    }
}

@Composable
private fun WasBanner(base: WorkEntry, changed: Boolean, ink: Color) {
    val pay = com.example.data.WorkPay.pay(base)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp).testTag("was_banner"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (changed) Tag("שונה", color = Workplaces[3].from, ink = Workplaces[3].on)
        Text("לפני: ${dayLabel(base.date)} · ${timeLabel(base)} · ${pay?.let { KisFormat.money(it, base.currency) } ?: com.example.data.WorkPay.missingLabel(base)}",
            color = ink, style = KisType.caption.copy(textDirection = TextDirection.Rtl), maxLines = 2)
    }
}

@Composable
private fun ModeTabs(mode: EntryMode, ink: Color, bg: Color, onMode: (EntryMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.16f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        EntryMode.entries.forEach { m ->
            val on = m == mode
            Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) ink else Color.Transparent)
                .kisClick(role = Role.Tab) { onMode(m) }.semantics { selected = on }.testTag("mode_${m.name.lowercase()}"), contentAlignment = Alignment.Center) {
                Text(m.label, color = if (on) bg else ink.copy(alpha = 0.9f), style = KisType.label)
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<WorkCategory>, selected: String, ink: Color, bg: Color, onPick: (WorkCategory) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { cat ->
            val on = cat.name == selected
            Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(99.dp)).background(if (on) ink else Color.Transparent)
                .border(1.5.dp, ink.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(99.dp))
                .kisClick { onPick(cat) }.semantics { this.selected = on }.testTag("editor_category_${cat.name}").padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(cat.name, color = if (on) bg else ink, style = KisType.label, maxLines = 1)
            }
        }
        Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(99.dp)).border(1.5.dp, ink.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
            .kisClick(onClick = onAdd).testTag("editor_add_place").padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text("+ מקום", color = ink, style = KisType.label)
        }
    }
}

@Composable
private fun ClockDisplay(start: String, end: String, startEmpty: Boolean, target: KeyTarget, ink: Color, onStart: () -> Unit, onEnd: () -> Unit) {
    // The digits scale with the width so "00:00" always fits whole (360dp phones included).
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val digits = (maxWidth.value * 0.125f).coerceIn(38f, 60f).sp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable fun cell(label: String, value: String, on: Boolean, empty: Boolean, tag: String, click: () -> Unit) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) ink.copy(alpha = 0.16f) else Color.Transparent)
                    .kisClick(onClick = click).testTag(tag).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(label, color = ink.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold))
                    Text(value, color = ink.copy(alpha = if (empty) 0.45f else 1f), style = KisType.hero.copy(fontSize = digits, lineHeight = digits * 1.05f,
                        textDirection = TextDirection.Ltr, fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
                }
            }
            cell("התחלה", start, target == KeyTarget.START, startEmpty, "clock_start", onStart)
            Text("←", color = ink.copy(alpha = 0.7f), fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
            cell("סיום", end, target == KeyTarget.END, false, "clock_end", onEnd)
        }
    }
}

@Composable
private fun HoursDisplay(value: String, active: Boolean, ink: Color, onOpen: () -> Unit, onQuick: (Int) -> Unit, onUseClock: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) ink.copy(alpha = 0.16f) else Color.Transparent)
        .kisClick(onClick = onOpen).testTag("hours_display").padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text("כמה שעות · נגיעה להקלדה", color = ink.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold))
        Text(value.ifEmpty { "0" }, color = ink.copy(alpha = if (value.isEmpty()) 0.45f else 1f), style = KisType.hero.copy(fontSize = 54.sp, textDirection = TextDirection.Ltr))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(2, 4, 6, 8).forEach { h ->
            Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.16f)).kisClick { onQuick(h) }.testTag("quick_hours_$h"),
                contentAlignment = Alignment.Center) { Text("$h ש׳", color = ink, style = KisType.label) }
        }
    }
    if (onUseClock != null) Text("לפי שעון (התחלה וסיום)", color = ink.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).kisClick(onClick = onUseClock).padding(vertical = 4.dp))
}

@Composable
private fun JobAmount(d: EntryDraft, amount: String, active: Boolean, ink: Color, onOpen: () -> Unit, onUnknown: () -> Unit, onKnown: () -> Unit, hoursLabel: String, onHours: () -> Unit) {
    if (d.amountUnknown) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).kisClick(onClick = onKnown).testTag("job_amount_unknown").padding(8.dp)) {
            Text("כמה הרווחת", color = ink.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold))
            Text("עוד לא יודע · להוספה", color = ink.copy(alpha = 0.6f), style = KisType.title.copy(fontSize = 30.sp))
        }
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) ink.copy(alpha = 0.16f) else Color.Transparent)
            .kisClick(onClick = onOpen).testTag("job_amount").padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("כמה הרווחת · נגיעה להקלדה", color = ink.copy(alpha = 0.85f), style = KisType.caption.copy(fontWeight = FontWeight.SemiBold))
            MoneyText(amount.toDoubleOrNull() ?: 0.0, d.currency, KisType.hero.copy(fontSize = 54.sp), color = ink.copy(alpha = if (amount.isEmpty()) 0.45f else 1f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!d.amountUnknown) Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.16f)).kisClick(onClick = onUnknown)
            .testTag("job_unknown").padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("עוד לא יודע", color = ink, style = KisType.label) }
        Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.16f)).kisClick(onClick = onHours).testTag("job_hours"),
            contentAlignment = Alignment.Center) {
            Text(if (hoursLabel.isNotEmpty() && hoursLabel != "0") "לקח $hoursLabel שעות" else "כמה זמן לקח? (לא חובה)", color = ink, style = KisType.label)
        }
    }
}

@Composable
private fun MetaLine(d: EntryDraft, check: EntryDraft.Check, ink: Color, bg: Color) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("editor_meta"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val minutes = check.minutes
            if (d.mode != EntryMode.JOB && minutes != null && minutes > 0 && check.errors.none { it.startsWith("שעת") || it.startsWith("ההפסקה") }) {
                Text("${KisFormat.hm(minutes)} שעות", color = ink, style = KisType.label.copy(fontSize = 15.sp))
                if (check.overnight) Tag("עד למחרת", color = ink, ink = bg)
            } else if (check.errors.isNotEmpty() && d.mode != EntryMode.JOB) Text(check.errors.first(), color = ink.copy(alpha = 0.85f), style = KisType.caption)
        }
        when {
            check.total != null -> MoneyText(check.total, d.currency, KisType.amount.copy(fontSize = 24.sp), color = ink, modifier = Modifier.testTag("editor_total"))
            d.rateUnknown && d.mode != EntryMode.JOB -> Text("תעריף חסר", color = ink, style = KisType.label)
            d.amountUnknown && d.mode == EntryMode.JOB -> Text("סכום חסר", color = ink, style = KisType.label)
        }
    }
}

@Composable
private fun DayStrip(day: Long, onDay: (Long) -> Unit) {
    val c = Kis.colors
    val today = EntryDraft.startOfDay(System.currentTimeMillis())
    Row(Modifier.fillMaxWidth().testTag("day_strip"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 6 downTo 0) {
            val millis = EntryDraft.startOfDay(today - i * 86_400_000L + 3_600_000L)
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val on = millis == day
            Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.ink else c.surface)
                .kisClick { onDay(millis) }.semantics { selected = on }.testTag("day_$i"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (i == 0) "היום" else KisFormat.DAY_SHORT[cal.get(Calendar.DAY_OF_WEEK) - 1], style = KisType.micro.copy(fontSize = 12.sp), color = if (on) c.onInk else c.muted)
                Text(cal.get(Calendar.DAY_OF_MONTH).toString(), style = KisType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = if (on) c.onInk else c.ink)
            }
        }
    }
}

@Composable
private fun JobNote(notes: String, history: List<WorkEntry>, onNotes: (String) -> Unit) {
    val past = remember(history) { history.filter { it.kind == WorkEntry.KIND_JOB && it.notes.isNotBlank() }.map { it.notes }.distinct().take(4) }
    KisCard(padding = PaddingValues(14.dp), radius = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KisTextField(notes, onNotes, "מה עשית?", Modifier.fillMaxWidth().testTag("job_note"), placeholder = "למשל: פירוק ארון ספרים")
            if (past.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                past.forEach { n -> KisChip(n, dashed = true, onClick = { onNotes(n) }) }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    val c = Kis.colors
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.pendingSoft).padding(horizontal = 12.dp, vertical = 8.dp), color = c.pending, style = KisType.caption)
}

@Composable
private fun GroupBlock(d: EntryDraft, directory: List<WorkerDirectory>, onDraft: (EntryDraft) -> Unit) {
    val c = Kis.colors
    var newName by rememberSaveable { mutableStateOf("") }
    KisCard(padding = PaddingValues(14.dp), radius = 18.dp, modifier = Modifier.testTag("group_block")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("מי עבד איתך", style = KisType.label.copy(fontSize = 16.sp), color = c.ink)
            d.workers.forEachIndexed { i, w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(w.name, style = KisType.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    SmallNumberField(w.hours, "שעות", Modifier.width(76.dp).testTag("worker_hours_$i")) { v -> onDraft(d.copy(workers = d.workers.toMutableList().also { it[i] = w.copy(hours = v) })) }
                    SmallNumberField(w.bonus, "בונוס", Modifier.width(76.dp).testTag("worker_bonus_$i")) { v -> onDraft(d.copy(workers = d.workers.toMutableList().also { it[i] = w.copy(bonus = v) })) }
                    Icon(Icons.Rounded.Close, "הסרת ${w.name}", tint = c.muted, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .kisClick { onDraft(d.copy(workers = d.workers.filterIndexed { j, _ -> j != i })) }.padding(9.dp))
                }
            }
            val defaultHours = d.check().minutes?.let { KisFormat.hours(it / 60.0) } ?: d.hours
            val suggestions = directory.map { it.name.trim() }.filter { n -> n.isNotEmpty() && d.workers.none { it.name == n } }.distinct()
            if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { n -> KisChip("+ $n", onClick = { onDraft(d.copy(workers = d.workers + WorkerDraft(n, defaultHours))) }, tag = "add_worker_$n") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisTextField(newName, { newName = it }, "עובד חדש", Modifier.weight(1f).testTag("worker_name_input"))
                Icon(Icons.Rounded.PersonAdd, "הוספת עובד", tint = c.onInk, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.ink)
                    .kisClick(enabled = newName.isNotBlank()) { onDraft(d.copy(workers = d.workers + WorkerDraft(newName.trim(), defaultHours))); newName = "" }.padding(12.dp).testTag("worker_add"))
            }
            if (d.workers.isNotEmpty() && d.bonusValue() > 0) {
                Text("הבונוס של ${d.currency}${d.bonus} הולך ל:", style = KisType.caption, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BonusTarget.entries.forEach { t -> KisChip(t.label, selected = d.bonusTarget == t, onClick = { onDraft(d.withBonusTarget(t)) }, tag = "bonus_target_${t.name.lowercase()}") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("תעריף נפרד למעסיק ולעובדים", style = KisType.label, color = c.ink)
                    Text("כשאתה מקבל מהמעסיק יותר ממה שאתה משלם לעובדים", style = KisType.caption, color = c.muted)
                }
                KisSwitch(d.separateRates, { onDraft(d.copy(separateRates = it)) }, label = "תעריף נפרד")
            }
            if (d.separateRates) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField(d.employerRate, "המעסיק משלם לשעה", Modifier.weight(1f).testTag("employer_rate")) { onDraft(d.copy(employerRate = it)) }
                SmallNumberField(d.workerRate, "אני משלם לעובד", Modifier.weight(1f).testTag("worker_rate")) { onDraft(d.copy(workerRate = it)) }
            }
        }
    }
}

@Composable
private fun SmallNumberField(value: String, label: String, modifier: Modifier, onValue: (String) -> Unit) {
    KisTextField(value, { v -> val f = v.filter { it.isDigit() || it == '.' }; if (f.count { it == '.' } <= 1) onValue(f) }, label, modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun Keypad(time: Boolean, onKey: (String) -> Unit, onDone: () -> Unit, saveLabel: String, canSave: Boolean, onSave: () -> Unit) {
    val c = Kis.colors
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (time) "00" else ".", "0", "⌫")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.surface).navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 10.dp).testTag("keypad")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // RTL: the action column sits on the left edge (end), digits read 1-2-3 from the right.
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.sunk)
                                .kisClick(haptic = KisSettings.Haptic.TICK) { onKey(k) }.semantics { contentDescription = if (k == "⌫") "מחיקה" else k }.testTag("key_$k"),
                                contentAlignment = Alignment.Center) {
                                Text(k, style = KisType.body.copy(fontSize = if (k.length > 1 || k == "." || k == "⌫") 17.sp else 22.sp, fontWeight = FontWeight.Medium,
                                    textDirection = TextDirection.Ltr), color = if (k == "⌫" || k == "00" || k == ".") c.muted else c.ink)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                    .kisClick(onClick = onDone).testTag("keypad_done"), contentAlignment = Alignment.Center) {
                    Text("סגירת\nמקלדת ✓", style = KisType.caption.copy(fontWeight = FontWeight.SemiBold), color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Box(Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(14.dp)).background(c.ink).graphicsAlpha(if (canSave) 1f else 0.35f)
                    .kisClick(enabled = canSave, haptic = null, onClick = onSave).testTag("save_shift_button"), contentAlignment = Alignment.Center) {
                    Text(saveLabel.replace(" ", "\n"), style = KisType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = c.onInk,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float) = this.graphicsLayer { alpha = a }

@Composable
private fun SaveBar(check: EntryDraft.Check, label: String, enabled: Boolean, onSave: () -> Unit) {
    val c = Kis.colors
    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (check.errors.isNotEmpty()) Text(check.errors.first(), style = KisType.caption, color = c.muted, modifier = Modifier.fillMaxWidth().testTag("save_hint"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        KisButton(label, onSave, Modifier.fillMaxWidth(), enabled = enabled, haptic = null, tag = "save_shift_button")
    }
}

/** A tiny flow row: wraps chips onto as many lines as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun AddWorkplaceSheet(viewModel: WorkViewModel, categories: List<WorkCategory>, onDismiss: () -> Unit, onAdded: (String, Double, String) -> Unit) {
    val c = Kis.colors
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("40") }
    var currency by rememberSaveable { mutableStateOf(defaultCurrency) }
    val taken = categories.any { it.name.trim().equals(name.trim(), ignoreCase = true) }
    val rateValue = rate.toDoubleOrNull()
    KisSheet(onDismiss = onDismiss, tag = "add_place_sheet") {
        Text("מקום עבודה חדש", style = KisType.title, color = c.ink)
        KisTextField(name, { name = it }, "שם המקום", Modifier.fillMaxWidth().testTag("new_place_name"), isError = taken)
        if (taken) Text("כבר יש מקום בשם הזה", style = KisType.caption, color = c.danger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' } }, "תעריף לשעה", Modifier.weight(1f).testTag("new_place_rate"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            CurrencyToggle(currency, { currency = it }, "new_place_currency")
        }
        KisButton("הוספה", {
            val n = name.trim()
            if (n.isNotEmpty() && !taken && rateValue != null && rateValue >= 0) { viewModel.addWorkplace(n, rateValue, currency); onAdded(n, rateValue, currency) }
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !taken && rateValue != null, tag = "new_place_add")
    }
}

// ------------------------------------------------------------------ labels used across Kis screens

fun dayLabel(millis: Long): String {
    val today = EntryDraft.startOfDay(System.currentTimeMillis())
    val day = EntryDraft.startOfDay(millis)
    val diff = Math.round((today - day) / 86_400_000.0)
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return when (diff) {
        0L -> "היום"; 1L -> "אתמול"; -1L -> "מחר"
        else -> "יום ${KisFormat.DAY_SHORT[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}"
    }
}

fun dayLabelLong(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${dayLabel(millis)}${if (cal.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR)) "." + cal.get(Calendar.YEAR) else ""}"
}

/** "17:00–22:30" for clock entries, "5.5 שעות" otherwise. */
fun timeLabel(e: WorkEntry): String =
    if (e.isTimeRange && e.startTime != null && e.endTime != null) "⁦${e.startTime}–${e.endTime}⁩" else "${KisFormat.hours(e.hours)} שעות"
