package com.missyou.app

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.Fade
import android.transition.TransitionManager
import android.view.animation.OvershootInterpolator
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
        updateStatusBanner()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatusBanner()
    }

    private fun updateStatusBanner() {
        if (binding.homeGroup.visibility != android.view.View.VISIBLE) return
        val notificationsOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        val disconnected = socket?.connected() != true

        when {
            notificationsOff -> {
                binding.statusBannerText.visibility = android.view.View.VISIBLE
                binding.statusBannerText.text =
                    "Notifications are off - you won't see your partner's alerts. Tap to fix"
                binding.statusBannerText.setOnClickListener {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                }
            }
            disconnected -> {
                binding.statusBannerText.visibility = android.view.View.VISIBLE
                binding.statusBannerText.text = "Reconnecting to the server..."
                binding.statusBannerText.setOnClickListener(null)
            }
            else -> {
                binding.statusBannerText.visibility = android.view.View.GONE
                binding.statusBannerText.setOnClickListener(null)
            }
        }
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
        TransitionManager.beginDelayedTransition(binding.root, Fade())
        binding.nameGroup.visibility = android.view.View.GONE
        binding.pairingGroup.visibility = android.view.View.GONE
        binding.homeGroup.visibility = android.view.View.GONE
        group.visibility = android.view.View.VISIBLE
        if (group === binding.homeGroup) updateStatusBanner()
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
            // Re-send identify right before this - the server drops
            // create-pairing-code silently if it doesn't know our userId yet,
            // and we've seen that race actually happen in practice.
            SocketHolder.identify(this)
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
            SocketHolder.identify(this)
            val payload = JSONObject().put("code", code)
            socket?.emit("redeem-pairing-code", payload)
        }
    }

    // ---- Home screen ----
    private fun setupHomeScreen() {
        binding.missYouButton.setOnClickListener {
            if (waitingForResponse) return@setOnClickListener
            pulseMissYouButton()
            socket?.emit("miss-you")
            waitingForResponse = true
            binding.missYouButton.isEnabled = false
            binding.missYouButton.text = "💗\nSent..."
            TransitionManager.beginDelayedTransition(binding.homeGroup, Fade())
            binding.homeHintText.visibility = android.view.View.VISIBLE
            binding.homeHintText.text = "Waiting for them to open their heart..."
        }
        binding.unlinkText.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Unlink from partner?")
                .setMessage("You'll need a new pairing code to link again.")
                .setPositiveButton("Unlink") { _, _ ->
                    socket?.emit("unlink")
                    Prefs.setPartnerId(this, null)
                    Prefs.setPartnerName(this, null)
                    waitingForResponse = false
                    showGroup(binding.pairingGroup)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun pulseMissYouButton() {
        val scaleX = ObjectAnimator.ofFloat(binding.missYouButton, "scaleX", 1f, 0.85f, 1.08f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.missYouButton, "scaleY", 1f, 0.85f, 1.08f, 1f)
        listOf(scaleX, scaleY).forEach {
            it.duration = 350
            it.interpolator = OvershootInterpolator()
            it.start()
        }
    }

    private fun updatePartnerStatus(online: Boolean) {
        val name = Prefs.getPartnerName(this) ?: "your partner"
        binding.partnerStatusText.text = "Linked with $name" + if (online) " · online" else ""
    }

    // ---- Socket listeners ----
    // Each .off() before .on() keeps this idempotent - onCreate (and therefore this
    // function) can run again if Android recreates the Activity, and without .off()
    // the shared SocketHolder.socket singleton would accumulate duplicate listeners.
    private fun attachListeners() {
        val s = socket ?: return

        s.off(Socket.EVENT_CONNECT)
        s.on(Socket.EVENT_CONNECT) { runOnUiThread { updateStatusBanner() } }
        s.off(Socket.EVENT_DISCONNECT)
        s.on(Socket.EVENT_DISCONNECT) { runOnUiThread { updateStatusBanner() } }

        s.off("miss-you-received")
        s.on("miss-you-received") { args ->
            val data = args.getOrNull(0) as? JSONObject
            val fromName = data?.optString("fromName")?.takeIf { it.isNotBlank() }
                ?: Prefs.getPartnerName(this)
                ?: "Your partner"
            // Belt-and-suspenders: MissYouService already shows a full-screen-intent
            // notification for this, but that path silently does nothing if notification
            // permission was denied. Since this app is currently in the foreground (it's
            // alive and attached to this socket), we can launch the popup directly too -
            // this path doesn't depend on notification permission at all.
            val intent = Intent(applicationContext, LockPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Constants.EXTRA_FROM_NAME, fromName)
            }
            applicationContext.startActivity(intent)
        }

        s.off("partner-unlinked")
        s.on("partner-unlinked") {
            runOnUiThread {
                Prefs.setPartnerId(this, null)
                Prefs.setPartnerName(this, null)
                waitingForResponse = false
                showGroup(binding.pairingGroup)
            }
        }

        s.off("pairing-code")
        s.on("pairing-code") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val code = data.optString("code")
            runOnUiThread { binding.pairingCodeText.text = code }
        }

        s.off("paired")
        s.on("paired") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            Prefs.setPartnerId(this, data.optString("partnerId", null))
            Prefs.setPartnerName(this, data.optString("partnerName", null))
            runOnUiThread {
                showGroup(binding.homeGroup)
                updatePartnerStatus(true)
            }
        }

        s.off("pairing-error")
        s.on("pairing-error") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            runOnUiThread {
                binding.pairingErrorText.visibility = android.view.View.VISIBLE
                binding.pairingErrorText.text = data.optString("message")
            }
        }

        s.off("miss-you-response-received")
        s.on("miss-you-response-received") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val accepted = data.optBoolean("accepted", false)
            runOnUiThread {
                waitingForResponse = false
                binding.missYouButton.isEnabled = true
                binding.missYouButton.text = "💗\nI miss you"
                TransitionManager.beginDelayedTransition(binding.homeGroup, Fade())
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
