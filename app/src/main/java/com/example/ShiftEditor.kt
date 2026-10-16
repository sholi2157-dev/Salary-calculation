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

// Add/edit shift dialog and bottom sheet (moved verbatim from MainActivity.kt).

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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f).testTag("edit_rate_input"),
                    isError = showErrorRate,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = com.example.ui.theme.FormSurface, unfocusedContainerColor = com.example.ui.theme.FormSurface,
                        focusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF5C6BC0),
                        unfocusedBorderColor = if (showErrorRate) Color.Red else Color(0xFF3F3F46),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                    com.example.ui.ShiftCurrencyPicker(selectedCurrency, { selectedCurrency = it }, "edit_shift_currency", Modifier.weight(1f), compact = true)
                }
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
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
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
