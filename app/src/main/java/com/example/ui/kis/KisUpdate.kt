package com.example.ui.kis

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.WorkUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class UpdatePhase { OFFER, PERMISSION, DOWNLOADING, VERIFYING, FAILED }

/**
 * One sheet for every way into an update (the bell, the phone notification, the daily check and Settings):
 * what is new → (once) allow installs from the app → the download with its percentage → verification → Android's
 * own installer. Nothing is installed without the user's tap there; a failed download leaves the app and data as they are.
 */
@Composable
fun UpdateSheet(release: WorkUpdates.Release, onDismiss: () -> Unit, onLater: () -> Unit = onDismiss) {
    val c = Kis.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.OFFER) }
    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun canInstall() = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun start() {
        if (!canInstall()) {
            phase = UpdatePhase.PERMISSION
            return
        }
        phase = UpdatePhase.DOWNLOADING; progress = 0f
        job = scope.launch {
            try {
                val file = WorkUpdates.download(context, release) { p -> progress = p; if (p >= 1f) phase = UpdatePhase.VERIFYING }
                WorkUpdates.install(context, file)
                onDismiss()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { phase = UpdatePhase.FAILED }
        }
    }
    // Back from Android's "allow from this source" screen: continue by itself once it is allowed.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && phase == UpdatePhase.PERMISSION && canInstall()) start()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val busy = phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.VERIFYING
    KisSheet(onDismiss = { job?.cancel(); if (busy) onDismiss() else onLater() }, tag = "update_sheet") {
        when (phase) {
            UpdatePhase.OFFER -> {
                Text("גרסה ${release.name} זמינה", style = KisType.title, color = c.ink)
                if (release.notes.isNotBlank()) Text(release.notes, style = KisType.body, color = c.muted,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).testTag("update_notes"))
                Text("כל המשמרות, הסכומים וההגדרות נשמרים בעדכון.", style = KisType.caption, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KisButton("אחר כך", onLater, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp, tag = "update_later")
                    KisButton("עדכון", { start() }, Modifier.weight(1f), height = 52.dp, tag = "update_start")
                }
            }
            UpdatePhase.PERMISSION -> {
                Text("עוד צעד אחד", style = KisType.title, color = c.ink)
                Text("Android מבקש לאשר פעם אחת עדכונים מהאפליקציה. במסך שייפתח, מפעילים את \"התרה מהמקור הזה\" וחוזרים לכאן. ההורדה תתחיל לבד.",
                    style = KisType.body, color = c.muted)
                KisButton("למסך האישור", {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                }, Modifier.fillMaxWidth(), height = 52.dp, tag = "update_allow")
            }
            UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING -> {
                Text(if (phase == UpdatePhase.VERIFYING) "מאמת את העדכון" else "מוריד את העדכון", style = KisType.title, color = c.ink)
                Text("בסיום ההורדה יופיע מסך ההתקנה של Android.", style = KisType.body, color = c.muted)
                val shown by animateFloatAsState(progress, tween(250), label = "download")
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(c.sunk)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }.testTag("update_progress")) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(99.dp)).background(Workplaces[0].accent(c.dark)))
                }
                Text(if (phase == UpdatePhase.VERIFYING) "בודק שהקובץ שלם ומקורי…" else "${Math.round(progress * 100)}%",
                    style = KisType.label, color = c.ink, modifier = Modifier.testTag("update_percent"))
                KisButton("ביטול", { job?.cancel(); onDismiss() }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "update_cancel")
            }
            UpdatePhase.FAILED -> {
                Text("העדכון לא הושלם", style = KisType.title, color = c.ink)
                Text("ההורדה או הבדיקה נכשלו. האפליקציה הקיימת וכל הנתונים נשארו כמו שהיו.", style = KisType.body, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KisButton("סגירה", onDismiss, Modifier.weight(1f), KisButtonKind.GHOST, height = 52.dp)
                    KisButton("לנסות שוב", { start() }, Modifier.weight(1f), height = 52.dp, tag = "update_retry")
                }
            }
        }
    }
}
