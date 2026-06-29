package com.sksdesign.musicmonster.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sksdesign.musicmonster.data.AppSettings
import com.sksdesign.musicmonster.data.RepeatModeUi
import com.sksdesign.musicmonster.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerController(context: Context) {
    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _currentTrack = MutableStateFlow<Track?>(null)
    private val _isPlaying = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _shuffle = MutableStateFlow(false)
    private val _repeatMode = MutableStateFlow(RepeatModeUi.Off)
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _queueIndex = MutableStateFlow(0)
    private var queueList: MutableList<Track> = mutableListOf()
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var effectsSessionId: Int = -1
    private var lastSettings: AppSettings = AppSettings()

    val currentTrack: StateFlow<Track?> = _currentTrack
    val isPlaying: StateFlow<Boolean> = _isPlaying
    val positionMs: StateFlow<Long> = _positionMs
    val durationMs: StateFlow<Long> = _durationMs
    val shuffle: StateFlow<Boolean> = _shuffle
    val repeatMode: StateFlow<RepeatModeUi> = _repeatMode
    val queue: StateFlow<List<Track>> = _queue
    val queueIndex: StateFlow<Int> = _queueIndex

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentTrack()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateProgress()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                configureAudioEffects(audioSessionId)
            }
        })
        scope.launch {
            while (isActive) {
                updateProgress()
                delay(500)
            }
        }
    }

    fun playQueue(items: List<Track>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        queueList = items.toMutableList()
        _queue.value = queueList.toList()
        val index = startIndex.coerceIn(queueList.indices)
        _queueIndex.value = index
        player.setMediaItems(queueList.map { MediaItem.fromUri(it.uri) }, index, 0L)
        player.prepare()
        player.play()
        updateCurrentTrack()
    }

    fun addToQueue(track: Track) {
        queueList.add(track)
        _queue.value = queueList.toList()
        player.addMediaItem(MediaItem.fromUri(track.uri))
        if (_currentTrack.value == null) playQueue(queueList, queueList.lastIndex)
    }

    fun removeQueueItem(index: Int) {
        if (index !in queueList.indices) return
        queueList.removeAt(index)
        _queue.value = queueList.toList()
        runCatching { player.removeMediaItem(index) }
        if (queueList.isEmpty()) {
            player.stop()
            _currentTrack.value = null
            _queueIndex.value = 0
        } else {
            updateCurrentTrack()
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from !in queueList.indices || to !in queueList.indices || from == to) return
        val item = queueList.removeAt(from)
        queueList.add(to, item)
        _queue.value = queueList.toList()
        runCatching { player.moveMediaItem(from, to) }
        updateCurrentTrack()
    }

    fun playQueueIndex(index: Int) {
        if (index !in queueList.indices) return
        player.seekTo(index, 0L)
        player.play()
        updateCurrentTrack()
    }

    fun play() { player.play() }

    fun pause() { player.pause() }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else if (player.repeatMode == Player.REPEAT_MODE_ALL && queueList.isNotEmpty()) player.seekTo(0, 0)
    }

    fun previous() {
        if (player.currentPosition > 3000) player.seekTo(0) else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration().coerceAtLeast(0L))
        seekTo(target)
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        _shuffle.value = player.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = when (nextMode) {
            Player.REPEAT_MODE_ALL -> RepeatModeUi.All
            Player.REPEAT_MODE_ONE -> RepeatModeUi.One
            else -> RepeatModeUi.Off
        }
    }


    fun applyAudioSettings(settings: AppSettings) {
        lastSettings = settings
        configureAudioEffects(player.audioSessionId)
    }

    private fun configureAudioEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (effectsSessionId != audioSessionId) {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            equalizer = null
            bassBoost = null
            effectsSessionId = audioSessionId
        }
        runCatching {
            val eq = equalizer ?: Equalizer(0, audioSessionId).also { equalizer = it }
            eq.enabled = lastSettings.equalizerEnabled
            val bands = parseEqBands(lastSettings.equalizerBands).toMutableList()
            bands[0] = (bands[0] + lastSettings.bassLevel).coerceIn(-10f, 10f)
            bands[4] = (bands[4] + lastSettings.trebleLevel).coerceIn(-10f, 10f)
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            val count = minOf(5, eq.numberOfBands.toInt())
            for (i in 0 until count) {
                val level = ((bands[i] / 10f) * 1000f).toInt().coerceIn(min, max).toShort()
                eq.setBandLevel(i.toShort(), level)
            }
        }
        runCatching {
            val boost = bassBoost ?: BassBoost(0, audioSessionId).also { bassBoost = it }
            boost.enabled = lastSettings.superBassBoost
            boost.setStrength((lastSettings.bassBoostLevel.coerceIn(0f, 1f) * 1000f).toInt().coerceIn(0, 1000).toShort())
        }
    }

    private fun parseEqBands(value: String): List<Float> {
        val parsed = value.split(',').mapNotNull { it.trim().toFloatOrNull() }.take(5)
        return (parsed + List(5) { 0f }).take(5)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        effectsSessionId = -1
        player.release()
    }

    private fun updateCurrentTrack() {
        val idx = player.currentMediaItemIndex
        if (idx in queueList.indices) {
            _queueIndex.value = idx
            _currentTrack.value = queueList[idx]
        }
        updateProgress()
    }

    private fun updateProgress() {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = duration().coerceAtLeast(0L)
    }

    private fun duration(): Long {
        val playerDuration = player.duration
        if (playerDuration > 0) return playerDuration
        return _currentTrack.value?.durationMs ?: 0L
    }
}
