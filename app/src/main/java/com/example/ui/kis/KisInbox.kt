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
 * Profile and notifications live behind two icons at the top-left of Home, not inside Settings.
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

/** Top-left of Home: the bell (a dot when something new arrived) and the profile. */
@Composable
fun HeaderActions(unread: Boolean, onBell: () -> Unit, onProfile: () -> Unit) {
    val c = Kis.colors
    val name = Kis.settings.name.trim()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).kisClick(onClick = onBell)
            .semantics { contentDescription = if (unread) "התראות · יש חדשות" else "התראות" }.testTag("home_bell"), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Notifications, null, tint = c.ink, modifier = Modifier.size(22.dp))
            if (unread) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(9.dp).clip(CircleShape).background(Workplaces[0].accent(c.dark)).testTag("home_bell_dot"))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (name.isEmpty()) c.surface else Workplaces[0].accent(c.dark))
            .border(1.dp, if (name.isEmpty()) c.line else Workplaces[0].accent(c.dark), CircleShape).kisClick(onClick = onProfile)
            .semantics { contentDescription = "פרופיל" }.testTag("home_profile"), contentAlignment = Alignment.Center) {
            if (name.isEmpty()) Icon(Icons.Outlined.Person, null, tint = c.ink, modifier = Modifier.size(22.dp))
            else Text(name.take(1), style = KisType.label.copy(fontSize = 17.sp), color = Workplaces[0].accentInk(c.dark))
        }
    }
}

/** The name, typed once. Afterwards it is just shown, with an edit for a typo. */
@Composable
fun ProfileSheet(onDismiss: () -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    var editing by rememberSaveable { mutableStateOf(settings.name.isBlank()) }
    var text by rememberSaveable { mutableStateOf(settings.name) }
    KisSheet(onDismiss = onDismiss, tag = "profile_sheet") {
        Text("פרופיל", style = KisType.title, color = c.ink)
        if (editing) {
            KisTextField(text, { text = it.take(40) }, "השם שלך", Modifier.fillMaxWidth().testTag("profile_name"))
            KisButton("שמירה", { settings.updateName(text); editing = false; onDismiss() }, Modifier.fillMaxWidth(), height = 48.dp,
                enabled = text.isNotBlank(), tag = "profile_save")
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Workplaces[0].accent(c.dark)), contentAlignment = Alignment.Center) {
                Text(settings.name.trim().take(1), style = KisType.title, color = Workplaces[0].accentInk(c.dark))
            }
            Text(settings.name, style = KisType.title, color = c.ink, modifier = Modifier.weight(1f).testTag("profile_shown"))
            KisChip("עריכה", onClick = { text = settings.name; editing = true }, tag = "profile_edit")
        }
    }
}

/** What arrived (the monthly summary opens again), then the notification switches. */
@Composable
fun NotificationsSheet(viewModel: WorkViewModel, onMonth: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    val plan = viewModel.planSettings
    val recent = remember { plan.recent }
    LaunchedEffect(Unit) { plan.seenAt = System.currentTimeMillis() }
    val serviceNotification by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    var notifyMonthly by remember { mutableStateOf(plan.notifyMonthly) }
    var notifyRate by remember { mutableStateOf(plan.notifyRate) }
    var quiet by remember { mutableStateOf(plan.quiet) }
    var quietFrom by remember { mutableStateOf(plan.quietFrom) }
    var quietTo by remember { mutableStateOf(plan.quietTo) }
    var denied by remember { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    fun turnOn(kind: String) {
        denied = false
        if (kind == "monthly") { plan.notifyMonthly = true; notifyMonthly = true } else { plan.notifyRate = true; notifyRate = true }
    }
    // Without permission the switch stays off.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> asking?.let { if (granted) turnOn(it) else denied = true }; asking = null }
    fun enable(kind: String, on: Boolean) {
        if (!on) { if (kind == "monthly") { plan.notifyMonthly = false; notifyMonthly = false } else { plan.notifyRate = false; notifyRate = false }; return }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) { asking = kind; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        else turnOn(kind)
    }
    @Composable fun line(title: String, info: String?, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
            if (info != null) InfoDot(title, info)
            Spacer(Modifier.weight(1f))
            KisSwitch(checked, onChange, Modifier.testTag(tag), label = title)
        }
    }
    KisSheet(onDismiss = onDismiss, tag = "notifications_sheet") {
        Text("התראות", style = KisType.title, color = c.ink)
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recent.isEmpty()) Text("אין התראות", style = KisType.body, color = c.muted, modifier = Modifier.padding(vertical = 6.dp).testTag("notifications_empty"))
            recent.forEachIndexed { i, n ->
                val month = if (n.kind == "monthly") monthKeyOf(n.month) else null
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(c.bg)
                    .then(if (month != null) Modifier.kisClick { onMonth(month) } else Modifier).testTag("recent_note_$i").padding(12.dp)) {
                    Text(n.title, style = KisType.label, color = c.ink)
                    Text(n.text, style = KisType.caption, color = c.muted)
                    Text(KisShare.shortDate(n.time), style = KisType.micro, color = c.muted)
                }
            }
            KisDivider(Modifier.padding(vertical = 6.dp))
            line("משמרת פעילה", "טיימר וסכום שנצבר בהתראה קבועה בזמן שהשעון רץ.", "service_notification_switch", serviceNotification) { viewModel.updateServiceNotificationEnabled(it) }
            line("הסיכום החודשי", "פעם בחודש, בתחילת החודש: כמה עבדת וכמה הרווחת. נגיעה בו פותחת את \"החודש שלך\".", "notify_monthly_switch", notifyMonthly) { enable("monthly", it) }
            line("תעריף חסר", "לכל היותר פעם בשבוע, ורק כשיש משמרות בלי תעריף.", "notify_rate_switch", notifyRate) { enable("rate", it) }
            if (denied) Text("אין הרשאת התראות בטלפון", style = KisType.caption, color = c.pending, modifier = Modifier.testTag("notify_denied"))
            else if ((notifyMonthly || notifyRate) && !com.example.PlanJobs.canNotify(context)) Text("ההתראות חסומות בהגדרות הטלפון", style = KisType.caption, color = c.pending,
                modifier = Modifier.testTag("notify_blocked"))
            line("שעות שקט", "תזכורת שנופלת בשעות האלה תגיע כשהן נגמרות.", "quiet_switch", quiet) { plan.quiet = it; quiet = it }
            if (quiet) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip("משעה", quietFrom, Modifier.weight(1f), "quiet_from") { plan.quietFrom = it; quietFrom = it }
                TimeChip("עד שעה", quietTo, Modifier.weight(1f), "quiet_to") { plan.quietTo = it; quietTo = it }
            }
        }
    }
}
