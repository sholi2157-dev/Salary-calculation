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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import kotlinx.coroutines.launch

/** A short message at the bottom, optionally with one action (usually undo). */
data class KisMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Requests the shell can receive from outside (system shortcuts, notifications). */
enum class ShellRequest { IMPORT, HISTORY_SEARCH }

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
    val defaultCategory = prefs["defaultCategory"]?.takeIf { n -> categories.any { it.name == n } } ?: categories.firstOrNull()?.name ?: ""

    // Home and history stay one swipeable pager, as before (RTL swipe = next tab).
    val pager = rememberPagerState(pageCount = { 2 })
    val history = remember { HistoryUi() }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editorJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editorBaseId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorAi by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var startLive by rememberSaveable { mutableStateOf(false) }
    var pendingBulk by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<KisMessage?>(null) }
    var navHeight by remember { mutableStateOf(0.dp) }

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
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) say("בלי הרשאה, הטיימר לא יופיע בהתראות. הוא עדיין רץ ונשמר.")
    }

    LaunchedEffect(request) {
        when (request) {
            ShellRequest.IMPORT -> { settingsOpen = true; onRequestHandled() }
            ShellRequest.HISTORY_SEARCH -> { pager.scrollToPage(1); history.searching = true; onRequestHandled() }
            null -> Unit
        }
    }
    LaunchedEffect(message?.id) { if (message != null) { delay(if (message?.action != null) 6000 else 2600); message = null } }
    BackHandler(enabled = !settingsOpen && editorJson == null && pager.currentPage != 0 && !history.selecting && !history.searching) { go(0) }

    Box(modifier.fillMaxSize().background(c.bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("main_screen_pager"), beyondViewportPageCount = 1,
            key = { if (it == 0) "home" else "history" }) { page ->
            when (page) {
                0 -> KisHome(entries, categories, live, navHeight,
                    onStartLive = { startLive = true },
                    onStopLive = { viewModel.finishActiveShift(); settings.haptic(KisSettings.Haptic.SUCCESS); say("המשמרת נשמרה ✓", "ביטול") { viewModel.undoLastAddedEntry() } },
                    onCancelLive = { viewModel.cancelActiveShift(liveStart) },
                    onLiveCurrency = { viewModel.updateActiveShiftCurrency(it, liveStart) },
                    onOpen = { detailId = it.id },
                    onPending = {
                        go(1)
                        history.selection = entries.filter { !it.payKnown && it.kind != WorkEntry.KIND_JOB }.map { it.id }.toSet()
                        pendingBulk = history.selection.isNotEmpty()
                    },
                    onAllHistory = { go(1) },
                    onSettings = { settingsOpen = true })
                else -> KisHistory(history, pager.currentPage == 1, entries, categories, viewModel, navHeight, onOpen = { detailId = it.id }, onDeleted = onDeleted, onMessage = { say(it) })
            }
        }
        // Floating pill navigation; hidden while choosing shifts (the selection bar takes its place).
        AnimatedVisibility(!history.selecting || pager.currentPage != 1, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(140)) + slideInVertically { it / 2 }, exit = fadeOut(tween(140)) + slideOutVertically { it / 2 }) {
            // Measured outside the system-bar padding: lists and messages must clear the pill on 3-button navigation too.
            Box(Modifier.onSizeChanged { navHeight = with(density) { it.height.toDp() } + 8.dp }.navigationBarsPadding().padding(bottom = 14.dp)) {
                PillNav(pager.currentPage, onPage = { go(it) }, onSettings = { settingsOpen = true }, onAdd = { openNew() })
            }
        }
        message?.let { m ->
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = navHeight, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(99.dp)).background(c.ink).padding(start = 18.dp, end = 6.dp).heightIn(min = 48.dp).testTag("kis_message"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.text, style = KisType.label, color = c.onInk, modifier = Modifier.padding(vertical = 10.dp))
                if (m.action != null) Text(m.action, style = KisType.label, color = Workplaces[3].accent(true),
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).kisClick { m.onAction?.invoke(); message = null }.testTag("kis_message_action").padding(horizontal = 12.dp, vertical = 10.dp))
                else Spacer(Modifier.width(12.dp))
            }
        }
        AnimatedVisibility(settingsOpen, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
            BackHandler { settingsOpen = false }
            KisSettingsPage(viewModel, categories, onClose = { settingsOpen = false }, onReplayTour = onReplayTour, onSignIn = onSignIn)
        }
        AnimatedVisibility(editorJson != null, enter = slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            val json = editorJson
            if (json != null) {
                val base = editorBaseId?.let { id -> entries.firstOrNull { it.id == id } }
                if (editorBaseId == null || base != null) key(json.hashCode(), editorBaseId) {
                    KisEditorPage(viewModel, EntryDraft.fromJson(json), base, categories, entries, workers,
                        onClose = { editorJson = null; editorBaseId = null },
                        onSaved = { text, undo -> if (undo != null) say(text, "ביטול", undo) else say(text) },
                        onDeleted = onDeleted, startInAi = editorAi)
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
    Row(Modifier.shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(c.nav).border(1.dp, c.line, shape)
        .padding(6.dp).testTag("bottom_navigation"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun item(label: String, on: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
            Box(modifier.heightIn(min = 48.dp).clip(shape).background(if (on) c.navOn else Color.Transparent).kisClick(role = Role.Tab, onClick = onClick)
                .semantics { selected = on }.testTag(tag).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(label, style = KisType.label, color = if (on) c.navOnInk else c.navInk)
            }
        }
        item("בית", page == 0, "tab_0") { onPage(0) }
        item("היסטוריה", page == 1, "tab_1", Modifier.coachTarget(CoachTarget.HISTORY)) { onPage(1) }
        item("הגדרות", false, "settings_button") { onSettings() }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Workplaces[0].accent(c.dark)).kisClick(onClick = onAdd).testTag("add_entry_button")
            .semantics { this.contentDescription = "משמרת חדשה" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Workplaces[0].accentInk(c.dark), modifier = Modifier.size(28.dp))
        }
    }
}
