package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import com.example.data.WorkEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Small shared helpers: vibration for the live-shift service, report and Excel clipboard text.

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

fun copyExcelToClipboard(context: Context, filteredEntries: List<WorkEntry>, selectedCategoryFilter: String) {
    val headers = listOf("קטגוריה", "תאריך", "שעות", "תעריף שעתי", "שכר לתשלום", "סטטוס", "הערות", "מטבע", "סוג", "בונוס")
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("he", "IL"))
    val rows = filteredEntries.map { entry ->
        val dateStr = sdf.format(Date(entry.date))
        val statusStr = if (entry.isPaid) "שולם" else "ממתין"
        listOf(
            entry.category,
            dateStr,
            String.format(Locale.US, "%.2f", entry.hours),
            // Unknown pay stays an empty cell, never 0; a fixed-price job has no hourly rate.
            if (!entry.payKnown || entry.kind == WorkEntry.KIND_JOB) "" else String.format(Locale.US, "%.2f", entry.hourlyRate),
            if (!entry.payKnown) "" else String.format(Locale.US, "%.2f", entry.totalEarnings),
            statusStr,
            entry.notes,
            entry.currency,
            if (entry.kind == WorkEntry.KIND_JOB) "קבלנות" else "משמרת",
            if (entry.bonus > 0) String.format(Locale.US, "%.2f", entry.bonus) else ""
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
