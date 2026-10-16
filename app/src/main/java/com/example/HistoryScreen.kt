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

// History screen and shift rows (moved verbatim from MainActivity.kt).

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
