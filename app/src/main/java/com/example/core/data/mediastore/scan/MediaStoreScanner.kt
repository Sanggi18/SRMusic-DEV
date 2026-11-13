package com.example.core.data.mediastore.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.core.data.database.song.SongEntity
import com.example.core.data.mediastore.tags.GenreUtils
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {
    suspend fun scanMediaStore(
        options: LibraryScanOptions,
        onProgress: ((count: Int) -> Unit)? = null
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val collection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val projection = projectionList.toTypedArray()

        val minDurationMs = if (options.filterShortAudio) {
            (options.minDurationSeconds * 1000L).coerceAtLeast(1000L)
        } else {
            1000L
        }

        val audioFilter = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
            "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR " +
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE '%.m4a' OR " +
            "${MediaStore.Audio.Media.DATA} LIKE '%.m4a')"

        val selection = if (options.filterShortAudio) {
            "($audioFilter) AND (${MediaStore.Audio.Media.DURATION} >= ? OR ${MediaStore.Audio.Media.DURATION} <= 0 OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        } else {
            audioFilter
        }

        val selectionArgs = if (options.filterShortAudio) arrayOf(minDurationMs.toString()) else null
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

        val items = mutableListOf<SongEntity>()

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(displayNameCol)
                    val lowerName = displayName?.lowercase().orEmpty()
                    if (
                        lowerName.isBlank() ||
                        lowerName.startsWith(".") ||
                        lowerName.startsWith("pending-") ||
                        lowerName.contains(".trashed") ||
                        lowerName.contains(".pending")
                    ) continue

                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    if (!dataPath.isNullOrBlank()) {
                        val diskFile = File(dataPath)
                        if (!diskFile.exists() || !diskFile.isFile || diskFile.length() <= 0L) continue
                    }

                    val relativePath = resolveRelativePath(cursor, relativePathCol, dataPath)
                    val filterDataPath = dataPath.orEmpty()
                    val filterRelativePath = relativePath.orEmpty()

                    if (ScanPathFilter.isPathBlacklisted(filterDataPath, filterRelativePath, options.blacklistedFolders)) continue
                    if (!ScanPathFilter.isPathIncluded(filterDataPath, filterRelativePath, options.includedFolders)) continue

                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    var duration = cursor.getLong(durationCol)
                    if (duration <= 0) {
                        duration = extractDuration(context, contentUri, dataPath)
                    } else if (duration > 86400000L) {
                        duration /= 1000L
                    }

                    if (options.filterShortAudio && duration in 1 until minDurationMs) continue

                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val rawAlbum = cursor.getString(albumCol)
                    val albumId = cursor.getLong(albumIdCol)

                    var title = rawTitle.takeUnless { it.isNullOrBlank() }
                        ?: displayName?.substringBeforeLast(".")
                        ?: "Unknown Track"

                    var artist = rawArtist.takeUnless { it.isNullOrBlank() || it == "<unknown>" }
                        ?: "Unknown Artist"

                    var album = rawAlbum.takeUnless { it.isNullOrBlank() || it == "<unknown>" }
                        ?: "Unknown Album"

                    var rawGenre = if (genreCol != -1 && !cursor.isNull(genreCol)) {
                        cursor.getString(genreCol)
                    } else {
                        null
                    }

                    var year = if (!cursor.isNull(yearCol)) cursor.getInt(yearCol) else null
                    var trackNumber = if (!cursor.isNull(trackCol)) cursor.getInt(trackCol) else null

                    if (title == "Unknown Track" || artist == "Unknown Artist" || album == "Unknown Album" || duration <= 0) {
                        try {
                            val file = dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
                            val pfd = if (file != null) {
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            } else {
                                context.contentResolver.openFileDescriptor(contentUri, "r")
                            }
                            pfd?.use { descriptor ->
                                if (duration <= 0) {
                                    val props = TagLib.getAudioProperties(descriptor.dup().detachFd())
                                    if (props != null && props.length > 0) {
                                        duration = props.length.toLong()
                                    }
                                }
                                val meta = TagLib.getMetadata(descriptor.dup().detachFd())
                                meta?.propertyMap?.let { map ->
                                    fun getVal(vararg keys: String): String? {
                                        for (k in keys) {
                                            map[k]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                                            map.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                                        }
                                        return null
                                    }
                                    getVal("TITLE")?.let { title = it }
                                    getVal("ARTIST")?.let { artist = it }
                                    getVal("ALBUM")?.let { album = it }
                                    getVal("DATE", "YEAR")?.toIntOrNull()?.let { year = it }
                                    getVal("TRACKNUMBER")?.toIntOrNull()?.let { trackNumber = it }
                                    if (rawGenre.isNullOrBlank()) rawGenre = getVal("GENRE")
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    if (duration <= 0L) continue
                    if (options.filterShortAudio && duration < minDurationMs) continue

                    val genre = if (GenreUtils.isValidGenre(rawGenre)) {
                        GenreUtils.cleanGenre(rawGenre!!)
                    } else {
                        null
                    }

                    val artworkUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)

                    items.add(
                        SongEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            albumId = albumId,
                            contentUriString = contentUri.toString(),
                            artworkUriString = artworkUri.toString(),
                            trackNumber = trackNumber,
                            year = year,
                            size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else null,
                            displayName = displayName,
                            relativePath = relativePath,
                            dataPath = dataPath,
                            dateAdded = if (dateAddedCol != -1 && !cursor.isNull(dateAddedCol)) cursor.getLong(dateAddedCol) else null,
                            dateModified = if (dateModifiedCol != -1 && !cursor.isNull(dateModifiedCol)) cursor.getLong(dateModifiedCol) else null,
                            genre = genre
                        )
                    )

                    if (items.size <= 20 || items.size % 20 == 0) onProgress?.invoke(items.size)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreScanner", "Error scanning media store", e)
        }

        items
    }

    private fun extractDuration(context: Context, uri: Uri, dataPath: String?): Long {
        var dur = 0L
        try {
            val retriever = MediaMetadataRetriever()
            if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                retriever.setDataSource(dataPath)
            } else if (uri != Uri.EMPTY) {
                retriever.setDataSource(context, uri)
            }
            dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (_: Exception) {}

        if (dur <= 0L) {
            try {
                val file = dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
                val pfd = if (file != null) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else if (uri != Uri.EMPTY) {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } else null
                pfd?.use { descriptor ->
                    val props = TagLib.getAudioProperties(descriptor.dup().detachFd())
                    if (props != null && props.length > 0) {
                        dur = props.length.toLong()
                    }
                }
            } catch (_: Exception) {}
        }

        if (dur <= 0L) {
            try {
                val extractor = android.media.MediaExtractor()
                if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                    extractor.setDataSource(dataPath)
                } else if (uri != Uri.EMPTY) {
                    extractor.setDataSource(context, uri, null)
                }
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        val durationUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                        if (durationUs > 0) {
                            dur = durationUs / 1000L
                            break
                        }
                    }
                }
                extractor.release()
            } catch (_: Exception) {}
        }

        if (dur > 86400000L) {
            dur /= 1000L
        }

        return dur.coerceAtLeast(0L)
    }

    private fun resolveRelativePath(cursor: Cursor, relativePathCol: Int, dataPath: String?): String? {
        if (relativePathCol != -1) {
            val rp = cursor.getString(relativePathCol)
            if (!rp.isNullOrBlank()) return rp.trim().trimEnd('/')
        }

        if (dataPath.isNullOrBlank()) return null

        val directoryPath = dataPath.substringBeforeLast('/', "")
        if (directoryPath.isBlank()) return null

        return when {
            directoryPath.contains("/Music/") -> "Music/" + directoryPath.substringAfter("/Music/")
            directoryPath.contains("/sdcard/") -> directoryPath.substringAfter("/sdcard/")
            directoryPath.contains("/storage/emulated/0/") -> directoryPath.substringAfter("/storage/emulated/0/")
            else -> directoryPath.trimStart('/')
        }.takeIf { it.isNotBlank() }
    }
}
