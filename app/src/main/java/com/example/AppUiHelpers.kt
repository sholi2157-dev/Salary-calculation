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

// Small shared UI helpers used across screens (moved verbatim from MainActivity.kt).

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
