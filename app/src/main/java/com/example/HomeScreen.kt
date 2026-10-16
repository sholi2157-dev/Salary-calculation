package com.example

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.ui.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.example.ui.coachTarget
import com.example.ui.CoachTarget
import com.example.ui.LocalCoachStep
import com.example.ui.CoachScreen
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Home (dashboard) screen and its cards (moved verbatim from MainActivity.kt).

@Composable
fun AnimatedGlowingEarnings(
    targetValue: Double,
    runCountAnimationTrigger: Int,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    glowColor: Color = Color(0xFF34D399),
    isCurrency: Boolean = true,
    isHours: Boolean = false,
    currencySymbol: String = "₪"
) {
    val animatable = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastAnimatedTrigger by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) }

    LaunchedEffect(runCountAnimationTrigger, targetValue) {
        if (runCountAnimationTrigger != lastAnimatedTrigger) {
            lastAnimatedTrigger = runCountAnimationTrigger
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        } else {
            animatable.snapTo(targetValue.toFloat())
        }
    }

    val animatedValue = animatable.value
    val formattedText = remember(animatedValue, isCurrency, isHours, currencySymbol) {
        if (isCurrency) {
            String.format(Locale.US, "%s%,.2f", currencySymbol, animatedValue.toDouble())
        } else if (isHours) {
            val rounded = Math.round(animatedValue * 100.0) / 100.0
            if (rounded % 1.0 == 0.0) {
                "${rounded.toInt()} שעות"
            } else {
                "$rounded שעות"
            }
        } else {
            String.format(Locale.US, "%,.1f", animatedValue.toDouble())
        }
    }

    Text(
        text = formattedText,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
    )
}

@Composable
fun ScrollCollapsibleFilterPanel(
    filterFractionProvider: () -> Float,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = filterFractionProvider()
            }
            .layout { measurable, constraints ->
                val fraction = filterFractionProvider()
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * fraction).toInt()
                layout(placeable.width, height) {
                    placeable.placeWithLayer(0, 0)
                }
            }
            .clipToBounds()
    ) {
        content()
    }
}


// ================= DASHBOARD SCREEN =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WorkViewModel,
    stats: WorkViewModel.StatsSummary,
    categories: List<WorkCategory>,
    workersDirectory: List<com.example.data.WorkerDirectory>,
    activeShiftStartTime: Long?,
    activeShiftCategory: String,
    activeShiftRate: Double,
    focusAlpha: Float,
    onStartShift: (String, Double, String) -> Unit,
    onSaveShift: (String, Double, Double) -> Unit,
    recentEntries: List<WorkEntry>,
    onTogglePaid: (WorkEntry) -> Unit,
    onViewAll: () -> Unit,
    onAddEntry: (String, Long, Boolean, String?, String?, Double, Double, String, Boolean, Double?, Double?, String, String) -> Unit,
    onAddCategory: (String, Double) -> Unit,
    onDeleteCategory: (WorkCategory) -> Unit,
    bottomNavigation: @Composable () -> Unit = {},
    selectedTab: Int = 0,
    onScrollStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isImeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val runCountAnimationTrigger = viewModel.runCountAnimationTrigger.value

    // Live Ticker Seconds State
    var tickerSeconds by remember { mutableStateOf(0L) }

    // Live Ticker Effect
    LaunchedEffect(activeShiftStartTime) {
        if (activeShiftStartTime != null) {
            while (true) {
                val currentSystemTimeSeconds = System.currentTimeMillis() / 1000L
                val startSystemTimeSeconds = activeShiftStartTime / 1000L
                tickerSeconds = currentSystemTimeSeconds - startSystemTimeSeconds
                kotlinx.coroutines.delay(1000)
            }
        } else {
            tickerSeconds = 0L
        }
    }

    val defaultCurr by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    var selectedCurrency by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(defaultCurr) }

    var hourlyRateStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("40") }
    val localPreferences by viewModel.localPreferences.collectAsStateWithLifecycle()
    val defaultCategory = localPreferences["defaultCategory"] ?: categories.firstOrNull()?.name ?: "עצמאי"
    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    LaunchedEffect(defaultCategory, categories) {
        if (categories.isNotEmpty() && categories.none { it.name == selectedCategory }) selectedCategory = defaultCategory
    }
    val selectedDefaultRate = categories.firstOrNull { it.name == selectedCategory }?.defaultRate
    var appliedCategoryDefaults by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    LaunchedEffect(selectedCategory, selectedDefaultRate) {
        if (selectedDefaultRate != null && appliedCategoryDefaults != selectedCategory) {
            selectedCurrency = viewModel.categoryCurrency(selectedCategory)
            hourlyRateStr = selectedDefaultRate.toString()
            appliedCategoryDefaults = selectedCategory
        }
    }
    val coachStep = LocalCoachStep.current
    var isReportCardExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(coachStep?.screen in listOf(CoachScreen.FORM, CoachScreen.AI)) }
    var reportSubmitted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val dashboardIsScrolling = scrollState.isScrollInProgress
    LaunchedEffect(dashboardIsScrolling, selectedTab) {
        if (selectedTab == 0) onScrollStateChanged(dashboardIsScrolling)
        // Do not dismiss the IME from scrollState changes. Android may report a brief
        // scroll while bringing a newly focused field into view, which previously made
        // the keyboard flash open and immediately close.
    }
    val scope = rememberCoroutineScope()
    var showQuickShiftDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showStopConfirmationDialog by remember { mutableStateOf(false) }
    var cancelShiftStart by remember { mutableStateOf<Long?>(null) }
    var categoryToDelete by remember { mutableStateOf<WorkCategory?>(null) }

    var isManualMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) } // false = שעון, true = ידני
    var isAiMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(coachStep?.screen == CoachScreen.AI) }
    var aiInputText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var isAiParsing by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var isAiSaving by remember { mutableStateOf(false) }
    var aiProposal by remember { mutableStateOf<List<com.example.api.GeminiParser.ParsedShift>?>(null) }
    var selectedDateMillis by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var startTimeStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("09:00") }
    var endTimeStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("17:00") }
    var breakMinutesStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0") }
    var notesText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var manualHoursStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("8.0") }

    var showErrorHours by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showErrorRate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    var isGroupShift by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var employerRateStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var workerRateStr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showSeparateRates by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val groupWorkers = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<WorkViewModel.GroupWorkerState>, String>(
            save = { viewModel.stringifyGroupWorkers(it) },
            restore = { mutableStateListOf(*viewModel.parseGroupWorkers(it).toTypedArray()) }
        )
    ) { mutableStateListOf<WorkViewModel.GroupWorkerState>() }
    var currentWorkerName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var currentWorkerHours by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0.0") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val voiceSession = remember {
        com.example.ui.VoiceTranscriptSession({ aiInputText = it; aiError = null }, { aiError = it })
    }
    val aiScreenActive by rememberUpdatedState(isAiMode && isReportCardExpanded && selectedTab == 0)


    val speechRecognizer = remember { android.speech.SpeechRecognizer.createSpeechRecognizer(context) }
    val speechRecognizerIntent = remember {
        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun startVoice() {
        if (!aiScreenActive || isAiParsing) return
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            aiError = "זיהוי קולי אינו זמין במכשיר הזה. אפשר להקליד את התיאור."
            return
        }
        aiError = null
        voiceSession.begin(aiInputText)
        try { speechRecognizer.startListening(speechRecognizerIntent) }
        catch (_: Exception) { voiceSession.interrupt(); aiError = "לא ניתן להתחיל הקלטה כרגע. אפשר לנסות שוב או להקליד." }
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoice()
        else aiError = "הקלטה דורשת הרשאת מיקרופון. אפשר לאשר בהגדרות המכשיר או להקליד."
    }
    val processAIInput: suspend (String) -> Unit = { text ->
        if (text.isNotBlank() && !isAiParsing && !voiceSession.active) {
            isAiParsing = true
            aiError = null
            try {
                val categoriesNow = viewModel.categories.value
                val results = com.example.api.GeminiParser.parseNaturalLanguageToShifts(text, categoriesNow.map { it.name }, categoriesNow.associate { it.name to it.defaultRate }, com.example.api.PersonalAiKey.read(context, viewModel.owner.uid))
                if (results.isNotEmpty()) aiProposal = results
                else aiError = "לא הצלחנו להבין את פרטי המשמרת. נסה להוסיף תאריך, שעות ותעריף ולפענח שוב."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                aiError = "הפענוח לא הושלם. בדוק את החיבור ואת המפתח האישי בהגדרות, או תקן את התיאור ונסה שוב."
            } finally { isAiParsing = false }
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(speechRecognizer, lifecycleOwner) {
        speechRecognizer.setRecognitionListener(voiceSession)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && voiceSession.active) {
                voiceSession.interrupt()
                speechRecognizer.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceSession.interrupt()
            speechRecognizer.destroy()
        }
    }
    LaunchedEffect(isAiMode, isReportCardExpanded, selectedTab) {
        if (!aiScreenActive && voiceSession.active) {
            voiceSession.interrupt()
            speechRecognizer.cancel()
        }
    }
    if (aiScreenActive) aiProposal?.let { proposal ->
        com.example.ui.AiShiftReview(proposal,
            onEdit = { if (!isAiSaving) aiProposal = null },
            onSave = {
                if (aiProposal != null && !isAiSaving) {
                    isAiSaving = true
                    viewModel.addShifts(proposal) { saved ->
                        isAiSaving = false
                        if (saved) {
                            aiProposal = null
                            aiInputText = ""
                            Toast.makeText(context, "המשמרות נשמרו", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, saving = isAiSaving)
    }

    val saveInteractionSource = remember { MutableInteractionSource() }
    val saveReport: @Composable () -> Unit = {
        Button(
            interactionSource = saveInteractionSource,
            onClick = {
                if (reportSubmitted) return@Button
                val testHours = if (isManualMode) manualHoursStr.toDoubleOrNull() ?: 0.0 else 1.0
                val testRate = hourlyRateStr.toDoubleOrNull() ?: 0.0

                showErrorHours = isManualMode && (manualHoursStr.isBlank() || testHours <= 0.0)
                showErrorRate = hourlyRateStr.isBlank() || testRate <= 0.0

                if (showErrorHours || showErrorRate) {
                    triggerHapticFeedback(context, isDestructive = true)
                    Toast.makeText(context, "נא לתקן את השדות המסומנים באדום", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val finalHours = if (isManualMode) {
                    manualHoursStr.toDoubleOrNull() ?: 8.0
                } else {
                    val sParts = startTimeStr.split(":")
                    val sMin = (sParts.getOrNull(0)?.toIntOrNull() ?: 9) * 60 + (sParts.getOrNull(1)?.toIntOrNull() ?: 0)
                    val eParts = endTimeStr.split(":")
                    val eMin = (eParts.getOrNull(0)?.toIntOrNull() ?: 17) * 60 + (eParts.getOrNull(1)?.toIntOrNull() ?: 0)
                    val bMins = breakMinutesStr.toDoubleOrNull() ?: 0.0
                    val durationVal = (if (eMin < sMin) eMin + 1440 - sMin else eMin - sMin) - bMins
                    maxOf(0.0, durationVal / 60.0)
                }
                val finalRate = hourlyRateStr.toDoubleOrNull() ?: 40.0
                val eRate = if (isGroupShift && showSeparateRates) employerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                val wRate = if (isGroupShift && showSeparateRates) workerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                if (!finalHours.isFinite() || finalHours <= 0 || !finalRate.isFinite() || finalRate < 0 ||
                    !eRate.isFinite() || eRate < 0 || !wRate.isFinite() || wRate < 0 ||
                    (breakMinutesStr.toDoubleOrNull()?.let { !it.isFinite() || it < 0 } != false)) {
                    Toast.makeText(context, "נא להזין שעות, הפסקה ותעריפים תקינים", Toast.LENGTH_LONG).show()
                    return@Button
                }
                val gJson = if (isGroupShift && groupWorkers.isNotEmpty()) {
                    // Simple JSON Array construction for Workers
                    val arr = org.json.JSONArray()
                    groupWorkers.forEach { w ->
                        val obj = if (w.sourceJson.isBlank()) org.json.JSONObject() else org.json.JSONObject(w.sourceJson)
                        obj.put("name", w.name)
                        obj.put("hours", w.hours)
                        obj.put("isPaid", w.isPaid)
                        arr.put(obj)
                    }
                    arr.toString()
                } else ""

                reportSubmitted = true
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                onAddEntry(
                    selectedCategory,
                    selectedDateMillis,
                    !isManualMode,
                    if (!isManualMode) startTimeStr else null,
                    if (!isManualMode) endTimeStr else null,
                    finalHours,
                    finalRate,
                    notesText,
                    isGroupShift,
                    eRate,
                    wRate,
                    gJson,
                    selectedCurrency
                )
                isReportCardExpanded = false
                notesText = ""
                breakMinutesStr = "0"
                if (isGroupShift) {
                    groupWorkers.clear()
                    isGroupShift = false
                    employerRateStr = ""
                    workerRateStr = ""
                }
                triggerHapticFeedback(context, isDestructive = false)
                Toast.makeText(context, "הדיווח נשמר בהצלחה!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .pressScale(interactionSource = saveInteractionSource)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("save_shift_button"),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "שמור",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("dashboard_scroll_container")
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            WorkSummaryCarousel(recentEntries, defaultCurr)
            if (activeShiftStartTime != null) {
                Card(colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("משמרת פעילה · $activeShiftCategory", color = Color(0xFF34D399))
                        val time = String.format(Locale.US, "%02d:%02d:%02d", tickerSeconds / 3600, (tickerSeconds % 3600) / 60, tickerSeconds % 60)
                        Text(time, fontSize = 28.sp, modifier = Modifier.widthIn(min = 148.dp).testTag("active_shift_timer"),
                            style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr, fontFeatureSettings = "tnum"))
                        Text(com.example.data.WorkMoney.format(tickerSeconds * activeShiftRate / 3600.0, viewModel.activeShiftCurrency.collectAsStateWithLifecycle().value),
                            modifier = Modifier.widthIn(min = 120.dp), style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr, fontFeatureSettings = "tnum"))
                        com.example.ui.ShiftCurrencyPicker(
                            viewModel.activeShiftCurrency.collectAsStateWithLifecycle().value,
                            { viewModel.updateActiveShiftCurrency(it, activeShiftStartTime) }, "active_shift_currency"
                        )
                        TextButton(onClick = { cancelShiftStart = activeShiftStartTime }, modifier = Modifier.testTag("cancel_active_shift")) {
                            Text("בטל משמרת פעילה", color = Color(0xFFF87171))
                        }
                    }
                }
            }

            if (!isReportCardExpanded && !showQuickShiftDialog && !isImeVisible) {
                OutlinedButton(
                    onClick = {
                        triggerHapticFeedback(context, isDestructive = false)
                        if (activeShiftStartTime != null) showStopConfirmationDialog = true else showQuickShiftDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("live_shift_fab"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (activeShiftStartTime != null) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, tint = Color(0xFFC7D2FE))
                    Spacer(Modifier.width(8.dp))
                    Text(if (activeShiftStartTime != null) "סיים משמרת פעילה" else "התחל משמרת פעילה", color = Color(0xFFF1F5F9))
                }
            }

        // Form Card Block: "+ דיווח חדש"

            // Category Addition dialog inside item
            if (showAddCategoryDialog) {
                var newCatName by remember { mutableStateOf("") }
                var newCatRate by remember { mutableStateOf("40") }
                AlertDialog(
                    onDismissRequest = { showAddCategoryDialog = false },
                    title = { Text("הוסף מעסיק חדש", fontWeight = FontWeight.Bold, color = Color.White) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = newCatName,
                                onValueChange = { newCatName = it },
                                label = { Text("שם מעסיק / קטגוריה") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF44444F),
                                    focusedLabelColor = Color(0xFF5C6BC0)
                                )
                            )
                            OutlinedTextField(
                                value = newCatRate,
                                onValueChange = { newCatRate = it },
                                label = { Text("תעריף שעתי לברירת מחדל") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF44444F),
                                    focusedLabelColor = Color(0xFF5C6BC0)
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newCatName.isNotBlank()) {
                                    val rVal = newCatRate.toDoubleOrNull() ?: 40.0
                                    onAddCategory(newCatName, rVal)
                                    selectedCategory = newCatName
                                    hourlyRateStr = rVal.toString()
                                    showAddCategoryDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                        ) {
                            Text("הוסף", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddCategoryDialog = false }) {
                            Text("ביטול", color = Color(0xFF8E8E93))
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_shift_form_card")
                    .graphicsLayer { alpha = focusAlpha }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .coachTarget(CoachTarget.SHIFT)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                if (!isReportCardExpanded) reportSubmitted = false
                                else {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                }
                                isReportCardExpanded = !isReportCardExpanded
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Right item: "דיווח חדש"
                        Text(
                            text = "דיווח חדש",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = com.example.ui.theme.AssistantFontFamily
                        )
                        // Left item: expand/collapse icon
                        Icon(
                            imageVector = if (isReportCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isReportCardExpanded) "כווץ" else "הרחב",
                            tint = Color(0xFF5C6BC0),
                            modifier = Modifier.size(28.dp)
                        )


                    }

                    AnimatedVisibility(
                        visible = isReportCardExpanded,
                        enter = expandVertically(animationSpec = tween(180)),
                        exit = shrinkVertically(animationSpec = tween(120))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                    // 1.5. Unified Mode Selector (שעון / ידני / קבוצה / AI)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Color(0xFF161922), shape = RoundedCornerShape(22.dp))
                            .border(1.dp, Color(0x1FFFFFFF), shape = RoundedCornerShape(22.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Part A: "שעון"
                        val isClockSelected = !isManualMode && !isGroupShift && !isAiMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isClockSelected) Color(0xFF2A2F45) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (isClockSelected) Modifier.border(1.dp, Color(0x66818CF8), RoundedCornerShape(18.dp))
                                    else Modifier
                                )
                                .clickable {
                                    isManualMode = false
                                    isGroupShift = false
                                    isAiMode = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "שעון",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isClockSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                            )
                        }

                        // Part B: "ידני"
                        val isManualSelected = isManualMode && !isGroupShift && !isAiMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isManualSelected) Color(0xFF2A2F45) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (isManualSelected) Modifier.border(1.dp, Color(0x66818CF8), RoundedCornerShape(18.dp))
                                    else Modifier
                                )
                                .clickable {
                                    isManualMode = true
                                    isGroupShift = false
                                    isAiMode = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ידני",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isManualSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                            )
                        }

                        // Part C: "קבוצה"
                        val isGroupSelected = isGroupShift && !isAiMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isGroupSelected) Color(0xFF2A2F45) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (isGroupSelected) Modifier.border(1.dp, Color(0x66818CF8), RoundedCornerShape(18.dp))
                                    else Modifier
                                )
                                .clickable {
                                    isManualMode = true
                                    isGroupShift = true
                                    isAiMode = false
                                    val hDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
                                    currentWorkerHours = if (hDouble > 0) String.format(Locale.US, "%.2f", hDouble) else "0.0"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "קבוצה",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGroupSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                            )
                        }

                        // Part D: "Ai"
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isAiMode) Color(0xFF2A2F45) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (isAiMode) Modifier.border(1.dp, Color(0x66818CF8), RoundedCornerShape(18.dp))
                                    else Modifier
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    isAiMode = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isAiMode) Color(0xFF818CF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Ai",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAiMode) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    if (isAiMode) {
                        com.example.ui.AiShiftInput(
                            text = aiInputText, onText = { aiInputText = it; aiError = null },
                            capturing = voiceSession.active, listening = voiceSession.listening,
                            processing = isAiParsing, error = aiError,
                            onRecord = {
                                val permission = android.Manifest.permission.RECORD_AUDIO
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoice()
                                else requestPermissionLauncher.launch(permission)
                            },
                            onStop = { voiceSession.stop(); speechRecognizer.stopListening() },
                            onCancel = { voiceSession.cancel(); speechRecognizer.cancel() },
                            onParse = { scope.launch { processAIInput(aiInputText) } }
                        )
                    } else {
                    CompactReportFields(
                        ReportFieldValues(selectedDateMillis, isManualMode, isGroupShift, startTimeStr, endTimeStr,
                            manualHoursStr, breakMinutesStr, hourlyRateStr, selectedCurrency, selectedCategory, notesText,
                            showSeparateRates, employerRateStr, workerRateStr, showErrorHours, showErrorRate),
                        categories = categories,
                        onDate = { selectedDateMillis = it }, onStart = { startTimeStr = it }, onEnd = { endTimeStr = it },
                        onHours = { value ->
                            manualHoursStr = value
                            if (isGroupShift) {
                                val hours = value.toDoubleOrNull() ?: 0.0
                                for (i in groupWorkers.indices) groupWorkers[i] = groupWorkers[i].copy(hours = hours)
                                currentWorkerHours = value
                            }
                            showErrorHours = false
                        },
                        onBreak = { breakMinutesStr = it },
                        onRate = { value ->
                            val filtered = value.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) { hourlyRateStr = filtered; showErrorRate = filtered.isBlank() }
                        },
                        onCurrency = { selectedCurrency = it },
                        onCategory = { selectedCategory = it.name; hourlyRateStr = it.defaultRate.toString() },
                        onNotes = { notesText = it }, onAddCategory = { showAddCategoryDialog = true },
                        onDeleteCategory = { categories.firstOrNull { it.name == selectedCategory }?.let { categoryToDelete = it } },
                        onSeparateRates = { showSeparateRates = !showSeparateRates },
                        onEmployerRate = { employerRateStr = it }, onWorkerRate = { workerRateStr = it }
                    )

                    // --- Group Shift Dynamic Inputs ---
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = isGroupShift,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val totalGroupHours = groupWorkers.sumOf { it.hours }

                                Text("עובדים ($totalGroupHours שעות סה\"כ)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                                groupWorkers.forEachIndexed { index, worker ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = worker.name,
                                            onValueChange = { newName ->
                                                groupWorkers[index] = worker.copy(name = newName)
                                            },
                                            label = { Text("שם", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF44444F),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                        OutlinedTextField(
                                            value = if (worker.hours == 0.0) "" else worker.hours.toString(),
                                            onValueChange = { newHours ->
                                                val filtered = newHours.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) {
                                                    val h = filtered.toDoubleOrNull() ?: 0.0
                                                    groupWorkers[index] = worker.copy(hours = h)
                                                }
                                            },
                                            label = { Text("שעות", fontSize = 12.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(80.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF44444F),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                        IconButton(onClick = { groupWorkers.removeAt(index) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "הסר עובד", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = currentWorkerName,
                                            onValueChange = {
                                                currentWorkerName = it
                                            },
                                            label = { Text("שם", fontSize = 12.sp) },
                                            modifier = Modifier.fillMaxWidth().testTag("worker_name_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF44444F),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                    }
                                    OutlinedTextField(
                                        value = currentWorkerHours,
                                        onValueChange = { currentWorkerHours = it },
                                        label = { Text("שעות", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(80.dp).testTag("worker_hours_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                                            focusedBorderColor = Color(0xFF5C6BC0),
                                            unfocusedBorderColor = Color(0xFF44444F),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            val h = currentWorkerHours.toDoubleOrNull()
                                            if (currentWorkerName.isNotBlank() && h != null && h > 0) {
                                                groupWorkers.add(WorkViewModel.GroupWorkerState(name = currentWorkerName.trim(), hours = h, isPaid = false))
                                                currentWorkerName = ""
                                                val hDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
                                                currentWorkerHours = if (hDouble > 0) String.format(Locale.US, "%.2f", hDouble) else "0.0"
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFF5C6BC0), CircleShape)
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "הוסף עובד", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }


                        }
                    }
                }
            }

            // Recent Shifts Section
            val latestThreeShifts = remember(recentEntries) {
                recentEntries.sortedByDescending { it.createdAt }.take(3)
            }

            if (latestThreeShifts.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0x331E293B)
                    ),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = focusAlpha }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "משמרות אחרונות שנוספו",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = com.example.ui.theme.AssistantFontFamily,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            latestThreeShifts.forEach { entry ->
                                RecentShiftCompactCard(
                                    entry = entry,
                                    onTogglePaid = { onTogglePaid(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

            // This footer is a sibling of the weighted scroll viewport, never an overlay.
            if (isReportCardExpanded && !isAiMode) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    saveReport()
                }
            }
            if (!isImeVisible) bottomNavigation()

        if (showStopConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showStopConfirmationDialog = false },
                title = { Text("סיום משמרת", color = Color.White) },
                text = { Text("האם אתה בטוח שברצונך לסיים את המשמרת הפעילה?", color = Color(0xFFE2E8F0)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val durationHours = tickerSeconds / 3600.0
                            onSaveShift(activeShiftCategory, durationHours, activeShiftRate)
                            Toast.makeText(context, "הדיווח נשמר בהצלחה!", Toast.LENGTH_SHORT).show()
                            showStopConfirmationDialog = false
                        }
                    ) {
                        Text("כן, סיים", color = Color(0xFF10B981))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirmationDialog = false }) {
                        Text("ביטול", color = Color(0xFF8E8E93))
                    }
                },
                containerColor = com.example.ui.theme.FormSurface
            )
        }
    }

    if (cancelShiftStart != null) {
        AlertDialog(
            onDismissRequest = { cancelShiftStart = null },
            title = { Text("ביטול משמרת פעילה") },
            text = { Text("האם אתה בטוח שברצונך לבטל? הזמן שנצבר במשמרת זו לא יישמר. דיווחים קודמים לא יימחקו.") },
            confirmButton = { TextButton(onClick = {
                viewModel.cancelActiveShift(cancelShiftStart)
                cancelShiftStart = null
            }, modifier = Modifier.testTag("confirm_cancel_active_shift")) { Text("כן, בטל את המשמרת") } },
            dismissButton = { TextButton(onClick = { cancelShiftStart = null }) { Text("המשך במשמרת") } }
        )
    }

    var dialogCategory by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    val dialogDefaultRate = categories.firstOrNull { it.name == dialogCategory }?.defaultRate ?: 40.0
    var dialogRateStr by remember(dialogCategory, dialogDefaultRate, showQuickShiftDialog) {
        mutableStateOf(dialogDefaultRate.toString())
    }
    val dialogDefaultCurrency = viewModel.categoryCurrency(dialogCategory)
    var dialogCurrency by androidx.compose.runtime.saveable.rememberSaveable(dialogCategory, dialogDefaultCurrency, showQuickShiftDialog) {
        mutableStateOf(dialogDefaultCurrency)
    }
    var expanded by remember { mutableStateOf(false) }

    if (showQuickShiftDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showQuickShiftDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("quick_shift_scrim")
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showQuickShiftDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, Color(0xFF2D2D2D)),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 520.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { /* consume card taps so only the outside scrim dismisses */ }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    Text("הגדרת משמרת פעילה", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = dialogCategory,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("קטגוריה / מעסיק") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        dialogCategory = cat.name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dialogRateStr,
                        onValueChange = { dialogRateStr = it },
                        label = { Text("תעריף שעתי") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                        com.example.ui.ShiftCurrencyPicker(dialogCurrency, { dialogCurrency = it }, "quick_shift_currency", Modifier.weight(1f), compact = true)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val lastEntry = recentEntries.firstOrNull()
                                val cat = lastEntry?.category ?: defaultCategory
                                val rate = categories.firstOrNull { it.name == cat }?.defaultRate ?: WorkViewModel.DEFAULT_RATE
                                onStartShift(cat, rate, viewModel.categoryCurrency(cat))
                                showQuickShiftDialog = false
                            }
                        ) {
                            Text("דלג", color = Color(0xFF8E8E93))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val rateVal = dialogRateStr.toDoubleOrNull() ?: 40.0
                                onStartShift(dialogCategory, rateVal, dialogCurrency)
                                showQuickShiftDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("אישור", color = Color.White)
                        }
                    }
                    }
                }
            }
        }

    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("אישור מחיקה", color = Color.White) },
            text = { Text("האם אתה בטוח שברצונך למחוק משרת '${categoryToDelete?.name}'? פעולה זו אינה ניתנת לביטול.", color = Color(0xFFE2E8F0)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let {
                            onDeleteCategory(it)
                            Toast.makeText(context, "מעסיק נמחק בהצלחה", Toast.LENGTH_SHORT).show()
                            if (categories.isNotEmpty()) {
                                selectedCategory = defaultCategory
                            }
                        }
                        categoryToDelete = null
                    }
                ) {
                    Text("מחק", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("ביטול", color = Color(0xFF8E8E93))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0)
        )
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecentShiftCompactCard(
    entry: WorkEntry,
    onTogglePaid: () -> Unit
) {
    var isExpanded by remember(entry.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val formattedDate = remember(entry.date) {
        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("he", "IL"))
        sdf.format(Date(entry.date))
    }

    val statusColor = if (entry.isPaid) Color(0xFF34D399) else Color(0xFFFBBF24)
    val statusBg = if (entry.isPaid) Color(0xFF064E3B) else Color(0xFF78350F)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                isExpanded = !isExpanded
            }
            .testTag("recent_shift_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val initialChar = if (entry.category.isNotEmpty()) entry.category.substring(0, 1) else "מ"
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x11FFFFFF), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialChar,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC7D2FE),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.category,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFE5E5EA),
                        fontFamily = com.example.ui.theme.AssistantFontFamily
                    )
                    Text(
                        text = formattedDate,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93),
                        fontFamily = com.example.ui.theme.AssistantFontFamily
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = com.example.data.WorkMoney.format(entry.totalEarnings, entry.currency),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5E5EA)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f ש'", entry.hours),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8E8E93)
                        )
                        Text(if (entry.isPaid) "שולם" else "ממתין", color = statusColor, fontSize = 12.sp,
                            modifier = Modifier.testTag("recent_payment_status_${entry.id}"), maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0x11FFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (entry.isTimeRange && entry.startTime != null && entry.endTime != null) {
                                Text(
                                    text = "שעות עבודה: ${entry.startTime} - ${entry.endTime} (${String.format(Locale.US, "%.1f", entry.hours)} שעות)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF8E8E93),
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            } else {
                                Text(
                                    text = "שעות שהוזנו ידנית: ${String.format(Locale.US, "%.1f", entry.hours)} שעות",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF8E8E93),
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            }

                            Text(
                                text = "תעריף שעתי: ${entry.currency}${entry.hourlyRate}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF8E8E93),
                                fontFamily = com.example.ui.theme.AssistantFontFamily
                            )

                            if (entry.notes.isNotBlank()) {
                                Text(
                                    text = "הערות: ${entry.notes}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF8E8E93),
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            }
                        }

                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable {
                                    triggerHapticFeedback(context, isDestructive = false)
                                    onTogglePaid()
                                }
                                .testTag("recent_toggle_payment_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isPaid) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (entry.isPaid) "שולם" else "חוב (שנה)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    stats: WorkViewModel.PeriodStats,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stats.money.entries.joinToString("\n") { com.example.data.WorkMoney.format(it.value, it.key) },
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                    )
                )
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.1f שעות", stats.totalHours),
                fontSize = 11.sp,
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun RecentShiftItemRow(
    entry: WorkEntry,
    onTogglePaid: () -> Unit
) {
    val formattedDate = remember(entry.date) {
        val sdf = SimpleDateFormat("dd בMMMM", Locale("he", "IL"))
        sdf.format(Date(entry.date))
    }

    val initialChar = if (entry.category.isNotEmpty()) entry.category.substring(0, 1) else "מ"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = com.example.ui.theme.FormSurface
        ),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTogglePaid() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Letter badge rounded box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF3F375A), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialChar,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC7D2FE),
                        fontSize = 16.sp
                    )
                }

                Column {
                    Text(
                        text = entry.category,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (entry.isTimeRange) {
                            "$formattedDate | ${entry.startTime} - ${entry.endTime}"
                        } else {
                            "$formattedDate | ${String.format(Locale.US, "%.1f", entry.hours)} שעות"
                        },
                        fontSize = 10.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = com.example.data.WorkMoney.format(entry.totalEarnings, entry.currency),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .background(
                            color = if (entry.isPaid) Color(0xFF064E3B) else Color(0xFF78350F),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (entry.isPaid) "שולם" else "ממתין",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.isPaid) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
            }
        }
    }
}
