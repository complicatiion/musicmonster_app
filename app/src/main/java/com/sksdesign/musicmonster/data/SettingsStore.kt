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
        val CUSTOM_FOLDER = stringPreferencesKey("custom_folder")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val AUTO_SCAN_INTERVAL = intPreferencesKey("auto_scan_interval")
        val LAST_AUTO_SCAN = longPreferencesKey("last_auto_scan")
        val LIBRARY_ORDER = stringPreferencesKey("library_order")
        val HOME_ORDER = stringPreferencesKey("home_order")
        val MINI = booleanPreferencesKey("mini")
        val HISTORY = booleanPreferencesKey("history")
        val AMOLED = booleanPreferencesKey("amoled")
        val FAVORITES = stringPreferencesKey("favorites")
        val INCLUDE_SOCIAL_AUDIO = booleanPreferencesKey("include_social_audio")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val BASS_LEVEL = floatPreferencesKey("bass_level")
        val TREBLE_LEVEL = floatPreferencesKey("treble_level")
        val SUPER_BASS = booleanPreferencesKey("super_bass")
        val BASS_BOOST = floatPreferencesKey("bass_boost")
        val LANDSCAPE = booleanPreferencesKey("landscape")
        val LANDSCAPE_PRESET = stringPreferencesKey("landscape_preset")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val DOCK_AUTO_HIDE = intPreferencesKey("dock_auto_hide")
        val DOCK_COLLAPSED = booleanPreferencesKey("dock_collapsed")
        val MINI_AUTO_HIDE = booleanPreferencesKey("mini_auto_hide")
        val MINI_AUTO_HIDE_SECONDS = intPreferencesKey("mini_auto_hide_seconds")
        val LANGUAGE = stringPreferencesKey("language")
        val LIGHT_MODE = booleanPreferencesKey("light_mode")
        val BALANCE = floatPreferencesKey("balance")
        val FADER = floatPreferencesKey("fader")
        val DUCK = booleanPreferencesKey("duck")
        val GAPLESS = booleanPreferencesKey("gapless")
        val REMEMBER_SHUFFLE = booleanPreferencesKey("remember_shuffle")
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
            customFolderUri = p[Keys.CUSTOM_FOLDER] ?: "",
            autoScanEnabled = p[Keys.AUTO_SCAN] ?: false,
            autoScanIntervalHours = p[Keys.AUTO_SCAN_INTERVAL] ?: 24,
            lastAutoScanAt = p[Keys.LAST_AUTO_SCAN] ?: 0L,
            libraryOrder = p[Keys.LIBRARY_ORDER] ?: "Albums,Tracks,Folders,Genres,Podcasts,Artists",
            homeOrder = p[Keys.HOME_ORDER] ?: "Home,Discovery,Favorites,Top,History",
            keepMiniPlayerVisible = p[Keys.MINI] ?: true,
            savePlayHistory = p[Keys.HISTORY] ?: true,
            amoledBlack = p[Keys.AMOLED] ?: false,
            favoriteIds = p[Keys.FAVORITES] ?: "",
            includeSocialAudio = p[Keys.INCLUDE_SOCIAL_AUDIO] ?: false,
            equalizerEnabled = p[Keys.EQ_ENABLED] ?: false,
            equalizerPreset = p[Keys.EQ_PRESET] ?: "Flat",
            equalizerBands = p[Keys.EQ_BANDS] ?: "0,0,0,0,0",
            bassLevel = p[Keys.BASS_LEVEL] ?: 0f,
            trebleLevel = p[Keys.TREBLE_LEVEL] ?: 0f,
            superBassBoost = p[Keys.SUPER_BASS] ?: false,
            bassBoostLevel = p[Keys.BASS_BOOST] ?: 0f,
            landscapeMode = p[Keys.LANDSCAPE] ?: false,
            landscapePreset = p[Keys.LANDSCAPE_PRESET] ?: "7",
            uiScale = p[Keys.UI_SCALE] ?: 1.0f,
            dockAutoHideSeconds = p[Keys.DOCK_AUTO_HIDE] ?: 5,
            dockStartsCollapsed = p[Keys.DOCK_COLLAPSED] ?: false,
            miniPlayerAutoHideEnabled = p[Keys.MINI_AUTO_HIDE] ?: true,
            miniPlayerAutoHideSeconds = p[Keys.MINI_AUTO_HIDE_SECONDS] ?: 5,
            language = p[Keys.LANGUAGE] ?: "en",
            lightMode = p[Keys.LIGHT_MODE] ?: false,
            balanceLevel = p[Keys.BALANCE] ?: 0f,
            faderLevel = p[Keys.FADER] ?: 0f,
            duckOnFocusLoss = p[Keys.DUCK] ?: true,
            gaplessPlayback = p[Keys.GAPLESS] ?: true,
            rememberShuffle = p[Keys.REMEMBER_SHUFFLE] ?: true
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
        p[Keys.CUSTOM_FOLDER] = s.customFolderUri
        p[Keys.AUTO_SCAN] = s.autoScanEnabled
        p[Keys.AUTO_SCAN_INTERVAL] = s.autoScanIntervalHours
        p[Keys.LAST_AUTO_SCAN] = s.lastAutoScanAt
        p[Keys.LIBRARY_ORDER] = s.libraryOrder
        p[Keys.HOME_ORDER] = s.homeOrder
        p[Keys.MINI] = s.keepMiniPlayerVisible
        p[Keys.HISTORY] = s.savePlayHistory
        p[Keys.AMOLED] = s.amoledBlack
        p[Keys.FAVORITES] = s.favoriteIds
        p[Keys.INCLUDE_SOCIAL_AUDIO] = s.includeSocialAudio
        p[Keys.EQ_ENABLED] = s.equalizerEnabled
        p[Keys.EQ_PRESET] = s.equalizerPreset
        p[Keys.EQ_BANDS] = s.equalizerBands
        p[Keys.BASS_LEVEL] = s.bassLevel
        p[Keys.TREBLE_LEVEL] = s.trebleLevel
        p[Keys.SUPER_BASS] = s.superBassBoost
        p[Keys.BASS_BOOST] = s.bassBoostLevel
        p[Keys.LANDSCAPE] = s.landscapeMode
        p[Keys.LANDSCAPE_PRESET] = s.landscapePreset
        p[Keys.UI_SCALE] = s.uiScale
        p[Keys.DOCK_AUTO_HIDE] = s.dockAutoHideSeconds
        p[Keys.DOCK_COLLAPSED] = s.dockStartsCollapsed
        p[Keys.MINI_AUTO_HIDE] = s.miniPlayerAutoHideEnabled
        p[Keys.MINI_AUTO_HIDE_SECONDS] = s.miniPlayerAutoHideSeconds
        p[Keys.LANGUAGE] = s.language
        p[Keys.LIGHT_MODE] = s.lightMode
        p[Keys.BALANCE] = s.balanceLevel
        p[Keys.FADER] = s.faderLevel
        p[Keys.DUCK] = s.duckOnFocusLoss
        p[Keys.GAPLESS] = s.gaplessPlayback
        p[Keys.REMEMBER_SHUFFLE] = s.rememberShuffle
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
            customFolderUri = imported.customFolderUri ?: "",
            autoScanEnabled = imported.autoScanEnabled,
            autoScanIntervalHours = imported.autoScanIntervalHours.takeIf { it > 0 } ?: 24,
            lastAutoScanAt = imported.lastAutoScanAt,
            libraryOrder = imported.libraryOrder ?: "Albums,Tracks,Folders,Genres,Podcasts,Artists",
            homeOrder = imported.homeOrder ?: "Home,Discovery,Favorites,Top,History",
            favoriteIds = imported.favoriteIds ?: "",
            includeSocialAudio = imported.includeSocialAudio,
            equalizerEnabled = imported.equalizerEnabled,
            equalizerPreset = imported.equalizerPreset ?: "Flat",
            equalizerBands = imported.equalizerBands ?: "0,0,0,0,0",
            bassLevel = imported.bassLevel,
            trebleLevel = imported.trebleLevel,
            superBassBoost = imported.superBassBoost,
            bassBoostLevel = imported.bassBoostLevel,
            landscapeMode = imported.landscapeMode,
            landscapePreset = imported.landscapePreset ?: "7",
            uiScale = imported.uiScale.takeIf { it > 0f } ?: 1.0f,
            dockAutoHideSeconds = imported.dockAutoHideSeconds.takeIf { it > 0 } ?: 5,
            dockStartsCollapsed = imported.dockStartsCollapsed,
            miniPlayerAutoHideEnabled = imported.miniPlayerAutoHideEnabled,
            miniPlayerAutoHideSeconds = imported.miniPlayerAutoHideSeconds.takeIf { it > 0 } ?: 5,
            language = imported.language ?: "en",
            lightMode = imported.lightMode,
            balanceLevel = imported.balanceLevel,
            faderLevel = imported.faderLevel,
            duckOnFocusLoss = imported.duckOnFocusLoss,
            gaplessPlayback = imported.gaplessPlayback,
            rememberShuffle = imported.rememberShuffle
        )
    }
}
