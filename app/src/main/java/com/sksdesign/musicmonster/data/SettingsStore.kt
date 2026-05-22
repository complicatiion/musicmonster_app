package com.sksdesign.musicmonster.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("musicmonster_settings")

class SettingsStore(private val context: Context) {
    private val gson = Gson()

    private object Keys {
        val ACCENT = longPreferencesKey("accent")
        val START = stringPreferencesKey("start")
        val REMEMBER = booleanPreferencesKey("remember")
        val DISCOVERY = booleanPreferencesKey("discovery")
        val SOUNDCLOUD = booleanPreferencesKey("soundcloud")
        val SPOTIFY = booleanPreferencesKey("spotify")
        val YOUTUBE_MUSIC = booleanPreferencesKey("youtube")
        val YOUTUBE = booleanPreferencesKey("youtube_normal")
        val WEB = booleanPreferencesKey("web")
        val WEB_NAME = stringPreferencesKey("web_name")
        val WEB_URL = stringPreferencesKey("web_url")
        val MUSIC_FOLDER = stringPreferencesKey("music_folder")
        val PODCAST_FOLDER = stringPreferencesKey("podcast_folder")
        val LIBRARY_ORDER = stringPreferencesKey("library_order")
        val MINI = booleanPreferencesKey("mini")
        val HISTORY = booleanPreferencesKey("history")
        val AMOLED = booleanPreferencesKey("amoled")
        val FAVORITES = stringPreferencesKey("favorites")
        val INCLUDE_SOCIAL_AUDIO = booleanPreferencesKey("include_social_audio")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            accentColor = p[Keys.ACCENT] ?: 0xFFB6FF2F,
            startPage = p[Keys.START] ?: "Home",
            rememberLastScreen = p[Keys.REMEMBER] ?: true,
            enableDiscovery = p[Keys.DISCOVERY] ?: true,
            enableSoundCloud = p[Keys.SOUNDCLOUD] ?: false,
            enableSpotify = p[Keys.SPOTIFY] ?: false,
            enableYouTubeMusic = p[Keys.YOUTUBE_MUSIC] ?: false,
            enableYouTube = p[Keys.YOUTUBE] ?: false,
            enableCustomWeb = p[Keys.WEB] ?: false,
            customWebName = p[Keys.WEB_NAME] ?: "Web Library",
            customWebUrl = p[Keys.WEB_URL] ?: "https://www.youtube.com",
            musicFolderUri = p[Keys.MUSIC_FOLDER] ?: "",
            podcastFolderUri = p[Keys.PODCAST_FOLDER] ?: "",
            libraryOrder = p[Keys.LIBRARY_ORDER] ?: "Albums,Tracks,Artists,Genres,Folders,Podcasts",
            keepMiniPlayerVisible = p[Keys.MINI] ?: true,
            savePlayHistory = p[Keys.HISTORY] ?: true,
            amoledBlack = p[Keys.AMOLED] ?: false,
            favoriteIds = p[Keys.FAVORITES] ?: "",
            includeSocialAudio = p[Keys.INCLUDE_SOCIAL_AUDIO] ?: false
        )
    }

    suspend fun save(s: AppSettings) = context.dataStore.edit { p ->
        p[Keys.ACCENT] = s.accentColor
        p[Keys.START] = s.startPage
        p[Keys.REMEMBER] = s.rememberLastScreen
        p[Keys.DISCOVERY] = s.enableDiscovery
        p[Keys.SOUNDCLOUD] = s.enableSoundCloud
        p[Keys.SPOTIFY] = s.enableSpotify
        p[Keys.YOUTUBE_MUSIC] = s.enableYouTubeMusic
        p[Keys.YOUTUBE] = s.enableYouTube
        p[Keys.WEB] = s.enableCustomWeb
        p[Keys.WEB_NAME] = s.customWebName
        p[Keys.WEB_URL] = s.customWebUrl
        p[Keys.MUSIC_FOLDER] = s.musicFolderUri
        p[Keys.PODCAST_FOLDER] = s.podcastFolderUri
        p[Keys.LIBRARY_ORDER] = s.libraryOrder
        p[Keys.MINI] = s.keepMiniPlayerVisible
        p[Keys.HISTORY] = s.savePlayHistory
        p[Keys.AMOLED] = s.amoledBlack
        p[Keys.FAVORITES] = s.favoriteIds
        p[Keys.INCLUDE_SOCIAL_AUDIO] = s.includeSocialAudio
    }

    fun exportJson(s: AppSettings): String = gson.toJson(s)

    fun importJson(json: String): AppSettings {
        val imported = runCatching { gson.fromJson(json, AppSettings::class.java) }.getOrNull() ?: return AppSettings()
        return imported.copy(
            startPage = imported.startPage ?: "Home",
            customWebName = imported.customWebName ?: "Web Library",
            customWebUrl = imported.customWebUrl ?: "https://www.youtube.com",
            musicFolderUri = imported.musicFolderUri ?: "",
            podcastFolderUri = imported.podcastFolderUri ?: "",
            libraryOrder = imported.libraryOrder ?: "Albums,Tracks,Artists,Genres,Folders,Podcasts",
            favoriteIds = imported.favoriteIds ?: "",
            includeSocialAudio = imported.includeSocialAudio
        )
    }
}
