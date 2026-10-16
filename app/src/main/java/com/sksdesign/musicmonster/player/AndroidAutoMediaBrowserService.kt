package com.sksdesign.musicmonster.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.sksdesign.musicmonster.MainActivity
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.data.MusicRepository
import com.sksdesign.musicmonster.data.SettingsStore
import com.sksdesign.musicmonster.ui.legal.LegalDocuments
import com.sksdesign.musicmonster.data.Playlist
import com.sksdesign.musicmonster.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class AndroidAutoMediaBrowserService : MediaBrowserServiceCompat() {
    companion object {
        private const val TAG = "MusicMonsterAuto"
        private const val ROOT = "root"
        private const val ALL_TRACKS = "all_tracks"
        private const val ALBUMS = "albums"
        private const val ARTISTS = "artists"
        private const val FAVORITES = "favorites"
        private const val PLAYLISTS = "playlists"
        private const val QUEUE = "queue"
        private const val NOW_PLAYING = "now_playing"
        private const val RECENT = "recent"
        private const val GENRES = "genres"
        private const val PODCASTS = "podcasts"
        private const val OST = "ost"

        private const val ACTION_OPEN_PLAYER = "com.sksdesign.musicmonster.car.OPEN_PLAYER"
        private const val ACTION_OPEN_QUEUE = "com.sksdesign.musicmonster.car.OPEN_QUEUE"
        private const val ACTION_OPEN_EQUALIZER = "com.sksdesign.musicmonster.car.OPEN_EQUALIZER"

        private const val EXTRA_CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val EXTRA_CONTENT_STYLE_HINT = "android.media.browse.CONTENT_STYLE_HINT"
        private const val EXTRA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        private const val EXTRA_RECENT = "android.media.browse.extra.RECENT"
        private const val EXTRA_OFFLINE = "android.media.browse.extra.OFFLINE"
        private const val CONTENT_STYLE_LIST = 1
        private const val CONTENT_STYLE_GRID = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: MusicRepository
    private lateinit var controller: PlayerController
    private lateinit var mediaBridge: PlayerMediaSession
    private var tracks: List<Track> = emptyList()
    private var playlists: List<Playlist> = emptyList()
    private var libraryLoaded = false

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(applicationContext)
        controller = MusicMonsterRuntime.getOrCreateController(applicationContext)
        mediaBridge = PlayerMediaSession.getOrCreate(applicationContext, controller).also {
            it.setSessionActivity(androidAutoMode = true, compact = false, request = 20)
        }
        sessionToken = mediaBridge.session.sessionToken
        mediaBridge.session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = this@AndroidAutoMediaBrowserService.controller.play()
            override fun onPause() = this@AndroidAutoMediaBrowserService.controller.pause()
            override fun onSkipToNext() = this@AndroidAutoMediaBrowserService.controller.next()
            override fun onSkipToPrevious() = this@AndroidAutoMediaBrowserService.controller.previous()
            override fun onSeekTo(pos: Long) {
                this@AndroidAutoMediaBrowserService.controller.seekTo(pos)
                publishCurrentSessionState()
            }
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { playMediaId(mediaId) }
            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                val q = query.orEmpty().trim()
                val match = tracks.firstOrNull { candidate: Track -> candidate.matches(q) }
                match?.let { selectedTrack: Track ->
                    this@AndroidAutoMediaBrowserService.controller.playQueue(tracks.ifEmpty { listOf(selectedTrack) }, tracks.indexOf(selectedTrack).coerceAtLeast(0))
                }
            }
            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    ACTION_OPEN_PLAYER -> launchPhonePlayer(compact = false)
                    ACTION_OPEN_QUEUE -> launchPhonePlayer(compact = true)
                    ACTION_OPEN_EQUALIZER -> launchPhonePlayer(compact = false)
                }
            }
        })
        scope.launch(Dispatchers.IO) {
            ensureLibraryLoaded()
            withContext(Dispatchers.Main) {
                notifyChildrenChanged(ROOT)
                notifyChildrenChanged(ALL_TRACKS)
            }
        }
        scope.launch {
            combine(controller.currentTrack, controller.isPlaying, controller.positionMs, controller.durationMs) { track, playing, position, duration ->
                AutoPlaybackSnapshot(track, playing, position, duration)
            }.collect { snapshot ->
                updateSession(snapshot)
                PlaybackNotificationService.sync(applicationContext, snapshot.track != null)
            }
        }
        Log.i(TAG, "Android Auto media browser service created")
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Do not expose the library before the initial EULA screen was accepted.
        val accepted = runBlocking(Dispatchers.IO) {
            runCatching { SettingsStore(applicationContext).acceptedLegalRevision.first() == LegalDocuments.REVISION }
                .getOrDefault(false)
        }
        if (!accepted) {
            Log.i(TAG, "Android Auto requires first-run license acceptance in the phone application")
            return null
        }
        Log.i(TAG, "Android Auto browser connected: $clientPackageName")
        return BrowserRoot(ROOT, browserExtras())
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        scope.launch(Dispatchers.IO) {
            ensureLibraryLoaded()
            val items = childrenFor(parentId)
            withContext(Dispatchers.Main) { result.sendResult(items.toMutableList()) }
        }
    }

    override fun onLoadItem(itemId: String, result: Result<MediaBrowserCompat.MediaItem>) {
        result.detach()
        scope.launch(Dispatchers.IO) {
            ensureLibraryLoaded()
            val item = when {
                itemId.startsWith("track:") -> tracks.firstOrNull { it.id == itemId.removePrefix("track:").toLongOrNull() }?.let(::trackItem)
                itemId == NOW_PLAYING -> controller.currentTrack.value?.let(::trackItem)
                else -> null
            } ?: browseItem(ROOT, "MusicMonster", "Local music library", CONTENT_STYLE_LIST)
            withContext(Dispatchers.Main) { result.sendResult(item) }
        }
    }

    override fun onSearch(query: String, extras: Bundle?, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        scope.launch(Dispatchers.IO) {
            ensureLibraryLoaded()
            val q = query.trim()
            val matches = tracks
                .filter { candidate: Track -> candidate.matches(q) }
                .take(100)
                .map(::trackItem)
            withContext(Dispatchers.Main) { result.sendResult(matches.toMutableList()) }
        }
    }

    private suspend fun ensureLibraryLoaded() {
        val accepted = runCatching {
            SettingsStore(applicationContext).acceptedLegalRevision.first() == LegalDocuments.REVISION
        }.getOrDefault(false)
        if (!accepted) return // Background service must not scan or expose tracks before consent.
        if (libraryLoaded && tracks.isNotEmpty()) return
        // Mirror the phone's per-share cache policy: never expose tracks from an
        // old SMB share after the selected URI changed or the library was disabled.
        val prefs = SettingsStore(applicationContext).settings.first()
        repository.migrateLegacyNetworkCache(prefs.networkLibraryUri)
        val cachedLocalTracks = repository.loadCachedTracks().filterNot { it.isNetwork }
        val cachedNetworkTracks = if (prefs.networkLibraryEnabled && prefs.networkLibraryUri.isNotBlank()) {
            repository.loadCachedNetworkTracks(prefs.networkLibraryUri)
        } else emptyList()
        val cachedPodcasts = repository.loadCachedPodcasts().filterNot { it.isNetwork }
        tracks = (cachedLocalTracks + cachedNetworkTracks + cachedPodcasts)
            .distinctBy { it.uri.toString() }
        playlists = repository.loadCachedPlaylists()
        libraryLoaded = true
    }

    private fun childrenFor(parentId: String): List<MediaBrowserCompat.MediaItem> = when {
        parentId == ROOT -> rootItems()
        parentId == NOW_PLAYING -> controller.currentTrack.value?.let { listOf(trackItem(it)) }.orEmpty()
        parentId == QUEUE -> controller.queue.value.map(::trackItem)
        parentId == RECENT -> tracks.sortedByDescending { it.lastPlayed }.take(50).map(::trackItem)
        parentId == ALL_TRACKS -> tracks.sortedBy { it.title.lowercase() }.map(::trackItem)
        parentId == FAVORITES -> favoriteTracks().map(::trackItem)
        parentId == PODCASTS -> tracks.filter { it.isPodcast }.sortedBy { it.title.lowercase() }.map(::trackItem)
        parentId == OST -> tracks.filter { it.isOst }.sortedBy { it.title.lowercase() }.map(::trackItem)
        parentId == ALBUMS -> tracks
            .groupBy { it.album.ifBlank { "Unknown album" } }
            .keys
            .sorted()
            .map { album -> browseItem("album:${Uri.encode(album)}", album, "Album", CONTENT_STYLE_GRID) }
        parentId.startsWith("album:") -> {
            val name = Uri.decode(parentId.removePrefix("album:"))
            tracks.filter { it.album.ifBlank { "Unknown album" } == name }
                .sortedWith(compareBy<Track> { it.trackNumber.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }.thenBy { it.title })
                .map(::trackItem)
        }
        parentId == ARTISTS -> tracks
            .groupBy { it.artist.ifBlank { "Unknown artist" } }
            .keys
            .sorted()
            .map { artist -> browseItem("artist:${Uri.encode(artist)}", artist, "Artist", CONTENT_STYLE_GRID) }
        parentId.startsWith("artist:") -> {
            val name = Uri.decode(parentId.removePrefix("artist:"))
            tracks.filter { it.artist.ifBlank { "Unknown artist" } == name }
                .sortedBy { it.title }
                .map(::trackItem)
        }
        parentId == GENRES -> tracks
            .groupBy { it.genre.ifBlank { "Unknown genre" } }
            .keys
            .sorted()
            .map { genre -> browseItem("genre:${Uri.encode(genre)}", genre, "Genre", CONTENT_STYLE_LIST) }
        parentId.startsWith("genre:") -> {
            val name = Uri.decode(parentId.removePrefix("genre:"))
            tracks.filter { it.genre.ifBlank { "Unknown genre" } == name }
                .sortedBy { it.title }
                .map(::trackItem)
        }
        parentId == PLAYLISTS -> playlists.mapIndexed { index, playlist ->
            browseItem("playlist:$index", playlist.name, "${playlist.tracks.size} tracks", CONTENT_STYLE_LIST)
        }
        parentId.startsWith("playlist:") -> playlists
            .getOrNull(parentId.removePrefix("playlist:").toIntOrNull() ?: -1)
            ?.tracks
            ?.map(::trackItem)
            .orEmpty()
        else -> emptyList()
    }

    private fun rootItems(): List<MediaBrowserCompat.MediaItem> = listOf(
        browseItem(NOW_PLAYING, "Now playing", "Current track", CONTENT_STYLE_LIST),
        browseItem(QUEUE, "Play queue", "Current playback queue", CONTENT_STYLE_LIST),
        browseItem(ALL_TRACKS, "All tracks", "Complete local library", CONTENT_STYLE_LIST),
        browseItem(ALBUMS, "Albums", "Browse by album", CONTENT_STYLE_GRID),
        browseItem(ARTISTS, "Artists", "Browse by artist", CONTENT_STYLE_GRID),
        browseItem(GENRES, "Genres", "Browse by genre", CONTENT_STYLE_LIST),
        browseItem(FAVORITES, "Favorites", "Favorite tracks", CONTENT_STYLE_LIST),
        browseItem(RECENT, "Recently played", "Recently played tracks", CONTENT_STYLE_LIST),
        browseItem(PODCASTS, "Podcasts", "Podcast folder", CONTENT_STYLE_LIST),
        browseItem(OST, "OST", "Official soundtracks", CONTENT_STYLE_LIST),
        browseItem(PLAYLISTS, "Playlists", "Saved playlists", CONTENT_STYLE_LIST)
    )

    private fun favoriteTracks(): List<Track> = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(100)

    private fun browseItem(id: String, title: String, subtitle: String, style: Int) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setExtras(Bundle().apply { putInt(EXTRA_CONTENT_STYLE_HINT, style) })
            .build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private fun trackItem(track: Track) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId("track:${track.id}")
            .setTitle(track.title.ifBlank { "Unknown title" })
            .setSubtitle(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
            .setIconUri(track.artworkUri)
            .setMediaUri(track.uri)
            .setExtras(Bundle().apply { putInt(EXTRA_CONTENT_STYLE_HINT, CONTENT_STYLE_LIST) })
            .build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )

    private fun playMediaId(mediaId: String?) {
        val id = mediaId?.removePrefix("track:")?.toLongOrNull() ?: return
        val queueSource = if (tracks.isNotEmpty()) tracks else controller.queue.value
        val index = queueSource.indexOfFirst { it.id == id }
        if (index >= 0) controller.playQueue(queueSource, index)
    }

    private fun publishCurrentSessionState() {
        updateSession(
            AutoPlaybackSnapshot(
                controller.currentTrack.value,
                controller.isPlaying.value,
                controller.positionMs.value,
                controller.durationMs.value
            )
        )
    }

    private fun updateSession(snapshot: AutoPlaybackSnapshot) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        val state = when {
            snapshot.track == null -> PlaybackStateCompat.STATE_NONE
            snapshot.playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val builder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setActiveQueueItemId(snapshot.track?.id ?: -1L)
            .setState(state, snapshot.position.coerceAtLeast(0L), if (snapshot.playing) 1f else 0f, SystemClock.elapsedRealtime())
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_OPEN_PLAYER, "Open player", R.drawable.ic_fullscreen).build())
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_OPEN_QUEUE, "Queue", R.drawable.ic_queue).build())
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_OPEN_EQUALIZER, "Equalizer", R.drawable.ic_equalizer).build())
        mediaBridge.session.setPlaybackState(builder.build())
        mediaBridge.updateQueue(controller.queue.value)
        snapshot.track?.let { track -> updateMetadata(track, snapshot.duration) }
    }

    private fun updateMetadata(track: Track, duration: Long) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "track:${track.id}")
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title.ifBlank { "Unknown title" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title.ifBlank { "Unknown title" })
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { track.folder })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L))
        track.artworkUri?.toString()?.let { art: String ->
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, art)
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, art)
        }
        mediaBridge.session.setMetadata(metadata.build())
    }

    private fun launchPhonePlayer(compact: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_ANDROID_AUTO_MODE, true)
            putExtra(MainActivity.EXTRA_ANDROID_AUTO_COMPACT, compact)
        }
        runCatching { startActivity(intent) }
    }

    private fun browserExtras(): Bundle = Bundle().apply {
        putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
        putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
        putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
        putBoolean(EXTRA_SEARCH_SUPPORTED, true)
        putBoolean(EXTRA_RECENT, true)
        putBoolean(EXTRA_OFFLINE, true)
    }

    private fun Track.matches(q: String): Boolean {
        if (q.isBlank()) return true
        return title.contains(q, ignoreCase = true) ||
            artist.contains(q, ignoreCase = true) ||
            album.contains(q, ignoreCase = true) ||
            genre.contains(q, ignoreCase = true) ||
            folder.contains(q, ignoreCase = true)
    }

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "Android Auto media browser service destroyed")
        super.onDestroy()
    }
}

private data class AutoPlaybackSnapshot(val track: Track?, val playing: Boolean, val position: Long, val duration: Long)
