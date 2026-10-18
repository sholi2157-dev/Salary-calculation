package com.example.ui.kis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** The Android share sheet and clipboard. All texts come from WorkShareText (docs/v2-sharing.md). */
object KisShare {
    fun shortDate(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.DAY_OF_MONTH)}.${c.get(Calendar.MONTH) + 1}"
    }

    /** The native Android share sheet (WhatsApp and every other installed target). False when no app can receive text (the caller then offers copying instead). */
    fun share(context: Context, text: String, title: String = "שיתוף"): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        return try { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        catch (_: android.content.ActivityNotFoundException) { false }
    }

    /** Shares, or copies with a message when nothing on the phone can receive text. Never sends by itself. */
    fun shareOrCopy(context: Context, text: String, title: String, onMessage: (String) -> Unit) {
        if (!share(context, text, title)) { copy(context, text); onMessage("אין אפליקציה לשיתוף · הטקסט הועתק") }
    }

    fun copy(context: Context, text: String, label: String = "משמרות") {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
