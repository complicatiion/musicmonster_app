package com.sksdesign.musicmonster.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.sksdesign.musicmonster.data.Track

data class LibrarySourceEntity(
    val sourceKey: String,
    val sourceType: String,
    val name: String,
    val uri: String,
    val lastScanAt: Long = System.currentTimeMillis(),
    val lastSuccessfulScanAt: Long = System.currentTimeMillis(),
    val available: Boolean = true
)

data class TrackEntity(
    val rowId: Long = 0L,
    val trackId: Long,
    val sourceKey: String,
    val sourceType: String,
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
    val dateAdded: Long,
    val isPodcast: Boolean,
    val isOst: Boolean,
    val isNetwork: Boolean,
    val lastSeenAt: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track = Track(
        id = trackId,
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
        isPodcast = isPodcast,
        isOst = isOst,
        isNetwork = isNetwork
    )
}

fun Track.toEntity(sourceKey: String, sourceType: String): TrackEntity = TrackEntity(
    trackId = id,
    sourceKey = sourceKey,
    sourceType = sourceType,
    title = title,
    artist = artist,
    album = album,
    genre = genre,
    durationMs = durationMs,
    uri = uri.toString(),
    folder = folder,
    artworkUri = artworkUri?.toString(),
    trackNumber = trackNumber,
    playCount = playCount,
    lastPlayed = lastPlayed,
    dateAdded = dateAdded,
    isPodcast = isPodcast,
    isOst = isOst,
    isNetwork = isNetwork
)

/**
 * Native SQLite library index for MusicMonster.
 *
 * V3.2.4 build-fix note:
 * The first V3.2.4 package used Room annotations and KAPT for this lightweight
 * library index. Some Android Studio/AGP/Kotlin combinations failed during
 * :app:kaptReleaseKotlin without emitting a useful processor message. The app
 * only needs a compact SQLite metadata index here, so this implementation uses
 * Android's SQLiteOpenHelper directly and removes the KAPT build step entirely.
 *
 * DataStore still owns settings/legal/theme state. JSON/Gson remains available
 * as legacy compatibility cache. Audio files and cover bytes are not stored in
 * SQLite; only URI/path metadata is indexed.
 */
class MusicDatabase private constructor(context: Context) {
    private val helper = MusicOpenHelper(context.applicationContext)
    private val dao = MusicLibraryDao(helper)

    fun libraryDao(): MusicLibraryDao = dao

    companion object {
        @Volatile private var INSTANCE: MusicDatabase? = null

        fun get(context: Context): MusicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: MusicDatabase(context).also { INSTANCE = it }
        }
    }
}

class MusicLibraryDao internal constructor(private val helper: MusicOpenHelper) {
    suspend fun tracksForSource(sourceKey: String): List<TrackEntity> = queryTracks(
        selection = "source_key = ?",
        selectionArgs = arrayOf(sourceKey)
    )

    suspend fun localTracks(): List<TrackEntity> = queryTracks(
        selection = "is_network = 0 AND is_podcast = 0",
        selectionArgs = emptyArray<String>()
    )

    suspend fun podcastTracks(): List<TrackEntity> = queryTracks(
        selection = "is_podcast = 1",
        selectionArgs = emptyArray<String>()
    )

    suspend fun networkTracks(sourceKey: String): List<TrackEntity> = queryTracks(
        selection = "is_network = 1 AND source_key = ?",
        selectionArgs = arrayOf(sourceKey)
    )

    suspend fun upsertSource(source: LibrarySourceEntity) {
        helper.writableDatabase.insertWithOnConflict(
            MusicOpenHelper.TABLE_SOURCES,
            null,
            source.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun insertTracks(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            tracks.forEach { track ->
                db.insertWithOnConflict(
                    MusicOpenHelper.TABLE_TRACKS,
                    null,
                    track.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteTracksForSource(sourceKey: String) {
        helper.writableDatabase.delete(
            MusicOpenHelper.TABLE_TRACKS,
            "source_key = ?",
            arrayOf(sourceKey)
        )
    }

    suspend fun deleteLocalAndPodcastTracks() {
        helper.writableDatabase.delete(
            MusicOpenHelper.TABLE_TRACKS,
            "is_network = 0",
            emptyArray<String>()
        )
    }

    suspend fun replaceTracksForSource(source: LibrarySourceEntity, tracks: List<TrackEntity>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                MusicOpenHelper.TABLE_SOURCES,
                null,
                source.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.delete(
                MusicOpenHelper.TABLE_TRACKS,
                "source_key = ?",
                arrayOf(source.sourceKey)
            )
            tracks.forEach { track ->
                db.insertWithOnConflict(
                    MusicOpenHelper.TABLE_TRACKS,
                    null,
                    track.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearPhoneLibrary() {
        deleteLocalAndPodcastTracks()
    }

    private fun queryTracks(selection: String, selectionArgs: Array<String>): List<TrackEntity> {
        val db = helper.readableDatabase
        val tracks = mutableListOf<TrackEntity>()
        db.query(
            MusicOpenHelper.TABLE_TRACKS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "album COLLATE NOCASE, track_number, title COLLATE NOCASE"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tracks += cursor.toTrackEntity()
            }
        }
        return tracks
    }
}

class MusicOpenHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Preserve already indexed metadata from the previous Room-backed V3.2.x
        // database where table names and columns are compatible. CREATE IF NOT
        // EXISTS also repairs missing tables when users jump from older builds.
        createSchema(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SOURCES (
                source_key TEXT NOT NULL PRIMARY KEY,
                source_type TEXT NOT NULL,
                name TEXT NOT NULL,
                uri TEXT NOT NULL,
                last_scan_at INTEGER NOT NULL,
                last_successful_scan_at INTEGER NOT NULL,
                available INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_sources_source_type ON $TABLE_SOURCES(source_type)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRACKS (
                rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                track_id INTEGER NOT NULL,
                source_key TEXT NOT NULL,
                source_type TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                genre TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                uri TEXT NOT NULL,
                folder TEXT NOT NULL,
                artwork_uri TEXT,
                track_number INTEGER NOT NULL,
                play_count INTEGER NOT NULL,
                last_played INTEGER NOT NULL,
                date_added INTEGER NOT NULL,
                is_podcast INTEGER NOT NULL,
                is_ost INTEGER NOT NULL,
                is_network INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_uri ON $TABLE_TRACKS(uri)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_source_key ON $TABLE_TRACKS(source_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_title ON $TABLE_TRACKS(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_artist ON $TABLE_TRACKS(artist)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_album ON $TABLE_TRACKS(album)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_genre ON $TABLE_TRACKS(genre)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_folder ON $TABLE_TRACKS(folder)")
    }

    companion object {
        const val DATABASE_NAME = "musicmonster-library.db"
        const val DATABASE_VERSION = 2
        const val TABLE_SOURCES = "library_sources"
        const val TABLE_TRACKS = "tracks"
    }
}

private fun LibrarySourceEntity.toContentValues(): ContentValues = ContentValues().apply {
    put("source_key", sourceKey)
    put("source_type", sourceType)
    put("name", name)
    put("uri", uri)
    put("last_scan_at", lastScanAt)
    put("last_successful_scan_at", lastSuccessfulScanAt)
    put("available", if (available) 1 else 0)
}

private fun TrackEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (rowId > 0L) put("rowId", rowId)
    put("track_id", trackId)
    put("source_key", sourceKey)
    put("source_type", sourceType)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("genre", genre)
    put("duration_ms", durationMs)
    put("uri", uri)
    put("folder", folder)
    put("artwork_uri", artworkUri)
    put("track_number", trackNumber)
    put("play_count", playCount)
    put("last_played", lastPlayed)
    put("date_added", dateAdded)
    put("is_podcast", if (isPodcast) 1 else 0)
    put("is_ost", if (isOst) 1 else 0)
    put("is_network", if (isNetwork) 1 else 0)
    put("last_seen_at", lastSeenAt)
}

private fun Cursor.toTrackEntity(): TrackEntity = TrackEntity(
    rowId = getLong(getColumnIndexOrThrow("rowId")),
    trackId = getLong(getColumnIndexOrThrow("track_id")),
    sourceKey = getString(getColumnIndexOrThrow("source_key")).orEmpty(),
    sourceType = getString(getColumnIndexOrThrow("source_type")).orEmpty(),
    title = getString(getColumnIndexOrThrow("title")).orEmpty(),
    artist = getString(getColumnIndexOrThrow("artist")).orEmpty(),
    album = getString(getColumnIndexOrThrow("album")).orEmpty(),
    genre = getString(getColumnIndexOrThrow("genre")).orEmpty(),
    durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
    uri = getString(getColumnIndexOrThrow("uri")).orEmpty(),
    folder = getString(getColumnIndexOrThrow("folder")).orEmpty(),
    artworkUri = getStringOrNull("artwork_uri"),
    trackNumber = getInt(getColumnIndexOrThrow("track_number")),
    playCount = getInt(getColumnIndexOrThrow("play_count")),
    lastPlayed = getLong(getColumnIndexOrThrow("last_played")),
    dateAdded = getLong(getColumnIndexOrThrow("date_added")),
    isPodcast = getInt(getColumnIndexOrThrow("is_podcast")) != 0,
    isOst = getInt(getColumnIndexOrThrow("is_ost")) != 0,
    isNetwork = getInt(getColumnIndexOrThrow("is_network")) != 0,
    lastSeenAt = getLong(getColumnIndexOrThrow("last_seen_at"))
)

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getString(index)
}
