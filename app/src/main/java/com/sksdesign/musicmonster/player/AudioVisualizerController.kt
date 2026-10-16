package com.sksdesign.musicmonster.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import com.sksdesign.musicmonster.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

class AudioVisualizerController(
    private val context: Context,
    private val audioSessionId: StateFlow<Int>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _bars = MutableStateFlow(List(DEFAULT_BAR_COUNT) { 0f })
    private val _waveform = MutableStateFlow(List(DEFAULT_BAR_COUNT) { 0f })
    private val _status = MutableStateFlow(VisualizerStatus.Idle)

    private var visualizer: Visualizer? = null
    private var active = false
    private var settings = AppSettings()
    private var attachedSessionId = -1
    private var smoothedBars = FloatArray(DEFAULT_BAR_COUNT)
    private var smoothedWave = FloatArray(DEFAULT_BAR_COUNT)

    val bars: StateFlow<List<Float>> = _bars
    val waveform: StateFlow<List<Float>> = _waveform
    val status: StateFlow<VisualizerStatus> = _status

    init {
        scope.launch {
            audioSessionId.collect { sessionId ->
                if (active && sessionId != attachedSessionId) attach(sessionId)
            }
        }
    }

    fun updateSettings(next: AppSettings) {
        settings = next
        val count = next.visualizerBarCount.coerceIn(12, 64)
        if (smoothedBars.size != count) {
            smoothedBars = FloatArray(count)
            smoothedWave = FloatArray(count)
            _bars.value = List(count) { 0f }
            _waveform.value = List(count) { 0f }
        }
    }

    fun setActive(enabled: Boolean) {
        active = enabled
        if (!enabled) {
            detach(VisualizerStatus.Idle)
        } else {
            attach(audioSessionId.value)
        }
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun attach(sessionId: Int) {
        detach(VisualizerStatus.Idle)
        if (!active) return
        if (!settings.visualizerEnabled) {
            _status.value = VisualizerStatus.Disabled
            return
        }
        if (!hasAudioPermission()) {
            _status.value = VisualizerStatus.PermissionRequired
            return
        }
        if (sessionId <= 0) {
            _status.value = VisualizerStatus.NoAudioSession
            return
        }
        attachedSessionId = sessionId
        try {
            val next = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            next.captureSize = range.last().coerceAtMost(1024)
            next.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    waveform?.let { publishWaveform(it) }
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { publishFft(it) }
                }
            }, Visualizer.getMaxCaptureRate() / 3, true, true)
            next.enabled = true
            visualizer = next
            _status.value = VisualizerStatus.Active
        } catch (_: Throwable) {
            attachedSessionId = -1
            _status.value = VisualizerStatus.Unavailable
        }
    }

    private fun detach(status: VisualizerStatus) {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        attachedSessionId = -1
        _status.value = status
    }

    private fun publishFft(fft: ByteArray) {
        val count = smoothedBars.size
        if (fft.size < 4 || count == 0) return
        val intensity = settings.visualizerIntensity.coerceIn(0f, 250f) / 100f
        val smoothing = settings.visualizerSmoothing.coerceIn(0f, 95f) / 100f
        val usableBins = (fft.size / 2).coerceAtLeast(2)
        for (i in 0 until count) {
            val start = 1 + (i * usableBins / count)
            val end = min(usableBins - 1, 1 + ((i + 1) * usableBins / count)).coerceAtLeast(start)
            var sum = 0f
            var samples = 0
            for (bin in start..end) {
                val re = fft.getOrNull(bin * 2)?.toFloat() ?: 0f
                val im = fft.getOrNull(bin * 2 + 1)?.toFloat() ?: 0f
                sum += hypot(re, im)
                samples++
            }
            val magnitude = sum / samples.coerceAtLeast(1)
            val raw = (ln(1f + magnitude) / ln(128f) * intensity * 2.55f).coerceIn(0f, 1f)
            smoothedBars[i] = (smoothedBars[i] * smoothing + raw * (1f - smoothing)).coerceIn(0f, 1f)
        }
        _bars.value = smoothedBars.toList()
    }

    private fun publishWaveform(bytes: ByteArray) {
        val count = smoothedWave.size
        if (bytes.isEmpty() || count == 0) return
        val intensity = settings.visualizerIntensity.coerceIn(0f, 250f) / 100f
        val smoothing = settings.visualizerSmoothing.coerceIn(0f, 95f) / 100f
        for (i in 0 until count) {
            val index = (i * bytes.size / count).coerceIn(0, bytes.lastIndex)
            val centered = (bytes[index].toInt() and 0xFF) - 128
            val raw = ((centered / 128f) * intensity * 2.15f).coerceIn(-1f, 1f)
            smoothedWave[i] = (smoothedWave[i] * smoothing + raw * (1f - smoothing)).coerceIn(-1f, 1f)
        }
        _waveform.value = smoothedWave.toList()
    }

    fun release() {
        detach(VisualizerStatus.Idle)
        scope.cancel()
    }

    companion object {
        private const val DEFAULT_BAR_COUNT = 32
    }
}

enum class VisualizerStatus {
    Idle,
    Active,
    Disabled,
    PermissionRequired,
    NoAudioSession,
    Unavailable
}
