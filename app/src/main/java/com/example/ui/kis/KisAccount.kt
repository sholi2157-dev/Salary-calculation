package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** 2.1: the AI intro before the sign-in is shown once per installation. */
object AiIntro {
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("ai_intro", android.content.Context.MODE_PRIVATE)
    fun seen(context: android.content.Context) = prefs(context).getBoolean("seen", false)
    fun markSeen(context: android.content.Context) { prefs(context).edit().putBoolean("seen", true).apply() }
}

/**
 * The sign-in screen. The first time it comes with the 2.1 intro: a few animated words about the AI connection
 * ([updated] says whether this phone had the app before), then a short "just ask" screen, and only then the Google
 * sign-in. What it is, the permissions and why it is safe live in Settings and behind the bell. [onLater] only exists while there is no internet.
 */
@Composable
fun SignInGate(onSignIn: suspend () -> String?, offline: Boolean, onLater: () -> Unit, updated: Boolean = true) {
    val c = Kis.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(if (AiIntro.seen(context)) 2 else 0) }
    androidx.activity.compose.BackHandler(enabled = step in 1..2) { step-- }
    LaunchedEffect(step) { if (step == 2) AiIntro.markSeen(context) }
    Box(Modifier.fillMaxSize().background(c.bg).testTag("signin")) {
        KisGlow(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (step) {
                0 -> {
                    // Centred when it fits, scrolls on a short screen.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                            IntroRise("חישוב שכר", 100, "intro_line_1", label = true)
                            IntroRise(if (updated) "גרסה 2.1" else "ברוכים הבאים", 300, "intro_version", label = true, muted = true)
                            IntroRise("כעת סוכן ה-AI שלך מכיר אותך ואת העבודה שלך.", 600, "intro_line_2", size = 36)
                            IntroRise("בקשו מהסוכן שלכם:", 1100, "intro_line_3", label = true)
                            IntroBubble("כמה הרווחתי החודש?", 1400, mine = true, small = true)
                            IntroBubble("עבדתי אתמול בכינוס השלוחים 15 שעות, ושניאור נגר ביקש ממני לרשום בהערה סידור כסאות. תוסיף לי בבקשה.", 1800, mine = true, small = true)
                            IntroRise("והוא יעשה זאת בשבילכם!", 2300, "intro_line_4", size = 26)
                            IntroAssistants(2700)
                        }
                    }
                    KisButton("הבא", { step = 1 }, Modifier.fillMaxWidth(), height = 56.dp, tag = "intro_next")
                }
                1 -> {
                    Spacer(Modifier.weight(1f))
                    IntroRise("פשוט מבקשים.", 100, "intro_how_title")
                    Spacer(Modifier.height(8.dp))
                    IntroBubble("כמה הרווחתי החודש?", 550, mine = true)
                    IntroBubble("2,140 ₪ על 15 משמרות.", 1000, mine = false)
                    IntroBubble("תרשום מחר 9 עד 5.", 1500, mine = true)
                    IntroBubble("רשמתי ✓", 1950, mine = false)
                    IntroRise("מתחברים פעם אחת, וזהו.", 2450, "intro_how_sub", small = true)
                    Spacer(Modifier.weight(1f))
                    KisButton("הבא", { step = 2 }, Modifier.fillMaxWidth(), height = 56.dp, tag = "intro_next_2")
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    KisLogo(Modifier.size(56.dp))
                    Text("מחברים את חשבון Google", style = KisType.page.copy(fontSize = 34.sp, lineHeight = 40.sp), color = c.ink, modifier = Modifier.semantics { heading() })
                    Text("פעם אחת, וכך האפליקציה, האתר והעוזר שלך רואים את אותן משמרות.", style = KisType.body, color = c.ink)
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
    }
}

/** A line of the intro that rises into place, like the 2.0 launch: a headline of [size] sp, or a smaller [label] line. */
@Composable
private fun IntroRise(text: String, delayMs: Long, tag: String, small: Boolean = false, label: Boolean = false, muted: Boolean = false, size: Int = 50) {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs); shown.animateTo(1f, androidx.compose.animation.core.tween(650)) }
    val style = when {
        small -> KisType.body.copy(fontSize = 18.sp, lineHeight = 26.sp)
        label -> KisType.body.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
        else -> KisType.hero.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp)
    }
    Text(text, style = style, color = if (small || muted) Kis.colors.muted else Kis.colors.ink,
        modifier = Modifier.fillMaxWidth().padding(top = if (small) 14.dp else 0.dp)
            .graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 28.dp.toPx() }.testTag(tag))
}

/** "מחובר עם" and the assistants, each in its own chip so no commas get lost between Hebrew and English. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroAssistants(delayMs: Long) {
    val c = Kis.colors
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs); shown.animateTo(1f, androidx.compose.animation.core.tween(500)) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 18.dp.toPx() }
        .testTag("intro_assistants"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("מחובר עם", style = KisType.caption, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ChatGPT", "Claude", "Grok", "Muse", "Instinct").forEach {
                Text(it, style = KisType.label, color = c.ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surface).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** A chat line in the intro's "פשוט מבקשים" screen: what you ask ([mine]) on one side, the assistant's answer on the other. */
@Composable
private fun IntroBubble(text: String, delayMs: Long, mine: Boolean, small: Boolean = false) {
    val c = Kis.colors
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs); shown.animateTo(1f, androidx.compose.animation.core.tween(450)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(text, style = KisType.body.copy(fontSize = if (small) 18.sp else 22.sp, lineHeight = if (small) 25.sp else 30.sp, fontWeight = FontWeight.SemiBold),
            color = if (mine) c.ink else c.onInk,
            modifier = Modifier.graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 18.dp.toPx() }
                .clip(RoundedCornerShape(22.dp)).background(if (mine) c.surface else c.ink).padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

/** What it is, how to connect, the permissions and why it is safe: in the intro and behind the bell's invitation. */
@Composable
fun AiExplainCards(withHow: Boolean = true) {
    IntroCard("מה זה", "מדברים עם העוזר בשפה רגילה: \"כמה הרווחתי החודש?\" או \"תוסיף משמרת מחר מ-9 עד 5 במסעדה\". הוא עונה ומעדכן לפי המשמרות שלך.", 0)
    if (withHow) IntroCard("איך מחברים", "בהגדרות העוזר מוסיפים חיבור (Connector) עם הכתובת שמופיעה בהגדרות ← עוזרי AI מחוברים. נפתח דף, מתחברים עם Google ובוחרים מה מותר לו.", 1)
    IntroCard("הרשאות", "לראות משמרות מותר תמיד. להוסיף, לערוך או למחוק רק אם הפעלת. בוחרים לכמה זמן: שעה, יום, 30 יום או עד שתנתק.", 2)
    IntroCard("בטוח", "העוזר רואה רק את המשמרות שלך ולא מקבל את הסיסמה. כל שינוי שלו נרשם ואפשר לבטל אותו, ומנתקים אותו בכל רגע.", 3)
}

@Composable
private fun IntroCard(title: String, body: String, index: Int) {
    val c = Kis.colors
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120L * index); shown.animateTo(1f, androidx.compose.animation.core.tween(450)) }
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 16.dp.toPx() }
        .clip(RoundedCornerShape(16.dp)).background(c.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = KisType.label, color = c.ink)
        Text(body, style = KisType.body, color = c.ink)
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
    var assistants by rememberSaveable { mutableStateOf(false) }
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
        KisButton("עוזרי AI מחוברים", { assistants = true }, Modifier.fillMaxWidth(), KisButtonKind.OUTLINE, height = 48.dp, tag = "account_assistants")
        KisButton("התנתקות", { leaving = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "account_signout")
    }
    if (assistants) AssistantsSheet(viewModel) { assistants = false }
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

private val PERM_LABEL = mapOf("shifts.read" to "קריאה", "shifts.add" to "הוספה", "shifts.edit" to "עריכה", "shifts.delete" to "מחיקה")
private val TOOL_LABEL = mapOf("add_shift" to "הוסיף", "update_shift" to "ערך", "delete_shift" to "מחק")
const val ASSISTANT_URL = "https://salary-calculation-eta.vercel.app/mcp"

/**
 * Personal AI assistants (ChatGPT, Claude and others) connected through the website: how to connect, who is
 * connected now, cutting one off, and undoing what one did. Same as the website (web/kis/account.js).
 */
@Composable
fun AssistantsSheet(viewModel: WorkViewModel, explain: Boolean = false, onDismiss: () -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<Pair<List<com.example.api.AssistantGrant>, List<com.example.api.AssistantAction>>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cutting by remember { mutableStateOf<com.example.api.AssistantGrant?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(round) { data = viewModel.assistantsOverview(); loaded = true }
    val date = remember { java.text.SimpleDateFormat("d.M HH:mm", java.util.Locale.ROOT) }
    KisSheet(onDismiss = onDismiss, tag = "assistants_sheet") {
        Text("עוזרי AI", style = KisType.title, color = c.ink, modifier = Modifier.semantics { heading() })
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (explain) AiExplainCards(withHow = false)
            else Text("עוזר מחובר יכול לענות על שאלות על המשמרות שלך, לסכם, ולפי מה שאישרת גם להוסיף, לערוך או למחוק. הוא רואה רק את המשמרות שלך.",
                style = KisType.caption, color = c.muted)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("איך מחברים", style = KisType.label, color = c.ink)
                Text("בהגדרות העוזר, בחיבורים (Connectors), מוסיפים חיבור חדש עם הכתובת:", style = KisType.caption, color = c.muted)
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(ASSISTANT_URL, style = KisType.body, color = c.ink, modifier = Modifier.testTag("assistants_url"))
                }
                KisButton(if (copied) "הועתק ✓" else "העתקת הכתובת", { KisShare.copy(context, ASSISTANT_URL, "כתובת לעוזר"); copied = true },
                    Modifier.fillMaxWidth(), KisButtonKind.OUTLINE, height = 44.dp, tag = "assistants_copy")
                Text("העוזר יפתח דף שבו מתחברים עם Google ובוחרים מה מותר לו ולכמה זמן.", style = KisType.caption, color = c.muted)
            }
            message?.let { Text(it, style = KisType.caption, color = c.danger, modifier = Modifier.testTag("assistants_message")) }
            val (grants, log) = data ?: (emptyList<com.example.api.AssistantGrant>() to emptyList())
            when {
                !loaded -> Text("טוען…", style = KisType.caption, color = c.muted)
                data == null -> Text("אין חיבור כרגע. נסה שוב בעוד רגע.", style = KisType.caption, color = c.muted, modifier = Modifier.testTag("assistants_offline"))
                else -> {
                    Text("מחוברים", style = KisType.label, color = c.ink)
                    if (grants.isEmpty()) Text("אין עוזרים מחוברים.", style = KisType.caption, color = c.muted, modifier = Modifier.testTag("assistants_none"))
                    grants.forEach { g ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(g.name.ifBlank { "עוזר AI" }, style = KisType.label, color = c.ink)
                                Text((g.scopes.mapNotNull { PERM_LABEL[it] } + (g.until?.let { "עד ${KisShare.shortDate(it)}" } ?: "עד שתנתק")).joinToString(" · "),
                                    style = KisType.caption, color = c.muted)
                            }
                            KisChip("ניתוק", onClick = { cutting = g }, warn = true, tag = "assistant_cut")
                        }
                    }
                    if (log.isNotEmpty()) Text("פעולות אחרונות", style = KisType.label, color = c.ink)
                    log.forEach { a ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.client} ${TOOL_LABEL[a.tool].orEmpty()} · ${date.format(java.util.Date(a.at))}", style = KisType.caption, color = c.muted)
                                Text(describe(a.type, a.after ?: a.before), style = KisType.body, color = c.ink)
                            }
                            if (a.undone) Text("בוטל", style = KisType.caption, color = c.muted)
                            else KisChip("ביטול", onClick = {
                                scope.launch {
                                    message = if (viewModel.undoAssistant(a)) null else "הביטול לא הושלם. אולי המשמרת השתנתה מאז"
                                    round++
                                }
                            }, tag = "assistant_undo")
                        }
                    }
                }
            }
        }
    }
    cutting?.let { g ->
        KisSheet(onDismiss = { cutting = null }, tag = "assistant_cut_sheet") {
            Text("לנתק את ${g.name.ifBlank { "העוזר" }}?", style = KisType.title, color = c.ink)
            Text("הוא לא יוכל יותר לראות או לשנות משמרות. מה שכבר שינה נשאר, ואפשר לבטל אותו כאן.", style = KisType.body, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KisButton("ביטול", { cutting = null }, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp)
                KisButton("ניתוק", {
                    cutting = null
                    scope.launch { message = if (viewModel.cutOffAssistant(g.id)) null else "הניתוק לא הושלם. נסה שוב"; round++ }
                }, Modifier.weight(1f), KisButtonKind.DANGER, height = 52.dp, tag = "assistant_cut_ok")
            }
        }
    }
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
