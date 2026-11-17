package com.example.mp3retrowidget

import android.content.Context

/** Per-widget skin storage + global photo / launcher settings. */
object WidgetPrefs {

    private const val PREFS = "mp3_retro_widget_prefs"
    private const val KEY_SKIN = "skin_"

    // The seven vertical skins.
    const val SKIN_ORANGE = 0
    const val SKIN_BRICK = 1
    const val SKIN_SWIVEL_WHITE = 2
    const val SKIN_BLACK_2008 = 3
    const val SKIN_SWIVEL_ORANGE = 4
    const val SKIN_SLIDER = 5
    const val SKIN_FLIP = 6
    const val SKIN_COUNT = 7

    private const val KEY_PHOTO_ENABLED = "photo_enabled"
    private const val KEY_BRIGHTNESS = "photo_brightness"
    private const val KEY_DARKEN = "photo_darken"
    private const val KEY_LAUNCHER_HIDDEN = "launcher_hidden"

    fun getSkin(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SKIN + appWidgetId, SKIN_ORANGE)
            .coerceIn(0, SKIN_COUNT - 1)
    }

    fun setSkin(context: Context, appWidgetId: Int, skin: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SKIN + appWidgetId, skin.coerceIn(0, SKIN_COUNT - 1))
            .apply()
    }

    fun removeSkin(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SKIN + appWidgetId)
            .apply()
    }

    // ------------------------------------------------------------------ photo

    /** True when a photo has been cropped and the user wants it on the body. */
    fun isPhotoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHOTO_ENABLED, false) &&
            PhotoProcessor.sourceFile(context).exists()

    fun setPhotoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PHOTO_ENABLED, enabled).apply()
    }

    /** 0..100, 50 = untouched. */
    fun photoBrightness(context: Context): Int =
        prefs(context).getInt(KEY_BRIGHTNESS, 50).coerceIn(0, 100)

    fun setPhotoBrightness(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_BRIGHTNESS, value.coerceIn(0, 100)).apply()
    }

    /** 0..100, 0 = untouched. */
    fun photoDarken(context: Context): Int =
        prefs(context).getInt(KEY_DARKEN, 0).coerceIn(0, 100)

    fun setPhotoDarken(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DARKEN, value.coerceIn(0, 100)).apply()
    }

    // ----------------------------------------------------------------- drawer

    fun isLauncherHidden(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAUNCHER_HIDDEN, false)

    fun setLauncherHidden(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAUNCHER_HIDDEN, hidden).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
