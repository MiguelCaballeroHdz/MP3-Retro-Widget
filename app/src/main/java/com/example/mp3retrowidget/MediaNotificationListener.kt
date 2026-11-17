package com.example.mp3retrowidget

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Bound system service used only as a callback bridge:
 *  - when a notification is posted/removed we refresh the active media session
 *    (no polling anywhere),
 *  - it also gives us access to the media notification actions, which is the
 *    only way to trigger "shuffle" on players that publish it.
 *
 * This is NOT a playback service and it never runs in the foreground.
 */
class MediaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        private var connected: MediaNotificationListener? = null

        /** True when the user granted "Notification access" to this app. */
        fun hasAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val target = ComponentName(context, MediaNotificationListener::class.java)
            return flat.split(":").any { entry ->
                ComponentName.unflattenFromString(entry) == target
            }
        }

        /** Clicks the shuffle button of the media notification, if it exists. */
        fun triggerShuffleAction(): Boolean {
            val service = connected ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

            val notifications = try {
                service.activeNotifications
            } catch (e: Exception) {
                return false
            } ?: return false

            for (sbn in notifications) {
                val actions = sbn.notification.actions ?: continue
                for (action in actions) {
                    val label = action.title?.toString()?.lowercase() ?: continue
                    val isShuffle = label.contains("shuffle") ||
                        label.contains("aleator") ||
                        label.contains("mezclar") ||
                        label.contains("random")
                    if (isShuffle) {
                        try {
                            action.actionIntent.send()
                            return true
                        } catch (e: Exception) {
                            // try the next action
                        }
                    }
                }
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
        MediaController.getInstance(this).refresh()
        MP3WidgetProvider.updateAllWidgets(this)
    }

    override fun onListenerDisconnected() {
        connected = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        MediaController.getInstance(this).refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        MediaController.getInstance(this).refresh()
    }
}
