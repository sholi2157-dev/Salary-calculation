package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.data.WorkEntry

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

/** Tab-separated table for Excel / Google Sheets (WorkShareText.excel); the caller shows the confirmation. */
fun copyExcelToClipboard(context: Context, filteredEntries: List<WorkEntry>) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("Excel Report", com.example.data.WorkShareText.excel(filteredEntries)))
}
