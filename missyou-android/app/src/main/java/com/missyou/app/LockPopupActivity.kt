package com.missyou.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.missyou.app.databinding.ActivityLockPopupBinding
import org.json.JSONObject

class LockPopupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockPopupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        binding = ActivityLockPopupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fromName = intent.getStringExtra(Constants.EXTRA_FROM_NAME) ?: "Your partner"
        binding.popupTitle.text = "$fromName misses you"

        val socket = SocketHolder.connect(applicationContext)

        binding.acceptButton.setOnClickListener {
            socket.emit("miss-you-response", JSONObject().put("accepted", true))
            NotificationHelper.clearAlert(this)
            finish()
        }
        binding.declineButton.setOnClickListener {
            socket.emit("miss-you-response", JSONObject().put("accepted", false))
            NotificationHelper.clearAlert(this)
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
