package com.sksdesign.musicmonster.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.data.Track
import kotlin.math.roundToInt

class PlayerNotificationController(
    private val context: Context,
    initialMediaSessionToken: MediaSessionCompat.Token? = null
) {
    companion object {
        const val ACTION_PREVIOUS = "com.sksdesign.musicmonster.action.PREVIOUS"
        const val ACTION_TOGGLE = "com.sksdesign.musicmonster.action.TOGGLE"
        const val ACTION_NEXT = "com.sksdesign.musicmonster.action.NEXT"
        const val CHANNEL_ID = "musicmonster_playback_controls"
        const val NOTIFICATION_ID = 2601
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private var mediaSessionToken: MediaSessionCompat.Token? = initialMediaSessionToken
    private var lastStableKey: String = ""
    private var lastNotifyAt: Long = 0L
    private var cachedIconKey: String = ""
    private var cachedIcon: Bitmap? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MusicMonster player controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent playback controls for the active MusicMonster player"
                setShowBadge(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun setMediaSession(token: MediaSessionCompat.Token) {
        mediaSessionToken = token
    }

    fun buildNotification(track: Track, isPlaying: Boolean, positionMs: Long = 0L, durationMs: Long = 0L): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingIntentFlags()
        )
        val previous = PendingIntent.getBroadcast(context, 1, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_PREVIOUS), pendingIntentFlags())
        val toggle = PendingIntent.getBroadcast(context, 2, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_TOGGLE), pendingIntentFlags())
        val next = PendingIntent.getBroadcast(context, 3, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_NEXT), pendingIntentFlags())
        val safeDuration = durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
        val safePosition = positionMs.coerceIn(0L, safeDuration.coerceAtLeast(1L))
        val artwork = loadLargeIcon(track)
        val mediaStyle = MediaStyle().setShowActionsInCompactView(0, 1, 2)
        mediaSessionToken?.let { token -> mediaStyle.setMediaSession(token) }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_mark)
            .setContentTitle(track.title)
            .setContentText(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
            .setSubText(track.album.ifBlank { track.folder })
            .setLargeIcon(artwork)
            .setColor(ContextCompat.getColor(context, R.color.musicmonster_accent))
            .setColorized(false)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(
                safeDuration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                safePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                false
            )
            .addAction(R.drawable.ic_skip_previous, "Previous", previous)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow, if (isPlaying) "Pause" else "Play", toggle)
            .addAction(R.drawable.ic_skip_next, "Next", next)
            .setStyle(mediaStyle)
            .setCustomBigContentView(bigRemoteViews(track, isPlaying, safePosition, safeDuration, artwork, previous, toggle, next))
            .build()
    }

    private fun bigRemoteViews(
        track: Track,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        artwork: Bitmap?,
        previous: PendingIntent,
        toggle: PendingIntent,
        next: PendingIntent
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_player_big).apply {
            setTextViewText(R.id.notification_title, track.title)
            setTextViewText(R.id.notification_subtitle, listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
            setTextViewText(R.id.notification_position, formatDuration(positionMs))
            setTextViewText(R.id.notification_duration, formatDuration(durationMs))
            setImageViewResource(R.id.notification_toggle, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            artwork?.let { setImageViewBitmap(R.id.notification_artwork, it) } ?: setImageViewResource(R.id.notification_artwork, R.drawable.logo)
            val progress = ((positionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()) * 1000f).roundToInt().coerceIn(0, 1000)
            setProgressBar(R.id.notification_progress, 1000, progress, false)
            setOnClickPendingIntent(R.id.notification_previous, previous)
            setOnClickPendingIntent(R.id.notification_toggle, toggle)
            setOnClickPendingIntent(R.id.notification_next, next)
        }
    }

    private fun formatDuration(value: Long): String {
        val totalSeconds = (value / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    private fun loadLargeIcon(track: Track): Bitmap? {
        val key = track.artworkUri?.toString() ?: "app-logo"
        if (key == cachedIconKey && cachedIcon != null) return cachedIcon
        val loaded = track.artworkUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
            }.getOrNull()
        } ?: BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        cachedIconKey = key
        cachedIcon = loaded
        return loaded
    }

    fun update(track: Track?, isPlaying: Boolean, positionMs: Long = 0L, durationMs: Long = 0L) {
        if (track == null) {
            cancel()
            return
        }
        val safeDuration = durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
        val progressBucket = (positionMs.coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        val stableKey = listOf(track.id, isPlaying, safeDuration, progressBucket).joinToString(":")
        val now = SystemClock.elapsedRealtime()
        if (stableKey == lastStableKey && now - lastNotifyAt < 900L) return
        lastStableKey = stableKey
        lastNotifyAt = now
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(track, isPlaying, positionMs, durationMs)) }
    }

    fun cancel() {
        lastStableKey = ""
        lastNotifyAt = 0L
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
