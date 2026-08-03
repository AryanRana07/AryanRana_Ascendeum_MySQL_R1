package com.missyou.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            Constants.SERVICE_CHANNEL_ID,
            context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_MIN
        )
        nm.createNotificationChannel(serviceChannel)

        val alertChannel = NotificationChannel(
            Constants.ALERT_CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(alertChannel)
    }

    fun buildServiceNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, Constants.SERVICE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    fun showMissYouAlert(context: Context, fromName: String) {
        val fullScreenIntent = Intent(context, LockPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_FROM_NAME, fromName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.ALERT_CHANNEL_ID)
            .setContentTitle("$fromName misses you")
            .setContentText("Tap to open your heart")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(Constants.ALERT_NOTIFICATION_ID, notification)
    }

    fun clearAlert(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(Constants.ALERT_NOTIFICATION_ID)
    }

    /**
     * Launching an Activity directly from a background Service is blocked on Android 10+
     * unless the app currently has a visible window. A full-screen-intent notification is
     * specifically exempted from that restriction, so we route through one here too -
     * this is what reliably brings DrawingActivity up even if the app was fully backgrounded.
     */
    fun showEnterCanvas(context: Context) {
        val intent = Intent(context, DrawingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.ALERT_CHANNEL_ID)
            .setContentTitle("Draw something for them")
            .setContentText("Tap to open the shared canvas")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(Constants.CANVAS_NOTIFICATION_ID, notification)
    }

    fun clearCanvasAlert(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(Constants.CANVAS_NOTIFICATION_ID)
    }
}
