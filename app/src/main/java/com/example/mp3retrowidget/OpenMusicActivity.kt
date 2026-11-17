package com.example.mp3retrowidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible activity used by the widget to open Spotify / YouTube Music.
 *
 * A widget cannot start an activity directly from a broadcast on Android 10+
 * (background activity start restriction), but a PendingIntent.getActivity sent
 * by the launcher can. This trampoline finishes immediately.
 */
class OpenMusicActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = MediaController.getInstance(this)
        controller.refresh()

        var launched = false
        val mediaPackage = controller.packageName
        if (mediaPackage != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(mediaPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(launchIntent)
                    launched = true
                } catch (e: Exception) {
                    launched = false
                }
            }
        }

        if (!launched) {
            val fallback = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(fallback)
            } catch (e: Exception) {
                // No music app installed: nothing to open.
            }
        }

        finish()
    }
}
