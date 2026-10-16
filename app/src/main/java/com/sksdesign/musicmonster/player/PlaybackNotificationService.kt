package com.sksdesign.musicmonster.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackNotificationService : Service() {
    companion object {
        private const val ACTION_SYNC = "com.sksdesign.musicmonster.action.NOTIFICATION_SYNC"
        private const val ACTION_STOP = "com.sksdesign.musicmonster.action.NOTIFICATION_STOP"

        @Volatile
        private var lastRequestedState: Boolean? = null
        @Volatile
        private var lastRequestAt: Long = 0L

        fun sync(context: Context, hasTrack: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (lastRequestedState == hasTrack && now - lastRequestAt < 5000L) return
            lastRequestedState = hasTrack
            lastRequestAt = now

            val appContext = context.applicationContext
            val intent = Intent(appContext, PlaybackNotificationService::class.java).setAction(if (hasTrack) ACTION_SYNC else ACTION_STOP)
            if (hasTrack) {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
                    .onFailure { runCatching { appContext.startService(intent) } }
            } else {
                runCatching { appContext.startService(intent) }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlayerController
    private lateinit var mediaSession: PlayerMediaSession
    private lateinit var notificationController: PlayerNotificationController
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        controller = MusicMonsterRuntime.getOrCreateController(applicationContext)
        SleepTimerManager.restore(applicationContext)
        mediaSession = PlayerMediaSession.getOrCreate(applicationContext, controller)
        notificationController = PlayerNotificationController(applicationContext, mediaSession.session.sessionToken)
        scope.launch {
            combine(controller.currentTrack, controller.isPlaying, controller.positionMs, controller.durationMs) { track, playing, position, duration ->
                NotificationSnapshot(track, playing, position, duration)
            }.collect { snapshot ->
                updateNotification(snapshot)
            }
        }
        scope.launch {
            while (isActive) {
                SleepTimerManager.poll(applicationContext, controller)
                val track = controller.currentTrack.value
                if (track != null && controller.isPlaying.value) {
                    updateNotification(
                        NotificationSnapshot(
                            track,
                            true,
                            controller.positionMs.value,
                            controller.durationMs.value
                        )
                    )
                }
                delay(1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlaybackNotification()
            return START_NOT_STICKY
        }
        updateNotification(
            NotificationSnapshot(
                controller.currentTrack.value,
                controller.isPlaying.value,
                controller.positionMs.value,
                controller.durationMs.value
            )
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(snapshot: NotificationSnapshot) {
        val track = snapshot.track
        if (track == null) {
            stopPlaybackNotification()
            return
        }
        mediaSession.update(track, snapshot.playing, snapshot.position, snapshot.duration)
        val notification = notificationController.buildNotification(track, snapshot.playing, snapshot.position, snapshot.duration)
        if (!foregroundStarted) {
            startForeground(PlayerNotificationController.NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            notificationController.update(track, snapshot.playing, snapshot.position, snapshot.duration)
        }
    }

    private fun stopPlaybackNotification() {
        notificationController.cancel()
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private data class NotificationSnapshot(
    val track: com.sksdesign.musicmonster.data.Track?,
    val playing: Boolean,
    val position: Long,
    val duration: Long
)
