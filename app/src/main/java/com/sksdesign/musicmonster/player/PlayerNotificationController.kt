package com.sksdesign.musicmonster.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.data.Track

class PlayerNotificationController(private val context: Context) {
    companion object {
        const val ACTION_PREVIOUS = "com.sksdesign.musicmonster.action.PREVIOUS"
        const val ACTION_TOGGLE = "com.sksdesign.musicmonster.action.TOGGLE"
        const val ACTION_NEXT = "com.sksdesign.musicmonster.action.NEXT"
        private const val CHANNEL_ID = "musicmonster_playback"
        private const val NOTIFICATION_ID = 2601
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MusicMonster playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quick controls for the active MusicMonster player"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun update(track: Track?, isPlaying: Boolean) {
        if (track == null) {
            cancel()
            return
        }
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingIntentFlags()
        )
        val previous = PendingIntent.getBroadcast(context, 1, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_PREVIOUS), pendingIntentFlags())
        val toggle = PendingIntent.getBroadcast(context, 2, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_TOGGLE), pendingIntentFlags())
        val next = PendingIntent.getBroadcast(context, 3, Intent(context, PlayerActionReceiver::class.java).setAction(ACTION_NEXT), pendingIntentFlags())
        val largeIcon = decodeArtwork(track.artworkUri)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_mark)
            .setContentTitle(track.title)
            .setContentText(listOf(track.album, track.folder).firstOrNull { it.isNotBlank() } ?: track.artist)
            .setSubText(track.artist)
            .setLargeIcon(largeIcon)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Previous", previous)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow, if (isPlaying) "Pause" else "Play", toggle)
            .addAction(R.drawable.ic_skip_next, "Next", next)
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()

        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun decodeArtwork(uri: Uri?) = runCatching {
        if (uri == null) null else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
