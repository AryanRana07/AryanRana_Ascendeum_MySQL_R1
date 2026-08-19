package com.missyou.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import com.missyou.app.databinding.ActivityDrawingBinding
import io.socket.client.Socket
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private data class BrushStyle(val label: String, val widthDp: Float, val alpha: Int)

class DrawingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawingBinding
    private var socket: Socket? = null

    private val palette = listOf(
        "#FF4D6D", "#FF8FAB", "#FFB3C6", "#7C3AED", "#3B82F6", "#111827", "#FFFFFF"
    )
    private val styles = listOf(
        BrushStyle("Fine", 4f, 255),
        BrushStyle("Pen", 8f, 255),
        BrushStyle("Marker", 20f, 120)
    )
    private val emojis = listOf("💗", "💕", "😘", "🥰", "😍", "💌", "🌹", "✨", "🐻", "🔥")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

        socket = SocketHolder.connect(applicationContext)

        buildColorRow()
        buildStyleRow()
        buildEmojiRow()
        setupStickerInput()
        wireDrawingCallbacks()
        attachSocketListeners()

        binding.closeButton.setOnClickListener {
            socket?.emit("leave-canvas")
            finish()
        }
        binding.clearButton.setOnClickListener {
            binding.drawingView.clearAll()
            socket?.emit("clear-canvas")
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

    private fun buildColorRow() {
        val sizePx = dp(36)
        val marginPx = dp(6)
        palette.forEach { hex ->
            val color = Color.parseColor(hex)
            val swatch = TextView(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), Color.parseColor("#3D2B3A"))
                }
                setOnClickListener { binding.drawingView.currentColor = color }
            }
            val params = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            binding.colorRow.addView(swatch, params)
        }
        binding.drawingView.currentColor = Color.parseColor(palette.first())
    }

    private fun buildStyleRow() {
        styles.forEachIndexed { index, style ->
            val button = TextView(this).apply {
                text = style.label
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTextColor(Color.parseColor("#FF4D6D"))
                background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.bg_button_secondary)
                setOnClickListener {
                    binding.drawingView.currentWidthDp = style.widthDp
                    binding.drawingView.currentAlpha = style.alpha
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                val m = dp(4)
                setMargins(m, 0, m, 0)
            }
            binding.styleRow.addView(button, params)
            if (index == 0) {
                binding.drawingView.currentWidthDp = style.widthDp
                binding.drawingView.currentAlpha = style.alpha
            }
        }
    }

    private fun buildEmojiRow() {
        emojis.forEach { emoji ->
            val view = TextView(this).apply {
                text = emoji
                textSize = 26f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { binding.drawingView.placeEmoji(emoji) }
            }
            binding.emojiRow.addView(view)
        }
    }

    /**
     * Gboard (and most keyboards) can send an image/GIF sticker straight into any input
     * field via the "commit content" API - this just needs a focusable input that declares
     * it accepts image mime types. We don't care about the sticker's accompanying text,
     * only the image, which we place on the canvas like an emoji stamp.
     *
     * Note: an animated GIF is decoded to its first frame only (BitmapFactory doesn't
     * animate), so GIFs land as a static image rather than looping - most Gboard "stickers"
     * are already static PNGs, so this covers the common case.
     */
    private fun setupStickerInput() {
        ViewCompat.setOnReceiveContentListener(
            binding.stickerInput, arrayOf("image/*")
        ) { _, payload ->
            val split = payload.partition { item -> item.uri != null }
            val uriContent = split.first
            if (uriContent != null) {
                for (i in 0 until uriContent.clip.itemCount) {
                    val uri = uriContent.clip.getItemAt(i).uri ?: continue
                    val bitmap = decodeDownscaledBitmap(uri)
                    if (bitmap != null) {
                        binding.drawingView.placeImage(bitmap)
                    } else {
                        Toast.makeText(this, "Couldn't load that sticker", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            binding.stickerInput.setText("")
            split.second
        }
    }

    private fun decodeDownscaledBitmap(uri: Uri, maxDimensionPx: Int = 400): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sampleSize = 1
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (largestSide / sampleSize > maxDimensionPx) sampleSize *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun wireDrawingCallbacks() {
        binding.drawingView.onStrokeDrawn = { seg ->
            val payload = JSONObject().apply {
                put("x0", seg.x0)
                put("y0", seg.y0)
                put("x1", seg.x1)
                put("y1", seg.y1)
                put("strokeColor", String.format("#%06X", 0xFFFFFF and seg.color))
                put("width", seg.widthDp)
                put("alpha", seg.alpha)
            }
            socket?.emit("draw-stroke", payload)
        }
        binding.drawingView.onEmojiPlaced = { stamp ->
            val payload = JSONObject().apply {
                put("x", stamp.x)
                put("y", stamp.y)
                put("emoji", stamp.emoji)
            }
            socket?.emit("draw-emoji", payload)
        }
        binding.drawingView.onImagePlaced = { stamp ->
            val bytes = ByteArrayOutputStream().use { out ->
                stamp.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            val payload = JSONObject().apply {
                put("x", stamp.x)
                put("y", stamp.y)
                put("image", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            socket?.emit("draw-sticker", payload)
        }
    }

    private fun attachSocketListeners() {
        val s = socket ?: return

        s.off("draw-stroke")
        s.on("draw-stroke") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val color = try {
                Color.parseColor(data.optString("strokeColor", "#FF4D6D"))
            } catch (e: IllegalArgumentException) {
                Color.parseColor("#FF4D6D")
            }
            val seg = StrokeSegment(
                x0 = data.optDouble("x0", 0.0).toFloat(),
                y0 = data.optDouble("y0", 0.0).toFloat(),
                x1 = data.optDouble("x1", 0.0).toFloat(),
                y1 = data.optDouble("y1", 0.0).toFloat(),
                color = color,
                widthDp = data.optDouble("width", 8.0).toFloat(),
                alpha = data.optInt("alpha", 255)
            )
            runOnUiThread { binding.drawingView.addRemoteStroke(seg) }
        }

        s.off("draw-emoji")
        s.on("draw-emoji") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val stamp = EmojiStamp(
                x = data.optDouble("x", 0.5).toFloat(),
                y = data.optDouble("y", 0.5).toFloat(),
                emoji = data.optString("emoji", "💗")
            )
            runOnUiThread { binding.drawingView.addRemoteEmoji(stamp) }
        }

        s.off("draw-sticker")
        s.on("draw-sticker") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val base64 = data.optString("image")
            val bitmap = try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            } ?: return@on
            val stamp = ImageStamp(
                x = data.optDouble("x", 0.5).toFloat(),
                y = data.optDouble("y", 0.5).toFloat(),
                bitmap = bitmap
            )
            runOnUiThread { binding.drawingView.addRemoteImage(stamp) }
        }

        s.off("clear-canvas")
        s.on("clear-canvas") {
            runOnUiThread { binding.drawingView.clearAll() }
        }

        s.off("partner-left-canvas")
        s.on("partner-left-canvas") {
            runOnUiThread {
                Toast.makeText(this, "Your partner left the canvas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.off("draw-stroke")
        socket?.off("draw-emoji")
        socket?.off("draw-sticker")
        socket?.off("clear-canvas")
        socket?.off("partner-left-canvas")
        NotificationHelper.clearCanvasAlert(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
