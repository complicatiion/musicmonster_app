package com.sksdesign.musicmonster.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.data.Track

class PlayerMediaSession private constructor(
    private val context: Context,
    private val playerController: PlayerController
) {
    companion object {
        private var shared: PlayerMediaSession? = null

        fun getOrCreate(context: Context, playerController: PlayerController): PlayerMediaSession {
            return shared ?: synchronized(this) {
                shared ?: PlayerMediaSession(context.applicationContext, playerController).also { shared = it }
            }
        }
    }

    private var cachedArtworkKey: String = ""
    private var cachedArtworkBitmap: Bitmap? = null
    private var lastQueueHash: Int = 0

    val session: MediaSessionCompat = MediaSessionCompat(context, "MusicMonsterSession").apply {
        setSessionActivity(openActivityIntent())
        @Suppress("DEPRECATION")
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = playerController.play()
            override fun onPause() = playerController.pause()
            override fun onSkipToNext() = playerController.next()
            override fun onSkipToPrevious() = playerController.previous()
            override fun onSeekTo(pos: Long) {
                playerController.seekTo(pos)
                publishCurrentState()
            }
            override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
                val id = mediaId?.removePrefix("track:")?.toLongOrNull() ?: return
                val queue = playerController.queue.value
                val index = queue.indexOfFirst { it.id == id }
                if (index >= 0) playerController.playQueue(queue, index)
            }
        })
        setQueueTitle("MusicMonster Queue")
        isActive = true
    }

    fun update(track: Track?, isPlaying: Boolean, positionMs: Long = 0L, durationMs: Long = 0L) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        val state = when {
            track == null -> PlaybackStateCompat.STATE_NONE
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setActiveQueueItemId(track?.id ?: -1L)
                .setState(state, positionMs.coerceAtLeast(0L), if (isPlaying) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )

        updateQueue(playerController.queue.value)
        if (track != null) updateMetadata(track, positionMs, durationMs)
    }

    private fun publishCurrentState() {
        update(
            playerController.currentTrack.value,
            playerController.isPlaying.value,
            playerController.positionMs.value,
            playerController.durationMs.value
        )
    }

    fun updateQueue(queue: List<Track>) {
        val queueSignature = queue.fold(17) { acc: Int, item: Track -> 31 * acc + item.id.hashCode() }
        if (queueSignature == lastQueueHash) return
        lastQueueHash = queueSignature
        val items = queue.mapIndexed { index: Int, track: Track ->
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("track:${track.id}")
                    .setTitle(track.title.ifBlank { "Unknown title" })
                    .setSubtitle(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
                    .setIconUri(track.artworkUri)
                    .setMediaUri(track.uri)
                    .build(),
                track.id.takeIf { it != 0L } ?: index.toLong()
            )
        }
        session.setQueue(items)
    }

    private fun updateMetadata(track: Track, positionMs: Long, durationMs: Long) {
        val safeDuration = durationMs.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "track:${track.id}")
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title.ifBlank { "Unknown title" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title.ifBlank { "Unknown title" })
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, safeDuration)

        track.artworkUri?.toString()?.let { art: String ->
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, art)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, art)
            loadArtworkBitmap(track.artworkUri)?.let { bitmap: Bitmap ->
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            }
        }
        session.setMetadata(builder.build())
    }

    fun setSessionActivity(androidAutoMode: Boolean = false, compact: Boolean = false, request: Int = 0) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_ANDROID_AUTO_MODE, androidAutoMode)
            putExtra(MainActivity.EXTRA_ANDROID_AUTO_COMPACT, compact)
        }
        session.setSessionActivity(PendingIntent.getActivity(context, request, intent, pendingIntentFlags()))
    }

    private fun openActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags())
    }

    private fun loadArtworkBitmap(uri: Uri?): Bitmap? {
        val key = uri?.toString().orEmpty()
        if (key.isBlank()) return null
        if (key == cachedArtworkKey && cachedArtworkBitmap != null) return cachedArtworkBitmap
        val loaded = runCatching {
            context.contentResolver.openInputStream(uri!!)?.use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
        cachedArtworkKey = key
        cachedArtworkBitmap = loaded
        return loaded
    }

    fun release() {
        // Shared process-wide session: keep it active for Android Auto and media button clients.
        session.isActive = true
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
