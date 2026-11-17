package com.example.mp3retrowidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

/**
 * Shown when the widget is dropped on the home screen (and from
 * Settings > Tus widgets): picks one of the 7 vertical skins, allows the photo
 * personalisation and can jump to the app settings.
 */
class WidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedSkin = WidgetPrefs.SKIN_ORANGE

    private lateinit var previewBody: ImageView
    private lateinit var previewScreen: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var brightnessBar: SeekBar
    private lateinit var darkenBar: SeekBar
    private lateinit var brightnessValue: TextView
    private lateinit var darkenValue: TextView
    private lateinit var clearPhotoButton: Button
    private lateinit var notificationsStatus: TextView

    private val radioIds = intArrayOf(
        R.id.skin0, R.id.skin1, R.id.skin2, R.id.skin3,
        R.id.skin4, R.id.skin5, R.id.skin6
    )

    private val bodyRes = intArrayOf(
        R.drawable.body_oval,
        R.drawable.body_brick,
        R.drawable.body_swivel_white,
        R.drawable.body_black2008,
        R.drawable.body_swivel_orange,
        R.drawable.body_slider,
        R.drawable.body_flip
    )

    private val screenRes = intArrayOf(
        R.drawable.lcd_oval,
        R.drawable.lcd_brick,
        R.drawable.lcd_swivel_white,
        R.drawable.lcd_black2008,
        R.drawable.lcd_swivel_orange,
        R.drawable.lcd_slider,
        R.drawable.lcd_flip
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The widget is only created if we return RESULT_OK with its id.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContentView(R.layout.activity_configure)

        previewBody = findViewById(R.id.imgSkinPreview)
        previewScreen = findViewById(R.id.imgSkinScreen)
        photoStatus = findViewById(R.id.txtPhotoStatus)
        brightnessBar = findViewById(R.id.sbBrightness)
        darkenBar = findViewById(R.id.sbDarken)
        brightnessValue = findViewById(R.id.txtBrightnessValue)
        darkenValue = findViewById(R.id.txtDarkenValue)
        clearPhotoButton = findViewById(R.id.btnClearPhoto)
        notificationsStatus = findViewById(R.id.txtNotificationStatus)
        val saveButton = findViewById<Button>(R.id.btnSave)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            selectedSkin = WidgetPrefs.getSkin(this, appWidgetId)
        }
        radioIds.getOrNull(selectedSkin)?.let {
            findViewById<android.widget.RadioButton>(it).isChecked = true
        }
        updatePreview()

        findViewById<RadioGroup>(R.id.skinGroup).setOnCheckedChangeListener { _, checkedId ->
            val index = radioIds.indexOf(checkedId)
            if (index >= 0) {
                selectedSkin = index
                updatePreview()
            }
        }

        saveButton.setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            saveButton.visibility = View.GONE
        }

        findViewById<Button>(R.id.btnPickPhoto).setOnClickListener {
            PhotoPicker.pick(this)
        }
        clearPhotoButton.setOnClickListener {
            PhotoProcessor.delete(this)
            WidgetPrefs.setPhotoEnabled(this, false)
            MP3WidgetProvider.updateAllWidgets(this)
            refreshPhoto()
        }

        bindSlider(brightnessBar, brightnessValue, WidgetPrefs.photoBrightness(this)) {
            WidgetPrefs.setPhotoBrightness(this, it)
        }
        bindSlider(darkenBar, darkenValue, WidgetPrefs.photoDarken(this)) {
            WidgetPrefs.setPhotoDarken(this, it)
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                // Some ROMs hide this screen.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPhoto()
        refreshNotifications()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (PhotoPicker.onActivityResult(this, requestCode, resultCode, data)) {
            refreshPhoto()
        }
    }

    private fun updatePreview() {
        val skin = selectedSkin.coerceIn(0, bodyRes.size - 1)
        if (skin == WidgetPrefs.SKIN_ORANGE) {
            // Exact artwork: one single image, screen and keys are part of it.
            previewBody.background = null
            previewBody.setImageResource(R.drawable.skin_orange_body)
            previewBody.scaleType = ImageView.ScaleType.FIT_CENTER
            previewScreen.visibility = View.GONE
        } else {
            previewBody.setImageDrawable(null)
            previewBody.setBackgroundResource(bodyRes[skin])
            previewScreen.visibility = View.VISIBLE
            previewScreen.setBackgroundResource(screenRes[skin])
        }
    }

    private fun saveAndFinish() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPrefs.setSkin(this, appWidgetId, selectedSkin)
            MP3WidgetProvider.updateAllWidgets(this)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
        }
        finish()
    }

    private fun bindSlider(
        bar: SeekBar,
        value: TextView,
        initial: Int,
        write: (Int) -> Unit
    ) {
        bar.max = 100
        bar.progress = initial
        value.text = "$initial %"

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) value.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                write(seekBar?.progress ?: initial)
                PhotoProcessor.invalidate()
                MP3WidgetProvider.updateAllWidgets(this@WidgetConfigureActivity)
                refreshPhoto()
            }
        })
    }

    private fun refreshPhoto() {
        val hasSource = PhotoProcessor.hasSource(this)
        val enabled = WidgetPrefs.isPhotoEnabled(this)
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
}
