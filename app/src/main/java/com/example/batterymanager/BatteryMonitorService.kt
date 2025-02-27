package com.example.batterymanager

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class BatteryMonitorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "BatteryMonitorChannel"
    private val CHANNEL_NAME = "Battery Monitor"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification("Battery Monitor Active")
            .apply {
                flags = flags or Notification.FLAG_FOREGROUND_SERVICE
            }
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            monitorBattery()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Battery monitoring service notifications"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        // Create a pending intent for the notification
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Manager")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private suspend fun monitorBattery() {
        while (true) {
            val batteryStatus = registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel = (level * 100 / scale.toFloat()).toInt()

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            if (batteryLevel >= 80 && isCharging) {
                // Update notification to inform user about battery level
                val message = "Battery at $batteryLevel%. Please unplug your charger to prevent overcharging."
                val notification = createNotification(message)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            } else if (isCharging) {
                val message = "Battery at $batteryLevel%. Charging..."
                val notification = createNotification(message)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            delay(5000) // Check every 5 seconds to reduce battery consumption
        }
    }

    // Note: This app monitors battery level and notifies the user when to unplug
    // the charger. Due to Android security restrictions, automatically stopping
    // charging is not possible without root access or manufacturer-specific APIs.


    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
