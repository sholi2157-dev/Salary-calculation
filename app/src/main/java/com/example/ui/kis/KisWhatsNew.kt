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
            "מהיום מקבלים את הכסף - מהר יותר: Zelle, ביט או חשבון בנק שמורים בפרופיל, ומצטרפים לכל דוח בלחיצה.",
            "כל משמרת עם תאריך עברי ולועזי.",
            "הגדרות האבטחה והפרטיות השתפרו, וכל הסיכומים שלך מחכים בפרופיל האישי.",
        ),
        // 2.3.1: the owner's wording (2026-11-09): the 2.3.0 lines and one more.
        "2.3.1" to listOf(
            "מהיום מקבלים את הכסף - מהר יותר: Zelle, ביט או חשבון בנק שמורים בפרופיל, ומצטרפים לכל דוח בלחיצה.",
            "כל משמרת עם תאריך עברי ולועזי.",
            "הגדרות האבטחה והפרטיות השתפרו, וכל הסיכומים שלך מחכים בפרופיל האישי.",
            "מצב בהיר חדש וברור יותר.",
        ),
        // 2.4.0: three user-facing highlights (identical on the website, web/kis/whatsnew.js).
        "2.4.0" to listOf(
            "משמרות שלא שולמו יותר מ-30 יום מופיעות עכשיו במסך הבית, לכל מקום עבודה, ובלחיצה רואים בדיוק אילו.",
            "לחיצה ארוכה על האייקון במסך הבית פותחת ישר את \"הוספת משמרת\".",
            "בהגדרות, כרטיס ההתראות מראה בדיוק מה פועל: הכול, חלק או כלום.",
        ),
        // 2.4.1: draft highlights, the owner approves the wording at publish (identical on the website, web/kis/whatsnew.js).
        "2.4.1" to listOf(
            "בטלפון מתקפל, כשפותחים אותו, הכול נראה כמו בטלפון רגיל, רק גדול ונוח יותר.",
            "בהיסטוריה יש כפתור שיתוף ששולח בדיוק את המשמרות שמוצגות לפי הסינון או החיפוש.",
            "דוחות לשיתוף קצרים וברורים יותר: מה שמשותף לכל המשמרות כתוב פעם אחת למעלה.",
            "העתקה של משמרות נעשית עכשיו כטבלה מוכנה להדבקה ב-Excel.",
        ),
        // 2.4.2: draft line, the owner approves the wording at publish (identical on the website, web/kis/whatsnew.js).
        "2.4.2" to listOf(
            "מסך הוספת משמרת נקי וקצר יותר: השעות בלי מסגרת, ובלי הצעות מוכנות שלא ביקשת.",
            "תאריך, תעריף, +, משמרת חוזרת ומטבע בשורה אחת. בונוס, החזר הוצאות, הפסקה והערה מוסיפים מה-+.",
            "כשיש בונוס או החזר הוצאות, רואים בדיוק מה ההכנסה שלך ומה לקבל מהמעסיק.",
            "כל המסך נגלל יחד, גם כשהמקלדת פתוחה.",
        ),
        // 2.4.3: draft lines, the owner approves the wording at publish (identical on the website, web/kis/whatsnew.js).
        "2.4.3" to listOf(
            "אפשר לשמור משמרת אחת בכמה ימים: בוחרים ימים בלוח, והיא נשמרת באותן שעות ובאותם פרטים בכל אחד מהם.",
            "הלוח מראה תאריך עברי, לועזי או את שניהם, לפי מה שבחרת בהגדרות.",
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

    private fun prefs(context: Context) = com.example.data.WorkStorage.preferences(context, "kis_whats_new")

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
