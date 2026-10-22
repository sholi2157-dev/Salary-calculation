package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ui.WorkUpdateNotice
import com.example.ui.WorkUpdates
import java.util.concurrent.TimeUnit

/**
 * A new version on the update channel becomes a phone notification (once per version), besides the dot on Home's bell.
 * A tap opens the update sheet; installing is always the user's own tap on Android's installer.
 */
object AppUpdateJobs {
    const val CHANNEL = "app_updates"
    const val ACTION_UPDATE = "com.example.ACTION_UPDATE"
    const val NOTIFICATION_ID = 2003
    private const val WORK = "kis-update-check"

    fun schedule(context: Context) {
        if (!BuildConfig.LOCAL_DISTRIBUTION) return
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }

    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!BuildConfig.LOCAL_DISTRIBUTION) return false
        val release = WorkUpdateNotice.refresh(context, force = true) ?: return false
        return notifyIfNew(context, release, now)
    }

    /** Once per version, only when switched on and allowed, and not in quiet hours (the next check, within hours, tries again). */
    fun notifyIfNew(context: Context, release: WorkUpdates.Release, now: Long): Boolean {
        val settings = PlanSettings(context)
        if (!settings.notifyUpdates || settings.notifiedUpdate >= release.code || !PlanJobs.canNotify(context)) return false
        if (PlanJobs.quietEnd(settings, now) != null) return false
        channel(context)
        val title = "גרסה ${release.name} זמינה"
        val notes = release.notes.ifBlank { "יש עדכון חדש לאפליקציה" }
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java).setAction(ACTION_UPDATE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_kis).setColor(0xFF5145E8.toInt())
            .setContentTitle(title).setContentText(notes).setStyle(NotificationCompat.BigTextStyle().bigText(notes))
            .setContentIntent(open).addAction(0, "לעדכון", open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, note) }
        settings.notifiedUpdate = release.code
        settings.addRecent(PlanSettings.Sent("update", title, notes.lineSequence().first().take(140), now, ""))
        return true
    }

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "גרסאות חדשות", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "הודעה כשיוצאת גרסה חדשה של האפליקציה"
            })
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        AppUpdateJobs.run(applicationContext); Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // No network or a bad answer: the next periodic check simply tries again.
        android.util.Log.w("UpdateWorker", "Update check failed", e)
        Result.success()
    }
}
