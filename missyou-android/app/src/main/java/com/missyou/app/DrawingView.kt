package com.missyou.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

data class StrokeSegment(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val color: Int, val widthDp: Float, val alpha: Int
)

data class EmojiStamp(val x: Float, val y: Float, val emoji: String)

data class ImageStamp(val x: Float, val y: Float, val bitmap: Bitmap, val sizeDp: Float = 90f)

/**
 * All coordinates are stored relative (0f..1f) to the view's size so strokes line up
 * correctly between two phones with different screen dimensions.
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var currentColor: Int = Color.parseColor("#FF4D6D")
    var currentWidthDp: Float = 8f
    var currentAlpha: Int = 255

    var onStrokeDrawn: ((StrokeSegment) -> Unit)? = null
    var onEmojiPlaced: ((EmojiStamp) -> Unit)? = null
    var onImagePlaced: ((ImageStamp) -> Unit)? = null

    private val segments = mutableListOf<StrokeSegment>()
    private val stamps = mutableListOf<EmojiStamp>()
    private val imageStamps = mutableListOf<ImageStamp>()
    private val imageDestRect = RectF()

    private var lastRelX = 0f
    private var lastRelY = 0f
    private var isDrawing = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        for (seg in segments) {
            paint.color = seg.color
            paint.alpha = seg.alpha
            paint.strokeWidth = dpToPx(seg.widthDp)
            canvas.drawLine(seg.x0 * w, seg.y0 * h, seg.x1 * w, seg.y1 * h, paint)
        }

        emojiPaint.textSize = dpToPx(40f)
        for (stamp in stamps) {
            canvas.drawText(stamp.emoji, stamp.x * w, stamp.y * h, emojiPaint)
        }

        for (stamp in imageStamps) {
            val half = dpToPx(stamp.sizeDp) / 2f
            val cx = stamp.x * w
            val cy = stamp.y * h
            imageDestRect.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawBitmap(stamp.bitmap, null, imageDestRect, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return true
        val relX = event.x / w
        val relY = event.y / h

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                lastRelX = relX
                lastRelY = relY
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return true
                val seg = StrokeSegment(lastRelX, lastRelY, relX, relY, currentColor, currentWidthDp, currentAlpha)
                segments.add(seg)
                onStrokeDrawn?.invoke(seg)
                lastRelX = relX
                lastRelY = relY
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
            }
        }
        return true
    }

    fun addRemoteStroke(seg: StrokeSegment) {
        segments.add(seg)
        invalidate()
    }

    fun addRemoteEmoji(stamp: EmojiStamp) {
        stamps.add(stamp)
        invalidate()
    }

    fun placeEmoji(emoji: String) {
        val x = 0.2f + Random.nextFloat() * 0.6f
        val y = 0.2f + Random.nextFloat() * 0.6f
        val stamp = EmojiStamp(x, y, emoji)
        stamps.add(stamp)
        invalidate()
        onEmojiPlaced?.invoke(stamp)
    }

    fun addRemoteImage(stamp: ImageStamp) {
        imageStamps.add(stamp)
        invalidate()
    }

    fun placeImage(bitmap: Bitmap) {
        val x = 0.2f + Random.nextFloat() * 0.6f
        val y = 0.2f + Random.nextFloat() * 0.6f
        val stamp = ImageStamp(x, y, bitmap)
        imageStamps.add(stamp)
        invalidate()
        onImagePlaced?.invoke(stamp)
    }

    fun clearAll() {
        segments.clear()
        stamps.clear()
        imageStamps.clear()
        invalidate()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
