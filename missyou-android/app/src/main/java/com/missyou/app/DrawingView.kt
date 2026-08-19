package com.missyou.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class StrokeSegment(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val color: Int, val widthDp: Float, val alpha: Int
)

/**
 * Everything placed on the canvas - a stroke (pen/marker/eraser drag) or a sticker
 * (emoji/image) - is one of these, kept in creation order in [DrawingView.elements].
 * That single ordered list is what makes Undo ("remove the last element, whatever
 * type it is") and the eraser ("remove whichever element is under my finger")
 * straightforward, and the [id] on each is what lets a remote transform/delete
 * event find the right element on the partner's screen.
 */
sealed class DrawElement(val id: String) {
    class Stroke(id: String, val segments: MutableList<StrokeSegment> = mutableListOf()) : DrawElement(id)
    class Sticker(
        id: String,
        var x: Float,
        var y: Float,
        var scale: Float = 1f,
        var rotationDeg: Float = 0f,
        val emoji: String? = null,
        val bitmap: Bitmap? = null,
        val baseSizeDp: Float = if (emoji != null) 40f else 90f,
    ) : DrawElement(id)
}

private enum class TouchMode { NONE, DRAWING, ERASING, DRAGGING, TRANSFORMING }

/**
 * All positions are stored relative (0f..1f) to the view's size so strokes and
 * stickers line up correctly between two phones with different screen dimensions.
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var currentColor: Int = Color.parseColor("#FF4D6D")
    var currentWidthDp: Float = 8f
    var currentAlpha: Int = 255
    var eraserMode: Boolean = false

    /** Fired per-segment while actively drawing a (non-eraser) stroke. */
    var onStrokeSegment: ((strokeId: String, seg: StrokeSegment) -> Unit)? = null
    var onStickerPlaced: ((DrawElement.Sticker) -> Unit)? = null

    /** Fired (throttled) while dragging/pinching a sticker, and once more on release. */
    var onStickerTransformed: ((DrawElement.Sticker) -> Unit)? = null
    var onElementDeleted: ((id: String) -> Unit)? = null

    private val elements = mutableListOf<DrawElement>()
    private var selected: DrawElement.Sticker? = null

    private var touchMode = TouchMode.NONE
    private var currentStroke: DrawElement.Stroke? = null
    private var lastRelX = 0f
    private var lastRelY = 0f

    // Single-pointer drag of the selected sticker.
    private var primaryPointerId = MotionEvent.INVALID_POINTER_ID
    private var dragStartTouchRelX = 0f
    private var dragStartTouchRelY = 0f
    private var dragStartStickerX = 0f
    private var dragStartStickerY = 0f

    // Two-pointer pinch/rotate of the selected sticker.
    private var secondaryPointerId = MotionEvent.INVALID_POINTER_ID
    private var transformStartDistance = 0f
    private var transformStartAngleDeg = 0f
    private var transformStartScale = 1f
    private var transformStartRotation = 0f
    private var transformStartMidRelX = 0f
    private var transformStartMidRelY = 0f
    private var transformStartStickerX = 0f
    private var transformStartStickerY = 0f

    private var lastTransformEmitAt = 0L

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7C3AED")
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val imageDestRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        for (element in elements) {
            when (element) {
                is DrawElement.Stroke -> drawStroke(canvas, element, w, h)
                is DrawElement.Sticker -> drawSticker(canvas, element, w, h)
            }
        }

        selected?.let { drawSelectionRing(canvas, it, w, h) }
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawElement.Stroke, w: Float, h: Float) {
        for (seg in stroke.segments) {
            strokePaint.color = seg.color
            strokePaint.alpha = seg.alpha
            strokePaint.strokeWidth = dpToPx(seg.widthDp)
            canvas.drawLine(seg.x0 * w, seg.y0 * h, seg.x1 * w, seg.y1 * h, strokePaint)
        }
    }

    private fun drawSticker(canvas: Canvas, sticker: DrawElement.Sticker, w: Float, h: Float) {
        val cx = sticker.x * w
        val cy = sticker.y * h
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(sticker.rotationDeg)
        canvas.scale(sticker.scale, sticker.scale)
        if (sticker.emoji != null) {
            emojiPaint.textSize = dpToPx(sticker.baseSizeDp)
            // Center vertically: drawText anchors on the text baseline, not its middle.
            val offsetY = -(emojiPaint.descent() + emojiPaint.ascent()) / 2f
            canvas.drawText(sticker.emoji, 0f, offsetY, emojiPaint)
        } else if (sticker.bitmap != null) {
            val half = dpToPx(sticker.baseSizeDp) / 2f
            imageDestRect.set(-half, -half, half, half)
            canvas.drawBitmap(sticker.bitmap, null, imageDestRect, null)
        }
        canvas.restore()
    }

    private fun drawSelectionRing(canvas: Canvas, sticker: DrawElement.Sticker, w: Float, h: Float) {
        val cx = sticker.x * w
        val cy = sticker.y * h
        val radius = (dpToPx(sticker.baseSizeDp) / 2f) * sticker.scale + dpToPx(10f)
        canvas.drawCircle(cx, cy, radius, selectionPaint)
    }

    // ---- Touch handling ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleFirstDown(event, w, h)
            MotionEvent.ACTION_POINTER_DOWN -> handleSecondDown(event, w, h)
            MotionEvent.ACTION_MOVE -> handleMove(event, w, h)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event, w, h)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleAllUp()
        }
        return true
    }

    private fun relPoint(event: MotionEvent, pointerIndex: Int, w: Float, h: Float): Pair<Float, Float> {
        return (event.getX(pointerIndex) / w) to (event.getY(pointerIndex) / h)
    }

    private fun handleFirstDown(event: MotionEvent, w: Float, h: Float) {
        primaryPointerId = event.getPointerId(0)
        val (relX, relY) = relPoint(event, 0, w, h)

        val hit = hitTestSticker(relX, relY, w, h)
        if (hit != null) {
            if (eraserMode) {
                deleteElement(hit.id)
                touchMode = TouchMode.NONE
                return
            }
            selected = hit
            touchMode = TouchMode.DRAGGING
            dragStartTouchRelX = relX
            dragStartTouchRelY = relY
            dragStartStickerX = hit.x
            dragStartStickerY = hit.y
            invalidate()
            return
        }

        if (eraserMode) {
            val strokeHit = hitTestStroke(relX, relY, w, h)
            touchMode = TouchMode.ERASING
            if (strokeHit != null) deleteElement(strokeHit.id)
            selected = null
            return
        }

        // Missed every sticker: deselect and start a fresh stroke.
        selected = null
        touchMode = TouchMode.DRAWING
        val stroke = DrawElement.Stroke(UUID.randomUUID().toString())
        elements.add(stroke)
        currentStroke = stroke
        lastRelX = relX
        lastRelY = relY
        invalidate()
    }

    private fun handleSecondDown(event: MotionEvent, w: Float, h: Float) {
        val sticker = selected ?: return
        if (touchMode != TouchMode.DRAGGING) return
        val newPointerIndex = event.actionIndex
        secondaryPointerId = event.getPointerId(newPointerIndex)

        val idx1 = event.findPointerIndex(primaryPointerId)
        val idx2 = event.findPointerIndex(secondaryPointerId)
        if (idx1 < 0 || idx2 < 0) return

        transformStartDistance = pixelDistance(event, idx1, idx2)
        transformStartAngleDeg = pixelAngleDeg(event, idx1, idx2)
        transformStartScale = sticker.scale
        transformStartRotation = sticker.rotationDeg
        val (midX, midY) = midpointRel(event, idx1, idx2, w, h)
        transformStartMidRelX = midX
        transformStartMidRelY = midY
        transformStartStickerX = sticker.x
        transformStartStickerY = sticker.y
        touchMode = TouchMode.TRANSFORMING
    }

    private fun handleMove(event: MotionEvent, w: Float, h: Float) {
        when (touchMode) {
            TouchMode.DRAWING -> {
                val idx = event.findPointerIndex(primaryPointerId)
                if (idx < 0) return
                val (relX, relY) = relPoint(event, idx, w, h)
                val stroke = currentStroke ?: return
                val seg = StrokeSegment(lastRelX, lastRelY, relX, relY, currentColor, currentWidthDp, currentAlpha)
                stroke.segments.add(seg)
                onStrokeSegment?.invoke(stroke.id, seg)
                lastRelX = relX
                lastRelY = relY
                invalidate()
            }
            TouchMode.ERASING -> {
                val idx = event.findPointerIndex(primaryPointerId)
                if (idx < 0) return
                val (relX, relY) = relPoint(event, idx, w, h)
                hitTestSticker(relX, relY, w, h)?.let { deleteElement(it.id) }
                hitTestStroke(relX, relY, w, h)?.let { deleteElement(it.id) }
            }
            TouchMode.DRAGGING -> {
                val sticker = selected ?: return
                val idx = event.findPointerIndex(primaryPointerId)
                if (idx < 0) return
                val (relX, relY) = relPoint(event, idx, w, h)
                sticker.x = dragStartStickerX + (relX - dragStartTouchRelX)
                sticker.y = dragStartStickerY + (relY - dragStartTouchRelY)
                invalidate()
                emitTransformThrottled(sticker)
            }
            TouchMode.TRANSFORMING -> {
                val sticker = selected ?: return
                val idx1 = event.findPointerIndex(primaryPointerId)
                val idx2 = event.findPointerIndex(secondaryPointerId)
                if (idx1 < 0 || idx2 < 0) return

                val currentDistance = pixelDistance(event, idx1, idx2)
                val currentAngle = pixelAngleDeg(event, idx1, idx2)
                if (transformStartDistance > 0f) {
                    val scaleFactor = currentDistance / transformStartDistance
                    sticker.scale = (transformStartScale * scaleFactor).coerceIn(0.3f, 4f)
                }
                sticker.rotationDeg = transformStartRotation + (currentAngle - transformStartAngleDeg)

                val (midX, midY) = midpointRel(event, idx1, idx2, w, h)
                sticker.x = transformStartStickerX + (midX - transformStartMidRelX)
                sticker.y = transformStartStickerY + (midY - transformStartMidRelY)

                invalidate()
                emitTransformThrottled(sticker)
            }
            TouchMode.NONE -> Unit
        }
    }

    private fun handlePointerUp(event: MotionEvent, w: Float, h: Float) {
        val liftedId = event.getPointerId(event.actionIndex)
        if (touchMode == TouchMode.TRANSFORMING && (liftedId == primaryPointerId || liftedId == secondaryPointerId)) {
            // Drop back to a single-finger drag using whichever pointer remains.
            val remainingId = if (liftedId == primaryPointerId) secondaryPointerId else primaryPointerId
            val remainingIndex = event.findPointerIndex(remainingId)
            val sticker = selected
            if (remainingIndex >= 0 && sticker != null) {
                primaryPointerId = remainingId
                secondaryPointerId = MotionEvent.INVALID_POINTER_ID
                val (relX, relY) = relPoint(event, remainingIndex, w, h)
                dragStartTouchRelX = relX
                dragStartTouchRelY = relY
                dragStartStickerX = sticker.x
                dragStartStickerY = sticker.y
                touchMode = TouchMode.DRAGGING
            } else {
                touchMode = TouchMode.NONE
            }
        }
    }

    private fun handleAllUp() {
        selected?.let { onStickerTransformed?.invoke(it) }
        // A tap with no drag never added a segment - drop the otherwise-permanent
        // empty stroke it left behind, or it'd silently eat the first Undo tap.
        currentStroke?.let { if (it.segments.isEmpty()) elements.remove(it) }
        touchMode = TouchMode.NONE
        currentStroke = null
        primaryPointerId = MotionEvent.INVALID_POINTER_ID
        secondaryPointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun emitTransformThrottled(sticker: DrawElement.Sticker) {
        val now = System.currentTimeMillis()
        if (now - lastTransformEmitAt < 40) return
        lastTransformEmitAt = now
        onStickerTransformed?.invoke(sticker)
    }

    private fun pixelDistance(event: MotionEvent, idx1: Int, idx2: Int): Float {
        val dx = (event.getX(idx1) - event.getX(idx2))
        val dy = (event.getY(idx1) - event.getY(idx2))
        return hypot(dx, dy)
    }

    private fun pixelAngleDeg(event: MotionEvent, idx1: Int, idx2: Int): Float {
        val dx = (event.getX(idx2) - event.getX(idx1))
        val dy = (event.getY(idx2) - event.getY(idx1))
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun midpointRel(event: MotionEvent, idx1: Int, idx2: Int, w: Float, h: Float): Pair<Float, Float> {
        val midX = (event.getX(idx1) + event.getX(idx2)) / 2f
        val midY = (event.getY(idx1) + event.getY(idx2)) / 2f
        return (midX / w) to (midY / h)
    }

    private fun hitTestSticker(relX: Float, relY: Float, w: Float, h: Float): DrawElement.Sticker? {
        for (element in elements.asReversed()) {
            if (element !is DrawElement.Sticker) continue
            val cx = element.x * w
            val cy = element.y * h
            val radius = (dpToPx(element.baseSizeDp) / 2f) * element.scale
            val dist = hypot(relX * w - cx, relY * h - cy)
            if (dist <= radius) return element
        }
        return null
    }

    private fun hitTestStroke(relX: Float, relY: Float, w: Float, h: Float): DrawElement.Stroke? {
        val tolerancePx = dpToPx(18f)
        val px = relX * w
        val py = relY * h
        for (element in elements.asReversed()) {
            if (element !is DrawElement.Stroke) continue
            for (seg in element.segments) {
                if (distanceToSegmentPx(px, py, seg.x0 * w, seg.y0 * h, seg.x1 * w, seg.y1 * h) <= tolerancePx) {
                    return element
                }
            }
        }
        return null
    }

    private fun distanceToSegmentPx(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 0f) return hypot(px - x0, py - y0)
        var t = ((px - x0) * dx + (py - y0) * dy) / lengthSq
        t = max(0f, min(1f, t))
        val projX = x0 + t * dx
        val projY = y0 + t * dy
        return hypot(px - projX, py - projY)
    }

    private fun deleteElement(id: String) {
        val removed = elements.removeAll { it.id == id }
        if (selected?.id == id) selected = null
        if (removed) {
            invalidate()
            onElementDeleted?.invoke(id)
        }
    }

    // ---- Public API ----

    fun placeEmoji(emoji: String) {
        val x = 0.2f + Random.nextFloat() * 0.6f
        val y = 0.2f + Random.nextFloat() * 0.6f
        val sticker = DrawElement.Sticker(UUID.randomUUID().toString(), x, y, emoji = emoji)
        elements.add(sticker)
        selected = sticker
        invalidate()
        onStickerPlaced?.invoke(sticker)
    }

    fun placeImage(bitmap: Bitmap) {
        val x = 0.2f + Random.nextFloat() * 0.6f
        val y = 0.2f + Random.nextFloat() * 0.6f
        val sticker = DrawElement.Sticker(UUID.randomUUID().toString(), x, y, bitmap = bitmap)
        elements.add(sticker)
        selected = sticker
        invalidate()
        onStickerPlaced?.invoke(sticker)
    }

    /** Removes the most recently added element, of any type. Returns its id, if any. */
    fun undo(): String? {
        val last = elements.lastOrNull() ?: return null
        elements.removeAt(elements.size - 1)
        if (selected?.id == last.id) selected = null
        invalidate()
        onElementDeleted?.invoke(last.id)
        return last.id
    }

    fun addRemoteStrokeSegment(strokeId: String, seg: StrokeSegment) {
        val stroke = elements.find { it.id == strokeId } as? DrawElement.Stroke
            ?: DrawElement.Stroke(strokeId).also { elements.add(it) }
        stroke.segments.add(seg)
        invalidate()
    }

    fun addRemoteSticker(id: String, x: Float, y: Float, scale: Float, rotationDeg: Float, emoji: String?, bitmap: Bitmap?) {
        if (elements.any { it.id == id }) return
        elements.add(DrawElement.Sticker(id, x, y, scale, rotationDeg, emoji, bitmap))
        invalidate()
    }

    fun updateRemoteSticker(id: String, x: Float, y: Float, scale: Float, rotationDeg: Float) {
        val sticker = elements.find { it.id == id } as? DrawElement.Sticker ?: return
        sticker.x = x
        sticker.y = y
        sticker.scale = scale
        sticker.rotationDeg = rotationDeg
        invalidate()
    }

    fun removeRemoteElement(id: String) {
        val removed = elements.removeAll { it.id == id }
        if (selected?.id == id) selected = null
        if (removed) invalidate()
    }

    fun clearAll() {
        elements.clear()
        selected = null
        touchMode = TouchMode.NONE
        currentStroke = null
        invalidate()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
