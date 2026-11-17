package com.example.mp3retrowidget

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import kotlin.math.max

/**
 * Lets the user zoom / move the picture and crops it to the widget body. The
 * cropped bitmap is stored as the photo source; brightness and darken are
 * applied later by [PhotoProcessor], so the sliders stay non destructive.
 */
class CropActivity : Activity() {

    companion object {
        const val EXTRA_URI = "uri"
    }

    private lateinit var cropView: CropView
    private var source: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        cropView = findViewById(R.id.cropView)
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnUse).setOnClickListener { saveAndFinish() }

        val uri = intent?.getStringExtra(EXTRA_URI)
        if (uri == null) {
            finish()
            return
        }
        load(Uri.parse(uri))
    }

    private fun load(uri: Uri) {
        Thread {
            val bitmap = try {
                decode(uri)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (bitmap == null) {
                    Toast.makeText(this, R.string.photo_pick_error, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    source = bitmap
                    cropView.setBitmap(bitmap)
                }
            }
        }.start()
    }

    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun saveAndFinish() {
        val cropped = cropView.croppedBitmap()
        if (cropped == null) {
            finish()
            return
        }
        val saved = PhotoProcessor.saveCropped(this, cropped)
        cropped.recycle()
        if (!saved) {
            Toast.makeText(this, R.string.photo_pick_error, Toast.LENGTH_SHORT).show()
            return
        }
        WidgetPrefs.setPhotoEnabled(this, true)
        MP3WidgetProvider.updateAllWidgets(this)
        setResult(RESULT_OK, Intent())
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        source?.recycle()
        source = null
    }
}
