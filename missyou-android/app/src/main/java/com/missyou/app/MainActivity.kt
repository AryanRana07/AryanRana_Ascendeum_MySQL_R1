package com.missyou.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.missyou.app.databinding.ActivityMainBinding
import io.socket.client.Socket
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var socket: Socket? = null
    private var waitingForResponse = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        NotificationHelper.ensureChannels(this)
        ContextCompat.startForegroundService(this, Intent(this, MissYouService::class.java))

        socket = SocketHolder.connect(applicationContext)
        attachListeners()

        setupNameScreen()
        setupPairingScreen()
        setupHomeScreen()

        renderInitialState()
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.clearAlert(this)
    }

    private fun renderInitialState() {
        val name = Prefs.getName(this)
        val partnerId = Prefs.getPartnerId(this)
        when {
            name.isNullOrBlank() -> showGroup(binding.nameGroup)
            partnerId.isNullOrBlank() -> showGroup(binding.pairingGroup)
            else -> {
                showGroup(binding.homeGroup)
                updatePartnerStatus(false)
            }
        }
    }

    private fun showGroup(group: android.view.View) {
        binding.nameGroup.visibility = android.view.View.GONE
        binding.pairingGroup.visibility = android.view.View.GONE
        binding.homeGroup.visibility = android.view.View.GONE
        group.visibility = android.view.View.VISIBLE
    }

    // ---- Name screen ----
    private fun setupNameScreen() {
        binding.nameContinueButton.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            Prefs.setName(this, name)
            SocketHolder.identify(this, name)
            if (Prefs.getPartnerId(this).isNullOrBlank()) {
                showGroup(binding.pairingGroup)
            } else {
                showGroup(binding.homeGroup)
                updatePartnerStatus(true)
            }
        }
    }

    // ---- Pairing screen ----
    private fun setupPairingScreen() {
        binding.generateCodeButton.setOnClickListener {
            binding.pairingChooseGroup.visibility = android.view.View.GONE
            binding.pairingCreateGroup.visibility = android.view.View.VISIBLE
            binding.pairingCodeText.text = if (socket?.connected() == true) "..." else "Not connected"
            socket?.emit("create-pairing-code")
        }
        binding.haveCodeButton.setOnClickListener {
            binding.pairingChooseGroup.visibility = android.view.View.GONE
            binding.pairingJoinGroup.visibility = android.view.View.VISIBLE
        }
        binding.pairingCreateBackButton.setOnClickListener {
            binding.pairingCreateGroup.visibility = android.view.View.GONE
            binding.pairingChooseGroup.visibility = android.view.View.VISIBLE
        }
        binding.pairingJoinBackButton.setOnClickListener {
            binding.pairingJoinGroup.visibility = android.view.View.GONE
            binding.pairingChooseGroup.visibility = android.view.View.VISIBLE
        }
        binding.linkUpButton.setOnClickListener {
            val code = binding.codeInput.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.length < 4) return@setOnClickListener
            if (socket?.connected() != true) {
                binding.pairingErrorText.visibility = android.view.View.VISIBLE
                binding.pairingErrorText.text = "Not connected to the server yet - check your internet and try again"
                return@setOnClickListener
            }
            binding.pairingErrorText.visibility = android.view.View.GONE
            val payload = JSONObject().put("code", code)
            socket?.emit("redeem-pairing-code", payload)
        }
    }

    // ---- Home screen ----
    private fun setupHomeScreen() {
        binding.missYouButton.setOnClickListener {
            if (waitingForResponse) return@setOnClickListener
            socket?.emit("miss-you")
            waitingForResponse = true
            binding.missYouButton.isEnabled = false
            binding.missYouButton.text = "💗\nSent..."
            binding.homeHintText.visibility = android.view.View.VISIBLE
            binding.homeHintText.text = "Waiting for them to open their heart..."
        }
    }

    private fun updatePartnerStatus(online: Boolean) {
        val name = Prefs.getPartnerName(this) ?: "your partner"
        binding.partnerStatusText.text = "Linked with $name" + if (online) " · online" else ""
    }

    // ---- Socket listeners ----
    private fun attachListeners() {
        val s = socket ?: return

        s.on("pairing-code") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val code = data.optString("code")
            runOnUiThread { binding.pairingCodeText.text = code }
        }

        s.on("paired") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            Prefs.setPartnerId(this, data.optString("partnerId", null))
            Prefs.setPartnerName(this, data.optString("partnerName", null))
            runOnUiThread {
                showGroup(binding.homeGroup)
                updatePartnerStatus(true)
            }
        }

        s.on("pairing-error") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            runOnUiThread {
                binding.pairingErrorText.visibility = android.view.View.VISIBLE
                binding.pairingErrorText.text = data.optString("message")
            }
        }

        s.on("miss-you-response-received") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val accepted = data.optBoolean("accepted", false)
            runOnUiThread {
                waitingForResponse = false
                binding.missYouButton.isEnabled = true
                binding.missYouButton.text = "💗\nI miss you"
                binding.homeHintText.visibility = android.view.View.VISIBLE
                binding.homeHintText.text = if (accepted) "They know now 💌" else "They'll come back to you 💌"
            }
        }

        // DrawingActivity is launched via a full-screen-intent notification from
        // MissYouService (see NotificationHelper.showEnterCanvas), which works reliably
        // regardless of whether this Activity is currently in the foreground.
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}
