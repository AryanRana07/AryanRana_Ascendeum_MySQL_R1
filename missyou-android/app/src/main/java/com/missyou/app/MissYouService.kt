package com.missyou.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.json.JSONObject

class MissYouService : Service() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        startForeground(Constants.SERVICE_NOTIFICATION_ID, NotificationHelper.buildServiceNotification(this))

        val socket = SocketHolder.connect(applicationContext)

        socket.off("miss-you-received")
        socket.on("miss-you-received") { args ->
            val data = args.getOrNull(0) as? JSONObject
            val fromName = data?.optString("fromName")?.takeIf { it.isNotBlank() }
                ?: Prefs.getPartnerName(applicationContext)
                ?: "Your partner"
            NotificationHelper.showMissYouAlert(applicationContext, fromName)
        }

        socket.off("enter-canvas")
        socket.on("enter-canvas") {
            NotificationHelper.showEnterCanvas(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }
}
