package com.sksdesign.musicmonster.data

import android.net.Uri

enum class MainTab { Home, Library, Search, Playlists, Settings }
enum class HomeSection { Home, Discovery, Favorites, Top, History }
enum class LibrarySection { Albums, Tracks, Artists, Genres, Folders, Podcasts, SoundCloud, Spotify, YouTubeMusic, YouTube, CustomWeb }
enum class RepeatModeUi { Off, All, One }

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "Unknown",
    val durationMs: Long,
    val uri: Uri,
    val folder: String = "Music",
    val artworkUri: Uri? = null,
    val trackNumber: Int = 0,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val dateAdded: Long = 0L,
    val isPodcast: Boolean = false
)

data class Album(
    val name: String,
    val artist: String,
    val tracks: List<Track>,
    val artworkUri: Uri? = tracks.sortedWith(compareBy<Track> { it.trackNumber.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }.thenBy { it.title }).firstOrNull { it.artworkUri != null }?.artworkUri
)

data class Artist(val name: String, val tracks: List<Track>)
data class Playlist(val name: String, val tracks: List<Track>, val updatedAt: Long = System.currentTimeMillis())

data class LibraryDetail(val title: String, val subtitle: String, val tracks: List<Track>)

data class AppSettings(
    val accentColor: Long = 0xFFB6FF2F,
    val startPage: String = "Home",
    val rememberLastScreen: Boolean = true,
    val enableDiscovery: Boolean = true,
    val enableSoundCloud: Boolean = false,
    val enableSpotify: Boolean = false,
    val enableYouTubeMusic: Boolean = false,
    val enableYouTube: Boolean = false,
    val enableCustomWeb: Boolean = false,
    val customWebName: String = "Web Library",
    val customWebUrl: String = "https://www.youtube.com",
    val musicFolderUri: String = "",
    val podcastFolderUri: String = "",
    val customFolderUri: String = "",
    val autoScanEnabled: Boolean = false,
    val autoScanIntervalHours: Int = 24,
    val lastAutoScanAt: Long = 0L,
    val libraryOrder: String = "Albums,Tracks,Folders,Genres,Podcasts,Artists",
    val homeOrder: String = "Home,Discovery,Favorites,Top,History",
    val keepMiniPlayerVisible: Boolean = true,
    val savePlayHistory: Boolean = true,
    val amoledBlack: Boolean = false,
    val favoriteIds: String = "",
    val includeSocialAudio: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: String = "0,0,0,0,0",
    val bassLevel: Float = 0f,
    val trebleLevel: Float = 0f,
    val superBassBoost: Boolean = false,
    val bassBoostLevel: Float = 0f,
    val landscapeMode: Boolean = false,
    val landscapePreset: String = "7",
    val uiScale: Float = 1.0f,
    val dockAutoHideSeconds: Int = 5,
    val dockStartsCollapsed: Boolean = false,
    val miniPlayerAutoHideEnabled: Boolean = true,
    val miniPlayerAutoHideSeconds: Int = 5,
    val language: String = "en",
    val lightMode: Boolean = false,
    val balanceLevel: Float = 0f,
    val faderLevel: Float = 0f,
    val duckOnFocusLoss: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val rememberShuffle: Boolean = true
)
