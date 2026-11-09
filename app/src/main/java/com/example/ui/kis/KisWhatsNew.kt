package com.example.ui.kis

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * FB23-F11: after the app has been updated, one calm sheet with the main things the user can now do ("מה חדש").
 * Highlights only: no bug fixes, internal changes or a full changelog. The "update available" notice is separate.
 *
 * The content is versioned with the release: one short list per versionName (without a "-rc…" suffix). A version
 * with no entry shows nothing, so a patch release stays quiet unless it adds an entry. Keep the same wording as the
 * website's list (web/kis/whatsnew.js) for the same version.
 */
object WhatsNew {
    // 2.3.0: placeholder draft; the final wording may be replaced at publish time. 3–5 short lines, features only.
    val NOTES: Map<String, List<String>> = mapOf(
        "2.3.0" to listOf(
            "הגדרות פשוטות יותר: כל קטגוריה נפתחת במקום, בלי מסכים נוספים",
            "רישום אוטומטי של משמרות קבועות, ישר בכרטיס העבודה",
            "שינוי תעריף יכול לעדכן גם משמרות קודמות שעוד לא שולמו",
            "בחירה אילו התראות מגיעות לטלפון",
            "מצב כהה חדש ומצב בהיר רגוע יותר",
        ),
    )

    /** "2.3.0-rc2" → "2.3.0". */
    fun key(versionName: String): String = versionName.substringBefore('-').trim()

    /**
     * What to show now, or null. [lastSeenCode] is the version code recorded on the previous run (null before this
     * existed); [updated] says Android installed this version over an earlier one (not a fresh install).
     */
    fun decide(lastSeenCode: Long?, currentCode: Long, updated: Boolean, versionName: String, notes: Map<String, List<String>> = NOTES): List<String>? {
        val fromUpdate = if (lastSeenCode != null) lastSeenCode < currentCode else updated
        return if (fromUpdate) notes[key(versionName)]?.takeIf { it.isNotEmpty() } else null
    }

    private fun prefs(context: Context) = context.getSharedPreferences("kis_whats_new", Context.MODE_PRIVATE)

    /** This version's highlights for the sheet. */
    data class Note(val version: String, val lines: List<String>)

    /** The highlights to show for this run, if any. Nothing is written for a pending note: [seen] records it. */
    fun pending(context: Context): Note? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val p = prefs(context)
        val last = if (p.contains("lastSeenCode")) p.getLong("lastSeenCode", 0L) else null
        val notes = decide(last, code, info.lastUpdateTime > info.firstInstallTime, info.versionName.orEmpty())
        // A fresh install or a version without notes is recorded at once, so it never shows later.
        if (notes == null) p.edit().putLong("lastSeenCode", code).apply()
        notes?.let { Note(key(info.versionName.orEmpty()), it) }
    }.getOrNull()

    fun seen(context: Context) = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        prefs(context).edit().putLong("lastSeenCode", code).apply()
    }
}

@Composable
fun WhatsNewSheet(note: WhatsNew.Note, onDismiss: () -> Unit) {
    val c = Kis.colors
    KisSheet(onDismiss = onDismiss, tag = "whats_new_sheet") {
        Text("מה חדש", style = KisType.title, color = c.ink)
        Text("עודכן לגרסה ${note.version}. הנתונים שלך נשארו כמו שהם.", style = KisType.caption, color = c.muted)
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            note.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(c.accent))
                    Text(line, style = KisType.body, color = c.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        KisButton("קדימה!", onDismiss, Modifier.fillMaxWidth(), height = 48.dp, tag = "whats_new_ok")
    }
}
