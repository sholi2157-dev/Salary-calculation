package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.data.WorkDatabase
import com.example.data.WorkEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShiftForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var timerJob: kotlinx.coroutines.Job? = null
    private lateinit var shiftState: WorkShiftState
    private var finishing = false

    companion object {
        const val CHANNEL_ID = "ShiftTrackingChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_FROM_NOTIFICATION = "STOP_FROM_NOTIFICATION"
        const val EXTRA_OWNER_UID = "shift_owner_uid"
        const val EXTRA_START_TIME = "shift_start_time"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (finishing) return START_NOT_STICKY
        val owner = if (intent?.hasExtra(EXTRA_OWNER_UID) == true)
            com.example.data.WorkAccountScope(intent.getStringExtra(EXTRA_OWNER_UID))
        else ShiftStateManager.activeOwner(this)
        val candidate = ShiftStateManager.forAccount(this, owner)
        if (intent?.action == ACTION_STOP_FROM_NOTIFICATION &&
            (intent.getLongExtra(EXTRA_START_TIME, -1L) != candidate.activeShiftStartTime.value ||
                owner != ShiftStateManager.activeOwner(this))) return START_NOT_STICKY
        shiftState = candidate
        if (intent != null && intent.action == ACTION_STOP_FROM_NOTIFICATION) {
            if (intent.getLongExtra(EXTRA_START_TIME, -1L) != shiftState.activeShiftStartTime.value) return START_NOT_STICKY
            handleEndShift()
        } else {
            if (shiftState.activeShiftStartTime.value == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            // Build and show persistent notification
            val notification = buildNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            startTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                val startTime = shiftState.activeShiftStartTime.value
                val rate = shiftState.activeShiftRate.value
                if (startTime != null) {
                    val currentSystemTimeSeconds = System.currentTimeMillis() / 1000L
                    val startSystemTimeSeconds = startTime / 1000L
                    val elapsedSecs = currentSystemTimeSeconds - startSystemTimeSeconds
                    val hours = elapsedSecs / 3600
                    val mins = (elapsedSecs % 3600) / 60
                    val secs = elapsedSecs % 60
                    val elapsedString = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                    
                    val liveEarnings = elapsedSecs * (rate / 3600.0)
                    val accumulatedEarnings = Math.round(liveEarnings * 100.0) / 100.0
                    val earningsString = shiftState.activeShiftCurrency.value + String.format(java.util.Locale.US, "%,.2f", accumulatedEarnings)
                    
                    val notification = buildNotification(elapsedString, earningsString)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun handleEndShift() {
        if (finishing) return
        finishing = true
        timerJob?.cancel()
        val context = applicationContext
        val sourceState = shiftState
        val startTime = sourceState.activeShiftStartTime.value
        val category = sourceState.activeShiftCategory.value
        val rate = sourceState.activeShiftRate.value

        if (startTime != null) {
            val currentSystemTimeSeconds = System.currentTimeMillis() / 1000L
            val startSystemTimeSeconds = startTime / 1000L
            val elapsedSeconds = (currentSystemTimeSeconds - startSystemTimeSeconds).coerceAtLeast(0L)
            val hours = elapsedSeconds.toDouble() / 3600.0

            // Save Entry to Room database
            serviceScope.launch {
              try {
                val db = sourceState.owner.database(context)
                val liveEarnings = elapsedSeconds * (rate / 3600.0)
                val roundedEarnings = Math.round(liveEarnings * 100.0) / 100.0
                val entry = WorkEntry(
                    category = category,
                    date = startTime,
                    isTimeRange = false,
                    startTime = null,
                    endTime = null,
                    hours = hours,
                    hourlyRate = rate,
                    totalEarnings = roundedEarnings,
                    isPaid = false,
                    notes = "משמרת פעילה (טיימר החישוב)",
                    currency = sourceState.activeShiftCurrency.value
                )
                db.workDao().finishTimer(startTime, entry) // Atomic row, journal and idempotency receipt.
                
                // Clear state
                sourceState.clear(startTime)

                // Trigger tactile haptic
                triggerHapticFeedback(context, isDestructive = false)

                // Safely dismiss notification & stop service
                stopForeground(true)
                stopSelf()
              } catch (_: Exception) {
                finishing = false
                startTimer() // Keep the persisted timer available for retry; never clear on failure.
              }
            }
        } else {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun buildNotification(timeString: String = "00:00:00", earningsString: String = "₪0.00"): Notification {
        val category = shiftState.activeShiftCategory.value
        val notificationText = "זמן: $timeString | שכר נצבר: $earningsString"

        // Open app on click
        val clickIntent = Intent(this, MainActivity::class.java)
        val clickPendingIntent = PendingIntent.getActivity(
            this, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End Shift Action
        val stopIntent = Intent(this, ShiftForegroundService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
            putExtra(EXTRA_OWNER_UID, shiftState.owner.uid)
            putExtra(EXTRA_START_TIME, shiftState.activeShiftStartTime.value ?: -1L)
            data = android.net.Uri.parse("workshift://stop/${shiftState.owner.storageKey}/${shiftState.activeShiftStartTime.value}")
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("משמרת פעילה: $category")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_notification_overlay) // Default system icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(clickPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "סיים משמרת",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "פעילות משמרת רצה",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "מציג סטטוס עבור משמרת פעילה"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
