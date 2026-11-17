package com.example.mp3retrowidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Photo editor: the picture can be pinched (zoom) and dragged, and the fixed
 * frame in the middle is what ends up on the widget body (110:250 proportion).
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val aspect = 110f / 250f

    private var bitmap: Bitmap? = null

    private val crop = RectF()
    private val matrix = Matrix()
    private val inverse = Matrix()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = 0xC4000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFFFFFFFF.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x55FFFFFF
    }

    private var baseScale = 1f
    private var userScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                bitmap ?: return false
                val previous = baseScale * userScale
                val target = (previous * detector.scaleFactor)
                    .coerceIn(baseScale, baseScale * 8f)
                val ratio = target / previous
                offsetX = detector.focusX - (detector.focusX - offsetX) * ratio
                offsetY = detector.focusY - (detector.focusY - offsetY) * ratio
                userScale = target / baseScale
                constrain()
                invalidate()
                return true
            }
        }
    )

    fun setBitmap(source: Bitmap) {
        bitmap = source
        resetTransform()
        invalidate()
    }

    fun hasBitmap(): Boolean = bitmap != null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCrop(w, h)
        resetTransform()
    }

    private fun updateCrop(width: Int, height: Int) {
        val side = dp(14f)
        val top = dp(66f)
        val bottom = dp(58f)
        val maxW = width - side * 2f
        val maxH = max(height - top - bottom, dp(80f))

        var cropWidth = maxW
        var cropHeight = cropWidth / aspect
        if (cropHeight > maxH) {
            cropHeight = maxH
            cropWidth = cropHeight * aspect
        }

        val left = (width - cropWidth) / 2f
        val cropTop = top + (maxH - cropHeight) / 2f
        crop.set(left, cropTop, left + cropWidth, cropTop + cropHeight)
    }

    private fun resetTransform() {
        val bmp = bitmap ?: return
        if (crop.width() <= 0f || crop.height() <= 0f) return
        baseScale = max(
            crop.width() / bmp.width.toFloat(),
            crop.height() / bmp.height.toFloat()
        )
        userScale = 1f
        offsetX = crop.centerX() - bmp.width * baseScale / 2f
        offsetY = crop.centerY() - bmp.height * baseScale / 2f
        constrain()
    }

    private fun constrain() {
        val bmp = bitmap ?: return
        val scale = baseScale * userScale
        val drawnWidth = bmp.width * scale
        val drawnHeight = bmp.height * scale
        val minX = crop.right - drawnWidth
        val maxX = crop.left
        val minY = crop.bottom - drawnHeight
        val maxY = crop.top
        offsetX = offsetX.coerceIn(min(minX, maxX), max(minX, maxX))
        offsetY = offsetY.coerceIn(min(minY, maxY), max(minY, maxY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF0B0B0D.toInt())

        val bmp = bitmap ?: return
        matrix.setScale(baseScale * userScale, baseScale * userScale)
        matrix.postTranslate(offsetX, offsetY)

        canvas.save()
        canvas.clipRect(crop)
        canvas.drawBitmap(bmp, matrix, bitmapPaint)
        canvas.restore()

        // Dim everything outside the frame.
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, dimPaint)
        canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, dimPaint)
        canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, dimPaint)

        canvas.drawRect(crop, framePaint)
        canvas.drawLine(crop.left + crop.width() / 3f, crop.top, crop.left + crop.width() / 3f, crop.bottom, gridPaint)
        canvas.drawLine(crop.left + crop.width() * 2f / 3f, crop.top, crop.left + crop.width() * 2f / 3f, crop.bottom, gridPaint)
        canvas.drawLine(crop.left, crop.top + crop.height() / 3f, crop.right, crop.top + crop.height() / 3f, gridPaint)
        canvas.drawLine(crop.left, crop.top + crop.height() * 2f / 3f, crop.right, crop.top + crop.height() * 2f / 3f, gridPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                dragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragging) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    constrain()
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** Bitmap cut exactly at the frame, ready to be saved as the photo source. */
    fun croppedBitmap(maxWidth: Int = 1400): Bitmap? {
        val bmp = bitmap ?: return null
        if (crop.width() <= 0f) return null

        val current = Matrix()
        current.setScale(baseScale * userScale, baseScale * userScale)
        current.postTranslate(offsetX, offsetY)
        current.invert(inverse)

        val points = floatArrayOf(crop.left, crop.top, crop.right, crop.bottom)
        inverse.mapPoints(points)

        val left = floor(min(points[0], points[2])).toInt().coerceIn(0, bmp.width - 1)
        val top = floor(min(points[1], points[3])).toInt().coerceIn(0, bmp.height - 1)
        val right = floor(max(points[0], points[2])).toInt().coerceIn(left + 1, bmp.width)
        val bottom = floor(max(points[1], points[3])).toInt().coerceIn(top + 1, bmp.height)

        val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
        if (cropped.width <= maxWidth) return cropped

        val ratio = maxWidth.toFloat() / cropped.width
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            maxWidth,
            max(1, (cropped.height * ratio).toInt()),
            true
        )
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
