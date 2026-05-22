package com.sksdesign.musicmonster.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

class MusicRepository(private val context: Context) {
    private val gson = Gson()
    private val libraryCacheFile = File(context.filesDir, "musicmonster-library-cache.json")
    private val podcastCacheFile = File(context.filesDir, "musicmonster-podcast-cache.json")
    private val playlistCacheFile = File(context.filesDir, "musicmonster-playlists-cache.json")
    private val albumArtworkCache = mutableMapOf<Long, Uri?>()

    suspend fun loadTracks(selectedFolderUri: String = "", includeSocialAudio: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        if (selectedFolderUri.isNotBlank()) scanDocumentTree(selectedFolderUri, podcast = false, includeSocialAudio = includeSocialAudio) else loadMediaStoreTracks(includeSocialAudio)
    }

    suspend fun loadPodcasts(selectedFolderUri: String = "", fallbackTracks: List<Track> = emptyList(), includeSocialAudio: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        if (selectedFolderUri.isNotBlank()) {
            scanDocumentTree(selectedFolderUri, podcast = true, includeSocialAudio = includeSocialAudio)
        } else {
            fallbackTracks.filter { track ->
                track.isPodcast ||
                    track.folder.contains("podcast", ignoreCase = true) ||
                    track.genre.contains("podcast", ignoreCase = true) ||
                    track.album.contains("podcast", ignoreCase = true)
            }
        }
    }

    suspend fun loadCachedTracks(): List<Track> = withContext(Dispatchers.IO) { readTrackCache(libraryCacheFile) }
    suspend fun loadCachedPodcasts(): List<Track> = withContext(Dispatchers.IO) { readTrackCache(podcastCacheFile) }
    suspend fun loadCachedPlaylists(): List<Playlist> = withContext(Dispatchers.IO) { readPlaylistCache() }
    suspend fun saveCachedTracks(tracks: List<Track>) = withContext(Dispatchers.IO) { writeTrackCache(libraryCacheFile, tracks) }
    suspend fun saveCachedPodcasts(tracks: List<Track>) = withContext(Dispatchers.IO) { writeTrackCache(podcastCacheFile, tracks) }
    suspend fun saveCachedPlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) { writePlaylistCache(playlists) }
    suspend fun clearCachedLibrary() = withContext(Dispatchers.IO) {
        libraryCacheFile.delete()
        podcastCacheFile.delete()
    }

    private fun loadMediaStoreTracks(includeSocialAudio: Boolean): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val genreMap = loadGenreMap()
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC}=1",
            null,
            "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol).orEmpty()
                if (!includeSocialAudio && shouldSkipSocialAudioPath(path)) continue
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val folder = path.substringBeforeLast('/', "Music").substringAfterLast('/', "Music")
                val artwork = albumArtworkUri(albumId)
                val genre = genreMap[id] ?: "Unknown"
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleCol).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistCol).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumCol).orEmpty().ifBlank { folder.ifBlank { "Unknown album" } },
                    genre = genre,
                    durationMs = cursor.getLong(durationCol),
                    uri = uri,
                    folder = folder,
                    artworkUri = artwork,
                    trackNumber = normalizeTrackNumber(cursor.getInt(trackCol)),
                    dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                    isPodcast = genre.contains("podcast", ignoreCase = true) || folder.contains("podcast", ignoreCase = true)
                )
            }
        }
        return tracks
    }

    private fun albumArtworkUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return albumArtworkCache.getOrPut(albumId) {
            val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                uri
            }.getOrNull()
        }
    }

    private fun loadGenreMap(): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
            )?.use { genres ->
                val idCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(idCol)
                    val name = genres.getString(nameCol).orEmpty().ifBlank { "Unknown" }
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null
                    )?.use { members ->
                        val audioIdCol = members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (members.moveToNext()) result[members.getLong(audioIdCol)] = name
                    }
                }
            }
        }
        return result
    }

    private fun scanDocumentTree(treeUriString: String, podcast: Boolean, includeSocialAudio: Boolean): List<Track> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return emptyList()
        val files = mutableListOf<ScannedAudioFile>()
        collectAudioFiles(root, files, root.name.orEmpty(), includeSocialAudio)
        return files.mapIndexedNotNull { index, scanned ->
            val file = scanned.file
            val metadata = readMetadata(file.uri, file.uri.toString().hashCode().toLong(), file.lastModified())
            val fileName = file.name.orEmpty().substringBeforeLast('.').ifBlank { "Unknown title" }
            val folder = scanned.relativePath.substringBeforeLast('/', root.name ?: if (podcast) "Podcasts" else "Music").substringAfterLast('/', root.name ?: "Music")
            Track(
                id = file.uri.toString().hashCode().toLong(),
                title = metadata.title ?: fileName,
                artist = metadata.artist ?: if (podcast) "Podcast" else "Unknown artist",
                album = metadata.album ?: folder,
                genre = metadata.genre ?: if (podcast) "Podcast" else "Unknown",
                durationMs = metadata.durationMs ?: 0L,
                uri = file.uri,
                folder = folder,
                artworkUri = metadata.artworkUri,
                trackNumber = metadata.trackNumber ?: (index + 1),
                dateAdded = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                isPodcast = podcast
            )
        }.sortedWith(compareBy<Track> { it.album }.thenBy { it.trackNumber }.thenBy { it.title })
    }

    private data class ScannedAudioFile(val file: DocumentFile, val relativePath: String)

    private fun collectAudioFiles(parent: DocumentFile, output: MutableList<ScannedAudioFile>, currentPath: String, includeSocialAudio: Boolean) {
        parent.listFiles().forEach { file ->
            val nextPath = listOf(currentPath, file.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/")
            when {
                file.isDirectory -> collectAudioFiles(file, output, nextPath, includeSocialAudio)
                file.isFile && isAudioFile(file.name.orEmpty(), file.type.orEmpty()) -> {
                    if (includeSocialAudio || !shouldSkipSocialAudioPath(nextPath)) output += ScannedAudioFile(file, nextPath)
                }
            }
        }
    }

    private fun shouldSkipSocialAudioPath(path: String): Boolean {
        val p = path.replace('\\', '/').lowercase()
        val markers = listOf(
            "/whatsapp voice notes", "/whatsapp audio", "/whatsapp/voice notes", "com.whatsapp", "com.whatsapp.w4b",
            "com.facebook.orca", "/messenger", "/facebook/messenger", "/fb_temp",
            "/telegram audio", "/telegram voice", "org.telegram.messenger",
            "org.thoughtcrime.securesms", "/signal/audio", "/signal/voice",
            "com.instagram.android", "/instagram", "com.snapchat.android", "/snapchat",
            "com.viber.voip", "/viber", "jp.naver.line.android", "/line/",
            "com.discord", "/discord", "com.skype.raider", "/skype",
            "/voice notes/", "/voicenotes/"
        )
        return markers.any { marker -> p.contains(marker) }
    }

    private fun isAudioFile(name: String, mime: String): Boolean {
        val lower = name.lowercase()
        return mime.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".wav") || lower.endsWith(".wma")
    }

    private data class Metadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val durationMs: Long? = null,
        val trackNumber: Int? = null,
        val artworkUri: Uri? = null
    )

    private fun readMetadata(uri: Uri, stableId: Long, modified: Long): Metadata {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            val artwork = if (picture != null && picture.isNotEmpty()) writeArtworkToCache(stableId, modified, picture) else null
            val metadata = Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { null },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.ifBlank { null },
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.ifBlank { null },
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                trackNumber = parseTrackNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                artworkUri = artwork
            )
            retriever.release()
            metadata
        } catch (_: Throwable) {
            Metadata()
        }
    }

    private fun writeArtworkToCache(stableId: Long, modified: Long, bytes: ByteArray): Uri? {
        return try {
            val dir = File(context.filesDir, "artwork").apply { mkdirs() }
            val file = File(dir, "art_${stableId.absoluteValue}_$modified.jpg")
            if (!file.exists()) file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseTrackNumber(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.substringBefore('/').filter { it.isDigit() }
        return cleaned.toIntOrNull()?.let { normalizeTrackNumber(it) }
    }

    private fun normalizeTrackNumber(value: Int): Int {
        if (value <= 0) return 0
        return value % 1000
    }

    fun albums(tracks: List<Track>): List<Album> = tracks
        .filter { it.album.isNotBlank() }
        .groupBy { it.album }
        .map { (album, albumTracks) ->
            Album(
                name = album,
                artist = albumTracks.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: "Unknown artist",
                tracks = albumTracks.sortedWith(compareBy<Track> { it.trackNumber.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }.thenBy { it.title })
            )
        }
        .sortedBy { it.name.lowercase() }

    fun artists(tracks: List<Track>): List<Artist> = tracks
        .groupBy { it.artist.ifBlank { "Unknown artist" } }
        .map { Artist(it.key, it.value.sortedBy { track -> track.title.lowercase() }) }
        .sortedBy { it.name.lowercase() }

    fun parseM3u(name: String, content: String, tracks: List<Track>): Playlist {
        val byTitle = tracks.associateBy { it.title.lowercase() }
        val byFileName = tracks.associateBy { it.uri.lastPathSegment.orEmpty().substringBeforeLast('.').lowercase() }
        val selected = content.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val normalized = line.substringAfterLast('/').substringBeforeLast('.').lowercase()
                byTitle[normalized] ?: byFileName[normalized]
            }
            .toList()
        return Playlist(name.substringBeforeLast('.'), selected)
    }

    fun exportM3u(playlist: Playlist): String = buildString {
        appendLine("#EXTM3U")
        playlist.tracks.forEach { track ->
            appendLine("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}")
            appendLine(track.uri.toString())
        }
    }

    fun exportPlaylist(playlist: Playlist, format: String): String {
        return if (format.equals("PLS", ignoreCase = true)) {
            buildString {
                appendLine("[playlist]")
                playlist.tracks.forEachIndexed { index, track ->
                    val number = index + 1
                    appendLine("File$number=${track.uri}")
                    appendLine("Title$number=${track.artist} - ${track.title}")
                    appendLine("Length$number=${track.durationMs / 1000}")
                }
                appendLine("NumberOfEntries=${playlist.tracks.size}")
                appendLine("Version=2")
            }
        } else {
            exportM3u(playlist)
        }
    }

    private data class TrackCacheDto(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val durationMs: Long,
        val uri: String,
        val folder: String,
        val artworkUri: String?,
        val trackNumber: Int,
        val playCount: Int,
        val lastPlayed: Long,
        val dateAdded: Long = 0L,
        val isPodcast: Boolean
    )

    private fun Track.toDto() = TrackCacheDto(id, title, artist, album, genre, durationMs, uri.toString(), folder, artworkUri?.toString(), trackNumber, playCount, lastPlayed, dateAdded, isPodcast)

    private fun TrackCacheDto.toTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        uri = Uri.parse(uri),
        folder = folder,
        artworkUri = artworkUri?.let { Uri.parse(it) },
        trackNumber = trackNumber,
        playCount = playCount,
        lastPlayed = lastPlayed,
        dateAdded = dateAdded,
        isPodcast = isPodcast
    )

    private fun writeTrackCache(file: File, tracks: List<Track>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(tracks.map { it.toDto() }))
        }
    }

    private fun readTrackCache(file: File): List<Track> {
        return runCatching {
            if (!file.exists()) return emptyList()
            val type = object : TypeToken<List<TrackCacheDto>>() {}.type
            gson.fromJson<List<TrackCacheDto>>(file.readText(), type).map { it.toTrack() }
        }.getOrDefault(emptyList())
    }


    private data class PlaylistCacheDto(
        val name: String,
        val tracks: List<TrackCacheDto>,
        val updatedAt: Long
    )

    private fun Playlist.toDto() = PlaylistCacheDto(name, tracks.map { it.toDto() }, updatedAt)

    private fun PlaylistCacheDto.toPlaylist() = Playlist(name, tracks.map { it.toTrack() }, updatedAt)

    private fun writePlaylistCache(playlists: List<Playlist>) {
        runCatching {
            playlistCacheFile.parentFile?.mkdirs()
            playlistCacheFile.writeText(gson.toJson(playlists.map { it.toDto() }))
        }
    }

    private fun readPlaylistCache(): List<Playlist> {
        return runCatching {
            if (!playlistCacheFile.exists()) return emptyList()
            val type = object : TypeToken<List<PlaylistCacheDto>>() {}.type
            gson.fromJson<List<PlaylistCacheDto>>(playlistCacheFile.readText(), type).map { it.toPlaylist() }
        }.getOrDefault(emptyList())
    }

}