package com.example.ui.kis

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.copyExcelToClipboard
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.generateWhatsAppReportText
import com.example.ui.WorkViewModel
import java.util.Calendar

private val FilterSaver = Saver<HistoryFilter, String>(save = { it.toJson() }, restore = { HistoryFilter.fromJson(it) })

/** State the shell needs to see (selection hides the navigation; search opens from a shortcut). */
@Stable
class HistoryUi {
    var selection by mutableStateOf<Set<Int>>(emptySet())
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    val selecting get() = selection.isNotEmpty()
}

@Composable
fun KisHistory(
    ui: HistoryUi,
    visible: Boolean,
    entries: List<WorkEntry>,
    categories: List<WorkCategory>,
    viewModel: WorkViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpen: (WorkEntry) -> Unit,
    onDeleted: (List<WorkEntry>) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(HistoryFilter()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val rows = remember(entries, filter, ui.query) { KisHistoryLogic.apply(entries, filter, ui.query) }
    val listState = rememberLazyListState()
    LaunchedEffect(filter.sort, filter.month, filter.period) { listState.scrollToItem(0) }
    LaunchedEffect(visible) { if (!visible) ui.selection = emptySet() }
    // Existing behaviour: back leaves selection first, then search.
    BackHandler(enabled = visible && ui.selecting) { ui.selection = emptySet() }
    BackHandler(enabled = visible && !ui.selecting && ui.searching) { ui.searching = false }
    // Rows that were deleted elsewhere leave the selection.
    LaunchedEffect(entries) { ui.selection = ui.selection.filter { id -> entries.any { it.id == id } }.toSet() }
    val selectedRows = entries.filter { it.id in ui.selection }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("history_scroll_container"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + if (ui.selecting) 150.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("היסטוריה", style = KisType.page, color = c.ink, modifier = Modifier.weight(1f))
                        ToolIcon(Icons.Rounded.Search, "חיפוש", "history_search_button") { ui.searching = true }
                        Box {
                            ToolIcon(Icons.Rounded.FilterList, if (filter.activeCount == 0) "סינון" else "סינון · ${filter.activeCount} פעילים", "history_filters") { showFilters = true }
                            if (filter.activeCount > 0) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                                Text(filter.activeCount.toString(), style = KisType.micro, color = c.onInk)
                            }
                        }
                        var sortMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolIcon(Icons.AutoMirrored.Rounded.Sort, "מיון", "history_sort") { sortMenu = true }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                SortOrder.entries.forEach { o ->
                                    DropdownMenuItem(text = { Text((if (o == filter.sort) "✓ " else "") + o.label) }, onClick = { sortMenu = false; filter = filter.copy(sort = o) })
                                }
                            }
                        }
                        var copyMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolIcon(Icons.Rounded.ContentCopy, "העתקת דוח", "copy_menu_btn") { copyMenu = true }
                            DropdownMenu(copyMenu, { copyMenu = false }) {
                                DropdownMenuItem(text = { Text("Excel (טבלה)") }, onClick = { copyMenu = false; copyExcelToClipboard(context, rows, filter.category ?: "הכל") })
                                DropdownMenuItem(text = { Text("WhatsApp (סיכום)") }, onClick = {
                                    copyMenu = false
                                    KisShare.copy(context, generateWhatsAppReportText(rows, filter.category ?: "הכל", ui.query), "WhatsApp Report"); onMessage("הסיכום הועתק ✓")
                                })
                                DropdownMenuItem(text = { Text("רשימת משמרות") }, onClick = { copyMenu = false; KisShare.copy(context, KisShare.text(rows)); onMessage("הועתק ✓") })
                            }
                        }
                    }
                    AnimatedVisibility(ui.searching, enter = fadeIn(), exit = fadeOut()) { SearchBar(ui) }
                    if (filter.period == Period.MONTH) MonthSwitcher(filter.month) { filter = filter.copy(month = it) }
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (filter.period == Period.ALL) "כל התאריכים" else "${KisShare.shortDate(filter.from)}–${KisShare.shortDate(filter.to)}", style = KisType.title, color = c.ink)
                        KisChip("לפי חודש", onClick = { filter = filter.copy(period = Period.MONTH) }, minHeight = 34.dp)
                    }
                    ActiveFilters(filter, onClear = { filter = it })
                    Totals(rows, onPending = {
                        ui.selection = rows.filter { !it.payKnown && !WorkPay.isJob(it) }.map { it.id }.toSet()
                        if (ui.selection.isNotEmpty()) bulkOpen = true
                    })
                }
            }
            if (rows.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (entries.isEmpty()) "עוד אין משמרות" else "אין משמרות להצגה", style = KisType.title, color = c.ink)
                    Text(if (entries.isEmpty()) "משמרת ראשונה מוסיפים בכפתור +" else "אפשר לשנות חודש, חיפוש או סינון", style = KisType.body, color = c.muted, textAlign = TextAlign.Center)
                }
            } else if (filter.sort == SortOrder.ADDED) {
                items(rows, key = { it.id }) { e -> HistoryCard(e, categories, ui, onOpen) }
            } else {
                KisHistoryLogic.byWeek(rows).forEach { (week, list) ->
                    item(key = "w$week") {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(KisHistoryLogic.weekLabel(week), style = KisType.label.copy(fontSize = 13.sp), color = c.muted, modifier = Modifier.weight(1f))
                            Text("${KisFormat.hours(list.sumOf { it.hours })} ש׳", style = KisType.label.copy(fontSize = 13.sp), color = c.muted)
                        }
                    }
                    items(list, key = { it.id }) { e -> HistoryCard(e, categories, ui, onOpen) }
                }
            }
            if (rows.isNotEmpty() && !ui.selecting) item(key = "hint") {
                Text("נגיעה לפרטים · לחיצה ארוכה לבחירה, שיתוף והעתקה", style = KisType.caption, color = c.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }
        AnimatedVisibility(ui.selecting, modifier = Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            SelectionBar(selectedRows, rows, ui,
                onShare = { KisShare.share(context, KisShare.text(selectedRows), "שיתוף משמרות") },
                onCopy = { KisShare.copy(context, KisShare.text(selectedRows)); onMessage("הועתק ✓ · מוכן להדבקה בוואטסאפ"); ui.selection = emptySet() },
                onPaid = { paid -> viewModel.setPaid(ui.selection, paid); settings.haptic(KisSettings.Haptic.SUCCESS); onMessage(if (paid) "סומן כשולם ✓" else "סומן כלא שולם"); ui.selection = emptySet() },
                onBulk = { bulkOpen = true },
                onDelete = { confirmDelete = true })
        }
    }
    if (showFilters) FiltersSheet(filter, categories, onDismiss = { showFilters = false }) { filter = it; showFilters = false }
    if (bulkOpen) RateCompletionSheet(selectedRows, viewModel, onDismiss = { bulkOpen = false }) { n ->
        bulkOpen = false
        ui.selection = emptySet()
        settings.haptic(KisSettings.Haptic.SUCCESS)
        onMessage(if (n == 1) "תעריף הושלם למשמרת אחת ✓" else "תעריף הושלם ל-$n משמרות ✓")
    }
    if (confirmDelete) KisSheet(onDismiss = { confirmDelete = false }, tag = "bulk_delete_sheet") {
        Text(if (selectedRows.size == 1) "למחוק משמרת אחת?" else "למחוק ${selectedRows.size} משמרות?", style = KisType.title, color = c.ink)
        Text("אפשר לבטל מיד אחרי המחיקה.", style = KisType.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("ביטול", { confirmDelete = false }, Modifier.weight(1f), KisButtonKind.GHOST)
            KisButton("מחיקה", {
                confirmDelete = false
                settings.haptic(KisSettings.Haptic.WARN)
                val doomed = selectedRows
                ui.selection = emptySet()
                viewModel.deleteEntries(doomed, onDeleted)
            }, Modifier.weight(1f), KisButtonKind.DANGER, haptic = null, tag = "confirm_bulk_delete")
        }
    }
}

@Composable
private fun HistoryCard(e: WorkEntry, categories: List<WorkCategory>, ui: HistoryUi, onOpen: (WorkEntry) -> Unit) {
    ShiftCard(e, categories, selected = e.id in ui.selection,
        onClick = { if (ui.selecting) ui.selection = if (e.id in ui.selection) ui.selection - e.id else ui.selection + e.id else onOpen(e) },
        onLongClick = { ui.selection = if (ui.selecting) (if (e.id in ui.selection) ui.selection - e.id else ui.selection + e.id) else setOf(e.id) })
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Icon(icon, label, tint = Kis.colors.ink, modifier = Modifier.size(48.dp).clip(CircleShape).kisClick(onClick = onClick).testTag(tag).padding(12.dp))
}

@Composable
private fun SearchBar(ui: HistoryUi) {
    val c = Kis.colors
    val focus = remember { FocusRequester() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KisTextField(ui.query, { ui.query = it }, "חיפוש (מקום, הערה, עובד, סכום)", Modifier.weight(1f).focusRequester(focus).testTag("history_search_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { ui.searching = false }))
        // Clear and close are separate on purpose: clearing keeps the field open.
        if (ui.query.isNotEmpty()) Text("ניקוי", style = KisType.label, color = c.ink, modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .kisClick { ui.query = "" }.testTag("history_search_clear").padding(10.dp))
        Icon(Icons.Rounded.Close, "סגירת החיפוש", tint = c.ink, modifier = Modifier.size(44.dp).clip(CircleShape).kisClick { ui.searching = false }.testTag("history_search_close").padding(10.dp))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun MonthSwitcher(month: Int, onMonth: (Int) -> Unit) {
    val c = Kis.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(HistoryFilter.monthName(month), style = KisType.title, color = c.ink, modifier = Modifier.weight(1f).testTag("history_month"))
        // RTL: "previous" points right, "next" points left.
        Icon(Icons.Rounded.ChevronRight, "חודש קודם", tint = c.ink, modifier = Modifier.size(44.dp).clip(CircleShape).kisClick { onMonth(month - 1) }.testTag("month_prev").padding(10.dp))
        Icon(Icons.Rounded.ChevronLeft, "חודש הבא", tint = c.ink, modifier = Modifier.size(44.dp).clip(CircleShape).kisClick { onMonth(month + 1) }.testTag("month_next").padding(10.dp))
    }
}

@Composable
private fun ActiveFilters(f: HistoryFilter, onClear: (HistoryFilter) -> Unit) {
    val chips = buildList {
        if (f.pay != PayFilter.ALL) add(f.pay.label to f.copy(pay = PayFilter.ALL))
        f.category?.let { add(it to f.copy(category = null)) }
        f.currency?.let { add(it to f.copy(currency = null)) }
    }
    if (chips.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (label, next) -> KisChip("$label  ✕", selected = true, onClick = { onClear(next) }, minHeight = 34.dp) }
    }
}

@Composable
private fun Totals(rows: List<WorkEntry>, onPending: () -> Unit) {
    val c = Kis.colors
    val totals = WorkPay.totals(rows)
    val pending = WorkPay.pending(rows).count { !WorkPay.isJob(it) }
    KisCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), radius = 18.dp, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("history_total")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${rows.size} משמרות · ${KisFormat.hours(rows.sumOf { it.hours })} שעות", style = KisType.caption, color = c.muted)
                if (totals.isEmpty()) Text("—", style = KisType.amount, color = c.ink)
                else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { totals.forEach { (cur, v) -> MoneyText(v, cur, KisType.amount) } }
            }
        }
        if (pending > 0) PendingFlag(pending, onPending, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SelectionBar(selected: List<WorkEntry>, rows: List<WorkEntry>, ui: HistoryUi, onShare: () -> Unit, onCopy: () -> Unit, onPaid: (Boolean) -> Unit, onBulk: () -> Unit, onDelete: () -> Unit) {
    val c = Kis.colors
    val pending = selected.count { !it.payKnown && !WorkPay.isJob(it) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(c.ink).navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("selection_bar"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("נבחרו ${selected.size}", style = KisType.title, color = c.onInk, modifier = Modifier.weight(1f).testTag("selection_count"))
            BarButton(if (selected.size < rows.size) "בחירת הכול" else "ניקוי") { ui.selection = if (selected.size < rows.size) rows.map { it.id }.toSet() else emptySet() }
            BarButton("ביטול", tag = "selection_cancel") { ui.selection = emptySet() }
        }
        FlowRowCompat {
            BarButton("שיתוף", tag = "selection_share", onClick = onShare)
            BarButton("העתקה", tag = "selection_copy", onClick = onCopy)
            BarButton("שולם", tag = "selection_paid") { onPaid(true) }
            BarButton("לא שולם", tag = "selection_unpaid") { onPaid(false) }
            if (pending > 0) BarButton("השלמת תעריף · $pending", tag = "selection_bulk_rate", onClick = onBulk)
            BarButton("מחיקה", tag = "selection_delete", onClick = onDelete)
        }
    }
}

@Composable
private fun BarButton(text: String, tag: String? = null, onClick: () -> Unit) {
    val c = Kis.colors
    Box(Modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).background(c.onInk.copy(alpha = 0.16f)).kisClick(onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = KisType.label, color = c.onInk, maxLines = 1)
    }
}

@Composable
private fun FiltersSheet(current: HistoryFilter, categories: List<WorkCategory>, onDismiss: () -> Unit, onApply: (HistoryFilter) -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    var f by remember { mutableStateOf(current) }
    KisSheet(onDismiss = onDismiss, tag = "filters_sheet") {
        Text("סינון", style = KisType.title, color = c.ink)
        Label("תקופה")
        KisSegmented(Period.entries.map { it to it.label }, f.period, { f = f.copy(period = it) }, tagPrefix = "period")
        if (f.period == Period.RANGE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun pick(initial: Long, onPicked: (Long) -> Unit) {
                val cal = Calendar.getInstance().apply { timeInMillis = initial }
                DatePickerDialog(context, { _, y, m, d -> onPicked(Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis) },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            KisChip("מ-${KisShare.shortDate(f.from)}", onClick = { pick(f.from) { f = f.copy(from = it) } }, tag = "range_from")
            KisChip("עד ${KisShare.shortDate(f.to)}", onClick = { pick(f.to) { f = f.copy(to = it) } }, tag = "range_to")
        }
        Label("תשלום")
        KisSegmented(PayFilter.entries.map { it to it.label }, f.pay, { f = f.copy(pay = it) }, tagPrefix = "history_status")
        Label("מקום עבודה")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KisChip("הכול", selected = f.category == null, onClick = { f = f.copy(category = null) })
            categories.forEach { cat -> KisChip(cat.name, selected = f.category == cat.name, onClick = { f = f.copy(category = cat.name) }, tag = "history_dropdown_cat_${cat.name}") }
        }
        Label("מטבע")
        KisSegmented(listOf<Pair<String?, String>>(null to "הכול", "₪" to "₪ שקל", "$" to "$ דולר"), f.currency, { f = f.copy(currency = it) }, tagPrefix = "currency_filter")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            KisButton("איפוס", { f = HistoryFilter(month = f.month, sort = f.sort) }, Modifier.weight(1f), KisButtonKind.GHOST, tag = "filters_reset")
            KisButton("הצגת תוצאות", { onApply(f) }, Modifier.weight(2f), tag = "filters_apply")
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = KisType.label, color = Kis.colors.muted)
