@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.*
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import kotlinx.coroutines.delay

/** Device-local, deliberately separate from payroll backups and account/category preferences. */
class WorkOnboardingStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("work_onboarding", Context.MODE_PRIVATE)
    val completedVersion get() = prefs.getInt("completedOnboardingVersion", 0)
    fun initialize(): Boolean {
        if (!prefs.contains("installationClassified")) {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // Capture BEFORE opening Room. An upgrade (even an empty one) or restored local
            // storage is an established installation; never force the initial tour on it.
            val established = info.lastUpdateTime > info.firstInstallTime ||
                context.databaseList().any { it.startsWith("sholi_") } ||
                context.filesDir.resolve("datastore").listFiles()?.isNotEmpty() == true ||
                context.getSharedPreferences("personal_ai_setup", 0).all.isNotEmpty() ||
                context.getSharedPreferences("active_shift_prefs", 0).all.isNotEmpty()
            check(prefs.edit().putBoolean("installationClassified", true)
                .putInt("completedOnboardingVersion", if (established) ONBOARDING_VERSION else 0).commit())
        }
        return completedVersion < ONBOARDING_VERSION
    }
    fun complete() { check(prefs.edit().putInt("completedOnboardingVersion", maxOf(completedVersion, ONBOARDING_VERSION)).remove("tourPendingUid").commit()) }
    /**
     * F12 (2.3.1): with Google sign-in the tour belongs to the account, not the phone. It opens once, on a new account's
     * first successful connect, and never again except from "הצגת ההדרכה מחדש". The first time an account is seen here
     * decides it: a brand-new account gets the tour (kept pending until it is finished or skipped, so closing the app
     * mid-way brings it back once); an account that already existed (an update, another phone, the site) never does.
     */
    fun claimAccount(uid: String, createdAt: Long?, lastSignInAt: Long?): Boolean {
        val seen = prefs.getStringSet("tourAccounts", emptySet()).orEmpty()
        if (uid in seen) return prefs.getString("tourPendingUid", null) == uid
        val fresh = isNewAccount(createdAt, lastSignInAt)
        check(prefs.edit().putStringSet("tourAccounts", seen + uid).apply { if (fresh) putString("tourPendingUid", uid) }.commit())
        return fresh
    }
    /** The 2.0 launch screen is shown once to everyone, new or updated (see KisLaunch). */
    val launchPending get() = prefs.getInt("launchedVersion", 0) < LAUNCH_VERSION
    fun launched() { check(prefs.edit().putInt("launchedVersion", LAUNCH_VERSION).commit()) }
}

const val LAUNCH_VERSION = 2

const val ONBOARDING_VERSION = 1

/** A Google account on its very first sign-in to the app: Firebase stamps both times at once (same rule on the site). */
const val NEW_ACCOUNT_WINDOW_MS = 120_000L
fun isNewAccount(createdAt: Long?, lastSignInAt: Long?): Boolean =
    createdAt != null && lastSignInAt != null && createdAt > 0 && kotlin.math.abs(lastSignInAt - createdAt) <= NEW_ACCOUNT_WINDOW_MS

/** With Google sign-in on, the tour follows the account (claimAccount); without it (or when sign-in is skipped), the installation. */
fun tourFollowsAccount(context: Context): Boolean = com.example.BuildConfig.GOOGLE_SIGN_IN_ENABLED && com.example.BuildConfig.ACCOUNTS_ENABLED &&
    !com.example.data.WorkAccountBinding.signInSkipped(context)

enum class CoachScreen { HOME, FORM, AI, HISTORY, SHARE, CATEGORIES, API }
enum class CoachTarget { SHIFT, FIELDS, AI, HISTORY, SHARE, CATEGORIES, API }
data class CoachStep(val screen: CoachScreen, val target: CoachTarget?, val title: String, val body: String)
val workOnboardingSteps = listOf(
    CoachStep(CoachScreen.HOME, null, "ברוכים הבאים לחישוב שכר", "כאן רושמים משמרות, רואים כמה הרווחת ושולחים דוח מסודר למעסיק. דקה אחת, ומכירים את העיקר."),
    CoachStep(CoachScreen.HOME, CoachTarget.SHIFT, "רישום משמרת", "עבדת היום? לוחצים על + למטה ורושמים. מתחילים לעבוד עכשיו? ״אני מתחיל עכשיו״ מפעיל שעון, ובסוף עוצרים אותו."),
    CoachStep(CoachScreen.FORM, CoachTarget.FIELDS, "השעות והסכום", "השעות כבר ממולאות לפי ההרגל שלך. משנים בסרגל או בהקלדה, והסכום מתעדכן מיד. בכפתור + מוסיפים בונוס, החזר הוצאות או הפסקה."),
    CoachStep(CoachScreen.AI, CoachTarget.AI, "או פשוט כותבים", "למשל ״אתמול מ־9 עד 5 במסעדה״. לוחצים ״פענוח״, בודקים ושומרים. צריך לזה מפתח AI אישי, מוסיפים אותו בהגדרות."),
    CoachStep(CoachScreen.HISTORY, CoachTarget.HISTORY, "כל המשמרות", "מסודרות לפי חודש ושבוע, עם חיפוש וסינון. לחיצה ארוכה בוחרת כמה משמרות יחד: לשתף, לסמן ששולם או להשלים תעריף."),
    CoachStep(CoachScreen.SHARE, CoachTarget.SHARE, "דוח למעסיק", "נגיעה במשמרת פותחת את הפרטים. ״שיתוף״ שולח סיכום מסודר בוואטסאפ, ו״העתקה״ מעתיקה אותו. שם גם מסמנים ששולם."),
    CoachStep(CoachScreen.CATEGORIES, CoachTarget.CATEGORIES, "כמה מקומות עבודה", "לכל מקום תעריף, מטבע וצבע משלו. מקום חדש מוסיפים ישר בטופס המשמרת, ומשמרות קבועות מגדירים כאן."),
    CoachStep(CoachScreen.API, CoachTarget.API, "עזרה וכלים", "כאן מפעילים רישום במילים (AI), וכאן אפשר לראות את ההדרכה הזו שוב."),
    CoachStep(CoachScreen.HOME, null, "זהו, אפשר להתחיל", "רושמים את המשמרת הראשונה, והחשבון עלינו.")
)

@Stable
class OnboardingController(private val store: WorkOnboardingStore, initialStep: Int = -1) {
    var index by mutableIntStateOf(initialStep); private set
    val active get() = index in workOnboardingSteps.indices
    val step get() = workOnboardingSteps.getOrNull(index)
    fun replay() { index = 0 }
    fun back() { if (index > 0) index-- }
    fun next() { if (index == workOnboardingSteps.lastIndex) finish() else if (active) index++ }
    fun finish() { store.complete(); index = -1 }
}

@Composable
fun rememberOnboardingController(): OnboardingController {
    val context = LocalContext.current
    val store = remember { WorkOnboardingStore(context) }
    // initialize() is also called in Activity before Room; tests may host this independently.
    // With Google sign-in the tour waits for a new account's first connect instead (MainAppContent, claimAccount).
    val auto = remember { store.initialize() && !tourFollowsAccount(context) }
    // Save the controller's current value directly. An asynchronous mirror can retain
    // the replay step when Android saves Activity state immediately after Skip.
    return rememberSaveable(saver = Saver(
        save = { it.index },
        restore = { OnboardingController(store, it) }
    )) { OnboardingController(store, if (auto) 0 else -1) }
}

val CoachTargetKey = SemanticsPropertyKey<CoachTarget>("Tutorial target")
var SemanticsPropertyReceiver.tutorialTarget by CoachTargetKey

class CoachTargets { val bounds = mutableStateMapOf<CoachTarget, Rect>() }
val LocalCoachTargets = staticCompositionLocalOf<CoachTargets?> { null }
val LocalCoachStep = staticCompositionLocalOf<CoachStep?> { null }

/** No layout or behavior change outside the intentionally isolated tutorial composition. */
fun Modifier.coachTarget(target: CoachTarget): Modifier = composed {
    val registry = LocalCoachTargets.current
    val step = LocalCoachStep.current
    if (registry == null || step?.target != target) return@composed this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(target) { onDispose { registry.bounds.remove(target) } }
    LaunchedEffect(step) {
        // Allow expansion/navigation to settle, then bring the real control into its viewport.
        delay(240)
        requester.bringIntoView()
    }
    this.bringIntoViewRequester(requester).onGloballyPositioned {
        registry.bounds[target] = it.boundsInRoot()
    }.semantics { tutorialTarget = target }
}

/** Both touch and accessibility are blocked on the demonstration's actual UI.
 * Normal app remains composed underneath, so its scroll, selection and form drafts survive replay.
 */
private fun Modifier.readOnlyTutorial() = clearAndSetSemantics { }.pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun WorkOnboarding(controller: OnboardingController, viewModel: WorkViewModel, categories: List<WorkCategory>, entries: List<WorkEntry>) {
    if (!controller.active) return
    val step = controller.step!!
    val targets = remember { CoachTargets() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val kisSettings = com.example.ui.kis.Kis.settings
    val dark = com.example.ui.kis.Kis.colors.dark
    LaunchedEffect(Unit) { keyboard?.hide() }
    // Constrain the fullscreen dialog to the host viewport. Android's WRAP_CONTENT
    // dialog probing can otherwise measure fillMaxSize beyond the physical screen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val hostWidth = maxWidth
    val hostHeight = maxHeight
    val hostInsets = WindowInsets.safeDrawing.asPaddingValues()
    Dialog(onDismissRequest = { if (controller.index > 0) controller.back() else controller.finish() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides density, LocalCoachTargets provides targets, LocalCoachStep provides step) {
            BackHandler { if (controller.index > 0) controller.back() else controller.finish() }
            var cardHeight by remember { mutableStateOf(260.dp) }
            var rootOrigin by remember { mutableStateOf(Offset.Zero) }
            com.example.ui.kis.KisTheme(kisSettings, darkOverride = dark) {
            val c = com.example.ui.kis.Kis.colors
            Box(Modifier.requiredSize(hostWidth, hostHeight).background(c.bg).padding(hostInsets).testTag("onboarding_root")) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
                    Column(Modifier.fillMaxSize().readOnlyTutorial()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (step.target != null && step.target != CoachTarget.HISTORY) cardHeight + 20.dp else 0.dp)) {
                            // Real screens with separate state. No save/start/payment/share callback does anything here.
                            key(step.screen) {
                                val sample = entries.firstOrNull()?.copy(isGroupShift = false, groupWorkersJson = "") ?: WorkEntry(id = -1, category = categories.firstOrNull()?.name ?: "עבודה לדוגמה",
                                    date = System.currentTimeMillis(), isTimeRange = true, startTime = "09:00", endTime = "17:00", hours = 8.0, hourlyRate = 40.0, totalEarnings = 320.0)
                                when (step.screen) {
                                    CoachScreen.HOME -> com.example.ui.kis.KisHome(entries, categories, null, 0.dp, {}, {}, {}, {}, {}, {}, {}, {})
                                    CoachScreen.FORM, CoachScreen.AI -> {
                                        val cat = categories.firstOrNull()
                                        com.example.ui.kis.KisEditorPage(viewModel, com.example.ui.kis.EntryDraft.new(cat, cat?.let { viewModel.categoryCurrency(it.name) } ?: "₪"), null, categories, entries, emptyList(), onClose = {}, onSaved = { _, _ -> }, onDeleted = {}, startInAi = step.screen == CoachScreen.AI, interactive = false)
                                    }
                                    CoachScreen.HISTORY -> com.example.ui.kis.KisHistory(remember { com.example.ui.kis.HistoryUi() }, false, entries, categories, viewModel, 0.dp, {}, {}, {})
                                    CoachScreen.SHARE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                        if (entries.isEmpty()) Text("משמרת לדוגמה בלבד · לא נשמרת", color = c.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 12.dp))
                                        com.example.ui.kis.ShiftDetailContent(sample, categories, onPaid = {}, onEdit = {}, onShare = {}, onCopy = {}, onDelete = {}, onCompleteRate = {}, group = {})
                                    }
                                    CoachScreen.CATEGORIES, CoachScreen.API -> com.example.ui.kis.KisSettingsPage(viewModel, categories, onClose = {}, onReplayTour = {})
                                }
                            }
                        }
                        if (step.screen != CoachScreen.FORM && step.screen != CoachScreen.AI) Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
                            com.example.ui.kis.PillNav(when (step.screen) { CoachScreen.HISTORY, CoachScreen.SHARE -> 1; CoachScreen.CATEGORIES, CoachScreen.API -> 2; else -> 0 }, {}, {}, {})
                        }
                    }
                    val rect = step.target?.let { targets.bounds[it] }?.translate(-rootOrigin)
                    CoachOverlay(controller, rect, Modifier.fillMaxSize(), onCardHeight = { cardHeight = with(density) { it.toDp() } })
                }
            }
            }
        }
    }
    }
}

@Composable
internal fun CoachOverlay(controller: OnboardingController, target: Rect?, modifier: Modifier = Modifier, onCardHeight: (Int) -> Unit = {}) {
    val step = controller.step ?: return
    // Do not point at an absent/off-screen target during scroll or initial layout.
    val valid = target?.takeIf { it.width > 1f && it.height > 1f }
    val left by animateFloatAsState(valid?.left ?: 0f, tween(160), label = "spotlight left")
    val top by animateFloatAsState(valid?.top ?: 0f, tween(160), label = "spotlight top")
    val right by animateFloatAsState(valid?.right ?: 0f, tween(160), label = "spotlight right")
    val bottom by animateFloatAsState(valid?.bottom ?: 0f, tween(160), label = "spotlight bottom")
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val hole = Rect(left, top, right, bottom).inflate(3.dp.toPx())
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (valid != null) addRoundRect(RoundRect(hole, CornerRadius(12.dp.toPx())))
            }
            drawPath(path, Color.Black.copy(alpha = .68f))
            if (valid != null) drawRoundRect(Color(0xFFFFC94D), hole.topLeft, hole.size, CornerRadius(12.dp.toPx()), style = Stroke(2.5.dp.toPx()))
        }
        // Full-window blocker also covers the highlighted hole. The only actionable controls
        // are the coach card; this never forwards a tap to a real Save/Share/Start button.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed }) } })
        val centered = step.target == null
        val ready = centered || valid != null
        val cardModifier = Modifier.align(if (centered) Alignment.Center else Alignment.BottomCenter)
            .padding(horizontal = 16.dp).padding(bottom = if (centered) 0.dp else 88.dp)
            .widthIn(max = 480.dp).fillMaxWidth()
            .heightIn(max = if (centered) maxHeight - 32.dp else minOf(260.dp, maxHeight - 112.dp))
            .onSizeChanged { onCardHeight(it.height) }.testTag("onboarding_card")
        Surface(modifier = cardModifier.semantics { isTraversalGroup = true; liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(22.dp), color = com.example.ui.kis.Kis.colors.surface, shadowElevation = 12.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${controller.index + 1} מתוך ${workOnboardingSteps.size}", color = com.example.ui.kis.Kis.colors.muted, style = com.example.ui.kis.KisType.label, modifier = Modifier.weight(1f).testTag("onboarding_progress"))
                    TextButton(onClick = controller::finish, modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_skip")) { Text("דלג", color = com.example.ui.kis.Kis.colors.muted) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, color = com.example.ui.kis.Kis.colors.ink, style = com.example.ui.kis.KisType.title.copy(fontSize = if (centered) 26.sp else 21.sp), modifier = Modifier.semantics { heading() })
                    Text(step.body, color = com.example.ui.kis.Kis.colors.ink, style = com.example.ui.kis.KisType.body)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.example.ui.kis.KisButton(if (controller.index == workOnboardingSteps.lastIndex) "יאללה, מתחילים" else "הבא", controller::next, Modifier.weight(1f),
                        enabled = ready, height = 50.dp, tag = "onboarding_next")
                    if (controller.index > 0) TextButton(onClick = controller::back, modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_back")) { Text("הקודם", color = com.example.ui.kis.Kis.colors.ink) }
                }
            }
        }
    }
}
