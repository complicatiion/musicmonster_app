package com.sksdesign.musicmonster

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.media.AudioManager
import android.provider.MediaStore
import android.content.Context
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import com.sksdesign.musicmonster.ui.legal.*
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sksdesign.musicmonster.data.*
import com.sksdesign.musicmonster.player.PlayerController
import com.sksdesign.musicmonster.player.MusicMonsterRuntime
import com.sksdesign.musicmonster.player.PlayerMediaSession
import com.sksdesign.musicmonster.player.PlaybackNotificationService
import com.sksdesign.musicmonster.player.AudioVisualizerController
import com.sksdesign.musicmonster.player.VisualizerStatus
import com.sksdesign.musicmonster.player.SleepTimerManager
import com.sksdesign.musicmonster.ui.WebLibraryScreen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.math.min
import kotlin.math.max
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ANDROID_AUTO_MODE = "android_auto_mode"
        const val EXTRA_ANDROID_AUTO_COMPACT = "android_auto_compact"
    }

    private var keyboardViewModel: MusicMonsterViewModel? = null

    fun bindKeyboardPlayer(vm: MusicMonsterViewModel?) { keyboardViewModel = vm }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Focused Compose components get first refusal: editing text / D-pad selection wins.
        if (super.dispatchKeyEvent(event)) return true
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val vm = keyboardViewModel ?: return false // License screen is handled separately.
        return when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.toggle(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { vm.next(); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { vm.previous(); true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(10_000L); true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000L); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.isAltPressed) { vm.seekBy(10_000L); true } else if (event.isCtrlPressed) { vm.next(); true } else false
            KeyEvent.KEYCODE_DPAD_LEFT -> if (event.isAltPressed) { vm.seekBy(-10_000L); true } else if (event.isCtrlPressed) { vm.previous(); true } else false
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL -> { vm.navigateBack(); true }
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> { adjustMusicVolume(AudioManager.ADJUST_RAISE); true }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { adjustMusicVolume(AudioManager.ADJUST_LOWER); true }
            KeyEvent.KEYCODE_EQUALS -> if (event.isShiftPressed) { adjustMusicVolume(AudioManager.ADJUST_RAISE); true } else false
            KeyEvent.KEYCODE_M -> { if (event.isCtrlPressed) { adjustMusicVolume(AudioManager.ADJUST_TOGGLE_MUTE); true } else false }
            KeyEvent.KEYCODE_F -> if (event.isCtrlPressed) { vm.setTab(MainTab.Search); true } else false
            else -> false // D-pad and Enter/OK remain normal Android Compose focus actions.
        }
    }

    private fun adjustMusicVolume(direction: Int) {
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    var externalMmUri by mutableStateOf<Uri?>(null)
        private set
    fun consumeExternalMmUri() { externalMmUri = null }

    private fun receiveMmIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) externalMmUri = intent.data
    }

    private var requestedAndroidAutoMode by mutableStateOf(false)
    private var requestedAndroidAutoCompact by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveMmIntent(intent)
        requestedAndroidAutoMode = intent?.getBooleanExtra(EXTRA_ANDROID_AUTO_MODE, false) == true
        requestedAndroidAutoCompact = intent?.getBooleanExtra(EXTRA_ANDROID_AUTO_COMPACT, false) == true
        setContent { MusicMonsterApp(requestedAndroidAutoMode, requestedAndroidAutoCompact) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveMmIntent(intent)
        requestedAndroidAutoMode = intent.getBooleanExtra(EXTRA_ANDROID_AUTO_MODE, false)
        requestedAndroidAutoCompact = intent.getBooleanExtra(EXTRA_ANDROID_AUTO_COMPACT, false)
    }
}

enum class NetworkLibraryStatus { Cached, Scanning, Online, Offline }

class MusicMonsterViewModel(private val context: android.content.Context) : ViewModel() {
    private val repo = MusicRepository(context)
    private val store = SettingsStore(context)
    val player = MusicMonsterRuntime.getOrCreateController(context)
    private val mediaSession = PlayerMediaSession.getOrCreate(context, player)
    private val visualizer = AudioVisualizerController(context, player.audioSessionId)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _podcasts = MutableStateFlow<List<Track>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _tab = MutableStateFlow(MainTab.Home)
    private val _previousTab = MutableStateFlow(MainTab.Home)
    private val _homeSection = MutableStateFlow(HomeSection.Home)
    private val _librarySection = MutableStateFlow(LibrarySection.Albums)
    private val _libraryDetail = MutableStateFlow<LibraryDetail?>(null)
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _showFullPlayer = MutableStateFlow(false)
    private val _showQueue = MutableStateFlow(false)
    private val _selectedPlaylistIndex = MutableStateFlow<Int?>(null)
    private val _settingsFocus = MutableStateFlow<String?>(null)
    private val _carMode = MutableStateFlow(false)
    private val _androidAutoConnected = MutableStateFlow(false)
    private val _androidAutoCompactMode = MutableStateFlow(false)
    private val carConnection = CarConnection(context)
    private val carConnectionObserver = Observer<Int> { type ->
        val connected = type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        _androidAutoConnected.value = connected
        if (connected) {
            _androidAutoCompactMode.value = false
            when (settings.value.androidAutoStartMode) {
                "BigPlayer" -> _showFullPlayer.value = true
                "LastTab" -> { _showFullPlayer.value = false; restoreLastTab() }
                else -> { _showFullPlayer.value = false; _tab.value = MainTab.Home }
            }
        }
    }
    private val networkScanMutex = Mutex()
    private val _networkLibraryStatus = MutableStateFlow(NetworkLibraryStatus.Cached)
    val networkLibraryStatus: StateFlow<NetworkLibraryStatus> = _networkLibraryStatus.asStateFlow()
    private val _cacheReady = MutableStateFlow(false)
    private val launchSeed = System.currentTimeMillis()

    val tracks = _tracks.asStateFlow()
    val podcasts = _podcasts.asStateFlow()
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val tab = _tab.asStateFlow()
    val homeSection = _homeSection.asStateFlow()
    val librarySection = _librarySection.asStateFlow()
    val libraryDetail = _libraryDetail.asStateFlow()
    val query = _query.asStateFlow()
    val playlists = _playlists.asStateFlow()
    val showFullPlayer = _showFullPlayer.asStateFlow()
    val showQueue = _showQueue.asStateFlow()
    val selectedPlaylistIndex = _selectedPlaylistIndex.asStateFlow()
    val settingsFocus = _settingsFocus.asStateFlow()
    val carMode = _carMode.asStateFlow()
    val androidAutoConnected = _androidAutoConnected.asStateFlow()
    val androidAutoCompactMode = _androidAutoCompactMode.asStateFlow()
    val currentTrack = player.currentTrack
    val isPlaying = player.isPlaying
    val positionMs = player.positionMs
    val durationMs = player.durationMs
    val shuffle = player.shuffle
    val repeatMode = player.repeatMode
    val queue = player.queue
    val queueIndex = player.queueIndex
    val visualizerBars = visualizer.bars
    val visualizerWaveform = visualizer.waveform
    val visualizerStatus = visualizer.status

    val filteredTracks = combine(_tracks, _query) { tracks, q ->
        if (q.isBlank()) tracks else tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.genre.contains(q, true) || it.folder.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        carConnection.type.observeForever(carConnectionObserver)
        viewModelScope.launch {
            // Read the persisted DataStore snapshot, not the stateIn placeholder AppSettings().
            // Otherwise a stored SMB share can disappear from the startup cache pass.
            val initialSettings = store.settings.first()
            val cachedBaseTracks = repo.loadCachedTracks()
                .filterNot { it.isNetwork } // Network tracks are restored only from the matching share URI.
                .filter { initialSettings.includeSocialAudio || !looksLikeSocialAudio(it) }
            repo.migrateLegacyNetworkCache(initialSettings.networkLibraryUri)
            val cachedNetworkTracks = if (initialSettings.networkLibraryEnabled) {
                repo.loadCachedNetworkTracks(initialSettings.networkLibraryUri).filter { initialSettings.includeSocialAudio || !looksLikeSocialAudio(it) }
            } else {
                emptyList()
            }
            val cachedTracks = mergeTrackLists(cachedBaseTracks, cachedNetworkTracks)
            val cachedPodcasts = repo.loadCachedPodcasts().filter { initialSettings.includeSocialAudio || !looksLikeSocialAudio(it) }
            val cachedPlaylists = repo.loadCachedPlaylists()
            if (cachedTracks.isNotEmpty()) _tracks.value = cachedTracks
            if (cachedPodcasts.isNotEmpty()) _podcasts.value = cachedPodcasts
            if (cachedPlaylists.isNotEmpty()) _playlists.value = cachedPlaylists
            _cacheReady.value = true
            if (initialSettings.networkLibraryEnabled && initialSettings.networkLibraryUri.isNotBlank()) {
                // An unreachable network provider is handled by cached metadata, not forgotten.
                scanNetworkLibrary()
            }
        }
        viewModelScope.launch {
            combine(player.currentTrack, player.isPlaying, player.positionMs, player.durationMs) { track, playing, position, duration ->
                PlaybackSnapshot(track, playing, position, duration)
            }.collect { snapshot ->
                mediaSession.update(snapshot.track, snapshot.playing, snapshot.positionMs, snapshot.durationMs)
                PlaybackNotificationService.sync(context, snapshot.track != null)
            }
        }
        viewModelScope.launch {
            _cacheReady.filter { it }.first()
            val s = store.settings.first()
            if (s.autoScanEnabled) {
                val cachedLibraryAvailable = _tracks.value.isNotEmpty() || _podcasts.value.isNotEmpty()
                val now = System.currentTimeMillis()
                val intervalMs = s.autoScanIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
                if (cachedLibraryAvailable && s.lastAutoScanAt == 0L) {
                    store.save(s.copy(lastAutoScanAt = now))
                } else if (!cachedLibraryAvailable || now - s.lastAutoScanAt >= intervalMs) {
                    delay(if (cachedLibraryAvailable) 3500L else 0L)
                    scanLibrary()
                }
            }
        }
        viewModelScope.launch {
            _cacheReady.filter { it }.first()
            val s = store.settings.first()
            if (s.networkLibraryEnabled && s.networkLibraryUri.isNotBlank() && s.networkAutoScanEnabled) {
                val now = System.currentTimeMillis()
                val intervalMs = s.networkScanIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
                if (s.lastNetworkAutoScanAt == 0L || now - s.lastNetworkAutoScanAt >= intervalMs) {
                    delay(4500L)
                    scanNetworkLibrary()
                    store.save(store.settings.first().copy(lastNetworkAutoScanAt = now))
                }
            }
        }
        viewModelScope.launch {
            settings.collect {
                player.applyAudioSettings(it)
                player.applyPlaybackDefaults(it)
                visualizer.updateSettings(it)
            }
        }
    }

    fun scanLibraryIfEmpty() = viewModelScope.launch {
        _cacheReady.filter { it }.first()
        if (_tracks.value.none { !it.isNetwork }) scanLibrary()
    }

    private fun mergeTrackLists(primary: List<Track>, extra: List<Track>): List<Track> {
        return (primary + extra)
            .distinctBy { it.uri.toString() }
            .sortedWith(compareBy<Track> { it.album.lowercase() }.thenBy { it.trackNumber }.thenBy { it.title.lowercase() })
    }

    private suspend fun customFolderTracks(s: AppSettings): List<Track> {
        return if (s.customFolderUri.isNotBlank()) repo.loadTracks(s.customFolderUri, s.includeSocialAudio) else emptyList()
    }

    private suspend fun ostFolderTracks(s: AppSettings): List<Track> {
        return if (s.ostFolderUri.isNotBlank()) repo.loadTracks(s.ostFolderUri, s.includeSocialAudio).map { it.copy(isOst = true) } else emptyList()
    }

    private suspend fun scanNetworkOrLoadCache(uri: String, includeSocialAudio: Boolean): List<Track> = networkScanMutex.withLock {
        _networkLibraryStatus.value = NetworkLibraryStatus.Scanning
        val result = repo.scanNetworkDocumentTree(uri, includeSocialAudio)
        if (result.reachable) {
            _networkLibraryStatus.value = NetworkLibraryStatus.Online
            // A transient empty listing must not silently erase the user's offline index.
            if (result.tracks.isNotEmpty()) {
                repo.saveCachedNetworkTracks(result.tracks, uri)
                return result.tracks
            }
        } else {
            _networkLibraryStatus.value = NetworkLibraryStatus.Offline
        }
        return repo.loadCachedNetworkTracks(uri).filter { includeSocialAudio || !looksLikeSocialAudio(it) }
    }

    private suspend fun networkLibraryTracks(s: AppSettings): List<Track> {
        if (!s.networkLibraryEnabled || s.networkLibraryUri.isBlank()) return emptyList()
        return scanNetworkOrLoadCache(s.networkLibraryUri, s.includeSocialAudio)
    }

    private suspend fun libraryExtras(s: AppSettings): List<Track> {
        return mergeTrackLists(mergeTrackLists(networkLibraryTracks(s), ostFolderTracks(s)), customFolderTracks(s))
    }

    fun scanLibrary() = viewModelScope.launch {
        // Reads the committed SAF/SMB selection even while the stateIn UI stream starts up.
        val s = store.settings.first()
        val baseTracks = repo.loadTracks(s.musicFolderUri, s.includeSocialAudio)
        val loadedTracks = mergeTrackLists(baseTracks, libraryExtras(s))
        val loadedPodcasts = repo.loadPodcasts(s.podcastFolderUri, loadedTracks, s.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
        store.save(s.copy(lastAutoScanAt = System.currentTimeMillis()))
        _libraryDetail.value = null
    }

    fun scanDeviceLibrary() = viewModelScope.launch {
        val s = store.settings.first()
        val baseTracks = repo.loadTracks("", s.includeSocialAudio)
        val loadedTracks = mergeTrackLists(baseTracks, libraryExtras(s))
        val loadedPodcasts = repo.loadPodcasts(s.podcastFolderUri, loadedTracks, s.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
        store.save(s.copy(lastAutoScanAt = System.currentTimeMillis()))
        _libraryDetail.value = null
    }

    fun scanPodcasts() = viewModelScope.launch {
        val loadedPodcasts = repo.loadPodcasts(settings.value.podcastFolderUri, _tracks.value, settings.value.includeSocialAudio)
        _podcasts.value = loadedPodcasts
        repo.saveCachedPodcasts(loadedPodcasts)
    }

    fun setMusicFolder(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        val s = settings.value.copy(musicFolderUri = value, localFolderPromptDone = true)
        store.save(s)
        val loadedTracks = mergeTrackLists(repo.loadTracks(value, s.includeSocialAudio), libraryExtras(s))
        val loadedPodcasts = repo.loadPodcasts(s.podcastFolderUri, loadedTracks, s.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
    }

    fun setPodcastFolder(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        store.save(settings.value.copy(podcastFolderUri = value))
        val loadedPodcasts = repo.loadPodcasts(value, _tracks.value, settings.value.includeSocialAudio)
        _podcasts.value = loadedPodcasts
        repo.saveCachedPodcasts(loadedPodcasts)
    }

    fun setOstFolder(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        val s = settings.value.copy(ostFolderUri = value)
        store.save(s)
        val ostTracks = repo.loadTracks(value, s.includeSocialAudio).map { it.copy(isOst = true) }
        val mergedTracks = mergeTrackLists(_tracks.value.filterNot { it.isOst }, ostTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        _libraryDetail.value = null
    }

    fun scanOstFolder() = viewModelScope.launch {
        val s = settings.value
        if (s.ostFolderUri.isBlank()) return@launch
        val ostTracks = repo.loadTracks(s.ostFolderUri, s.includeSocialAudio).map { it.copy(isOst = true) }
        val mergedTracks = mergeTrackLists(_tracks.value.filterNot { it.isOst }, ostTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        _libraryDetail.value = null
    }

    fun setCustomFolder(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        val s = settings.value.copy(customFolderUri = value)
        store.save(s)
        val customTracks = repo.loadTracks(value, s.includeSocialAudio)
        val mergedTracks = mergeTrackLists(_tracks.value, customTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        _libraryDetail.value = null
    }

    fun scanCustomFolder() = viewModelScope.launch {
        val s = settings.value
        if (s.customFolderUri.isBlank()) return@launch
        val customTracks = repo.loadTracks(s.customFolderUri, s.includeSocialAudio)
        val mergedTracks = mergeTrackLists(_tracks.value, customTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        _libraryDetail.value = null
    }

    fun setNetworkLibraryEnabled(enabled: Boolean) = viewModelScope.launch {
        val updated = settings.value.copy(networkLibraryEnabled = enabled)
        store.save(updated)
        if (!enabled) {
            _networkLibraryStatus.value = NetworkLibraryStatus.Cached
            val cleanedTracks = _tracks.value.filterNot { it.isNetwork }
            _tracks.value = cleanedTracks
            repo.saveCachedTracks(cleanedTracks)
            _libraryDetail.value = null
        } else if (updated.networkLibraryUri.isNotBlank()) {
            scanNetworkLibrary()
        }
    }

    fun setNetworkLibrary(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        val label = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: "Network Library"
        val updated = settings.value.copy(
            networkLibraryEnabled = true,
            networkLibraryUri = value,
            networkLibraryName = label
        )
        store.save(updated)
        val fallbackTracks = scanNetworkOrLoadCache(value, updated.includeSocialAudio)
        val mergedTracks = mergeTrackLists(_tracks.value.filterNot { it.isNetwork }, fallbackTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        _libraryDetail.value = null
    }

    fun scanNetworkLibrary() = viewModelScope.launch {
        val s = store.settings.first()
        if (!s.networkLibraryEnabled || s.networkLibraryUri.isBlank()) return@launch
        val fallbackTracks = scanNetworkOrLoadCache(s.networkLibraryUri, s.includeSocialAudio)
        val mergedTracks = mergeTrackLists(_tracks.value.filterNot { it.isNetwork }, fallbackTracks)
        _tracks.value = mergedTracks
        repo.saveCachedTracks(mergedTracks)
        store.save(s.copy(lastNetworkAutoScanAt = System.currentTimeMillis()))
        _libraryDetail.value = null
    }

    fun clearLocalLibraryCache() = viewModelScope.launch {
        repo.clearCachedLibrary()
        val s = settings.value
        // Clearing phone cache must not hide the separately cached SMB library.
        _tracks.value = if (s.networkLibraryEnabled) repo.loadCachedNetworkTracks(s.networkLibraryUri) else emptyList()
        _podcasts.value = emptyList()
        _libraryDetail.value = null
    }

    fun markLocalFolderPromptDone() = viewModelScope.launch {
        store.save(settings.value.copy(localFolderPromptDone = true))
    }

    fun resetAppData() = viewModelScope.launch {
        SleepTimerManager.cancel(context)
        player.pause()
        repo.clearCachedLibrary()
        repo.saveCachedPlaylists(emptyList())
        _tracks.value = emptyList()
        _podcasts.value = emptyList()
        _playlists.value = emptyList()
        _libraryDetail.value = null
        _selectedPlaylistIndex.value = null
        _showFullPlayer.value = false
        _showQueue.value = false
        store.resetAllSettings()
        _tab.value = MainTab.Home
        _homeSection.value = HomeSection.Home
        _librarySection.value = LibrarySection.Albums
    }

    fun setTab(t: MainTab) {
        val current = _tab.value
        if (t == MainTab.Settings && current != MainTab.Settings) {
            _previousTab.value = current
        }
        _tab.value = t
        _libraryDetail.value = null
        if (settings.value.rememberLastScreen && t != MainTab.Settings) {
            val page = when (t) {
                MainTab.Home -> _homeSection.value.name
                MainTab.Library -> _librarySection.value.name
                else -> t.name
            }
            viewModelScope.launch { store.save(settings.value.copy(startPage = page)) }
        }
    }

    fun setHome(s: HomeSection) {
        _tab.value = MainTab.Home
        _homeSection.value = s
        if (settings.value.rememberLastScreen) {
            viewModelScope.launch { store.save(settings.value.copy(startPage = s.name)) }
        }
    }

    fun setLibrary(s: LibrarySection) {
        _tab.value = MainTab.Library
        _librarySection.value = s
        _libraryDetail.value = null
        if (settings.value.rememberLastScreen) {
            viewModelScope.launch { store.save(settings.value.copy(startPage = s.name)) }
        }
    }

    fun applyStartPage(page: String) {
        when (page) {
            "Home" -> setHome(HomeSection.Home)
            "Discovery" -> setHome(HomeSection.Discovery)
            "Favorites" -> setHome(HomeSection.Favorites)
            "Top" -> setHome(HomeSection.Top)
            "History" -> setHome(HomeSection.History)
            "Search" -> setTab(MainTab.Search)
            "Playlists" -> setTab(MainTab.Playlists)
            "Settings" -> setTab(MainTab.Settings)
            else -> runCatching { setLibrary(LibrarySection.valueOf(page)) }.getOrElse { setHome(HomeSection.Home) }
        }
    }

    fun search(q: String) { _query.value = q }
    fun play(items: List<Track>, index: Int) {
        player.playQueue(items, index)
        markTrackPlayed(items.getOrNull(index))
    }
    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(position: Long) = player.seekTo(position)
    fun seekBy(delta: Long) = player.seekBy(delta)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeatMode() = player.cycleRepeatMode()
    fun addToQueue(track: Track) = player.addToQueue(track)
    fun removeFromQueue(index: Int) = player.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun playQueueItem(index: Int) {
        player.playQueueIndex(index)
        markTrackPlayed(queue.value.getOrNull(index))
    }
    fun showFullPlayer(show: Boolean) { _showFullPlayer.value = show }
    fun enterAndroidAutoMode(compact: Boolean = false) {
        _androidAutoCompactMode.value = false
        when (settings.value.androidAutoStartMode) {
            "BigPlayer" -> _showFullPlayer.value = true
            "LastTab" -> { _showFullPlayer.value = false; restoreLastTab() }
            else -> { _showFullPlayer.value = false; _tab.value = MainTab.Home }
        }
    }
    private fun restoreLastTab() {
        // Read the last *persisted* page, including the Home/Library sub-section.
        // This is the same preference used by the existing "Remember last tab" setting.
        if (settings.value.rememberLastScreen) applyStartPage(settings.value.startPage)
        else _tab.value = MainTab.Home
        _libraryDetail.value = null
    }
    fun showQueue(show: Boolean) { _showQueue.value = show }
    fun cycleCarShortcut() = toggleLandscapeMode()

    fun toggleLandscapeMode() = viewModelScope.launch {
        store.save(settings.value.copy(landscapeMode = !settings.value.landscapeMode))
    }
    fun updateSettings(s: AppSettings) = viewModelScope.launch { store.save(s) }
    fun closeSettings() { _tab.value = _previousTab.value }
    fun navigateBack() {
        when {
            _showFullPlayer.value -> _showFullPlayer.value = false
            _showQueue.value -> _showQueue.value = false
            _libraryDetail.value != null -> _libraryDetail.value = null
            _selectedPlaylistIndex.value != null -> _selectedPlaylistIndex.value = null
            _tab.value == MainTab.Settings -> closeSettings()
            _tab.value != MainTab.Home -> setTab(MainTab.Home)
            else -> Unit
        }
    }
    fun exportSettings(): String = store.exportJson(settings.value)
    fun importSettings(json: String) = viewModelScope.launch { store.save(store.importJson(json)) }
    fun exportFavoritesJson(): String = favoriteSet().sorted().joinToString(prefix = "[", postfix = "]")
    fun importFavoritesJson(json: String) = viewModelScope.launch {
        val ids = Regex("\\d+").findAll(json).mapNotNull { it.value.toLongOrNull() }.distinct().toList()
        store.save(settings.value.copy(favoriteIds = ids.joinToString(",")))
    }
    fun importM3u(name: String, content: String) = viewModelScope.launch {
        _playlists.value = _playlists.value + repo.parseM3u(name, content, _tracks.value)
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun exportM3u(p: Playlist): String = repo.exportM3u(p)
    fun exportPlaylist(p: Playlist, format: String): String = repo.exportPlaylist(p, format)
    fun exportAllPlaylistsZip(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            _playlists.value.forEach { playlist ->
                val safe = playlist.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "Playlist" }
                zip.putNextEntry(ZipEntry("$safe.m3u8"))
                zip.write(repo.exportPlaylist(playlist, "M3U8").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            if (_playlists.value.isEmpty()) {
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write("No playlists available for export.".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
    fun createPlaylist(name: String = "New Playlist ${_playlists.value.size + 1}", tracks: List<Track> = emptyList()) = viewModelScope.launch {
        val safeName = name.ifBlank { "New Playlist ${_playlists.value.size + 1}" }
        _playlists.value = _playlists.value + Playlist(safeName, tracks)
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun createPlaylistFromLibrary() = createPlaylist("Library Playlist ${_playlists.value.size + 1}", _tracks.value.take(25))
    fun openPlaylist(index: Int) { _selectedPlaylistIndex.value = index.takeIf { it in _playlists.value.indices } }
    fun closePlaylist() { _selectedPlaylistIndex.value = null }
    fun deletePlaylist(index: Int) = viewModelScope.launch {
        if (index !in _playlists.value.indices) return@launch
        _playlists.value = _playlists.value.toMutableList().apply { removeAt(index) }
        _selectedPlaylistIndex.value = null
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun renamePlaylist(index: Int, name: String) = viewModelScope.launch {
        if (index !in _playlists.value.indices || name.isBlank()) return@launch
        _playlists.value = _playlists.value.toMutableList().apply { this[index] = this[index].copy(name = name, updatedAt = System.currentTimeMillis()) }
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun addTrackToPlaylist(index: Int, track: Track) = viewModelScope.launch {
        if (index !in _playlists.value.indices) return@launch
        _playlists.value = _playlists.value.toMutableList().apply {
            val p = this[index]
            this[index] = p.copy(tracks = p.tracks + track, updatedAt = System.currentTimeMillis())
        }
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun addCurrentToPlaylist(index: Int) { currentTrack.value?.let { addTrackToPlaylist(index, it) } }
    fun removeTrackFromPlaylist(playlistIndex: Int, trackIndex: Int) = viewModelScope.launch {
        if (playlistIndex !in _playlists.value.indices) return@launch
        val p = _playlists.value[playlistIndex]
        if (trackIndex !in p.tracks.indices) return@launch
        _playlists.value = _playlists.value.toMutableList().apply { this[playlistIndex] = p.copy(tracks = p.tracks.toMutableList().apply { removeAt(trackIndex) }, updatedAt = System.currentTimeMillis()) }
        repo.saveCachedPlaylists(_playlists.value)
    }
    fun movePlaylistTrack(playlistIndex: Int, from: Int, to: Int) = viewModelScope.launch {
        if (playlistIndex !in _playlists.value.indices) return@launch
        val p = _playlists.value[playlistIndex]
        if (from !in p.tracks.indices || to !in p.tracks.indices) return@launch
        val moved = p.tracks.toMutableList().apply { add(to, removeAt(from)) }
        _playlists.value = _playlists.value.toMutableList().apply { this[playlistIndex] = p.copy(tracks = moved, updatedAt = System.currentTimeMillis()) }
        repo.saveCachedPlaylists(_playlists.value)
    }

    fun movePlaylist(from: Int, to: Int) = viewModelScope.launch {
        if (from !in _playlists.value.indices || to !in _playlists.value.indices || from == to) return@launch
        _playlists.value = _playlists.value.toMutableList().apply { add(to, removeAt(from)) }
        repo.saveCachedPlaylists(_playlists.value)
    }

    fun smartPlaylists(): List<Playlist> {
        val allTracks = _tracks.value
        val favorites = allTracks.filter { isFavorite(it) }.take(100)
        val mostPlayed = allTracks.sortedByDescending { it.playCount }.filter { it.playCount > 0 }.take(100)
        val recentlyPlayed = allTracks.filter { it.lastPlayed > 0L }.sortedByDescending { it.lastPlayed }.take(100)
        val recentlyAdded = allTracks.sortedByDescending { it.dateAdded }.take(100)
        val forgotten = allTracks.filter { it.lastPlayed == 0L }.take(100).ifEmpty { allTracks.sortedBy { it.lastPlayed }.take(100) }
        val genreGroups = allTracks.filter { it.genre.isNotBlank() && !it.genre.equals("Unknown", true) }.groupBy { it.genre.lowercase() }
        val discover = genreGroups.values.maxByOrNull { it.size }?.shuffled(Random(launchSeed + 303))?.take(50)
            ?: allTracks.shuffled(Random(launchSeed + 304)).take(50)
        val randomTracks = allTracks.shuffled(Random(System.currentTimeMillis() / 60000L)).take(50)
        return listOf(
            Playlist("Recently Added", recentlyAdded),
            Playlist("Favorite Tracks", favorites),
            Playlist("Most Played", mostPlayed),
            Playlist("Recently Played", recentlyPlayed),
            Playlist("Random Tracks", randomTracks),
            Playlist("Discover Tracks", discover),
            Playlist("Forgotten Tracks", forgotten)
        )
    }

    fun openAlbum(album: Album) { _libraryDetail.value = LibraryDetail(album.name, "${album.artist} • ${album.tracks.size} tracks", album.tracks) }
    fun openGroup(title: String, tracks: List<Track>) { _libraryDetail.value = LibraryDetail(title, "${tracks.size} tracks", tracks) }
    fun openArtistFromTrack(track: Track) {
        val matches = _tracks.value.filter { it.artist.equals(track.artist, ignoreCase = true) }
        _librarySection.value = LibrarySection.Artists
        _libraryDetail.value = LibraryDetail(track.artist, "${matches.size} tracks", matches)
        _showFullPlayer.value = false
        _tab.value = MainTab.Library
    }
    fun openAlbumFromTrack(track: Track) {
        val matches = _tracks.value.filter { it.album.equals(track.album, ignoreCase = true) }
        _librarySection.value = LibrarySection.Albums
        _libraryDetail.value = LibraryDetail(track.album, "${track.artist} • ${matches.size} tracks", matches)
        _showFullPlayer.value = false
        _tab.value = MainTab.Library
    }
    fun closeDetail() { _libraryDetail.value = null }
    fun openLocalLibrarySettings() {
        _settingsFocus.value = "LocalLibrary"
        _tab.value = MainTab.Settings
    }
    fun consumeSettingsFocus() { _settingsFocus.value = null }

    fun randomTracks(count: Int): List<Track> = _tracks.value.shuffled(Random(launchSeed)).take(count)
    fun randomAlbums(count: Int): List<Album> = repo.albums(_tracks.value).shuffled(Random(launchSeed + 21)).take(count)

    private fun homeRecommendationTracks(count: Int, offset: Long = 0L): List<Track> {
        val allTracks = _tracks.value
        if (allTracks.isEmpty()) return emptyList()
        val recent = allTracks.filter { it.lastPlayed > 0L }.sortedByDescending { it.lastPlayed }.take(120)
        val topArtists = recent.groupBy { it.artist.lowercase(Locale.US) }.entries.sortedByDescending { it.value.size }.take(5).map { it.key }.toSet()
        val topGenres = recent.filter { !it.genre.equals("Unknown", true) && it.genre.isNotBlank() }
            .groupBy { it.genre.lowercase(Locale.US) }.entries.sortedByDescending { it.value.size }.take(5).map { it.key }.toSet()
        val tastePool = allTracks.filter { track ->
            track.artist.lowercase(Locale.US) in topArtists || track.genre.lowercase(Locale.US) in topGenres
        }
        val weighted = (tastePool.shuffled(Random(launchSeed + 410 + offset)).take(count * 2) +
            allTracks.shuffled(Random(launchSeed + 510 + offset)).take(count * 2))
            .distinctBy { it.id }
        return weighted.take(count).ifEmpty { allTracks.shuffled(Random(launchSeed + offset)).take(count) }
    }

    fun homePicks(count: Int): List<Track> = homeRecommendationTracks(count, 1L)

    fun homeAlbums(count: Int): List<Album> {
        val albums = repo.albums(_tracks.value)
        if (albums.isEmpty()) return emptyList()
        val byKey = albums.associateBy { (it.name + "|" + it.artist).lowercase(Locale.US) }
        val preferredKeys = homeRecommendationTracks(count * 4, 2L).map { (it.album + "|" + it.artist).lowercase(Locale.US) }
        return (preferredKeys.mapNotNull { byKey[it] } + albums.shuffled(Random(launchSeed + 621))).distinctBy { it.name.lowercase(Locale.US) + it.artist.lowercase(Locale.US) }.take(count)
    }

    fun homeArtists(count: Int): List<Artist> {
        val artists = repo.artists(_tracks.value)
        if (artists.isEmpty()) return emptyList()
        val byName = artists.associateBy { it.name.lowercase(Locale.US) }
        val preferredNames = homeRecommendationTracks(count * 4, 3L).map { it.artist.lowercase(Locale.US) }
        return (preferredNames.mapNotNull { byName[it] } + artists.shuffled(Random(launchSeed + 721))).distinctBy { it.name.lowercase(Locale.US) }.take(count)
    }

    fun albums(): List<Album> = repo.albums(_tracks.value)
    fun artists(): List<Artist> = repo.artists(_tracks.value)

    fun isFavorite(track: Track): Boolean = favoriteSet().contains(track.id)

    fun toggleFavorite(track: Track) = viewModelScope.launch {
        val ids = favoriteSet().toMutableSet()
        if (!ids.add(track.id)) ids.remove(track.id)
        store.save(settings.value.copy(favoriteIds = ids.joinToString(",")))
    }

    /** One DataStore write for the entire album/folder; mixed selections become favorites. */
    fun toggleGroupFavorites(group: List<Track>) = viewModelScope.launch {
        if (group.isEmpty()) return@launch
        val ids = favoriteSet().toMutableSet()
        val selected = group.map { it.id }.toSet()
        if (selected.all(ids::contains)) ids.removeAll(selected) else ids.addAll(selected)
        store.save(settings.value.copy(favoriteIds = ids.joinToString(",")))
    }

    private fun favoriteSet(): Set<Long> = settings.value.favoriteIds.split(',').mapNotNull { it.toLongOrNull() }.toSet()

    private fun markTrackPlayed(track: Track?) = viewModelScope.launch {
        if (track == null || !settings.value.savePlayHistory) return@launch
        val now = System.currentTimeMillis()
        fun updateList(list: List<Track>): List<Track> = list.map {
            if (it.id == track.id) it.copy(playCount = it.playCount + 1, lastPlayed = now) else it
        }
        _tracks.value = updateList(_tracks.value)
        _podcasts.value = updateList(_podcasts.value)
        repo.saveCachedTracks(_tracks.value)
        repo.saveCachedPodcasts(_podcasts.value)
    }

    fun setVisualizerActive(active: Boolean) { visualizer.setActive(active) }
    fun hasVisualizerPermission(): Boolean = visualizer.hasAudioPermission()

    override fun onCleared() {
        carConnection.type.removeObserver(carConnectionObserver)
        visualizer.release()
        mediaSession.release()
    }
}

@Suppress("UNCHECKED_CAST")
data class PlaybackSnapshot(val track: Track?, val playing: Boolean, val positionMs: Long, val durationMs: Long)

class MusicMonsterViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MusicMonsterViewModel(context.applicationContext) as T
}

@Composable
fun MusicMonsterApp(startInAndroidAutoMode: Boolean = false, startInAndroidAutoCompact: Boolean = false) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context.applicationContext) }
    LaunchedEffect(Unit) { SleepTimerManager.restore(context.applicationContext) }
    var accepted by remember { mutableStateOf<Boolean?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val consentSettings by store.settings.collectAsState(initial = AppSettings())
    LaunchedEffect(store) {
        accepted = runCatching { store.acceptedLegalRevision.first() == LegalDocuments.REVISION }
            .getOrDefault(false)
    }
    when (accepted) {
        true -> MusicMonsterAppContent(startInAndroidAutoMode, startInAndroidAutoCompact)
        false -> {
        val consentHaze = remember { HazeState() }
        var consentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { consentVisible = true }
        CompositionLocalProvider(LocalBackdropHaze provides consentHaze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().haze(consentHaze)) {
                MusicMonsterAppContent(startInAndroidAutoMode, startInAndroidAutoCompact, firstStartBlocked = true)
            }
            CompositionLocalProvider(LocalSurfaceAppearance provides consentSettings) {
            AnimatedVisibility(
                visible = consentVisible,
                enter = fadeIn(tween(animationDurationMs(240, consentSettings))) +
                    scaleIn(initialScale = .96f, animationSpec = tween(animationDurationMs(240, consentSettings)))
            ) {
            LicenseConsentScreen(
            isSaving = saving,
            onAccept = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        // Enter the application only after the preference was written successfully.
                        runCatching { store.acceptLegalRevision(LegalDocuments.REVISION) }
                            .onSuccess { accepted = true }
                            .onFailure { android.widget.Toast.makeText(context, "Unable to save license acceptance", android.widget.Toast.LENGTH_LONG).show() }
                        saving = false
                    }
                }
            },
            onDecline = { (context as? Activity)?.finishAffinity() }
        )
            } // AnimatedVisibility
            } // Appearance provider
        } // first-start app / dialog scene
        } // backdrop provider
        }
        null -> Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

fun isRunningOnTv(context: Context): Boolean {
    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTelevision = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanback || hasTelevision
}

@Composable
private fun MusicMonsterAppContent(startInAndroidAutoMode: Boolean, startInAndroidAutoCompact: Boolean, firstStartBlocked: Boolean = false) {
    val context = LocalContext.current
    val vm: MusicMonsterViewModel = viewModel(factory = MusicMonsterViewModelFactory(context))
    val incomingUri = (context as? MainActivity)?.externalMmUri
    LaunchedEffect(incomingUri, firstStartBlocked) {
        if (!firstStartBlocked && incomingUri != null) {
            val activity = context as? MainActivity
            try {
                // Cap unknown external .mm files to 4 MiB before deserializing.
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(incomingUri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(out.size() + read <= 4 * 1024 * 1024) { ".mm file is too large" }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    } ?: error("Unable to open .mm file")
                }
                val json = bytes.toString(Charsets.UTF_8).trim()
                when {
                    json.startsWith("{") -> vm.importSettings(json)
                    json.startsWith("[") -> vm.importFavoritesJson(json)
                    else -> error("Unsupported .mm document")
                }
            } catch (t: Exception) {
                android.widget.Toast.makeText(context, t.message ?: "Unable to import .mm file", android.widget.Toast.LENGTH_LONG).show()
            } finally { activity?.consumeExternalMmUri() }
        }
    }
    DisposableEffect(vm, context) {
        val activity = context as? MainActivity
        activity?.bindKeyboardPlayer(vm)
        onDispose { activity?.bindKeyboardPlayer(null) }
    }
    val settings by vm.settings.collectAsState()
    val tracks by vm.tracks.collectAsState()
    // stateIn(initialValue=AppSettings()) is only a placeholder; never gate onboarding on it.
    val persistedSettingsFlow: Flow<AppSettings?> = remember(context) {
        SettingsStore(context.applicationContext).settings.map { it as AppSettings? }
    }
    val persistedSettings by persistedSettingsFlow.collectAsState(initial = null)
    val androidAutoConnected by vm.androidAutoConnected.collectAsState()
    val isTvDevice = remember(context) { isRunningOnTv(context) }
    val timerEnd by SleepTimerManager.endsAt.collectAsState()
    val timerReleasesDisplay by SleepTimerManager.releaseDisplay.collectAsState()
    val screenReleased by SleepTimerManager.screenReleased.collectAsState()
    val keepScreenAwake = (settings.alwaysOn || (settings.automaticAlwaysOn && androidAutoConnected)) &&
        !screenReleased && !(timerEnd > 0L && timerReleasesDisplay)
    val accent = Color(settings.accentColor)
    val bg = when {
        settings.lightMode -> Color(0xFFF5F7F2)
        settings.amoledBlack -> Color.Black
        else -> if (settings.surfaceStyle == "Solid") {
            lerp(Color(0xFF0D1010), Color.Black, settings.surfaceDarkness.coerceIn(0f, 100f) / 100f)
        } else Color(0xFF0D1010)
    }
    var permissionsProcessed by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    val localFolderOnboardingLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.onFailure { _ ->
                runCatching { context.contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            vm.setMusicFolder(it)
        } ?: vm.markLocalFolderPromptDone()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val audioGranted = if (Build.VERSION.SDK_INT >= 33) result[Manifest.permission.READ_MEDIA_AUDIO] == true else result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) vm.scanLibraryIfEmpty()
        permissionsProcessed = true
    }

    val shouldShowLocalFolderOnboarding = !firstStartBlocked && persistedSettings != null &&
        persistedSettings?.localFolderPromptDone == false && persistedSettings?.musicFolderUri.isNullOrBlank() &&
        tracks.none { !it.isNetwork }
    LaunchedEffect(firstStartBlocked, shouldShowLocalFolderOnboarding, persistedSettings) {
        if (!firstStartBlocked && persistedSettings != null && !shouldShowLocalFolderOnboarding && !permissionRequested) {
            permissionRequested = true
            val permissions = buildList {
                add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            }.toTypedArray()
            permission.launch(permissions)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            SleepTimerManager.poll(context.applicationContext, vm.player)
            delay(1000L)
        }
    }
    LaunchedEffect(Unit) {
        delay(250)
        vm.applyStartPage(vm.settings.value.startPage)
    }
    LaunchedEffect(startInAndroidAutoMode, startInAndroidAutoCompact) {
        if (startInAndroidAutoMode) vm.enterAndroidAutoMode(startInAndroidAutoCompact)
    }
    LaunchedEffect(settings.landscapeMode, settings.tvModeEnabled, isTvDevice) {
        (context as? Activity)?.requestedOrientation = if (settings.landscapeMode || settings.tvModeEnabled || isTvDevice) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }


    DisposableEffect(keepScreenAwake, context) {
        val window = (context as? Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepScreenAwake) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = settings.lightMode
                isAppearanceLightNavigationBars = settings.lightMode
            }
        }
    }

    MaterialTheme(
        colorScheme = if (settings.lightMode) {
            lightColorScheme(primary = accent, onPrimary = Color.Black, primaryContainer = accent, onPrimaryContainer = Color.Black, secondary = accent, onSecondary = Color.Black, background = bg, surface = Color.White, onSurface = Color(0xFF111414))
        } else {
            darkColorScheme(primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White, secondary = accent, onSecondary = Color.White, background = bg, surface = lerp(Color(0xFF151919), Color.Black, settings.surfaceDarkness.coerceIn(0f, 100f) / 100f), onSurface = Color.White)
        }
    ) {
        val dialogHost = remember { SceneDialogHost() }
        val sceneHaze = remember { HazeState() }
        CompositionLocalProvider(
            LocalSurfaceAppearance provides settings,
            LocalSceneDialogHost provides dialogHost,
            LocalBackdropHaze provides sceneHaze
        ) {
            Box(Modifier.fillMaxSize()) {
                // Scene source must be below ALL floating overlays in the same window.
                Box(Modifier.fillMaxSize().haze(sceneHaze)) {
                    Surface(Modifier.fillMaxSize(), color = bg) {
                        Box(Modifier.fillMaxSize()) {
                            AmbientGlowBackground(settings)
                            MainScaffold(vm)
                        }
                    }
                }
                if (shouldShowLocalFolderOnboarding) {
                    LocalMusicFolderOnboardingDialog(
                        onSelect = { localFolderOnboardingLauncher.launch(null) },
                        onSkip = { vm.markLocalFolderPromptDone() }
                    )
                }
                SceneGlassOverlayLayer(dialogHost)
            }
        }
    }
}


@Composable
fun LocalMusicFolderOnboardingDialog(onSelect: () -> Unit, onSkip: () -> Unit) {
    SceneGlassDialog(onDismissRequest = onSkip) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Card(
                Modifier.fillMaxWidth(.96f).widthIn(max = 760.dp).sceneGlassPanel(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = overlayPanelColor())
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Text("Select Local Music Folder", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White)
                    Text(
                        "Choose your local music folder so MusicMonster can build the library after the first start.",
                        color = Color.White.copy(alpha = .74f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Note: You can change this later in Local Library settings and add custom folders such as Podcasts, Soundtracks or other folders.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Skip")
                        }
                        Button(onClick = onSelect, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScaffold(vm: MusicMonsterViewModel) {
    val tab by vm.tab.collectAsState()
    val librarySection by vm.librarySection.collectAsState()
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val fullPlayer by vm.showFullPlayer.collectAsState()
    val showQueue by vm.showQueue.collectAsState()
    val carMode by vm.carMode.collectAsState()
    val androidAutoConnected by vm.androidAutoConnected.collectAsState()
    val androidAutoCompactMode by vm.androidAutoCompactMode.collectAsState()
    val settings by vm.settings.collectAsState()
    val isWeb = tab == MainTab.Library && isWebLibrary(librarySection)
    var navExpandedState by remember(settings.dockStartsCollapsed) { mutableStateOf(!settings.dockStartsCollapsed) }
    var miniPlayerCollapsed by remember { mutableStateOf(false) }
    val navExpanded = if (settings.dockStartsCollapsed) navExpandedState else true

    BackHandler { vm.navigateBack() }

    LaunchedEffect(track?.id) { miniPlayerCollapsed = false }
    LaunchedEffect(navExpandedState, settings.dockStartsCollapsed, settings.dockAutoHideSeconds) {
        if (settings.dockStartsCollapsed && navExpandedState && settings.dockAutoHideSeconds > 0) {
            delay(settings.dockAutoHideSeconds * 1000L)
            navExpandedState = false
        }
    }
    LaunchedEffect(track?.id, navExpandedState, settings.dockStartsCollapsed, settings.miniPlayerAutoHideEnabled, settings.miniPlayerAutoHideSeconds) {
        if (!settings.dockStartsCollapsed || !settings.miniPlayerAutoHideEnabled) {
            miniPlayerCollapsed = false
        } else if (navExpandedState) {
            miniPlayerCollapsed = false
        } else if (track != null && settings.miniPlayerAutoHideSeconds > 0) {
            miniPlayerCollapsed = false
            delay(settings.miniPlayerAutoHideSeconds * 1000L)
            miniPlayerCollapsed = true
        }
    }

    val haze = remember { HazeState() }
    CompositionLocalProvider(LocalBackdropHaze provides haze) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (showQueue && settings.surfaceStyle == "Glass" && Build.VERSION.SDK_INT >= 31) Modifier.blur(22.dp) else Modifier)) {
        Scaffold(
            modifier = Modifier.haze(haze),
            topBar = { TopBar(vm) },
            containerColor = Color.Transparent
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val navDuration = animationDurationMs(260, settings)
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(animationSpec = tween(navDuration)) { it * direction } + fadeIn(animationSpec = tween(navDuration))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(navDuration)) { -it * direction } + fadeOut(animationSpec = tween(navDuration)))
                    },
                    label = "main-navigation"
                ) { activeTab ->
                    when (activeTab) {
                        MainTab.Home -> HomeScreen(vm)
                        MainTab.Library -> LibraryScreen(vm)
                        MainTab.Search -> SearchScreen(vm)
                        MainTab.Playlists -> PlaylistsScreen(vm)
                        MainTab.Settings -> SettingsScreen(vm)
                    }
                }
            }
        }
        if (!isWeb && (!miniPlayerCollapsed || navExpanded || !settings.dockStartsCollapsed)) {
            MiniPlayer(
                track,
                playing,
                vm,
                carMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (navExpanded) 70.dp else 24.dp)
            )
        }
        DockedBottomNavigation(
            selected = tab,
            language = settings.language,
            expanded = navExpanded,
            allowCollapse = settings.dockStartsCollapsed,
            cornerStyle = settings.bottomBarCornerStyle,
            transparency = settings.bottomBarTransparency,
            onExpandedChange = { navExpandedState = it },
            onSelect = { selected -> vm.setTab(selected); if (settings.dockStartsCollapsed) navExpandedState = false },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
        AnimatedVisibility(
            visible = fullPlayer && track != null,
            enter = fadeIn(tween(animationDurationMs(220, settings))) + slideInVertically(tween(animationDurationMs(220, settings))) { it / 4 },
            exit = fadeOut(tween(animationDurationMs(180, settings))) + slideOutVertically(tween(animationDurationMs(180, settings))) { it / 4 }
        ) {
            FullPlayerOverlay(vm, carMode, androidAutoCompactMode)
        }
        } // blurred app content behind the queue overlay
        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn(tween(animationDurationMs(220, settings))) + slideInVertically(tween(animationDurationMs(220, settings))) { it / 3 },
            exit = fadeOut(tween(animationDurationMs(180, settings))) + slideOutVertically(tween(animationDurationMs(180, settings))) { it / 3 }
        ) {
            QueueOverlay(vm)
        }
    }
    } // LocalBackdropHaze
}

@Composable
fun TopBar(vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val track by vm.currentTrack.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }
    if (showEqualizer) EqualizerDialog(vm = vm, onDismiss = { showEqualizer = false })
    val activeTint = if (track != null) MaterialTheme.colorScheme.primary else appIconColor(.82f)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentLogo(Modifier.size(44.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.showQueue(true) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = appIconColor(.82f))
        }
        IconButton(onClick = { vm.showFullPlayer(true) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MusicNote, contentDescription = "Open player", tint = activeTint)
        }
        IconButton(onClick = { showEqualizer = !showEqualizer }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = activeTint)
        }
        TimerActionButton(vm)
        IconButton(onClick = vm::toggleLandscapeMode, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_landscape_display), contentDescription = "Landscape mode", tint = if (settings.landscapeMode) MaterialTheme.colorScheme.primary else appIconColor(.75f))
        }
        IconButton(onClick = { vm.setTab(MainTab.Settings) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = appIconColor(.82f))
        }
    }
}

/** Android 12+ real cross-window blur for Compose Dialog windows.
 *
 * A Compose Dialog creates a separate Window, so the HazeState of the Activity cannot
 * capture pixels from inside that different window. Merely lowering a Card's alpha
 * does not blur the screen behind it. Window.setBackgroundBlurRadius draws a live
 * blur behind the translucent dialog window; FLAG_BLUR_BEHIND also blurs outside it.
 * On devices where the OS disables cross-window blur (battery saving / OEM), a
 * readable dimmed translucent fallback is used. No bitmap snapshots are used.
 */
@Composable
fun SystemBackdropBlur() {
    val settings = LocalSurfaceAppearance.current
    val view = LocalView.current
    DisposableEffect(view, settings.surfaceStyle, settings.surfaceOpacity, settings.lightMode) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val glass = settings.surfaceStyle == "Glass" && Build.VERSION.SDK_INT >= 31
            val blurAllowed = if (Build.VERSION.SDK_INT >= 31) {
                runCatching { window.windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
            } else false
            val useBlur = glass && blurAllowed
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (useBlur) {
                // A non-opaque window background is required for background blur to show.
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.argb(24, 0, 0, 0)
                ))
                window.setBackgroundBlurRadius(86)
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = 48
                attrs.dimAmount = .13f
                window.attributes = attrs
            } else {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT
                ))
                val attrs = window.attributes
                attrs.dimAmount = .48f
                window.attributes = attrs
                if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= 31) {
                window.setBackgroundBlurRadius(0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}

/** Shared readable glass tint. Live pixels are sampled by sceneGlassPanel/Haze;
 * this Color is only the foreground tint, not a blur implementation.
 */
@Composable
fun overlayPanelColor(): Color {
    val settings = LocalSurfaceAppearance.current
    return when (settings.surfaceStyle) {
        "Glass" -> {
            // At 75% transparency the window remains visibly blurred through the panel.
            val tintAlpha = (1f - settings.surfaceOpacity.coerceIn(0f, 100f) / 100f)
                .coerceIn(.28f, .88f)
            // Legal, timer and edit cards use white text in both themes.
            // A visible dark glass tint preserves contrast without flattening the blur.
            surfaceBaseColor(settings).copy(alpha =
                if (Build.VERSION.SDK_INT >= 31) tintAlpha.coerceAtLeast(.50f) else .94f)
        }
        "Balanced" -> surfaceBaseColor(settings).copy(alpha =
            (1f - settings.surfaceOpacity.coerceIn(0f, 100f) / 100f).coerceIn(.12f, 1f))
        else -> surfaceBaseColor(settings)
    }
}

@Composable
fun TimerActionButton(vm: MusicMonsterViewModel) {
    var show by remember { mutableStateOf(false) }
    val deadline by SleepTimerManager.endsAt.collectAsState()
    IconButton(onClick = { show = true }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Rounded.Timer, contentDescription = "Sleep timer", tint = if (show || deadline > 0L) MaterialTheme.colorScheme.primary else appIconColor(.82f))
    }
    if (show) SleepTimerDialog(onDismiss = { show = false })
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val deadline by SleepTimerManager.endsAt.collectAsState()
    var minutes by remember { mutableIntStateOf(30) }
    var customText by remember { mutableStateOf("30") }
    var customOpen by remember { mutableStateOf(false) }
    var allowTimeout by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (deadline > 0L) { now = System.currentTimeMillis(); delay(1000L) }
    }
    SceneGlassDialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(.94f).widthIn(max = 680.dp).sceneGlassPanel(),
            colors = CardDefaults.cardColors(containerColor = overlayPanelColor()), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sleep Timer", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                if (deadline > 0L) {
                    val remainingSeconds = ((deadline - now).coerceAtLeast(0L) + 999L) / 1000L
                    Text("Remaining: ${remainingSeconds / 3600}h ${(remainingSeconds % 3600) / 60}m ${remainingSeconds % 60}s", color = MaterialTheme.colorScheme.primary)
                }
                Text("Pause playback after", color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(10, 15, 30, 45, 60, 90, 120)) { preset ->
                        FilterChip(selected = !customOpen && minutes == preset, onClick = { minutes = preset; customText = preset.toString(); customOpen = false }, label = { Text("${preset} min") })
                    }
                    item { FilterChip(selected = customOpen, onClick = { customOpen = true }, label = { Text("Custom") }) }
                }
                if (customOpen) {
                    OutlinedTextField(value = customText, onValueChange = { input -> customText = input.filter(Char::isDigit).take(4); customText.toIntOrNull()?.let { minutes = it } },
                        label = { Text("Custom minutes (1–1440)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow display timeout", color = Color.White)
                        // The OS handles the actual screen timeout.
                    }
                    Switch(checked = allowTimeout, onCheckedChange = { allowTimeout = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { SleepTimerManager.cancel(context); onDismiss() }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9999)), border = BorderStroke(1.dp, Color(0xFFB64D4D))) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel", maxLines = 1)
                    }
                    Button(onClick = { SleepTimerManager.start(context, minutes, allowTimeout); onDismiss() },
                        enabled = minutes in 1..1440, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerDialog(vm: MusicMonsterViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    SceneGlassDialog(onDismissRequest = onDismiss) {
        Card(
            Modifier
                .fillMaxWidth(if (settings.landscapeMode) .88f else .94f)
                .heightIn(max = 620.dp).sceneGlassPanel(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = if (settings.surfaceStyle == "Glass") overlayPanelColor() else if (isLightThemeActive()) Color.White else if (settings.amoledBlack) Color.Black else Color(0xFF151919))
        ) {
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { EqualizerControls(settings, vm::updateSettings) }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(onClick = onDismiss, shape = RoundedCornerShape(24.dp)) {
                            Icon(Icons.Rounded.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccentLogo(modifier: Modifier = Modifier) {
    Box(modifier.accentGlow().clip(RoundedCornerShape(14.dp)).background(appMuted(.08f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.logo_mark), contentDescription = "Music Monster", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize(.72f))
    }
}


@Composable
fun isLightThemeActive(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.55f

@Composable
fun appText(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
fun appMuted(alpha: Float = .64f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
fun appCardColor(alpha: Float = .06f): Color {
    val settings = LocalSurfaceAppearance.current
    if (isLightThemeActive()) return Color.White
    return when (settings.surfaceStyle) {
        "Solid" -> surfaceBaseColor(settings)
        "Balanced" -> surfaceBaseColor(settings).copy(
            alpha = (1f - settings.surfaceOpacity.coerceIn(0f, 100f) / 100f).coerceIn(.12f, 1f))
        else -> {
            // Keep established Glass card appearance at 0%; add darker tint gradually.
            val strength = settings.surfaceDarkness.coerceIn(0f, 100f) / 100f
            lerp(Color.White.copy(alpha),
                Color.Black.copy(alpha = (1f - settings.surfaceOpacity / 100f).coerceIn(.16f, .9f)), strength)
        }
    }
}

/** In-scene overlays: Haze can sample the actual app only when the overlay and
 * its source live in the SAME Android window. Android Dialog creates a second window,
 * so merely changing Card alpha or Window blur does not blur inside a card.
 * SceneGlassDialog registers a composable in this activity-level overlay host.
 */
private class SceneDialogEntry(
    val id: Any,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit
)

private class SceneDialogHost {
    val entries = mutableStateListOf<SceneDialogEntry>()
}

private val LocalSceneDialogHost = staticCompositionLocalOf<SceneDialogHost?> { null }

@Composable
fun SceneGlassDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val host = LocalSceneDialogHost.current
    if (host == null) {
        // Only used outside the activity overlay host; the actual app uses in-scene glass.
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SystemBackdropBlur()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    } else {
        val id = remember { Any() }
        val currentDismiss = rememberUpdatedState(onDismissRequest)
        val currentContent = rememberUpdatedState(content)
        DisposableEffect(host, id) {
            val entry = SceneDialogEntry(id, { currentDismiss.value() }, { currentContent.value() })
            host.entries.add(entry)
            onDispose { host.entries.removeAll { it.id === id } }
        }
    }
}

@Composable
private fun SceneGlassOverlayLayer(host: SceneDialogHost) {
    val entry = host.entries.lastOrNull()
    BackHandler(enabled = entry != null) { entry?.onDismiss() }
    val settings = LocalSurfaceAppearance.current
    val openMs = animationDurationMs(240, settings)
    val closeMs = animationDurationMs(180, settings)
    // Animate the in-window overlay itself; previously content appeared immediately
    // when the host list changed. AnimatedContent retains the outgoing entry for exit.
    AnimatedContent(
        targetState = entry,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(openMs)) + scaleIn(initialScale = .96f, animationSpec = tween(openMs))) togetherWith
                (fadeOut(tween(closeMs)) + scaleOut(targetScale = .98f, animationSpec = tween(closeMs)))
        },
        label = "MusicMonster glass overlay"
    ) { showing ->
        if (showing != null) {
            val noRipple = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = if (settings.surfaceStyle == "Glass") .28f else .60f))
                    .clickable(interactionSource = noRipple, indication = null) { showing.onDismiss() })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { showing.content() }
            }
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/** Base for all three surface styles: original dark slate at 0%, true black at 100%. */
@Composable
fun surfaceBaseColor(settings: AppSettings = LocalSurfaceAppearance.current): Color =
    lerp(Color(0xFF151919), Color.Black, settings.surfaceDarkness.coerceIn(0f, 100f) / 100f)

/** The border is controlled in Appearance, not baked into every glass panel. */
@Composable
fun Modifier.sceneGlassPanel(shape: RoundedCornerShape = RoundedCornerShape(28.dp)): Modifier {
    val settings = LocalSurfaceAppearance.current
    val base = this.clip(shape).then(if (settings.surfaceStyle == "Glass") Modifier.musicGlassSurface() else Modifier)
    if (!settings.surfaceBordersEnabled || settings.surfaceBorderTransparency >= 100f) return base
    val borderColor = if (settings.surfaceBorderUseAccentColor) MaterialTheme.colorScheme.primary
        else if (isLightThemeActive()) Color(0xFF647381) else Color.White
    return base.border(1.dp, borderColor.copy(alpha = 1f - settings.surfaceBorderTransparency.coerceIn(0f, 100f) / 100f), shape)
}

val LocalSurfaceAppearance = staticCompositionLocalOf { AppSettings() }
val LocalBackdropHaze = staticCompositionLocalOf<HazeState?> { null }

/** Real scene-backed blur on Android 12+; safe translucent fallback otherwise. */
@Composable
fun Modifier.musicGlassSurface(): Modifier {
    val settings = LocalSurfaceAppearance.current
    val haze = LocalBackdropHaze.current
    return if (settings.surfaceStyle == "Glass" && haze != null && Build.VERSION.SDK_INT >= 31) {
        this.hazeChild(
            state = haze,
            style = HazeStyle(
                backgroundColor = if (isLightThemeActive()) Color(0xFFF5F7F2) else surfaceBaseColor(settings),
                blurRadius = 26.dp,
                tint = HazeTint(
                    (if (isLightThemeActive()) Color.White else surfaceBaseColor(settings))
                        .copy(alpha = (1f - settings.surfaceOpacity.coerceIn(0f, 100f) / 100f).coerceIn(.16f, .88f))
                )
            )
        ) { blurEnabled = Build.VERSION.SDK_INT >= 31 }
    } else this
}

@Composable
fun appNavColor(transparency: Float = 0f): Color {
    val settings = LocalSurfaceAppearance.current
    val alpha = when (settings.surfaceStyle) {
        "Solid" -> 1f
        "Balanced" -> (1f - transparency.coerceIn(0f, 100f) / 100f) *
            (1f - settings.surfaceOpacity.coerceIn(0f, 100f) / 100f)
        else -> (1f - transparency.coerceIn(0f, 100f) / 100f) * (settings.surfaceOpacity.coerceIn(0f, 100f) / 100f)
    }.coerceIn(.08f, 1f)
    return (if (isLightThemeActive()) Color.White else surfaceBaseColor(settings)).copy(alpha = alpha)
}

@Composable
fun appIconColor(alpha: Float = .82f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
fun AmbientGlowBackground(settings: AppSettings) {
    if (!settings.ambientGlowEnabled) return
    val glowColor = if (settings.ambientGlowUseCustomColor) Color(settings.ambientGlowColor) else MaterialTheme.colorScheme.primary
    val strength = settings.ambientGlowIntensity.coerceIn(0f, 100f) / 100f
    if (strength <= 0f) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = .34f * strength), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 1050f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = .24f * strength), Color.Transparent),
                        center = Offset(2900f, 2300f),
                        radius = 1250f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(glowColor.copy(alpha = .07f * strength), Color.Transparent, glowColor.copy(alpha = .05f * strength))
                    )
                )
        )
    }
}

fun Modifier.subtleGlow(enabled: Boolean, intensity: Float, color: Color): Modifier {
    if (!enabled || intensity <= 0f) return this
    val elevation = (intensity.coerceIn(0f, 100f) / 100f) * 18f
    return this.graphicsLayer {
        shadowElevation = elevation
        ambientShadowColor = color.copy(alpha = .72f)
        spotShadowColor = color.copy(alpha = .72f)
    }
}

@Composable
fun Modifier.accentGlow(): Modifier = this

fun animationSpeedLabel(value: Float): String {
    val stepped = (value.coerceIn(0.25f, 2.0f) * 4f).toInt() / 4f
    return if (stepped == 0.25f) "0.25x" else String.format(Locale.US, "%.1fx", stepped)
}

fun animationDurationMs(baseMs: Int, settings: AppSettings): Int {
    if (!settings.smoothAnimationsEnabled) return 0
    return (baseMs / settings.animationSpeed.coerceIn(0.25f, 2.0f)).toInt().coerceIn(80, 900)
}

fun landscapeScale(settings: AppSettings): Float {
    if (settings.tvModeEnabled) {
        val presetScale = when (settings.tvScreenPreset) {
            "32" -> 0.92f
            "43" -> 1.0f
            "50" -> 1.08f
            "65" -> 1.18f
            "75" -> 1.28f
            else -> 1.0f
        }
        return (presetScale * settings.tvUiScale).coerceIn(0.75f, 1.85f)
    }
    val presetScale = when (settings.landscapePreset) {
        "10" -> 0.72f
        "8" -> 0.82f
        "7" -> 0.92f
        else -> 1.0f
    }
    return (presetScale * settings.uiScale).coerceIn(0.65f, 1.25f)
}

@Composable
fun BottomNav(selected: MainTab, language: String, navTransparency: Float = 0f, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = if (LocalSurfaceAppearance.current.surfaceStyle == "Glass" && Build.VERSION.SDK_INT >= 32) Color.Transparent else appNavColor(navTransparency)) {
        MainTab.values().forEach { t ->
            val label = if (t == MainTab.Library && language == "en") "Library" else tr(language, t.name)
            NavigationBarItem(
                selected = selected == t,
                onClick = { onSelect(t) },
                icon = { Icon(iconFor(t), null) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, lineHeight = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = appIconColor(.68f),
                    unselectedTextColor = appMuted(.68f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(.16f)
                )
            )
        }
    }
}


@Composable
fun DockedBottomNavigation(
    selected: MainTab,
    language: String,
    expanded: Boolean,
    allowCollapse: Boolean,
    cornerStyle: String = "Compact",
    transparency: Float = 0f,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val height = if (expanded) 72.dp else 18.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(expanded, allowCollapse) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (allowCollapse && dragAmount < -8f) onExpandedChange(true)
                    if (allowCollapse && dragAmount > 8f) onExpandedChange(false)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (expanded) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(bottomBarShape(cornerStyle))
                    .musicGlassSurface(),
                shape = bottomBarShape(cornerStyle),
                colors = CardDefaults.cardColors(containerColor = if (LocalSurfaceAppearance.current.surfaceStyle == "Glass" && Build.VERSION.SDK_INT >= 31) Color.Transparent else appNavColor(transparency))
            ) {
                BottomNav(selected, language, transparency, onSelect)
            }
        } else {
            Box(
                Modifier
                    .width(108.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(.55f))
                    .clickable(enabled = allowCollapse) { onExpandedChange(true) }
            )
        }
    }
}

@Composable
fun bottomBarShape(style: String): RoundedCornerShape = when (style) {
    "Round" -> RoundedCornerShape(30.dp)
    "Soft" -> RoundedCornerShape(22.dp)
    else -> RoundedCornerShape(16.dp)
}

fun iconFor(t: MainTab) = when (t) {
    MainTab.Home -> Icons.Rounded.Home
    MainTab.Library -> Icons.Rounded.LibraryMusic
    MainTab.Search -> Icons.Rounded.Search
    MainTab.Playlists -> Icons.Rounded.QueueMusic
    MainTab.Settings -> Icons.Rounded.Settings
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    track: Track?,
    playing: Boolean,
    vm: MusicMonsterViewModel,
    carMode: Boolean,
    modifier: Modifier = Modifier,
    onMinimize: () -> Unit = {}
) {
    if (track == null) return

    val settings by vm.settings.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val favorite = vm.isFavorite(track)
    val landscape = settings.landscapeMode || settings.tvModeEnabled
    val scale = if (landscape) landscapeScale(settings).coerceIn(.78f, 1.15f) else 1f
    val shape = bottomBarShape(settings.playerControlsCornerStyle)
    val container = appNavColor(settings.playerControlsTransparency)
    val rightActionSize = if (landscape) (36f * scale).dp else 32.dp
    val rightIconSize = if (landscape) (20f * scale).dp else 18.dp
    val textWidth = if (landscape) (280f * scale).dp else 122.dp

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height((74f * scale).dp)
    ) {
        Card(
            Modifier.fillMaxSize().clip(shape).musicGlassSurface().clickable { vm.showFullPlayer(true) },
            colors = CardDefaults.cardColors(containerColor = if (settings.surfaceStyle == "Glass" && Build.VERSION.SDK_INT >= 31) Color.Transparent else container),
            shape = shape
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = (12f * scale).dp, vertical = (8f * scale).dp)) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(textWidth),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (landscape) {
                        TrackArtwork(track.artworkUri, Modifier.size((50f * scale).dp), albumPlaceholder = true)
                        Spacer(Modifier.width((10f * scale).dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontWeight = FontWeight.Bold,
                            fontSize = (14f * scale).sp,
                            color = appText()
                        )
                        Text(
                            "${track.artist} • ${track.album}",
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = appMuted(.66f),
                            fontSize = (11.5f * scale).sp
                        )
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy((2f * scale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::previous, modifier = Modifier.size((40f * scale).dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = appIconColor(), modifier = Modifier.size((26f * scale).dp))
                    }
                    FilledIconButton(
                        onClick = vm::toggle,
                        shape = CircleShape,
                        modifier = Modifier.size((52f * scale).dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", tint = Color.White, modifier = Modifier.size((30f * scale).dp))
                    }
                    IconButton(onClick = vm::next, modifier = Modifier.size((40f * scale).dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = appIconColor(), modifier = Modifier.size((26f * scale).dp))
                    }
                }

                Row(
                    Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(if (landscape) (0f * scale).dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::toggleShuffle, modifier = Modifier.size(rightActionSize)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size(rightIconSize))
                    }
                    IconButton(onClick = vm::cycleRepeatMode, modifier = Modifier.size(rightActionSize)) {
                        Icon(if (repeat == RepeatModeUi.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (repeat != RepeatModeUi.Off) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size(rightIconSize))
                    }
                    IconButton(onClick = { vm.toggleFavorite(track) }, modifier = Modifier.size(rightActionSize)) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size(rightIconSize))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: MusicMonsterViewModel) {
    val section by vm.homeSection.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val sections = homeSections(settings)
    val sectionIndex = sections.indexOf(section).coerceAtLeast(0)
    LaunchedEffect(sections, section) { if (section !in sections) vm.setHome(sections.first()) }
    var previousIndex by remember { mutableIntStateOf(sectionIndex) }
    val albums = remember(tracks) { vm.homeAlbums(10) }
    val artists = remember(tracks) { vm.homeArtists(10) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .swipeHorizontal(
                onNext = { moveHomeSection(vm, sections, section, 1) },
                onPrevious = { moveHomeSection(vm, sections, section, -1) }
            )
    ) {
        ChipRow(sections.map { homeLabel(it, settings.language) }, homeLabel(section, settings.language)) { label -> sections.firstOrNull { homeLabel(it, settings.language) == label }?.let(vm::setHome) }
        Spacer(Modifier.height(12.dp))
        val duration = animationDurationMs(240, settings)
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val target = sections.indexOf(targetState).coerceAtLeast(0)
                val origin = sections.indexOf(initialState).coerceAtLeast(0)
                val direction = if (target >= origin) 1 else -1
                (slideInHorizontally(animationSpec = tween(duration)) { it * direction } + fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(duration)) { -it * direction } + fadeOut(tween(duration)))
            },
            label = "home-section"
        ) { activeSection ->
            LazyColumn(Modifier.fillMaxSize().verticalEdgeFade(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (activeSection) {
                    HomeSection.Home -> {
                        item { AlbumStrip(tr(settings.language, "Albums"), albums, vm) }
                        item { ArtistStrip(tr(settings.language, "Artists"), artists, vm) }
                        item { TrackSection("Custom Picks", vm.homePicks(10), vm) }
                        item { PlaylistSection(tr(settings.language, "Playlists"), playlists) }
                    }
                    HomeSection.Discovery -> item { Discovery(vm, tracks) }
                    HomeSection.Favorites -> item { TrackSection("Favorite Tracks", tracks.filter { vm.isFavorite(it) }, vm) }
                    HomeSection.Top -> item { TrackSection("Top Tracks", tracks.sortedByDescending { it.playCount }.ifEmpty { tracks.take(10) }, vm) }
                    HomeSection.History -> item { TrackSection("History", tracks.sortedByDescending { it.lastPlayed }.ifEmpty { tracks.take(10) }, vm) }
                }
            }
        }
    }
}

@Composable
fun Discovery(vm: MusicMonsterViewModel, tracks: List<Track>) {
    val randomAlbums = remember(tracks) { vm.randomAlbums(8) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TrackSection("Random Tracks", vm.randomTracks(12), vm)
        AlbumStrip("Try Something Different", randomAlbums, vm)
        TrackSection("Rediscover", tracks.asReversed().take(12), vm)
    }
}

/** Determine visible descendants from provider document IDs when the provider exposes
 * hierarchical paths. MediaStore numeric IDs and opaque SMB IDs cannot be reversed to
 * paths; for those sources only the already indexed selected group is actionable.
 */
private fun indexedFolderAndDescendants(selected: List<Track>, library: List<Track>): List<Track> {
    fun parentPath(track: Track): String? {
        val documentId = track.uri.lastPathSegment?.replace('\\', '/') ?: return null
        if (!documentId.contains('/')) return null
        return documentId.substringBeforeLast('/').trimEnd('/').lowercase(Locale.ROOT)
    }
    val roots = selected.mapNotNull(::parentPath).toSet()
    if (roots.isEmpty()) return selected
    val indexed = library.filter { track ->
        val parent = parentPath(track)
        track in selected || (parent != null && roots.any { root -> parent == root || parent.startsWith("$root/") })
    }
    return indexed.distinctBy { it.uri.toString() }
}

@Composable
fun LibraryScreen(vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val section by vm.librarySection.collectAsState()
    val detail by vm.libraryDetail.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val podcasts by vm.podcasts.collectAsState()
    val sections = librarySections(settings)
    val sectionIndex = sections.indexOf(section).coerceAtLeast(0)
    LaunchedEffect(sections, section) { if (section !in sections) vm.setLibrary(sections.first()) }
    var previousIndex by remember { mutableIntStateOf(sectionIndex) }
    var selectedFolderGroup by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    selectedFolderGroup?.let { (name, group) ->
        FolderContextActionsDialog(name, group, vm) { selectedFolderGroup = null }
    }

    BackHandler(enabled = detail != null) { vm.closeDetail() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .swipeHorizontal(
                onNext = { if (detail == null) moveLibrarySection(vm, sections, section, 1) },
                onPrevious = { if (detail == null) moveLibrarySection(vm, sections, section, -1) }
            )
    ) {
        if (detail == null) {
            ChipRow(sections.map { libraryLabel(it, settings) }, libraryLabel(section, settings)) { selectedLabel ->
                val selected = sections.firstOrNull { libraryLabel(it, settings) == selectedLabel } ?: LibrarySection.Albums
                vm.setLibrary(selected)
            }
            Spacer(Modifier.height(12.dp))
        }
        AnimatedContent(
            modifier = Modifier.weight(1f),
            targetState = detail,
            transitionSpec = {
                val ms = animationDurationMs(230, settings)
                if (targetState != null) {
                    (slideInHorizontally(tween(ms)) { it / 4 } + fadeIn(tween(ms))) togetherWith
                        (slideOutHorizontally(tween(ms)) { -it / 5 } + fadeOut(tween(ms)))
                } else {
                    (slideInHorizontally(tween(ms)) { -it / 4 } + fadeIn(tween(ms))) togetherWith
                        (slideOutHorizontally(tween(ms)) { it / 5 } + fadeOut(tween(ms)))
                }
            }, label = "library-detail"
        ) { visibleDetail ->
        if (visibleDetail != null) {
            LibraryDetailScreen(visibleDetail, vm)
        } else {
            val duration = animationDurationMs(240, settings)
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val target = sections.indexOf(targetState).coerceAtLeast(0)
                    val origin = sections.indexOf(initialState).coerceAtLeast(0)
                    val direction = if (target >= origin) 1 else -1
                    (slideInHorizontally(animationSpec = tween(duration)) { it * direction } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(duration)) { -it * direction } + fadeOut(tween(duration)))
                },
                label = "library-section"
            ) { activeSection ->
                when (activeSection) {
                    LibrarySection.Tracks -> TrackList(tracks, vm)
                    LibrarySection.Albums -> AlbumGrid(vm.albums(), vm)
                    LibrarySection.Artists -> GroupedList(vm.artists().associate { it.name to it.tracks }, icon = Icons.Rounded.KeyboardArrowDown) { name, list -> vm.openGroup(name, list) }
                    LibrarySection.Genres -> GroupedList(tracks.groupBy { it.genre.ifBlank { "Unknown" } }.toSortedMap(), icon = Icons.Rounded.KeyboardArrowDown) { name, list -> vm.openGroup(name, list) }
                    LibrarySection.Folders -> GroupedList(tracks.groupBy { it.folder.ifBlank { "Music" } }.toSortedMap(), icon = Icons.Rounded.Folder, highlightIcon = true,
                        onLongPress = { name, list -> selectedFolderGroup = name to indexedFolderAndDescendants(list, tracks) }) { name, list -> vm.openGroup(name, list) }
                    LibrarySection.Podcasts -> PodcastScreen(podcasts, vm)
                    LibrarySection.OST -> PodcastScreen(tracks.filter { it.isOst }, vm, title = "OST", emptyMessage = "Set an OST folder in Settings", folderLabel = "OST folders", singlesLabel = "Single soundtracks")
                    LibrarySection.SoundCloud -> WebLibraryScreen("SoundCloud", "https://soundcloud.com", Modifier.fillMaxSize())
                    LibrarySection.Spotify -> WebLibraryScreen("Spotify", "https://open.spotify.com", Modifier.fillMaxSize())
                    LibrarySection.YouTubeMusic -> WebLibraryScreen("YouTube Music", "https://music.youtube.com", Modifier.fillMaxSize())
                    LibrarySection.YouTube -> WebLibraryScreen("YouTube", "https://www.youtube.com", Modifier.fillMaxSize())
                    LibrarySection.CustomWeb -> WebLibraryScreen(settings.customWebName.ifBlank { "Custom Web Library" }, settings.customWebUrl.ifBlank { "https://example.com" }, Modifier.fillMaxSize())
                }
            }
        }
        }
    }
}

@Composable
fun LibraryDetailScreen(detail: LibraryDetail, vm: MusicMonsterViewModel) {
    val current by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val detailIsPlaying = playing && current?.let { active -> detail.tracks.any { it.id == active.id } } == true
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeDetail) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(detail.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail.subtitle, color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            FilledIconButton(onClick = { if (detailIsPlaying) vm.toggle() else vm.play(detail.tracks, 0) }, shape = CircleShape) {
                Icon(if (detailIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (detailIsPlaying) "Pause" else "Play all")
            }
        }
        Spacer(Modifier.height(12.dp))
        TrackList(detail.tracks, vm)
    }
}

@Composable
fun EmptyLibraryShortcut(
    title: String,
    actionText: String,
    body: String,
    onOpenSettings: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = appText())
            Text(
                actionText,
                modifier = Modifier.clickable(onClick = onOpenSettings),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(body, color = appMuted(.68f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PodcastScreen(podcasts: List<Track>, vm: MusicMonsterViewModel, title: String = "Podcasts", emptyMessage: String = "Select a podcast folder in Settings", folderLabel: String = "Podcast folders", singlesLabel: String = "Single episodes") {
    val current by vm.currentTrack.collectAsState()
    if (podcasts.isEmpty()) {
        EmptyLibraryShortcut(
            title = title,
            actionText = emptyMessage,
            body = "Local audio files appear here after scanning the selected folder.",
            onOpenSettings = vm::openLocalLibrarySettings
        )
        return
    }
    val grouped = podcasts.groupBy { it.folder.ifBlank { "Podcasts" } }.toSortedMap()
    val folderGroups = grouped.filter { it.value.size > 1 }
    val singles = grouped.filter { it.value.size == 1 }.flatMap { it.value }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (folderGroups.isNotEmpty()) {
            item { Text(folderLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            items(folderGroups.toList()) { (folder, tracks) ->
                Card(Modifier.fillMaxWidth().clickable { vm.openGroup(folder, tracks) }, colors = CardDefaults.cardColors(containerColor = appCardColor(.05f)), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(tracks.firstOrNull { it.artworkUri != null }?.artworkUri, Modifier.size(58.dp), albumPlaceholder = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${tracks.size} episodes", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (singles.isNotEmpty()) {
            item { Text(singlesLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            items(singles.indices.toList()) { index ->
                val track = singles[index]
                TrackRow(track, vm.isFavorite(track), { vm.toggleFavorite(track) }, { vm.play(singles, index) }, showArtwork = true, containerColor = activeTrackContainer(track, current)) {
                    CompactIconButton(onClick = { vm.addToQueue(track) }) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to queue", tint = appMuted(.66f), modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }
}


@Composable
fun SearchScreen(vm: MusicMonsterViewModel) {
    val q by vm.query.collectAsState()
    val tracks by vm.filteredTracks.collectAsState()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = vm::search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search tracks, albums or artists") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (q.isBlank()) {
            val recent = tracks.filter { it.lastPlayed > 0L }.sortedByDescending { it.lastPlayed }.take(30)
            Text("Recently searched songs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (recent.isEmpty()) Text("No recent searches yet.", color = appMuted(.62f)) else TrackList(recent, vm)
        } else {
            val settings by vm.settings.collectAsState()
            val currentTrack by vm.currentTrack.collectAsState()
            val favoriteIds = remember(settings.favoriteIds) { settings.favoriteIds.split(',').mapNotNull { it.toLongOrNull() }.toSet() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks.indices.toList(), key = { tracks[it].id.toString() + "-search-$it" }) { index ->
                    val track = tracks[index]
                    TrackRow(
                        track = track,
                        favorite = favoriteIds.contains(track.id),
                        onFavorite = { vm.toggleFavorite(track) },
                        onClick = { vm.play(tracks, index); vm.search("") },
                        showArtwork = true,
                        containerColor = activeTrackContainer(track, currentTrack),
                        trailing = {
                            CompactIconButton(onClick = { vm.addToQueue(track) }) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to queue", tint = appIconColor(.66f), modifier = Modifier.size(22.dp)) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(vm: MusicMonsterViewModel) {
    val playlists by vm.playlists.collectAsState()
    val selectedIndex by vm.selectedPlaylistIndex.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var exportTarget by remember { mutableStateOf<Playlist?>(null) }
    var exportFormat by remember { mutableStateOf("M3U") }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedSmartName by remember { mutableStateOf<String?>(null) }
    val smartPlaylists = remember(tracks, settings.favoriteIds) { vm.smartPlaylists() }
    val exportM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        uri?.let { target ->
            exportTarget?.let { playlist -> context.contentResolver.openOutputStream(target)?.use { it.write(vm.exportPlaylist(playlist, exportFormat).toByteArray()) } }
        }
    }

    selectedSmartName?.let { name ->
        val smartPlaylist = smartPlaylists.firstOrNull { it.name == name }
        if (smartPlaylist != null) {
            SmartPlaylistDetailScreen(smartPlaylist, vm) { selectedSmartName = null }
            return
        } else {
            selectedSmartName = null
        }
    }

    if (selectedIndex != null && selectedIndex in playlists.indices) {
        PlaylistDetailScreen(selectedIndex!!, playlists[selectedIndex!!], vm) { playlist ->
            exportTarget = playlist
            exportM3uLauncher.launch("${playlist.name}.${exportFormat.lowercase()}")
        }
        return
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Playlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Smart Playlists", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        items(smartPlaylists) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onOpen = { selectedSmartName = playlist.name },
                onPlay = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) },
                readonly = true
            )
        }
        item {
            Text("Create Playlist", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(newPlaylistName, { newPlaylistName = it }, Modifier.fillMaxWidth(), label = { Text("New playlist name") }, shape = RoundedCornerShape(18.dp))
                    Text("Export format", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
                    ChipRow(listOf("M3U", "M3U8", "PLS"), exportFormat) { exportFormat = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.createPlaylist(newPlaylistName.ifBlank { "New Playlist" }); newPlaylistName = "" }, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) { Text("Create new") }
                        OutlinedButton(onClick = vm::createPlaylistFromLibrary, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) { Text("From library") }
                    }
                }
            }
        }
        item { Text("Your Playlists", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        items(playlists.indices.toList()) { index ->
            val playlist = playlists[index]
            PlaylistCard(
                playlist = playlist,
                onOpen = { vm.openPlaylist(index) },
                onPlay = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) },
                onExport = { exportTarget = playlist; exportM3uLauncher.launch("${playlist.name}.${exportFormat.lowercase()}") },
                onMoveUp = if (index > 0) ({ vm.movePlaylist(index, index - 1) }) else null,
                onMoveDown = if (index < playlists.lastIndex) ({ vm.movePlaylist(index, index + 1) }) else null
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onExport: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    readonly: Boolean = false
) {
    Card(Modifier.clickable { onOpen() }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = appCardColor(.05f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (readonly) Icons.Rounded.Star else Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = appMuted(.65f), style = MaterialTheme.typography.labelMedium)
            }
            if (onMoveUp != null) IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(19.dp)) }
            if (onMoveDown != null) IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(19.dp)) }
            if (onExport != null) IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.FileUpload, contentDescription = "Export", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onPlay, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
fun SmartPlaylistDetailScreen(playlist: Playlist, vm: MusicMonsterViewModel, onBack: () -> Unit) {
    val current by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val playlistIsPlaying = playing && current?.let { active -> playlist.tracks.any { it.id == active.id } } == true
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { if (playlist.tracks.isNotEmpty()) { if (playlistIsPlaying) vm.toggle() else vm.play(playlist.tracks, 0) } }) { Icon(if (playlistIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playlistIsPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(12.dp))
        TrackList(
            tracks = playlist.tracks,
            vm = vm,
            showQueueButton = true,
            showArtwork = false,
            compactActions = true,
            durationBelow = true
        )
    }
}


@Composable
fun PlaylistDetailScreen(index: Int, playlist: Playlist, vm: MusicMonsterViewModel, onExport: (Playlist) -> Unit) {
    var renameText by remember(playlist.name) { mutableStateOf(playlist.name) }
    val current by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val playlistIsPlaying = playing && current?.let { active -> playlist.tracks.any { it.id == active.id } } == true
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closePlaylist) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { if (playlist.tracks.isNotEmpty()) { if (playlistIsPlaying) vm.toggle() else vm.play(playlist.tracks, 0) } }) { Icon(if (playlistIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playlistIsPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = { onExport(playlist) }) { Icon(Icons.Rounded.FileUpload, contentDescription = "Export") }
            IconButton(onClick = { vm.deletePlaylist(index) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = appMuted(.74f)) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(renameText, { renameText = it }, Modifier.weight(1f), label = { Text("Playlist name") }, shape = RoundedCornerShape(18.dp), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.renamePlaylist(index, renameText) }, shape = RoundedCornerShape(18.dp)) { Text("Save") }
            }
        }
        Spacer(Modifier.height(12.dp))
        TrackList(
            tracks = playlist.tracks,
            vm = vm,
            showQueueButton = false,
            showRemoveButton = true,
            showArtwork = false,
            compactActions = true,
            durationBelow = true,
            onRemove = { trackIndex -> vm.removeTrackFromPlaylist(index, trackIndex) },
            onMoveUp = { trackIndex -> vm.movePlaylistTrack(index, trackIndex, trackIndex - 1) },
            onMoveDown = { trackIndex -> vm.movePlaylistTrack(index, trackIndex, trackIndex + 1) }
        )
    }
}

@Composable
fun SurfaceStyleSettingsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Card style", fontWeight = FontWeight.SemiBold)
            ChipRow(listOf("Solid", "Balanced", "Glass"), settings.surfaceStyle) { onUpdate(settings.copy(surfaceStyle = it)) }
            Text("Transparency: ${settings.surfaceOpacity.toInt()}%", color = appMuted(.8f))
            Slider(value = settings.surfaceOpacity, onValueChange = { onUpdate(settings.copy(surfaceOpacity = it)) }, valueRange = 0f..100f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 25, 50, 75, 100).forEach { mark ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(4.dp).background(appMuted(.5f), CircleShape))
                        Text("$mark", color = appMuted(.56f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Background intensity: ${settings.surfaceDarkness.toInt()}%", color = appMuted(.8f))
            Slider(
                value = settings.surfaceDarkness,
                onValueChange = { onUpdate(settings.copy(surfaceDarkness = it)) },
                valueRange = 0f..100f
            )
        }
    }
}

@Composable
fun SurfaceBorderSettingsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Card borders", fontWeight = FontWeight.SemiBold)
            SettingsSwitchInline("Enable borders", settings.surfaceBordersEnabled) {
                onUpdate(settings.copy(surfaceBordersEnabled = it))
            }
            Column(Modifier.graphicsLayer(alpha = if (settings.surfaceBordersEnabled) 1f else .40f)) {
                Text("Border transparency: ${settings.surfaceBorderTransparency.toInt()}%", color = appMuted(.8f))
                Slider(value = settings.surfaceBorderTransparency,
                    onValueChange = { onUpdate(settings.copy(surfaceBorderTransparency = it)) },
                    valueRange = 0f..100f, enabled = settings.surfaceBordersEnabled)
                SettingsSwitchInline("Use accent color", settings.surfaceBorderUseAccentColor) {
                    onUpdate(settings.copy(surfaceBorderUseAccentColor = it))
                }
            }
        }
    }
}

@Composable
fun LibraryVisibilitySettingsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Home sections", fontWeight = FontWeight.SemiBold)
            HomeSection.entries.forEach { section ->
                val hidden = settings.hiddenHomeSections.split(',').filter(String::isNotBlank).toSet()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.name, Modifier.weight(1f))
                    Switch(checked = section.name !in hidden, onCheckedChange = { visible ->
                        val next = if (visible) hidden - section.name else hidden + section.name
                        onUpdate(settings.copy(hiddenHomeSections = next.joinToString(",")))
                    })
                }
            }
            HorizontalDivider()
            Text("Library sections", fontWeight = FontWeight.SemiBold)
            listOf(LibrarySection.Albums, LibrarySection.Tracks, LibrarySection.Folders, LibrarySection.Genres,
                LibrarySection.Artists, LibrarySection.Podcasts, LibrarySection.OST).forEach { section ->
                val hidden = settings.hiddenLibrarySections.split(',').filter(String::isNotBlank).toSet()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.name, Modifier.weight(1f))
                    Switch(checked = section.name !in hidden, onCheckedChange = { visible ->
                        val next = if (visible) hidden - section.name else hidden + section.name
                        onUpdate(settings.copy(hiddenLibrarySections = next.joinToString(",")))
                    })
                }
            }
        }
    }
}

@Composable
fun QueueOverlay(vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val queue by vm.queue.collectAsState()
    val queueIndex by vm.queueIndex.collectAsState()
    BackHandler { vm.showQueue(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(
        alpha = if (settings.surfaceStyle == "Solid") 1f else (settings.surfaceOpacity / 100f).coerceIn(.55f, 1f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showQueue(false) }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close queue") }
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${queue.size} tracks", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (queue.isEmpty()) {
                HeroCard("Queue", "No tracks in queue", "Start playback from an album, folder, playlist or track list.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp), modifier = Modifier.fillMaxSize().verticalEdgeFade()) {
                    items(queue.indices.toList()) { index ->
                        val track = queue[index]
                        TrackRow(
                            track = track,
                            favorite = vm.isFavorite(track),
                            onFavorite = { vm.toggleFavorite(track) },
                            onClick = { vm.playQueueItem(index) },
                            showArtwork = false,
                            compactActions = true,
                            durationBelow = true,
                            containerColor = if (index == queueIndex) MaterialTheme.colorScheme.primary.copy(.24f) else null,
                            trailing = {
                                CompactIconButton(enabled = index > 0, onClick = { vm.moveQueueItem(index, index - 1) }, compact = true) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp)) }
                                CompactIconButton(enabled = index < queue.lastIndex, onClick = { vm.moveQueueItem(index, index + 1) }, compact = true) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp)) }
                                CompactIconButton(onClick = { vm.removeFromQueue(index) }, compact = true) { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp)) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val settingsFocus by vm.settingsFocus.collectAsState()
    val networkStatus by vm.networkLibraryStatus.collectAsState()
    val settingsListState = rememberLazyListState()
    val context = LocalContext.current
    var webUrl by remember(settings.customWebUrl) { mutableStateOf(settings.customWebUrl) }
    var webName by remember(settings.customWebName) { mutableStateOf(settings.customWebName) }
    var customColor by remember(settings.accentColor) { mutableStateOf("#" + settings.accentColor.toString(16).takeLast(6).uppercase()) }
    var playlistName by remember { mutableStateOf("") }

    val musicFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.onFailure { _ ->
                runCatching { context.contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            vm.setMusicFolder(it)
        }
    }
    val podcastFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setPodcastFolder(it)
        }
    }
    val ostFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setOstFolder(it)
        }
    }
    val customFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setCustomFolder(it)
        }
    }
    val networkLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setNetworkLibrary(it)
        }
    }
    val exportSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.musicmonster+json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(vm.exportSettings().toByteArray()) } }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> vm.importSettings(reader.readText()) } }
    }
    val importM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = it.lastPathSegment ?: "Imported Playlist.m3u"
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            vm.importM3u(name, text)
        }
    }
    val exportAllPlaylistsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(vm.exportAllPlaylistsZip()) } }
    }
    val exportFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.musicmonster+json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(vm.exportFavoritesJson().toByteArray()) } }
    }
    val importFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> vm.importFavoritesJson(reader.readText()) } }
    }
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings(settings.copy(visualizerEnabled = true))
        else vm.updateSettings(settings.copy(visualizerEnabled = false))
    }

    LaunchedEffect(settingsFocus) {
        if (settingsFocus == "LocalLibrary") {
            settingsListState.animateScrollToItem(22)
            vm.consumeSettingsFocus()
        }
    }

    LazyColumn(
        state = settingsListState,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SettingsHeader() }

        item { SettingsSectionTitle("Local Library") }
        item {
            LocalLibraryActionsCard(
                onScanDevice = vm::scanDeviceLibrary,
                onRescanLibrary = vm::scanLibrary,
                onSelectMusicFolder = { musicFolderLauncher.launch(null) },
                onScanMusicFolder = vm::scanLibrary,
                onSelectPodcastFolder = { podcastFolderLauncher.launch(null) },
                onScanPodcastFolder = vm::scanPodcasts,
                onSelectOstFolder = { ostFolderLauncher.launch(null) },
                onScanOstFolder = vm::scanOstFolder,
                onSelectCustomFolder = { customFolderLauncher.launch(null) },
                onScanCustomFolder = vm::scanCustomFolder
            )
        }
        item { SettingsSwitchDescription("Include social audio", "Note: Include voice notes and audio files from social folders.", settings.includeSocialAudio) { vm.updateSettings(settings.copy(includeSocialAudio = it)) } }
        item { AutoScanOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Equalizer") }
        item { EqualizerCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Audio") }
        item { AudioOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Android Auto") }
        item { AndroidAutoOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Always On Display") }
        item { AlwaysOnDisplayCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Appearance") }
        item { AccentColorPicker(settings, customColor, { customColor = it }, vm::updateSettings) }
        item { SettingsSwitch("AMOLED MODE", settings.amoledBlack) { vm.updateSettings(settings.copy(amoledBlack = it, lightMode = false)) } }
        item { SettingsSwitch("LIGHT MODE", settings.lightMode) { vm.updateSettings(settings.copy(lightMode = it, amoledBlack = false)) } }
        item { SurfaceStyleSettingsCard(settings, vm::updateSettings) }
        item { SurfaceBorderSettingsCard(settings, vm::updateSettings) }
        item { AmbientGlowOptionsCard(settings, vm::updateSettings) }
        item { AnimationOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("TV & Landscape Mode") }
        item { TvLandscapeOptionsCard(settings, vm::updateSettings) }
        item { DisplayDockOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Localization") }
        item { LanguagePicker(settings) { vm.updateSettings(settings.copy(language = it)) } }

        item { SettingsSectionTitle("Startup") }
        item { StartupPicker(settings) { vm.updateSettings(settings.copy(startPage = it)) } }
        item { SettingsSwitch("Remember last tab", settings.rememberLastScreen) { vm.updateSettings(settings.copy(rememberLastScreen = it)) } }
        item { AutostartOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Network Library") }
        item {
            NetworkLibraryOptionsCard(
                settings = settings,
                status = networkStatus,
                onEnabledChange = vm::setNetworkLibraryEnabled,
                onSelect = { networkLibraryLauncher.launch(null) },
                onScan = vm::scanNetworkLibrary,
                onUpdate = vm::updateSettings
            )
        }

        item { SettingsSectionTitle("Library Management") }
        item { LibraryVisibilitySettingsCard(settings, vm::updateSettings) }
        item { SettingsSectionTitle("Library Order") }
        item { LibraryOrderEditor(settings) { vm.updateSettings(settings.copy(libraryOrder = it.joinToString(","))) } }

        item { SettingsSectionTitle("Home Order") }
        item { HomeOrderEditor(settings) { vm.updateSettings(settings.copy(homeOrder = it.joinToString(","))) } }

        item { SettingsSectionTitle("Web Libraries") }
        item { SettingsSwitch("Enable SoundCloud", settings.enableSoundCloud) { vm.updateSettings(settings.copy(enableSoundCloud = it)) } }
        item { SettingsSwitch("Enable Spotify", settings.enableSpotify) { vm.updateSettings(settings.copy(enableSpotify = it)) } }
        item { SettingsSwitch("Enable YouTube Music", settings.enableYouTubeMusic) { vm.updateSettings(settings.copy(enableYouTubeMusic = it)) } }
        item { SettingsSwitch("Enable YouTube", settings.enableYouTube) { vm.updateSettings(settings.copy(enableYouTube = it)) } }
        item { SettingsSwitch("Enable custom web library", settings.enableCustomWeb) { vm.updateSettings(settings.copy(enableCustomWeb = it)) } }
        item {
            OutlinedTextField(
                value = webName,
                onValueChange = { webName = it; vm.updateSettings(settings.copy(customWebName = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom web library name") },
                placeholder = { Text("Example Library Name", color = appMuted(.48f)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
        }
        item {
            OutlinedTextField(
                value = webUrl,
                onValueChange = { webUrl = it; vm.updateSettings(settings.copy(customWebUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom web library URL") },
                placeholder = { Text("https://example.com", color = appMuted(.48f)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
        }

        item { SettingsSectionTitle("Playlist Management") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(playlistName, { playlistName = it }, Modifier.fillMaxWidth(), label = { Text("Playlist name") }, shape = RoundedCornerShape(18.dp), singleLine = true)
                    Text("Playlist format", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
                    ChipRow(listOf("M3U", "M3U8", "PLS"), "M3U") { }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsActionButton("Create", Icons.Rounded.Add, primary = true, modifier = Modifier.weight(1f)) { vm.createPlaylist(playlistName.ifBlank { "New Playlist" }); playlistName = "" }
                        SettingsActionButton("Import", Icons.Rounded.FileDownload, modifier = Modifier.weight(1f)) { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/*")) }
                    }
                    SettingsActionButton("Export all playlists", Icons.Rounded.FileUpload, modifier = Modifier.fillMaxWidth()) { exportAllPlaylistsLauncher.launch("musicmonster-playlists.zip") }
                }
            }
        }

        item { SettingsSectionTitle("Visualizer") }
        item {
            VisualizerOptionsCard(
                settings = settings,
                hasPermission = vm.hasVisualizerPermission(),
                onRequestPermission = { visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onUpdate = vm::updateSettings
            )
        }

        item { SettingsSectionTitle("Import & Export") }
        item {
            ImportExportCard(
                onExportSettings = { exportSettingsLauncher.launch("musicmonster-settings.mm") },
                onImportSettings = { importSettingsLauncher.launch(arrayOf("application/vnd.musicmonster+json", "application/json", "application/octet-stream", "*/*")) },
                onImportPlaylist = { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/*")) },
                onExportPlaylists = { exportAllPlaylistsLauncher.launch("musicmonster-playlists.zip") },
                onExportFavorites = { exportFavoritesLauncher.launch("musicmonster-favorites.mm") },
                onImportFavorites = { importFavoritesLauncher.launch(arrayOf("application/vnd.musicmonster+json", "application/json", "application/octet-stream", "*/*")) }
            )
        }

        item { SettingsSectionTitle("Keyboard & TV Remote") }
        item { KeyboardRemoteHelpCard(settings, vm::updateSettings) }
        item { SavePlayerSettingsCard { vm.closeSettings() } }
        item { ClearResetDataCard(onClear = vm::clearLocalLibraryCache, onReset = vm::resetAppData) }
        item { AboutSection(settings) }
        item { Spacer(Modifier.height(112.dp)) }
    }
}


@Composable
fun LocalLibraryActionsCard(
    onScanDevice: () -> Unit,
    onRescanLibrary: () -> Unit,
    onSelectMusicFolder: () -> Unit,
    onScanMusicFolder: () -> Unit,
    onSelectPodcastFolder: () -> Unit,
    onScanPodcastFolder: () -> Unit,
    onSelectOstFolder: () -> Unit,
    onScanOstFolder: () -> Unit,
    onSelectCustomFolder: () -> Unit,
    onScanCustomFolder: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton("Scan local music library", Icons.Rounded.LibraryMusic, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onScanDevice)
            SettingsActionButton("Rescan library", Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onRescanLibrary)
            SettingsActionButton("Select local music folder", Icons.Rounded.Folder, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSelectMusicFolder)
            SettingsActionButton("Rescan local music folder", Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onScanMusicFolder)
            SettingsActionButton("Select podcast folder", Icons.Rounded.Folder, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSelectPodcastFolder)
            SettingsActionButton("Scan podcast folder", Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onScanPodcastFolder)
            SettingsActionButton("Set OST folder", Icons.Rounded.Album, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSelectOstFolder)
            SettingsActionButton("Scan OST folder", Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onScanOstFolder)
            SettingsActionButton("Set custom folder", Icons.Rounded.CreateNewFolder, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSelectCustomFolder)
            SettingsActionButton("Scan custom folder", Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onScanCustomFolder)
        }
    }
}

@Composable
fun ImportExportCard(
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onImportPlaylist: () -> Unit,
    onExportPlaylists: () -> Unit,
    onExportFavorites: () -> Unit,
    onImportFavorites: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton("Export settings", Icons.Rounded.FileUpload, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onExportSettings)
            SettingsActionButton("Import settings", Icons.Rounded.FileDownload, modifier = Modifier.fillMaxWidth(), onClick = onImportSettings)
            SettingsActionButton("Export playlists", Icons.Rounded.FileUpload, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onExportPlaylists)
            SettingsActionButton("Import playlist", Icons.Rounded.FileDownload, modifier = Modifier.fillMaxWidth(), onClick = onImportPlaylist)
            SettingsActionButton("Export favorites", Icons.Rounded.FileUpload, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onExportFavorites)
            SettingsActionButton("Import favorites", Icons.Rounded.FileDownload, modifier = Modifier.fillMaxWidth(), onClick = onImportFavorites)
        }
    }
}


@Composable
fun KeyboardRemoteHelpCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    var newProfileName by remember { mutableStateOf("") }
    val customProfiles = remember(settings.customRemoteProfiles) {
        settings.customRemoteProfiles.split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val baseProfiles = listOf("Standard", "D-Pad Focus", "IR Remote", "Keyboard")
    val profileItems = baseProfiles + listOf("Custom") + customProfiles.map { "Custom: $it" }
    val fallbackItems = baseProfiles + customProfiles.map { "Custom: $it" }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("D-pad profile", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            ChipRow(profileItems, settings.remoteControlProfile.ifBlank { "Standard" }) { selected -> onUpdate(settings.copy(remoteControlProfile = selected)) }
            Text("Current fallback", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            ChipRow(fallbackItems, settings.remoteFallbackProfile.ifBlank { "Standard" }) { selected -> onUpdate(settings.copy(remoteFallbackProfile = selected)) }
            SettingsSwitchInline("Editable key mapping", settings.editableKeyMappingEnabled) { onUpdate(settings.copy(editableKeyMappingEnabled = it)) }
            if (settings.remoteControlProfile.startsWith("Custom")) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom profile name") },
                    placeholder = { Text("Example: Living Room IR", color = appMuted(.48f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingsActionButton("Add", Icons.Rounded.Add, primary = true, modifier = Modifier.weight(1f)) {
                        val safe = newProfileName.trim().replace('|', ' ')
                        if (safe.isNotBlank()) {
                            val next = (customProfiles + safe).distinct().joinToString("|")
                            onUpdate(settings.copy(customRemoteProfiles = next, remoteControlProfile = "Custom: $safe"))
                            newProfileName = ""
                        }
                    }
                    SettingsActionButton("Delete", Icons.Rounded.Delete, modifier = Modifier.weight(1f)) {
                        val active = settings.remoteControlProfile.removePrefix("Custom:").trim()
                        val next = customProfiles.filterNot { it == active }.joinToString("|")
                        onUpdate(settings.copy(customRemoteProfiles = next, remoteControlProfile = "Custom", remoteFallbackProfile = if (settings.remoteFallbackProfile == settings.remoteControlProfile) "Standard" else settings.remoteFallbackProfile))
                    }
                }
            }
            val actions = when {
                settings.remoteControlProfile.startsWith("Custom") -> listOf(
                    "Custom profile" to "Uses saved profile.",
                    "Fallback" to settings.remoteFallbackProfile.ifBlank { "Standard" },
                    "Editable mapping" to if (settings.editableKeyMappingEnabled) "Enabled" else "Disabled"
                )
                settings.remoteControlProfile == "IR Remote" -> listOf("D-pad" to "Move focus", "OK / Enter" to "Activate focused item", "Back / Exit" to "Back or cancel", "Play / Pause" to "Toggle playback", "Next / Previous" to "Change track")
                settings.remoteControlProfile == "Keyboard" -> listOf("Space" to "Play or pause", "Ctrl + Right / Left" to "Next / previous track", "Alt + Right / Left" to "Seek +10 / -10 seconds", "+ / -" to "Music volume", "Ctrl + F" to "Open search")
                settings.remoteControlProfile == "D-Pad Focus" -> listOf("Arrow keys / D-pad" to "Move visible focus", "Enter / OK" to "Tap selected control", "Escape / Backspace" to "Back or cancel", "Ctrl + arrows" to "Player shortcuts")
                else -> listOf("Space / Play-Pause" to "Play or pause", "Arrow keys / D-pad" to "Move focus", "Enter / OK" to "Activate focused item", "Escape / Backspace / Back" to "Back or cancel", "+ / -" to "Adjust music volume")
            }
            actions.forEach { (keys, action) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(keys, modifier = Modifier.weight(.9f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(9.dp))
                    Text(action, modifier = Modifier.weight(1.1f), color = appText(.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}



@Composable
fun AboutSection(settings: AppSettings) {
    val context = LocalContext.current
    val buildType = remember(context) {
        val flags = context.applicationInfo.flags
        if ((flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Debug" else "Release"
    }
    var showInformation by remember { mutableStateOf(false) }
    if (showInformation) LegalInformationDialog(amoledBlack = settings.amoledBlack, lightMode = settings.lightMode) { showInformation = false }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Music Monster Android Music Player", color = appText(.78f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version: ", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
                Text("3.3.5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text("  $buildType", color = MaterialTheme.colorScheme.primary.copy(.86f), style = MaterialTheme.typography.labelMedium)
            }
            Text("Package: com.sksdesign.musicmonster", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Author: Sven Klocke aka sksdesign", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/complicatiion/musicmonster_app"))) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Icon(painterResource(R.drawable.ic_github), contentDescription = "GitHub", tint = appIconColor(.9f), modifier = Modifier.size(22.dp))
                }
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sksdesign.de"))) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Rounded.Public, contentDescription = "Website", tint = appIconColor(.9f), modifier = Modifier.size(22.dp))
                }
                OutlinedButton(onClick = { showInformation = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = "Music Monster Information: EULA, Privacy Policy, Legal Notice, Copyright", tint = appIconColor(.9f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsHeader() {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.07f)), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentLogo(Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = appText())
                Text("Music Monster", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = appText(.9f), modifier = Modifier.padding(top = 4.dp))
}


@Composable
fun AnimationOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSwitchInline("Animations", settings.smoothAnimationsEnabled) {
                onUpdate(settings.copy(smoothAnimationsEnabled = it))
            }
            Text("Animation speed: " + animationSpeedLabel(settings.animationSpeed), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.animationSpeed.coerceIn(0.25f, 2.0f),
                onValueChange = { value ->
                    val stepped = (value * 4f).toInt() / 4f
                    onUpdate(settings.copy(animationSpeed = stepped.coerceIn(0.25f, 2.0f)))
                },
                valueRange = 0.25f..2.0f,
                steps = 6,
                enabled = settings.smoothAnimationsEnabled
            )
            ChipRow(listOf("0.25x", "0.5x", "1.0x", "1.5x", "2.0x"), animationSpeedLabel(settings.animationSpeed)) { selected ->
                selected.removeSuffix("x").toFloatOrNull()?.let { onUpdate(settings.copy(animationSpeed = it.coerceIn(0.25f, 2.0f))) }
            }
        }
    }
}

@Composable
fun AmbientGlowOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    val activeColor = if (settings.ambientGlowUseCustomColor) settings.ambientGlowColor else settings.accentColor
    var hue by remember(activeColor) { mutableStateOf(colorToHue(activeColor)) }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ambient glow effect", fontWeight = FontWeight.SemiBold, color = appText())
            SettingsSwitchInline("Ambient Glow", settings.ambientGlowEnabled) {
                onUpdate(settings.copy(ambientGlowEnabled = it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glow intensity: ${settings.ambientGlowIntensity.toInt()}%", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = settings.ambientGlowIntensity.coerceIn(0f, 100f),
                onValueChange = { onUpdate(settings.copy(ambientGlowIntensity = (it / 10f).toInt() * 10f)) },
                valueRange = 0f..100f,
                steps = 9
            )
            SettingsSwitchInline("Use custom glow color", settings.ambientGlowUseCustomColor) {
                onUpdate(settings.copy(ambientGlowUseCustomColor = it, ambientGlowColor = if (it) activeColor else settings.ambientGlowColor))
            }
            Text("Glow color", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            RainbowHueSlider(
                hue = hue,
                enabled = settings.ambientGlowUseCustomColor,
                onHueChange = { value ->
                    hue = value
                    onUpdate(settings.copy(ambientGlowUseCustomColor = true, ambientGlowColor = hueToColor(value)))
                }
            )
        }
    }
}

@Composable
fun RainbowHueSlider(hue: Float, enabled: Boolean = true, onHueChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                .graphicsLayer(alpha = if (enabled) 1f else .38f)
        )
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color(hueToColor(hue)),
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = appText())
            AppSwitch(checked, onChecked)
        }
    }
}

@Composable
fun SettingsSwitchDescription(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = appText(), fontWeight = FontWeight.SemiBold)
                Text(description, color = appMuted(.62f), style = MaterialTheme.typography.labelMedium, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            AppSwitch(checked, onChecked)
        }
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChecked,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(.72f),
            uncheckedThumbColor = if (isLightThemeActive()) Color(0xFF222222) else appMuted(.88f),
            uncheckedTrackColor = if (isLightThemeActive()) Color.Black.copy(.14f) else appMuted(.18f)
        )
    )
}

@Composable
fun SettingsActionButton(
    text: String,
    icon: ImageVector,
    primary: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier.accentGlow(), shape = RoundedCornerShape(24.dp)) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.accentGlow(), shape = RoundedCornerShape(24.dp)) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = appIconColor(.82f))
            Spacer(Modifier.width(8.dp))
            Text(text, color = appText(.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}



@Composable
fun ConfirmActionButton(
    label: String,
    message: String,
    icon: ImageVector,
    danger: Boolean = false,
    onConfirm: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val tint = if (danger) Color(0xFFFF6B6B) else Color(0xFFFFB74D)
    Button(
        onClick = { showConfirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = tint.copy(alpha = if (danger) .24f else .18f), contentColor = tint),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showConfirm) {
        SceneGlassDialog(onDismissRequest = { showConfirm = false }) {
                Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                Card(
                    Modifier.fillMaxWidth().widthIn(max = 560.dp).sceneGlassPanel(),
                    colors = CardDefaults.cardColors(containerColor = overlayPanelColor()),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
                        Text("Confirm action", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = appText(), textAlign = TextAlign.Center)
                        Text(message, color = appMuted(.72f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            OutlinedButton(onClick = { showConfirm = false }, shape = RoundedCornerShape(18.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("No")
                            }
                            Spacer(Modifier.width(14.dp))
                            Button(onClick = { showConfirm = false; onConfirm() }, colors = ButtonDefaults.buttonColors(containerColor = tint), shape = RoundedCornerShape(18.dp)) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Yes")
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun AutoScanOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    var customHours by remember(settings.autoScanIntervalHours) { mutableStateOf(settings.autoScanIntervalHours.toString()) }
    val presets = listOf(6, 12, 24, 72, 96, 128)
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsSwitchInline("Automatic library scan", settings.autoScanEnabled) { onUpdate(settings.copy(autoScanEnabled = it)) }
            Text("Auto-scan interval", color = appMuted(if (settings.autoScanEnabled) .72f else .36f), fontWeight = FontWeight.SemiBold)
            Box(Modifier.graphicsLayer(alpha = if (settings.autoScanEnabled) 1f else .38f)) {
                ChipRow(listOf("6h", "12h", "24h", "72h", "96h", "128h", "Custom"), if (customOpen || settings.autoScanIntervalHours !in presets) "Custom" else "${settings.autoScanIntervalHours}h") { label ->
                    if (settings.autoScanEnabled) {
                        if (label == "Custom") customOpen = true
                        else label.removeSuffix("h").toIntOrNull()?.let { onUpdate(settings.copy(autoScanIntervalHours = it)); customOpen = false }
                    }
                }
            }
            if (customOpen && settings.autoScanEnabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHours,
                        onValueChange = { customHours = it.filter(Char::isDigit).take(5) },
                        label = { Text("Custom hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = { customHours.toIntOrNull()?.takeIf { it in 1..8760 }?.let { onUpdate(settings.copy(autoScanIntervalHours = it)); customOpen = false } },
                        enabled = (customHours.toIntOrNull() ?: 0) in 1..8760
                    ) { Icon(Icons.Rounded.Save, contentDescription = "Save custom interval") }
                }
            }
        }
    }
}

@Composable
fun ClearResetDataCard(onClear: () -> Unit, onReset: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clear / Reset", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = appText())
            ConfirmActionButton(
                label = "Clear local library cache",
                message = "Clear cached local music metadata and rescan later? Network library metadata is retained.",
                icon = Icons.Rounded.CleaningServices,
                danger = false,
                onConfirm = onClear
            )
            ConfirmActionButton(
                label = "Reset app to factory state",
                message = "Reset MusicMonster as if it was freshly installed? The license dialog and first-start setup will appear again.",
                icon = Icons.Rounded.RestartAlt,
                danger = true,
                onConfirm = onReset
            )
        }
    }
}

@Composable
fun AutostartOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Autostart", fontWeight = FontWeight.SemiBold, color = appText())
            SettingsSwitchInline("Start in background", settings.autostartBackground) { onUpdate(settings.copy(autostartBackground = it)) }
            SettingsSwitchInline("Open app on device start", settings.autostartOpenApp) { onUpdate(settings.copy(autostartOpenApp = it)) }
            Text("Note: Android TV and phone manufacturers may restrict background autostart.", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TvLandscapeOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSwitchInline("Enable landscape layout", settings.landscapeMode) { onUpdate(settings.copy(landscapeMode = it)) }
            SettingsSwitchInline("Dedicated TV mode", settings.tvModeEnabled) { onUpdate(settings.copy(tvModeEnabled = it)) }
            Text("Screen preset", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("6 inch", "7 inch", "8 inch", "10 inch", "32 inch", "50 inch", "65 inch", "Custom"), if (settings.tvScreenPreset == "Custom") "Custom" else "${settings.tvScreenPreset} inch") { selected ->
                onUpdate(settings.copy(tvScreenPreset = selected.substringBefore(" ")))
            }
            Text("TV UI scale: " + String.format(Locale.US, "%.2f", settings.tvUiScale) + "x", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.tvUiScale.coerceIn(.75f, 1.75f), onValueChange = { onUpdate(settings.copy(tvUiScale = it.coerceIn(.75f, 1.75f))) }, valueRange = .75f..1.75f)
            Text("Album grid columns: ${settings.tvGridColumns}", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.tvGridColumns.toFloat().coerceIn(3f, 10f), onValueChange = { onUpdate(settings.copy(tvGridColumns = it.toInt().coerceIn(3, 10))) }, valueRange = 3f..10f, steps = 6)
            Text("Grid scale: " + String.format(Locale.US, "%.2f", settings.tvGridScale) + "x", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.tvGridScale.coerceIn(.75f, 1.75f), onValueChange = { onUpdate(settings.copy(tvGridScale = it.coerceIn(.75f, 1.75f))) }, valueRange = .75f..1.75f)
        }
    }
}

@Composable
fun SavePlayerSettingsCard(onSave: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Save Player Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = appText())
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().accentGlow(), shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Rounded.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save", color = Color.White)
            }
        }
    }
}

@Composable
fun DisplayDockOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Navigation and player controls", fontWeight = FontWeight.SemiBold, color = appText())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone UI scale: " + String.format(Locale.US, "%.2f", settings.uiScale) + "x", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = settings.uiScale, onValueChange = { onUpdate(settings.copy(uiScale = it.coerceIn(.75f, 1.25f))) }, valueRange = .75f..1.25f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bottom navigation auto-hide: ${settings.dockAutoHideSeconds}s", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = settings.dockAutoHideSeconds.toFloat(), onValueChange = { onUpdate(settings.copy(dockAutoHideSeconds = it.toInt().coerceIn(2, 15))) }, valueRange = 2f..15f, steps = 12)
            SettingsSwitchInline("Start navigation collapsed", settings.dockStartsCollapsed) { onUpdate(settings.copy(dockStartsCollapsed = it)) }
            Text("Bottom bar corner style", fontWeight = FontWeight.SemiBold, color = appText())
            ChipRow(listOf("Compact", "Soft", "Round"), settings.bottomBarCornerStyle.ifBlank { "Compact" }) { selected -> onUpdate(settings.copy(bottomBarCornerStyle = selected)) }
            Text("Bottom bar transparency: ${settings.bottomBarTransparency.toInt()}%", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.bottomBarTransparency.coerceIn(0f, 100f), onValueChange = { onUpdate(settings.copy(bottomBarTransparency = it.coerceIn(0f, 100f))) }, valueRange = 0f..100f, steps = 9)
            Divider(color = appMuted(.12f))
            Text("Player controls corner style", fontWeight = FontWeight.SemiBold, color = appText())
            ChipRow(listOf("Compact", "Soft", "Round"), settings.playerControlsCornerStyle.ifBlank { "Compact" }) { selected -> onUpdate(settings.copy(playerControlsCornerStyle = selected)) }
            Text("Player controls transparency: ${settings.playerControlsTransparency.toInt()}%", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.playerControlsTransparency.coerceIn(0f, 100f), onValueChange = { onUpdate(settings.copy(playerControlsTransparency = it.coerceIn(0f, 100f))) }, valueRange = 0f..100f, steps = 9)
            SettingsSwitchInline("Auto-hide player controls", settings.miniPlayerAutoHideEnabled) { onUpdate(settings.copy(miniPlayerAutoHideEnabled = it)) }
            Text("Player controls auto-hide: ${settings.miniPlayerAutoHideSeconds}s", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.miniPlayerAutoHideSeconds.toFloat(), onValueChange = { onUpdate(settings.copy(miniPlayerAutoHideSeconds = it.toInt().coerceIn(2, 15))) }, valueRange = 2f..15f, steps = 12)
        }
    }
}

data class PairedBluetoothDevice(val name: String, val address: String)

fun pairedBluetoothDevices(context: android.content.Context): List<PairedBluetoothDevice> {
    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.map { PairedBluetoothDevice(it.name ?: "Unnamed device", it.address) }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}

@Composable
fun NetworkLibraryOptionsCard(
    settings: AppSettings,
    status: NetworkLibraryStatus,
    onEnabledChange: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onScan: () -> Unit,
    onUpdate: (AppSettings) -> Unit = {}
) {
    val selectedLabel = settings.networkLibraryName.ifBlank {
        if (settings.networkLibraryUri.isBlank()) "No network folder selected" else "Network folder selected"
    }
    val statusText = when (status) {
        NetworkLibraryStatus.Cached -> "Saved — connection not checked"
        NetworkLibraryStatus.Scanning -> "Connecting to share..."
        NetworkLibraryStatus.Online -> "Online — share available"
        NetworkLibraryStatus.Offline -> "Offline — saved library retained"
    }
    val statusColor = when (status) {
        NetworkLibraryStatus.Online -> Color(0xFF69F0AE)
        NetworkLibraryStatus.Scanning -> MaterialTheme.colorScheme.primary
        NetworkLibraryStatus.Offline -> Color(0xFFFFB74D)
        NetworkLibraryStatus.Cached -> appMuted(.62f)
    }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSwitchInline("Enable network library", settings.networkLibraryEnabled, onEnabledChange)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedLabel, color = appText(.86f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (settings.networkLibraryEnabled && settings.networkLibraryUri.isNotBlank()) Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
            }
            SettingsSwitchInline("Automatic network rescan", settings.networkAutoScanEnabled) { onUpdate(settings.copy(networkAutoScanEnabled = it)) }
            Text("Network rescan interval", color = appMuted(if (settings.networkAutoScanEnabled) .72f else .36f), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.graphicsLayer(alpha = if (settings.networkAutoScanEnabled) 1f else .38f)) {
                ChipRow(listOf("6h", "12h", "24h", "72h", "96h", "128h"), "${settings.networkScanIntervalHours}h") { label ->
                    if (settings.networkAutoScanEnabled) label.removeSuffix("h").toIntOrNull()?.let { onUpdate(settings.copy(networkScanIntervalHours = it.coerceAtLeast(1))) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton("Select", Icons.Rounded.Folder, primary = true, modifier = Modifier.weight(1f), onClick = onSelect)
                SettingsActionButton("Rescan", Icons.Rounded.Refresh, modifier = Modifier.weight(1f), onClick = onScan)
            }
        }
    }
}

@Composable
fun AndroidAutoOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val devices = remember(refresh) { pairedBluetoothDevices(context) }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Phone screen on car connection", fontWeight = FontWeight.SemiBold, color = appText())
            ChipRow(listOf("Home", "Big Player", "Last Tab"), when (settings.androidAutoStartMode) {
                "Home" -> "Home"
                "LastTab" -> "Last Tab"
                else -> "Big Player"
            }) { selected ->
                onUpdate(settings.copy(androidAutoStartMode = when (selected) {
                    "Home" -> "Home"
                    "Last Tab" -> "LastTab"
                    else -> "BigPlayer"
                }))
            }
            SettingsSwitchInline("Bluetooth autostart", settings.autostartAndroidAuto) { onUpdate(settings.copy(autostartAndroidAuto = it)) }
            Divider(color = appMuted(.12f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth trigger device", fontWeight = FontWeight.SemiBold, color = appText())
                    Text(settings.androidAutoBluetoothName.ifBlank { "No device selected" }, color = appMuted(.64f), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh devices") }
            }
            if (devices.isNotEmpty()) {
                devices.forEach { device ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { onUpdate(settings.copy(androidAutoBluetoothAddress = device.address, androidAutoBluetoothName = device.name)) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary.copy(.18f) else appCardColor(.05f),
                        border = BorderStroke(1.dp, if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary else appMuted(.12f))
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary else appIconColor(.75f))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name, color = appText(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(device.address, color = appMuted(.58f), style = MaterialTheme.typography.labelSmall)
                            }
                            if (settings.androidAutoBluetoothAddress == device.address) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlwaysOnDisplayCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsSwitchInline("Always On", settings.alwaysOn) { onUpdate(settings.copy(alwaysOn = it)) }
            SettingsSwitchInline("Automatic On", settings.automaticAlwaysOn) { onUpdate(settings.copy(automaticAlwaysOn = it)) }
            Text("Note: If Android Auto is connected.", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
        }
    }
}




@Composable
fun BluetoothDevicePickerCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val devices = remember(refresh) { pairedBluetoothDevices(context) }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth trigger device", fontWeight = FontWeight.SemiBold, color = appText())
                    Text(settings.androidAutoBluetoothName.ifBlank { "No device selected" }, color = appMuted(.64f), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh devices") }
            }
            if (devices.isNotEmpty()) {
                devices.forEach { device ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { onUpdate(settings.copy(androidAutoBluetoothAddress = device.address, androidAutoBluetoothName = device.name)) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary.copy(.18f) else appCardColor(.05f),
                        border = BorderStroke(1.dp, if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary else appMuted(.12f))
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = if (settings.androidAutoBluetoothAddress == device.address) MaterialTheme.colorScheme.primary else appIconColor(.75f))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name, color = appText(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(device.address, color = appMuted(.58f), style = MaterialTheme.typography.labelSmall)
                            }
                            if (settings.androidAutoBluetoothAddress == device.address) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioOptionsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSwitchInline("Lower volume on focus loss", settings.duckOnFocusLoss) { onUpdate(settings.copy(duckOnFocusLoss = it)) }
            SettingsSwitchInline("Gapless playback", settings.gaplessPlayback) { onUpdate(settings.copy(gaplessPlayback = it)) }
            SettingsSwitchInline("Remember shuffle mode", settings.rememberShuffle) { onUpdate(settings.copy(rememberShuffle = it)) }
            SettingsSwitchInline("Repeat all by default", settings.repeatAllDefault) { onUpdate(settings.copy(repeatAllDefault = it)) }
            SettingsSwitchInline("Shuffle by default", settings.shuffleDefault) { onUpdate(settings.copy(shuffleDefault = it)) }
        }
    }
}

@Composable
fun VisualizerOptionsCard(
    settings: AppSettings,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onUpdate: (AppSettings) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Audio Visualizer", fontWeight = FontWeight.SemiBold, color = appText(), modifier = Modifier.weight(1f))
                AppSwitch(settings.visualizerEnabled, onChecked = { enabled ->
                    if (enabled && !hasPermission) onRequestPermission()
                    else onUpdate(settings.copy(visualizerEnabled = enabled))
                })
            }
            if (!hasPermission) {
                Text("Note: Permission required: Android needs microphone/audio capture permission for the native Visualizer API. Playback works without it.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRequestPermission, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Grant visualizer permission")
                }
            }
            Text("Style", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("Smooth Wave", "Wave", "Spectrum", "Bars", "Edge", "Pulse", "Ribbon"), settings.visualizerStyle) { selected ->
                onUpdate(settings.copy(visualizerStyle = selected))
            }
            Text("Placement", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("Cover", "Progress"), settings.visualizerPlacement.ifBlank { "Cover" }) { selected ->
                onUpdate(settings.copy(visualizerPlacement = selected))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity: ${settings.visualizerIntensity.toInt()}%", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = settings.visualizerIntensity.coerceIn(0f, 250f), onValueChange = { onUpdate(settings.copy(visualizerIntensity = (it / 5f).toInt() * 5f)) }, valueRange = 0f..250f, steps = 49)
            Text("Smoothing: ${settings.visualizerSmoothing.toInt()}%", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.visualizerSmoothing.coerceIn(0f, 95f), onValueChange = { onUpdate(settings.copy(visualizerSmoothing = (it / 5f).toInt() * 5f)) }, valueRange = 0f..95f, steps = 18)
            Text("Opacity: ${settings.visualizerOpacity.toInt()}%", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.visualizerOpacity.coerceIn(0f, 100f), onValueChange = { onUpdate(settings.copy(visualizerOpacity = (it / 5f).toInt() * 5f)) }, valueRange = 0f..100f, steps = 19)
            Text("Bars: ${settings.visualizerBarCount}", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Slider(value = settings.visualizerBarCount.toFloat().coerceIn(12f, 64f), onValueChange = { onUpdate(settings.copy(visualizerBarCount = it.toInt().coerceIn(12, 64))) }, valueRange = 12f..64f, steps = 12)
            SettingsSwitchInline("Use accent color", settings.visualizerUseAccentColor) { onUpdate(settings.copy(visualizerUseAccentColor = it)) }
        }
    }
}

@Composable
fun EqualizerCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        EqualizerControls(settings, onUpdate, Modifier.padding(14.dp))
    }
}

@Composable
fun EqualizerControls(settings: AppSettings, onUpdate: (AppSettings) -> Unit, modifier: Modifier = Modifier) {
    val bands = parseEqBands(settings.equalizerBands)
    val presets = equalizerPresets()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSwitchInline("Enable equalizer", settings.equalizerEnabled) { onUpdate(settings.copy(equalizerEnabled = it)) }
        Text("Preset", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
        ChipRow(presets.keys.toList(), settings.equalizerPreset) { name ->
            val nextBands = presets[name] ?: presets.getValue("Flat")
            onUpdate(settings.copy(equalizerEnabled = true, equalizerPreset = name, equalizerBands = formatEqBands(nextBands)))
        }
        val labels = listOf("60 Hz", "230 Hz", "910 Hz", "4 kHz", "14 kHz")
        labels.forEachIndexed { index, label ->
            EqSlider(
                label = label,
                value = bands[index],
                range = -10f..10f,
                suffix = " dB"
            ) { value ->
                val next = bands.toMutableList().apply { this[index] = value }
                onUpdate(settings.copy(equalizerEnabled = true, equalizerPreset = "Custom", equalizerBands = formatEqBands(next)))
            }
        }
        Divider(color = appMuted(.12f))
        EqSlider("Bass", settings.bassLevel, -10f..10f, " dB") { onUpdate(settings.copy(equalizerEnabled = true, bassLevel = it)) }
        EqSlider("Treble", settings.trebleLevel, -10f..10f, " dB") { onUpdate(settings.copy(equalizerEnabled = true, trebleLevel = it)) }
        Divider(color = appMuted(.12f))
        EqSlider("Left / Right balance", settings.balanceLevel, -1f..1f, "") { onUpdate(settings.copy(balanceLevel = it)) }
        EqSlider("Front / Rear focus", settings.faderLevel, -1f..1f, "") { onUpdate(settings.copy(faderLevel = it)) }
        Divider(color = appMuted(.12f))
        SettingsSwitchInline("Super Bass Boost", settings.superBassBoost) { onUpdate(settings.copy(superBassBoost = it)) }
        EqSlider("Bass boost", settings.bassBoostLevel, 0f..1f, "") { onUpdate(settings.copy(superBassBoost = true, bassBoostLevel = it)) }
    }
}

@Composable
fun EqSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = appText(.82f), style = MaterialTheme.typography.labelMedium)
            val display = if (range.endInclusive <= 1f) "${(value * 100).toInt()}%" else "${String.format(Locale.US, "%.1f", value)}$suffix"
            Text(display, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
fun SettingsSwitchInline(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = appText())
        AppSwitch(checked, onChecked)
    }
}

@Composable
fun LanguagePicker(settings: AppSettings, onChange: (String) -> Unit) {
    val languages = listOf(
        "en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "it" to "Italiano", "pl" to "Polski",
        "ar" to "Arabic", "fa" to "Farsi", "ja" to "Japanese", "zh" to "Chinese", "ru" to "Russian", "uk" to "Ukrainian",
        "sv" to "Swedish", "da" to "Danish", "fi" to "Finnish", "nl" to "Dutch", "cs" to "Czech"
    )
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        LazyRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languages) { (code, label) ->
                AssistChip(
                    onClick = { onChange(code) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { FlatFlag(code, Modifier.size(width = 24.dp, height = 16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.language == code) MaterialTheme.colorScheme.primary.copy(.34f) else appMuted(.05f))
                )
            }
        }
    }
}

@Composable
fun FlatFlag(code: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.clip(shape).background(Color.White).border(1.dp, appMuted(.22f), shape)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            fun horizontal(colors: List<Color>) {
                val stripeH = h / colors.size.coerceAtLeast(1)
                colors.forEachIndexed { index, color ->
                    drawRect(color, topLeft = Offset(0f, stripeH * index), size = Size(w, stripeH + 1f))
                }
            }
            fun vertical(colors: List<Color>) {
                val stripeW = w / colors.size.coerceAtLeast(1)
                colors.forEachIndexed { index, color ->
                    drawRect(color, topLeft = Offset(stripeW * index, 0f), size = Size(stripeW + 1f, h))
                }
            }
            fun nordicCross(bg: Color, cross: Color, offset: Float = .36f, thick: Float = .18f) {
                drawRect(bg, size = Size(w, h))
                drawRect(cross, topLeft = Offset(w * offset - w * thick / 2f, 0f), size = Size(w * thick, h))
                drawRect(cross, topLeft = Offset(0f, h * .5f - h * thick / 2f), size = Size(w, h * thick))
            }
            when (code) {
                "de" -> horizontal(listOf(Color.Black, Color(0xFFDD0000), Color(0xFFFFCE00)))
                "es" -> { drawRect(Color(0xFFC60B1E), size = Size(w, h)); drawRect(Color(0xFFFFC400), topLeft = Offset(0f, h * .25f), size = Size(w, h * .5f)) }
                "fr" -> vertical(listOf(Color(0xFF0055A4), Color.White, Color(0xFFEF4135)))
                "it" -> vertical(listOf(Color(0xFF009246), Color.White, Color(0xFFCE2B37)))
                "pl" -> horizontal(listOf(Color.White, Color(0xFFDC143C)))
                "nl" -> horizontal(listOf(Color(0xFFAE1C28), Color.White, Color(0xFF21468B)))
                "ru" -> horizontal(listOf(Color.White, Color(0xFF0039A6), Color(0xFFD52B1E)))
                "uk" -> horizontal(listOf(Color(0xFF0057B7), Color(0xFFFFD700)))
                "sv" -> nordicCross(Color(0xFF006AA7), Color(0xFFFECC00), offset = .38f)
                "da" -> nordicCross(Color(0xFFC8102E), Color.White, offset = .34f)
                "fi" -> nordicCross(Color.White, Color(0xFF002F6C), offset = .38f)
                "cs" -> { horizontal(listOf(Color.White, Color(0xFFD7141A))); drawRect(Color(0xFF11457E), size = Size(w * .36f, h)) }
                "ja" -> { drawRect(Color.White, size = Size(w, h)); drawCircle(Color(0xFFBC002D), radius = min(w, h) * .26f, center = Offset(w / 2f, h / 2f)) }
                "zh" -> { drawRect(Color(0xFFDE2910), size = Size(w, h)); drawCircle(Color(0xFFFFDE00), radius = min(w, h) * .13f, center = Offset(w * .24f, h * .32f)) }
                "ar" -> { horizontal(listOf(Color(0xFF007A3D), Color.White, Color.Black)); drawRect(Color(0xFFCE1126), size = Size(w * .24f, h)) }
                "fa" -> horizontal(listOf(Color(0xFF239F40), Color.White, Color(0xFFDA0000)))
                else -> {
                    drawRect(Color(0xFF012169), size = Size(w, h))
                    drawRect(Color.White, topLeft = Offset(w * .42f, 0f), size = Size(w * .16f, h))
                    drawRect(Color.White, topLeft = Offset(0f, h * .40f), size = Size(w, h * .20f))
                    drawRect(Color(0xFFC8102E), topLeft = Offset(w * .46f, 0f), size = Size(w * .08f, h))
                    drawRect(Color(0xFFC8102E), topLeft = Offset(0f, h * .44f), size = Size(w, h * .12f))
                }
            }
        }
    }
}


@Composable
fun AccentColorPicker(settings: AppSettings, customColor: String, onColorText: (String) -> Unit, onUpdate: (AppSettings) -> Unit) {
    val presets = listOf(
        0xFF263238, 0xFF00E5FF, 0xFF2979FF, 0xFF7C4DFF,
        0xFFA855F7, 0xFFFF3B7F, 0xFFFF1744, 0xFFFF6B2C,
        0xFFFFD600, 0xFF64FFDA, 0xFF00E676, 0xFF76FF03,
        0xFFFF4081, 0xFF40C4FF, 0xFFE2E8F0, 0xFFFFF8E1
    )
    var hue by remember { mutableStateOf(colorToHue(settings.accentColor)) }
    LaunchedEffect(settings.accentColor) { hue = colorToHue(settings.accentColor) }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Accent color", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            val selected = settings.accentColor == color
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) appText(.9f) else appMuted(.20f), CircleShape)
                                    .clickable { onUpdate(settings.copy(accentColor = color)) }
                            )
                        }
                    }
                }
            }
            Text("Custom color", color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                )
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        val next = hueToColor(it)
                        onColorText("#" + next.toString(16).takeLast(6).uppercase())
                        onUpdate(settings.copy(accentColor = next))
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(hueToColor(hue)),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun StartupPicker(settings: AppSettings, onSelect: (String) -> Unit) {
    val options = startupOptions(settings)
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Start page", fontWeight = FontWeight.SemiBold)
            ChipRow(options, settings.startPage, onSelect)
        }
    }
}

@Composable
fun LibraryOrderEditor(settings: AppSettings, onChange: (List<String>) -> Unit) {
    val order = baseLibraryOrder(settings).map { it.name }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(10.dp)) {
            order.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { onChange(order.toMutableList().apply { add(index - 1, removeAt(index)) }) }) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
                    IconButton(enabled = index < order.lastIndex, onClick = { onChange(order.toMutableList().apply { add(index + 1, removeAt(index)) }) }) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                }
            }
        }
    }
}


@Composable
fun HomeOrderEditor(settings: AppSettings, onChange: (List<String>) -> Unit) {
    val order = homeSections(settings).map { it.name }
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(10.dp)) {
            order.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(homeLabel(HomeSection.valueOf(item), settings.language), Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { onChange(order.toMutableList().apply { add(index - 1, removeAt(index)) }) }) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
                    IconButton(enabled = index < order.lastIndex, onClick = { onChange(order.toMutableList().apply { add(index + 1, removeAt(index)) }) }) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                }
            }
        }
    }
}

@Composable
fun FullPlayerOverlay(vm: MusicMonsterViewModel, carMode: Boolean, androidAutoMode: Boolean = false) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val position by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val visualizerBars by vm.visualizerBars.collectAsState()
    val visualizerWaveform by vm.visualizerWaveform.collectAsState()
    val visualizerStatus by vm.visualizerStatus.collectAsState()
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val current = track ?: return
    val safeDuration = duration.takeIf { it > 0 } ?: current.durationMs.coerceAtLeast(1L)
    val visualizerAllowed = settings.visualizerEnabled && !androidAutoMode && vm.hasVisualizerPermission()

    DisposableEffect(visualizerAllowed, current.id) {
        vm.setVisualizerActive(visualizerAllowed)
        onDispose { vm.setVisualizerActive(false) }
    }

    BackHandler { vm.showFullPlayer(false) }

    if (showEqualizer) {
        SceneGlassDialog(onDismissRequest = { showEqualizer = false }) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp).sceneGlassPanel(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.surfaceStyle == "Glass") overlayPanelColor() else if (isLightThemeActive()) Color.White else if (settings.amoledBlack) Color.Black else Color(0xFF151919))
            ) {
                LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { EqualizerControls(settings, vm::updateSettings) }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(onClick = { showEqualizer = false }, shape = RoundedCornerShape(24.dp)) {
                                Icon(Icons.Rounded.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddToPlaylist) {
        SceneGlassDialog(onDismissRequest = { showAddToPlaylist = false }) {
            Card(Modifier.fillMaxWidth(.94f).widthIn(max = 610.dp).sceneGlassPanel(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = overlayPanelColor())) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add to playlist", modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    playlists.forEachIndexed { index, playlist ->
                        OutlinedButton(onClick = { vm.addCurrentToPlaylist(index); showAddToPlaylist = false },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedTextField(newPlaylistName, { newPlaylistName = it },
                        label = { Text("New playlist name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { showAddToPlaylist = false },
                            modifier = Modifier.background(Color(0x22FF5555), CircleShape)) {
                            Icon(Icons.Rounded.Close, "Cancel", tint = Color(0xFFFF9B9B))
                        }
                        IconButton(onClick = {
                            vm.createPlaylist(newPlaylistName.ifBlank { "New Playlist" }, listOf(current))
                            newPlaylistName = ""
                            showAddToPlaylist = false
                        }) { Icon(Icons.Rounded.Add, "Create", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AmbientGlowBackground(settings)
            val configuration = LocalConfiguration.current
            val compactAndroidAuto = androidAutoMode && (configuration.screenWidthDp < 600 || configuration.screenHeightDp < 420)
            val useLandscapePlayer = settings.landscapeMode || (!androidAutoMode && configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp >= 600)
            if (compactAndroidAuto) {
                AndroidAutoCompactPlayerContent(current, playing, position, safeDuration, shuffle, repeat, vm, onEqualizer = { showEqualizer = true })
            } else if (useLandscapePlayer) {
                LandscapeFullPlayerContent(
                    current = current,
                    playing = playing,
                    position = position,
                    safeDuration = safeDuration,
                    shuffle = shuffle,
                    repeat = repeat,
                    settings = settings,
                    visualizerBars = visualizerBars,
                    visualizerWaveform = visualizerWaveform,
                    visualizerStatus = visualizerStatus,
                    vm = vm,
                    onEqualizer = { showEqualizer = true },
                    onAddToPlaylist = { showAddToPlaylist = true }
                )
            } else {
                PortraitFullPlayerContent(
                    current = current,
                    playing = playing,
                    position = position,
                    safeDuration = safeDuration,
                    shuffle = shuffle,
                    repeat = repeat,
                    carMode = carMode,
                    vm = vm,
                    settings = settings,
                    visualizerBars = visualizerBars,
                    visualizerWaveform = visualizerWaveform,
                    visualizerStatus = visualizerStatus,
                    onEqualizer = { showEqualizer = true },
                    onAddToPlaylist = { showAddToPlaylist = true }
                )
            }
        }
    }
}

@Composable
fun AndroidAutoCompactPlayerContent(
    current: Track,
    playing: Boolean,
    position: Long,
    safeDuration: Long,
    shuffle: Boolean,
    repeat: RepeatModeUi,
    vm: MusicMonsterViewModel,
    onEqualizer: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        current.artworkUri?.let { art ->
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = .13f),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showQueue(true) }) { Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onEqualizer) { Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { vm.showFullPlayer(false) }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { vm.showFullPlayer(true) }) { Icon(Icons.Rounded.OpenInFull, contentDescription = "Maximize", tint = MaterialTheme.colorScheme.primary) }
            }
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                TrackArtwork(current.artworkUri, Modifier.fillMaxHeight(.70f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)), albumPlaceholder = true)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.title, Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    ArtistAlbumLinks(current, vm)
                    Spacer(Modifier.height(8.dp))
                    PlayerProgress(position, safeDuration, vm)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = vm::cycleRepeatMode) { Icon(if (repeat == RepeatModeUi.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if (repeat != RepeatModeUi.Off) MaterialTheme.colorScheme.primary else appIconColor(.76f)) }
                        MainTransportRow(playing, false, vm, .85f)
                        IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) MaterialTheme.colorScheme.primary else appIconColor(.76f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PortraitFullPlayerContent(
    current: Track,
    playing: Boolean,
    position: Long,
    safeDuration: Long,
    shuffle: Boolean,
    repeat: RepeatModeUi,
    carMode: Boolean,
    vm: MusicMonsterViewModel,
    settings: AppSettings,
    visualizerBars: List<Float>,
    visualizerWaveform: List<Float>,
    visualizerStatus: VisualizerStatus,
    onEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FullPlayerTopActions(vm, current, settings, onEqualizer, onAddToPlaylist)
        Spacer(Modifier.height(if (carMode) 18.dp else 8.dp))
        var coverFeedbackVisible by remember { mutableStateOf(false) }
        val coverFeedbackAlpha by animateFloatAsState(
            targetValue = if (coverFeedbackVisible) .32f else 0f,
            animationSpec = tween(animationDurationMs(150, settings)),
            label = "cover-feedback"
        )
        LaunchedEffect(coverFeedbackVisible) {
            if (coverFeedbackVisible) {
                delay(160)
                coverFeedbackVisible = false
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(34.dp))
                .bigPlayerCoverGestures(vm, current) { coverFeedbackVisible = true }
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val duration = animationDurationMs(260, settings)
                    (slideInHorizontally(animationSpec = tween(duration)) { it / 3 } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(duration)) { -it / 3 } + fadeOut(tween(duration)))
                },
                label = "portrait-cover-change"
            ) { animatedTrack ->
                VisualizerArtwork(
                    uri = animatedTrack.artworkUri,
                    modifier = Modifier.fillMaxSize(),
                    albumPlaceholder = true,
                    settings = settings,
                    bars = visualizerBars,
                    waveform = visualizerWaveform,
                    status = visualizerStatus,
                    position = position,
                    safeDuration = safeDuration
                )
            }
            if (coverFeedbackAlpha > 0f) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = coverFeedbackAlpha)))
        }
        Spacer(Modifier.height(if (carMode) 28.dp else 20.dp))
        Text(
            current.title,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            fontWeight = FontWeight.Bold,
            fontSize = if (carMode) 28.sp else 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = appText(),
            textAlign = TextAlign.Center
        )
        ArtistAlbumLinks(current, vm)
        Spacer(Modifier.height(22.dp))
        PlayerProgress(position, safeDuration, vm, settings, visualizerBars, visualizerWaveform, visualizerStatus)
        Spacer(Modifier.height(if (carMode) 24.dp else 14.dp))
        ShuffleRepeatRow(shuffle, repeat, carMode, vm)
        Spacer(Modifier.height(6.dp))
        MainTransportRow(playing, carMode, vm)

    }
}

@Composable
fun LandscapeFullPlayerContent(
    current: Track,
    playing: Boolean,
    position: Long,
    safeDuration: Long,
    shuffle: Boolean,
    repeat: RepeatModeUi,
    settings: AppSettings,
    visualizerBars: List<Float>,
    visualizerWaveform: List<Float>,
    visualizerStatus: VisualizerStatus,
    vm: MusicMonsterViewModel,
    onEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val scale = landscapeScale(settings)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding((18f * scale).dp)) {
        val coverSize = min(maxHeight.value * 0.74f, maxWidth.value * 0.34f).dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((22f * scale).dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(coverSize)) {
                var coverFeedbackVisible by remember { mutableStateOf(false) }
                val coverFeedbackAlpha by animateFloatAsState(
                    targetValue = if (coverFeedbackVisible) .32f else 0f,
                    animationSpec = tween(animationDurationMs(150, settings)),
                    label = "landscape-cover-feedback"
                )
                LaunchedEffect(coverFeedbackVisible) {
                    if (coverFeedbackVisible) {
                        delay(160)
                        coverFeedbackVisible = false
                    }
                }
                Box(
                    Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape((30f * scale).dp))
                        .bigPlayerCoverGestures(vm, current) { coverFeedbackVisible = true }
                ) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            val duration = animationDurationMs(260, settings)
                            (slideInHorizontally(animationSpec = tween(duration)) { it / 4 } + fadeIn(tween(duration))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                        },
                        label = "landscape-cover-change"
                    ) { animatedTrack ->
                        VisualizerArtwork(
                            uri = animatedTrack.artworkUri,
                            modifier = Modifier.fillMaxSize(),
                            albumPlaceholder = true,
                            settings = settings,
                            bars = visualizerBars,
                            waveform = visualizerWaveform,
                            status = visualizerStatus,
                            position = position,
                            safeDuration = safeDuration
                        )
                    }
                    if (coverFeedbackAlpha > 0f) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = coverFeedbackAlpha)))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                FullPlayerTopActions(vm, current, settings, onEqualizer, onAddToPlaylist)
                Spacer(Modifier.height((10f * scale).dp))
                Text(
                    current.title,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    fontWeight = FontWeight.Bold,
                    fontSize = (24f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = appText(),
                    textAlign = TextAlign.Center
                )
                ArtistAlbumLinks(current, vm, fontSize = (14f * scale).sp)
                Spacer(Modifier.height((14f * scale).dp))
                PlayerProgress(position, safeDuration, vm, settings, visualizerBars, visualizerWaveform, visualizerStatus)
                Spacer(Modifier.weight(1f))
                ShuffleRepeatRow(shuffle, repeat, true, vm, scale)
                Spacer(Modifier.height((4f * scale).dp))
                MainTransportRow(playing, true, vm, scale)
                Spacer(Modifier.height((6f * scale).dp))
            }
        }
    }
}

@Composable
fun FullPlayerTopActions(
    vm: MusicMonsterViewModel,
    current: Track,
    settings: AppSettings,
    onEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = vm::toggleLandscapeMode, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_landscape_display), contentDescription = "Landscape shortcut", tint = if (settings.landscapeMode) MaterialTheme.colorScheme.primary else appIconColor(.76f))
        }
        IconButton(onClick = onEqualizer, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.primary)
        }
        TimerActionButton(vm)
        IconButton(onClick = { vm.showQueue(true) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = appIconColor(.78f))
        }
        IconButton(onClick = { vm.toggleFavorite(current) }, modifier = Modifier.size(36.dp)) {
            Icon(if (vm.isFavorite(current)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (vm.isFavorite(current)) MaterialTheme.colorScheme.primary else appIconColor(.76f))
        }
        FilledIconButton(
            onClick = { vm.showFullPlayer(false) },
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(.22f), contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", tint = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
fun ArtistAlbumLinks(current: Track, vm: MusicMonsterViewModel, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(
            current.artist,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = fontSize,
            modifier = Modifier.clickable { vm.openArtistFromTrack(current) }
        )
        Text(" • ", color = appMuted(.62f), fontSize = fontSize)
        Text(
            current.album,
            color = MaterialTheme.colorScheme.primary.copy(.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = fontSize,
            modifier = Modifier.clickable { vm.openAlbumFromTrack(current) }
        )
    }
}

@Composable
fun PlayerProgress(position: Long, safeDuration: Long, vm: MusicMonsterViewModel) {
    PlayerProgress(position, safeDuration, vm, AppSettings(visualizerEnabled = false), emptyList(), emptyList(), VisualizerStatus.Disabled)
}

@Composable
fun PlayerProgress(
    position: Long,
    safeDuration: Long,
    vm: MusicMonsterViewModel,
    settings: AppSettings,
    bars: List<Float>,
    waveform: List<Float>,
    status: VisualizerStatus
) {
    val progressVisualizerActive = settings.visualizerEnabled && settings.visualizerPlacement == "Progress" && status == VisualizerStatus.Active
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        if (progressVisualizerActive) {
            AudioVisualizerCanvas(
                style = settings.visualizerStyle,
                bars = bars,
                waveform = waveform,
                color = (if (settings.visualizerUseAccentColor) Color(settings.accentColor) else Color.White).copy(alpha = (settings.visualizerOpacity.coerceIn(0f, 100f) / 100f) * .70f),
                modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp)
            )
        }
        Slider(value = position.coerceIn(0L, safeDuration).toFloat(), onValueChange = { vm.seekTo(it.toLong()) }, valueRange = 0f..safeDuration.toFloat(), modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration(position), color = appMuted(.65f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(formatDuration(safeDuration), color = appMuted(.65f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ShuffleRepeatRow(shuffle: Boolean, repeat: RepeatModeUi, carMode: Boolean, vm: MusicMonsterViewModel, scale: Float = 1f) {
    Row(horizontalArrangement = Arrangement.spacedBy((18f * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::cycleRepeatMode, modifier = Modifier.size(((if (carMode) 64f else 52f) * scale).dp)) {
            Icon(if (repeat == RepeatModeUi.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (repeat != RepeatModeUi.Off) MaterialTheme.colorScheme.primary else appIconColor(.78f))
        }
        IconButton(onClick = vm::toggleShuffle, modifier = Modifier.size(((if (carMode) 64f else 52f) * scale).dp)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else appIconColor(.78f))
        }
    }
}

@Composable
fun MainTransportRow(playing: Boolean, carMode: Boolean, vm: MusicMonsterViewModel, scale: Float = 1f) {
    val settings by vm.settings.collectAsState()
    Row(
        Modifier.padding(horizontal = (10f * scale).dp, vertical = (4f * scale).dp),
        horizontalArrangement = Arrangement.spacedBy(((if (carMode) 28f else 18f) * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = vm::previous, modifier = Modifier.size(((if (carMode) 76f else 60f) * scale).dp)) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(((if (carMode) 40f else 32f) * scale).dp), tint = appIconColor()) }
        val playRotation by animateFloatAsState(targetValue = if (playing) 180f else 0f, animationSpec = tween(animationDurationMs(220, settings)), label = "playPauseRotation")
        FilledIconButton(
            onClick = vm::toggle,
            shape = CircleShape,
            modifier = Modifier.size(((if (carMode) 96f else 76f) * scale).dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", modifier = Modifier.size(((if (carMode) 46f else 36f) * scale).dp).graphicsLayer(rotationZ = playRotation), tint = Color.White)
        }
        IconButton(onClick = vm::next, modifier = Modifier.size(((if (carMode) 76f else 60f) * scale).dp)) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(((if (carMode) 40f else 32f) * scale).dp), tint = appIconColor()) }
    }
}



@Composable
fun HeroCard(title: String, subtitle: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.07f)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(body, color = appMuted(.72f))
            }
        }
    }
}

@Composable
fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { label ->
            var focused by remember { mutableStateOf(false) }
            FilterChip(
                selected = label == selected,
                modifier = (if (label == selected) Modifier.accentGlow() else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)) else Modifier),
                onClick = { onSelect(label) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = appCardColor(.05f),
                    labelColor = appText(.78f)
                )
            )
        }
    }
}

@Composable
fun TrackSection(title: String, tracks: List<Track>, vm: MusicMonsterViewModel) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (tracks.isEmpty()) Text("No tracks yet.", color = appMuted(.62f)) else TrackList(tracks, vm, compact = true)
    }
}

@Composable
fun PlaylistSection(title: String, playlists: List<Playlist>) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(if (playlists.isEmpty()) "No playlists imported yet." else "${playlists.size} playlists", color = appMuted(.65f))
    }
}

@Composable
fun activeTrackContainer(track: Track, current: Track?): Color? =
    if (current?.id == track.id) MaterialTheme.colorScheme.primary.copy(alpha = if (isLightThemeActive()) .20f else .28f) else null

/** Device-independent, offscreen alpha fade for scrollable lists.
 * Avoids the hard horizontal cutoff while scrolling into/out of the viewport.
 */
fun Modifier.verticalEdgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent, 0.035f to Color.Black,
                0.95f to Color.Black, 1f to Color.Transparent,
                startY = 0f, endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun TrackList(
    tracks: List<Track>,
    vm: MusicMonsterViewModel,
    compact: Boolean = false,
    showQueueButton: Boolean = true,
    showRemoveButton: Boolean = false,
    showArtwork: Boolean = true,
    compactActions: Boolean = false,
    durationBelow: Boolean = false,
    onRemove: ((Int) -> Unit)? = null,
    onMoveUp: ((Int) -> Unit)? = null,
    onMoveDown: ((Int) -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val favoriteIds = remember(settings.favoriteIds) { settings.favoriteIds.split(',').mapNotNull { it.toLongOrNull() }.toSet() }
    val current by vm.currentTrack.collectAsState()
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    actionTrack?.let { selected -> TrackContextActionsDialog(selected, vm) { actionTrack = null } }
    val modifier = if (compact) Modifier.fillMaxWidth().heightIn(max = 420.dp) else Modifier.fillMaxSize()
    val padding = if (compact) PaddingValues(0.dp) else PaddingValues(bottom = 128.dp)
    Box(modifier) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = padding, modifier = Modifier.fillMaxSize().verticalEdgeFade()) {
        items(tracks.indices.toList(), key = { tracks[it].id.toString() + "-$it" }) { index ->
            val track = tracks[index]
            TrackRow(
                track = track,
                favorite = favoriteIds.contains(track.id),
                onFavorite = { vm.toggleFavorite(track) },
                onClick = { vm.play(tracks, index) },
                showArtwork = showArtwork,
                compactActions = compactActions,
                durationBelow = durationBelow,
                containerColor = activeTrackContainer(track, current),
                onLongPress = { actionTrack = track },
                trailing = {
                    if (showQueueButton) CompactIconButton(onClick = { vm.addToQueue(track) }, compact = compactActions) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to queue", tint = appMuted(.66f), modifier = Modifier.size(if (compactActions) 18.dp else 22.dp)) }
                    if (showRemoveButton) {
                        CompactIconButton(enabled = index > 0, onClick = { onMoveUp?.invoke(index) }, compact = compactActions) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(if (compactActions) 18.dp else 22.dp)) }
                        CompactIconButton(enabled = index < tracks.lastIndex, onClick = { onMoveDown?.invoke(index) }, compact = compactActions) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(if (compactActions) 18.dp else 22.dp)) }
                        CompactIconButton(onClick = { onRemove?.invoke(index) }, compact = compactActions) { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove", modifier = Modifier.size(if (compactActions) 18.dp else 22.dp)) }
                    }
                }
            )
        }
    }
    }
}

@Composable
fun TrackContextActionsDialog(track: Track, vm: MusicMonsterViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by vm.playlists.collectAsState()
    val currentFavorite = vm.isFavorite(track)
    var edit by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    if (showEqualizer) EqualizerDialog(vm, onDismiss = { showEqualizer = false })
    var addPlaylist by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.scanLibrary()
        onDismiss()
    }
    val writeTags: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val result = com.sksdesign.musicmonster.data.Mp3TagEditor.save(context, track, title, artist, album, genre)
                busy = false
                result.onSuccess { vm.scanLibrary(); onDismiss() }
                    .onFailure { error = it.message ?: "The music provider denied write access" }
            }
        }
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) writeTags()
        else error = "Write permission was not granted"
    }
    SceneGlassDialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(.94f).widthIn(max = 640.dp).sceneGlassPanel(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = overlayPanelColor())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (edit) {
                    Text("MP3 ID3 Tag Editor", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                    OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                    OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true)
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF9A9A), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { edit = false }, enabled = !busy, modifier = Modifier.background(Color(0x22FF5555), CircleShape)) { Icon(Icons.Rounded.Close, "Cancel", tint = Color(0xFFFF9999)) }
                        IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 30 && track.uri.authority == MediaStore.AUTHORITY) {
                                runCatching {
                                    val req = MediaStore.createWriteRequest(context.contentResolver, listOf(track.uri))
                                    writeLauncher.launch(IntentSenderRequest.Builder(req.intentSender).build())
                                }.onFailure { error = it.message ?: "Unable to request write permission" }
                            } else writeTags()
                        }, enabled = !busy && title.isNotBlank()) { Icon(Icons.Rounded.Save, "Save tags", tint = MaterialTheme.colorScheme.primary) }
                    }
                } else if (addPlaylist) {
                    Text("Add to playlist", color = MaterialTheme.colorScheme.primary)
                    playlists.forEachIndexed { index, item ->
                        OutlinedButton(onClick = { vm.addTrackToPlaylist(index, track); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.name, maxLines = 1)
                        }
                    }
                    OutlinedTextField(newName, { newName = it }, label = { Text("New playlist") }, singleLine = true)
                    Row {
                        IconButton(onClick = { addPlaylist = false }) { Icon(Icons.Rounded.Close, "Cancel") }
                        IconButton(onClick = { vm.createPlaylist(newName.ifBlank { "New Playlist" }, listOf(track)); onDismiss() }) { Icon(Icons.Rounded.Add, "Create playlist", tint = MaterialTheme.colorScheme.primary) }
                    }
                } else if (confirmDelete) {
                    Text("Delete the original music file?\nThis cannot be undone.", modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) { Text("No") }
                        Button(onClick = {
                            if (track.isNetwork) { error = "Network share deletion is not supported"; return@Button }
                            if (Build.VERSION.SDK_INT >= 30 && track.uri.scheme == "content") {
                                runCatching {
                                    val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(track.uri))
                                    deleteLauncher.launch(IntentSenderRequest.Builder(req.intentSender).build())
                                }.onFailure { error = it.message ?: "Delete is not supported by this provider" }
                            } else { error = "Deletion requires a writable MediaStore URI on this Android version" }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E3030), contentColor = Color.White),
                            modifier = Modifier.weight(1f)) { Text("Yes") }
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF9999))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).setType("audio/*")
                                .putExtra(Intent.EXTRA_STREAM, track.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(share, "Share audio")) }
                                .onFailure { error = it.message ?: "File sharing not supported" }
                        }) { Icon(Icons.Rounded.Share, "Share", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { vm.toggleFavorite(track); onDismiss() }) {
                            Icon(if (currentFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { vm.addToQueue(track); onDismiss() }) { Icon(Icons.Rounded.PlaylistAdd, "Add to queue", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { addPlaylist = true }) { Icon(Icons.Rounded.LibraryAdd, "Playlist", tint = MaterialTheme.colorScheme.primary) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { edit = true }, enabled = !track.isNetwork && (track.uri.toString().endsWith(".mp3", ignoreCase = true) ||
                            context.contentResolver.getType(track.uri) in listOf("audio/mpeg", "audio/mp3", "audio/x-mpeg"))) {
                            Icon(Icons.Rounded.Edit, "Edit MP3 tags", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { confirmDelete = true }, enabled = !track.isNetwork) {
                            Icon(Icons.Rounded.Delete, "Delete file", tint = Color(0xFFFF9191))
                        }
                        IconButton(onClick = { showEqualizer = true }) {
                            Icon(Icons.Rounded.GraphicEq, "Open equalizer", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.background(Color(0x22FF5555), CircleShape)) {
                            Icon(Icons.Rounded.Close, "Close", tint = Color(0xFFFF9B9B))
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF9191))
                }
            }
        }
    }
}

@Composable
fun CompactIconButton(enabled: Boolean = true, onClick: () -> Unit, compact: Boolean = false, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(if (compact) 34.dp else 44.dp)) { content() }
}

/** A 600 ms hold opens the context actions; Enter/OK keeps normal D-Pad activation. */
fun Modifier.trackLongPress(onClick: () -> Unit, onLongPress: (() -> Unit)?): Modifier = this
    .pointerInput(onClick, onLongPress) {
        detectTapGestures(onPress = {
            coroutineScope {
                var invoked = false
                val longPressJob = launch {
                    if (onLongPress != null) {
                        delay(600L)
                        invoked = true
                        onLongPress()
                    }
                }
                val released = tryAwaitRelease()
                longPressJob.cancel()
                if (released && !invoked) onClick()
            }
        })
    }
    .semantics { onClick(label = "Play or open item") { onClick(); true } }
    .onKeyEvent { keyEvent ->
        val native = keyEvent.nativeKeyEvent
        if (native.action == KeyEvent.ACTION_UP && (native.keyCode == KeyEvent.KEYCODE_ENTER ||
            native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            onClick(); true
        } else false
    }
    .focusable()

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TrackRow(
    track: Track,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    showArtwork: Boolean = true,
    compactActions: Boolean = false,
    durationBelow: Boolean = false,
    containerColor: Color? = null,
    onLongPress: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var rowFocused by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().onFocusChanged { rowFocused = it.isFocused }
            .clip(RoundedCornerShape(18.dp))
            .trackLongPress(onClick, onLongPress),
        colors = CardDefaults.cardColors(containerColor = containerColor ?: appCardColor(.05f)),
        border = if (rowFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(horizontal = if (compactActions) 10.dp else 12.dp, vertical = if (compactActions) 9.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showArtwork) {
                TrackArtwork(track.artworkUri, Modifier.size(42.dp), albumPlaceholder = false)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (compactActions) 14.sp else 16.sp)
                Text("${track.artist} • ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = appMuted(.6f), style = MaterialTheme.typography.labelMedium)
                if (durationBelow) Text(formatDuration(track.durationMs), color = appMuted(.55f), style = MaterialTheme.typography.labelSmall)
            }
            if (!durationBelow) Text(formatDuration(track.durationMs), color = appMuted(.55f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = if (compactActions) 2.dp else 6.dp))
            CompactIconButton(onClick = onFavorite, compact = compactActions) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (favorite) MaterialTheme.colorScheme.primary else appIconColor(.6f), modifier = Modifier.size(if (compactActions) 18.dp else 22.dp))
            }
            trailing()
        }
    }
}


@Composable
fun AlbumGrid(albums: List<Album>, vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    var contextAlbum by remember { mutableStateOf<Album?>(null) }
    contextAlbum?.let { selected ->
        FolderContextActionsDialog(selected.name, selected.tracks, vm, kind = "Album") {
            contextAlbum = null
        }
    }
    val tvGrid = settings.tvModeEnabled
    val cellSize = if (settings.landscapeMode) {
        when (settings.landscapePreset) {
            "10" -> 86.dp
            "8" -> 104.dp
            "7" -> 122.dp
            else -> 145.dp
        }
    } else 150.dp
    val columns = if (tvGrid) GridCells.Fixed(settings.tvGridColumns.coerceIn(3, 10)) else GridCells.Adaptive(cellSize)
    val gridGap = if (tvGrid) (12f * settings.tvGridScale.coerceIn(.75f, 1.75f)).dp else 12.dp
    LazyVerticalGrid(columns = columns, modifier = Modifier.fillMaxSize().verticalEdgeFade(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(gridGap), horizontalArrangement = Arrangement.spacedBy(gridGap)) {
        items(albums) { album ->
            val albumIsPlaying = playing && current?.let { track -> album.tracks.any { it.id == track.id } } == true
            Card(Modifier.trackLongPress(onClick = { vm.openAlbum(album) }, onLongPress = { contextAlbum = album }), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = appCardColor(.06f))) {
                Column(Modifier.padding(14.dp)) {
                    TrackArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), albumPlaceholder = true)
                    Spacer(Modifier.height(10.dp))
                    Text(album.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = appMuted(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${album.tracks.size} tracks", color = appMuted(.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (albumIsPlaying) vm.toggle() else vm.play(album.tracks, 0) }, modifier = Modifier.size(36.dp)) {
                            Icon(if (albumIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (albumIsPlaying) "Pause album" else "Play album", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun AlbumStrip(title: String, albums: List<Album>, vm: MusicMonsterViewModel) {
    var contextAlbum by remember { mutableStateOf<Album?>(null) }
    contextAlbum?.let { selected ->
        FolderContextActionsDialog(selected.name, selected.tracks, vm, kind = "Album") {
            contextAlbum = null
        }
    }
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums) { album ->
                Card(Modifier.width(150.dp).trackLongPress(onClick = { vm.setTab(MainTab.Library); vm.openAlbum(album) }, onLongPress = { contextAlbum = album }), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = appCardColor(.06f))) {
                    Column(Modifier.padding(12.dp)) {
                        TrackArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), albumPlaceholder = true)
                        Spacer(Modifier.height(8.dp))
                        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = appMuted(.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistStrip(title: String, artists: List<Artist>, vm: MusicMonsterViewModel) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(artists) { artist ->
                AssistChip(onClick = { vm.setTab(MainTab.Library); vm.openGroup(artist.name, artist.tracks) }, label = { Text("${artist.name} • ${artist.tracks.size}") }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
            }
        }
    }
}

@Composable
fun FolderContextActionsDialog(
    name: String,
    tracks: List<Track>,
    vm: MusicMonsterViewModel,
    kind: String = "Folder",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    val currentIds = remember(tracks) { tracks.map { it.id }.toSet() }
    val settings by vm.settings.collectAsState()
    val groupIsFavorite = remember(settings.favoriteIds, currentIds) {
        val favoriteIds = settings.favoriteIds.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        currentIds.isNotEmpty() && currentIds.all(favoriteIds::contains)
    }
    val localTracks = remember(tracks) { tracks.filterNot { it.isNetwork } }
    val mediaUriTracks = remember(localTracks) {
        localTracks.filter { it.uri.scheme == "content" && it.uri.authority == MediaStore.AUTHORITY }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.scanLibrary()
        onDismiss()
    }
    if (showEqualizer) EqualizerDialog(vm, onDismiss = { showEqualizer = false })
    SceneGlassDialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth(.94f).widthIn(max = 660.dp).sceneGlassPanel(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = overlayPanelColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("$kind • ${tracks.size} tracks", modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center, color = Color.LightGray)
                when {
                    edit -> {
                        Text("Batch edit MP3 tags", modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                        OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                        OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { edit = false }, enabled = !busy,
                                modifier = Modifier.background(Color(0x22FF5555), CircleShape)) {
                                Icon(Icons.Rounded.Close, "Cancel", tint = Color(0xFFFF9292))
                            }
                            IconButton(onClick = {
                                busy = true
                                scope.launch {
                                    val eligible = withContext(Dispatchers.IO) {
                                        localTracks.filter { track ->
                                            track.uri.toString().endsWith(".mp3", true) ||
                                                context.contentResolver.getType(track.uri) in
                                                listOf("audio/mpeg", "audio/mp3", "audio/x-mpeg")
                                        }
                                    }
                                    // All changes are scoped to the tracks already indexed for the
                                    // selected album/folder. Never write directly to a network share.
                                    val updated = withContext(Dispatchers.IO) {
                                        eligible.count { track ->
                                            com.sksdesign.musicmonster.data.Mp3TagEditor.save(
                                                context, track, track.title,
                                                artist.ifBlank { track.artist },
                                                album.ifBlank { track.album },
                                                genre.ifBlank { track.genre }
                                            ).isSuccess
                                        }
                                    }
                                    resultMessage = "Saved $updated of ${eligible.size} writable MP3 files."
                                    busy = false
                                    if (updated > 0) vm.scanLibrary()
                                }
                            }, enabled = !busy && localTracks.isNotEmpty() &&
                                (artist.isNotBlank() || album.isNotBlank() || genre.isNotBlank())) {
                                Icon(Icons.Rounded.Save, "Save tags", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    confirmDelete -> {
                        Text("Delete ${mediaUriTracks.size} original music files?\nThis cannot be undone.",
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = Color.White)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) {
                                Text("No")
                            }
                            Button(onClick = {
                                if (mediaUriTracks.isEmpty() || mediaUriTracks.size != tracks.size || Build.VERSION.SDK_INT < 30) {
                                    resultMessage = "Only writable local MediaStore music can be deleted as a group."
                                } else {
                                    // The OS shows the actual deletion confirmation; nothing is
                                    // removed silently and provider-owned network paths are excluded.
                                    runCatching {
                                        val request = MediaStore.createDeleteRequest(
                                            context.contentResolver, mediaUriTracks.map { it.uri }
                                        )
                                        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                    }.onFailure { resultMessage = it.message ?: "Deletion unavailable" }
                                }
                            }, enabled = !busy && mediaUriTracks.isNotEmpty() && mediaUriTracks.size == tracks.size,
                                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF9E3030), contentColor = Color.White
                                )) { Text("Yes") }
                        }
                    }
                    else -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { vm.toggleGroupFavorites(tracks); onDismiss() }) {
                                Icon(if (groupIsFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    "Favorite all", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { edit = true }, enabled = localTracks.isNotEmpty()) {
                                Icon(Icons.Rounded.Edit, "Edit MP3 tags", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { confirmDelete = true }, enabled = mediaUriTracks.isNotEmpty()) {
                                Icon(Icons.Rounded.Delete, "Delete files", tint = Color(0xFFFF9191))
                            }
                            IconButton(onClick = { showEqualizer = true }) {
                                Icon(Icons.Rounded.GraphicEq, "Equalizer", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onDismiss,
                                modifier = Modifier.background(Color(0x22FF5555), CircleShape)) {
                                Icon(Icons.Rounded.Close, "Close", tint = Color(0xFFFF9B9B))
                            }
                        }
                    }
                }
                if (resultMessage.isNotBlank()) {
                    Text(resultMessage, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun GroupedList(
    items: Map<String, List<Track>>,
    icon: ImageVector = Icons.Rounded.KeyboardArrowDown,
    highlightIcon: Boolean = false,
    onLongPress: ((String, List<Track>) -> Unit)? = null,
    onOpen: (String, List<Track>) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().verticalEdgeFade(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items.toList()) { (name, tracks) ->
            Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).trackLongPress(
                onClick = { onOpen(name, tracks) }, onLongPress = onLongPress?.let { callback -> { callback(name, tracks) } }),
                colors = CardDefaults.cardColors(containerColor = appCardColor(.05f)), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (highlightIcon) MaterialTheme.colorScheme.primary else appMuted(.72f))
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tracks.size} tracks", color = appMuted(.6f))
                }
            }
        }
    }
}

@Composable
fun VisualizerArtwork(
    uri: Uri?,
    modifier: Modifier,
    albumPlaceholder: Boolean,
    settings: AppSettings,
    bars: List<Float>,
    waveform: List<Float>,
    status: VisualizerStatus,
    position: Long,
    safeDuration: Long
) {
    val active = settings.visualizerEnabled && status == VisualizerStatus.Active
    val color = if (settings.visualizerUseAccentColor) Color(settings.accentColor) else Color.White
    Box(modifier.background(MaterialTheme.colorScheme.primary.copy(.14f)), contentAlignment = Alignment.Center) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (albumPlaceholder) Icons.Rounded.Album else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        if (active && settings.visualizerPlacement != "Progress") {
            val overlayHeight = if (settings.visualizerStyle == "Edge") 1f else .20f
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(overlayHeight).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.46f))))) {
                AudioVisualizerCanvas(
                    style = settings.visualizerStyle,
                    bars = bars,
                    waveform = waveform,
                    color = color.copy(alpha = settings.visualizerOpacity.coerceIn(0f, 100f) / 100f),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (settings.visualizerStyle == "Edge") 8.dp else 4.dp)
                )
            }
        }
    }
}

@Composable
fun AudioVisualizerCanvas(style: String, bars: List<Float>, waveform: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val normalizedBars = if (bars.isEmpty()) List(32) { 0f } else bars
    val normalizedWave = if (waveform.isEmpty()) List(48) { 0f } else waveform
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            "Wave" -> {
                val step = if (normalizedWave.size <= 1) w else w / (normalizedWave.size - 1)
                var previous = Offset(0f, h * .92f)
                normalizedWave.forEachIndexed { index, value ->
                    val x = index * step
                    val y = (h * .92f) - value.coerceIn(-1f, 1f) * h * .62f
                    val point = Offset(x, y.coerceIn(0f, h))
                    if (index > 0) drawLine(color = color, start = previous, end = point, strokeWidth = max(2.5f, h * .035f), cap = StrokeCap.Round)
                    previous = point
                }
            }
            "Smooth Wave" -> {
                val step = if (normalizedWave.size <= 1) w else w / (normalizedWave.size - 1)
                var previous = Offset(0f, h * .90f)
                normalizedWave.forEachIndexed { index, value ->
                    val x = index * step
                    val y = (h * .90f) - value.coerceIn(-1f, 1f) * h * .58f
                    val point = Offset(x, y.coerceIn(0f, h))
                    if (index > 0) {
                        val mid = Offset((previous.x + point.x) / 2f, (previous.y + point.y) / 2f)
                        drawLine(color = color.copy(alpha = color.alpha * .85f), start = previous, end = mid, strokeWidth = max(3f, h * .045f), cap = StrokeCap.Round)
                        drawLine(color = color, start = mid, end = point, strokeWidth = max(3f, h * .045f), cap = StrokeCap.Round)
                    }
                    previous = point
                }
            }
            "Bars" -> {
                val visualCount = min(18, normalizedBars.size).coerceAtLeast(8)
                val sampled = List(visualCount) { idx -> normalizedBars[(idx * normalizedBars.size / visualCount).coerceIn(0, normalizedBars.lastIndex)] }
                val gap = max(5f, w * .012f)
                val barWidth = max(9f, (w - gap * (sampled.size - 1)) / sampled.size)
                sampled.forEachIndexed { index, value ->
                    val height = (value.coerceIn(0f, 1f) * h).coerceAtLeast(5f)
                    val x = index * (barWidth + gap)
                    drawRoundRect(color = color, topLeft = Offset(x, h - height), size = Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2.6f, barWidth / 2.6f))
                }
            }
            "Pulse" -> {
                val average = normalizedBars.take(8).ifEmpty { listOf(0f) }.average().toFloat().coerceIn(0f, 1f)
                val center = Offset(w / 2f, h * .70f)
                val radius = min(w, h) * (.16f + average * .42f)
                drawCircle(color = color.copy(alpha = color.alpha * .26f), radius = radius * 1.55f, center = center)
                drawCircle(color = color.copy(alpha = color.alpha * .48f), radius = radius, center = center)
                drawCircle(color = color, radius = max(5f, radius * .16f), center = center)
            }
            "Ribbon" -> {
                val count = min(28, normalizedBars.size).coerceAtLeast(12)
                val step = w / count
                normalizedBars.take(count).forEachIndexed { index, value ->
                    val v = value.coerceIn(0f, 1f)
                    val x = index * step + step / 2f
                    val height = (v * h * .56f).coerceAtLeast(4f)
                    drawLine(color.copy(alpha = color.alpha * .45f), Offset(x, h), Offset(x, h - height), strokeWidth = max(3f, step * .30f), cap = StrokeCap.Round)
                    drawLine(color, Offset(x, h - height * .45f), Offset(x, h - height), strokeWidth = max(2.5f, step * .18f), cap = StrokeCap.Round)
                }
            }
            "Edge" -> {
                val count = normalizedBars.size.coerceAtLeast(16)
                val bottomCount = count / 2
                val sideCount = (count - bottomCount) / 2
                val cornerInset = min(w, h) * .07f
                for (i in 0 until bottomCount) {
                    val v = normalizedBars[(i * normalizedBars.size / bottomCount).coerceIn(0, normalizedBars.lastIndex)]
                    val x = cornerInset + (w - cornerInset * 2f) * (i / (bottomCount - 1).toFloat().coerceAtLeast(1f))
                    val len = (v * h * .22f).coerceAtLeast(4f)
                    drawLine(color, Offset(x, h), Offset(x, h - len), strokeWidth = max(3f, w / 150f), cap = StrokeCap.Round)
                }
                for (i in 0 until sideCount) {
                    val left = normalizedBars[(i * normalizedBars.size / sideCount).coerceIn(0, normalizedBars.lastIndex)]
                    val right = normalizedBars[((i + sideCount) * normalizedBars.size / count).coerceIn(0, normalizedBars.lastIndex)]
                    val y = cornerInset + (h - cornerInset * 2f) * (i / (sideCount - 1).toFloat().coerceAtLeast(1f))
                    drawLine(color.copy(alpha = color.alpha * .75f), Offset(0f, y), Offset(left * w * .12f, y), strokeWidth = max(3f, h / 150f), cap = StrokeCap.Round)
                    drawLine(color.copy(alpha = color.alpha * .75f), Offset(w, y), Offset(w - right * w * .12f, y), strokeWidth = max(3f, h / 150f), cap = StrokeCap.Round)
                }
            }
            else -> {
                val step = w / normalizedBars.size.coerceAtLeast(1)
                normalizedBars.forEachIndexed { index, value ->
                    val height = (value.coerceIn(0f, 1f) * h).coerceAtLeast(5f)
                    val x = index * step + step / 2f
                    drawLine(color = color, start = Offset(x, h), end = Offset(x, h - height), strokeWidth = max(2.5f, step * .34f), cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun TrackArtwork(uri: Uri?, modifier: Modifier, albumPlaceholder: Boolean) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(.14f)), contentAlignment = Alignment.Center) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (albumPlaceholder) Icons.Rounded.Album else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
        }
    }
}




fun Modifier.bigPlayerCoverGestures(vm: MusicMonsterViewModel, track: Track, onFeedback: () -> Unit = {}): Modifier = this
    .swipeHorizontal(
        onNext = vm::next,
        onPrevious = vm::previous
    )
    .pointerInput(track.id) {
        detectTapGestures(onDoubleTap = { vm.toggleFavorite(track); onFeedback() })
    }

fun Modifier.swipeHorizontal(onNext: () -> Unit, onPrevious: () -> Unit): Modifier = pointerInput(onNext, onPrevious) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
        onDragEnd = {
            when {
                totalDrag < -90f -> onNext()
                totalDrag > 90f -> onPrevious()
            }
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f }
    )
}

fun moveHomeSection(vm: MusicMonsterViewModel, sections: List<HomeSection>, current: HomeSection, delta: Int) {
    val index = sections.indexOf(current)
    if (index < 0) return
    val next = (index + delta).coerceIn(0, sections.lastIndex)
    if (next != index) vm.setHome(sections[next])
}

fun moveLibrarySection(vm: MusicMonsterViewModel, sections: List<LibrarySection>, current: LibrarySection, delta: Int) {
    val index = sections.indexOf(current)
    if (index < 0) return
    val next = (index + delta).coerceIn(0, sections.lastIndex)
    if (next != index) vm.setLibrary(sections[next])
}

fun homeSections(settings: AppSettings): List<HomeSection> {
    val allowed = HomeSection.values().toList()
    val parsed = settings.homeOrder.split(',').mapNotNull { name -> runCatching { HomeSection.valueOf(name) }.getOrNull() }.filter { it in allowed }
    val hidden = settings.hiddenHomeSections.split(',').filter { it.isNotBlank() }.toSet()
    return (parsed + allowed).distinct().filter { it.name !in hidden }.ifEmpty { listOf(HomeSection.Home) }
}

fun homeLabel(section: HomeSection, language: String): String = tr(language, section.name)

fun tr(language: String, key: String): String {
    val table = translations[language] ?: return key
    return table[key] ?: key
}

val translations = mapOf(
    "de" to mapOf("Home" to "Start", "Library" to "Bibliothek", "Search" to "Suche", "Playlists" to "Playlists", "Settings" to "Einstellungen", "Discovery" to "Entdecken", "Favorites" to "Favoriten", "Top" to "Top", "History" to "Verlauf", "Albums" to "Alben", "Artists" to "Künstler"),
    "es" to mapOf("Home" to "Inicio", "Library" to "Biblioteca", "Search" to "Buscar", "Playlists" to "Listas", "Settings" to "Ajustes", "Discovery" to "Descubrir", "Favorites" to "Favoritos", "Top" to "Top", "History" to "Historial", "Albums" to "Álbumes", "Artists" to "Artistas"),
    "fr" to mapOf("Home" to "Accueil", "Library" to "Bibliothèque", "Search" to "Recherche", "Playlists" to "Playlists", "Settings" to "Réglages", "Discovery" to "Découverte", "Favorites" to "Favoris", "Top" to "Top", "History" to "Historique", "Albums" to "Albums", "Artists" to "Artistes"),
    "it" to mapOf("Home" to "Home", "Library" to "Libreria", "Search" to "Cerca", "Playlists" to "Playlist", "Settings" to "Impostazioni", "Discovery" to "Scopri", "Favorites" to "Preferiti", "Top" to "Top", "History" to "Cronologia", "Albums" to "Album", "Artists" to "Artisti"),
    "pl" to mapOf("Home" to "Start", "Library" to "Biblioteka", "Search" to "Szukaj", "Playlists" to "Playlisty", "Settings" to "Ustawienia", "Discovery" to "Odkrywaj", "Favorites" to "Ulubione", "Top" to "Top", "History" to "Historia", "Albums" to "Albumy", "Artists" to "Artyści"),
    "ar" to mapOf("Home" to "الرئيسية", "Library" to "المكتبة", "Search" to "بحث", "Playlists" to "القوائم", "Settings" to "الإعدادات", "Discovery" to "استكشاف", "Favorites" to "المفضلة", "Top" to "الأكثر", "History" to "السجل"),
    "fa" to mapOf("Home" to "خانه", "Library" to "کتابخانه", "Search" to "جستجو", "Playlists" to "فهرست‌ها", "Settings" to "تنظیمات", "Discovery" to "کشف", "Favorites" to "علاقه‌مندی‌ها", "Top" to "برتر", "History" to "تاریخچه"),
    "ja" to mapOf("Home" to "ホーム", "Library" to "ライブラリ", "Search" to "検索", "Playlists" to "プレイリスト", "Settings" to "設定", "Discovery" to "発見", "Favorites" to "お気に入り", "Top" to "トップ", "History" to "履歴"),
    "zh" to mapOf("Home" to "首页", "Library" to "资料库", "Search" to "搜索", "Playlists" to "播放列表", "Settings" to "设置", "Discovery" to "发现", "Favorites" to "收藏", "Top" to "热门", "History" to "历史"),
    "ru" to mapOf("Home" to "Главная", "Library" to "Библиотека", "Search" to "Поиск", "Playlists" to "Плейлисты", "Settings" to "Настройки", "Discovery" to "Открытия", "Favorites" to "Избранное", "Top" to "Топ", "History" to "История"),
    "uk" to mapOf("Home" to "Головна", "Library" to "Бібліотека", "Search" to "Пошук", "Playlists" to "Плейлисти", "Settings" to "Налаштування", "Discovery" to "Відкриття", "Favorites" to "Обране", "Top" to "Топ", "History" to "Історія"),
    "sv" to mapOf("Home" to "Hem", "Library" to "Bibliotek", "Search" to "Sök", "Playlists" to "Spellistor", "Settings" to "Inställningar", "Discovery" to "Upptäck", "Favorites" to "Favoriter", "Top" to "Topp", "History" to "Historik"),
    "da" to mapOf("Home" to "Hjem", "Library" to "Bibliotek", "Search" to "Søg", "Playlists" to "Playlister", "Settings" to "Indstillinger", "Discovery" to "Opdag", "Favorites" to "Favoritter", "Top" to "Top", "History" to "Historik"),
    "fi" to mapOf("Home" to "Koti", "Library" to "Kirjasto", "Search" to "Haku", "Playlists" to "Soittolistat", "Settings" to "Asetukset", "Discovery" to "Löydä", "Favorites" to "Suosikit", "Top" to "Top", "History" to "Historia"),
    "nl" to mapOf("Home" to "Home", "Library" to "Bibliotheek", "Search" to "Zoeken", "Playlists" to "Afspeellijsten", "Settings" to "Instellingen", "Discovery" to "Ontdekken", "Favorites" to "Favorieten", "Top" to "Top", "History" to "Geschiedenis"),
    "cs" to mapOf("Home" to "Domů", "Library" to "Knihovna", "Search" to "Hledat", "Playlists" to "Playlisty", "Settings" to "Nastavení", "Discovery" to "Objevit", "Favorites" to "Oblíbené", "Top" to "Top", "History" to "Historie")
)

fun parseEqBands(value: String): List<Float> {
    val parsed = value.split(',').mapNotNull { it.trim().toFloatOrNull() }.take(5)
    return (parsed + List(5) { 0f }).take(5)
}

fun formatEqBands(values: List<Float>): String = values.take(5).joinToString(",") { String.format(Locale.US, "%.2f", it.coerceIn(-10f, 10f)) }

fun equalizerPresets(): Map<String, List<Float>> = linkedMapOf(
    "Custom" to parseEqBands("0,0,0,0,0"),
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
    "Bass Boost" to listOf(6f, 4f, 1f, 0f, 0f),
    "Bass & Treble Boost" to listOf(6f, 3f, 0f, 3f, 6f),
    "Low" to listOf(4f, 2f, 0f, -1f, -2f),
    "Reverb" to listOf(1f, 2f, 3f, 2f, 1f),
    "Hall" to listOf(2f, 3f, 2f, 3f, 2f),
    "Rock" to listOf(5f, 3f, -1f, 3f, 5f),
    "Disco" to listOf(4f, 2f, 0f, 2f, 4f),
    "Classic" to listOf(3f, 2f, 0f, 2f, 3f),
    "Vocal" to listOf(-1f, 1f, 4f, 3f, 1f)
)

fun librarySections(settings: AppSettings): List<LibrarySection> {
    val base = baseLibraryOrder(settings).toMutableList()
    if (settings.enableSoundCloud) base += LibrarySection.SoundCloud
    if (settings.enableSpotify) base += LibrarySection.Spotify
    if (settings.enableYouTubeMusic) base += LibrarySection.YouTubeMusic
    if (settings.enableYouTube) base += LibrarySection.YouTube
    if (settings.enableCustomWeb) base += LibrarySection.CustomWeb
    val hidden = settings.hiddenLibrarySections.split(',').filter { it.isNotBlank() }.toSet()
    return base.distinct().filter { it.name !in hidden }.ifEmpty { listOf(LibrarySection.Tracks) }
}

fun baseLibraryOrder(settings: AppSettings): List<LibrarySection> {
    val allowed = listOf(LibrarySection.Albums, LibrarySection.Tracks, LibrarySection.Folders, LibrarySection.Genres, LibrarySection.Artists, LibrarySection.Podcasts, LibrarySection.OST)
    val orderValue = when (settings.libraryOrder) {
        "Albums,Tracks,Artists,Genres,Podcasts,Folders",
        "Albums,Tracks,Artists,Genres,Folders,Podcasts" -> "Albums,Tracks,Folders,Genres,Artists,Podcasts,OST"
        "Albums,Tracks,Folders,Genres,Podcasts,Artists" -> "Albums,Tracks,Folders,Genres,Artists,Podcasts,OST"
        else -> settings.libraryOrder
    }
    val parsed = orderValue.split(',').mapNotNull { name -> runCatching { LibrarySection.valueOf(name) }.getOrNull() }.filter { it in allowed }
    return (parsed + allowed).distinct()
}

fun libraryLabel(section: LibrarySection, settings: AppSettings): String = when (section) {
    LibrarySection.CustomWeb -> settings.customWebName.ifBlank { "Custom Web Library" }
    LibrarySection.YouTubeMusic -> "YouTube Music"
    LibrarySection.YouTube -> "YouTube"
    else -> section.name
}

fun isWebLibrary(section: LibrarySection): Boolean = section == LibrarySection.SoundCloud || section == LibrarySection.Spotify || section == LibrarySection.YouTubeMusic || section == LibrarySection.YouTube || section == LibrarySection.CustomWeb

fun startupOptions(settings: AppSettings): List<String> = buildList {
    addAll(HomeSection.values().map { it.name })
    addAll(baseLibraryOrder(settings).map { it.name })
    if (settings.enableSoundCloud) add("SoundCloud")
    if (settings.enableSpotify) add("Spotify")
    if (settings.enableYouTubeMusic) add("YouTubeMusic")
    if (settings.enableYouTube) add("YouTube")
    if (settings.enableCustomWeb) add("CustomWeb")
    add("Search")
    add("Playlists")
}

fun parseColor(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    val normalized = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return normalized.toLongOrNull(16)
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}


fun looksLikeSocialAudio(track: Track): Boolean {
    val text = listOf(track.folder, track.album, track.genre, track.title, track.uri.toString()).joinToString("/").lowercase()
    val markers = listOf("whatsapp", "voice notes", "voicenotes", "messenger", "facebook", "telegram", "signal", "instagram", "snapchat", "viber", "discord", "skype")
    return markers.any { text.contains(it) }
}

fun colorToHue(color: Long): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toInt(), hsv)
    return hsv[0].coerceIn(0f, 360f)
}

fun hueToColor(hue: Float): Long {
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), 0.86f, 1.0f))
    return color.toLong() and 0xFFFFFFFF
}
