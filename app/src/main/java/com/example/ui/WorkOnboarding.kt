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
    fun complete() { check(prefs.edit().putInt("completedOnboardingVersion", maxOf(completedVersion, ONBOARDING_VERSION)).commit()) }
}

const val ONBOARDING_VERSION = 1

enum class CoachScreen { HOME, FORM, AI, HISTORY, SHARE, CATEGORIES, API }
enum class CoachTarget { SHIFT, FIELDS, AI, HISTORY, SHARE, CATEGORIES, API }
data class CoachStep(val screen: CoachScreen, val target: CoachTarget?, val title: String, val body: String)
val workOnboardingSteps = listOf(
    CoachStep(CoachScreen.HOME, null, "ברוכים הבאים לחישוב שכר", "רושמים משמרות, מחשבים שכר ומשתפים בקלות. בוא נכיר את המקומות החשובים."),
    CoachStep(CoachScreen.HOME, CoachTarget.SHIFT, "כאן מתחילים", "״דיווח חדש״ לרישום שעות שכבר עבדת. ״התחל משמרת פעילה״ מפעיל טיימר בזמן העבודה."),
    CoachStep(CoachScreen.FORM, CoachTarget.FIELDS, "הפרטים שלך, החישוב שלנו", "ממלאים תאריך ושעות, הפסקה, תעריף ומטבע; בוחרים קטגוריה והערה אם צריך. הסכום מחושב אוטומטית."),
    CoachStep(CoachScreen.AI, CoachTarget.AI, "אפשר גם במילים", "כותבים או מכתיבים, עורכים את הטקסט ולוחצים ״פענח עם AI״. בודקים את התוצאה ורק אז שומרים."),
    CoachStep(CoachScreen.HISTORY, CoachTarget.HISTORY, "כל המשמרות במקום אחד", "כאן רואים סכומים, מחפשים ומסננים. לחיצה ארוכה בוחרת כמה משמרות לפעולה משותפת."),
    CoachStep(CoachScreen.SHARE, CoachTarget.SHARE, "שולחים סיכום מסודר", "פותחים משמרת ולוחצים על שיתוף. השעות והסכום מוכנים לשליחה, למשל בוואטסאפ. בהדרכה לא נשלח דבר."),
    CoachStep(CoachScreen.CATEGORIES, CoachTarget.CATEGORIES, "עובד בכמה מקומות?", "בהגדרות יוצרים קטגוריה לכל עבודה, עם תעריף ומטבע ברירת מחדל: שקל או דולר. אפשר לבחור מטבע גם בכל משמרת."),
    CoachStep(CoachScreen.API, CoachTarget.API, "רוצה להשתמש ב־AI?", "בהגדרות, תחת ״מערכת ומשוב״, מוסיפים מפתח API אישי של ג׳מיני. שאר האפליקציה עובדת גם בלי AI."),
    CoachStep(CoachScreen.HOME, null, "זה הכול. אתה מוכן.", "מוסיפים משמרת — והחישובים עלינו. ההדרכה תמיד זמינה שוב בהגדרות.")
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
    val auto = remember { store.initialize() }
    var savedIndex by rememberSaveable { mutableIntStateOf(if (auto) 0 else -1) }
    val controller = remember { OnboardingController(store, savedIndex) }
    LaunchedEffect(controller.index) { savedIndex = controller.index }
    return controller
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOnboarding(controller: OnboardingController, viewModel: WorkViewModel, categories: List<WorkCategory>, entries: List<WorkEntry>) {
    if (!controller.active) return
    val step = controller.step!!
    val targets = remember { CoachTargets() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    Dialog(onDismissRequest = { if (controller.index > 0) controller.back() else controller.finish() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides density, LocalCoachTargets provides targets, LocalCoachStep provides step) {
            BackHandler { if (controller.index > 0) controller.back() else controller.finish() }
            var cardHeight by remember { mutableStateOf(260.dp) }
            var rootOrigin by remember { mutableStateOf(Offset.Zero) }
            com.example.ui.theme.MyApplicationTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("onboarding_root")) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
                    Column(Modifier.fillMaxSize().readOnlyTutorial()) {
                        TopAppBar(title = { Text(if (step.screen in listOf(CoachScreen.CATEGORIES, CoachScreen.API)) "הגדרות מערכת" else "שכר עבודות אישי", style = MaterialTheme.typography.titleMedium) },
                            actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Settings, "הגדרות") } }, windowInsets = WindowInsets(0, 0, 0, 0))
                        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (step.target != null && step.target != CoachTarget.HISTORY) cardHeight + 20.dp else 0.dp)) {
                            // Separate state from normal screens. No simulated save/start/payment callbacks.
                            key(step.screen) {
                                when (step.screen) {
                                    CoachScreen.HOME, CoachScreen.FORM, CoachScreen.AI -> DashboardScreen(
                                        viewModel, WorkViewModel.StatsSummary(), categories, emptyList(), null, "", 40.0, 1f,
                                        { _, _ -> }, { _, _, _ -> }, entries, {}, {}, { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, { _, _ -> }, {})
                                    CoachScreen.HISTORY -> ShiftsScreen(viewModel = viewModel, entries = entries, categories = categories,
                                        searchQuery = "", onSearchQueryChange = {}, onTogglePaid = {}, onEdit = {}, onDelete = {})
                                    CoachScreen.SHARE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                        if (entries.isEmpty()) Text("משמרת לדוגמה בלבד · לא נשמרת", color = Color(0xFFC7D2FE), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 12.dp))
                                        WorkEntryRowCard(entry = entries.firstOrNull()?.copy(isGroupShift = false, groupWorkersJson = "") ?: WorkEntry(id = -1, category = "עבודה לדוגמה", date = 1735732800000, isTimeRange = true, startTime = "09:00", endTime = "17:00", hours = 8.0, hourlyRate = 40.0, totalEarnings = 320.0), onTogglePaid = {}, onEdit = {}, onDelete = {})
                                    }
                                    CoachScreen.CATEGORIES, CoachScreen.API -> ManagementScreen(viewModel, categories, {})
                                }
                            }
                        }
                        NavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0, 0, 0, 0)) {
                            NavigationBarItem(selected = step.screen !in listOf(CoachScreen.HISTORY, CoachScreen.SHARE), onClick = {}, icon = { Icon(Icons.Outlined.GridView, null) }, label = { Text("ראשי") })
                            NavigationBarItem(selected = step.screen in listOf(CoachScreen.HISTORY, CoachScreen.SHARE), onClick = {}, icon = { Icon(Icons.Outlined.History, null) }, label = { Text("היסטוריה") }, modifier = Modifier.coachTarget(CoachTarget.HISTORY))
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
            if (valid != null) drawRoundRect(Color(0xFFA5B4FC), hole.topLeft, hole.size, CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
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
            shape = RoundedCornerShape(20.dp), color = Color(0xFF202238), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4E70)), shadowElevation = 12.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${controller.index + 1} מתוך ${workOnboardingSteps.size}", color = Color(0xFFA5B4FC), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).testTag("onboarding_progress"))
                    TextButton(onClick = controller::finish, modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_skip")) { Text("דלג", color = Color(0xFFCBD5E1)) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(step.body, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = controller::next, enabled = ready, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("onboarding_next"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))) {
                        Text(if (controller.index == workOnboardingSteps.lastIndex) "יאללה, מתחילים" else "הבא", fontWeight = FontWeight.Bold)
                    }
                    if (controller.index > 0) TextButton(onClick = controller::back, modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_back")) { Text("הקודם", color = Color(0xFFE2E8F0)) }
                }
            }
        }
    }
}
