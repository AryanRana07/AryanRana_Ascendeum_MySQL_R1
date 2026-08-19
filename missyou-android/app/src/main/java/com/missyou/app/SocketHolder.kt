package com.missyou.app

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketHolder {
    private const val TAG = "SocketHolder"
    var socket: Socket? = null
        private set

    @Synchronized
    fun connect(context: Context): Socket {
        val existing = socket
        if (existing != null) {
            if (existing.connected()) {
                // Already connected from an earlier call in this process - the
                // EVENT_CONNECT listener below (which sends identify) only fires on
                // a fresh connect, so nothing re-sends identify on this path unless
                // we do it explicitly here.
                identify(context)
            } else {
                existing.connect()
            }
            return existing
        }

        val options = IO.Options().apply {
            forceNew = false
            reconnection = true
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
        }

        val s = IO.socket(Constants.SERVER_URL, options)
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "connected")
            val payload = JSONObject()
            payload.put("userId", Prefs.getOrCreateUserId(context))
            Prefs.getName(context)?.let { payload.put("name", it) }
            s.emit("identify", payload)
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.w(TAG, "connect error: ${args.joinToString()}")
        }
        s.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "disconnected")
        }
        s.on("paired") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            Prefs.setPartnerId(context, data.optString("partnerId", null))
            Prefs.setPartnerName(context, data.optString("partnerName", null))
        }

        s.connect()
        return s
    }

    fun identify(context: Context, name: String? = Prefs.getName(context)) {
        val s = socket ?: return
        val payload = JSONObject()
        payload.put("userId", Prefs.getOrCreateUserId(context))
        name?.let { payload.put("name", it) }
        s.emit("identify", payload)
    }

    fun disconnect() {
        socket?.disconnect()
    }
}
