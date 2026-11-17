package com.example.mp3retrowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.util.Locale
import kotlin.math.max

/**
 * Widget provider. All interactions arrive as explicit broadcasts, so the
 * receiver instance is short lived and no state is kept here.
 */
class MP3WidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_CENTER_TAP = "com.example.mp3retrowidget.ACTION_CENTER_TAP"
        const val ACTION_PLAY_PAUSE = "com.example.mp3retrowidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mp3retrowidget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.mp3retrowidget.ACTION_PREVIOUS"
        const val ACTION_SHUFFLE = "com.example.mp3retrowidget.ACTION_SHUFFLE"
        const val ACTION_REFRESH = "com.example.mp3retrowidget.ACTION_REFRESH"

        /** Double tap window on the centre key. */
        private const val DOUBLE_TAP_MS = 400L

        private val handler = Handler(Looper.getMainLooper())
        private var pendingCentreTap: Runnable? = null
        private var lastCentreTap = 0L

        /**
         * Everything that changes from one skin to another. The layout is a
         * single file; only drawables, colours and a few visibilities move.
         */
        private class Skin(
            val body: Int,
            val pad: Int,
            val bezel: Int,
            val lcd: Int,
            val centreButton: Int,
            val sideButton: Int,
            val tab: Int,
            val accent: Int,
            val grille: Boolean,
            val pixels: Boolean,
            val hinge: Boolean,
            val groove: Boolean,
            val titleColor: Int,
            val timeColor: Int,
            val noteColor: Int,
            val iconColor: Int
        )

        private val skins = arrayOf(
            // 0 - ORANGE: exact vertical MP3 artwork supplied by the user.
            //     Its own dedicated layout is used (renderOrange), this entry
            //     only keeps the legacy table indexes stable.
            Skin(
                R.drawable.body_oval, R.drawable.pad_silver,
                R.drawable.bezel_silver, R.drawable.lcd_oval,
                R.drawable.btn_silver_center, R.drawable.btn_silver_side,
                R.drawable.decor_tab_silver, 0,
                grille = false, pixels = false, hinge = false, groove = false,
                titleColor = 0xFFE9F1F3.toInt(), timeColor = 0xFFDFE9EA.toInt(),
                noteColor = 0xFFE6EEF0.toInt(), iconColor = 0xFF3A3F42.toInt()
            ),

            // 1 - BRICK: blue-grey, single pixel-green screen on the left
            Skin(
                R.drawable.body_brick, R.drawable.pad_steel,
                R.drawable.bezel_dark, R.drawable.lcd_brick,
                R.drawable.btn_steel_center, R.drawable.btn_steel_side,
                0, 0,
                grille = true, pixels = true, hinge = false, groove = false,
                titleColor = 0xFF8CFF9E.toInt(), timeColor = 0xFF6FE58A.toInt(),
                noteColor = 0xFF4CFA7A.toInt(), iconColor = 0xFFE8F2F7.toInt()
            ),

            // 2 - SWIVEL WHITE: white + orange, black screen with orange text
            Skin(
                R.drawable.body_swivel_white, R.drawable.pad_white,
                R.drawable.bezel_dark, R.drawable.lcd_swivel_white,
                R.drawable.btn_cream_center, R.drawable.btn_cream_side,
                R.drawable.decor_tab_orange, R.drawable.accent_orange,
                grille = false, pixels = false, hinge = false, groove = false,
                titleColor = 0xFFFF8A00.toInt(), timeColor = 0xFFFF6F00.toInt(),
                noteColor = 0xFFFF8A00.toInt(), iconColor = 0xFFE2570F.toInt()
            ),

            // 3 - BLACK 2008: matte black, blue LCD
            Skin(
                R.drawable.body_black2008, R.drawable.pad_black,
                R.drawable.bezel_dark, R.drawable.lcd_black2008,
                R.drawable.btn_dark_center, R.drawable.btn_dark_side,
                0, 0,
                grille = false, pixels = false, hinge = false, groove = false,
                titleColor = 0xFF9FD8FF.toInt(), timeColor = 0xFF7FC8FF.toInt(),
                noteColor = 0xFF59B8FF.toInt(), iconColor = 0xFF9FD8FF.toInt()
            ),

            // 4 - SWIVEL NARANJA: grey + red-orange, no top tab, no logos
            Skin(
                R.drawable.body_swivel_orange, R.drawable.pad_orange,
                R.drawable.bezel_dark, R.drawable.lcd_swivel_orange,
                R.drawable.btn_orange_center, R.drawable.btn_orange_side,
                0, R.drawable.accent_red,
                grille = false, pixels = false, hinge = false, groove = false,
                titleColor = 0xFFFF5A36.toInt(), timeColor = 0xFFE04A2A.toInt(),
                noteColor = 0xFFFF5A36.toInt(), iconColor = 0xFFFFF2EA.toInt()
            ),

            // 5 - SLIDER: glossy black, fixed (it does not slide)
            Skin(
                R.drawable.body_slider, R.drawable.pad_black,
                R.drawable.bezel_dark, R.drawable.lcd_slider,
                R.drawable.btn_dark_center, R.drawable.btn_dark_side,
                0, 0,
                grille = false, pixels = false, hinge = false, groove = true,
                titleColor = 0xFFEDEDED.toInt(), timeColor = 0xFFCFCFCF.toInt(),
                noteColor = 0xFFFFFFFF.toInt(), iconColor = 0xFFF2F4F7.toInt()
            ),

            // 6 - FLIP: silver lid + black keypad, open
            Skin(
                R.drawable.body_flip, R.drawable.pad_black,
                R.drawable.bezel_silver, R.drawable.lcd_flip,
                R.drawable.btn_silver_center, R.drawable.btn_silver_side,
                0, 0,
                grille = false, pixels = false, hinge = true, groove = false,
                titleColor = 0xFF9BFFC4.toInt(), timeColor = 0xFF7BE3A6.toInt(),
                noteColor = 0xFF6BFFA8.toInt(), iconColor = 0xFF22262B.toInt()
            )
        )

        /** Repaints every instance of this widget. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MP3WidgetProvider::class.java)
            )
            for (id in ids) {
                render(context, manager, id)
            }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val controller = MediaController.getInstance(context)
            val skinIndex = WidgetPrefs.getSkin(context, appWidgetId)

            // The ORANGE skin is the user's exact artwork: dedicated layout.
            if (skinIndex == WidgetPrefs.SKIN_ORANGE) {
                renderOrange(context, manager, appWidgetId, controller)
                return
            }

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val skin = skins[skinIndex.coerceIn(0, skins.size - 1)]

            // ---- body -----------------------------------------------------
            views.setInt(R.id.widget_root, "setBackgroundResource", skin.body)
            views.setInt(R.id.control_area, "setBackgroundResource", skin.pad)

            val photo = if (WidgetPrefs.isPhotoEnabled(context)) {
                PhotoProcessor.render(context, skinIndex, widgetWidthDp(manager, appWidgetId))
            } else {
                null
            }
            if (photo != null) {
                views.setImageViewBitmap(R.id.widget_photo, photo)
                views.setViewVisibility(R.id.widget_photo, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_photo, View.GONE)
            }

            // ---- skin decorations ----------------------------------------
            if (skin.tab != 0) {
                views.setInt(R.id.top_decor, "setBackgroundResource", skin.tab)
                views.setViewVisibility(R.id.top_decor, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.top_decor, View.GONE)
            }

            if (skin.accent != 0) {
                views.setInt(R.id.accent_left, "setBackgroundResource", skin.accent)
                views.setInt(R.id.accent_right, "setBackgroundResource", skin.accent)
                views.setViewVisibility(R.id.accent_left, View.VISIBLE)
                views.setViewVisibility(R.id.accent_right, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.accent_left, View.GONE)
                views.setViewVisibility(R.id.accent_right, View.GONE)
            }

            views.setViewVisibility(
                R.id.grille,
                if (skin.grille) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.screen_pixels,
                if (skin.pixels) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.hinge,
                if (skin.hinge) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.slider_groove,
                if (skin.groove) View.VISIBLE else View.GONE
            )

            // ---- screen ---------------------------------------------------
            views.setInt(R.id.screen_bezel, "setBackgroundResource", skin.bezel)
            views.setInt(R.id.screen_inner, "setBackgroundResource", skin.lcd)

            val title = controller.title
            val playing = controller.isPlaying
            val hint = controller.transientHint
            val hasTrack = !title.isNullOrEmpty()

            views.setTextViewText(
                R.id.txt_title,
                when {
                    hint != null -> hint
                    hasTrack -> title
                    else -> context.getString(R.string.lcd_no_disc)
                }
            )
            views.setTextColor(R.id.txt_title, skin.titleColor)
            views.setTextColor(R.id.chrono, skin.timeColor)
            views.setTextColor(R.id.txt_time_suffix, skin.timeColor)
            views.setInt(R.id.ic_note, "setColorFilter", skin.noteColor)
            views.setInt(
                R.id.ic_note,
                "setImageAlpha",
                if (hasTrack && playing) 255 else 140
            )

            val duration = controller.durationMs
            val position = controller.positionMs
            val progress = if (duration > 0L) {
                (position * 1000L / duration).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            views.setProgressBar(R.id.progress, 1000, progress, false)

            if (duration > 0L && hasTrack) {
                views.setViewVisibility(R.id.chrono, View.VISIBLE)
                views.setViewVisibility(R.id.txt_time_suffix, View.VISIBLE)
                views.setChronometer(
                    R.id.chrono,
                    SystemClock.elapsedRealtime() - position,
                    "%s",
                    playing
                )
                views.setTextViewText(
                    R.id.txt_time_suffix,
                    " / " + formatTime(duration)
                )
            } else if (hasTrack) {
                views.setViewVisibility(R.id.chrono, View.VISIBLE)
                views.setViewVisibility(R.id.txt_time_suffix, View.VISIBLE)
                views.setChronometer(
                    R.id.chrono,
                    SystemClock.elapsedRealtime() - position,
                    "%s",
                    playing
                )
                views.setTextViewText(R.id.txt_time_suffix, " / --:--")
            } else {
                views.setViewVisibility(R.id.chrono, View.GONE)
                views.setViewVisibility(R.id.txt_time_suffix, View.GONE)
            }

            // ---- keys -----------------------------------------------------
            views.setInt(R.id.btn_play_container, "setBackgroundResource", skin.centreButton)
            views.setInt(R.id.btn_prev, "setBackgroundResource", skin.sideButton)
            views.setInt(R.id.btn_next, "setBackgroundResource", skin.sideButton)
            views.setInt(R.id.btn_play, "setColorFilter", skin.iconColor)
            views.setInt(R.id.ic_prev, "setColorFilter", skin.iconColor)
            views.setInt(R.id.ic_next, "setColorFilter", skin.iconColor)
            views.setImageViewResource(
                R.id.btn_play,
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )

            views.setOnClickPendingIntent(
                R.id.btn_play_container,
                broadcast(context, appWidgetId, ACTION_CENTER_TAP, 1)
            )
            views.setOnClickPendingIntent(
                R.id.btn_prev,
                broadcast(context, appWidgetId, ACTION_PREVIOUS, 2)
            )
            views.setOnClickPendingIntent(
                R.id.btn_next,
                broadcast(context, appWidgetId, ACTION_NEXT, 3)
            )

            // Tapping the LCD opens the music app that owns the session.
            views.setOnClickPendingIntent(
                R.id.screen_unit,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 4,
                    Intent(context, OpenMusicActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        /**
         * ORANGE skin. Uses the exact photo artwork of the device and paints
         * only the dynamic LCD contents on top; the keys stay part of the
         * artwork and are covered by transparent hitboxes.
         */
        private fun renderOrange(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            controller: MediaController
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_orange_layout)

            val photo = if (WidgetPrefs.isPhotoEnabled(context)) {
                PhotoProcessor.render(context, WidgetPrefs.SKIN_ORANGE, widgetWidthDp(manager, appWidgetId))
            } else {
                null
            }
            if (photo != null) {
                views.setImageViewBitmap(R.id.orange_photo, photo)
                views.setViewVisibility(R.id.orange_photo, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.orange_photo, View.GONE)
            }

            val title = controller.title
            val playing = controller.isPlaying
            val hint = controller.transientHint
            val hasTrack = !title.isNullOrEmpty()

            views.setTextViewText(
                R.id.orange_title,
                when {
                    hint != null -> hint
                    hasTrack -> title
                    else -> context.getString(R.string.lcd_no_disc)
                }
            )
            views.setInt(
                R.id.orange_note,
                "setImageAlpha",
                when {
                    hasTrack && playing -> 255
                    hasTrack -> 165
                    else -> 120
                }
            )

            val duration = controller.durationMs
            val position = controller.positionMs
            val progress = if (duration > 0L) {
                (position * 1000L / duration).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            views.setProgressBar(R.id.orange_progress, 1000, progress, false)

            if (hasTrack) {
                views.setViewVisibility(R.id.orange_chrono, View.VISIBLE)
                views.setViewVisibility(R.id.orange_total, View.VISIBLE)
                views.setChronometer(
                    R.id.orange_chrono,
                    SystemClock.elapsedRealtime() - position,
                    "%s",
                    playing
                )
                views.setTextViewText(
                    R.id.orange_total,
                    if (duration > 0L) formatTime(duration) else "--:--"
                )
            } else {
                views.setViewVisibility(R.id.orange_chrono, View.GONE)
                views.setViewVisibility(R.id.orange_total, View.GONE)
            }

            // Keys: single tap centre = play/pause, double tap = shuffle.
            views.setOnClickPendingIntent(
                R.id.orange_play,
                broadcast(context, appWidgetId, ACTION_CENTER_TAP, 1)
            )
            views.setOnClickPendingIntent(
                R.id.orange_prev,
                broadcast(context, appWidgetId, ACTION_PREVIOUS, 2)
            )
            views.setOnClickPendingIntent(
                R.id.orange_next,
                broadcast(context, appWidgetId, ACTION_NEXT, 3)
            )

            // Tapping the screen opens the music app that owns the session.
            views.setOnClickPendingIntent(
                R.id.orange_lcd,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 4,
                    Intent(context, OpenMusicActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        /** Widget width in dp, used to keep the photo corners proportional. */
        private fun widgetWidthDp(manager: AppWidgetManager, appWidgetId: Int): Int {
            val options: Bundle = try {
                manager.getAppWidgetOptions(appWidgetId)
            } catch (e: Exception) {
                return 110
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val width = if (minWidth > 0) minWidth else maxWidth
            return width.coerceIn(80, 320)
        }

        private fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

        /**
         * Centre key: single tap = play/pause after a short delay, so a second
         * tap inside the window can be understood as "shuffle".
         */
        private fun handleCentreTap(context: Context) {
            val app = context.applicationContext
            val now = SystemClock.elapsedRealtime()
            val pending = pendingCentreTap

            if (pending != null && now - lastCentreTap <= DOUBLE_TAP_MS) {
                handler.removeCallbacks(pending)
                pendingCentreTap = null
                lastCentreTap = 0L
                MediaController.getInstance(app).toggleShuffle()
                updateAllWidgets(app)
                return
            }

            lastCentreTap = now
            val runnable = Runnable {
                pendingCentreTap = null
                MediaController.getInstance(app).playPause()
                updateAllWidgets(app)
            }
            pendingCentreTap = runnable
            handler.postDelayed(runnable, DOUBLE_TAP_MS)
        }

        private fun broadcast(
            context: Context,
            appWidgetId: Int,
            action: String,
            slot: Int
        ): PendingIntent {
            val intent = Intent(context, MP3WidgetProvider::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        MediaController.getInstance(context).refresh()
        for (appWidgetId in appWidgetIds) {
            render(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val controller = MediaController.getInstance(context)
        controller.refresh()
        when (intent.action) {
            ACTION_CENTER_TAP -> handleCentreTap(context)

            ACTION_PLAY_PAUSE -> {
                controller.playPause()
                updateAllWidgets(context)
            }

            ACTION_NEXT -> {
                controller.next()
                updateAllWidgets(context)
            }

            ACTION_PREVIOUS -> {
                controller.previous()
                updateAllWidgets(context)
            }

            ACTION_SHUFFLE -> {
                controller.toggleShuffle()
                updateAllWidgets(context)
            }

            // Repaint after an app update or when asked with adb
            Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_REFRESH -> updateAllWidgets(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetPrefs.removeSkin(context, appWidgetId)
        }
        super.onDeleted(context, appWidgetIds)
    }
}
