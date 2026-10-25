package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WorkBackup
import com.example.data.WorkSyncConflict
import com.example.ui.WorkViewModel
import kotlinx.coroutines.launch

/*
 * 2.1: the Google account. Everyone signs in once (new and existing installs); what is already on the phone stays and
 * goes to the account. Without internet the app keeps working and asks again later. The same rules as the website
 * (web/kis/account.js).
 */

/** The sign-in screen. [onLater] only exists while there is no internet. */
@Composable
fun SignInGate(onSignIn: suspend () -> String?, offline: Boolean, onLater: () -> Unit) {
    val c = Kis.colors
    val scope = rememberCoroutineScope()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.bg).testTag("signin")) {
        KisGlow(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            KisLogo(Modifier.size(56.dp))
            Text("מתחברים עם Google", style = KisType.page.copy(fontSize = 34.sp, lineHeight = 40.sp), color = c.ink, modifier = Modifier.semantics { heading() })
            Text("כך המשמרות נשמרות בחשבון שלך ומסתנכרנות בין האפליקציה לאתר.", style = KisType.body, color = c.ink)
            Text("מה שכבר רשום בטלפון נשאר ועובר לחשבון.", style = KisType.caption, color = c.muted)
            Spacer(Modifier.weight(1f))
            if (offline) Text("אין חיבור לאינטרנט. אפשר להמשיך לעבוד בטלפון, וכשהחיבור יחזור נבקש להתחבר.", style = KisType.caption, color = c.muted,
                modifier = Modifier.testTag("signin_offline"))
            error?.takeIf { it.isNotEmpty() }?.let { Text(it, style = KisType.caption, color = c.danger, modifier = Modifier.testTag("signin_error")) }
            KisButton(if (busy) "מתחבר…" else "המשך עם Google", {
                if (!busy) scope.launch { busy = true; error = null; error = onSignIn(); busy = false }
            }, Modifier.fillMaxWidth(), enabled = !offline && !busy, height = 56.dp, tag = "signin_google")
            if (offline) KisButton("להמשיך בינתיים", onLater, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "signin_later")
        }
    }
}

/** Settings → חשבון: who is signed in, how the sync is doing, conflicts waiting for a choice, and signing out. */
@Composable
fun AccountRows(viewModel: WorkViewModel, onSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    val session by viewModel.currentUserSession.collectAsStateWithLifecycle()
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    val last by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val conflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    val user = session
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("account_card"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (user == null) {
            Text("לא מחובר", style = KisType.label, color = c.ink)
            Text("הנתונים שמורים רק בטלפון הזה", style = KisType.caption, color = c.muted)
            KisButton("התחברות עם Google", onSignIn, Modifier.fillMaxWidth(), KisButtonKind.OUTLINE, height = 48.dp, tag = "account_signin")
            return@Column
        }
        Text(user.displayName ?: user.email.orEmpty(), style = KisType.label, color = c.ink)
        if (user.email != null && user.email != user.displayName) Text(user.email, style = KisType.caption, color = c.muted)
        val line = when (status) {
            WorkViewModel.SyncStatus.SYNCING -> "מסנכרן…"
            WorkViewModel.SyncStatus.OFFLINE -> "אין חיבור. השינויים יישלחו כשהחיבור יחזור"
            WorkViewModel.SyncStatus.ERROR -> "הסנכרון לא הצליח. ננסה שוב בקרוב"
            else -> last?.let { "מסונכרן · ${KisShare.shortDate(it)} ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(it))}" } ?: "מסונכרן"
        }
        Text(line, style = KisType.caption, color = c.muted, modifier = Modifier.testTag("account_status"))
        if (conflicts > 0) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.pendingSoft).kisClick { reviewing = true }
            .testTag("account_conflicts").padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(if (conflicts == 1) "שינוי אחד נעשה בשני מקומות. לבחירה" else "$conflicts שינויים נעשו בשני מקומות. לבחירה", style = KisType.caption, color = c.pending)
        }
        KisButton("התנתקות", { leaving = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "account_signout")
    }
    if (leaving) KisSheet(onDismiss = { leaving = false }, tag = "account_signout_sheet") {
        Text("להתנתק מהחשבון?", style = KisType.title, color = c.ink)
        Text("הנתונים נשארים בחשבון ובטלפון. כדי להמשיך תצטרך להתחבר שוב.", style = KisType.body, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("ביטול", { leaving = false }, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp)
            KisButton("התנתקות", { leaving = false; if (viewModel.signOut(context)) onSignedOut() }, Modifier.weight(1f), KisButtonKind.DANGER, height = 52.dp, tag = "account_signout_ok")
        }
    }
    if (reviewing) ConflictsSheet(viewModel) { reviewing = false }
}

/** One change made in two places: both versions side by side, and the user keeps one. Nothing is lost until then. */
@Composable
private fun ConflictsSheet(viewModel: WorkViewModel, onDismiss: () -> Unit) {
    val c = Kis.colors
    var list by remember { mutableStateOf<List<WorkSyncConflict>?>(null) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) { list = viewModel.syncConflictList().also { if (it.isEmpty() && round > 0) onDismiss() } }
    KisSheet(onDismiss = onDismiss, tag = "conflicts_sheet") {
        Text("שינוי בשני מקומות", style = KisType.title, color = c.ink)
        Text("אותו פריט השתנה כאן וגם במקום אחר. איזו גרסה לשמור?", style = KisType.caption, color = c.muted)
        val first = list?.firstOrNull()
        if (first != null) Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("בטלפון הזה" to first.mine, "בחשבון" to first.theirs).forEach { (title, payload) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(12.dp)) {
                    Text(title, style = KisType.micro, color = c.muted)
                    Text(describe(first.type, payload), style = KisType.body, color = c.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("מהחשבון", { viewModel.resolveSyncConflict(first.syncId, false) { round++ } }, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp, tag = "conflict_theirs")
                KisButton("מהטלפון", { viewModel.resolveSyncConflict(first.syncId, true) { round++ } }, Modifier.weight(1f), height = 52.dp, tag = "conflict_mine")
            }
        }
    }
}

private fun describe(type: String, payload: String?): String {
    if (payload == null) return "נמחק"
    return runCatching {
        val d = WorkBackup.decode(payload)
        when (type) {
            "entry" -> d.entries.single().let { "${it.category} · ${KisShare.shortDate(it.date)}" +
                (if (it.startTime != null && it.endTime != null) " · ${it.startTime}–${it.endTime}" else " · ${it.hours} שעות") +
                (if (it.payKnown) " · ${it.totalEarnings} ${it.currency}" else "") + (if (it.notes.isNotBlank()) "\n${it.notes}" else "") }
            "category" -> d.categories.single().let { "${it.name} · ${it.defaultRate} לשעה" }
            "worker" -> d.workers.single().name
            "series" -> d.series.single().let { "משמרת קבועה · ${it.category} · ${it.startTime}–${it.endTime}" }
            "exception" -> d.exceptions.single().let { "יום במשמרת קבועה · ${it.date}" + if (it.cancelled) " · בוטל" else "" }
            else -> d.localPreferences.entries.single().let { "הגדרה · ${it.value}" }
        }
    }.getOrDefault("פריט")
}
