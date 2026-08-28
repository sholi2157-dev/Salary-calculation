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

// Unified State management for tracking active shifts across ViewModel and Service
object ShiftStateManager {
    val activeShiftStartTime = MutableStateFlow<Long?>(null)
    val activeShiftCategory = MutableStateFlow("עצמאי")
    val activeShiftRate = MutableStateFlow(40.0)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("active_shift_prefs", Context.MODE_PRIVATE)
        val startTime = prefs.getLong("start_time", -1L)
        if (startTime != -1L) {
            activeShiftStartTime.value = startTime
            activeShiftCategory.value = prefs.getString("category", "עצמאי") ?: "עצמאי"
            activeShiftRate.value = prefs.getFloat("rate", 40f).toDouble()
        } else {
            activeShiftStartTime.value = null
        }
    }

    fun start(context: Context, category: String, rate: Double, startTime: Long) {
        val prefs = context.getSharedPreferences("active_shift_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("start_time", startTime)
            putString("category", category)
            putFloat("rate", rate.toFloat())
            apply()
        }
        activeShiftStartTime.value = startTime
        activeShiftCategory.value = category
        activeShiftRate.value = rate
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences("active_shift_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        activeShiftStartTime.value = null
    }
}

class ShiftForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var timerJob: kotlinx.coroutines.Job? = null

    companion object {
        const val CHANNEL_ID = "ShiftTrackingChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_FROM_NOTIFICATION = "STOP_FROM_NOTIFICATION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_STOP_FROM_NOTIFICATION) {
            handleEndShift()
        } else {
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
                val startTime = ShiftStateManager.activeShiftStartTime.value
                val rate = ShiftStateManager.activeShiftRate.value
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
                    val earningsString = String.format(java.util.Locale.US, "₪%,.2f", accumulatedEarnings)
                    
                    val notification = buildNotification(elapsedString, earningsString)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun handleEndShift() {
        timerJob?.cancel()
        val context = applicationContext
        val startTime = ShiftStateManager.activeShiftStartTime.value
        val category = ShiftStateManager.activeShiftCategory.value
        val rate = ShiftStateManager.activeShiftRate.value

        if (startTime != null) {
            val currentSystemTimeSeconds = System.currentTimeMillis() / 1000L
            val startSystemTimeSeconds = startTime / 1000L
            val elapsedSeconds = currentSystemTimeSeconds - startSystemTimeSeconds
            val hours = elapsedSeconds.toDouble() / 3600.0

            // Save Entry to Room database
            serviceScope.launch {
                val db = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
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
                    notes = "משמרת פעילה (טיימר החישוב)"
                )
                val insertedId = db.workDao().insertEntry(entry)
                
                try {
                    val uid = com.example.api.AuthManager.currentUser.value?.uid
                        ?: com.example.api.FirebaseSafeInitializer.currentUser.value?.uid
                    if (!uid.isNullOrBlank()) {
                        com.example.api.FirestoreSyncManager.saveShift(
                            userId = uid,
                            entry = entry.copy(id = insertedId.toInt())
                        )
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("ShiftForegroundService", "Firestore sync safeguard caught: ${t.localizedMessage}")
                }
                
                // Clear state
                ShiftStateManager.clear(context)

                // Trigger tactile haptic
                triggerHapticFeedback(context, isDestructive = false)

                // Safely dismiss notification & stop service
                stopForeground(true)
                stopSelf()
            }
        } else {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun buildNotification(timeString: String = "00:00:00", earningsString: String = "₪0.00"): Notification {
        val category = ShiftStateManager.activeShiftCategory.value
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
