package com.example.mp3retrowidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Settings screen: per-widget skin, photo personalisation (crop, brightness,
 * darken), notification access and the "hide from drawer" switch.
 */
class MainActivity : Activity() {

    private lateinit var widgetList: LinearLayout
    private lateinit var photoPreview: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var clearPhotoButton: Button
    private lateinit var brightnessBar: SeekBar
    private lateinit var darkenBar: SeekBar
    private lateinit var brightnessValue: TextView
    private lateinit var darkenValue: TextView
    private lateinit var notificationsStatus: TextView
    private lateinit var hideSwitch: Switch
    private lateinit var hideStatus: TextView

    private var bindingHide = false
    private var previewBitmap: Bitmap? = null

    private val skinNames = intArrayOf(
        R.string.skin_oval,
        R.string.skin_brick,
        R.string.skin_swivel_white,
        R.string.skin_black2008,
        R.string.skin_swivel_orange,
        R.string.skin_slider,
        R.string.skin_flip
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        widgetList = findViewById(R.id.widgetList)
        photoPreview = findViewById(R.id.imgPhotoPreview)
        photoStatus = findViewById(R.id.txtPhotoStatus)
        clearPhotoButton = findViewById(R.id.btnClearPhoto)
        brightnessBar = findViewById(R.id.sbBrightness)
        darkenBar = findViewById(R.id.sbDarken)
        brightnessValue = findViewById(R.id.txtBrightnessValue)
        darkenValue = findViewById(R.id.txtDarkenValue)
        notificationsStatus = findViewById(R.id.txtNotificationStatus)
        hideSwitch = findViewById(R.id.switchHide)
        hideStatus = findViewById(R.id.txtHideStatus)

        findViewById<Button>(R.id.btnPickPhoto).setOnClickListener {
            PhotoPicker.pick(this)
        }

        findViewById<Button>(R.id.btnAddWidget).setOnClickListener {
            addWidget()
        }

        clearPhotoButton.setOnClickListener {
            PhotoProcessor.delete(this)
            WidgetPrefs.setPhotoEnabled(this, false)
            MP3WidgetProvider.updateAllWidgets(this)
            refreshPhoto()
        }

        bindSlider(brightnessBar, brightnessValue) {
            WidgetPrefs.setPhotoBrightness(this, it)
        }
        bindSlider(darkenBar, darkenValue) {
            WidgetPrefs.setPhotoDarken(this, it)
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                // Some ROMs hide this screen; the widget still works without it.
            }
        }

        hideSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingHide) return@setOnCheckedChangeListener
            if (checked && !LauncherVisibility.isHidden(this)) {
                confirmHide()
            } else {
                LauncherVisibility.setHidden(this, checked)
                refreshHide()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaController.getInstance(this).refresh()
        refreshWidgets()
        refreshPhoto()
        refreshNotifications()
        refreshHide()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (PhotoPicker.onActivityResult(this, requestCode, resultCode, data)) {
            refreshPhoto()
        }
    }

    // ------------------------------------------------------------------ sliders

    private fun bindSlider(bar: SeekBar, value: TextView, write: (Int) -> Unit) {
        bar.max = 100
        bar.progress = if (bar === brightnessBar) {
            WidgetPrefs.photoBrightness(this)
        } else {
            WidgetPrefs.photoDarken(this)
        }
        value.text = "${bar.progress} %"

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) value.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                write(seekBar?.progress ?: 0)
                PhotoProcessor.invalidate()
                MP3WidgetProvider.updateAllWidgets(this@MainActivity)
                refreshPhoto()
            }
        })
    }

    // ------------------------------------------------------------------ widgets

    private fun refreshWidgets() {
        widgetList.removeAllViews()

        val manager = AppWidgetManager.getInstance(this)
        val ids = manager?.getAppWidgetIds(
            ComponentName(this, MP3WidgetProvider::class.java)
        ) ?: IntArray(0)

        if (ids.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.widget_list_empty)
                setTextColor(0xFF8C919B.toInt())
                textSize = 12.5f
                setLineSpacing(dp(2f).toFloat(), 1f)
                setPadding(0, dp(4f), 0, dp(4f))
            }
            widgetList.addView(empty)
            return
        }

        for (id in ids) {
            val skin = WidgetPrefs.getSkin(this, id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6f), 0, dp(6f))
            }

            val label = TextView(this).apply {
                text = getString(
                    R.string.widget_row_name,
                    id,
                    getString(skinNames.getOrElse(skin) { R.string.skin_oval })
                )
                setTextColor(0xFFE6E6E6.toInt())
                textSize = 13f
            }
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val edit = Button(this).apply {
                text = getString(R.string.widget_row_edit)
                textSize = 12f
                setOnClickListener { openConfigure(id) }
            }
            row.addView(edit)

            widgetList.addView(row)
        }
    }

    private fun openConfigure(appWidgetId: Int) {
        startActivity(
            Intent(this, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
    }

    /** Asks the launcher to pin a fresh 2x4 instance (Android 8+). */
    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, R.string.add_widget_manual, Toast.LENGTH_LONG).show()
            return
        }

        val provider = ComponentName(this, MP3WidgetProvider::class.java)
        val requested = try {
            manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(provider, null, null)
        } catch (e: Exception) {
            false
        }

        if (!requested) {
            Toast.makeText(this, R.string.add_widget_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------- photo

    private fun refreshPhoto() {
        val hasSource = PhotoProcessor.hasSource(this)
        val enabled = WidgetPrefs.isPhotoEnabled(this)

        if (hasSource) {
            val bitmap = PhotoProcessor.preview(this, 320)
            previewBitmap = bitmap
            photoPreview.setImageBitmap(bitmap)
            photoPreview.setBackgroundColor(0xFF1B1B20.toInt())
        } else {
            photoPreview.setImageDrawable(null)
            photoPreview.setBackgroundColor(0xFF1B1B20.toInt())
        }

        photoStatus.setText(
            if (hasSource && enabled) R.string.photo_active else R.string.photo_none
        )
        photoStatus.setTextColor(
            if (hasSource && enabled) 0xFF8FDDA0.toInt() else 0xFFB9BCC4.toInt()
        )
        clearPhotoButton.isEnabled = hasSource

        brightnessBar.progress = WidgetPrefs.photoBrightness(this)
        darkenBar.progress = WidgetPrefs.photoDarken(this)
        brightnessValue.text = "${brightnessBar.progress} %"
        darkenValue.text = "${darkenBar.progress} %"
    }

    // ------------------------------------------------------------ notifications

    private fun refreshNotifications() {
        val enabled = try {
            Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )?.contains(packageName) == true
        } catch (e: Exception) {
            false
        }

        notificationsStatus.setText(
            if (enabled) R.string.notifications_granted else R.string.notifications_missing
        )
        notificationsStatus.setTextColor(
            if (enabled) 0xFF8FDDA0.toInt() else 0xFFFFCC80.toInt()
        )
    }

    // --------------------------------------------------------------------- hide

    private fun confirmHide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hide_title)
            .setMessage(R.string.hide_warning)
            .setPositiveButton(R.string.hide_confirm) { _, _ ->
                LauncherVisibility.setHidden(this, true)
                refreshHide()
            }
            .setNegativeButton(R.string.hide_cancel) { _, _ -> refreshHide() }
            .setOnCancelListener { refreshHide() }
            .show()
    }

    private fun refreshHide() {
        val hidden = LauncherVisibility.isHidden(this)
        bindingHide = true
        hideSwitch.isChecked = hidden
        bindingHide = false
        hideStatus.setText(if (hidden) R.string.hide_on else R.string.hide_off)
        hideStatus.setTextColor(
            if (hidden) 0xFFFFB74D.toInt() else 0xFF8C919B.toInt()
        )
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
