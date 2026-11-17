package com.example.mp3retrowidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns the picture the user cropped into the bitmap that is painted on the
 * widget body: centre-cropped to the widget proportions, brightness / darken
 * applied and clipped to the rounded body silhouette so the screen frame and
 * the keys always stay on top.
 */
object PhotoProcessor {

    private const val SOURCE_NAME = "photo_source.png"

    /** Opaque pixels budget: 150k px ARGB is ~600 KB, safe for a RemoteViews. */
    private const val TARGET_AREA = 150_000f

    /** Body corner radius (dp) per skin, must match the body drawables. */
    private val cornerRadii = floatArrayOf(30f, 10f, 18f, 14f, 16f, 9f, 12f)

    private var cache: Bitmap? = null
    private var cacheKey: String? = null

    fun sourceFile(context: Context): File = File(context.filesDir, SOURCE_NAME)

    fun hasSource(context: Context): Boolean = sourceFile(context).exists()

    fun saveCropped(context: Context, bitmap: Bitmap): Boolean = try {
        FileOutputStream(sourceFile(context)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        invalidate()
        true
    } catch (e: Exception) {
        false
    }

    fun delete(context: Context) {
        sourceFile(context).delete()
        invalidate()
    }

    fun invalidate() {
        cache = null
        cacheKey = null
    }

    /**
     * Bitmap ready to be handed to RemoteViews, or null when there is no photo.
     * [widthDp] is the real widget width so the corner radius is not distorted.
     */
    fun render(context: Context, skin: Int, widthDp: Int): Bitmap? {
        val file = sourceFile(context)
        if (!file.exists()) return null

        val brightness = WidgetPrefs.photoBrightness(context)
        val darken = WidgetPrefs.photoDarken(context)
        val key = "${file.lastModified()}|$skin|$brightness|$darken|$widthDp"
        cache?.let { if (key == cacheKey) return it }

        val bitmap = if (skin == WidgetPrefs.SKIN_ORANGE) {
            buildOrange(context, file, brightness, darken, widthDp)
        } else {
            build(file, skin, brightness, darken, widthDp)
        } ?: return null
        cache = bitmap
        cacheKey = key
        return bitmap
    }

    private fun build(
        file: File,
        skin: Int,
        brightness: Int,
        darken: Int,
        widthDp: Int
    ): Bitmap? {
        val source = decode(file) ?: return null

        val aspect = 110f / 250f
        var width = sqrt(TARGET_AREA * aspect)
        var height = width / aspect

        // Do not render much wider than the widget itself.
        val widgetPx = max(widthDp, 90) * 3f
        if (width > widgetPx) {
            width = widgetPx
            height = width / aspect
        }

        val outW = max(width.toInt(), 64)
        val outH = max(height.toInt(), 64)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val radiusDp = cornerRadii.getOrElse(skin) { 18f }
        val radiusPx = radiusDp * outW / max(widthDp, 90).toFloat()
        val inset = outW * 0.004f
        val rect = RectF(inset, inset, outW - inset, outH - inset)
        val path = Path().apply { addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = 0.45f + (brightness.coerceIn(0, 100) / 100f) * 1.1f
        val matrix = ColorMatrix().apply { setScale(scale, scale, scale, 1f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(source, centreCrop(source, outW, outH), Rect(0, 0, outW, outH), paint)

        if (darken > 0) {
            val alpha = (darken.coerceIn(0, 100) * 2.4f).toInt().coerceIn(0, 245)
            canvas.drawColor(Color.argb(alpha, 0, 0, 0))
        }
        canvas.restore()

        source.recycle()
        return output
    }

    /**
     * ORANGE skin: the photo is clipped to the orange shell of the exact
     * artwork, so the silver faceplate, the LCD and the three keys remain the
     * original photo.
     */
    private fun buildOrange(
        context: Context,
        file: File,
        brightness: Int,
        darken: Int,
        widthDp: Int
    ): Bitmap? {
        val source = decode(file) ?: return null

        val aspect = 834f / 1576f
        var width = sqrt(TARGET_AREA * aspect)
        var height = width / aspect

        val widgetPx = max(widthDp, 90) * 3f
        if (width > widgetPx) {
            width = widgetPx
            height = width / aspect
        }

        val outW = max(width.toInt(), 64)
        val outH = max(height.toInt(), 64)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = 0.45f + (brightness.coerceIn(0, 100) / 100f) * 1.1f
        val matrix = ColorMatrix().apply { setScale(scale, scale, scale, 1f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, centreCrop(source, outW, outH), Rect(0, 0, outW, outH), paint)

        if (darken > 0) {
            val alpha = (darken.coerceIn(0, 100) * 2.4f).toInt().coerceIn(0, 245)
            canvas.drawColor(Color.argb(alpha, 0, 0, 0))
        }

        // Clip the photo to the orange shell.
        val mask = BitmapFactory.decodeResource(context.resources, R.drawable.skin_orange_photo_mask)
        if (mask != null) {
            val scaled = Bitmap.createScaledBitmap(mask, outW, outH, true)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(scaled, 0f, 0f, maskPaint)
            if (scaled !== mask) scaled.recycle()
            mask.recycle()
        }

        source.recycle()
        return output
    }

    private fun centreCrop(source: Bitmap, dstW: Int, dstH: Int): Rect {
        val srcW = source.width
        val srcH = source.height
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW.toFloat() / dstH
        return if (srcAspect > dstAspect) {
            val w = (srcH * dstAspect).toInt().coerceAtLeast(1)
            val left = (srcW - w) / 2
            Rect(left, 0, left + w, srcH)
        } else {
            val h = (srcW / dstAspect).toInt().coerceAtLeast(1)
            val top = (srcH - h) / 2
            Rect(0, top, srcW, top + h)
        }
    }

    private fun decode(file: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 1600) sample *= 2

        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } catch (e: Throwable) {
        null
    }

    /** Small 16:9-ish thumbnail for the settings screen. */
    fun preview(context: Context, maxSize: Int): Bitmap? {
        val source = decode(sourceFile(context)) ?: return null
        val scale = min(maxSize.toFloat() / source.width, maxSize.toFloat() / source.height)
        val w = max((source.width * scale).toInt(), 1)
        val h = max((source.height * scale).toInt(), 1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}
