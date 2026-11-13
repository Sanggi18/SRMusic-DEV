package com.example.ui.common.components

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.kyant.taglib.TagLib
import com.example.core.data.mediastore.tags.TaglibHelper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.common.ArtworkPlaceholderType
import androidx.collection.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val artworkBitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val artworkImageBitmapCache = LruCache<String, ImageBitmap>(256)

private val inFlightLock = Any()
private val inFlightArtworkLoads = HashMap<String, CompletableDeferred<Bitmap?>>()

private val fallbackSizes = intArrayOf(1024, 1000, 800, 512, 256)

private fun findCachedImageBitmap(cacheKey: String?, srcKey: String?, reqPx: Int): ImageBitmap? {
    if (cacheKey != null) {
        artworkImageBitmapCache.get(cacheKey)?.let { return it }
        artworkBitmapCache.get(cacheKey)?.let { bmp ->
            val img = bmp.asImageBitmap()
            artworkImageBitmapCache.put(cacheKey, img)
            return img
        }
    }
    if (srcKey != null) {
        for (size in fallbackSizes) {
            if (size >= reqPx) {
                val altKey = "${srcKey}_$size"
                artworkImageBitmapCache.get(altKey)?.let { return it }
                artworkBitmapCache.get(altKey)?.let { bmp ->
                    val img = bmp.asImageBitmap()
                    artworkImageBitmapCache.put(altKey, img)
                    return img
                }
            }
        }
    }
    return null
}

private val missingArtworkKeys = java.util.Collections.synchronizedSet(
    object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            if (size >= 1000) {
                val it = iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            return super.add(element)
        }
    }
)

private val updatedEmbeddedArtworkKeys = java.util.Collections.synchronizedSet(HashSet<String>())

private val artworkInvalidationCounter = androidx.compose.runtime.mutableIntStateOf(0)

fun invalidateArtworkCache(dataPath: String?, contentUri: Uri?, artworkUri: Uri?) {
    val prefixes = listOfNotNull(
        dataPath?.takeIf { it.isNotBlank() },
        contentUri?.takeIf { it != Uri.EMPTY }?.toString(),
        artworkUri?.takeIf { it != Uri.EMPTY }?.toString()
    )
    if (prefixes.isEmpty()) return

    prefixes.forEach { prefix ->
        missingArtworkKeys.remove(prefix)
        updatedEmbeddedArtworkKeys.add(prefix)
    }

    val bmpKeys = artworkBitmapCache.snapshot().keys
    for (k in bmpKeys) {
        if (prefixes.any { k == it || k.startsWith("${it}_") }) {
            artworkBitmapCache.remove(k)
        }
    }

    val imgKeys = artworkImageBitmapCache.snapshot().keys
    for (k in imgKeys) {
        if (prefixes.any { k == it || k.startsWith("${it}_") }) {
            artworkImageBitmapCache.remove(k)
        }
    }

    artworkInvalidationCounter.intValue += 1
}

@Composable
fun AlbumArtwork(
    artworkUri: Uri? = null,
    fallbackUri: Uri? = null,
    dataPath: String? = null,
    imageFile: File? = null,
    fallbackText: String = "",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    targetSizePx: Int? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    placeholderType: ArtworkPlaceholderType = ArtworkPlaceholderType.MUSIC,
    topCrop: Boolean = false,
    onLoadingChange: ((Boolean) -> Unit)? = null,
    isPinned: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val invalidationTick = artworkInvalidationCounter.intValue

    val px = remember(density, size, targetSizePx) {
        (targetSizePx ?: with(density) { size.roundToPx() }).coerceIn(1, 1024)
    }

    val placeholderIcon: ImageVector = remember(placeholderType) {
        when (placeholderType) {
            ArtworkPlaceholderType.MUSIC -> Icons.Rounded.MusicNote
            ArtworkPlaceholderType.ARTIST -> Icons.Rounded.Person
            ArtworkPlaceholderType.ALBUM -> Icons.Rounded.Album
            ArtworkPlaceholderType.FOLDER -> Icons.Rounded.Folder
            ArtworkPlaceholderType.AUDIO_FILE -> Icons.Rounded.AudioFile
        }
    }

    val srcKey = remember(artworkUri, fallbackUri, dataPath, imageFile?.absolutePath) {
        dataPath ?: artworkUri?.toString() ?: fallbackUri?.toString() ?: imageFile?.absolutePath
    }

    val cacheKey = remember(srcKey, px) {
        if (srcKey != null) "${srcKey}_$px" else null
    }

    val cachedBitmap = remember(cacheKey, srcKey, px, invalidationTick) {
        findCachedImageBitmap(cacheKey, srcKey, px)
    }

    val bitmap by produceState<ImageBitmap?>(
        initialValue = cachedBitmap,
        artworkUri, fallbackUri, dataPath, imageFile, px, cacheKey, invalidationTick
    ) {
        val memImg = findCachedImageBitmap(cacheKey, srcKey, px)
        if (memImg != null) {
            value = memImg
            return@produceState
        }

        if (srcKey == null || missingArtworkKeys.contains(srcKey)) {
            value = null
            return@produceState
        }

        if (artworkUri == null && fallbackUri == null && dataPath.isNullOrBlank() && (imageFile == null || !imageFile.exists())) {
            value = null
            return@produceState
        }

        onLoadingChange?.invoke(true)

        val loaded = withContext(Dispatchers.IO) {
            val requestKey = cacheKey ?: srcKey
            val (deferred, isLeader) = synchronized(inFlightLock) {
                val existing = inFlightArtworkLoads[requestKey]
                if (existing != null) {
                    existing to false
                } else {
                    val created = CompletableDeferred<Bitmap?>()
                    inFlightArtworkLoads[requestKey] = created
                    created to true
                }
            }

            if (!isLeader) {
                return@withContext runCatching { deferred.await() }.getOrNull()
            }

            try {
                val decoded = runCatching {
                    var resultBmp: Bitmap? = null

                    val primaryUri = artworkUri ?: fallbackUri
                    val preferEmbedded = updatedEmbeddedArtworkKeys.contains(srcKey) ||
                        (dataPath != null && updatedEmbeddedArtworkKeys.contains(dataPath)) ||
                        (primaryUri != null && updatedEmbeddedArtworkKeys.contains(primaryUri.toString()))

                    if (preferEmbedded && (!dataPath.isNullOrBlank() || primaryUri != null)) {
                        resultBmp = extractMasterEmbeddedArtwork(context, dataPath, primaryUri, px)
                    }

                    if (resultBmp == null && primaryUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        resultBmp = runCatching {
                            context.contentResolver.loadThumbnail(primaryUri, Size(px, px), null)
                        }.getOrNull()
                    }

                    if (resultBmp == null && primaryUri != null) {
                        resultBmp = decodeSampledStream(context.contentResolver, primaryUri, px)
                    }

                    if (resultBmp == null && (!dataPath.isNullOrBlank() || primaryUri != null)) {
                        resultBmp = extractMasterEmbeddedArtwork(context, dataPath, primaryUri, px)
                    }

                    if (resultBmp == null && imageFile != null && imageFile.exists()) {
                        resultBmp = decodeSampledFile(imageFile, px)
                    }

                    if (resultBmp != null && cacheKey != null) {
                        artworkBitmapCache.put(cacheKey, resultBmp)
                        artworkImageBitmapCache.put(cacheKey, resultBmp.asImageBitmap())
                    } else if (resultBmp == null) {
                        missingArtworkKeys.add(srcKey)
                    }

                    resultBmp
                }.getOrNull()

                deferred.complete(decoded)
                decoded
            } catch (ce: CancellationException) {
                deferred.cancel(ce)
                throw ce
            } catch (t: Throwable) {
                deferred.complete(null)
                null
            } finally {
                synchronized(inFlightLock) {
                    if (inFlightArtworkLoads[requestKey] === deferred) {
                        inFlightArtworkLoads.remove(requestKey)
                    }
                }
            }
        }

        value = if (cacheKey != null) {
            artworkImageBitmapCache.get(cacheKey) ?: loaded?.asImageBitmap()
        } else {
            loaded?.asImageBitmap()
        }
        onLoadingChange?.invoke(false)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                alignment = if (topCrop) Alignment.TopCenter else Alignment.Center,
                contentScale = ContentScale.Crop,
            )
        } else if (fallbackText.isNotBlank()) {
            Text(
                text = fallbackText.trim().first().uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        } else {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

private fun calcSampleSize(w: Int, h: Int, reqPx: Int): Int {
    var s = 1
    val minSide = minOf(w, h)
    while (s * 2 <= minSide / reqPx) s *= 2
    return s.coerceAtLeast(1)
}

private fun decodeSampledStream(resolver: ContentResolver, uri: Uri, reqPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val sample = calcSampleSize(bounds.outWidth, bounds.outHeight, reqPx)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.HARDWARE
        }

        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        bmp ?: resolver.openInputStream(uri)?.use {
            val fallbackOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeStream(it, null, fallbackOpts)
        }
    } catch (_: Exception) { null }
}

private fun decodeSampledFile(file: File, reqPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val sample = calcSampleSize(bounds.outWidth, bounds.outHeight, reqPx)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.HARDWARE
        }

        BitmapFactory.decodeFile(file.absolutePath, opts) ?: BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (_: Exception) { null }
}

private fun extractMasterEmbeddedArtwork(
    context: android.content.Context,
    dataPath: String?,
    uri: Uri?,
    reqPx: Int
): Bitmap? {
    var rawBytes: ByteArray? = null

    if (!dataPath.isNullOrBlank()) {
        val file = File(dataPath)
        if (file.exists() && file.canRead()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(dataPath)
                rawBytes = retriever.embeddedPicture
                retriever.release()
            } catch (_: Exception) {}

            if (rawBytes == null || rawBytes.isEmpty()) {
                rawBytes = TaglibHelper.getArtworkBytes(file)
            }
        }
    }

    if ((rawBytes == null || rawBytes.isEmpty()) && uri != null) {
        try {
            val retriever = MediaMetadataRetriever()
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    rawBytes = retriever.embeddedPicture
                }
            } else {
                retriever.setDataSource(uri.path ?: uri.toString())
                rawBytes = retriever.embeddedPicture
            }
            retriever.release()
        } catch (_: Exception) {}

        if (rawBytes == null || rawBytes.isEmpty()) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val pictures = TagLib.getPictures(pfd.dup().detachFd())
                    rawBytes = pictures.firstOrNull()?.data
                }
            } catch (_: Exception) {}
        }
    }

    if (rawBytes != null && rawBytes.isNotEmpty()) {
        return decodeSampledByteArray(rawBytes, reqPx)
    }

    return null
}

private fun decodeSampledByteArray(data: ByteArray, reqPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)

        val sample = calcSampleSize(bounds.outWidth, bounds.outHeight, reqPx)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.HARDWARE
        }

        BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: BitmapFactory.decodeByteArray(
            data, 0, data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (_: Exception) { null }
}
