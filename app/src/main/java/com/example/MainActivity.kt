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
                    indicatorColor = Color(0xFF1E1E1E)
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
                    indicatorColor = Color(0xFF1E1E1E)
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
