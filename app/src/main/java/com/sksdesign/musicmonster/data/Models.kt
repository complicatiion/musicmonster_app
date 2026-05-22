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
    val libraryOrder: String = "Albums,Tracks,Artists,Genres,Folders,Podcasts",
    val keepMiniPlayerVisible: Boolean = true,
    val savePlayHistory: Boolean = true,
    val amoledBlack: Boolean = false,
    val favoriteIds: String = "",
    val includeSocialAudio: Boolean = false
)
