package com.example.ui.kis

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WorkViewModel

/*
 * Screens say what things are, not how to use them. An explanation exists only behind the ⓘ next to a title.
 * Home's bell shows what arrived; the notification switches and the profile live in Settings.
 */

/** ⓘ: the explanation of [title], shown only when asked for. */
@Composable
fun InfoDot(title: String, text: String, tag: String? = null) {
    val c = Kis.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.size(40.dp).clip(CircleShape).kisClick { open = true }.semantics { contentDescription = "הסבר: $title" }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Info, null, tint = c.muted, modifier = Modifier.size(20.dp))
    }
    if (open) KisSheet(onDismiss = { open = false }, tag = "info_sheet") {
        Text(title, style = KisType.title, color = c.ink)
        Text(text, style = KisType.body, color = c.ink)
        KisButton("הבנתי", { open = false }, Modifier.fillMaxWidth(), height = 48.dp)
    }
}

/** Top-left of Home: the bell, with a dot when something new arrived. */
@Composable
fun HeaderActions(unread: Boolean, onBell: () -> Unit) {
    val c = Kis.colors
    Box(Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).kisClick(onClick = onBell)
        .semantics { contentDescription = if (unread) "התראות · יש חדשות" else "התראות" }.testTag("home_bell"), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Notifications, null, tint = c.ink, modifier = Modifier.size(22.dp))
        if (unread) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(9.dp).clip(CircleShape).background(c.danger).testTag("home_bell_dot"))
    }
}

/** What arrived; a monthly summary opens again. The switches live in Settings. */
@Composable
fun NotificationsSheet(viewModel: WorkViewModel, onMonth: (Int) -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit,
                       update: com.example.ui.WorkUpdates.Release? = null, onUpdate: (com.example.ui.WorkUpdates.Release) -> Unit = {},
                       onStory: ((String) -> Unit)? = null) {
    val c = Kis.colors
    val plan = viewModel.planSettings
    // A monthly summary shows only once its recap has opened, and stays marked "חדש" until the recap is watched.
    val recent = remember { plan.notes() }
    LaunchedEffect(Unit) { plan.seenAt = System.currentTimeMillis() }
    KisSheet(onDismiss = onDismiss, tag = "notifications_sheet") {
        Text("התראות", style = KisType.title, color = c.ink)
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // A new version comes first: one tap opens the update sheet (download with progress, then Android's installer).
            update?.let { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Workplaces[0].from, Workplaces[4].from)))
                    .testTag("update_note").padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("גרסה ${r.name} זמינה", style = KisType.title, color = Color.White)
                    if (r.notes.isNotBlank()) Text(r.notes, style = KisType.caption, color = Color.White.copy(alpha = .9f), maxLines = 4)
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .kisClick { onUpdate(r) }.testTag("update_now"),
                        contentAlignment = Alignment.Center) {
                        Text("עדכון", style = KisType.label, color = Color(0xFF121212))
                    }
                }
            }
            if (recent.isEmpty() && update == null) Text("אין התראות", style = KisType.body, color = c.muted, modifier = Modifier.padding(vertical = 6.dp).testTag("notifications_empty"))
            recent.forEachIndexed { i, n ->
                val month = if (n.kind == "monthly") monthKeyOf(n.month) else null
                val open: (() -> Unit)? = when {
                    month != null && onStory != null -> { { onStory(n.month) } }
                    month != null -> { { onMonth(month) } }
                    n.kind == "update" && update != null -> { { onUpdate(update) } }
                    else -> null
                }
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(c.bg)
                    .then(if (open != null) Modifier.kisClick(onClick = open) else Modifier).testTag("recent_note_$i").padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n.title, style = KisType.label, color = c.ink, modifier = Modifier.weight(1f, fill = false))
                        if (n.kind == "monthly" && !n.opened) Text("חדש", style = KisType.micro, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(Workplaces[0].from).padding(horizontal = 8.dp, vertical = 2.dp).testTag("recent_note_new_$i"))
                    }
                    Text(n.text, style = KisType.caption, color = c.muted)
                    Text(KisShare.shortDate(n.time), style = KisType.micro, color = c.muted)
                }
            }
        }
        KisButton("הגדרות התראות", onSettings, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "notifications_settings")
    }
}

/** The notification switches (a group in Settings), each explained behind its ⓘ. */
@Composable
fun NotificationSwitches(viewModel: WorkViewModel) {
    val c = Kis.colors
    val context = LocalContext.current
    val plan = viewModel.planSettings
    val serviceNotification by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    var notifyMonthly by remember { mutableStateOf(plan.notifyMonthly) }
    var notifyRate by remember { mutableStateOf(plan.notifyRate) }
    var notifyPlans by remember { mutableStateOf(plan.notifyPlans) }
    var notifyUpdates by remember { mutableStateOf(plan.notifyUpdates) }
    var quiet by remember { mutableStateOf(plan.quiet) }
    var quietFrom by remember { mutableStateOf(plan.quietFrom) }
    var quietTo by remember { mutableStateOf(plan.quietTo) }
    var denied by remember { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    fun set(kind: String, on: Boolean) {
        when (kind) {
            "monthly" -> { plan.notifyMonthly = on; notifyMonthly = on }
            "rate" -> { plan.notifyRate = on; notifyRate = on }
            "plans" -> { plan.notifyPlans = on; notifyPlans = on }
            else -> { plan.notifyUpdates = on; notifyUpdates = on }
        }
        // The next wake-up follows the switches (the monthly summary is one of them).
        com.example.PlanJobs.scheduleNextAsync(context)
    }
    fun turnOn(kind: String) { denied = false; set(kind, true) }
    // Without permission the switch stays off.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> asking?.let { if (granted) turnOn(it) else denied = true }; asking = null }
    fun enable(kind: String, on: Boolean) {
        if (!on) { set(kind, false); return }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) { asking = kind; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        else turnOn(kind)
    }
    @Composable fun line(title: String, info: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
            InfoDot(title, info)
            Spacer(Modifier.weight(1f))
            KisSwitch(checked, onChange, Modifier.testTag(tag), label = title)
        }
    }
    line("משמרת פעילה", "טיימר וסכום שנצבר בהתראה קבועה בזמן שהשעון רץ.", "service_notification_switch", serviceNotification) { viewModel.updateServiceNotificationEnabled(it) }
    KisDivider()
    line("הסיכום החודשי", "ב-30 לכל חודש (בפברואר ביום האחרון) בשעה 20:00: כמה עבדת וכמה הרווחת החודש. נגיעה בו פותחת את \"החודש שלך\". נשלח רק כשיש משמרות בחודש.",
        "notify_monthly_switch", notifyMonthly) { enable("monthly", it) }
    KisDivider()
    line("משמרת קבועה שהסתיימה", "כשמשמרת קבועה נגמרת: \"נרשמה\" עם כפתור \"לא עבדתי\". אם הרישום האוטומטי כבוי, השאלה \"עבדת?\" עם \"עבדתי\" ו\"לא עבדתי\".",
        "notify_plans_switch", notifyPlans) { enable("plans", it) }
    KisDivider()
    line("תעריף חסר", "לכל היותר פעם בשבוע, ורק כשיש משמרות בלי תעריף.", "notify_rate_switch", notifyRate) { enable("rate", it) }
    if (com.example.BuildConfig.LOCAL_DISTRIBUTION) {
        KisDivider()
        line("גרסה חדשה", "הודעה בטלפון כשיוצאת גרסה חדשה, פעם אחת לכל גרסה. ההתקנה תמיד באישור שלך.", "notify_updates_switch", notifyUpdates) { enable("updates", it) }
    }
    // Switched on but the phone does not allow notifications: say so, with one tap to the phone's own setting.
    var allowed by remember { mutableStateOf(com.example.PlanJobs.canNotify(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = com.example.PlanJobs.canNotify(context) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (denied || ((notifyMonthly || notifyRate || notifyPlans || notifyUpdates) && !allowed)) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
        .background(c.pendingSoft).kisClick {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        }.testTag("notify_blocked").padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("הטלפון חוסם את ההתראות של האפליקציה", style = KisType.caption, color = c.pending, modifier = Modifier.weight(1f))
        Text("להפעלה", style = KisType.label, color = c.pending)
    }
    KisDivider()
    line("שעות שקט", "תזכורת שנופלת בשעות האלה תגיע כשהן נגמרות.", "quiet_switch", quiet) { plan.quiet = it; quiet = it }
    if (quiet) Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeChip("משעה", quietFrom, Modifier.weight(1f), "quiet_from") { plan.quietFrom = it; quietFrom = it }
        TimeChip("עד שעה", quietTo, Modifier.weight(1f), "quiet_to") { plan.quietTo = it; quietTo = it }
    }
}

/**
 * Asked once (Android 13+, after the launch and the tour), before Android's own permission dialog: what the
 * notifications are for, in one line each. "לא עכשיו" never asks again; Settings → התראות always can.
 */
@Composable
fun NotificationsPrompt(onAllow: () -> Unit, onLater: () -> Unit) {
    val c = Kis.colors
    KisSheet(onDismiss = onLater, tag = "notifications_prompt") {
        Text("התראות מהאפליקציה?", style = KisType.title, color = c.ink)
        listOf("גרסה חדשה, עם הורדה בנגיעה אחת", "הסיכום החודשי, ב-30 לכל חודש", "משמרת קבועה שהסתיימה: נרשמה או לא").forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Workplaces[0].accent(c.dark)))
                Text(it, style = KisType.body, color = c.ink)
            }
        }
        Text("אפשר לשנות בכל רגע בהגדרות, בקבוצת התראות.", style = KisType.caption, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("לא עכשיו", onLater, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp, tag = "notifications_prompt_later")
            KisButton("להפעלה", onAllow, Modifier.weight(1f), height = 52.dp, tag = "notifications_prompt_allow")
        }
    }
}

/** Profile (bottom of Settings): first and last name, saved as typed. Only the first name is shown anywhere. */
@Composable
fun ProfileFields() {
    val settings = Kis.settings
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KisTextField(settings.name, settings::updateName, "שם פרטי", Modifier.fillMaxWidth().testTag("profile_name"))
        KisTextField(settings.lastName, settings::updateLastName, "שם משפחה", Modifier.fillMaxWidth().testTag("profile_last_name"))
    }
}
