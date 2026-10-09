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

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 1f,
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
    val names = filteredEntries.flatMap { getWorkerNamesFromEntry(it.groupWorkersJson) }.distinct()
    val worker = names.singleOrNull { searchQuery.isNotBlank() && it.equals(searchQuery.trim(), ignoreCase = true) }
    return com.example.data.WorkMoney.report(filteredEntries, worker)
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
    return com.example.data.WorkMoney.report(selectedEntries)
}

class MainActivity : ComponentActivity() {
    private fun modelFor(uid: String?): WorkViewModel {
        val owner = com.example.data.WorkAccountScope(uid)
        return androidx.lifecycle.ViewModelProvider(this, WorkViewModelFactory(application, owner))
            .get(owner.storageKey, WorkViewModel::class.java)
    }
    private val viewModel: WorkViewModel
        get() = modelFor(com.example.api.AuthManager.currentUser.value?.uid)

    val intentActionFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.ui.WorkOnboardingStore(this).initialize()
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

        // Notification permission is requested only when starting a live shift.

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
                            val session by com.example.api.AuthManager.currentUser.collectAsStateWithLifecycle()
                            // All remembered form/selection/AI review state is confined to this owner.
                            androidx.compose.runtime.key(session?.uid) {
                                MainAppContent(
                                    viewModel = modelFor(session?.uid),
                                    modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)
                                )
                            }
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
            viewModel.startDefaultActiveShift()
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
    val onboarding = com.example.ui.rememberOnboardingController()
    if (!onboarding.active) com.example.ui.WorkUpdateSettings(automatic = true)
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

    val signInWithGoogle: () -> Unit = {
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

    var showAccountDialog by remember { mutableStateOf(false) }
    val signInForSync: () -> Unit = { showAccountDialog = true }
    if (showAccountDialog && BuildConfig.ACCOUNTS_ENABLED) com.example.ui.WorkAccountDialog(
        onDismiss = { showAccountDialog = false }, onGoogle = signInWithGoogle
    )

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

    // Pager is the single navigation state: tab presses and RTL swipes stay in sync.
    val mainPagerState = rememberPagerState(pageCount = { 2 })
    val navigationScope = rememberCoroutineScope()
    val selectedTab = mainPagerState.currentPage
    val navigateToTab: (Int) -> Unit = { page ->
        navigationScope.launch { mainPagerState.animateScrollToPage(page) }
    }
    val accountSession by viewModel.currentUserSession.collectAsStateWithLifecycle()
    // Optional personal AI setup is now introduced by the tour and available in Settings.
    var showSettings by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<WorkEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchDialogOpen by remember { mutableStateOf(false) }
    var isContentScrolling by remember { mutableStateOf(false) }
    var historyNavigationHeight by remember { mutableStateOf(0.dp) }
    val navigationDensity = androidx.compose.ui.platform.LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(selectedTab) {
        isContentScrolling = false
    }

    val activeShiftStartTime by viewModel.activeShiftStartTime.collectAsStateWithLifecycle()

    // Keep every interactive control readable during an active shift.
    val focusAlpha = 1f

    val intentAction by (context as MainActivity).intentActionFlow.collectAsStateWithLifecycle()
    LaunchedEffect(intentAction) {
        when (intentAction) {
            "com.example.ACTION_IMPORT_EXCEL" -> {
                showSettings = true
                (context as MainActivity).intentActionFlow.value = null
            }
            "com.example.ACTION_OPEN_HISTORY_SEARCH" -> {
                if (selectedTab == 1) isSearchDialogOpen = true
                (context as MainActivity).intentActionFlow.value = null
            }
        }
    }

    BackHandler(enabled = selectedTab != 0 || showSettings) {
        if (showSettings) {
            showSettings = false
        } else {
            navigateToTab(0)
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val bottomNavigation: @Composable () -> Unit = {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .heightIn(min = 58.dp)
                .testTag("bottom_navigation")
                .graphicsLayer { alpha = focusAlpha },
            windowInsets = if (selectedTab == 1) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0)
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    navigateToTab(0)
                },
                icon = { Icon(imageVector = Icons.Outlined.GridView, contentDescription = "ראשי", modifier = Modifier.size(20.dp)) },
                label = { Text("ראשי", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF6366F1),
                    unselectedIconColor = Color(0xFF8E8E93),
                    selectedTextColor = Color(0xFF6366F1),
                    unselectedTextColor = Color(0xFF8E8E93),
                    indicatorColor = com.example.ui.theme.FormSurface
                ),
                modifier = Modifier.testTag("tab_0")
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    navigateToTab(1)
                },
                icon = { Icon(imageVector = Icons.Outlined.History, contentDescription = "היסטוריה", modifier = Modifier.size(20.dp)) },
                label = { Text("היסטוריה", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF6366F1),
                    unselectedIconColor = Color(0xFF8E8E93),
                    selectedTextColor = Color(0xFF6366F1),
                    unselectedTextColor = Color(0xFF8E8E93),
                    indicatorColor = com.example.ui.theme.FormSurface
                ),
                modifier = Modifier.testTag("tab_1")
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            containerColor = Color.Transparent,
            // IME is owned by the outer imePadding; scaffold owns system bars only.
            contentWindowInsets = WindowInsets.systemBars,
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
                                    },
                                    modifier = Modifier.testTag("history_search_button")
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
                                },
                                modifier = Modifier.testTag("settings_button")
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
            bottomBar = {}
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    HorizontalPager(
                        state = mainPagerState,
                        modifier = Modifier.fillMaxSize().testTag("main_screen_pager"),
                        beyondViewportPageCount = 1,
                        key = { page -> if (page == 0) "dashboard" else "history" }
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
                                    onStartShift = { cat, rate, currency ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (!isGranted) {
                                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        viewModel.startActiveShift(cat, rate, currency)
                                    },
                                    onSaveShift = { cat, hrs, rate ->
                                        viewModel.finishActiveShift()
                                    },
                                    recentEntries = entries,
                                    onTogglePaid = { entry ->
                                        triggerHapticFeedback(context, isDestructive = false)
                                        viewModel.togglePaymentStatus(entry)
                                    },
                                    onViewAll = { navigateToTab(1) },
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
                                    bottomNavigation = { if (selectedTab == 0) bottomNavigation() },
                                    selectedTab = selectedTab,
                                    onScrollStateChanged = { scrolling ->
                                        if (selectedTab == 0) isContentScrolling = scrolling
                                    }
                                )
                            }
                            1 -> ShiftsScreen(
                                isVisible = selectedTab == 1,
                                navigationBottomInset = historyNavigationHeight,
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
                                },
                                onScrollStateChanged = { scrolling ->
                                    if (selectedTab == 1) isContentScrolling = scrolling
                                }
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        visible = selectedTab == 1 && !isImeVisible && !isContentScrolling,
                        enter = fadeIn(tween(140)), exit = fadeOut(tween(140))
                    ) {
                        Box(Modifier.onSizeChanged { size ->
                            if (size.height > 0) historyNavigationHeight = with(navigationDensity) { size.height.toDp() }
                        }) { bottomNavigation() }
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
            onSave = { category, date, isRange, start, end, hours, rate, notes, isPaid, isGroup, empRate, workerRate, groupJson, currency ->
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
                        currency = currency
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
                    .testTag("settings_root")
                    .pointerInput(Unit) { detectTapGestures(onTap = { showSettings = false }) }
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
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                ) {
                    ManagementScreen(
                        viewModel = viewModel,
                        categories = distinctCategories,
                        onNavigateBack = { showSettings = false },
                        onSignIn = signInForSync,
                        onReplayTutorial = { onboarding.replay() }
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
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
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
                        textAlign = TextAlign.Start,
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
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("history_search_clear")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "נקה", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { isSearchDialogOpen = false }),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                            Text("הצג תוצאות", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    com.example.ui.WorkOnboarding(onboarding, viewModel, distinctCategories, entries)



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
            onEdit = { aiProposal = null },
            onSave = {
                // Consume before dispatch to prevent a second tap from adding it twice.
                if (aiProposal != null) {
                    aiProposal = null
                    viewModel.addShifts(proposal)
                    aiInputText = ""
                    Toast.makeText(context, "המשמרות הועברו לשמירה", Toast.LENGTH_SHORT).show()
                }
            })
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
                    com.example.ui.ShiftCurrencyPicker(dialogCurrency, { dialogCurrency = it }, "quick_shift_currency")

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

// ================= SHIFTS SCREEN =================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftsScreen(
    isVisible: Boolean = true,
    navigationBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: WorkViewModel,
    entries: List<WorkEntry>,
    categories: List<WorkCategory>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTogglePaid: (WorkEntry) -> Unit,
    onEdit: (WorkEntry) -> Unit,
    onDelete: (WorkEntry) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {}
) {
    var selectedCategoryFilter by remember { mutableStateOf("הכל") }
    var sortOption by remember { mutableStateOf("newest") } // "newest", "oldest", "latest_added"
    var statusFilter by remember { mutableStateOf("הכל") } // "הכל", "ממתין", "שולם"
    var currencyFilter by remember { mutableStateOf("הכל") } // "הכל", "₪", "$"

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val historyDensity = androidx.compose.ui.platform.LocalDensity.current
    var selectionActionsHeight by remember { mutableStateOf(0.dp) }
    val scrollCallback by rememberUpdatedState(onScrollStateChanged)
    LaunchedEffect(lazyListState, isVisible) {
        if (!isVisible) return@LaunchedEffect
        var previous = lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        var moved = false
        snapshotFlow { Triple(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset, lazyListState.isScrollInProgress) }
            .collect { (index, offset, scrolling) ->
                val position = index to offset
                if (!scrolling) moved = false
                else if (position != previous) moved = true
                scrollCallback(moved && scrolling && lazyListState.canScrollForward)
                previous = position
            }
    }

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
    BackHandler(enabled = isVisible && isMultiSelectMode) {
        isMultiSelectMode = false
        selectedShiftIds = emptySet()
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            isMultiSelectMode = false
            selectedShiftIds = emptySet()
            onScrollStateChanged(false)
        }
    }
    var showFilters by remember { mutableStateOf(false) }

    var showShiftDeleteConfirm by remember { mutableStateOf<WorkEntry?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    // Map categories names using "כל הקטגוריות" as "הכל"
    val filterOptions = listOf("הכל") + categories.map { it.name.trim() }.distinct()

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = com.example.ui.theme.FormSurface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("סינון היסטוריה", style = MaterialTheme.typography.titleLarge)
                        // Compact Filter Row: Date selection chips & Category Dropdown Filter
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                if (isSelected) Color(0xFF5C6BC0) else com.example.ui.theme.FormSurface,
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
                                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
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
                                        Text(
                                            text = if (selectedCategoryFilter == "הכל") "כל הקטגוריות" else selectedCategoryFilter,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Start,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF8E8E93),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = categoryDropdownExpanded,
                                    onDismissRequest = { categoryDropdownExpanded = false },
                                    modifier = Modifier.background(com.example.ui.theme.FormSurface)
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
                                            if (isSelected) Color(0xFF5C6BC0) else com.example.ui.theme.FormSurface,
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
                                        }.testTag("history_status_$statusVal"),
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

                        // Month Selector
                        if (filterType == "חודש") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
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
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
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
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
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
                                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
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
                                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
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

                TextButton(onClick = { filterType = "הכל"; statusFilter = "הכל"; selectedCategoryFilter = "הכל"; currencyFilter = "הכל" }) { Text("איפוס סינון") }
                Button(onClick = { showFilters = false }) { Text("הצג תוצאות") }
            }
        }
    }

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
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                        Text("נבחרו ${selectedShiftIds.size} משמרות", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row {
                        TextButton(onClick = { selectedShiftIds = emptySet() }) { Text("בטל הכל", color = Color(0xFFC7D2FE)) }
                        TextButton(onClick = { selectedShiftIds = filteredEntries.map { it.id }.toSet() }) { Text("בחר הכל", color = Color(0xFFC7D2FE)) }
                    }
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
                    .testTag("history_scroll_container")
                    .weight(1f),
                contentPadding = PaddingValues(bottom = (if (isMultiSelectMode) maxOf(navigationBottomInset, selectionActionsHeight) else navigationBottomInset) + 16.dp),
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
                                        .background(com.example.ui.theme.FormSurface, RoundedCornerShape(8.dp))
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
                            HistoryToolbar(
                                entries = filteredEntries,
                                currencyFilter = currencyFilter,
                                activeFilterCount = listOf(filterType != "הכל", statusFilter != "הכל", selectedCategoryFilter != "הכל", currencyFilter != "הכל").count { it },
                                onFilters = { showFilters = true },
                                onCurrencyFilterChange = { triggerHapticFeedback(context, isDestructive = false); currencyFilter = it },
                                onSort = { sortOption = it },
                                onCopyExcel = { triggerHapticFeedback(context, isDestructive = false); copyExcelToClipboard(context, filteredEntries, selectedCategoryFilter) },
                                onCopyWhatsApp = { triggerHapticFeedback(context, isDestructive = false); copyWhatsAppToClipboard(context, filteredEntries, selectedCategoryFilter, searchQuery) }
                            )
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
                    .onSizeChanged { selectionActionsHeight = with(historyDensity) { it.height.toDp() } }
                    .padding(bottom = navigationBottomInset)
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
                .padding(bottom = navigationBottomInset + 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = com.example.ui.theme.FormSurface,
                contentColor = Color.White,
                actionColor = Color(0xFFEF4444) // Bold Red as requested!
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    val tutorialShare = LocalCoachStep.current?.screen == CoachScreen.SHARE
    var isExpanded by remember { mutableStateOf(tutorialShare) }
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
                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(10.dp))
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
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFE5E5EA)
                        )
                        if (isMultiSelectMode) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = com.example.data.WorkMoney.format(entry.totalEarnings, entry.currency),
                                modifier = Modifier.testTag("selection_amount_${entry.id}"),
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE5E5EA)
                            )
                        }
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
                                text = com.example.data.WorkMoney.format(entry.totalEarnings, entry.currency),
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
                            Text(if (entry.isPaid) "שולם" else "ממתין", color = statusColor, fontSize = 12.sp,
                                modifier = Modifier.testTag("payment_status_${entry.id}"), maxLines = 1)
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
                                containerColor = com.example.ui.theme.FormSurface
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
                            contractorProfit += h * (com.example.data.WorkMoney.employerRate(workersArray.getJSONObject(i), entry) - com.example.data.WorkMoney.workerRate(workersArray.getJSONObject(i), entry))
                            totalWorkersBossPay += h * com.example.data.WorkMoney.employerRate(workersArray.getJSONObject(i), entry)
                        }

                        val grandTotalBoss = sholiOwnPay + totalWorkersBossPay
                        val sholiNetTotal = sholiOwnPay + contractorProfit

                        Text("סה\"כ לתשלום (כולל כולם): ${entry.currency}${String.format(Locale.US, "%.2f", grandTotalBoss)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ההכנסה שלי (כולל עמלה): ${entry.currency}${String.format(Locale.US, "%.2f", sholiNetTotal)}", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in 0 until workersArray.length()) {
                                val obj = workersArray.getJSONObject(i)
                                val wName = obj.optString("name", "")
                                val wHours = obj.optDouble("hours", 0.0)
                                val wPaid = obj.optBoolean("isPaid", false)
                                val wPay = wHours * com.example.data.WorkMoney.workerRate(obj, entry)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(com.example.ui.theme.FormSurface, RoundedCornerShape(8.dp))
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
                                    Column(Modifier.weight(1f)) {
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
                                                if (!tutorialShare) context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף פרטי משמרת לעובד"))
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = "שתף פרטי משמרת לעובד", tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
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
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().testTag("entry_actions_${entry.id}"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Payment status toggle badge
                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        // Individual actions wrap independently at their full touch size.
                        // Every single shift card displays this generic share button when expanded
                        IconButton(
                            onClick = {
                                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(entry.date))
                                val textToSend = "היי, להלן פרטי המשמרת שלי מיום $dateStr:\n" +
                                        "קטגוריה: ${entry.category}\n" +
                                        "שעות עבודה: ${entry.hours} שעות\n" +
                                        "תעריף שעתי: ${entry.currency}${String.format(Locale.US, "%.2f", entry.hourlyRate)}\n" +
                                        "סה\"כ לתשלום: ${entry.currency}${String.format(Locale.US, "%.2f", entry.totalEarnings)}"

                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, textToSend)
                                }
                                if (!tutorialShare) context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף פרטי משמרת"))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF064E3B), shape = RoundedCornerShape(8.dp))
                                .testTag("global_share_btn_${entry.id}")
                                .coachTarget(CoachTarget.SHARE)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "שתף פרטי משמרת",
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
                                    var workersLines = "החלק שלי: ${entry.hours} שעות (${entry.currency}${String.format(Locale.US, "%.2f", sholiBossPay)})\n"

                                    for (i in 0 until workersArray.length()) {
                                        val obj = workersArray.getJSONObject(i)
                                        val wName = obj.optString("name", "")
                                        val wHours = obj.optDouble("hours", 0.0)
                                        val wPay = wHours * com.example.data.WorkMoney.employerRate(obj, entry)
                                        totalPay += wPay
                                        workersLines += "${wName}: ${wHours} שעות (${entry.currency}${String.format(Locale.US, "%.2f", wPay)})\n"
                                    }

                                    val textToSend = "היי, להלן סיכום שעות עבודה ליום ${dateStr}:\n" +
                                            "**סה\"כ לתשלום (כולל כולם): ${entry.currency}${String.format(Locale.US, "%.2f", totalPay)}**\n" +
                                            "---\n" +
                                            "פירוט:\n" +
                                            workersLines +
                                            "---"

                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, textToSend)
                                    }
                                    if (!tutorialShare) context.startActivity(android.content.Intent.createChooser(sendIntent, "שתף חשבונית לקבלן"))
                                },
                                modifier = Modifier
                                    .size(48.dp)
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
                                .size(48.dp)
                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
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
                                .size(48.dp)
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

/** The secret is only entered here; never restored into a visible field or saved UI state. */
@Composable
private fun PersonalAiKeyDialog(onDismiss: () -> Unit, isFirstSetup: Boolean = true, ownerUid: String?) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showExplanation by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var hasKey by remember {
        mutableStateOf(runCatching { com.example.api.PersonalAiKey.read(context, ownerUid).isNotBlank() }.getOrDefault(false))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שמירת משמרות בעזרת ג׳מיני", modifier = Modifier.weight(1f))
                IconButton(onClick = { showExplanation = !showExplanation }) {
                    Icon(Icons.Outlined.Info, contentDescription = "הסבר על המפתח האישי")
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (hasKey) "מפתח אישי כבר שמור. אפשר להחליף או להסיר אותו כאן." else "אפשר להוסיף מפתח אישי עכשיו, או לדלג ולהוסיף בהגדרות בהמשך.")
                AnimatedVisibility(visible = showExplanation) {
                    Text("מפתח אישי מאפשר לתאר עבודה במילים ולקבל משמרות לבדיקה לפני שמירה. בלי מפתח אפשר להשתמש בכל הפעולות הרגילות. המפתח נשמר מוצפן במכשיר הזה, אינו מוצג במסכים ואינו נכלל בגיבוי המשמרות. הוא לא מסתנכרן למכשירים אחרים. הפענוח נשלח לגוגל ומשתמש במכסת הפרויקט שאליו שייך המפתח.")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; status = null },
                    label = { Text(if (hasKey) "מפתח אישי חדש" else "מפתח אישי (לא חובה)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                status?.let { Text(it) }
                if (hasKey) {
                    TextButton(onClick = {
                        try {
                            com.example.api.PersonalAiKey.remove(context, ownerUid)
                            draft = ""
                            hasKey = false
                            status = "המפתח הוסר. המשמרות וכל שאר הנתונים נשמרו."
                        } catch (_: Exception) {
                            status = "הסרת המפתח לא הצליחה. אפשר לנסות שוב."
                        }
                    }) { Text("הסרת המפתח האישי") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    try {
                        com.example.api.PersonalAiKey.save(context, draft, ownerUid)
                        draft = ""
                        Toast.makeText(context, "המפתח נשמר במכשיר. החיבור ייבדק בזמן הפענוח.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    } catch (_: Exception) {
                        status = "שמירת המפתח לא הצליחה. יש לבדוק את המפתח ולנסות שוב."
                    }
                }
            ) { Text("שמירת המפתח") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isFirstSetup) "לא עכשיו" else "סגירה") }
        }
    )
}

// ================= MANAGEMENT & BACKUP SCREEN =================
@Composable
fun ManagementScreen(
    viewModel: WorkViewModel,
    categories: List<WorkCategory>,
    onNavigateBack: () -> Unit,
    onSignIn: () -> Unit = {},
    onReplayTutorial: () -> Unit = {}
) {
    val context = LocalContext.current
    val accountSession by viewModel.currentUserSession.collectAsStateWithLifecycle()
    var showPersonalKeySettings by remember { mutableStateOf(false) }
    if (showPersonalKeySettings) {
        key(accountSession?.uid) {
            PersonalAiKeyDialog(onDismiss = { showPersonalKeySettings = false }, isFirstSetup = false, ownerUid = viewModel.owner.uid)
        }
    }
    var newCategoryText by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<WorkCategory?>(null) }
    var categoryToEditByRate by remember { mutableStateOf<WorkCategory?>(null) }
    var editRateText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }

    val localPreferences by viewModel.localPreferences.collectAsStateWithLifecycle()
    val saveBackupLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.saveBackup(context, it) }
    }
    val savedNotificationEnabled by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    val savedDefaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    var draftNotificationEnabled by remember(savedNotificationEnabled) { mutableStateOf(savedNotificationEnabled) }
    var draftDefaultCurrency by remember(savedDefaultCurrency) { mutableStateOf(savedDefaultCurrency) }

    // Accordion state - default to all closed (-1)
    val coachStep = LocalCoachStep.current
    var expandedSection by remember { mutableStateOf(if (coachStep?.screen == CoachScreen.API) 3 else -1) }

    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // During guidance, scroll targets must remain above the real sticky actions.
                .padding(bottom = if (coachStep != null) 88.dp else 0.dp)
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

            OutlinedButton(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("replay_onboarding"), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("הצג שוב את ההדרכה")
            }

            // Category 1: ניהול עבודה וקטגוריות
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .coachTarget(CoachTarget.CATEGORIES)

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 0
                        }.testTag("settings_section_עבודה וקטגוריות")) {
                            SettingsSectionHeader("עבודה וקטגוריות", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "קטגוריות מסווגות את המשמרות. שינוי ברירת מחדל אינו משנה סכומים שנשמרו.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
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
                                                focusedContainerColor = com.example.ui.theme.FormSurface,
                                                unfocusedContainerColor = com.example.ui.theme.FormSurface
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
                                                focusedContainerColor = com.example.ui.theme.FormSurface,
                                                unfocusedContainerColor = com.example.ui.theme.FormSurface
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
                                    textAlign = TextAlign.Start,
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
                                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
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
                                                        text = "${cat.name} (${viewModel.categoryCurrency(cat.name)}${cat.defaultRate})",
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
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 1
                        }.testTag("settings_section_מטבע וברירות מחדל")) {
                            SettingsSectionHeader("מטבע וברירות מחדל", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("קטגוריית ברירת מחדל")
                categories.forEach { cat ->
                    TextButton(onClick = { viewModel.setDefaultCategory(cat.name) }) {
                        Text(if ((localPreferences["defaultCategory"] ?: categories.firstOrNull()?.name) == cat.name) "✓ ${cat.name}" else cat.name)
                    }
                }

                                Text(
                                    text = "בחר את מטבע ברירת המחדל לחישוב וניהול משמרות ברחבי האפליקציה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
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
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 3
                        }.testTag("settings_section_מערכת ומשוב")) {
                            SettingsSectionHeader("מערכת ומשוב", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("עדכונים", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                WorkUpdateSettings()
                                HorizontalDivider(color = Color(0xFF334155))

                                Text("התראות משמרת", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "ניהול הגדרות התראה, טיימר פעיל במכשיר, ושומר מסך כהה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Active Shift Notification Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "התראת משמרת פעילה",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Start
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "הצגת טיימר פעיל ועדכון שכר שנצבר בהתראת רקע קבועה במכשיר בזמן שהשעון רץ.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8E8E93),
                                            textAlign = TextAlign.Start
                                        )
                                    }
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
                                            uncheckedTrackColor = com.example.ui.theme.FormSurface
                                        ),
                                        modifier = Modifier.testTag("service_notification_switch")
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF334155))
                                Text("בינה מלאכותית", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("שימוש אופציונלי בג׳מיני באמצעות המפתח האישי שלך")
                                TextButton(onClick = { showPersonalKeySettings = true }, modifier = Modifier.coachTarget(CoachTarget.API)) { Text("מפתח אישי") }

                                HorizontalDivider(color = Color(0xFF334155))
                                Text("משוב ודיווח על תקלה", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FeedbackForm()
                            }
                        }
                    }
                }
            }

            // Category 5: תחזוקה וגיבוי נתונים
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 4
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 4
                        }.testTag("settings_section_גיבוי ונתונים")) {
                            SettingsSectionHeader("גיבוי ונתונים", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text("הנתונים נשמרים במכשיר. שמור גיבוי גם מחוץ לאפליקציה.")
                                Button(onClick = { saveBackupLauncher.launch("salary-backup.json") }) { Text("שמור קובץ גיבוי") }

                                Text(
                                    text = "ייבוא וייצוא נתונים לצורך גיבוי ושחזור.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Import/Restore section
                                Text(
                                    text = "ייבוא נתונים (אקסל או JSON)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "הדבק שורות מאקסל (מופרד באמצעות Tabs/פסיקים) או טקסט גיבוי JSON:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
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
                                        focusedContainerColor = com.example.ui.theme.FormSurface,
                                        unfocusedContainerColor = com.example.ui.theme.FormSurface
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
                                    textAlign = TextAlign.Start,
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

            if (viewModel.owner.uid != null) {
                val syncStatus by viewModel.cloudStatus.collectAsStateWithLifecycle()
                Text(syncStatus)
                if (BuildConfig.VERSIONED_SYNC_ENABLED) {
                    TextButton(onClick = { viewModel.syncNow() }) { Text("סנכרון עכשיו") }
                    TextButton(onClick = { viewModel.reviewSyncConflicts(context) }) { Text("סקירת שינויים מתנגשים") }
                }
                TextButton(onClick = { viewModel.reviewLegacyData(context) }) {
                    Text("העתקה חד־פעמית של הנתונים המקומיים לחשבון")
                }
            }
            // Sign Out row option
            if (BuildConfig.ACCOUNTS_ENABLED) Box(modifier = Modifier.fillMaxWidth()) {
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
                                    android.app.AlertDialog.Builder(context)
                                        .setTitle("התנתקות מהחשבון")
                                        .setMessage("הנתונים השמורים יישארו בחשבון במכשיר. טפסים שלא נשמרו ייסגרו. לאחר ההתנתקות יוצגו נתוני השימוש המקומי.")
                                        .setNegativeButton("ביטול", null)
                                        .setPositiveButton("התנתקות") { _, _ ->
                                            if (viewModel.signOut(context)) {
                                                onNavigateBack()
                                                Toast.makeText(context, "התנתקת מהמערכת בהצלחה", Toast.LENGTH_SHORT).show()
                                            }
                                        }.show()
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
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sessionUser by viewModel.currentUserSession.collectAsStateWithLifecycle()
                            Text(
                                text = sessionUser?.email ?: sessionUser?.displayName ?: "שימוש מקומי — ללא סנכרון",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.Start
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
                .testTag("settings_action_bar")
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, com.example.ui.theme.FormSurface.copy(alpha = 0.95f), com.example.ui.theme.FormSurface)
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
        var editName by remember(categoryToEditByRate) { mutableStateOf(categoryToEditByRate?.name ?: "") }
        var editCurrency by remember(categoryToEditByRate) { mutableStateOf(viewModel.categoryCurrency(categoryToEditByRate?.name ?: "")) }
        AlertDialog(
            onDismissRequest = { categoryToEditByRate = null },
            title = { Text("עדכון תעריף שעתי ברירת מחדל", color = Color.White, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) },
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
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("שם קטגוריה") })
                    Row { listOf("₪", "$").forEach { currency ->
                        TextButton(onClick = { editCurrency = currency }) { Text(if (currency == editCurrency) "✓ $currency" else currency) }
                    } }
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
                            focusedContainerColor = com.example.ui.theme.FormSurface,
                            unfocusedContainerColor = com.example.ui.theme.FormSurface
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
                                viewModel.editCategory(cat, editName, parsedRate, editCurrency)
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
        mutableStateOf(entry?.let { it.hours.toString() } ?: "")
    }
    var rateText by remember {
        mutableStateOf((entry?.hourlyRate ?: WorkViewModel.DEFAULT_RATE).toString())
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
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                    label = { Text("תעריף שעתי (${entry?.currency ?: "₪"})") },
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

                    if (isTimeRange) {
                        hours = if (entry != null && entry.isTimeRange && startTime == entry.startTime && endTime == entry.endTime) {
                            entry.hours
                        } else com.example.data.WorkEntryEdits.rangeHours(startTime, endTime)
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
        groupWorkersJson: String,
        currency: String
    ) -> Unit
) {
    var selectedCurrency by androidx.compose.runtime.saveable.rememberSaveable(entry.id) { mutableStateOf(entry.currency) }
    val context = LocalContext.current
    var isManualMode by remember { mutableStateOf(!entry.isTimeRange || entry.isGroupShift) }
    var selectedDateMillis by remember { mutableStateOf(entry.date) }
    var startTimeStr by remember { mutableStateOf(entry.startTime ?: "09:00") }
    var endTimeStr by remember { mutableStateOf(entry.endTime ?: "17:00") }
    var breakMinutesStr by remember { mutableStateOf(com.example.data.WorkEntryEdits.breakMinutes(entry).toString()) }
    var hourlyRateStr by remember { mutableStateOf(entry.hourlyRate.toString()) }
    var selectedCategory by remember { mutableStateOf(entry.category) }
    var notesText by remember { mutableStateOf(entry.notes) }
    var manualHoursStr by remember { mutableStateOf(entry.hours.toString()) }

    var isGroupShift by remember { mutableStateOf(entry.isGroupShift) }
    var employerRateStr by remember { mutableStateOf(entry.employerRate?.toString() ?: "") }
    var workerRateStr by remember { mutableStateOf(entry.workerRate?.toString() ?: "") }
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
        containerColor = com.example.ui.theme.FormSurface,
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

            com.example.ui.ShiftCurrencyPicker(selectedCurrency, { selectedCurrency = it }, "edit_shift_currency")
            Text("שינוי המטבע מתקן את סימון הדיווח; הסכום אינו מומר לפי שער חליפין.", style = MaterialTheme.typography.bodySmall)

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
                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                            .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                        .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(12.dp))
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
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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
                            OutlinedTextField(
                                value = currentWorkerName,
                                onValueChange = {
                                    currentWorkerName = it
                                },
                                label = { Text("שם", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
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

            Spacer(modifier = Modifier.height(10.dp))

            // Save Buttons
            Button(
                onClick = {
                    val testHours = if (isManualMode) manualHoursStr.toDoubleOrNull() ?: 0.0 else 1.0
                    val testRate = hourlyRateStr.toDoubleOrNull() ?: 0.0

                    showErrorHours = isManualMode && (manualHoursStr.isBlank() || !testHours.isFinite() || testHours <= 0.0)
                    showErrorRate = hourlyRateStr.isBlank() || !testRate.isFinite() || testRate <= 0.0

                    if (showErrorHours || showErrorRate) {
                        triggerHapticFeedback(context, isDestructive = true)
                        Toast.makeText(context, "נא לתקן את השדות המסומנים באדום", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val finalHours = if (isManualMode) {
                        manualHoursStr.toDoubleOrNull() ?: 8.0
                    } else {
                        com.example.data.WorkEntryEdits.netHours(
                            entry, startTimeStr, endTimeStr, breakMinutesStr.toDoubleOrNull() ?: 0.0
                        )
                    }
                    val finalRate = hourlyRateStr.toDoubleOrNull() ?: 40.0
                    val separateSettingsUnchanged = isGroupShift == entry.isGroupShift &&
                        showSeparateRates == (entry.employerRate != null || entry.workerRate != null)
                    val eRate = if (separateSettingsUnchanged && employerRateStr == (entry.employerRate?.toString() ?: "")) entry.employerRate
                        else if (isGroupShift && showSeparateRates) employerRateStr.toDoubleOrNull() ?: finalRate else null
                    val wRate = if (separateSettingsUnchanged && workerRateStr == (entry.workerRate?.toString() ?: "")) entry.workerRate
                        else if (isGroupShift && showSeparateRates) workerRateStr.toDoubleOrNull() ?: finalRate else null
                    if (!finalHours.isFinite() || finalHours <= 0.0 ||
                        (eRate != null && (!eRate.isFinite() || eRate < 0.0)) ||
                        (wRate != null && (!wRate.isFinite() || wRate < 0.0)) ||
                        (breakMinutesStr.toDoubleOrNull()?.let { !it.isFinite() || it < 0.0 } == true)) {
                        Toast.makeText(context, "נא להזין שעות ותעריפים תקינים", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val gJson = if (isGroupShift && groupWorkers.isNotEmpty()) {
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
                        gJson,
                        selectedCurrency
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
        border = BorderStroke(1.dp, com.example.ui.theme.FormSurface),
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
                            color = com.example.ui.theme.FormSurface,
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

@Composable
fun MoneySummary(values: Map<String, Double>, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(values.entries.joinToString("\n") { com.example.data.WorkMoney.format(it.value, it.key) }.ifEmpty { "0.00" },
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize)
}
