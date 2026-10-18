package com.example.ui.kis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WorkEntry
import com.example.ui.CoachTarget
import com.example.ui.WorkViewModel
import com.example.ui.coachTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A short message at the bottom, optionally with one action (usually undo). */
data class KisMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Requests the shell can receive from outside (system shortcuts, notifications). */
enum class ShellRequest { IMPORT, HISTORY_SEARCH, HISTORY, RATES }

@Composable
fun KisApp(
    viewModel: WorkViewModel,
    request: ShellRequest? = null,
    onRequestHandled: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    onSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val allCategories by viewModel.categories.collectAsStateWithLifecycle()
    val categories = remember(allCategories) { allCategories.distinctBy { it.name.trim() } }
    val workers by viewModel.workersDirectory.collectAsStateWithLifecycle()
    val prefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val liveStart by viewModel.activeShiftStartTime.collectAsStateWithLifecycle()
    val liveCategory by viewModel.activeShiftCategory.collectAsStateWithLifecycle()
    val liveRate by viewModel.activeShiftRate.collectAsStateWithLifecycle()
    val liveCurrency by viewModel.activeShiftCurrency.collectAsStateWithLifecycle()
    val live = liveStart?.let { LiveShift(it, liveCategory, liveRate, liveCurrency) }
    val plans by viewModel.occurrences.collectAsStateWithLifecycle()
    val defaultCategory = prefs["defaultCategory"]?.takeIf { n -> categories.any { it.name == n } } ?: categories.firstOrNull()?.name ?: ""

    // Home, history and settings are one swipeable pager (RTL swipe = next tab).
    val pager = rememberPagerState(pageCount = { 3 })
    val history = remember { HistoryUi() }
    var editorJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editorBaseId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorAi by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var startLive by rememberSaveable { mutableStateOf(false) }
    var pendingBulk by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<KisMessage?>(null) }
    var navHeight by remember { mutableStateOf(0.dp) }
    var hoursFor by rememberSaveable { mutableStateOf<String?>(null) }
    var monthSheet by rememberSaveable { mutableStateOf<Int?>(null) }
    var storyScope by rememberSaveable { mutableStateOf<String?>(null) }
    var bellOpen by rememberSaveable { mutableStateOf(false) }
    // A dot on the bell for what arrived since it was last opened (the daily job writes it; read again on every return).
    var unread by remember { mutableStateOf(viewModel.planSettings.unread) }

    fun say(text: String, action: String? = null, onAction: (() -> Unit)? = null) { message = KisMessage(text, action, onAction) }
    fun openNew(ai: Boolean = false) {
        val cat = categories.firstOrNull { it.name == defaultCategory }
        editorBaseId = null; editorAi = ai
        editorJson = EntryDraft.new(cat, cat?.let { viewModel.categoryCurrency(it.name) } ?: defaultCurrency, entries).toJson()
    }
    fun openEdit(e: WorkEntry) { detailId = null; editorBaseId = e.id; editorAi = false; editorJson = EntryDraft.from(e).toJson() }
    fun go(page: Int) { scope.launch { pager.animateScrollToPage(page) } }
    val onDeleted: (List<WorkEntry>) -> Unit = { rows ->
        say(if (rows.size == 1) "המשמרת נמחקה" else "${rows.size} משמרות נמחקו", "ביטול") { viewModel.restoreEntries(rows) }
    }
    // Weekly plans: recording one keeps an undo that holds it (it is not recorded again by itself).
    val planActions = remember(viewModel) {
        PlanActions(
            worked = { o -> viewModel.confirmOccurrence(o) { id ->
                if (id != null) { settings.haptic(KisSettings.Haptic.SUCCESS); say("נרשמה ✓", "ביטול") { viewModel.undoWorked(id) } }
            } },
            skip = { o -> viewModel.cancelOccurrence(o); say("סומן שלא עבדת", "ביטול") { viewModel.restoreOccurrence(o) } },
            hours = { o -> hoursFor = o.key },
            share = { o -> KisShare.shareOrCopy(context, com.example.data.WorkShareText.planned(o), "שיתוף משמרת מתוכננת") { say(it) } },
        )
    }
    // Plans that ended are recorded on open and on every return to the app (and by the daily job), with an undo.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) unread = viewModel.planSettings.unread
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.catchUpPlans { ids ->
                say(if (ids.size == 1) "משמרת קבועה הסתיימה ונרשמה" else "${ids.size} משמרות קבועות הסתיימו ונרשמו", "ביטול") { viewModel.undoAutoConfirm(ids) }
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) say("בלי הרשאה, הטיימר לא יופיע בהתראות. הוא עדיין רץ ונשמר.")
    }

    LaunchedEffect(request) {
        when (request) {
            ShellRequest.IMPORT -> { pager.scrollToPage(2); onRequestHandled() }
            ShellRequest.HISTORY_SEARCH -> { pager.scrollToPage(1); history.planned = false; history.searching = true; onRequestHandled() }
            // The monthly summary is about last month: show that one.
            ShellRequest.HISTORY -> {
                pager.scrollToPage(1); history.planned = false
                val last = HistoryFilter.monthKey(System.currentTimeMillis()) - 1
                history.showMonth = last
                monthSheet = last
                onRequestHandled()
            }
            ShellRequest.RATES -> {
                pager.scrollToPage(1); history.planned = false
                // After a cold start from the notification the shifts are still loading: wait for them briefly.
                val rows = kotlinx.coroutines.withTimeoutOrNull(4_000) { viewModel.entries.first { it.isNotEmpty() } } ?: entries
                history.selection = rows.filter { !it.payKnown && it.kind != WorkEntry.KIND_JOB }.map { it.id }.toSet()
                pendingBulk = history.selection.isNotEmpty()
                onRequestHandled()
            }
            null -> Unit
        }
    }
    // An edit whose shift was deleted meanwhile closes instead of leaving an invisible editor behind.
    LaunchedEffect(editorBaseId, entries) {
        if (editorBaseId != null && entries.isNotEmpty() && entries.none { it.id == editorBaseId }) { editorJson = null; editorBaseId = null }
    }
    LaunchedEffect(message?.id) { if (message != null) { delay(if (message?.action != null) 6000 else 2600); message = null } }
    // History handles its own Back for selection and search, but only on its page; elsewhere Back goes Home.
    BackHandler(enabled = editorJson == null && pager.currentPage != 0 && !(pager.currentPage == 1 && (history.selecting || history.searching || history.planned))) { go(0) }
    // A plan whose time ends while the app is open turns to "waiting" without leaving the screen.
    LaunchedEffect(Unit) { while (true) { delay(60_000); viewModel.tickPlans() } }
    // Leaving a page never leaves the keyboard open over the next one. Only the keyboard is hidden: clearing focus
    // here would, after a hardware key, hand focus to the first control of the old page and scroll the pager back.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var settingsHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        keyboard?.hide()
        // A field typed in Settings would otherwise keep that page pinned (composed) behind Home.
        if (pager.currentPage != 2 && settingsHasFocus) focusManager.clearFocus(force = true)
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("main_screen_pager"), beyondViewportPageCount = 1,
            key = { listOf("home", "history", "settings")[it] }) { page ->
            when (page) {
                0 -> KisHome(entries, categories, live, navHeight,
                    onStartLive = { startLive = true },
                    onStopLive = { viewModel.finishActiveShift(onSaved = { settings.haptic(KisSettings.Haptic.SUCCESS); say("המשמרת נשמרה ✓", "ביטול") { viewModel.undoLastAddedEntry() } }) },
                    onCancelLive = { viewModel.cancelActiveShift(liveStart) },
                    onLiveCurrency = { viewModel.updateActiveShiftCurrency(it, liveStart) },
                    onOpen = { detailId = it.id },
                    onPending = {
                        go(1)
                        history.selection = entries.filter { !it.payKnown && it.kind != WorkEntry.KIND_JOB }.map { it.id }.toSet()
                        pendingBulk = history.selection.isNotEmpty()
                    },
                    onAllHistory = { go(1) },
                    onSettings = { go(2) },
                    plans = plans, planActions = planActions,
                    onAllPlans = { history.selection = emptySet(); history.searching = false; history.planned = true; go(1) },
                    unread = unread, onBell = { bellOpen = true },
                    onMonthSummary = { monthSheet = HistoryFilter.monthKey(System.currentTimeMillis()) })
                1 -> KisHistory(history, pager.currentPage == 1, entries, categories, viewModel, navHeight, onOpen = { detailId = it.id }, onDeleted = onDeleted, onMessage = { say(it) },
                    plans = plans, planActions = planActions, onMonthSummary = { monthSheet = it })
                else -> Box(Modifier.onFocusChanged { settingsHasFocus = it.hasFocus }) {
                    KisSettingsPage(viewModel, categories, onClose = { go(0) }, onReplayTour = onReplayTour, onSignIn = onSignIn, bottomInset = navHeight)
                }
            }
        }
        // Floating pill navigation; hidden while choosing shifts (the selection bar takes its place).
        AnimatedVisibility(!history.selecting || pager.currentPage != 1, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(140)) + slideInVertically { it / 2 }, exit = fadeOut(tween(140)) + slideOutVertically { it / 2 }) {
            // Measured outside the system-bar padding: lists and messages must clear the pill on 3-button navigation too.
            Box(Modifier.onSizeChanged { navHeight = with(density) { it.height.toDp() } + 8.dp }.navigationBarsPadding().padding(bottom = 14.dp)) {
                // The navigation keeps its shape with very large system fonts (labels cap at 1.3×), so "+" never disappears.
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.3f))) {
                    PillNav(pager.currentPage, onPage = { go(it) }, onSettings = { go(2) }, onAdd = { openNew() })
                }
            }
        }
        message?.let { m ->
            // Above the selection bar while choosing shifts, so it never covers its buttons.
            val above = if (history.selecting && pager.currentPage == 1) history.barHeight + 8.dp else navHeight
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = above, start = 16.dp, end = 16.dp)
                .shadow(14.dp, RoundedCornerShape(99.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(99.dp)).background(c.bar).border(1.dp, c.line, RoundedCornerShape(99.dp)).padding(start = 18.dp, end = 6.dp).heightIn(min = 48.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }.testTag("kis_message"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.text, style = KisType.label, color = c.onBar, modifier = Modifier.padding(vertical = 10.dp))
                if (m.action != null) Text(m.action, style = KisType.label, color = if (c.dark) Workplaces[3].accent(true) else Workplaces[0].from,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).kisClick { m.onAction?.invoke(); message = null }.testTag("kis_message_action").padding(horizontal = 12.dp, vertical = 10.dp))
                else Spacer(Modifier.width(12.dp))
            }
        }
        AnimatedVisibility(editorJson != null, enter = slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            val json = editorJson
            if (json != null) {
                val base = editorBaseId?.let { id -> entries.firstOrNull { it.id == id } }
                if (editorBaseId == null || base != null) key(json.hashCode(), editorBaseId) {
                    val page = @Composable {
                        KisEditorPage(viewModel, EntryDraft.fromJson(json), base, categories, entries, workers,
                            onClose = { editorJson = null; editorBaseId = null },
                            onSaved = { text, undo -> if (undo != null) say(text, "ביטול", undo) else say(text) },
                            onDeleted = onDeleted, startInAi = editorAi)
                    }
                    // Editing is the same page as adding, shown as a framed pop-up over the app so it is clearly an edit.
                    // Neither way can a tap or swipe reach the pages behind the editor.
                    if (base == null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) { page() }
                    else Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures { } } // the app behind is not touchable while editing
                        .systemBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
                        val frame = RoundedCornerShape(26.dp)
                        Box(Modifier.fillMaxSize().clip(frame).border(2.dp, if (c.dark) Color.White.copy(alpha = 0.85f) else Color.White, frame).testTag("edit_frame")) { page() }
                    }
                }
            }
        }
    }

    detailId?.let { id -> entries.firstOrNull { it.id == id } }?.let { e ->
        ShiftDetailSheet(e, categories, viewModel, onDismiss = { detailId = null }, onEdit = { openEdit(it) }, onDeleted = onDeleted, onMessage = { say(it) })
    }
    if (pendingBulk && history.selecting) {
        RateCompletionSheet(entries.filter { it.id in history.selection }, viewModel, onDismiss = { pendingBulk = false }) { n ->
            pendingBulk = false; history.selection = emptySet()
            settings.haptic(KisSettings.Haptic.SUCCESS)
            say(if (n == 1) "תעריף הושלם למשמרת אחת ✓" else "תעריף הושלם ל-$n משמרות ✓")
        }
    }
    if (bellOpen) NotificationsSheet(viewModel, onMonth = { bellOpen = false; unread = false; monthSheet = it },
        onSettings = { bellOpen = false; unread = false; go(2) }, onDismiss = { bellOpen = false; unread = false })
    monthSheet?.let { m ->
        MonthSummarySheet(m, entries, categories, onHistory = { monthSheet = null; history.planned = false; history.showMonth = m; go(1) },
            onDismiss = { monthSheet = null }, onMessage = { say(it) }, onStory = { monthSheet = null; storyScope = it })
    }
    storyScope?.let { sc ->
        KisStory(sc, entries, categories, onClose = { storyScope = null }, onMessage = { say(it) })
    }
    hoursFor?.let { key -> plans.firstOrNull { it.key == key } }?.let { o ->
        PlanHoursSheet(o, onDismiss = { hoursFor = null }) { start, end, fromNowOn ->
            hoursFor = null
            viewModel.editOccurrence(o, start, end, fromNowOn)
            say(if (fromNowOn) "השעות עודכנו מעכשיו והלאה ✓" else "השעות עודכנו ליום הזה ✓")
        }
    }
    if (startLive) StartLiveSheet(categories, defaultCategory, viewModel::categoryCurrency, onDismiss = { startLive = false }) { cat, rate, currency ->
        startLive = false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.startActiveShift(cat, rate, currency)
    }
}

@Composable
internal fun PillNav(page: Int, onPage: (Int) -> Unit, onSettings: () -> Unit, onAdd: () -> Unit) {
    val c = Kis.colors
    val shape = RoundedCornerShape(99.dp)
    Row(Modifier.shadow(if (c.dark) 18.dp else 14.dp, shape, ambientColor = Color.Black.copy(alpha = if (c.dark) 1f else 0.3f), spotColor = Color.Black.copy(alpha = if (c.dark) 1f else 0.3f))
        .clip(shape).background(c.nav).border(1.dp, if (c.dark) c.line else Color.White, shape)
        .padding(6.dp).testTag("bottom_navigation"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun item(label: String, on: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
            Box(modifier.heightIn(min = 48.dp).clip(shape).background(if (on) c.navOn else Color.Transparent).kisClick(role = Role.Tab, onClick = onClick)
                .semantics { selected = on }.testTag(tag).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(label, style = KisType.label, color = if (on) c.navOnInk else c.navInk, maxLines = 1)
            }
        }
        item("בית", page == 0, "tab_0") { onPage(0) }
        item("היסטוריה", page == 1, "tab_1", Modifier.coachTarget(CoachTarget.HISTORY)) { onPage(1) }
        item("הגדרות", page == 2, "settings_button") { onSettings() }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Workplaces[0].accent(c.dark)).kisClick(onClick = onAdd).testTag("add_entry_button")
            .semantics { this.contentDescription = "משמרת חדשה" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Workplaces[0].accentInk(c.dark), modifier = Modifier.size(28.dp))
        }
    }
}
