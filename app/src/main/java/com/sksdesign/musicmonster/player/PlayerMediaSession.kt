package com.sksdesign.musicmonster.player

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sksdesign.musicmonster.data.Track

class PlayerMediaSession(context: Context, private val playerController: PlayerController) {
    val session: MediaSessionCompat = MediaSessionCompat(context, "MusicMonsterSession").apply {
        isActive = true
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = playerController.play()
            override fun onPause() = playerController.pause()
            override fun onSkipToNext() = playerController.next()
            override fun onSkipToPrevious() = playerController.previous()
            override fun onSeekTo(pos: Long) = playerController.seekTo(pos)
        })
    }

    fun update(track: Track?, isPlaying: Boolean, positionMs: Long = 0L) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, if (isPlaying) 1f else 0f)
                .build()
        )

        if (track != null) {
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                    .build()
            )
        }
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
