package com.example

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "pressScale"
    )
    val baseModifier = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
    } else {
        baseModifier
    }
}

fun triggerHapticFeedback(context: Context, isDestructive: Boolean = false) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isDestructive) {
                val timings = longArrayOf(0, 50, 100, 50)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (isDestructive) {
                vibrator.vibrate(longArrayOf(0, 50, 100, 50), -1)
            } else {
                vibrator.vibrate(45)
            }
        }
    }
}

fun formatCleanHours(hours: Double): String {
    val rounded = Math.round(hours * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toInt()} שעות"
    } else {
        "$rounded שעות"
    }
}

fun getWorkerNamesFromEntry(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    val regex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    return regex.findAll(json).map { it.groupValues[1] }.toList()
}

fun generateWhatsAppReportText(filteredEntries: List<WorkEntry>, selectedCategoryFilter: String, searchQuery: String): String {
    if (filteredEntries.isEmpty()) return ""
    val uniqueCategories = filteredEntries.map { it.category }.distinct()
    val allWorkers = filteredEntries.flatMap { getWorkerNamesFromEntry(it.groupWorkersJson) }.distinct()
    
    val isSingleCategory = selectedCategoryFilter != "הכל" || uniqueCategories.size == 1
    val isSingleWorker = allWorkers.size == 1
    
    val isWorkerQuery = isSingleWorker || (searchQuery.isNotBlank() && allWorkers.any { it.contains(searchQuery, ignoreCase = true) })
    
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("he", "IL"))
    val totalHours = filteredEntries.sumOf { it.hours }
    val totalPay = filteredEntries.sumOf { it.totalEarnings }
    
    val minDate = filteredEntries.minOfOrNull { it.date } ?: System.currentTimeMillis()
    val dateStr = sdf.format(Date(minDate))
    
    val hasUnpaid = filteredEntries.any { !it.isPaid }
    val statusStr = if (hasUnpaid) "לא שולם" else "שולם"
    
    return if (isWorkerQuery) {
        val workerName = when {
            isSingleWorker -> allWorkers.first()
            searchQuery.isNotBlank() && allWorkers.any { it.contains(searchQuery, ignoreCase = true) } -> allWorkers.first { it.contains(searchQuery, ignoreCase = true) }
            else -> allWorkers.firstOrNull() ?: searchQuery
        }
        
        val grouped = filteredEntries.groupBy { it.category }
        val categoriesText = if (grouped.size > 1) {
            val catsStr = grouped.map { "${it.key}: ${String.format(Locale.US, "%.1f", it.value.sumOf { entry -> entry.hours })} שעות" }.joinToString("... ") + "..."
            " ($catsStr)"
        } else {
            ""
        }
        
        val endSentence = if (hasUnpaid) "\nאיך אתה מעדיף שאשלם לך?" else ""
        
        "היי $workerName, להלן פירוט שעות עבודה שלך מיום $dateStr:\n" +
                "עבדת ${String.format(Locale.US, "%.1f", totalHours)} שעות$categoriesText. מגיע לך: ₪${String.format(Locale.US, "%,.2f", totalPay)}.\n" +
                "סטטוס תשלום: $statusStr.$endSentence"
    } else {
        // Employer (Category Report)
        val grouped = filteredEntries.groupBy { it.category }
        val invoiceLines = grouped.map { (catName, catEntries) ->
            val catHours = catEntries.sumOf { it.hours }
            val catPay = catEntries.sumOf { it.totalEarnings }
            "קטגוריה: $catName | סך שעות: ${String.format(Locale.US, "%.1f", catHours)} | סה\"כ לתשלום: ₪${String.format(Locale.US, "%,.2f", catPay)}."
        }.joinToString("\n")
        
        invoiceLines
    }
}

fun copyWhatsAppToClipboard(context: Context, filteredEntries: List<WorkEntry>, selectedCategoryFilter: String, searchQuery: String) {
    val textToSend = generateWhatsAppReportText(filteredEntries, selectedCategoryFilter, searchQuery)
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("WhatsApp Report", textToSend)
    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, "ההודעה הועתקה בהצלחה!", Toast.LENGTH_SHORT).show()
}

fun copyExcelToClipboard(context: Context, filteredEntries: List<WorkEntry>, selectedCategoryFilter: String) {
    val headers = listOf("קטגוריה", "תאריך", "שעות", "תעריף שעתי", "שכר לתשלום", "סטטוס", "הערות", "מטבע")
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("he", "IL"))
    val rows = filteredEntries.map { entry ->
        val dateStr = sdf.format(Date(entry.date))
        val statusStr = if (entry.isPaid) "שולם" else "ממתין"
        listOf(
            entry.category,
            dateStr,
            String.format(Locale.US, "%.2f", entry.hours),
            String.format(Locale.US, "%.2f", entry.hourlyRate),
            String.format(Locale.US, "%.2f", entry.totalEarnings),
            statusStr,
            entry.notes,
            entry.currency
        ).joinToString("\t") { cell ->
            if (cell.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
        }
    }
    val tsvContent = (listOf(headers.joinToString("\t")) + rows).joinToString("\n")
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Excel Report", tsvContent)
    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, "הקובץ הועתק בהצלחה!", Toast.LENGTH_SHORT).show()
}

fun formatSelectedShiftsForWhatsApp(selectedEntries: List<WorkEntry>): String {
    val allWorkers = selectedEntries.flatMap { getWorkerNamesFromEntry(it.groupWorkersJson) }.distinct()
    val isWorker = allWorkers.isNotEmpty()
    return generateWhatsAppReportText(selectedEntries, if (isWorker) "הכל" else "קטגוריה", if (isWorker) allWorkers.first() else "")
}

class MainActivity : ComponentActivity() {
    private val viewModel: WorkViewModel by viewModels {
        WorkViewModelFactory(application)
    }

    val intentActionFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.example.api.FirebaseSafeInitializer.init(applicationContext)
            com.example.api.AuthManager.init(applicationContext)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Firebase startup safeguarded: ${e.localizedMessage}")
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        handleIntent(intent)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        setContent {
            MyApplicationTheme {
                // Force RTL Layout Direction representing the Hebrew language requirements strictly
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    androidx.compose.foundation.LocalOverscrollConfiguration provides null
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            MainAppContent(
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 680.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.performAutoBackup()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intentActionFlow.value = intent?.action
        if (intent?.action == "com.example.ACTION_START_SHIFT") {
            // Need to retrieve default rate for "עצמאי" if it exists, otherwise use 40.0
            val rate = viewModel.categories.value.find { it.name == "עצמאי" }?.defaultRate ?: 40.0
            viewModel.startActiveShift("עצמאי", rate)
            intentActionFlow.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: WorkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.example.api.AuthManager.handleGoogleSignInResult(result.data) { success, errorMsg ->
            if (success) {
                Toast.makeText(context, "התחברת בהצלחה למערכת", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, errorMsg ?: "ההתחברות בוטלה", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val signInForSync: () -> Unit = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                if (com.example.api.WebPlatformBridge.isWebTarget) {
                    com.example.api.WebPlatformBridge.signInWithWebOAuthPopup(context) { success, errorMsg ->
                        if (success) {
                            Toast.makeText(context, "התחברת בהצלחה למערכת", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, errorMsg ?: "ההתחברות בוטלה", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else try {
                    val client = com.example.api.AuthManager.getGoogleSignInClient(context)
                    if (client != null) {
                        googleSignInLauncher.launch(client.signInIntent)
                    } else {
                        Toast.makeText(context, "החיבור לחשבון עדיין לא הוגדר. אפשר להמשיך להשתמש באפליקציה.", Toast.LENGTH_LONG).show()
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "לא ניתן להתחבר כעת. אפשר להמשיך להשתמש באפליקציה.", Toast.LENGTH_LONG).show()
                }
    }

    // Runtime permission launcher for POST_NOTIFICATIONS
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "שים לב: התרעות המשמרת לא יופיעו ללא אישור", Toast.LENGTH_LONG).show()
        }
    }

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val distinctCategories = remember(categories) { categories.distinctBy { it.name.trim() } }
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<WorkEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchDialogOpen by remember { mutableStateOf(false) }

    val activeShiftStartTime by viewModel.activeShiftStartTime.collectAsStateWithLifecycle()
    
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isFocusedMode by remember { mutableStateOf(false) }

    LaunchedEffect(activeShiftStartTime, lastInteractionTime) {
        if (activeShiftStartTime != null && selectedTab == 0) {
            isFocusedMode = false
            kotlinx.coroutines.delay(7000)
            isFocusedMode = true
        } else {
            isFocusedMode = false
        }
    }

    val focusAlpha by animateFloatAsState(targetValue = if (isFocusedMode) 0.15f else 1f, label = "focusAlpha")

    val intentAction by (context as MainActivity).intentActionFlow.collectAsStateWithLifecycle()
    LaunchedEffect(intentAction) {
        if (intentAction == "com.example.ACTION_IMPORT_EXCEL") {
            showSettings = true
            (context as MainActivity).intentActionFlow.value = null
        }
    }

    BackHandler(enabled = selectedTab != 0 || showSettings) {
        if (showSettings) {
            showSettings = false
        } else {
            selectedTab = 0
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(modifier = Modifier.graphicsLayer { alpha = focusAlpha }) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "שכר עבודות אישי",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    fontFamily = com.example.ui.theme.RubikFontFamily
                                )
                            }
                        },
                        actions = {
                            if (selectedTab == 1) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        isSearchDialogOpen = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "חיפוש",
                                        tint = Color(0xFF8E8E93)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    showSettings = true 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "ניהול וקטגוריות",
                                    tint = Color(0xFF8E8E93)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior
                    )
                }
            },
            bottomBar = {
                Box(modifier = Modifier.graphicsLayer { alpha = focusAlpha }) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(58.dp),
                        windowInsets = WindowInsets(0.dp)
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedTab = 0 
                            },
                            icon = { Icon(imageVector = Icons.Outlined.GridView, contentDescription = "ראשי", modifier = Modifier.size(20.dp)) },
                            label = { Text("ראשי", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6366F1),
                                unselectedIconColor = Color(0xFF8E8E93),
                                selectedTextColor = Color(0xFF6366F1),
                                unselectedTextColor = Color(0xFF8E8E93),
                                indicatorColor = Color(0xFF1E1E1E)
                            ),
                            modifier = Modifier.testTag("tab_0").pressScale()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedTab = 1 
                            },
                            icon = { Icon(imageVector = Icons.Outlined.History, contentDescription = "היסטוריה", modifier = Modifier.size(20.dp)) },
                            label = { Text("היסטוריה", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6366F1),
                                unselectedIconColor = Color(0xFF8E8E93),
                                selectedTextColor = Color(0xFF6366F1),
                                unselectedTextColor = Color(0xFF8E8E93),
                                indicatorColor = Color(0xFF1E1E1E)
                            ),
                            modifier = Modifier.testTag("tab_1").pressScale()
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    Crossfade(
                        targetState = selectedTab,
                        label = "tab_crossfade",
                        animationSpec = tween(durationMillis = 200)
                    ) { currentTab ->
                        when (currentTab) {
                            0 -> {
                                val activeShiftCategory by viewModel.activeShiftCategory.collectAsStateWithLifecycle()
                                val activeShiftRate by viewModel.activeShiftRate.collectAsStateWithLifecycle()
                                val workersDirectory by viewModel.workersDirectory.collectAsStateWithLifecycle()
                                DashboardScreen(
                                    viewModel = viewModel,
                                    stats = stats,
                                    categories = distinctCategories,
                                    workersDirectory = workersDirectory,
                                    activeShiftStartTime = activeShiftStartTime,
                                    activeShiftCategory = activeShiftCategory,
                                    activeShiftRate = activeShiftRate,
                                    focusAlpha = focusAlpha,
                                    onStartShift = { cat, rate ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (!isGranted) {
                                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        viewModel.startActiveShift(cat, rate)
                                    },
                                    onStopShift = { viewModel.stopActiveShift() },
                                    onSaveShift = { cat, hrs, rate ->
                                        viewModel.addEntry(
                                            category = cat,
                                            dateMillis = System.currentTimeMillis(),
                                            isTimeRange = false,
                                            startTime = null,
                                            endTime = null,
                                            hours = hrs,
                                            rate = rate,
                                            notes = "משמרת פעילה (טיימר החישוב)"
                                        )
                                        viewModel.stopActiveShift()
                                    },
                                    recentEntries = entries,
                                    onTogglePaid = { entry ->
                                        triggerHapticFeedback(context, isDestructive = false)
                                        viewModel.togglePaymentStatus(entry)
                                    },
                                    onViewAll = { selectedTab = 1 },
                                    onAddEntry = { category, date, isRange, start, end, hours, rate, notes, isGroupShift, empRate, workerRate, groupJson, currency ->
                                        viewModel.addEntry(category, date, isRange, start, end, hours, rate, notes, false, isGroupShift, empRate, workerRate, groupJson, currency)
                                    },
                                    onAddCategory = { name, rate ->
                                        viewModel.addCategory(name, rate)
                                    },
                                    onDeleteCategory = { category ->
                                        triggerHapticFeedback(context, isDestructive = true)
                                        viewModel.deleteCategory(category)
                                    },
                                    selectedTab = selectedTab
                                )
                            }
                            1 -> ShiftsScreen(
                                viewModel = viewModel,
                                entries = entries,
                                categories = distinctCategories,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onTogglePaid = { entry ->
                                    triggerHapticFeedback(context, isDestructive = false)
                                    viewModel.togglePaymentStatus(entry)
                                },
                                onEdit = { entryToEdit = it },
                                onDelete = { entry ->
                                    triggerHapticFeedback(context, isDestructive = true)
                                    viewModel.deleteEntry(entry)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Forms
    if (showAddDialog) {
        ShiftFormDialog(
            categories = distinctCategories,
            onDismiss = { showAddDialog = false },
            onSave = { category, date, isRange, start, end, hours, rate, notes ->
                viewModel.addEntry(category, date, isRange, start, end, hours, rate, notes)
                showAddDialog = false
                triggerHapticFeedback(context, isDestructive = false)
                Toast.makeText(context, "הדיווח נשמר בהצלחה!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (entryToEdit != null) {
        EditShiftBottomSheet(
            entry = entryToEdit!!,
            categories = distinctCategories,
            viewModel = viewModel,
            onDismiss = { entryToEdit = null },
            onSave = { category, date, isRange, start, end, hours, rate, notes, isPaid, isGroup, empRate, workerRate, groupJson ->
                entryToEdit?.let { old ->
                    viewModel.editEntry(
                        id = old.id,
                        category = category,
                        dateMillis = date,
                        isTimeRange = isRange,
                        startTime = start,
                        endTime = end,
                        hours = hours,
                        rate = rate,
                        notes = notes,
                        isPaid = isPaid,
                        isGroupShift = isGroup,
                        employerRate = empRate,
                        workerRate = workerRate,
                        groupWorkersJson = groupJson,
                        currency = old.currency
                    )
                }
                entryToEdit = null
                triggerHapticFeedback(context, isDestructive = false)
                Toast.makeText(context, "הדיווח עודכן בהצלחה!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettings = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .fillMaxWidth(0.95f)
                        .heightIn(max = 680.dp)
                        .wrapContentHeight()
                        .animateContentSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xE6121212))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume click inside dialog
                        )
                ) {
                    ManagementScreen(
                        viewModel = viewModel,
                        categories = distinctCategories,
                        onNavigateBack = { showSettings = false },
                        onSignIn = signInForSync
                    )
                }
            }
        }
    }

    if (isSearchDialogOpen) {
        Dialog(
            onDismissRequest = { isSearchDialogOpen = false }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "חיפוש משמרות",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input"),
                        placeholder = { Text("חיפוש משמרת (קטגוריה, הערה)...", color = Color.Gray, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "חיפוש", tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "נקה", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5C6BC0),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { isSearchDialogOpen = false }
                        ) {
                            Text("אישור", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }


}


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
        style = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = glowColor.copy(alpha = 0.8f),
                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                blurRadius = 10f
            )
        ),
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
    onStartShift: (String, Double) -> Unit,
    onStopShift: () -> Unit,
    onSaveShift: (String, Double, Double) -> Unit,
    recentEntries: List<WorkEntry>,
    onTogglePaid: (WorkEntry) -> Unit,
    onViewAll: () -> Unit,
    onAddEntry: (String, Long, Boolean, String?, String?, Double, Double, String, Boolean, Double?, Double?, String, String) -> Unit,
    onAddCategory: (String, Double) -> Unit,
    onDeleteCategory: (WorkCategory) -> Unit,
    selectedTab: Int = 0
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
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
    var selectedCurrency by remember(defaultCurr) { mutableStateOf(defaultCurr) }

    var hourlyRateStr by remember { mutableStateOf("40") }
    var selectedCategory by remember { mutableStateOf("עצמאי") }
    val selectedDefaultRate = categories.firstOrNull { it.name == selectedCategory }?.defaultRate
    LaunchedEffect(selectedCategory, selectedDefaultRate) {
        selectedDefaultRate?.let { hourlyRateStr = it.toString() }
    }
    var isReportCardExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showQuickShiftDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<WorkCategory?>(null) }

    var isManualMode by remember { mutableStateOf(false) } // false = שעון, true = ידני
    var isAiMode by remember { mutableStateOf(false) }
    var aiInputText by remember { mutableStateOf("") }
    var isAiParsing by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var startTimeStr by remember { mutableStateOf("09:00") }
    var endTimeStr by remember { mutableStateOf("17:00") }
    var breakMinutesStr by remember { mutableStateOf("0") }
    var notesText by remember { mutableStateOf("") }
    var manualHoursStr by remember { mutableStateOf("8.0") }

    var showErrorHours by remember { mutableStateOf(false) }
    var showErrorRate by remember { mutableStateOf(false) }

    var isGroupShift by remember { mutableStateOf(false) }
    var employerRateStr by remember { mutableStateOf("") }
    var workerRateStr by remember { mutableStateOf("") }
    var showSeparateRates by remember { mutableStateOf(false) }

    val groupWorkers = remember { mutableStateListOf<WorkViewModel.GroupWorkerState>() }
    var currentWorkerName by remember { mutableStateOf("") }
    var currentWorkerHours by remember { mutableStateOf("0.0") }
    var showWorkerAutocomplete by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isGroupShift) {
        if (isGroupShift) {
            groupWorkers.clear()
            currentWorkerName = ""
            val hDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
            currentWorkerHours = if (hDouble > 0) String.format(Locale.US, "%.2f", hDouble) else "0.0"
            showWorkerAutocomplete = false
        } else {
            groupWorkers.clear()
            currentWorkerName = ""
            currentWorkerHours = "0.0"
            showWorkerAutocomplete = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizer = remember { android.speech.SpeechRecognizer.createSpeechRecognizer(context) }
    val speechRecognizerIntent = remember {
        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "he-IL")
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                speechRecognizer.startListening(speechRecognizerIntent)
                isListening = true
            } catch (e: Exception) {
                Toast.makeText(context, "שגיאה בהפעלת הקלטה: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "הקלטה קולית דורשת אישור הרשאה",
                    actionLabel = "הגדרות",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "לא ניתן לפתוח הגדרות", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val processAIInput: suspend (String) -> Unit = { text ->
        if (text.isBlank()) {
            Toast.makeText(context, "נא להזין או להקליט טקסט לפענוח", Toast.LENGTH_SHORT).show()
        } else if (!isAiParsing) {
            isAiParsing = true
            try {
                val cats = viewModel.categories.value.map { it.name }
                val results = com.example.api.GeminiParser.parseNaturalLanguageToShifts(text, cats, viewModel.categories.value.associate { it.name to it.defaultRate })
                if (!results.isNullOrEmpty()) {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("אישור המשמרות שפוענחו")
                        .setMessage(results.joinToString("\n\n") { "${it.category} | ${SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(it.date))}\n${it.hours} שעות × ${it.hourlyRate} ${it.currency}\n${it.notes}" })
                        .setNegativeButton("ביטול", null)
                        .setPositiveButton("הוספת המשמרות") { _, _ ->
                            viewModel.addShifts(results)
                            aiInputText = ""
                            Toast.makeText(context, "המשמרות הועברו לשמירה", Toast.LENGTH_SHORT).show()
                        }.show()

                } else {
                    isManualMode = true
                    isAiMode = false
                    isGroupShift = false
                    Toast.makeText(context, "לא הצלחנו לפענח את המשמרת אוטומטית. אנא הזן ידנית.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isManualMode = true
                isAiMode = false
                isGroupShift = false
                Toast.makeText(context, "AI Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isAiParsing = false
            }
        }
    }

    val recognitionListener = remember {
        object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "שגיאת שמע"
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> "שגיאת לקוח"
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "חוסר הרשאות"
                    android.speech.SpeechRecognizer.ERROR_NETWORK -> "שגיאת רשת"
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "פסק זמן לרשת"
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "לא נמצאה התאמה"
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "מזהה קולי עסוק"
                    android.speech.SpeechRecognizer.ERROR_SERVER -> "שגיאת שרת"
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא זוהה דיבור"
                    else -> "שגיאה לא ידועה"
                }
                Toast.makeText(context, "שגיאת זיהוי: $errorMsg", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    aiInputText = text
                    scope.launch {
                        processAIInput(text)
                    }
                } else if (aiInputText.isNotBlank()) {
                    scope.launch {
                        processAIInput(aiInputText)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    aiInputText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .testTag("dashboard_scroll_container")
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Auto-Snap when shift becomes active
            LaunchedEffect(activeShiftStartTime) {
                if (activeShiftStartTime != null) {
                    pagerState.animateScrollToPage(2)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val scale = 1f - (kotlin.math.abs(pageOffset) * 0.05f)
                    val alpha = 1f - (kotlin.math.abs(pageOffset) * 0.3f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .height(200.dp) // Fixed height to prevent layout jumping
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .then(
                                if (page == 2 && activeShiftStartTime != null) {
                                    Modifier.background(
                                        brush = Brush.linearGradient(colors = listOf(Color(0x80064E3B), Color(0x331E293B))),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (page == 2 && activeShiftStartTime != null) Color.Transparent else Color(0x331E293B)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (page == 2 && activeShiftStartTime != null) Color(0xFF10B981) else Color(0x26FFFFFF)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .padding(16.dp)
                        ) {
                            when (page) {
                                0 -> {
                                    // Page 1 - Current Effort
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = "השבוע והחודש", color = Color(0xFF8E8E93), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("שבוע", color = Color(0xFF818CF8), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = stats.thisWeek.totalEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 22.sp, glowColor = Color(0xFF818CF8))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                AnimatedGlowingEarnings(targetValue = stats.thisWeek.totalHours, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 13.sp, glowColor = Color(0xFF818CF8), isCurrency = false, isHours = true)
                                            }
                                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                Text("חודש", color = Color(0xFF34D399), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = stats.thisMonth.totalEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 22.sp, glowColor = Color(0xFF34D399))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                AnimatedGlowingEarnings(targetValue = stats.thisMonth.totalHours, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 13.sp, glowColor = Color(0xFF34D399), isCurrency = false, isHours = true)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val currentMonthEarnings = stats.thisMonth.totalEarnings.toFloat()
                                        val monthlyTarget = 10000f
                                        val progress = if (monthlyTarget > 0) (currentMonthEarnings / monthlyTarget).coerceIn(0f, 1f) else 0f

                                        if (currentMonthEarnings > 0f) {
                                            BoxWithConstraints(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(12.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                val maxWidth = maxWidth
                                                // Track background
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .background(Color(0xFF2C2C2E), RoundedCornerShape(2.dp))
                                                )
                                                // Progress line
                                                Box(
                                                    modifier = Modifier
                                                        .width(maxWidth * progress)
                                                        .height(4.dp)
                                                        .background(Color(0xFF34D399), RoundedCornerShape(2.dp))
                                                )
                                                // Green dot indicating current progress
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = (maxWidth * progress) - 6.dp)
                                                        .size(12.dp)
                                                        .background(Color(0xFF34D399), CircleShape)
                                                )
                                            }
                                        } else {
                                            // If currentMonthEarnings == 0f, draw the progress line as entirely empty and keep design clean
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(Color(0xFF2C2C2E), RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    // Page 2 - Financial Summary
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = "סיכום כל הזמנים", color = Color(0xFF8E8E93), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val totalEarnings = stats.total.totalEarnings
                                        val paidEarnings = stats.total.paidEarnings
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text("סה\"כ הכנסות", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = totalEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 24.sp, glowColor = Color(0xFF34D399))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("שולם", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = paidEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 20.sp, glowColor = Color(0xFF6366F1))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text("סה\"כ שעות עבודה", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = stats.total.totalHours, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 18.sp, glowColor = Color(0xFF10B981), isCurrency = false, isHours = true)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("ממתין לתשלום", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                AnimatedGlowingEarnings(targetValue = stats.total.unpaidEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 18.sp, glowColor = Color(0xFFF59E0B))
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        // Minimalist Sparkline Chart
                                        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                                            val path = androidx.compose.ui.graphics.Path()
                                            val dataPoints = listOf(0.2f, 0.5f, 0.4f, 0.7f, 0.6f, 0.9f, 0.8f, 1.0f)
                                            val width = size.width
                                            val height = size.height
                                            val stepX = width / (dataPoints.size - 1)
                                            
                                            dataPoints.forEachIndexed { index, point ->
                                                val x = index * stepX
                                                val y = height - (point * height)
                                                if (index == 0) {
                                                    path.moveTo(x, y)
                                                } else {
                                                    // Add cubic bezier for smooth curve
                                                    val prevX = (index - 1) * stepX
                                                    val prevY = height - (dataPoints[index - 1] * height)
                                                    val cx1 = prevX + stepX / 2f
                                                    val cy1 = prevY
                                                    val cx2 = prevX + stepX / 2f
                                                    val cy2 = y
                                                    path.cubicTo(cx1, cy1, cx2, cy2, x, y)
                                                }
                                            }
                                            drawPath(
                                                path = path,
                                                color = Color(0xFF6366F1),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 4f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }
                                }
                                2 -> {
                                    // Page 3 - Today & Active Clock
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val isRunning = activeShiftStartTime != null
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "היום", color = if (isRunning) Color(0xFF10B981) else Color(0xFF8E8E93), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            if (isRunning) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                                                    Text("עובד כעת", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        if (isRunning) {
                                            val currentSystemTimeSeconds = System.currentTimeMillis() / 1000L
                                            val startSystemTimeSeconds = activeShiftStartTime / 1000L
                                            val liveEarnings = (currentSystemTimeSeconds - startSystemTimeSeconds) * (activeShiftRate / 3600.0)
                                            val accumulatedEarnings = Math.round(liveEarnings * 100.0) / 100.0
                                            val hours = tickerSeconds / 3600
                                            val mins = (tickerSeconds % 3600) / 60
                                            val secs = tickerSeconds % 60
                                            val timeString = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                                Column {
                                                    Text("נצבר", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                    AnimatedGlowingEarnings(targetValue = accumulatedEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 28.sp, glowColor = Color(0xFF10B981))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("זמן", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                    Text(timeString, fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color(0xFF34D399))
                                                }
                                            }
                                        } else {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column {
                                                    Text("הכנסות היום", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                    AnimatedGlowingEarnings(targetValue = stats.today.totalEarnings, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 28.sp, glowColor = Color(0xFF34D399))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("סה\"כ שעות היום", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                                    AnimatedGlowingEarnings(targetValue = stats.today.totalHours, runCountAnimationTrigger = runCountAnimationTrigger, fontSize = 24.sp, glowColor = Color(0xFF10B981), isCurrency = false, isHours = true)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                // Pager Indicator
                Row(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = focusAlpha },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val color = if (isSelected) Color(0xFF6366F1) else Color(0xFF44444F)
                        val width = if (isSelected) 24.dp else 8.dp
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .background(color, RoundedCornerShape(4.dp))
                        )
                    }
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
                                colors = OutlinedTextFieldDefaults.colors(
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
                                colors = OutlinedTextFieldDefaults.colors(
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
                    .animateContentSize()
                    .testTag("add_shift_form_card")
                    .graphicsLayer { alpha = focusAlpha }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                isReportCardExpanded = !isReportCardExpanded
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left item: expand/collapse icon
                        Icon(
                            imageVector = if (isReportCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isReportCardExpanded) "כווץ" else "הרחב",
                            tint = Color(0xFF5C6BC0),
                            modifier = Modifier.size(28.dp)
                        )

                        // Right item: "דיווח חדש"
                        Text(
                            text = "דיווח חדש",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = com.example.ui.theme.AssistantFontFamily
                        )
                    }

                    AnimatedVisibility(
                        visible = isReportCardExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                    // 1.5. Unified Mode Selector (שעון / ידני / קבוצה / AI)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "הקלד את פרטי המשמרת שלך בטקסט חופשי (עברית או אנגלית):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.align(Alignment.End)
                            )
                            
                            OutlinedTextField(
                                value = aiInputText,
                                onValueChange = { aiInputText = it },
                                placeholder = { 
                                    Text(
                                        text = "לדוגמה: אתמול עבדתי עצמאי 8 שעות בתעריף 50 ש\"ח, הערה: הדרכה וישיבת צוות",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    ) 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("ai_free_text_input"),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(textAlign = TextAlign.Right, color = Color.White, fontSize = 14.sp),
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        if (isListening) {
                                            Text(
                                                text = "מקשיב...",
                                                color = Color(0xFFEF4444),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    speechRecognizer.stopListening()
                                                    isListening = false
                                                },
                                                modifier = Modifier.testTag("ai_mic_btn")
                                            ) {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Filled.Stop,
                                                    contentDescription = "עצור הקלטה",
                                                    tint = Color(0xFFEF4444)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val permission = android.Manifest.permission.RECORD_AUDIO
                                                    val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context,
                                                        permission
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    
                                                    if (isGranted) {
                                                        speechRecognizer.startListening(speechRecognizerIntent)
                                                        isListening = true
                                                    } else {
                                                        requestPermissionLauncher.launch(permission)
                                                    }
                                                },
                                                modifier = Modifier.testTag("ai_mic_btn")
                                            ) {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Mic,
                                                    contentDescription = "הקלטה קולית",
                                                    tint = Color(0xFF8E8E93)
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF3F3F46),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            if (isListening) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "מקשיב... דבר כעת",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFEF4444), shape = CircleShape)
                                    )
                                }
                            }

                            // Quick examples suggestion row
                            Text(
                                text = "הצעות מהירות (לחץ לבדיקה):",
                                fontSize = 11.sp,
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.align(Alignment.End)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                val examples = listOf(
                                    "אתמול עבדתי 6.5 שעות עצמאי",
                                    "היום עבדתי 8 שעות בתעריף 60",
                                    "יום ראשון שעבר 7 שעות, הערה: בדיקות"
                                )
                                examples.forEach { example ->
                                    SuggestionChip(
                                        onClick = { aiInputText = example },
                                        label = { Text(example, fontSize = 11.sp, color = Color.White) },
                                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFF1C1C1E)
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "פענוח באמצעות ג׳מיני",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontFamily = com.example.ui.theme.AssistantFontFamily,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            )

                            Button(
                                onClick = {
                                    if (aiInputText.isBlank()) {
                                        Toast.makeText(context, "נא להזין טקסט לפענוח", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        processAIInput(aiInputText)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0)),
                                enabled = !isAiParsing
                            ) {
                                if (isAiParsing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("מפענח משמרת...", fontWeight = FontWeight.Bold, color = Color.White)
                                } else {
                                    Text("שמור משמרת עם AI", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        // 3. "תאריך" Box Selection
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        Text(
                            text = "תאריך",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sdfDate = remember { SimpleDateFormat("dd.MM.yyyy", Locale.US) }
                        val dateStr = sdfDate.format(Date(selectedDateMillis))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newCal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            selectedDateMillis = newCal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF8E8E93)
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 4. Entry/Exit Dropdown Selectors (Side-by-side)
                    if (!isManualMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left item: יציאה
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "יציאה",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8E8E93),
                                    modifier = Modifier.align(Alignment.End)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                        .clickable {
                                            val t = endTimeStr.split(":")
                                            val h = t.getOrNull(0)?.toIntOrNull() ?: 17
                                            val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                                            TimePickerDialog(context, { _, hour, minute ->
                                                endTimeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                                            }, h, m, true).show()
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                                        Text(endTimeStr, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    }
                                }
                            }

                            // Right item: כניסה
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "כניסה",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8E8E93),
                                    modifier = Modifier.align(Alignment.End)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                        .clickable {
                                            val t = startTimeStr.split(":")
                                            val h = t.getOrNull(0)?.toIntOrNull() ?: 9
                                            val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                                            TimePickerDialog(context, { _, hour, minute ->
                                                startTimeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                                            }, h, m, true).show()
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                                        Text(startTimeStr, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // Manual hours input field if selected "ידני"
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "שעות עבודה",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.align(Alignment.End)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val currentHoursDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
                            val hVal = currentHoursDouble.toInt()
                            val mVal = Math.round((currentHoursDouble - hVal) * 60).toInt()
                            val displayHoursText = if (manualHoursStr.isBlank()) {
                                "לחץ לבחירת שעות עבודה..."
                            } else {
                                formatCleanHours(currentHoursDouble)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, if (showErrorHours) Color.Red else Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                    .clickable {
                                        TimePickerDialog(context, { _, hour, minute ->
                                            val calculatedHours = hour + (minute / 60.0)
                                            manualHoursStr = String.format(Locale.US, "%.2f", calculatedHours)
                                            if (isGroupShift) {
                                                for (i in groupWorkers.indices) {
                                                    groupWorkers[i] = groupWorkers[i].copy(hours = calculatedHours)
                                                }
                                                currentWorkerHours = String.format(Locale.US, "%.2f", calculatedHours)
                                            }
                                            showErrorHours = false
                                        }, hVal, mVal, true).show()
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccessTime,
                                        contentDescription = null,
                                        tint = Color(0xFF5C6BC0)
                                    )
                                    Text(
                                        text = displayHoursText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (manualHoursStr.isBlank()) Color(0xFF64748B) else Color.White
                                    )
                                }
                            }
                            if (showErrorHours) {
                                Text(
                                    text = "נא להזין כמות שעות תקינה",
                                    color = Color.Red,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // 5. "הפסקה (דקות)" Standard Input Card
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "הפסקה",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalMinutes = breakMinutesStr.toIntOrNull() ?: 0
                        val bHours = totalMinutes / 60
                        val bMins = totalMinutes % 60
                        val displayBreakText = if (breakMinutesStr.isBlank() || breakMinutesStr == "0") {
                            "0 שעות"
                        } else {
                            formatCleanHours(totalMinutes / 60.0)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    TimePickerDialog(context, { _, hour, minute ->
                                        val totalMins = hour * 60 + minute
                                        breakMinutesStr = totalMins.toString()
                                    }, bHours, bMins, true).show()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = Color(0xFF5C6BC0)
                                )
                                Text(
                                    text = displayBreakText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 6. "תעריף שעתי" Input Card
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Micro-toggle chip adjacent to Hourly Rate input to switch between "₪" and "$"
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selectedCurrency == "₪") Color(0xFF5C6BC0) else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedCurrency = "₪" }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("₪", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedCurrency == "₪") Color.White else Color(0xFF8E8E93))
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selectedCurrency == "$") Color(0xFF5C6BC0) else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedCurrency = "$" }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedCurrency == "$") Color.White else Color(0xFF8E8E93))
                                }
                            }

                            Text(
                                "תעריף לשעה",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                                value = hourlyRateStr,
                                onValueChange = { newValue ->
                                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                                    val dotCount = filtered.count { it == '.' }
                                    if (dotCount <= 1) {
                                        hourlyRateStr = filtered
                                        showErrorRate = filtered.isBlank()
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_rate_input"),
                                isError = showErrorRate,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF5C6BC0),
                                    unfocusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF3F3F46),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                        )
                        if (showErrorRate) {
                            Text(
                                text = "נא להזין תעריף תקין",
                                color = Color.Red,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                            )
                        }
                        
                        if (isGroupShift) {
                            Text(
                                text = "+ הגדר תעריפים נפרדים לקבוצה",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .align(Alignment.Start)
                                    .clickable { showSeparateRates = !showSeparateRates }
                            )
                            
                            AnimatedVisibility(visible = showSeparateRates) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = employerRateStr,
                                        onValueChange = { employerRateStr = it },
                                        label = { Text("תעריף מעסיק (לקבלן)", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5C6BC0), unfocusedBorderColor = Color(0xFF44444F), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                    )
                                    OutlinedTextField(
                                        value = workerRateStr,
                                        onValueChange = { workerRateStr = it },
                                        label = { Text("תעריף לעובד", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5C6BC0), unfocusedBorderColor = Color(0xFF44444F), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                    )
                                }
                            }
                        }
                    }

                    // 7. Employer ("מעסיק") Dropdown Selector inside custom container Card with integrated addition / delete
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ חדש",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5C6BC0),
                                modifier = Modifier
                                    .clickable { showAddCategoryDialog = true }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                            Text(
                                "מעסיק",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Delete current category button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF3A1C1C), shape = RoundedCornerShape(12.dp))
                                    .clickable {
                                        val matched = categories.find { it.name == selectedCategory }
                                        if (matched != null) {
                                            categoryToDelete = matched
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Dynamic dropdown box selector
                            var expandedDropdown by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                    .clickable { expandedDropdown = true }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                                    Text(
                                        text = selectedCategory,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name, fontSize = 14.sp) },
                                            onClick = {
                                                selectedCategory = cat.name
                                                val matchedRate = cat.defaultRate
                                                hourlyRateStr = matchedRate.toString()
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 8. "הערות" Description Field
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "הערות",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            placeholder = { Text("מה עשית במשמרת?", color = Color(0xFF64748B)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5C6BC0),
                                unfocusedBorderColor = Color(0xFF3F3F46),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedPlaceholderColor = Color(0xFF64748B),
                                unfocusedPlaceholderColor = Color(0xFF64748B)
                            )
                        )
                    }

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
                                    .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
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
                                            colors = OutlinedTextFieldDefaults.colors(
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
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF44444F),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                        IconButton(onClick = { groupWorkers.removeAt(index) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
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
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
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
                                        modifier = Modifier.width(80.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
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
                                                showWorkerAutocomplete = false
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFF5C6BC0), CircleShape)
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "Add", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Spacer before save button
                    Spacer(modifier = Modifier.height(6.dp))

                    // 9. Full width navy-blue "שמור" button
                    Button(
                        onClick = {
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
                                val durationVal = eMin - sMin - bMins
                                maxOf(0.0, durationVal / 60.0)
                            }
                            val finalRate = hourlyRateStr.toDoubleOrNull() ?: 40.0
                            val eRate = if (isGroupShift && showSeparateRates) employerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                            val wRate = if (isGroupShift && showSeparateRates) workerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                            val gJson = if (isGroupShift && groupWorkers.isNotEmpty()) {
                                // Simple JSON Array construction for Workers
                                val arr = org.json.JSONArray()
                                groupWorkers.forEach { w ->
                                    val obj = org.json.JSONObject()
                                    obj.put("name", w.name)
                                    obj.put("hours", w.hours)
                                    obj.put("isPaid", w.isPaid)
                                    arr.put(obj)
                                }
                                arr.toString()
                            } else ""

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
                            .pressScale()
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
                    }
                }
            }

            // Recent Shifts Section
            val latestThreeShifts = remember(recentEntries) {
                recentEntries.sortedByDescending { it.createdAt }.take(3)
            }

            if (latestThreeShifts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
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
                            textAlign = TextAlign.Right
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

    Box(
        modifier = Modifier
            .align(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        val isRunning = activeShiftStartTime != null
        var showStopConfirmationDialog by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = !showQuickShiftDialog,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    triggerHapticFeedback(context, isDestructive = false)
                    if (isRunning) {
                        showStopConfirmationDialog = true
                    } else {
                        showQuickShiftDialog = true
                    }
                },
                containerColor = if (isRunning) Color(0xFF10B981) else Color(0xFF1E2235),
                contentColor = if (isRunning) Color.White else Color(0xFFF1F5F9),
                modifier = Modifier
                    .testTag("live_shift_fab")
                    .border(
                        1.dp,
                        if (isRunning) Color(0x6610B981) else Color(0x33818CF8),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "סיים משמרת פעילה" else "התחל משמרת פעילה",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
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
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }

    var dialogCategory by remember { mutableStateOf("עצמאי") }
    val dialogDefaultRate = categories.firstOrNull { it.name == dialogCategory }?.defaultRate ?: 40.0
    var dialogRateStr by remember(dialogCategory, dialogDefaultRate, showQuickShiftDialog) {
        mutableStateOf(dialogDefaultRate.toString())
    }
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart
    ) {
        AnimatedVisibility(
            visible = showQuickShiftDialog,
            enter = androidx.compose.animation.expandIn(
                expandFrom = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) + fadeIn(),
            exit = androidx.compose.animation.shrinkOut(
                shrinkTowards = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) + fadeOut(),
            modifier = Modifier.padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .pointerInput(Unit) { /* intercept touches */ }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("הגדרת משמרת מהירה ⏱️", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = dialogCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("קטגוריה / מעסיק") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
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
                    OutlinedTextField(
                        value = dialogRateStr,
                        onValueChange = { dialogRateStr = it },
                        label = { Text("תעריף שעתי") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val lastEntry = recentEntries.firstOrNull()
                                val cat = lastEntry?.category ?: "עצמאי"
                                val rate = lastEntry?.hourlyRate ?: 40.0
                                onStartShift(cat, rate)
                                showQuickShiftDialog = false
                            }
                        ) {
                            Text("דלג", color = Color(0xFF8E8E93))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val rateVal = dialogRateStr.toDoubleOrNull() ?: 40.0
                                onStartShift(dialogCategory, rateVal)
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
    
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
                                selectedCategory = "עצמאי"
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
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(8.dp))
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
                        fontWeight = FontWeight.Light,
                        fontSize = 10.sp,
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
                            text = String.format(Locale.US, "₪%,.1f", entry.totalEarnings),
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFF8E8E93)
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(statusColor, shape = CircleShape)
                        )
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF8E8E93),
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            } else {
                                Text(
                                    text = "שעות שהוזנו ידנית: ${String.format(Locale.US, "%.1f", entry.hours)} שעות",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF8E8E93),
                                    fontFamily = com.example.ui.theme.AssistantFontFamily
                                )
                            }

                            Text(
                                text = "תעריף שעתי: ₪${entry.hourlyRate}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF8E8E93),
                                fontFamily = com.example.ui.theme.AssistantFontFamily
                            )
                            
                            if (entry.notes.isNotBlank()) {
                                Text(
                                    text = "הערות: ${entry.notes}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
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
                                    fontSize = 9.sp,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
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
                text = String.format(Locale.US, "₪%,.2f", stats.totalEarnings),
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
            containerColor = Color(0xFF121212)
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
                    text = String.format(Locale.US, "₪%,.0f", entry.totalEarnings),
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

// ================= SHIFTS SCREEN =================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShiftsScreen(
    viewModel: WorkViewModel,
    entries: List<WorkEntry>,
    categories: List<WorkCategory>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTogglePaid: (WorkEntry) -> Unit,
    onEdit: (WorkEntry) -> Unit,
    onDelete: (WorkEntry) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("הכל") }
    var sortOption by remember { mutableStateOf("newest") } // "newest", "oldest", "latest_added"
    var statusFilter by remember { mutableStateOf("הכל") } // "הכל", "ממתין", "שולם"
    var currencyFilter by remember { mutableStateOf("הכל") } // "הכל", "₪", "$"

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showCopyMenu by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(sortOption) {
        lazyListState.scrollToItem(0)
    }

    // Date range picker states
    var filterType by remember { mutableStateOf("הכל") } // "הכל", "חודש", "טווח"
    var selectedMonthYear by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }) }
    var customStartDate by remember { mutableStateOf(System.currentTimeMillis() - 86400000 * 7) } // 7 days ago
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    var importText by remember { mutableStateOf("") }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showImportBox by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Backup states for swipe undo
    var lastDeletedEntry by remember { mutableStateOf<WorkEntry?>(null) }
    var lastToggledEntry by remember { mutableStateOf<WorkEntry?>(null) }
    
    // Multi-select mode and exact delete rule states
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedShiftIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showShiftDeleteConfirm by remember { mutableStateOf<WorkEntry?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    // Map categories names using "כל הקטגוריות" as "הכל"
    val filterOptions = listOf("הכל") + categories.map { it.name.trim() }.distinct()

    // Filter list including both search, categories, status, date and currency filters
    val filteredEntries = remember(entries, selectedCategoryFilter, searchQuery, filterType, selectedMonthYear, customStartDate, customEndDate, statusFilter, currencyFilter) {
        entries.filter { entry ->
            val matchesCurrency = currencyFilter == "הכל" || entry.currency == currencyFilter
            val matchesCategory = selectedCategoryFilter == "הכל" || entry.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() || run {
                val categoryMatch = entry.category.contains(searchQuery, ignoreCase = true)
                val notesMatch = entry.notes.contains(searchQuery, ignoreCase = true)
                val earningsString = String.format(Locale.US, "%.2f", entry.totalEarnings)
                val earningsStringFormatted = String.format(Locale.US, "%,.2f", entry.totalEarnings)
                val earningsMatch = earningsString.contains(searchQuery) || earningsStringFormatted.contains(searchQuery) || entry.totalEarnings.toString().contains(searchQuery)
                
                val workersNames = viewModel.parseGroupWorkers(entry.groupWorkersJson).map { it.name }
                val workerMatch = workersNames.any { it.contains(searchQuery, ignoreCase = true) }
                
                categoryMatch || notesMatch || earningsMatch || workerMatch
            }
            val matchesStatus = when (statusFilter) {
                "הכל" -> true
                "ממתין" -> !entry.isPaid
                "שולם" -> entry.isPaid
                else -> true
            }
            val matchesDateRange = when (filterType) {
                "הכל" -> true
                "חודש" -> {
                    val entryCal = Calendar.getInstance().apply { timeInMillis = entry.date }
                    entryCal.get(Calendar.YEAR) == selectedMonthYear.get(Calendar.YEAR) &&
                    entryCal.get(Calendar.MONTH) == selectedMonthYear.get(Calendar.MONTH)
                }
                "טווח" -> {
                    val entryDayStart = Calendar.getInstance().apply { 
                        timeInMillis = entry.date
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    
                    val rangeStart = Calendar.getInstance().apply {
                        timeInMillis = customStartDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    
                    val rangeEnd = Calendar.getInstance().apply {
                        timeInMillis = customEndDate
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    
                    entryDayStart in rangeStart..rangeEnd
                }
                else -> true
            }

            matchesCategory && matchesSearch && matchesStatus && matchesDateRange && matchesCurrency
        }
    }

    val sortedEntries = remember(filteredEntries, sortOption) {
        when (sortOption) {
            "newest" -> filteredEntries.sortedByDescending { it.date }
            "oldest" -> filteredEntries.sortedBy { it.date }
            "latest_added" -> filteredEntries.sortedByDescending { it.id }
            else -> filteredEntries
        }
    }

    val totalFilteredSum = remember(filteredEntries) {
        filteredEntries.sumOf { it.totalEarnings }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isMultiSelectMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        TextButton(onClick = { selectedShiftIds = emptySet() }) { Text("בטל הכל", color = Color(0xFFC7D2FE)) }
                        TextButton(onClick = { selectedShiftIds = filteredEntries.map { it.id }.toSet() }) { Text("בחר הכל", color = Color(0xFFC7D2FE)) }
                    }
                    Text("נבחרו ${selectedShiftIds.size} משמרות", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val selectedEntries = entries.filter { selectedShiftIds.contains(it.id) }
                                if (selectedEntries.isNotEmpty()) {
                                    val formattedText = formatSelectedShiftsForWhatsApp(selectedEntries)
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(formattedText))
                                    Toast.makeText(context, "המשמרות הועתקו! מוכן להדבקה בוואטסאפ.", Toast.LENGTH_SHORT).show()
                                    isMultiSelectMode = false
                                    selectedShiftIds = emptySet()
                                }
                            },
                            enabled = selectedShiftIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "העתק לקליפבורד עבור וואטסאפ",
                                tint = if (selectedShiftIds.isNotEmpty()) Color.White else Color.Gray
                            )
                        }
                        IconButton(onClick = { 
                            isMultiSelectMode = false
                            selectedShiftIds = emptySet()
                        }) {
                            Icon(Icons.Outlined.Close, "סגור", tint = Color.White)
                        }
                    }
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isMultiSelectMode) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (searchQuery.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "תוצאות חיפוש עבור: $searchQuery",
                                        fontSize = 12.sp,
                                        color = Color(0xFFC7D2FE),
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "נקה",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        // Title / Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "יומן עבודה (${filteredEntries.size})",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Copy menu
                                Box {
                                    var showCopyMenuLocal by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(0xFF121212), shape = RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(10.dp))
                                            .clickable {
                                                triggerHapticFeedback(context, isDestructive = false)
                                                showCopyMenuLocal = true
                                            }
                                            .testTag("copy_menu_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "שתף דוח",
                                            tint = Color(0xFFC7D2FE),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showCopyMenuLocal,
                                        onDismissRequest = { showCopyMenuLocal = false },
                                        modifier = Modifier.background(Color(0xFF1E1E1E))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Excel (ייצוא)", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                showCopyMenuLocal = false
                                                triggerHapticFeedback(context, isDestructive = false)
                                                copyExcelToClipboard(context, filteredEntries, selectedCategoryFilter)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("WhatsApp", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                showCopyMenuLocal = false
                                                triggerHapticFeedback(context, isDestructive = false)
                                                copyWhatsAppToClipboard(context, filteredEntries, selectedCategoryFilter, searchQuery)
                                            }
                                        )
                                    }
                                }

                                // Sort Dropdown button
                                Box {
                                    var showSortDropdownLocal by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(0xFF121212), shape = RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(10.dp))
                                            .clickable { showSortDropdownLocal = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sort,
                                            contentDescription = "מיון",
                                            tint = Color(0xFFC7D2FE),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSortDropdownLocal,
                                        onDismissRequest = { showSortDropdownLocal = false },
                                        modifier = Modifier.background(Color(0xFF1E1E1E))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("תאריך: מהחדש לישן", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                sortOption = "newest"
                                                showSortDropdownLocal = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("תאריך: מהישן לחדש", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                sortOption = "oldest"
                                                showSortDropdownLocal = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("נוסף לאחרונה", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                sortOption = "latest_added"
                                                showSortDropdownLocal = false
                                            }
                                        )
                                    }
                                }

                                // Cyclic Filter Button: Place a single compact Micro-Button next to the Sort/Copy action icons. Tapping cycles through states: "הכל" -> "₪" -> "$" -> "הכל".
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF121212), shape = RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(10.dp))
                                        .clickable {
                                            triggerHapticFeedback(context, isDestructive = false)
                                            currencyFilter = when (currencyFilter) {
                                                "הכל" -> "₪"
                                                "₪" -> "$"
                                                else -> "הכל"
                                            }
                                        }
                                        .testTag("currency_filter_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currencyFilter,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (currencyFilter) {
                                            "$" -> Color(0xFF60A5FA)
                                            "₪" -> Color(0xFF34D399)
                                            else -> Color(0xFFC7D2FE)
                                        }
                                    )
                                }
                            }
                        }

                        // Compact Filter Row: Date selection chips & Category Dropdown Filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "הכל" to "הכל תאריכים",
                                    "חודש" to "סינון חודשי",
                                    "טווח" to "טווח מותאם"
                                ).forEach { (type, label) ->
                                    val isSelected = filterType == type
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) Color(0xFF5C6BC0) else Color(0xFF1C1C1E),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Transparent else Color(0x33FFFFFF),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                triggerHapticFeedback(context, isDestructive = false)
                                                filterType = type
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF8E8E93)
                                        )
                                    }
                                }
                            }

                            // Category Selector
                            Box(modifier = Modifier.width(130.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .background(Color(0xFF121212), shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(8.dp))
                                        .clickable { categoryDropdownExpanded = true }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF8E8E93),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (selectedCategoryFilter == "הכל") "כל הקטגוריות" else selectedCategoryFilter,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Right,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = categoryDropdownExpanded,
                                    onDismissRequest = { categoryDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    filterOptions.forEach { filter ->
                                        DropdownMenuItem(
                                            text = { Text(text = if (filter == "הכל") "כל הקטגוריות" else filter, color = Color.White, fontSize = 11.sp) },
                                            onClick = {
                                                selectedCategoryFilter = filter
                                                categoryDropdownExpanded = false
                                            },
                                            modifier = Modifier.testTag("history_dropdown_cat_$filter")
                                        )
                                    }
                                }
                            }
                        }

                        // Styled Horizontal Status Selector Chips ("הכל", "ממתין", "שולם")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "הכל" to "הכל",
                                "ממתין" to "ממתין",
                                "שולם" to "שולם"
                            ).forEach { (statusVal, statusLabel) ->
                                val isSelected = statusFilter == statusVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .background(
                                            if (isSelected) Color(0xFF5C6BC0) else Color(0xFF121212),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent else Color(0x22FFFFFF),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .clickable {
                                            triggerHapticFeedback(context, isDestructive = false)
                                            statusFilter = statusVal
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = statusLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF8E8E93)
                                    )
                                }
                            }
                        }

                        // Display text for filtered sum
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "סה\"כ מוצג:",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            AnimatedGlowingEarnings(
                                targetValue = totalFilteredSum,
                                runCountAnimationTrigger = viewModel.runCountAnimationTrigger.value,
                                fontSize = 16.sp,
                                glowColor = Color(0xFF34D399),
                                currencySymbol = if (currencyFilter == "$") "$" else "₪"
                            )
                        }

                        // Month Selector
                        if (filterType == "חודש") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val next = (selectedMonthYear.clone() as Calendar).apply {
                                                add(Calendar.MONTH, 1)
                                            }
                                            selectedMonthYear = next
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowBack,
                                            contentDescription = "חודש הבא",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("he", "IL")) }
                                    Text(
                                        text = monthFormat.format(Date(selectedMonthYear.timeInMillis)),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )

                                    IconButton(
                                        onClick = {
                                            val prev = (selectedMonthYear.clone() as Calendar).apply {
                                                add(Calendar.MONTH, -1)
                                            }
                                            selectedMonthYear = prev
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowForward,
                                            contentDescription = "חודש קודם",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Range Selector
                        if (filterType == "טווח") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "עד תאריך",
                                            fontSize = 9.sp,
                                            color = Color(0xFF8E8E93),
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val sdfEnd = remember { SimpleDateFormat("dd.MM.yyyy", Locale.US) }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
                                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(8.dp))
                                                .clickable {
                                                    val cal = Calendar.getInstance().apply { timeInMillis = customEndDate }
                                                    DatePickerDialog(
                                                        context,
                                                        { _, y, m, d ->
                                                            val newCal = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, y)
                                                                set(Calendar.MONTH, m)
                                                                set(Calendar.DAY_OF_MONTH, d)
                                                            }
                                                            customEndDate = newCal.timeInMillis
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                }
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = sdfEnd.format(Date(customEndDate)),
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "מתאריך",
                                            fontSize = 9.sp,
                                            color = Color(0xFF8E8E93),
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val sdfStart = remember { SimpleDateFormat("dd.MM.yyyy", Locale.US) }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
                                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0x22FFFFFF), shape = RoundedCornerShape(8.dp))
                                                .clickable {
                                                    val cal = Calendar.getInstance().apply { timeInMillis = customStartDate }
                                                    DatePickerDialog(
                                                        context,
                                                        { _, y, m, d ->
                                                            val newCal = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, y)
                                                                set(Calendar.MONTH, m)
                                                                set(Calendar.DAY_OF_MONTH, d)
                                                            }
                                                            customStartDate = newCal.timeInMillis
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                }
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = sdfStart.format(Date(customStartDate)),
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
                // 3. ACTUAL ITEMS OR EMPTY STATE
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WorkHistory,
                                    contentDescription = null,
                                    tint = Color(0xFF44444F),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "אין משמרות להצגה",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "נסה לשנות את הסינון או הוסף משמרת חדשה",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                } else {
                    items(items = sortedEntries, key = { it.id }) { entry ->
                        WorkEntryRowCard(
                            entry = entry,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedShiftIds.contains(entry.id),
                            onToggleSelect = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedShiftIds = if (selectedShiftIds.contains(entry.id)) {
                                    selectedShiftIds - entry.id
                                } else {
                                    selectedShiftIds + entry.id
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                isMultiSelectMode = true
                                selectedShiftIds = setOf(entry.id)
                            },
                            onTogglePaid = { onTogglePaid(entry) },
                            onUpdateDirect = { viewModel.updateEntryDirect(it) },
                            onEdit = { onEdit(entry) },
                            onDelete = { showShiftDeleteConfirm = entry }
                        )
                    }
                }
            }
        } // Close outer Column
        
        if (isMultiSelectMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC1E293B),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showBulkDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = selectedShiftIds.isNotEmpty()
                    ) {
                        Text("מחק", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                selectedShiftIds.forEach { id ->
                                    val e = entries.find { it.id == id }
                                    if (e != null && e.isPaid) {
                                        onTogglePaid(e)
                                    }
                                }
                                isMultiSelectMode = false
                                selectedShiftIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedShiftIds.isNotEmpty()
                        ) {
                            Text("סמן כלא שולם", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                selectedShiftIds.forEach { id ->
                                    val e = entries.find { it.id == id }
                                    if (e != null && !e.isPaid) {
                                        onTogglePaid(e)
                                    }
                                }
                                isMultiSelectMode = false
                                selectedShiftIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedShiftIds.isNotEmpty()
                        ) {
                            Text("סמן כשולם", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showShiftDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showShiftDeleteConfirm = null },
                title = { Text("אישור מחיקה", color = Color.White) },
                text = { Text("האם אתה בטוח שברצונך למחוק? פעולה זו אינה ניתנת לביטול.", color = Color(0xFFE2E8F0)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val activeShift = showShiftDeleteConfirm
                            if (activeShift != null) {
                                onDelete(activeShift)
                                showShiftDeleteConfirm = null
                                Toast.makeText(context, "המשמרת נמחקה בהצלחה!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("מחק", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShiftDeleteConfirm = null }) {
                        Text("ביטול", color = Color(0xFF8E8E93))
                    }
                },
                containerColor = Color(0xFF1E293B),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFE2E8F0)
            )
        }

        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text("אישור מחיקה", color = Color.White) },
                text = { Text("האם אתה בטוח שברצונך למחוק? פעולה זו אינה ניתנת לביטול.", color = Color(0xFFE2E8F0)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedShiftIds.forEach { id ->
                                val e = entries.find { it.id == id }
                                if (e != null) onDelete(e)
                            }
                            isMultiSelectMode = false
                            selectedShiftIds = emptySet()
                            showBulkDeleteConfirm = false
                        }
                    ) {
                        Text("מחק", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text("ביטול", color = Color(0xFF8E8E93))
                    }
                },
                containerColor = Color(0xFF1E293B),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFE2E8F0)
            )
        }

        // Custom themed Snackbar representing the Hebrew cancel undo button in solid red colors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                actionColor = Color(0xFFEF4444) // Bold Red as requested!
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WorkEntryRowCard(
    entry: WorkEntry,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onTogglePaid: () -> Unit,
    onUpdateDirect: (WorkEntry) -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val formattedDate = remember(entry.date) {
        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("he", "IL"))
        sdf.format(Date(entry.date))
    }

    val statusColor = if (entry.isPaid) Color(0xFF34D399) else Color(0xFFFBBF24)
    val statusBg = if (entry.isPaid) Color(0xFF064E3B) else Color(0xFF78350F)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2D3748).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    if (isMultiSelectMode) onToggleSelect() else isExpanded = !isExpanded 
                },
                onLongClick = { 
                    if (!isMultiSelectMode) onLongClick() else onToggleSelect()
                }
            )
            .testTag("work_entry_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Circular Initial Letter Badge + Category + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category initial letter badge
                val initialChar = if (entry.category.isNotEmpty()) entry.category.substring(0, 1) else "מ"
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x33FFFFFF), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialChar,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC7D2FE),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Label,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.category,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFE5E5EA)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isMultiSelectMode) {
                        androidx.compose.material3.Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = Color(0xFF6366F1),
                                uncheckedColor = Color(0xFF8E8E93)
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%s%,.2f", entry.currency, entry.totalEarnings),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE5E5EA)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f ש'", entry.hours),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF8E8E93)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, shape = CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "צמצם" else "הרחב",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Details breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Only display start/end times if available and isTimeRange
                            if (entry.isTimeRange && entry.startTime != null && entry.endTime != null) {
                                Text(
                                    text = "שעות עבודה: ${entry.startTime} - ${entry.endTime} (${String.format(Locale.US, "%.1f", entry.hours)} שעות)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF8E8E93)
                                )
                            } else {
                                Text(
                                    text = "שעות שהוזנו ידנית: ${String.format(Locale.US, "%.1f", entry.hours)} שעות",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF8E8E93)
                                )
                            }

                            Text(
                                text = "תעריף שעתי: ${entry.currency}${entry.hourlyRate}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    if (entry.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1C1C1E)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Text(
                                text = "הערות: ${entry.notes}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    if (entry.isGroupShift && entry.groupWorkersJson.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val workersArray = try {
                            org.json.JSONArray(entry.groupWorkersJson)
                        } catch(e: Exception) { org.json.JSONArray() }
                        
                        val employerRate = entry.employerRate ?: 0.0
                        val workerRate = entry.workerRate ?: 0.0
                        val sholiOwnPay = entry.totalEarnings

                        var contractorProfit = 0.0
                        var totalWorkersBossPay = 0.0

                        for (i in 0 until workersArray.length()) {
                            val h = workersArray.getJSONObject(i).optDouble("hours", 0.0)
                            contractorProfit += h * (employerRate - workerRate)
                            totalWorkersBossPay += h * employerRate
                        }

                        val grandTotalBoss = sholiOwnPay + totalWorkersBossPay
                        val sholiNetTotal = sholiOwnPay + contractorProfit

                        Text("סה\"כ לתשלום (כולל כולם): ${entry.currency}${String.format(Locale.US, "%.2f", grandTotalBoss)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("שולי (המשתמש): ${entry.currency}${String.format(Locale.US, "%.2f", sholiNetTotal)}", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in 0 until workersArray.length()) {
                                val obj = workersArray.getJSONObject(i)
                                val wName = obj.optString("name", "")
                                val wHours = obj.optDouble("hours", 0.0)
                                val wPaid = obj.optBoolean("isPaid", false)
                                val wPay = wHours * (entry.workerRate ?: 0.0)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1C1C1E), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (wPaid) Color(0xFF34D399).copy(alpha = 0.3f) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                obj.put("isPaid", !wPaid)
                                                workersArray.put(i, obj)
                                                val newJson = workersArray.toString()
                                                onUpdateDirect(entry.copy(groupWorkersJson = newJson))
                                            } catch(e: Exception) {}
                                            triggerHapticFeedback(context, isDestructive = false)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(wName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${wHours} שעות • ${entry.currency}${String.format(Locale.US, "%.2f", wPay)}", color = Color(0xFF8E8E93), fontSize = 11.sp)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // Individual WhatsApp share
                                        IconButton(
                                            onClick = {
                                                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(entry.date))
                                                val textToSend = "היי ${wName}, להלן פירוט שעות עבודה שלך מיום ${dateStr}:\n" +
                                                        "עבדת ${wHours} שעות. מגיע לך: ${entry.currency}${String.format(Locale.US, "%.2f", wPay)}.\n" +
                                                        "סטטוס תשלום: ${if (wPaid) "שולם" else "ממתין"}"
                                                
                                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, textToSend)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף פרטי משמרת לעובד"))
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = "Share WhatsApp", tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                                        }
                                        
                                        // Checkbox for payment
                                        androidx.compose.material3.Checkbox(
                                            checked = wPaid,
                                            onCheckedChange = null,
                                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF34D399),
                                                uncheckedColor = Color(0xFF8E8E93)
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons/Actions Row in expanded view
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Payment status toggle badge
                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .clickable { onTogglePaid() }
                                .testTag("toggle_payment_badge_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isPaid) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (entry.isPaid) "שולם" else "חוב (לחץ לשינוי)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Every single shift card displays this generic share button when expanded
                            IconButton(
                                onClick = {
                                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(entry.date))
                                    val textToSend = "היי, להלן פרטי המשמרת שלי מיום $dateStr:\n" +
                                            "קטגוריה: ${entry.category}\n" +
                                            "שעות עבודה: ${entry.hours} שעות\n" +
                                            "תעריף שעתי: ₪${String.format(Locale.US, "%.2f", entry.hourlyRate)}\n" +
                                            "סה\"כ לתשלום: ₪${String.format(Locale.US, "%.2f", entry.totalEarnings)}"
                                            
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, textToSend)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף פרטי משמרת"))
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF064E3B), shape = RoundedCornerShape(8.dp))
                                    .testTag("global_share_btn_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "שתף פרטי משמרת ב-WhatsApp",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (entry.isGroupShift) {
                                IconButton(
                                    onClick = {
                                        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(entry.date))
                                        val workersArray = try { org.json.JSONArray(entry.groupWorkersJson) } catch(e: Exception) { org.json.JSONArray() }
                                        
                                        val empRate = entry.employerRate ?: 0.0
                                        val sholiBossPay = entry.totalEarnings
                                        var totalPay = sholiBossPay
                                        var workersLines = "שולי: ${entry.hours} שעות (₪${String.format(Locale.US, "%.2f", sholiBossPay)})\n"
                                        
                                        for (i in 0 until workersArray.length()) {
                                            val obj = workersArray.getJSONObject(i)
                                            val wName = obj.optString("name", "")
                                            val wHours = obj.optDouble("hours", 0.0)
                                            val wPay = wHours * empRate
                                            totalPay += wPay
                                            workersLines += "${wName}: ${wHours} שעות (₪${String.format(Locale.US, "%.2f", wPay)})\n"
                                        }
                                        
                                        val textToSend = "היי, להלן סיכום שעות עבודה ליום ${dateStr}:\n" +
                                                "**סה\"כ לתשלום (כולל כולם): ₪${String.format(Locale.US, "%.2f", totalPay)}**\n" +
                                                "---\n" +
                                                "פירוט:\n" +
                                                workersLines +
                                                "---"
                                                
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, textToSend)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף חשבונית לקבלן"))
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = "שתף סיכום לקבלן",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(8.dp))
                                    .testTag("edit_entry_btn_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "ערוך משמרת",
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    triggerHapticFeedback(context, isDestructive = true)
                                    onDelete()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF451A1A), shape = RoundedCornerShape(8.dp))
                                    .testTag("delete_entry_btn_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "מחק משמרת",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= MANAGEMENT & BACKUP SCREEN =================
@Composable
fun ManagementScreen(
    viewModel: WorkViewModel,
    categories: List<WorkCategory>,
    onNavigateBack: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val accountSession by viewModel.currentUserSession.collectAsStateWithLifecycle()
    var newCategoryText by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<WorkCategory?>(null) }
    var categoryToEditByRate by remember { mutableStateOf<WorkCategory?>(null) }
    var editRateText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }

    val savedNotificationEnabled by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    val savedDefaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    var draftNotificationEnabled by remember(savedNotificationEnabled) { mutableStateOf(savedNotificationEnabled) }
    var draftDefaultCurrency by remember(savedDefaultCurrency) { mutableStateOf(savedDefaultCurrency) }

    // Accordion state - default to all closed (-1)
    var expandedSection by remember { mutableStateOf(-1) }

    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "הגדרות מערכת",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Category 1: ניהול עבודה וקטגוריות
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedSection = if (isExpanded) -1 else 0
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "צמצם" else "הרחב",
                                tint = if (isExpanded) Color(0xFF6366F1) else Color(0xFF8E8E93)
                            )
                            Text(
                                text = "ניהול עבודה וקטגוריות",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isExpanded) Color(0xFF818CF8) else Color.White
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "קטגוריות אלו משמשות סיווג לכל משמרת. קטגוריות ברירת המחדל הן: קריאייטיב, עצמאי, צאח.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Input parameter to add category
                                var newCategoryRateText by remember { mutableStateOf("40") }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newCategoryText,
                                            onValueChange = { newCategoryText = it },
                                            label = { Text("קטגוריה חדשה", color = Color(0xFF8E8E93)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("category_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF3F3F46),
                                                focusedContainerColor = Color(0xFF1C1C1E),
                                                unfocusedContainerColor = Color(0xFF1C1C1E)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = newCategoryRateText,
                                            onValueChange = { newCategoryRateText = it },
                                            label = { Text("תעריף שעתי", color = Color(0xFF8E8E93)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(0.7f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF3F3F46),
                                                focusedContainerColor = Color(0xFF1C1C1E),
                                                unfocusedContainerColor = Color(0xFF1C1C1E)
                                            )
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (newCategoryText.isNotBlank()) {
                                                val rVal = newCategoryRateText.toDoubleOrNull() ?: 40.0
                                                viewModel.addCategory(newCategoryText, rVal)
                                                newCategoryText = ""
                                                triggerHapticFeedback(context, isDestructive = false)
                                                Toast.makeText(context, "הקטגוריה נוספה!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("add_category_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("הוסף קטגוריה", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "קטגוריות קיימות (לחץ על ה-X למחיקה):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Flex style category flow
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    categories.forEach { cat ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.clickable {
                                                        triggerHapticFeedback(context, isDestructive = false)
                                                        categoryToEditByRate = cat
                                                        editRateText = cat.defaultRate.toString()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "ערוך תעריף",
                                                        tint = Color(0xFF6366F1),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "${cat.name} (₪${cat.defaultRate.toString()})",
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Outlined.Cancel,
                                                    contentDescription = "מחק קטגוריה",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            triggerHapticFeedback(context, isDestructive = true)
                                                            categoryToDelete = cat
                                                        }
                                                        .testTag("delete_category_${cat.name}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 2: הגדרת מטבע ראשי
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 1
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedSection = if (isExpanded) -1 else 1
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "צמצם" else "הרחב",
                                tint = if (isExpanded) Color(0xFF6366F1) else Color(0xFF8E8E93)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = draftDefaultCurrency,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6366F1)
                                )
                                Text(
                                    text = "הגדרת מטבע ראשי",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpanded) Color(0xFF818CF8) else Color.White
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "בחר את מטבע ברירת המחדל לחישוב וניהול משמרות ברחבי האפליקציה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("₪", "$").forEach { curr ->
                                            val isSelected = draftDefaultCurrency == curr
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 54.dp, height = 36.dp)
                                                    .background(
                                                        if (isSelected) Color(0xFF6366F1) else Color(0xFF1E293B),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color.Transparent else Color(0x33FFFFFF),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { draftDefaultCurrency = curr }
                                                    .testTag("settings_currency_$curr"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = curr,
                                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "מטבע ברירת מחדל",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "יחול אוטומטית בהוספת משמרות",
                                            color = Color(0xFF8E8E93),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 4: התראות מערכת
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 3
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedSection = if (isExpanded) -1 else 3
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "צמצם" else "הרחב",
                                tint = if (isExpanded) Color(0xFF6366F1) else Color(0xFF8E8E93)
                            )
                            Text(
                                text = "התראות מערכת",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isExpanded) Color(0xFF818CF8) else Color.White
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "ניהול הגדרות התראה, טיימר פעיל במכשיר, ושומר מסך כהה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Active Shift Notification Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = draftNotificationEnabled,
                                        onCheckedChange = { newValue ->
                                            triggerHapticFeedback(context, isDestructive = false)
                                            draftNotificationEnabled = newValue
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF6366F1),
                                            uncheckedThumbColor = Color(0xFF8E8E93),
                                            uncheckedTrackColor = Color(0xFF1C1C1E)
                                        ),
                                        modifier = Modifier.testTag("service_notification_switch")
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 16.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "התראת משמרת פעילה",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "הצגת טיימר פעיל ועדכון שכר שנצבר בהתראת רקע קבועה במכשיר בזמן שהשעון רץ.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8E8E93),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 5: תחזוקה וגיבוי נתונים
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 4
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedSection = if (isExpanded) -1 else 4
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "צמצם" else "הרחב",
                                tint = if (isExpanded) Color(0xFF6366F1) else Color(0xFF8E8E93)
                            )
                            Text(
                                text = "תחזוקה וגיבוי נתונים",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isExpanded) Color(0xFF818CF8) else Color.White
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "ייבוא וייצוא נתונים לצורך גיבוי ושחזור.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Import/Restore section
                                Text(
                                    text = "ייבוא נתונים (אקסל או JSON)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "הדבק שורות מאקסל (מופרד באמצעות Tabs/פסיקים) או טקסט גיבוי JSON:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = importText,
                                    onValueChange = { importText = it },
                                    placeholder = { Text("הדבק נתונים כאן...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .testTag("settings_import_text_field"),
                                    maxLines = 3,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF5C6BC0),
                                        unfocusedBorderColor = Color(0xFF3F3F46),
                                        focusedContainerColor = Color(0xFF1C1C1E),
                                        unfocusedContainerColor = Color(0xFF1C1C1E)
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (importText.isBlank()) {
                                            Toast.makeText(context, "נא להזין טקסט ייבוא תקין", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val success = viewModel.importDataFromString(context, importText)
                                            if (success) {
                                                importText = ""
                                                triggerHapticFeedback(context, isDestructive = false)
                                                // The confirmation dialog owns the actual import.
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("settings_import_action_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ייבא נתונים כעת", color = Color.White, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFF2D2D2D))
                                Spacer(modifier = Modifier.height(16.dp))

                                // Export section
                                Text(
                                    text = "גיבוי וייצוא נתונים",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.copyExportToClipboard(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("copy_backup_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F46))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("העתק ללוח", fontSize = 12.sp, maxLines = 1, color = Color.White)
                                    }

                                    Button(
                                        onClick = { viewModel.shareExportData(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("share_backup_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("שתף גיבוי", fontSize = 12.sp, maxLines = 1, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sign Out row option
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                triggerHapticFeedback(context, isDestructive = true)
                                if (accountSession == null) {
                                    onSignIn()
                                } else {
                                    viewModel.signOut(context)
                                    onNavigateBack()
                                    Toast.makeText(context, "התנתקת מהמערכת בהצלחה", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("settings_sign_out_btn")
                        ) {
                            Text(
                                text = if (accountSession == null) "התחברות" else "התנתק",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "חשבון משתמש",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sessionUser by viewModel.currentUserSession.collectAsStateWithLifecycle()
                            Text(
                                text = sessionUser?.email ?: sessionUser?.displayName ?: "שימוש מקומי — ללא סנכרון",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // Pinned/Sticky Bottom Action Bar containing Cancel & Save Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF121212).copy(alpha = 0.95f), Color(0xFF121212))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Button on the left
            OutlinedButton(
                onClick = {
                    onNavigateBack()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("settings_cancel_btn"),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "ביטול",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Save Button on the right
            Button(
                onClick = {
                    viewModel.updateServiceNotificationEnabled(draftNotificationEnabled)
                    viewModel.updateDefaultCurrency(draftDefaultCurrency)
                    Toast.makeText(context, "ההגדרות נשמרו בהצלחה!", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("settings_save_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "שמור",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("אישור מחיקה", color = Color.White) },
            text = { Text("האם אתה בטוח שברצונך למחוק? פעולה זו אינה ניתנת לביטול.", color = Color(0xFFE2E8F0)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let { viewModel.deleteCategory(it) }
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

    if (categoryToEditByRate != null) {
        var errorEditRate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { categoryToEditByRate = null },
            title = { Text("עדכון תעריף שעתי ברירת מחדל", color = Color.White, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "עדכון תעריף ברירת המחדל עבור '${categoryToEditByRate?.name}'. שינוי זה ישפיע רק על משמרות עתידיות ולא ישנה נתונים קודמים.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editRateText,
                        onValueChange = {
                            editRateText = it
                            errorEditRate = it.toDoubleOrNull() == null || it.toDouble() <= 0.0
                        },
                        label = { Text("תעריף שעתי חדש", color = Color(0xFF8E8E93)) },
                        singleLine = true,
                        isError = errorEditRate,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF5C6BC0),
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedContainerColor = Color(0xFF1C1C1E),
                            unfocusedContainerColor = Color(0xFF1C1C1E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorEditRate) {
                        Text("אנא הזן מספר תקין הגדול מ-0", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedRate = editRateText.toDoubleOrNull()
                        if (parsedRate != null && parsedRate > 0.0) {
                            categoryToEditByRate?.let { cat ->
                                viewModel.updateCategoryRate(cat, parsedRate)
                            }
                            categoryToEditByRate = null
                            Toast.makeText(context, "תעריף שעתי עודכן בהצלחה", Toast.LENGTH_SHORT).show()
                        } else {
                            errorEditRate = true
                        }
                    }
                ) {
                    Text("שמור", color = Color(0xFF6366F1))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEditByRate = null }) {
                    Text("ביטול", color = Color(0xFF8E8E93))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0)
        )
    }
}
// Simple FlowRow helper representation for Jetpack compose (since standard flow is experimental / in layout package)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Since we are showing a small list of badges, simple wrapping can be easily handled or we can use standard horizontal flow.
        // For standard simplicity, we render them in a wrapped Row using Compose flow or standard Row flow.
        // Compose 1.4+ has FlowRow in foundation. Here's a beautiful, lightweight Row wrap mock using a simple Box wrap or using Android's standard Row to prevent compilation errors.
        // Actually, in multi-line flow we can just draw them in a lazy row or simple wrap arrangement.
        // Let's draw them elegantly using a horizontal Row with scroll or wrapping.
        LazyRow(
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Row(
                    horizontalArrangement = horizontalArrangement,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ================= SHIFT FORM DIALOG (ADD/EDIT) =================
@Composable
fun ShiftFormDialog(
    entry: WorkEntry? = null,
    categories: List<WorkCategory>,
    onDismiss: () -> Unit,
    onSave: (
        category: String,
        date: Long,
        isRange: Boolean,
        startTime: String?,
        endTime: String?,
        hours: Double,
        rate: Double,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditMode = entry != null

    // Form entries States
    var selectedCategory by remember {
        mutableStateOf(
            entry?.category ?: "עצמאי"
        )
    }
    var dateMillis by remember { mutableStateOf(entry?.date ?: System.currentTimeMillis()) }
    var isTimeRange by remember { mutableStateOf(entry?.isTimeRange ?: true) }
    var startTime by remember { mutableStateOf(entry?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(entry?.endTime ?: "17:00") }
    var manualHoursText by remember {
        mutableStateOf(entry?.let { if (!it.isTimeRange) String.format(Locale.US, "%.1f", it.hours) else "" } ?: "")
    }
    var rateText by remember {
        mutableStateOf(String.format(Locale.US, "%.0f", entry?.hourlyRate ?: WorkViewModel.DEFAULT_RATE))
    }
    var notesText by remember { mutableStateOf(entry?.notes ?: "") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Helpers to open Date and Time selectors
    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dateMillis = selected.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val formattedDate = remember(dateMillis) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(dateMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "עריכת משמרת" else "הוספת משמרת חדשה",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Picker (M3 Dropdown)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("קטגוריית עבודה") },
                        trailingIcon = {
                            IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDropdownExpanded = !isDropdownExpanded }
                            .testTag("form_category_select")
                    )

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategory = cat.name
                                    isDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("dropdown_cat_${cat.name}")
                            )
                        }
                    }
                }

                // Date Picker trigger button
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תאריך עבודה") },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(imageVector = Icons.Outlined.CalendarToday, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                        .testTag("form_date_select")
                )

                // Time tracking option selector Tab/Row
                Text(
                    text = "אופן הזנת שעות המשמרת:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isTimeRange = true }
                    ) {
                        RadioButton(
                            selected = isTimeRange,
                            onClick = { isTimeRange = true },
                            modifier = Modifier.testTag("radio_time_range")
                        )
                        Text("טווח שעות", fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isTimeRange = false }
                    ) {
                        RadioButton(
                            selected = !isTimeRange,
                            onClick = { isTimeRange = false },
                            modifier = Modifier.testTag("radio_manual_hours")
                        )
                        Text("שעות ידנית", fontSize = 14.sp)
                    }
                }

                // Conditional Layout based on Type Selector
                if (isTimeRange) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start Time Selector
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val parts = startTime.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    TimePickerDialog(context, { _, hour, minute ->
                                        startTime = String.format(Locale.US, "%02d:%02d", hour, minute)
                                    }, h, m, true).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("שעת התחלה") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledBorderColor = Color(0xFF3F3F46),
                                    disabledLabelColor = Color(0xFF8E8E93)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_start_time")
                            )
                        }

                        // End Time Selector
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val parts = endTime.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 17
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    TimePickerDialog(context, { _, hour, minute ->
                                        endTime = String.format(Locale.US, "%02d:%02d", hour, minute)
                                    }, h, m, true).show()
                                }
                        ) {
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("שעת סיום") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledBorderColor = Color(0xFF3F3F46),
                                    disabledLabelColor = Color(0xFF8E8E93)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_end_time")
                            )
                        }
                    }
                } else {
                    // Manual hours textfield using clock dial picker by default
                    val currentHoursDouble = manualHoursText.toDoubleOrNull() ?: 0.0
                    val hVal = currentHoursDouble.toInt()
                    val mVal = Math.round((currentHoursDouble - hVal) * 60).toInt()
                    val displayHoursText = if (manualHoursText.isBlank() || manualHoursText == "0.0" || manualHoursText == "0") {
                        "לחץ לבחירת שעות עבודה..."
                    } else {
                        formatCleanHours(currentHoursDouble)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                TimePickerDialog(context, { _, hour, minute ->
                                    val calculatedHours = hour + (minute / 60.0)
                                    manualHoursText = String.format(Locale.US, "%.2f", calculatedHours)
                                }, hVal, mVal, true).show()
                            }
                    ) {
                        OutlinedTextField(
                            value = displayHoursText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("כמות שעות עבודה") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.White,
                                disabledBorderColor = Color(0xFF3F3F46),
                                disabledLabelColor = Color(0xFF8E8E93)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_manual_hours")
                        )
                    }
                }

                // Hourly Rate Parameters
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("תעריף שעתי (₪)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_rate_input")
                )

                // Notes parameter
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("הערות (חופשי)") },
                    placeholder = { Text("רשום הערות נוספות על העבודה...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = rateText.toDoubleOrNull() ?: WorkViewModel.DEFAULT_RATE
                    var hours = 0.0
                    if (!isTimeRange) {
                        hours = manualHoursText.toDoubleOrNull() ?: 0.0
                        if (hours <= 0.0) {
                            Toast.makeText(context, "נא להזין כמות שעות תקינה", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    }

                    // Save shift
                    onSave(
                        selectedCategory,
                        dateMillis,
                        isTimeRange,
                        if (isTimeRange) startTime else null,
                        if (isTimeRange) endTime else null,
                        hours,
                        rate,
                        notesText
                    )
                },
                modifier = Modifier.testTag("form_save_btn")
            ) {
                Text("שמור משמרת")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("form_cancel_btn")
            ) {
                Text("ביטול")
            }
        }
    )
}

// Compact Scroll state helper for custom layouts in compose
@Composable
fun rememberScrollState(): androidx.compose.foundation.ScrollState {
    return androidx.compose.foundation.rememberScrollState()
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShiftBottomSheet(
    entry: WorkEntry,
    categories: List<WorkCategory>,
    viewModel: WorkViewModel,
    onDismiss: () -> Unit,
    onSave: (
        category: String,
        date: Long,
        isRange: Boolean,
        startTime: String?,
        endTime: String?,
        hours: Double,
        rate: Double,
        notes: String,
        isPaid: Boolean,
        isGroupShift: Boolean,
        employerRate: Double?,
        workerRate: Double?,
        groupWorkersJson: String
    ) -> Unit
) {
    val context = LocalContext.current
    var isManualMode by remember { mutableStateOf(!entry.isTimeRange || entry.isGroupShift) }
    var selectedDateMillis by remember { mutableStateOf(entry.date) }
    var startTimeStr by remember { mutableStateOf(entry.startTime ?: "09:00") }
    var endTimeStr by remember { mutableStateOf(entry.endTime ?: "17:00") }
    var breakMinutesStr by remember { mutableStateOf("0") }
    var hourlyRateStr by remember { mutableStateOf(String.format(Locale.US, "%.0f", entry.hourlyRate)) }
    var selectedCategory by remember { mutableStateOf(entry.category) }
    var notesText by remember { mutableStateOf(entry.notes) }
    var manualHoursStr by remember { mutableStateOf(if (!entry.isTimeRange) String.format(Locale.US, "%.1f", entry.hours) else "8.0") }
    
    var isGroupShift by remember { mutableStateOf(entry.isGroupShift) }
    var employerRateStr by remember { mutableStateOf(entry.employerRate?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var workerRateStr by remember { mutableStateOf(entry.workerRate?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var showSeparateRates by remember { mutableStateOf(entry.employerRate != null || entry.workerRate != null) }
    
    val groupWorkers = remember { 
        mutableStateListOf<WorkViewModel.GroupWorkerState>().apply {
            addAll(viewModel.parseGroupWorkers(entry.groupWorkersJson))
        }
    }
    var currentWorkerName by remember { mutableStateOf("") }
    var currentWorkerHours by remember { mutableStateOf("0.0") }

    var showErrorHours by remember { mutableStateOf(false) }
    var showErrorRate by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "עריכת דיווח",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.End)
            )
            
            // Replicate same layout as "דיווח חדש"
            // 2. Pill Toggle Switch (שעון / ידני / קבוצה)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF161922), shape = RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0x1FFFFFFF), shape = RoundedCornerShape(22.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Part A: "שעון" (Timer / Hours Range option)
                val isClockSelected = !isManualMode && !isGroupShift
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = if (isClockSelected) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "שעון",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isClockSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                        )
                    }
                }

                // Part B: "ידני" (Manual Hours input option)
                val isManualSelected = isManualMode && !isGroupShift
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = if (isManualSelected) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "ידני",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isManualSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                        )
                    }
                }

                // Part C: "קבוצה" (Group Shift option)
                val isGroupSelected = isGroupShift
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
                            val hDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
                            currentWorkerHours = if (hDouble > 0) String.format(Locale.US, "%.2f", hDouble) else "0.0"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.People,
                            contentDescription = null,
                            tint = if (isGroupSelected) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "קבוצה",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGroupSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // 3. "תאריך" Box Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "תאריך",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sdfDate = remember { SimpleDateFormat("dd.MM.yyyy", Locale.US) }
                val dateStr = sdfDate.format(Date(selectedDateMillis))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    selectedDateMillis = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF8E8E93)
                        )
                        Text(
                            text = dateStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            // 4. Entry/Exit Dropdown Selectors (Side-by-side)
            if (!isManualMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left item: יציאה
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "יציאה",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    val t = endTimeStr.split(":")
                                    val h = t.getOrNull(0)?.toIntOrNull() ?: 17
                                    val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                                    TimePickerDialog(context, { _, hour, minute ->
                                        endTimeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                                    }, h, m, true).show()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                                Text(endTimeStr, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }

                    // Right item: כניסה
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "כניסה",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    val t = startTimeStr.split(":")
                                    val h = t.getOrNull(0)?.toIntOrNull() ?: 9
                                    val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                                    TimePickerDialog(context, { _, hour, minute ->
                                        startTimeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                                    }, h, m, true).show()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                                Text(startTimeStr, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Manual hours input field using clock dial picker by default
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "שעות עבודה",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val currentHoursDouble = manualHoursStr.toDoubleOrNull() ?: 0.0
                    val hVal = currentHoursDouble.toInt()
                    val mVal = Math.round((currentHoursDouble - hVal) * 60).toInt()
                    val displayHoursText = if (manualHoursStr.isBlank()) {
                        "לחץ לבחירת שעות עבודה..."
                    } else {
                        formatCleanHours(currentHoursDouble)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, if (showErrorHours) Color.Red else Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                            .clickable {
                                TimePickerDialog(context, { _, hour, minute ->
                                    val calculatedHours = hour + (minute / 60.0)
                                    manualHoursStr = String.format(Locale.US, "%.2f", calculatedHours)
                                    showErrorHours = false
                                }, hVal, mVal, true).show()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFF5C6BC0)
                            )
                            Text(
                                text = displayHoursText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (manualHoursStr.isBlank()) Color(0xFF64748B) else Color.White
                            )
                        }
                    }
                    if (showErrorHours) {
                        Text(
                            text = "נא להזין כמות שעות תקינה",
                            color = Color.Red,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // 5. "הפסקה (דקות)"
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "הפסקה",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(4.dp))
                val totalMinutes = breakMinutesStr.toIntOrNull() ?: 0
                val bHours = totalMinutes / 60
                val bMins = totalMinutes % 60
                val displayBreakText = if (breakMinutesStr.isBlank() || breakMinutesStr == "0") {
                    "0 שעות"
                } else {
                    formatCleanHours(totalMinutes / 60.0)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                        .clickable {
                            TimePickerDialog(context, { _, hour, minute ->
                                val totalMins = hour * 60 + minute
                                breakMinutesStr = totalMins.toString()
                            }, bHours, bMins, true).show()
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF5C6BC0)
                        )
                        Text(
                            text = displayBreakText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            // 6. "תעריף שעתי"
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "תעריף לשעה",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = hourlyRateStr,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        val dotCount = filtered.count { it == '.' }
                        if (dotCount <= 1) {
                            hourlyRateStr = filtered
                            showErrorRate = filtered.isBlank()
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_rate_input"),
                    isError = showErrorRate,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF5C6BC0),
                        unfocusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF3F3F46),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (showErrorRate) {
                    Text(
                        text = "נא להזין תעריף תקין",
                        color = Color.Red,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
                
                if (isGroupShift) {
                    Text(
                        text = if (showSeparateRates) "- בטל תעריפים נפרדים לקבוצה" else "+ הגדר תעריפים נפרדים לקבוצה",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.Start)
                            .clickable { showSeparateRates = !showSeparateRates }
                    )
                    
                    AnimatedVisibility(visible = showSeparateRates) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = employerRateStr,
                                onValueChange = { employerRateStr = it },
                                label = { Text("תעריף מעסיק (לקבלן)", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF44444F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = workerRateStr,
                                onValueChange = { workerRateStr = it },
                                label = { Text("תעריף לעובד", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF44444F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 7. Employer ("מעסיק") Dropdown Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "מעסיק",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                var expandedDropdown by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(12.dp))
                        .clickable { expandedDropdown = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFF8E8E93))
                        Text(
                            text = selectedCategory,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, fontSize = 14.sp) },
                                onClick = {
                                    selectedCategory = cat.name
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // 8. "הערות" Description Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "הערות",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text("הערות...", color = Color(0xFF64748B)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5C6BC0),
                        unfocusedBorderColor = Color(0xFF3F3F46),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

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
                            .background(Color(0xFF1C1C1E), shape = RoundedCornerShape(12.dp))
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
                                    colors = OutlinedTextFieldDefaults.colors(
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF5C6BC0),
                                        unfocusedBorderColor = Color(0xFF44444F),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                IconButton(onClick = { groupWorkers.removeAt(index) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = currentWorkerName,
                                onValueChange = { 
                                    currentWorkerName = it
                                },
                                label = { Text("שם", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5C6BC0),
                                    unfocusedBorderColor = Color(0xFF44444F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = currentWorkerHours,
                                onValueChange = { currentWorkerHours = it },
                                label = { Text("שעות", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
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
                                Icon(Icons.Outlined.Add, contentDescription = "Add", tint = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Save Buttons
            Button(
                onClick = {
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
                        val durationVal = eMin - sMin - bMins
                        maxOf(0.0, durationVal / 60.0)
                    }
                    val finalRate = hourlyRateStr.toDoubleOrNull() ?: 40.0
                    val eRate = if (isGroupShift && showSeparateRates) employerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                    val wRate = if (isGroupShift && showSeparateRates) workerRateStr.toDoubleOrNull() ?: finalRate else finalRate
                    val gJson = if (isGroupShift && groupWorkers.isNotEmpty()) {
                        val arr = org.json.JSONArray()
                        groupWorkers.forEach { w ->
                            val obj = org.json.JSONObject()
                            obj.put("name", w.name)
                            obj.put("hours", w.hours)
                            obj.put("isPaid", w.isPaid)
                            arr.put(obj)
                        }
                        arr.toString()
                    } else ""
                    
                    onSave(
                        selectedCategory,
                        selectedDateMillis,
                        !isManualMode,
                        if (!isManualMode) startTimeStr else null,
                        if (!isManualMode) endTimeStr else null,
                        finalHours,
                        finalRate,
                        notesText,
                        entry.isPaid,
                        isGroupShift,
                        eRate,
                        wRate,
                        gJson
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5C6BC0),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_edited_entry_btn")
            ) {
                Text("שמור שינויים", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardBarChart(entries: List<WorkEntry>) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH)
    val actualDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val dailyHours = remember(entries) {
        val hoursArray = FloatArray(actualDays + 1)
        val entryCal = Calendar.getInstance()
        for (entry in entries) {
            entryCal.timeInMillis = entry.date
            if (entryCal.get(Calendar.YEAR) == currentYear && entryCal.get(Calendar.MONTH) == currentMonth) {
                val d = entryCal.get(Calendar.DAY_OF_MONTH)
                if (d in 1..actualDays) {
                    hoursArray[d] += entry.hours.toFloat()
                }
            }
        }
        hoursArray
    }

    val maxHours = remember(dailyHours) {
        (dailyHours.maxOrNull() ?: 0.0f).coerceAtLeast(8.0f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)), // Pitch-black
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "שעות עבודה יומיות - החודש",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Standard scroll structure
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Canvas(
                    modifier = Modifier
                        .width((actualDays * 32 + 50).dp) // Each day is 32dp wide
                        .fillMaxHeight()
                ) {
                    val paddingLeft = 35.dp.toPx()
                    val paddingRight = 10.dp.toPx()
                    val paddingTop = 20.dp.toPx()
                    val paddingBottom = 25.dp.toPx()
                    
                    val chartWidth = size.width - paddingLeft - paddingRight
                    val chartHeight = size.height - paddingTop - paddingBottom
                    
                    // Draw horizontal grid lines & Y axis values (0, max/2, max)
                    val gridYLevels = listOf(0f, maxHours / 2f, maxHours)
                    val paintText = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#94A3B8") // Slate
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    
                    for (level in gridYLevels) {
                        val y = paddingTop + chartHeight - (level / maxHours) * chartHeight
                        drawLine(
                            color = Color(0xFF1E1E1E),
                            start = Offset(paddingLeft, y),
                            end = Offset(size.width - paddingRight, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${level.toInt()}h",
                            paddingLeft - 8.dp.toPx(),
                            y + 4.dp.toPx(),
                            paintText
                        )
                    }
                    
                    // Draw bars and day labels
                    val barWidth = 16.dp.toPx()
                    val daySpacing = 32.dp.toPx()
                    
                    for (day in 1..actualDays) {
                        val hours = dailyHours[day]
                        val x = paddingLeft + (day - 1) * daySpacing + (daySpacing - barWidth) / 2f
                        val barHeight = (hours / maxHours) * chartHeight
                        val y = paddingTop + chartHeight - barHeight
                        
                        if (hours > 0) {
                            // Vibrant purple / indigo gradient
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFC084FC), Color(0xFF5C6BC0))
                                ),
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            
                            // Tiny label on top of the bar for non-zero hours
                            val textPaintHours = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                String.format(Locale.US, "%.1f", hours),
                                x + barWidth / 2f,
                                y - 5.dp.toPx(),
                                textPaintHours
                            )
                        } else {
                            // Light background indicator line for empty day
                            drawRect(
                                color = Color(0xFF0F0F0F),
                                topLeft = Offset(x, paddingTop),
                                size = Size(barWidth, chartHeight)
                            )
                        }
                        
                        // Day Label (X-axis)
                        val paintDayText = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#94A3B8")
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            day.toString(),
                            x + barWidth / 2f,
                            paddingTop + chartHeight + 16.dp.toPx(),
                            paintDayText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginOverlay(
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE121212)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF1E293B), shape = CircleShape)
                        .border(1.dp, Color(0x33818CF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "שכר עבודות אישי",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = com.example.ui.theme.RubikFontFamily,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "התחבר למערכת כדי לנהל ולשמור את שעות העבודה והמשמרות שלך",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Button(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_sign_in_button")
                        .pressScale(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "התחבר באמצעות Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
