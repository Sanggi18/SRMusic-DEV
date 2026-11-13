package com.example.core.data.mediastore.tags

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.core.data.database.AppDatabase
import com.example.core.data.database.song.SongEntity
import com.example.core.model.Song
import com.example.core.util.TimeUtils
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.HashMap
import java.util.Locale

data class AudioStreamProps(
    val format: String,
    val sampleRateHz: Int?,
    val bitrateKbps: Int?,
    val bitDepth: Int? = null
) {
    fun formatSampleRate(): String? {
        val hz = sampleRateHz ?: return null

        if (hz >= 1000000) {
            val mhz = hz / 1000000.0
            return if (mhz % 1.0 == 0.0) {
                "${mhz.toInt()} MHz"
            } else {
                String.format(Locale.US, "%.1f MHz", mhz)
            }
        }

        val khz = hz / 1000.0

        return if (khz % 1.0 == 0.0) {
            "${khz.toInt()} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", khz)
        }
    }
}

data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val genre: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val copyright: String = "",
    val lyrics: String = "",
    val artworkWidth: Int = 0,
    val artworkHeight: Int = 0,
    val artworkSizeBytes: Long = 0L,
    val format: String = "",
    val durationMs: Long = 0L,
    val bitrateKbps: Int = 0,
    val fileSizeBytes: Long = 0L,
    val streamSizeBytes: Long = 0L,
    val newArtworkBytes: ByteArray? = null,
    val newArtworkMimeType: String? = null
) {
    val formattedDimensions: String
        get() = if (artworkWidth > 0 && artworkHeight > 0) "${artworkWidth} × ${artworkHeight}" else "No Artwork"

    val formattedArtworkSize: String
        get() = formatByteCount(artworkSizeBytes)

    val formattedDuration: String
        get() = if (durationMs > 0L) TimeUtils.formatDuration(durationMs) else "—"

    val formattedBitrate: String
        get() = if (bitrateKbps > 0) "$bitrateKbps kbps" else "—"

    val formattedFileSize: String
        get() = formatByteCount(fileSizeBytes)

    val formattedStreamSize: String
        get() {
            if (streamSizeBytes <= 0L) return "—"
            val sizeStr = formatByteCount(streamSizeBytes)
            return if (fileSizeBytes > 0L) {
                val pct = ((streamSizeBytes.toDouble() * 100.0) / fileSizeBytes.toDouble()).toInt().coerceIn(1, 100)
                "$sizeStr ($pct%)"
            } else {
                sizeStr
            }
        }

    companion object {
        private fun formatByteCount(bytes: Long): String = when {
            bytes <= 0L -> "—"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

sealed interface SaveMetadataResult {
    data object Success : SaveMetadataResult
    data class NeedsWritePermission(val intentSender: android.content.IntentSender) : SaveMetadataResult
    data object Failed : SaveMetadataResult
}

object TaglibHelper {
    fun getPropertyMap(file: File): Map<String, Array<String>>? {
        return try {
            if (!file.exists() || !file.canRead()) return null

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                TagLib.getMetadata(pfd.dup().detachFd())?.propertyMap
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getFirstProperty(file: File, vararg keys: String): String? {
        val map = getPropertyMap(file) ?: return null

        for (targetKey in keys) {
            val direct = map[targetKey]?.firstOrNull()?.takeIf { it.isNotBlank() }

            if (direct != null) return direct

            val caseInsensitive = map.entries
                .firstOrNull { it.key.equals(targetKey, ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }

            if (caseInsensitive != null) return caseInsensitive
        }

        return null
    }

    fun getLyrics(file: File): String? {
        return getFirstProperty(
            file,
            "LYRICS",
            "UNSYNCEDLYRICS",
            "UNSYNCED LYRICS",
            "SYLT",
            "©lyr"
        )
    }

    fun getArtworkBytes(file: File): ByteArray? {
        return try {
            if (!file.exists() || !file.canRead()) return null

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                TagLib.getPictures(pfd.dup().detachFd())
                    .firstOrNull()?.data
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun detectDetailedAudioFormat(context: Context, song: Song, file: File?): Pair<String, Int?> {
        var detectedFormat: String? = null
        var extractorBitrateKbps: Int? = null

        val extractor = MediaExtractor()
        try {
            if (file != null && file.exists() && file.canRead()) {
                extractor.setDataSource(file.absolutePath)
            } else if (song.contentUri != android.net.Uri.EMPTY) {
                extractor.setDataSource(context, song.contentUri, null)
            }
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                if (trackFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    val br = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE)
                    if (br > 0) extractorBitrateKbps = br / 1000
                }

                val profile = if (trackFormat.containsKey(MediaFormat.KEY_AAC_PROFILE)) {
                    trackFormat.getInteger(MediaFormat.KEY_AAC_PROFILE)
                } else -1

                detectedFormat = when (mime.lowercase(Locale.US)) {
                    MediaFormat.MIMETYPE_AUDIO_AAC.lowercase(Locale.US), "audio/mp4a-latm", "audio/aac" -> {
                        when (profile) {
                            MediaCodecInfo.CodecProfileLevel.AACObjectHE -> "HE-AAC"
                            MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS -> "HE-AAC v2"
                            MediaCodecInfo.CodecProfileLevel.AACObjectLD -> "AAC LD"
                            MediaCodecInfo.CodecProfileLevel.AACObjectELD -> "AAC ELD"
                            MediaCodecInfo.CodecProfileLevel.AACObjectMain -> "AAC Main"
                            MediaCodecInfo.CodecProfileLevel.AACObjectSSR -> "AAC SSR"
                            MediaCodecInfo.CodecProfileLevel.AACObjectLTP -> "AAC LTP"
                            else -> "AAC LC"
                        }
                    }
                    MediaFormat.MIMETYPE_AUDIO_FLAC.lowercase(Locale.US), "audio/flac", "audio/x-flac" -> "FLAC"
                    MediaFormat.MIMETYPE_AUDIO_MPEG.lowercase(Locale.US), "audio/mpeg", "audio/mp3" -> "MP3"
                    MediaFormat.MIMETYPE_AUDIO_OPUS.lowercase(Locale.US), "audio/opus" -> "OPUS"
                    MediaFormat.MIMETYPE_AUDIO_VORBIS.lowercase(Locale.US), "audio/vorbis", "audio/ogg" -> "Vorbis"
                    MediaFormat.MIMETYPE_AUDIO_RAW.lowercase(Locale.US), "audio/raw", "audio/wav", "audio/x-wav" -> "WAV"
                    "audio/alac" -> "ALAC"
                    "audio/ac3" -> "AC-3"
                    "audio/eac3" -> "E-AC-3"
                    else -> null
                }
                if (detectedFormat != null) break
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        val streamProps = getAudioStreamProps(song, context)
        val finalFormat = when {
            streamProps.format == "ALAC" -> "ALAC"
            detectedFormat != null -> detectedFormat
            streamProps.format == "M4A" || streamProps.format == "AAC" -> "AAC LC"
            else -> streamProps.format
        }
        return finalFormat to (streamProps.bitrateKbps ?: extractorBitrateKbps)
    }

    fun extractDetailedFormatFromSong(song: Song, context: Context): String {
        val dp = song.dataPath
        val file = dp?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
        return detectDetailedAudioFormat(context, song, file).first
    }

    fun getSongMetadata(context: Context, song: Song): SongMetadata {
        var title = song.title
        var artist = song.artist
        var album = song.album
        var year = song.year?.toString() ?: ""
        var trackNumber = song.trackNumber?.toString() ?: ""
        var discNumber = ""
        var genre = song.genre ?: ""
        var albumArtist = ""
        var composer = ""
        var copyright = ""
        var lyrics = ""
        var artworkWidth = 0
        var artworkHeight = 0
        var artworkSizeBytes = 0L
        var tagTextBytes = 0L
        var durationMs = song.duration
        var bitrateKbps = 0

        val file = song.dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
        var fileSizeBytes = file?.length()?.takeIf { it > 0L } ?: (song.size ?: 0L)

        val pfd = try {
            if (file != null) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else if (song.contentUri != android.net.Uri.EMPTY) {
                context.contentResolver.openFileDescriptor(song.contentUri, "r")
            } else null
        } catch (_: Exception) {
            null
        }

        pfd?.use { descriptor ->
            try {
                if (fileSizeBytes <= 0L) {
                    val stat = descriptor.statSize
                    if (stat > 0L) fileSizeBytes = stat
                }

                val props = runCatching { TagLib.getAudioProperties(descriptor.dup().detachFd()) }.getOrNull()
                if (props != null) {
                    if (props.bitrate > 0) bitrateKbps = props.bitrate
                    if (durationMs <= 0L && props.length > 0) {
                        durationMs = props.length * 1000L
                    }
                }

                val metadata = TagLib.getMetadata(descriptor.dup().detachFd())
                val map = metadata?.propertyMap
                if (map != null) {
                    for ((k, values) in map) {
                        tagTextBytes += k.toByteArray(Charsets.UTF_8).size
                        for (v in values) {
                            tagTextBytes += v.toByteArray(Charsets.UTF_8).size
                        }
                    }

                    fun getVal(vararg keys: String): String {
                        for (k in keys) {
                            map[k]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                            map.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                        }
                        return ""
                    }
                    val t = getVal("TITLE")
                    if (t.isNotBlank()) title = t
                    val a = getVal("ARTIST")
                    if (a.isNotBlank()) artist = a
                    val al = getVal("ALBUM")
                    if (al.isNotBlank()) album = al
                    val y = getVal("DATE", "YEAR")
                    if (y.isNotBlank()) year = y
                    val tr = getVal("TRACKNUMBER")
                    if (tr.isNotBlank()) trackNumber = tr
                    val dn = getVal("DISCNUMBER")
                    if (dn.isNotBlank()) discNumber = dn
                    val g = getVal("GENRE")
                    if (g.isNotBlank()) genre = g
                    val aa = getVal("ALBUMARTIST", "ALBUM ARTIST")
                    if (aa.isNotBlank()) albumArtist = aa
                    val comp = getVal("COMPOSER")
                    if (comp.isNotBlank()) composer = comp
                    val cpr = getVal("COPYRIGHT")
                    if (cpr.isNotBlank()) copyright = cpr
                    val lyr = getVal("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYLT", "©lyr")
                    if (lyr.isNotBlank()) lyrics = lyr
                }

                val pics = metadata?.pictures
                val pic = pics?.firstOrNull()
                if (pic != null && pic.data.isNotEmpty()) {
                    val data = pic.data
                    artworkSizeBytes = data.size.toLong()
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                    artworkWidth = opts.outWidth
                    artworkHeight = opts.outHeight
                }
            } catch (_: Exception) {
            }
        }

        if (artworkWidth <= 0 || artworkHeight <= 0) {
            try {
                val uriToUse = if (song.artworkUri != android.net.Uri.EMPTY) song.artworkUri else song.contentUri
                if (uriToUse != android.net.Uri.EMPTY) {
                    context.contentResolver.openInputStream(uriToUse)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.isNotEmpty()) {
                            artworkSizeBytes = bytes.size.toLong()
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            artworkWidth = opts.outWidth
                            artworkHeight = opts.outHeight
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val (detailedFormat, detectedBitrate) = detectDetailedAudioFormat(context, song, file)
        if (bitrateKbps <= 0 && detectedBitrate != null && detectedBitrate > 0) {
            bitrateKbps = detectedBitrate
        }
        if (bitrateKbps <= 0 && durationMs > 0L && fileSizeBytes > 0L) {
            val audioBytes = (fileSizeBytes - artworkSizeBytes - tagTextBytes).coerceAtLeast(1L)
            bitrateKbps = ((audioBytes * 8L) / durationMs).toInt()
        }

        val streamSizeBytes = when {
            fileSizeBytes > 0L -> {
                val tagOverhead = tagTextBytes + 1024L
                val deducted = fileSizeBytes - artworkSizeBytes - tagOverhead
                if (deducted > 0L) deducted else (fileSizeBytes - artworkSizeBytes).coerceAtLeast(0L)
            }
            bitrateKbps > 0 && durationMs > 0L -> {
                (bitrateKbps.toLong() * durationMs) / 8L
            }
            else -> 0L
        }

        return SongMetadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            genre = genre,
            albumArtist = albumArtist,
            composer = composer,
            copyright = copyright,
            lyrics = lyrics,
            artworkWidth = artworkWidth,
            artworkHeight = artworkHeight,
            artworkSizeBytes = artworkSizeBytes,
            format = detailedFormat,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            fileSizeBytes = fileSizeBytes,
            streamSizeBytes = streamSizeBytes
        )
    }

    suspend fun saveSongMetadataWithResult(
        context: Context,
        song: Song,
        newMetadata: SongMetadata
    ): SaveMetadataResult {
        return withContext(Dispatchers.IO) {
            val file = song.dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
            val ext = (file?.extension?.takeIf { it.isNotBlank() }
                ?: song.displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                ?: "mp3")
            val tempFile = File(context.cacheDir, "tag_edit_${song.id}_${System.currentTimeMillis()}.$ext")

            try {
                val copiedToTemp = try {
                    when {
                        file != null && file.canRead() -> {
                            file.inputStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            true
                        }
                        song.contentUri != android.net.Uri.EMPTY -> {
                            context.contentResolver.openInputStream(song.contentUri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                                true
                            } ?: false
                        }
                        else -> false
                    }
                } catch (_: Exception) {
                    false
                }

                if (!copiedToTemp || !tempFile.exists() || tempFile.length() <= 0L) {
                    return@withContext SaveMetadataResult.Failed
                }

                var propSaved = false
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                    val existingMeta = TagLib.getMetadata(descriptor.dup().detachFd())
                    val map = existingMeta?.propertyMap ?: HashMap<String, Array<String>>()
                    map["TITLE"] = arrayOf(newMetadata.title)
                    map["ARTIST"] = arrayOf(newMetadata.artist)
                    map["ALBUM"] = arrayOf(newMetadata.album)
                    if (newMetadata.year.isNotBlank()) map["DATE"] = arrayOf(newMetadata.year)
                    if (newMetadata.trackNumber.isNotBlank()) map["TRACKNUMBER"] = arrayOf(newMetadata.trackNumber)
                    if (newMetadata.discNumber.isNotBlank()) map["DISCNUMBER"] = arrayOf(newMetadata.discNumber)
                    if (newMetadata.genre.isNotBlank()) map["GENRE"] = arrayOf(newMetadata.genre)
                    if (newMetadata.albumArtist.isNotBlank()) map["ALBUMARTIST"] = arrayOf(newMetadata.albumArtist)
                    if (newMetadata.composer.isNotBlank()) map["COMPOSER"] = arrayOf(newMetadata.composer)
                    if (newMetadata.copyright.isNotBlank()) map["COPYRIGHT"] = arrayOf(newMetadata.copyright)
                    if (newMetadata.lyrics.isNotBlank()) {
                        map["LYRICS"] = arrayOf(newMetadata.lyrics)
                        map["UNSYNCEDLYRICS"] = arrayOf(newMetadata.lyrics)
                    }
                    propSaved = TagLib.savePropertyMap(descriptor.dup().detachFd(), map)
                }

                val newArtBytes = newMetadata.newArtworkBytes
                var artSaved = true
                if (newArtBytes != null && newArtBytes.isNotEmpty()) {
                    ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                        val mime = newMetadata.newArtworkMimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                        val picture = Picture(
                            newArtBytes,
                            "Front Cover",
                            "Front Cover",
                            mime
                        )
                        artSaved = TagLib.savePictures(descriptor.dup().detachFd(), arrayOf(picture))
                    }
                }

                if (!propSaved && !artSaved) {
                    return@withContext SaveMetadataResult.Failed
                }

                var writtenBack = false
                if (file != null && file.canWrite()) {
                    try {
                        tempFile.inputStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                                output.fd.sync()
                            }
                        }
                        writtenBack = true
                    } catch (_: Exception) {
                    }
                }

                if (!writtenBack && song.contentUri != android.net.Uri.EMPTY) {
                    try {
                        context.contentResolver.openFileDescriptor(song.contentUri, "rwt")?.use { pfd ->
                            tempFile.inputStream().use { input ->
                                java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            writtenBack = true
                        }
                    } catch (se: SecurityException) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val pi = android.provider.MediaStore.createWriteRequest(
                                context.contentResolver,
                                listOf(song.contentUri)
                            )
                            return@withContext SaveMetadataResult.NeedsWritePermission(pi.intentSender)
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                            se is android.app.RecoverableSecurityException
                        ) {
                            return@withContext SaveMetadataResult.NeedsWritePermission(
                                se.userAction.actionIntent.intentSender
                            )
                        }
                    } catch (_: Exception) {
                    }
                }

                if (!writtenBack) {
                    return@withContext SaveMetadataResult.Failed
                }

                if (file != null) {
                    runCatching {
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    }
                }

                try {
                    val updatedSize = file?.length()?.takeIf { it > 0L } ?: tempFile.length().takeIf { it > 0L } ?: song.size
                    val updatedEntity = SongEntity(
                        id = song.id,
                        title = newMetadata.title.ifBlank { song.title },
                        artist = newMetadata.artist.ifBlank { song.artist },
                        album = newMetadata.album.ifBlank { song.album },
                        duration = song.duration,
                        albumId = song.albumId,
                        contentUriString = song.contentUri.toString(),
                        artworkUriString = song.artworkUri.toString(),
                        trackNumber = newMetadata.trackNumber.toIntOrNull() ?: song.trackNumber,
                        year = newMetadata.year.toIntOrNull() ?: song.year,
                        size = updatedSize,
                        displayName = song.displayName,
                        relativePath = song.relativePath,
                        dataPath = song.dataPath,
                        dateAdded = song.dateAdded,
                        dateModified = System.currentTimeMillis(),
                        genre = newMetadata.genre.ifBlank { song.genre }
                    )
                    AppDatabase.getInstance(context).songDao().upsertSongs(listOf(updatedEntity))
                } catch (_: Exception) {
                }

                SaveMetadataResult.Success
            } finally {
                runCatching { tempFile.delete() }
            }
        }
    }

    private val streamPropsCache = LruCache<Long, AudioStreamProps>(64)

    fun getCachedProps(songId: Long): AudioStreamProps? {
        return streamPropsCache.get(songId)
    }

    fun extractBitDepthFromSong(song: Song, context: Context): Int {
        streamPropsCache.get(song.id)?.bitDepth?.let { return it }
        return getAudioStreamProps(song, context).bitDepth ?: 16
    }

    fun getAudioStreamProps(song: Song, context: Context): AudioStreamProps {
        streamPropsCache.get(song.id)?.let { return it }

        var sampleRate: Int? = null

        var bitrate: Int? = null

        var format = song.displayName?.let { dn ->
            if (dn.contains('.')) dn.substringAfterLast('.').uppercase() else null
        } ?: song.dataPath?.let { dp ->
            if (dp.contains('.')) dp.substringAfterLast('.').uppercase() else null
        } ?: "MP3"

        if (format.contains("AAC", ignoreCase = true) || format.contains("MP4", ignoreCase = true)) {
            format = "M4A"
        }

        try {
            val dp = song.dataPath
            val file = dp?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
            val pfd = if (file != null) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else if (song.contentUri != android.net.Uri.EMPTY) {
                context.contentResolver.openFileDescriptor(song.contentUri, "r")
            } else null

            pfd?.use { descriptor ->
                val props = TagLib.getAudioProperties(descriptor.dup().detachFd())
                if (props != null) {
                    if (props.sampleRate > 0) sampleRate = props.sampleRate
                    if (props.bitrate > 0) bitrate = props.bitrate
                }
            }
        } catch (_: Exception) {}

        if (bitrate == null && song.duration > 0 && (song.size ?: 0L) > 0) {
            bitrate = (((song.size ?: 0L) * 8) / song.duration).toInt()
        }

        var bitDepth: Int? = null

        try {
            val fileToRead = song.dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }

            val inputStream = fileToRead?.inputStream() ?: if (song.contentUri != android.net.Uri.EMPTY) {
                context.contentResolver.openInputStream(song.contentUri)
            } else null

            inputStream?.use { stream ->
                val buffer = ByteArray(32768)
                val readBytes = stream.read(buffer)

                if (readBytes >= 26 && buffer[0] == 'f'.code.toByte() && buffer[1] == 'L'.code.toByte() && buffer[2] == 'a'.code.toByte() && buffer[3] == 'C'.code.toByte()) {
                    val b20 = buffer[20].toInt() and 0xFF
                    val b21 = buffer[21].toInt() and 0xFF
                    val flacBits = (((b20 and 0x01) shl 4) or ((b21 and 0xF0) ushr 4)) + 1
                    if (flacBits in 8..32) {
                        bitDepth = flacBits
                    }
                } else if (readBytes >= 36 && buffer[0] == 'R'.code.toByte() && buffer[1] == 'I'.code.toByte() && buffer[2] == 'F'.code.toByte() && buffer[3] == 'F'.code.toByte()) {
                    val wavBits = (buffer[34].toInt() and 0xFF) or ((buffer[35].toInt() and 0xFF) shl 8)
                    if (wavBits in 8..32) {
                        bitDepth = wavBits
                    }
                } else if (readBytes > 40 && (format == "M4A" || format == "ALAC")) {
                    // Check for 'alac' atom in MP4 / M4A container
                    for (i in 0 until readBytes - 36) {
                        if (buffer[i] == 'a'.code.toByte() && buffer[i + 1] == 'l'.code.toByte() &&
                            buffer[i + 2] == 'a'.code.toByte() && buffer[i + 3] == 'c'.code.toByte()
                        ) {
                            format = "ALAC"
                            val bd = buffer[i + 17].toInt() and 0xFF
                            if (bd in 8..32) {
                                bitDepth = bd
                            }
                            if (sampleRate == null || sampleRate == 0) {
                                val sr = ((buffer[i + 32].toInt() and 0xFF) shl 24) or
                                        ((buffer[i + 33].toInt() and 0xFF) shl 16) or
                                        ((buffer[i + 34].toInt() and 0xFF) shl 8) or
                                        (buffer[i + 35].toInt() and 0xFF)
                                if (sr in 8000..384000) {
                                    sampleRate = sr
                                }
                            }
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (bitDepth == null) {
            bitDepth = when (format) {
                "FLAC", "WAV", "ALAC", "AIFF" -> 16
                "DSF", "DFF", "DSD" -> 1
                else -> 16
            }
        }

        val result = AudioStreamProps(
            format = format,
            sampleRateHz = sampleRate,
            bitrateKbps = bitrate,
            bitDepth = bitDepth
        )

        streamPropsCache.put(song.id, result)

        return result
    }
}
