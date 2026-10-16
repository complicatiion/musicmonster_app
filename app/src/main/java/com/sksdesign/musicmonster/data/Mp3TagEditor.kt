package com.sksdesign.musicmonster.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/** Minimal ID3v2.3/v2.4 editor for writable local MP3 URIs.
 * Preserves unrelated frames (including attached pictures) and all audio bytes.
 * Does not rewrite non-MP3 containers or unsupported compressed/unsynchronized tags.
 * File providers may reject writes; an error is returned instead of pretending success.
 */
object Mp3TagEditor {
    private fun synchsafe(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
               ((data[offset + 1].toInt() and 0x7F) shl 14) or
               ((data[offset + 2].toInt() and 0x7F) shl 7) or
               (data[offset + 3].toInt() and 0x7F)
    }
    private fun writeSize(value: Int, syncsafe: Boolean) = if (syncsafe) byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(), ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(), (value and 0x7F).toByte()) else byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    private fun readSize(data: ByteArray, index: Int, syncsafe: Boolean) = if (syncsafe) synchsafe(data, index) else
        ((data[index].toInt() and 255) shl 24) or ((data[index + 1].toInt() and 255) shl 16) or
        ((data[index + 2].toInt() and 255) shl 8) or (data[index + 3].toInt() and 255)
    private fun textFrame(name: String, value: String, version: Int): ByteArray {
        // Encoding 1 (UTF-16 BOM) is supported by ID3v2.3 and ID3v2.4.
        val encoded = byteArrayOf(1) + value.toByteArray(Charset.forName("UTF-16"))
        return name.toByteArray(Charsets.US_ASCII) + writeSize(encoded.size, version == 4) + byteArrayOf(0, 0) + encoded
    }
    private fun readFully(stream: InputStream, dst: ByteArray) {
        var pos = 0
        while (pos < dst.size) {
            val read = stream.read(dst, pos, dst.size - pos)
            if (read < 0) error("Unexpected end of MP3")
            pos += read
        }
    }

    suspend fun save(context: Context, track: Track, title: String, artist: String, album: String, genre: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(!track.isNetwork) { "Network files are read-only in the tag editor" }
            require(track.uri.scheme == "content" || track.uri.scheme == "file") { "Unsupported URI" }
            val mime = context.contentResolver.getType(track.uri)?.lowercase().orEmpty()
            val name = track.uri.lastPathSegment.orEmpty().lowercase()
            require(mime in setOf("audio/mpeg", "audio/mp3", "audio/x-mpeg") || name.endsWith(".mp3")) {
                "Only MP3 audio files are supported"
            }
            require(title.isNotBlank()) { "Title cannot be empty" }
            // A MediaStore display name may be missing: verify the actual file signature instead.
            val original = File.createTempFile("mm-id3-original-", ".mp3", context.cacheDir)
            val updated = File.createTempFile("mm-id3-updated-", ".mp3", context.cacheDir)
            try {
                context.contentResolver.openInputStream(track.uri)?.use { src ->
                    FileOutputStream(original).use { dst -> src.copyTo(dst) }
                } ?: error("Unable to read selected track")
                FileInputStream(original).use { input ->
                    val head = ByteArray(10)
                    readFully(input, head)
                    val hasId3 = head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
                    val version = if (hasId3) head[3].toInt() and 255 else 3
                    require(version == 3 || version == 4) { "Only ID3v2.3 and ID3v2.4 tags are supported" }
                    if (hasId3) require((head[5].toInt() and 0xE0) == 0) { "Extended/unsynchronized ID3 tags are not supported for safe editing" }
                    val existingLength = if (hasId3) synchsafe(head, 6) else 0
                    require(existingLength in 0..(16 * 1024 * 1024)) { "Tag header too large" }
                    val body = ByteArray(existingLength)
                    if (hasId3) readFully(input, body)
                    val retained = java.io.ByteArrayOutputStream()
                    var pos = 0
                    while (pos + 10 <= body.size) {
                        val name = body.copyOfRange(pos, pos + 4).toString(Charsets.US_ASCII)
                        if (!name.matches(Regex("[A-Z0-9]{4}"))) break
                        val len = readSize(body, pos + 4, version == 4)
                        require(len >= 0 && pos + 10 + len <= body.size) { "Corrupt ID3 frame" }
                        if (name !in setOf("TIT2", "TPE1", "TALB", "TCON")) retained.write(body, pos, len + 10)
                        pos += len + 10
                    }
                    val replacement = java.io.ByteArrayOutputStream().apply {
                        write(retained.toByteArray())
                        write(textFrame("TIT2", title, version)); write(textFrame("TPE1", artist, version))
                        write(textFrame("TALB", album, version)); write(textFrame("TCON", genre, version))
                    }.toByteArray()
                    require(replacement.size <= 16 * 1024 * 1024) { "Tags too large" }
                    FileOutputStream(updated).use { output ->
                        output.write("ID3".toByteArray(Charsets.US_ASCII))
                        output.write(byteArrayOf(version.toByte(), 0, 0))
                        output.write(writeSize(replacement.size, true))
                        output.write(replacement)
                        // Preserve remaining audio, including the original ID3v1 trailer.
                        FileInputStream(original).use { content ->
                            val toSkip = if (hasId3) 10L + existingLength.toLong() + (if ((head[5].toInt() and 16) != 0) 10L else 0L) else 0L
                            var remaining = toSkip
                            while (remaining > 0L) {
                                val skipped = content.skip(remaining)
                                if (skipped <= 0) error("Cannot seek past existing ID3 tag")
                                remaining -= skipped
                            }
                            content.copyTo(output)
                        }
                    }
                }
                // Provider must grant a real write. No cache-only edits masquerading as file edits.
                try {
                    val out = context.contentResolver.openOutputStream(track.uri, "wt")
                        ?: error("Selected provider is not writable")
                    out.use { dst -> FileInputStream(updated).use { src -> src.copyTo(dst) } }
                } catch (writeError: Exception) {
                    // Best-effort restore; on some SAF providers writes are non-atomic.
                    runCatching {
                        context.contentResolver.openOutputStream(track.uri, "wt")?.use { dst ->
                            FileInputStream(original).use { src -> src.copyTo(dst) }
                        }
                    }
                    throw writeError
                }
            } finally { original.delete(); updated.delete() }
            // Always return Unit: OutputStream.copyTo() returns Long (byte count).
            // Without this, runCatching infers Result<Long> instead of Result<Unit>.
            Unit
        }
    }
}
