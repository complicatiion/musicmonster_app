package com.sksdesign.musicmonster

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.sksdesign.musicmonster.player.PlayerNotificationController
import com.sksdesign.musicmonster.ui.WebLibraryScreen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.math.min
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicMonsterApp() }
    }
}

class MusicMonsterViewModel(private val context: android.content.Context) : ViewModel() {
    private val repo = MusicRepository(context)
    private val store = SettingsStore(context)
    val player = PlayerController(context)
    private val notifications = PlayerNotificationController(context)
    private val mediaSession = PlayerMediaSession(context, player)

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
    private val _carMode = MutableStateFlow(false)
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
    val carMode = _carMode.asStateFlow()
    val currentTrack = player.currentTrack
    val isPlaying = player.isPlaying
    val positionMs = player.positionMs
    val durationMs = player.durationMs
    val shuffle = player.shuffle
    val repeatMode = player.repeatMode
    val queue = player.queue
    val queueIndex = player.queueIndex

    val filteredTracks = combine(_tracks, _query) { tracks, q ->
        if (q.isBlank()) tracks else tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.genre.contains(q, true) || it.folder.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        MusicMonsterRuntime.controller = player
        viewModelScope.launch {
            val cachedTracks = repo.loadCachedTracks().filter { settings.value.includeSocialAudio || !looksLikeSocialAudio(it) }
            val cachedPodcasts = repo.loadCachedPodcasts().filter { settings.value.includeSocialAudio || !looksLikeSocialAudio(it) }
            val cachedPlaylists = repo.loadCachedPlaylists()
            if (cachedTracks.isNotEmpty()) _tracks.value = cachedTracks
            if (cachedPodcasts.isNotEmpty()) _podcasts.value = cachedPodcasts
            if (cachedPlaylists.isNotEmpty()) _playlists.value = cachedPlaylists
            _cacheReady.value = true
        }
        viewModelScope.launch {
            combine(player.currentTrack, player.isPlaying, player.positionMs) { track, playing, position -> Triple(track, playing, position) }
                .collect { (track, playing, position) ->
                    notifications.update(track, playing)
                    mediaSession.update(track, playing, position)
                }
        }
        viewModelScope.launch {
            _cacheReady.filter { it }.first()
            val s = settings.first()
            if (s.autoScanEnabled) {
                val intervalMs = s.autoScanIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
                if (System.currentTimeMillis() - s.lastAutoScanAt >= intervalMs) {
                    scanLibrary()
                }
            }
        }
        viewModelScope.launch {
            settings.collect { player.applyAudioSettings(it) }
        }
    }

    fun scanLibraryIfEmpty() = viewModelScope.launch {
        _cacheReady.filter { it }.first()
        if (_tracks.value.isEmpty()) scanLibrary()
    }

    private fun mergeTrackLists(primary: List<Track>, extra: List<Track>): List<Track> {
        return (primary + extra)
            .distinctBy { it.uri.toString() }
            .sortedWith(compareBy<Track> { it.album.lowercase() }.thenBy { it.trackNumber }.thenBy { it.title.lowercase() })
    }

    private suspend fun customFolderTracks(s: AppSettings): List<Track> {
        return if (s.customFolderUri.isNotBlank()) repo.loadTracks(s.customFolderUri, s.includeSocialAudio) else emptyList()
    }

    fun scanLibrary() = viewModelScope.launch {
        val s = settings.value
        val baseTracks = repo.loadTracks(s.musicFolderUri, s.includeSocialAudio)
        val loadedTracks = mergeTrackLists(baseTracks, customFolderTracks(s))
        val loadedPodcasts = repo.loadPodcasts(s.podcastFolderUri, loadedTracks, s.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
        store.save(s.copy(lastAutoScanAt = System.currentTimeMillis()))
        _libraryDetail.value = null
    }

    fun scanDeviceLibrary() = viewModelScope.launch {
        val s = settings.value
        val baseTracks = repo.loadTracks("", s.includeSocialAudio)
        val loadedTracks = mergeTrackLists(baseTracks, customFolderTracks(s))
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
        val s = settings.value.copy(musicFolderUri = value)
        store.save(s)
        val loadedTracks = mergeTrackLists(repo.loadTracks(value, s.includeSocialAudio), customFolderTracks(s))
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

    fun clearLocalLibraryCache() = viewModelScope.launch {
        _tracks.value = emptyList()
        _podcasts.value = emptyList()
        _libraryDetail.value = null
        repo.clearCachedLibrary()
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
    fun showQueue(show: Boolean) { _showQueue.value = show }
    fun cycleCarShortcut() = viewModelScope.launch {
        val landscape = settings.value.landscapeMode
        when {
            !_carMode.value -> _carMode.value = true
            !landscape -> store.save(settings.value.copy(landscapeMode = true))
            else -> {
                store.save(settings.value.copy(landscapeMode = false))
                _carMode.value = false
            }
        }
    }

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
        return listOf(
            Playlist("Recently Added", recentlyAdded),
            Playlist("Favorite Tracks", favorites),
            Playlist("Most Played", mostPlayed),
            Playlist("Recently Played", recentlyPlayed),
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

    fun randomTracks(count: Int): List<Track> = _tracks.value.shuffled(Random(launchSeed)).take(count)
    fun randomAlbums(count: Int): List<Album> = repo.albums(_tracks.value).shuffled(Random(launchSeed + 21)).take(count)

    fun albums(): List<Album> = repo.albums(_tracks.value)
    fun artists(): List<Artist> = repo.artists(_tracks.value)

    fun isFavorite(track: Track): Boolean = favoriteSet().contains(track.id)

    fun toggleFavorite(track: Track) = viewModelScope.launch {
        val ids = favoriteSet().toMutableSet()
        if (!ids.add(track.id)) ids.remove(track.id)
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

    override fun onCleared() {
        notifications.cancel()
        mediaSession.release()
        MusicMonsterRuntime.controller = null
        player.release()
    }
}

@Suppress("UNCHECKED_CAST")
class MusicMonsterViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MusicMonsterViewModel(context.applicationContext) as T
}

@Composable
fun MusicMonsterApp() {
    val context = LocalContext.current
    val vm: MusicMonsterViewModel = viewModel(factory = MusicMonsterViewModelFactory(context))
    val settings by vm.settings.collectAsState()
    val accent = Color(settings.accentColor)
    val bg = when {
        settings.lightMode -> Color(0xFFF5F7F2)
        settings.amoledBlack -> Color.Black
        else -> Color(0xFF0D1010)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val audioGranted = if (Build.VERSION.SDK_INT >= 33) result[Manifest.permission.READ_MEDIA_AUDIO] == true else result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) vm.scanLibraryIfEmpty()
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permission.launch(permissions)
    }
    LaunchedEffect(Unit) {
        delay(250)
        vm.applyStartPage(vm.settings.value.startPage)
    }
    LaunchedEffect(settings.landscapeMode) {
        (context as? Activity)?.requestedOrientation = if (settings.landscapeMode) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    MaterialTheme(
        colorScheme = if (settings.lightMode) {
            lightColorScheme(primary = accent, onPrimary = Color.Black, primaryContainer = accent, onPrimaryContainer = Color.Black, secondary = accent, onSecondary = Color.Black, background = bg, surface = Color.White, onSurface = Color(0xFF111414))
        } else {
            darkColorScheme(primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White, secondary = accent, onSecondary = Color.White, background = bg, surface = Color(0xFF151919), onSurface = Color.White)
        }
    ) {
        Surface(Modifier.fillMaxSize(), color = bg) { MainScaffold(vm) }
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopBar(vm) },
            containerColor = MaterialTheme.colorScheme.background
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    MainTab.Home -> HomeScreen(vm)
                    MainTab.Library -> LibraryScreen(vm)
                    MainTab.Search -> SearchScreen(vm)
                    MainTab.Playlists -> PlaylistsScreen(vm)
                    MainTab.Settings -> SettingsScreen(vm)
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
                    .padding(bottom = if (navExpanded) 78.dp else 18.dp)
            )
        }
        DockedBottomNavigation(
            selected = tab,
            language = settings.language,
            expanded = navExpanded,
            allowCollapse = settings.dockStartsCollapsed,
            onExpandedChange = { navExpandedState = it },
            onSelect = { selected -> vm.setTab(selected); if (settings.dockStartsCollapsed) navExpandedState = false },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        if (fullPlayer && track != null) FullPlayerOverlay(vm, carMode)
        if (showQueue) QueueOverlay(vm)
    }
}

@Composable
fun TopBar(vm: MusicMonsterViewModel) {
    val carMode by vm.carMode.collectAsState()
    val settings by vm.settings.collectAsState()
    val track by vm.currentTrack.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }
    if (showEqualizer) EqualizerDialog(vm = vm, onDismiss = { showEqualizer = false })
    val activeTint = if (track != null) MaterialTheme.colorScheme.primary else appIconColor(.82f)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = vm::cycleCarShortcut, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = "Car mode", tint = if (carMode || settings.landscapeMode) MaterialTheme.colorScheme.primary else appIconColor(.75f))
            }
            if (carMode || settings.landscapeMode) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (settings.landscapeMode) "2" else "1",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        IconButton(onClick = { vm.setTab(MainTab.Settings) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = appIconColor(.82f))
        }
    }
}

@Composable
fun EqualizerDialog(vm: MusicMonsterViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier
                .fillMaxWidth(if (settings.landscapeMode) .88f else .94f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = if (isLightThemeActive()) Color.White else Color(0xFF151919))
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
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(appMuted(.08f)), contentAlignment = Alignment.Center) {
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
fun appCardColor(alpha: Float = .06f): Color = if (isLightThemeActive()) {
    Color(0xFFFFFFFF)
} else {
    Color.White.copy(alpha)
}

@Composable
fun appNavColor(): Color = if (isLightThemeActive()) Color(0xF4FFFFFF) else Color(0xEE151919)

@Composable
fun appIconColor(alpha: Float = .82f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

fun landscapeScale(settings: AppSettings): Float {
    val presetScale = when (settings.landscapePreset) {
        "10" -> 0.72f
        "8" -> 0.82f
        "7" -> 0.92f
        else -> 1.0f
    }
    return (presetScale * settings.uiScale).coerceIn(0.65f, 1.25f)
}

@Composable
fun BottomNav(selected: MainTab, language: String, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = appNavColor()) {
        MainTab.values().forEach { t ->
            val label = if (t == MainTab.Library && language == "en") "Librarys" else tr(language, t.name)
            NavigationBarItem(
                selected = selected == t,
                onClick = { onSelect(t) },
                icon = { Icon(iconFor(t), null) },
                label = { Text(label) },
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
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = appNavColor())
            ) {
                BottomNav(selected, language, onSelect)
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
    if (carMode) {
        Card(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = if (isLightThemeActive()) Color(0xF6FFFFFF) else Color(0xF0151919)),
            shape = RoundedCornerShape(34.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, modifier = Modifier.size(70.dp)) { Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(40.dp)) }
                FilledIconButton(
                    onClick = vm::toggle,
                    shape = CircleShape,
                    modifier = Modifier.size(78.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(42.dp), tint = Color.White) }
                IconButton(onClick = vm::next, modifier = Modifier.size(70.dp)) { Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(40.dp)) }
            }
        }
        return
    }

    val settings by vm.settings.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val favorite = vm.isFavorite(track)
    val scale = if (settings.landscapeMode) landscapeScale(settings).coerceIn(.78f, 1.05f) else 1f
    val shape = RoundedCornerShape((26f * scale).dp)
    val container = if (isLightThemeActive()) Color(0xF6FFFFFF) else Color(0xEE151919)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height((74f * scale).dp)
    ) {
        Card(
            Modifier
                .matchParentSize()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(.28f), shape)
                .clickable { vm.showFullPlayer(true) },
            colors = CardDefaults.cardColors(containerColor = container),
            shape = shape
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = (10f * scale).dp, vertical = (8f * scale).dp)) {
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxHeight().widthIn(max = if (settings.landscapeMode) (260f * scale).dp else 136.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(track.artworkUri, Modifier.size((50f * scale).dp), albumPlaceholder = true)
                    Spacer(Modifier.width((10f * scale).dp))
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
                    horizontalArrangement = Arrangement.spacedBy((0f * scale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::toggleShuffle, modifier = Modifier.size((36f * scale).dp)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size((20f * scale).dp))
                    }
                    IconButton(onClick = vm::cycleRepeatMode, modifier = Modifier.size((36f * scale).dp)) {
                        Icon(if (repeat == RepeatModeUi.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (repeat != RepeatModeUi.Off) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size((20f * scale).dp))
                    }
                    IconButton(onClick = { vm.toggleFavorite(track) }, modifier = Modifier.size((36f * scale).dp)) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else appIconColor(.72f), modifier = Modifier.size((20f * scale).dp))
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
    val albums = remember(tracks) { vm.albums().take(10) }
    val artists = remember(tracks) { vm.artists().take(10) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .swipeHorizontal(
                onNext = { moveHomeSection(vm, sections, section, 1) },
                onPrevious = { moveHomeSection(vm, sections, section, -1) }
            ),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { ChipRow(sections.map { homeLabel(it, settings.language) }, homeLabel(section, settings.language)) { label -> sections.firstOrNull { homeLabel(it, settings.language) == label }?.let(vm::setHome) } }
        when (section) {
            HomeSection.Home -> {
                item { AlbumStrip(tr(settings.language, "Albums"), albums, vm) }
                item { ArtistStrip(tr(settings.language, "Artists"), artists, vm) }
                item { TrackSection("Custom Picks", vm.randomTracks(10), vm) }
                item { PlaylistSection(tr(settings.language, "Playlists"), playlists) }
            }
            HomeSection.Discovery -> item { Discovery(vm, tracks) }
            HomeSection.Favorites -> item { TrackSection("Favorite Tracks", tracks.filter { vm.isFavorite(it) }, vm) }
            HomeSection.Top -> item { TrackSection("Top Tracks", tracks.sortedByDescending { it.playCount }.ifEmpty { tracks.take(10) }, vm) }
            HomeSection.History -> item { TrackSection("History", tracks.sortedByDescending { it.lastPlayed }.ifEmpty { tracks.take(10) }, vm) }
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

@Composable
fun LibraryScreen(vm: MusicMonsterViewModel) {
    val settings by vm.settings.collectAsState()
    val section by vm.librarySection.collectAsState()
    val detail by vm.libraryDetail.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val podcasts by vm.podcasts.collectAsState()
    val sections = librarySections(settings)

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
        if (detail != null) {
            LibraryDetailScreen(detail!!, vm)
        } else {
            ChipRow(sections.map { libraryLabel(it, settings) }, libraryLabel(section, settings)) { selectedLabel ->
                val selected = sections.firstOrNull { libraryLabel(it, settings) == selectedLabel } ?: LibrarySection.Albums
                vm.setLibrary(selected)
            }
            Spacer(Modifier.height(12.dp))
            when (section) {
                LibrarySection.Tracks -> TrackList(tracks, vm)
                LibrarySection.Albums -> AlbumGrid(vm.albums(), vm)
                LibrarySection.Artists -> GroupedList(vm.artists().associate { it.name to it.tracks }) { name, list -> vm.openGroup(name, list) }
                LibrarySection.Genres -> GroupedList(tracks.groupBy { it.genre.ifBlank { "Unknown" } }.toSortedMap()) { name, list -> vm.openGroup(name, list) }
                LibrarySection.Folders -> GroupedList(tracks.groupBy { it.folder.ifBlank { "Music" } }.toSortedMap()) { name, list -> vm.openGroup(name, list) }
                LibrarySection.Podcasts -> PodcastScreen(podcasts, vm)
                LibrarySection.SoundCloud -> WebLibraryScreen("SoundCloud", "https://soundcloud.com", Modifier.fillMaxSize())
                LibrarySection.Spotify -> WebLibraryScreen("Spotify", "https://open.spotify.com", Modifier.fillMaxSize())
                LibrarySection.YouTubeMusic -> WebLibraryScreen("YouTube Music", "https://music.youtube.com", Modifier.fillMaxSize())
                LibrarySection.YouTube -> WebLibraryScreen("YouTube", "https://www.youtube.com", Modifier.fillMaxSize())
                LibrarySection.CustomWeb -> WebLibraryScreen(settings.customWebName.ifBlank { "Web Library" }, settings.customWebUrl, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun LibraryDetailScreen(detail: LibraryDetail, vm: MusicMonsterViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeDetail) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(detail.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail.subtitle, color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = { vm.play(detail.tracks, 0) }, shape = RoundedCornerShape(22.dp)) { Text("Play") }
        }
        Spacer(Modifier.height(12.dp))
        TrackList(detail.tracks, vm)
    }
}

@Composable
fun PodcastScreen(podcasts: List<Track>, vm: MusicMonsterViewModel) {
    if (podcasts.isEmpty()) {
        HeroCard("Podcasts", "Select a podcast folder in Settings", "Local podcast files appear here after scanning the selected folder.")
        return
    }
    val grouped = podcasts.groupBy { it.folder.ifBlank { "Podcasts" } }.toSortedMap()
    val folderGroups = grouped.filter { it.value.size > 1 }
    val singles = grouped.filter { it.value.size == 1 }.flatMap { it.value }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (folderGroups.isNotEmpty()) {
            item { Text("Podcast folders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
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
            item { Text("Single episodes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            items(singles.indices.toList()) { index ->
                val track = singles[index]
                TrackRow(track, vm.isFavorite(track), { vm.toggleFavorite(track) }, { vm.play(singles, index) }, showArtwork = true) {
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
            if (onExport != null) IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.IosShare, contentDescription = "Export", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onPlay, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
fun SmartPlaylistDetailScreen(playlist: Playlist, vm: MusicMonsterViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary) }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closePlaylist) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = { onExport(playlist) }) { Icon(Icons.Rounded.IosShare, contentDescription = "Export") }
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
fun QueueOverlay(vm: MusicMonsterViewModel) {
    val queue by vm.queue.collectAsState()
    val queueIndex by vm.queueIndex.collectAsState()
    BackHandler { vm.showQueue(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp), modifier = Modifier.fillMaxSize()) {
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
    val context = LocalContext.current
    var webUrl by remember(settings.customWebUrl) { mutableStateOf(settings.customWebUrl) }
    var webName by remember(settings.customWebName) { mutableStateOf(settings.customWebName) }
    var customColor by remember(settings.accentColor) { mutableStateOf("#" + settings.accentColor.toString(16).takeLast(6).uppercase()) }
    var playlistName by remember { mutableStateOf("") }

    val musicFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setMusicFolder(it)
        }
    }
    val podcastFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setPodcastFolder(it)
        }
    }
    val customFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setCustomFolder(it)
        }
    }
    val exportSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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
    val exportFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(vm.exportFavoritesJson().toByteArray()) } }
    }
    val importFavoritesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> vm.importFavoritesJson(reader.readText()) } }
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader() }
        item { SettingsSectionTitle("Equalizer") }
        item { EqualizerCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Audio") }
        item { AudioOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Appearance") }
        item { AccentColorPicker(settings, customColor, { customColor = it }, vm::updateSettings) }
        item { SettingsSwitch("AMOLED black background", settings.amoledBlack) { vm.updateSettings(settings.copy(amoledBlack = it, lightMode = false)) } }
        item { SettingsSwitch("Light mode", settings.lightMode) { vm.updateSettings(settings.copy(lightMode = it, amoledBlack = false)) } }

        item { SettingsSectionTitle("Landscape Mode (Car Mode 2)") }
        item { SettingsSwitch("Enable landscape layout", settings.landscapeMode) { vm.updateSettings(settings.copy(landscapeMode = it)) } }
        item { ChipRow(listOf("6 inch", "7 inch", "8 inch", "10 inch"), "${settings.landscapePreset} inch") { selected -> vm.updateSettings(settings.copy(landscapePreset = selected.substringBefore(" "))) } }
        item { DisplayDockOptionsCard(settings, vm::updateSettings) }

        item { SettingsSectionTitle("Localization") }
        item { LanguagePicker(settings) { vm.updateSettings(settings.copy(language = it)) } }

        item { SettingsSectionTitle("Startup") }
        item { StartupPicker(settings) { vm.updateSettings(settings.copy(startPage = it)) } }
        item { SettingsSwitch("Remember last tab", settings.rememberLastScreen) { vm.updateSettings(settings.copy(rememberLastScreen = it)) } }

        item { SettingsSectionTitle("Local Library") }
        item { SettingsActionButton("Scan local music library", Icons.Rounded.LibraryMusic, primary = true, onClick = vm::scanDeviceLibrary) }
        item { SettingsActionButton("Rescan library", Icons.Rounded.Refresh, onClick = vm::scanLibrary) }
        item { SettingsActionButton("Select local music folder", Icons.Rounded.Folder, onClick = { musicFolderLauncher.launch(null) }) }
        item { SettingsActionButton("Select local podcast folder", Icons.Rounded.Folder, onClick = { podcastFolderLauncher.launch(null) }) }
        item { SettingsActionButton("Scan local podcast folder", Icons.Rounded.Refresh, onClick = vm::scanPodcasts) }
        item { SettingsActionButton("Set custom folder", Icons.Rounded.CreateNewFolder, primary = true, onClick = { customFolderLauncher.launch(null) }) }
        item { SettingsActionButton("Scan custom folder", Icons.Rounded.Refresh, onClick = vm::scanCustomFolder) }
        item { SettingsSwitchDescription("Include social audio", "Include voice notes and audio files from social folders.", settings.includeSocialAudio) { vm.updateSettings(settings.copy(includeSocialAudio = it)) } }
        item { SettingsSwitch("Automatic library scan", settings.autoScanEnabled) { vm.updateSettings(settings.copy(autoScanEnabled = it)) } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Auto-scan interval", color = appText(), fontWeight = FontWeight.SemiBold)
                    ChipRow(listOf("6h", "12h", "24h", "72h", "96h", "128h"), "${settings.autoScanIntervalHours}h") { label ->
                        label.removeSuffix("h").toIntOrNull()?.let { vm.updateSettings(settings.copy(autoScanIntervalHours = it.coerceAtLeast(1))) }
                    }
                }
            }
        }
        item { SettingsActionButton("Clean local library cache", Icons.Rounded.Delete, onClick = vm::clearLocalLibraryCache) }

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
        item { OutlinedTextField(webName, { webName = it; vm.updateSettings(settings.copy(customWebName = it)) }, Modifier.fillMaxWidth(), label = { Text("Custom web library name") }, shape = RoundedCornerShape(20.dp)) }
        item { OutlinedTextField(webUrl, { webUrl = it; vm.updateSettings(settings.copy(customWebUrl = it)) }, Modifier.fillMaxWidth(), label = { Text("Custom web library URL") }, shape = RoundedCornerShape(20.dp)) }

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
                }
            }
        }

        item { SettingsSectionTitle("Import and Export") }
        item { SettingsActionButton("Export settings library", Icons.Rounded.IosShare, primary = true) { exportSettingsLauncher.launch("musicmonster-settings.json") } }
        item { SettingsActionButton("Import settings library", Icons.Rounded.FileDownload) { importSettingsLauncher.launch(arrayOf("application/json", "text/*")) } }
        item { SettingsActionButton("Import playlist", Icons.Rounded.FileDownload) { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/*")) } }
        item { SettingsActionButton("Export favorites", Icons.Rounded.Favorite, primary = true) { exportFavoritesLauncher.launch("musicmonster-favorites.json") } }
        item { SettingsActionButton("Import favorites", Icons.Rounded.FileDownload) { importFavoritesLauncher.launch(arrayOf("application/json", "text/*")) } }

        item { SavePlayerSettingsCard { vm.closeSettings() } }
        item { AboutSection() }
    }
}


@Composable
fun AboutSection() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Music Monster Android Music Player", color = appText(.78f))
            Text("Version 3.0.2", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Package: com.sksdesign.musicmonster", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Author: complicatiion aka sksdesign", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Web: sksdesign.de", color = appMuted(.62f), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/complicatiion/musicmonster_app"))) }
                    },
                    shape = RoundedCornerShape(24.dp)
                ) { Text("GitHub Repository") }
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sksdesign.de"))) }
                    },
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Website", color = appText(.9f)) }
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
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp)) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp)) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = appIconColor(.82f))
            Spacer(Modifier.width(8.dp))
            Text(text, color = appText(.9f))
        }
    }
}


@Composable
fun SavePlayerSettingsCard(onSave: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = appCardColor(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Save Player Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = appText())
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
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
            Text("Display and bottom navigation", fontWeight = FontWeight.SemiBold, color = appText())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("UI scale: " + String.format(Locale.US, "%.2f", settings.uiScale) + "x", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = settings.uiScale,
                onValueChange = { onUpdate(settings.copy(uiScale = it.coerceIn(.75f, 1.25f))) },
                valueRange = .75f..1.25f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bottom navigation auto-hide: ${settings.dockAutoHideSeconds}s", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = settings.dockAutoHideSeconds.toFloat(),
                onValueChange = { onUpdate(settings.copy(dockAutoHideSeconds = it.toInt().coerceIn(2, 15))) },
                valueRange = 2f..15f,
                steps = 12
            )
            SettingsSwitchInline("Start bottom navigation collapsed", settings.dockStartsCollapsed) {
                onUpdate(settings.copy(dockStartsCollapsed = it))
            }
            SettingsSwitchInline("Auto-hide player controls", settings.miniPlayerAutoHideEnabled) {
                onUpdate(settings.copy(miniPlayerAutoHideEnabled = it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Player controls auto-hide: ${settings.miniPlayerAutoHideSeconds}s", Modifier.weight(1f), color = appMuted(.72f), style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = settings.miniPlayerAutoHideSeconds.toFloat(),
                onValueChange = { onUpdate(settings.copy(miniPlayerAutoHideSeconds = it.toInt().coerceIn(2, 15))) },
                valueRange = 2f..15f,
                steps = 12
            )
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
                    label = { Text(label) },
                    leadingIcon = { FlatFlag(code, Modifier.size(width = 24.dp, height = 16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.language == code) MaterialTheme.colorScheme.primary.copy(.34f) else appMuted(.05f))
                )
            }
        }
    }
}

@Composable
fun FlatFlag(code: String, modifier: Modifier = Modifier) {
    val stripes = when (code) {
        "de" -> listOf(Color.Black, Color(0xFFDD0000), Color(0xFFFFCE00))
        "es" -> listOf(Color(0xFFC60B1E), Color(0xFFFFC400), Color(0xFFC60B1E))
        "fr" -> listOf(Color(0xFF0055A4), Color.White, Color(0xFFEF4135))
        "it" -> listOf(Color(0xFF009246), Color.White, Color(0xFFCE2B37))
        "pl" -> listOf(Color.White, Color(0xFFDC143C))
        "nl" -> listOf(Color(0xFFAE1C28), Color.White, Color(0xFF21468B))
        "ru" -> listOf(Color.White, Color(0xFF0039A6), Color(0xFFD52B1E))
        "uk" -> listOf(Color(0xFF0057B7), Color(0xFFFFD700))
        "sv" -> listOf(Color(0xFF006AA7), Color(0xFFFECC00), Color(0xFF006AA7))
        "da" -> listOf(Color(0xFFC8102E), Color.White, Color(0xFFC8102E))
        "fi" -> listOf(Color.White, Color(0xFF002F6C), Color.White)
        "cs" -> listOf(Color.White, Color(0xFFD7141A), Color(0xFF11457E))
        "ja" -> listOf(Color.White, Color(0xFFBC002D), Color.White)
        "zh" -> listOf(Color(0xFFDE2910), Color(0xFFFFDE00), Color(0xFFDE2910))
        "ar" -> listOf(Color(0xFF007A3D), Color.White, Color.Black)
        "fa" -> listOf(Color(0xFF239F40), Color.White, Color(0xFFDA0000))
        else -> listOf(Color(0xFF012169), Color.White, Color(0xFFC8102E))
    }
    Row(modifier.clip(RoundedCornerShape(3.dp)).border(1.dp, appMuted(.22f), RoundedCornerShape(3.dp))) {
        stripes.forEach { color -> Box(Modifier.weight(1f).fillMaxHeight().background(color)) }
    }
}


@Composable
fun AccentColorPicker(settings: AppSettings, customColor: String, onColorText: (String) -> Unit, onUpdate: (AppSettings) -> Unit) {
    val presets = listOf(
        0xFFB6FF2F, 0xFF00E5FF, 0xFF2979FF, 0xFF7C4DFF,
        0xFFA855F7, 0xFFFF3B7F, 0xFFFF1744, 0xFFFF6B2C,
        0xFFFFD600, 0xFF64FFDA, 0xFF00E676, 0xFF76FF03,
        0xFFFF4081, 0xFF40C4FF, 0xFFFFFFFF, 0xFFFFF8E1
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
fun FullPlayerOverlay(vm: MusicMonsterViewModel, carMode: Boolean) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val position by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val current = track ?: return
    val safeDuration = duration.takeIf { it > 0 } ?: current.durationMs.coerceAtLeast(1L)

    BackHandler { vm.showFullPlayer(false) }

    if (showEqualizer) {
        Dialog(onDismissRequest = { showEqualizer = false }) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = if (isLightThemeActive()) Color.White else Color(0xFF151919))
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
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false },
            title = { Text("Add to playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlists.forEachIndexed { index, playlist ->
                        OutlinedButton(onClick = { vm.addCurrentToPlaylist(index); showAddToPlaylist = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Text(playlist.name)
                        }
                    }
                    OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("New playlist name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.createPlaylist(newPlaylistName.ifBlank { "New Playlist" }, listOf(current)); newPlaylistName = ""; showAddToPlaylist = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showAddToPlaylist = false }) { Text("Cancel") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (settings.landscapeMode) {
            LandscapeFullPlayerContent(
                current = current,
                playing = playing,
                position = position,
                safeDuration = safeDuration,
                shuffle = shuffle,
                repeat = repeat,
                settings = settings,
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
                onEqualizer = { showEqualizer = true },
                onAddToPlaylist = { showAddToPlaylist = true }
            )
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
    onEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FullPlayerTopActions(vm, current, settings, onEqualizer, onAddToPlaylist)
        Spacer(Modifier.height(if (carMode) 18.dp else 8.dp))
        TrackArtwork(current.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(34.dp)), albumPlaceholder = true)
        Spacer(Modifier.height(if (carMode) 28.dp else 20.dp))
        Text(current.title, fontWeight = FontWeight.Bold, fontSize = if (carMode) 28.sp else 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = appText())
        ArtistAlbumLinks(current, vm)
        Spacer(Modifier.height(22.dp))
        PlayerProgress(position, safeDuration, vm)
        Spacer(Modifier.height(if (carMode) 24.dp else 14.dp))
        ShuffleRepeatRow(shuffle, repeat, carMode, vm)
        Spacer(Modifier.height(6.dp))
        MainTransportRow(playing, carMode, vm)
        if (repeat != RepeatModeUi.Off) {
            Spacer(Modifier.height(10.dp))
            Text(if (repeat == RepeatModeUi.One) "Repeat current track" else "Repeat queue", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
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
    vm: MusicMonsterViewModel,
    onEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val scale = landscapeScale(settings)
    BoxWithConstraints(Modifier.fillMaxSize().padding((18f * scale).dp)) {
        val coverSize = min(maxHeight.value * 0.74f, maxWidth.value * 0.34f).dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((22f * scale).dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(coverSize)) {
                TrackArtwork(current.artworkUri, Modifier.size(coverSize).clip(RoundedCornerShape((30f * scale).dp)), albumPlaceholder = true)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                FullPlayerTopActions(vm, current, settings, onEqualizer, onAddToPlaylist)
                Spacer(Modifier.height((10f * scale).dp))
                Text(current.title, fontWeight = FontWeight.Bold, fontSize = (24f * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = appText())
                ArtistAlbumLinks(current, vm, fontSize = (14f * scale).sp)
                Spacer(Modifier.height((14f * scale).dp))
                PlayerProgress(position, safeDuration, vm)
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
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = vm::toggleLandscapeMode) {
            Icon(Icons.Rounded.DirectionsCar, contentDescription = "Landscape shortcut", tint = if (settings.landscapeMode) MaterialTheme.colorScheme.primary else appIconColor(.76f))
        }
        IconButton(onClick = onEqualizer) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { vm.showQueue(true) }) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = appIconColor(.78f))
        }
        IconButton(onClick = { vm.toggleFavorite(current) }) {
            Icon(if (vm.isFavorite(current)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (vm.isFavorite(current)) MaterialTheme.colorScheme.primary else appIconColor(.76f))
        }
        FilledIconButton(
            onClick = { vm.showFullPlayer(false) },
            modifier = Modifier.size(42.dp),
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
    Slider(value = position.coerceIn(0L, safeDuration).toFloat(), onValueChange = { vm.seekTo(it.toLong()) }, valueRange = 0f..safeDuration.toFloat())
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
    Row(horizontalArrangement = Arrangement.spacedBy(((if (carMode) 28f else 18f) * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previous, modifier = Modifier.size(((if (carMode) 76f else 60f) * scale).dp)) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(((if (carMode) 40f else 32f) * scale).dp), tint = appIconColor()) }
        val playRotation by animateFloatAsState(targetValue = if (playing) 180f else 0f, animationSpec = tween(220), label = "playPauseRotation")
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
            FilterChip(
                selected = label == selected,
                onClick = { onSelect(label) },
                label = { Text(label) },
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
    val modifier = if (compact) Modifier.fillMaxWidth().heightIn(max = 420.dp) else Modifier.fillMaxSize()
    val padding = if (compact) PaddingValues(0.dp) else PaddingValues(bottom = 128.dp)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = padding, modifier = modifier) {
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

@Composable
fun CompactIconButton(enabled: Boolean = true, onClick: () -> Unit, compact: Boolean = false, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(if (compact) 34.dp else 44.dp)) { content() }
}

@Composable
fun TrackRow(
    track: Track,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    showArtwork: Boolean = true,
    compactActions: Boolean = false,
    durationBelow: Boolean = false,
    containerColor: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = containerColor ?: appCardColor(.05f)), shape = RoundedCornerShape(18.dp)) {
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
    val cellSize = if (settings.landscapeMode) {
        when (settings.landscapePreset) {
            "10" -> 86.dp
            "8" -> 104.dp
            "7" -> 122.dp
            else -> 145.dp
        }
    } else 150.dp
    LazyVerticalGrid(columns = GridCells.Adaptive(cellSize), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums) { album ->
            Card(Modifier.clickable { vm.openAlbum(album) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = appCardColor(.06f))) {
                Column(Modifier.padding(14.dp)) {
                    TrackArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), albumPlaceholder = true)
                    Spacer(Modifier.height(10.dp))
                    Text(album.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = appMuted(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${album.tracks.size} tracks", color = appMuted(.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.play(album.tracks, 0) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumStrip(title: String, albums: List<Album>, vm: MusicMonsterViewModel) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums) { album ->
                Card(Modifier.width(150.dp).clickable { vm.setTab(MainTab.Library); vm.openAlbum(album) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = appCardColor(.06f))) {
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
fun GroupedList(items: Map<String, List<Track>>, onOpen: (String, List<Track>) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items.toList()) { (name, tracks) ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(name, tracks) }, colors = CardDefaults.cardColors(containerColor = appCardColor(.05f)), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tracks.size} tracks", color = appMuted(.6f))
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
    return (parsed + allowed).distinct()
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
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
    "Bass Boost" to listOf(6f, 4f, 1f, 0f, 0f),
    "Bass & Treble Boost" to listOf(6f, 3f, 0f, 3f, 6f),
    "Low" to listOf(4f, 2f, 0f, -1f, -2f),
    "Reverb" to listOf(1f, 2f, 3f, 2f, 1f),
    "Hall" to listOf(2f, 3f, 2f, 3f, 2f),
    "Rock" to listOf(5f, 3f, -1f, 3f, 5f),
    "Disco" to listOf(4f, 2f, 0f, 2f, 4f),
    "Classic" to listOf(3f, 2f, 0f, 2f, 3f),
    "Vocal" to listOf(-1f, 1f, 4f, 3f, 1f),
    "Custom" to parseEqBands("0,0,0,0,0")
)

fun librarySections(settings: AppSettings): List<LibrarySection> {
    val base = baseLibraryOrder(settings).toMutableList()
    if (settings.enableSoundCloud) base += LibrarySection.SoundCloud
    if (settings.enableSpotify) base += LibrarySection.Spotify
    if (settings.enableYouTubeMusic) base += LibrarySection.YouTubeMusic
    if (settings.enableYouTube) base += LibrarySection.YouTube
    if (settings.enableCustomWeb) base += LibrarySection.CustomWeb
    return base.distinct()
}

fun baseLibraryOrder(settings: AppSettings): List<LibrarySection> {
    val allowed = listOf(LibrarySection.Albums, LibrarySection.Tracks, LibrarySection.Folders, LibrarySection.Genres, LibrarySection.Podcasts, LibrarySection.Artists)
    val orderValue = when (settings.libraryOrder) {
        "Albums,Tracks,Artists,Genres,Podcasts,Folders",
        "Albums,Tracks,Artists,Genres,Folders,Podcasts" -> "Albums,Tracks,Folders,Genres,Podcasts,Artists"
        else -> settings.libraryOrder
    }
    val parsed = orderValue.split(',').mapNotNull { name -> runCatching { LibrarySection.valueOf(name) }.getOrNull() }.filter { it in allowed }
    return (parsed + allowed).distinct()
}

fun libraryLabel(section: LibrarySection, settings: AppSettings): String = when (section) {
    LibrarySection.CustomWeb -> settings.customWebName.ifBlank { "Web Library" }
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
