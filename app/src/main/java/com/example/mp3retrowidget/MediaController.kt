package com.example.mp3retrowidget

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.media.session.MediaController as SystemMediaController

/**
 * Reads and controls the media session that is currently active on the device
 * (Spotify, YouTube Music, Samsung Music, VLC...).
 *
 * Design rules (as required):
 *  - Callback based: state arrives through [SystemMediaController.Callback] and
 *    through [MediaNotificationListener]. There is NO polling loop.
 *  - No foreground service and no playback service. This class just holds a
 *    reference to another app's session.
 *  - If the user has not granted "notification access", every button still works
 *    because we fall back to dispatching media key events through AudioManager.
 */
class MediaController private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: MediaController? = null

        fun getInstance(context: Context): MediaController =
            instance ?: synchronized(this) {
                instance ?: MediaController(context.applicationContext).also { instance = it }
            }
    }

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var session: SystemMediaController? = null
    private var metadata: MediaMetadata? = null
    private var playbackState: PlaybackState? = null
    private var shuffleOn = false
    private var hint: String? = null

    private val sessionCallback = object : SystemMediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playbackState = state
            if (isPlaying) startTicker() else stopTicker()
            notifyWidgets()
        }

        override fun onMetadataChanged(md: MediaMetadata?) {
            metadata = md
            notifyWidgets()
        }

        override fun onSessionDestroyed() {
            detach()
            notifyWidgets()
        }
    }

    /** One-shot delayed refresh so the widget catches up right after a tap. */
    private val refreshRunnable = Runnable {
        refresh()
        notifyWidgets()
    }

    /** One-shot message on the LCD (used when an action is not possible). */
    private val clearHintRunnable = Runnable {
        hint = null
        notifyWidgets()
    }

    /**
     * Light 4 s repaint while something is playing, so the elapsed time and the
     * progress bar move. It is a single delayed Runnable, not a polling loop,
     * and it stops by itself as soon as the session pauses.
     */
    private var tickerRunning = false
    private val tickerRunnable = object : Runnable {
        override fun run() {
            tickerRunning = false
            if (isPlaying) {
                notifyWidgets()
                startTicker()
            }
        }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.postDelayed(tickerRunnable, 4000L)
    }

    private fun stopTicker() {
        tickerRunning = false
        handler.removeCallbacks(tickerRunnable)
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Binds to the currently active media session. Cheap, idempotent and safe to
     * call from any callback (widget update, notification posted, config screen).
     */
    fun refresh() {
        val manager =
            appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (manager == null) {
            detach()
            return
        }

        // Passing our NotificationListener component is the only public way for a
        // normal app to read another app's media session.
        val listener = ComponentName(appContext, MediaNotificationListener::class.java)
        val sessions: List<SystemMediaController>? = try {
            manager.getActiveSessions(listener)
        } catch (e: SecurityException) {
            null // notification access not granted yet
        }

        if (sessions.isNullOrEmpty()) {
            detach()
            return
        }

        val active = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.first()

        if (active.sessionToken == session?.sessionToken) {
            // Same session: just sync cached values, do not re-register.
            metadata = active.metadata ?: metadata
            playbackState = active.playbackState ?: playbackState
            if (isPlaying) startTicker() else stopTicker()
            return
        }

        detach()
        session = active
        try {
            active.registerCallback(sessionCallback, handler)
        } catch (e: Exception) {
            session = null
            return
        }
        metadata = active.metadata
        playbackState = active.playbackState
        if (isPlaying) startTicker() else stopTicker()
        notifyWidgets()
    }

    private fun detach() {
        stopTicker()
        session?.let {
            try {
                it.unregisterCallback(sessionCallback)
            } catch (e: Exception) {
                // session already gone
            }
        }
        session = null
        metadata = null
        playbackState = null
    }

    private fun notifyWidgets() {
        MP3WidgetProvider.updateAllWidgets(appContext)
    }

    private fun refreshSoon() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 500L)
    }

    // ------------------------------------------------------------------ control

    fun playPause() {
        val controller = session
        if (controller != null) {
            if (isPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        refreshSoon()
    }

    fun next() {
        val controller = session
        if (controller != null) controller.transportControls.skipToNext()
        else dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        refreshSoon()
    }

    fun previous() {
        val controller = session
        if (controller != null) controller.transportControls.skipToPrevious()
        else dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        refreshSoon()
    }

    /**
     * Shuffle is not part of the public transport controls, but many players
     * (Spotify, YouTube Music, Poweramp...) publish a custom session action for
     * it. We send that action; if the player has none we fall back to the
     * notification button and, if that does not exist either, we say so on the LCD.
     */
    fun toggleShuffle(): Boolean {
        val controller = session
        val custom = playbackState?.customActions?.firstOrNull { action ->
            val name = action.name?.toString()?.lowercase() ?: ""
            name.contains("shuffle") ||
                name.contains("aleatorio") ||
                name.contains("random") ||
                name.contains("mezclar")
        }

        if (controller != null && custom != null) {
            try {
                controller.transportControls.sendCustomAction(custom, null)
                Log.d("MP3Retro", "shuffle via custom action: ${custom.name}")
                showHint("ALEATORIO")
                refreshSoon()
                return true
            } catch (e: Exception) {
                Log.w("MP3Retro", "custom shuffle action failed", e)
            }
        }

        val fired = MediaNotificationListener.triggerShuffleAction()
        Log.d("MP3Retro", "shuffle via notification action: $fired")
        shuffleOn = if (fired) !shuffleOn else shuffleOn
        showHint(if (fired) "ALEATORIO" else "SHUFFLE N/D")
        refreshSoon()
        return fired
    }

    private fun showHint(text: String) {
        hint = text
        notifyWidgets()
        handler.removeCallbacks(clearHintRunnable)
        handler.postDelayed(clearHintRunnable, 2500L)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            // Nothing else we can do without media access.
        }
    }

    // ------------------------------------------------------------------- state

    val isPlaying: Boolean
        get() = playbackState?.state == PlaybackState.STATE_PLAYING

    val shuffleEnabled: Boolean
        get() = shuffleOn

    /** Transient LCD message, null when there is nothing to say. */
    val transientHint: String?
        get() = hint

    val title: String?
        get() = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

    val artist: String?
        get() = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

    /** Elapsed time of the current track in milliseconds. */
    val positionMs: Long
        get() = playbackState?.position?.coerceAtLeast(0L) ?: 0L

    /** Total length of the current track in milliseconds (0 when unknown). */
    val durationMs: Long
        get() = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    /** Package of the app that owns the session, so the widget can open it. */
    val packageName: String?
        get() = session?.packageName
}
