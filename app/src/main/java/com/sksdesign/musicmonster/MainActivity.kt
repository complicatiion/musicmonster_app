package com.sksdesign.musicmonster

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sksdesign.musicmonster.data.*
import com.sksdesign.musicmonster.player.PlayerController
import com.sksdesign.musicmonster.player.MusicMonsterRuntime
import com.sksdesign.musicmonster.player.PlayerNotificationController
import com.sksdesign.musicmonster.ui.WebLibraryScreen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random

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

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _podcasts = MutableStateFlow<List<Track>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _tab = MutableStateFlow(MainTab.Home)
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
            combine(player.currentTrack, player.isPlaying) { track, playing -> track to playing }
                .collect { (track, playing) -> notifications.update(track, playing) }
        }
    }

    fun scanLibraryIfEmpty() = viewModelScope.launch {
        _cacheReady.filter { it }.first()
        if (_tracks.value.isEmpty()) scanLibrary()
    }

    fun scanLibrary() = viewModelScope.launch {
        val loadedTracks = repo.loadTracks(settings.value.musicFolderUri, settings.value.includeSocialAudio)
        val loadedPodcasts = repo.loadPodcasts(settings.value.podcastFolderUri, loadedTracks, settings.value.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
        _libraryDetail.value = null
    }

    fun scanDeviceLibrary() = viewModelScope.launch {
        val loadedTracks = repo.loadTracks("", settings.value.includeSocialAudio)
        val loadedPodcasts = repo.loadPodcasts(settings.value.podcastFolderUri, loadedTracks, settings.value.includeSocialAudio)
        _tracks.value = loadedTracks
        _podcasts.value = loadedPodcasts
        repo.saveCachedTracks(loadedTracks)
        repo.saveCachedPodcasts(loadedPodcasts)
        _libraryDetail.value = null
    }

    fun scanPodcasts() = viewModelScope.launch {
        val loadedPodcasts = repo.loadPodcasts(settings.value.podcastFolderUri, _tracks.value, settings.value.includeSocialAudio)
        _podcasts.value = loadedPodcasts
        repo.saveCachedPodcasts(loadedPodcasts)
    }

    fun setMusicFolder(uri: Uri) = viewModelScope.launch {
        val value = uri.toString()
        store.save(settings.value.copy(musicFolderUri = value))
        val loadedTracks = repo.loadTracks(value, settings.value.includeSocialAudio)
        val loadedPodcasts = repo.loadPodcasts(settings.value.podcastFolderUri, loadedTracks, settings.value.includeSocialAudio)
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

    fun clearLocalLibraryCache() = viewModelScope.launch {
        _tracks.value = emptyList()
        _podcasts.value = emptyList()
        _libraryDetail.value = null
        repo.clearCachedLibrary()
    }

    fun setTab(t: MainTab) {
        _tab.value = t
        _libraryDetail.value = null
    }

    fun setHome(s: HomeSection) {
        _tab.value = MainTab.Home
        _homeSection.value = s
    }

    fun setLibrary(s: LibrarySection) {
        _tab.value = MainTab.Library
        _librarySection.value = s
        _libraryDetail.value = null
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
    fun toggleCarMode() { _carMode.value = !_carMode.value }
    fun updateSettings(s: AppSettings) = viewModelScope.launch { store.save(s) }
    fun exportSettings(): String = store.exportJson(settings.value)
    fun importSettings(json: String) = viewModelScope.launch { store.save(store.importJson(json)) }
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
    val bg = if (settings.amoledBlack) Color.Black else Color(0xFF0D1010)
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

    MaterialTheme(
        colorScheme = darkColorScheme(primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White, secondary = accent, onSecondary = Color.White, background = bg, surface = Color(0xFF151919), onSurface = Color.White)
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
    val isWeb = tab == MainTab.Library && isWebLibrary(librarySection)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopBar(vm) },
            bottomBar = { BottomNav(tab, vm::setTab) },
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
        if (!isWeb) MiniPlayer(track, playing, vm, carMode, Modifier.align(Alignment.BottomCenter).padding(bottom = 78.dp))
        if (fullPlayer && track != null) FullPlayerOverlay(vm, carMode)
        if (showQueue) QueueOverlay(vm)
    }
}

@Composable
fun TopBar(vm: MusicMonsterViewModel) {
    val carMode by vm.carMode.collectAsState()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentLogo(Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Text("Music Monster", fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.showQueue(true) }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(.82f))
        }
        IconButton(onClick = vm::toggleCarMode, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.DirectionsCar, contentDescription = "Car mode", tint = if (carMode) MaterialTheme.colorScheme.primary else Color.White.copy(.75f))
        }
        IconButton(onClick = { vm.setTab(MainTab.Settings) }, modifier = Modifier.size(42.dp)) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
    }
}

@Composable
fun AccentLogo(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.08f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.logo_mark), contentDescription = "Music Monster", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize(.72f))
    }
}

@Composable
fun BottomNav(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = Color(0xEE151919)) {
        MainTab.values().forEach { t ->
            NavigationBarItem(selected = selected == t, onClick = { onSelect(t) }, icon = { Icon(iconFor(t), null) }, label = { Text(t.name) })
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

@Composable
fun MiniPlayer(track: Track?, playing: Boolean, vm: MusicMonsterViewModel, carMode: Boolean, modifier: Modifier = Modifier) {
    if (track == null) return
    if (carMode) {
        Card(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF0151919)),
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
    Card(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { vm.showFullPlayer(true) },
        colors = CardDefaults.cardColors(containerColor = Color(0xDD151919)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArtwork(track.artworkUri, Modifier.size(42.dp), albumPlaceholder = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(46.dp)) { Icon(Icons.Rounded.SkipPrevious, null) }
            FilledIconButton(
                onClick = vm::toggle,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White) }
            IconButton(onClick = vm::next, modifier = Modifier.size(46.dp)) { Icon(Icons.Rounded.SkipNext, null) }
        }
    }
}

@Composable
fun HomeScreen(vm: MusicMonsterViewModel) {
    val section by vm.homeSection.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val albums = remember(tracks) { vm.albums().take(10) }
    val artists = remember(tracks) { vm.artists().take(10) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ChipRow(HomeSection.values().map { it.name }, section.name) { vm.setHome(HomeSection.valueOf(it)) } }
        when (section) {
            HomeSection.Home -> {
                item { TrackSection("Custom Picks", vm.randomTracks(10), vm) }
                item { AlbumStrip("Albums", albums, vm) }
                item { ArtistStrip("Artists", artists, vm) }
                item { PlaylistSection("Playlists", playlists) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                Text(detail.subtitle, color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
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
    } else {
        TrackList(podcasts, vm)
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
        TrackList(tracks, vm)
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
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(newPlaylistName, { newPlaylistName = it }, Modifier.fillMaxWidth(), label = { Text("New playlist name") }, shape = RoundedCornerShape(18.dp))
                    Text("Export format", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
                    ChipRow(listOf("M3U", "M3U8", "PLS"), exportFormat) { exportFormat = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.createPlaylist(newPlaylistName.ifBlank { "New Playlist" }); newPlaylistName = "" }, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) { Text("Create new") }
                        OutlinedButton(onClick = vm::createPlaylistFromLibrary, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) { Text("From library") }
                    }
                }
            }
        }
        item { Text("Smart Playlists", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        items(smartPlaylists) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onOpen = { selectedSmartName = playlist.name },
                onPlay = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) },
                readonly = true
            )
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
    Card(Modifier.clickable { onOpen() }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(.05f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (readonly) Icons.Rounded.Star else Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.tracks.size} tracks", color = Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium)
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
                Text("${playlist.tracks.size} tracks", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
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
                Text("${playlist.tracks.size} tracks", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { if (playlist.tracks.isNotEmpty()) vm.play(playlist.tracks, 0) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = { onExport(playlist) }) { Icon(Icons.Rounded.IosShare, contentDescription = "Export") }
            IconButton(onClick = { vm.deletePlaylist(index) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White.copy(.74f)) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(20.dp)) {
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
                    Text("${queue.size} tracks", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
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
                            trailing = {
                                if (index == queueIndex) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

    LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader() }
        item { SettingsSectionTitle("Appearance") }
        item { AccentColorPicker(settings, customColor, { customColor = it }, vm::updateSettings) }
        item { SettingsSwitch("AMOLED black background", settings.amoledBlack) { vm.updateSettings(settings.copy(amoledBlack = it)) } }

        item { SettingsSectionTitle("Startup") }
        item { StartupPicker(settings) { vm.updateSettings(settings.copy(startPage = it)) } }

        item { SettingsSectionTitle("Local Library") }
        item { Button(onClick = vm::scanDeviceLibrary, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Scan local music library") } }
        item { OutlinedButton(onClick = { musicFolderLauncher.launch(null) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Select local music folder") } }
        item { OutlinedButton(onClick = { podcastFolderLauncher.launch(null) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Select local podcast folder") } }
        item { OutlinedButton(onClick = vm::scanPodcasts, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Scan local podcast folder") } }
        item { SettingsSwitch("Include messenger and social audio", settings.includeSocialAudio) { vm.updateSettings(settings.copy(includeSocialAudio = it)) } }
        item { OutlinedButton(onClick = vm::clearLocalLibraryCache, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Clean local library cache") } }

        item { SettingsSectionTitle("Library Order") }
        item { LibraryOrderEditor(settings) { vm.updateSettings(settings.copy(libraryOrder = it.joinToString(","))) } }

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
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(playlistName, { playlistName = it }, Modifier.fillMaxWidth(), label = { Text("Playlist name") }, shape = RoundedCornerShape(18.dp), singleLine = true)
                    Text("Playlist format", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
                    ChipRow(listOf("M3U", "M3U8", "PLS"), "M3U") { }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.createPlaylist(playlistName.ifBlank { "New Playlist" }); playlistName = "" }, Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("Create") }
                        OutlinedButton(onClick = { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/*")) }, Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("Import") }
                    }
                }
            }
        }

        item { SettingsSectionTitle("Import and Export") }
        item { Button(onClick = { exportSettingsLauncher.launch("musicmonster-settings.json") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Export settings library") } }
        item { OutlinedButton(onClick = { importSettingsLauncher.launch(arrayOf("application/json", "text/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Import settings library") } }
        item { OutlinedButton(onClick = { importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Import playlist") } }

        item { AboutSection() }
    }
}


@Composable
fun AboutSection() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Music Monster Android Music Player", color = Color.White.copy(.78f))
            Text("Version 2.0.5", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Package: com.sksdesign.musicmonster", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
            Text("Author: complicatiion aka sksdesign", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelMedium)
            Button(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/complicatiion/musicmonster_app"))) }
                },
                shape = RoundedCornerShape(24.dp)
            ) { Text("GitHub Repository") }
        }
    }
}

@Composable
fun SettingsHeader() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.07f)), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentLogo(Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Music Monster", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(.9f), modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun SettingsSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(.72f),
                    uncheckedThumbColor = Color.White.copy(.88f),
                    uncheckedTrackColor = Color.White.copy(.18f)
                )
            )
        }
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
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
                                    .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(.20f), CircleShape)
                                    .clickable { onUpdate(settings.copy(accentColor = color)) }
                            )
                        }
                    }
                }
            }
            Text("Custom color", color = Color.White.copy(.72f), style = MaterialTheme.typography.labelMedium)
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Start page", fontWeight = FontWeight.SemiBold)
            ChipRow(options, settings.startPage, onSelect)
        }
    }
}

@Composable
fun LibraryOrderEditor(settings: AppSettings, onChange: (List<String>) -> Unit) {
    val order = baseLibraryOrder(settings).map { it.name }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f)), shape = RoundedCornerShape(22.dp)) {
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
fun FullPlayerOverlay(vm: MusicMonsterViewModel, carMode: Boolean) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val position by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val current = track ?: return
    val safeDuration = duration.takeIf { it > 0 } ?: current.durationMs.coerceAtLeast(1L)

    BackHandler { vm.showFullPlayer(false) }

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
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showFullPlayer(false) }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.showQueue(true) }) { Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { showAddToPlaylist = true }) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White.copy(.78f)) }
                IconButton(onClick = { vm.toggleFavorite(current) }) {
                    Icon(if (vm.isFavorite(current)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (vm.isFavorite(current)) MaterialTheme.colorScheme.primary else Color.White.copy(.76f))
                }
            }
            Spacer(Modifier.height(if (carMode) 18.dp else 8.dp))
            TrackArtwork(current.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(34.dp)), albumPlaceholder = true)
            Spacer(Modifier.height(if (carMode) 28.dp else 20.dp))
            Text(current.title, fontWeight = FontWeight.Bold, fontSize = if (carMode) 28.sp else 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${current.artist} • ${current.album}", color = Color.White.copy(.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(22.dp))
            Slider(value = position.coerceIn(0L, safeDuration).toFloat(), onValueChange = { vm.seekTo(it.toLong()) }, valueRange = 0f..safeDuration.toFloat())
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(position), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(safeDuration), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(if (carMode) 24.dp else 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::cycleRepeatMode, modifier = Modifier.size(if (carMode) 64.dp else 52.dp)) {
                    Icon(if (repeat == RepeatModeUi.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (repeat != RepeatModeUi.Off) MaterialTheme.colorScheme.primary else Color.White.copy(.78f))
                }
                IconButton(onClick = vm::toggleShuffle, modifier = Modifier.size(if (carMode) 64.dp else 52.dp)) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(.78f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(if (carMode) 28.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, modifier = Modifier.size(if (carMode) 76.dp else 60.dp)) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(if (carMode) 40.dp else 32.dp)) }
                FilledIconButton(
                    onClick = vm::toggle,
                    shape = CircleShape,
                    modifier = Modifier.size(if (carMode) 96.dp else 76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", modifier = Modifier.size(if (carMode) 46.dp else 36.dp), tint = Color.White)
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(if (carMode) 76.dp else 60.dp)) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(if (carMode) 40.dp else 32.dp)) }
            }
            if (repeat != RepeatModeUi.Off) {
                Spacer(Modifier.height(10.dp))
                Text(if (repeat == RepeatModeUi.One) "Repeat current track" else "Repeat queue", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun HeroCard(title: String, subtitle: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(.07f)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(body, color = Color.White.copy(.72f))
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
                    containerColor = Color.White.copy(.05f),
                    labelColor = Color.White.copy(.78f)
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
        if (tracks.isEmpty()) Text("No tracks yet.", color = Color.White.copy(.62f)) else TrackList(tracks, vm, compact = true)
    }
}

@Composable
fun PlaylistSection(title: String, playlists: List<Playlist>) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(if (playlists.isEmpty()) "No playlists imported yet." else "${playlists.size} playlists", color = Color.White.copy(.65f))
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
                    if (showQueueButton) CompactIconButton(onClick = { vm.addToQueue(track) }, compact = compactActions) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to queue", tint = Color.White.copy(.66f), modifier = Modifier.size(if (compactActions) 18.dp else 22.dp)) }
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
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White.copy(.05f)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(horizontal = if (compactActions) 10.dp else 12.dp, vertical = if (compactActions) 9.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showArtwork) {
                TrackArtwork(null, Modifier.size(42.dp), albumPlaceholder = false)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (compactActions) 14.sp else 16.sp)
                Text("${track.artist} • ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.6f), style = MaterialTheme.typography.labelMedium)
                if (durationBelow) Text(formatDuration(track.durationMs), color = Color.White.copy(.55f), style = MaterialTheme.typography.labelSmall)
            }
            if (!durationBelow) Text(formatDuration(track.durationMs), color = Color.White.copy(.55f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = if (compactActions) 2.dp else 6.dp))
            CompactIconButton(onClick = onFavorite, compact = compactActions) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White.copy(.6f), modifier = Modifier.size(if (compactActions) 18.dp else 22.dp))
            }
            trailing()
        }
    }
}


@Composable
fun AlbumGrid(albums: List<Album>, vm: MusicMonsterViewModel) {
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums) { album ->
            Card(Modifier.clickable { vm.openAlbum(album) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f))) {
                Column(Modifier.padding(14.dp)) {
                    TrackArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), albumPlaceholder = true)
                    Spacer(Modifier.height(10.dp))
                    Text(album.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${album.tracks.size} tracks", color = Color.White.copy(.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
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
                Card(Modifier.width(150.dp).clickable { vm.setTab(MainTab.Library); vm.openAlbum(album) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(.06f))) {
                    Column(Modifier.padding(12.dp)) {
                        TrackArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), albumPlaceholder = true)
                        Spacer(Modifier.height(8.dp))
                        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.6f), style = MaterialTheme.typography.labelSmall)
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
            Card(Modifier.fillMaxWidth().clickable { onOpen(name, tracks) }, colors = CardDefaults.cardColors(containerColor = Color.White.copy(.05f)), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tracks.size} tracks", color = Color.White.copy(.6f))
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
    val allowed = listOf(LibrarySection.Albums, LibrarySection.Tracks, LibrarySection.Artists, LibrarySection.Genres, LibrarySection.Folders, LibrarySection.Podcasts)
    val parsed = settings.libraryOrder.split(',').mapNotNull { name -> runCatching { LibrarySection.valueOf(name) }.getOrNull() }.filter { it in allowed }
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
