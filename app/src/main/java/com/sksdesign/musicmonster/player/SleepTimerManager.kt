package com.sksdesign.musicmonster.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persisted end-time; evaluated by the playback foreground service and the active UI.
 * A timer pauses playback rather than force-stopping the app process.
 */
object SleepTimerManager {
    private const val PREFS = "musicmonster_sleep_timer"
    private const val END_AT = "ends_at"
    private const val RELEASE_DISPLAY = "release_display"
    private val _endsAt = MutableStateFlow(0L)
    val endsAt: StateFlow<Long> = _endsAt
    private val _releaseDisplay = MutableStateFlow(true)
    val releaseDisplay: StateFlow<Boolean> = _releaseDisplay
    private val _screenReleased = MutableStateFlow(false)
    val screenReleased: StateFlow<Boolean> = _screenReleased

    fun restore(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _endsAt.value = p.getLong(END_AT, 0L)
        _releaseDisplay.value = p.getBoolean(RELEASE_DISPLAY, true)
    }

    fun start(context: Context, minutes: Int, allowDisplayTimeout: Boolean) {
        val until = System.currentTimeMillis() + minutes.coerceIn(1, 1440) * 60_000L
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(END_AT, until).putBoolean(RELEASE_DISPLAY, allowDisplayTimeout).apply()
        _releaseDisplay.value = allowDisplayTimeout
        _screenReleased.value = false
        _endsAt.value = until
    }

    fun cancel(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(END_AT).apply()
        _endsAt.value = 0L
        _screenReleased.value = false
    }

    fun poll(context: Context, controller: PlayerController): Boolean {
        val deadline = _endsAt.value
        if (deadline <= 0L || System.currentTimeMillis() < deadline) return false
        controller.pause()
        val allowScreenOff = _releaseDisplay.value
        cancel(context)
        // Keep the screen awake flag released after playback has been paused.
        // This is not a privileged screen-off command: Android controls actual timeout.
        _screenReleased.value = allowScreenOff
        return true
    }
}
