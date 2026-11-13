package com.example.ui.player.core

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt

object FastAudioWaveformExtractor {
    private const val TAG = "FastWaveformExtractor"
    private const val BAR_COUNT = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFlows = HashMap<Long, MutableStateFlow<FloatArray?>>()
    private val lock = Any()
    private val flatLineWaveform = FloatArray(BAR_COUNT) { 0f }

    @Volatile
    private var currentExtractionJob: Job? = null

    @Volatile
    private var currentProcessingSongId: Long? = null

    init {
        try {
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "waveform_fast_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    private fun isDsdSong(song: Song): Boolean {
        val path = song.dataPath?.lowercase().orEmpty()
        val name = song.displayName?.lowercase().orEmpty()
        return path.endsWith(".dsf") || path.endsWith(".dff") || name.endsWith(".dsf") || name.endsWith(".dff")
    }

    private fun isAlacSong(context: Context, song: Song): Boolean {
        val path = song.dataPath?.lowercase().orEmpty()
        val name = song.displayName?.lowercase().orEmpty()
        if (path.endsWith(".alac") || path.endsWith(".caf") || name.endsWith(".alac") || name.endsWith(".caf")) {
            return true
        }
        if (path.endsWith(".m4a") || path.endsWith(".mp4") || name.endsWith(".m4a") || name.endsWith(".mp4")) {
            try {
                val inputStream = song.dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }?.inputStream()
                    ?: if (song.contentUri != Uri.EMPTY) context.contentResolver.openInputStream(song.contentUri) else null

                inputStream?.use { stream ->
                    val buffer = ByteArray(65536)
                    val read = stream.read(buffer)
                    if (read > 40) {
                        for (i in 0 until read - 4) {
                            if (buffer[i] == 'a'.code.toByte() && buffer[i + 1] == 'l'.code.toByte() &&
                                buffer[i + 2] == 'a'.code.toByte() && buffer[i + 3] == 'c'.code.toByte()
                            ) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun extractDsfRealAmplitudes(song: Song, barCount: Int): FloatArray? {
        val path = song.dataPath ?: return null
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() < 128) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magicBytes = ByteArray(4)
                raf.readFully(magicBytes)
                val magic = String(magicBytes, Charsets.US_ASCII)
                if (magic != "DSD ") return null

                raf.seek(28)
                raf.readFully(magicBytes)
                val fmtMagic = String(magicBytes, Charsets.US_ASCII)
                if (fmtMagic != "fmt ") return null

                raf.seek(80)
                raf.readFully(magicBytes)
                val dataMagic = String(magicBytes, Charsets.US_ASCII)
                val dataStartPos: Long
                val dataEndPos: Long
                if (dataMagic == "data") {
                    val sizeBuf = ByteArray(8)
                    raf.readFully(sizeBuf)
                    val dataChunkSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).long
                    dataStartPos = 92L
                    dataEndPos = (dataStartPos + (dataChunkSize - 12L)).coerceAtMost(file.length())
                } else {
                    dataStartPos = 92L
                    dataEndPos = file.length()
                }

                val totalDataBytes = dataEndPos - dataStartPos
                if (totalDataBytes <= 0) return null

                val rmsValues = FloatArray(barCount)
                val segmentBytes = totalDataBytes / barCount
                val sampleBufferSize = 2048
                val buffer = ByteArray(sampleBufferSize)

                for (i in 0 until barCount) {
                    val targetPos = dataStartPos + (i * segmentBytes)
                    val seekPos = targetPos.coerceIn(dataStartPos, (dataEndPos - sampleBufferSize).coerceAtLeast(dataStartPos))
                    raf.seek(seekPos)
                    val bytesRead = raf.read(buffer, 0, sampleBufferSize)
                    if (bytesRead > 0) {
                        var densitySum = 0.0
                        for (bIdx in 0 until bytesRead) {
                            val onesCount = Integer.bitCount(buffer[bIdx].toInt() and 0xFF)
                            val dev = (onesCount / 8.0) - 0.5
                            densitySum += (dev * dev)
                        }
                        val rms = sqrt(densitySum / bytesRead).toFloat() * 2.0f
                        rmsValues[i] = rms
                    } else {
                        rmsValues[i] = 0f
                    }
                }

                var maxVal = 0.001f
                for (v in rmsValues) {
                    if (v > maxVal) maxVal = v
                }

                val normalized = FloatArray(barCount)
                for (i in 0 until barCount) {
                    val prev = if (i > 0) rmsValues[i - 1] else rmsValues[i]
                    val curr = rmsValues[i]
                    val next = if (i < barCount - 1) rmsValues[i + 1] else rmsValues[i]
                    val smoothed = (0.2f * prev + 0.6f * curr + 0.2f * next)
                    val ratio = (smoothed / maxVal).coerceIn(0.05f, 1.0f)
                    normalized[i] = ratio
                }
                normalized
            }
        } catch (e: Exception) {
            Log.w(TAG, "DSF extraction failed for ${song.title}: ${e.message}")
            null
        }
    }

    fun getWaveformFlow(context: Context, song: Song?): StateFlow<FloatArray?> {
        val songId = song?.id ?: return MutableStateFlow(null)

        synchronized(lock) {
            if (isAlacSong(context, song)) {
                return MutableStateFlow(flatLineWaveform)
            }

            if (currentProcessingSongId != songId) {
                currentExtractionJob?.cancel()
                currentExtractionJob = null
                activeFlows.clear()
                currentProcessingSongId = songId
            }

            activeFlows[songId]?.let {
                return it.asStateFlow()
            }

            val newFlow = MutableStateFlow<FloatArray?>(null)
            activeFlows[songId] = newFlow

            currentExtractionJob = scope.launch {
                try {
                    val waveform = if (isDsdSong(song)) {
                        extractDsfRealAmplitudes(song, BAR_COUNT)
                    } else {
                        extractRealAmplitudes(context, song, BAR_COUNT)
                    }

                    if (waveform != null && waveform.size == BAR_COUNT) {
                        newFlow.value = waveform
                    } else {
                        newFlow.value = flatLineWaveform
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Waveform extraction error for songId=$songId", e)
                    newFlow.value = flatLineWaveform
                } finally {
                    synchronized(lock) {
                        if (activeFlows[songId] === newFlow) {
                            activeFlows.remove(songId)
                        }
                        if (currentExtractionJob === coroutineContext[Job]) {
                            currentExtractionJob = null
                        }
                    }
                }
            }

            return newFlow.asStateFlow()
        }
    }

    private suspend fun extractRealAmplitudes(
        context: Context,
        song: Song,
        barCount: Int
    ): FloatArray? = withContext(Dispatchers.IO) {
        val audioUri = getAudioUri(song)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            coroutineContext.ensureActive()
            extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, audioUri, null)
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                val dp = song.dataPath
                if (!dp.isNullOrBlank()) {
                    extractor.setDataSource(dp)
                } else {
                    throw e
                }
            }

            coroutineContext.ensureActive()
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var mime: String? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    if (m.contains("alac", ignoreCase = true)) {
                        return@withContext null
                    }
                    audioTrackIndex = i
                    audioFormat = format
                    mime = m
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null || mime == null) {
                return@withContext null
            }

            coroutineContext.ensureActive()
            extractor.selectTrack(audioTrackIndex)

            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                song.duration * 1000L
            }

            if (durationUs <= 0L) {
                return@withContext null
            }

            try {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()
            } catch (e: Exception) {
                Log.w(TAG, "Codec initialization failed for mime=$mime: ${e.message}")
                return@withContext null
            }

            val pcmEncoding = if (audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val rmsValues = FloatArray(barCount)
            val segmentUs = durationUs / barCount
            val bufferInfo = MediaCodec.BufferInfo()
            var totalSampleCount = 0

            for (barIdx in 0 until barCount) {
                coroutineContext.ensureActive()
                val targetUs = barIdx * segmentUs
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var samplesSumSquares = 0.0
                var sampleCount = 0
                var iterations = 0
                val maxIterations = 8
                var sawOutput = false

                while (iterations < maxIterations) {
                    coroutineContext.ensureActive()
                    iterations++
                    val inputBufIdx = codec.dequeueInputBuffer(2000L)
                    if (inputBufIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIdx)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                codec.queueInputBuffer(inputBufIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufIdx = codec.dequeueOutputBuffer(bufferInfo, 2000L)
                    if (outputBufIdx >= 0) {
                        sawOutput = true
                        val outputBuf = codec.getOutputBuffer(outputBufIdx)
                        if (outputBuf != null && bufferInfo.size > 0) {
                            outputBuf.position(bufferInfo.offset)
                            outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuf.order(ByteOrder.LITTLE_ENDIAN)

                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floatBuffer = outputBuf.asFloatBuffer()
                                    val limit = floatBuffer.remaining()
                                    val step = max(1, limit / 128)
                                    var pos = 0
                                    while (pos < limit) {
                                        val sample = floatBuffer.get(pos)
                                        samplesSumSquares += (sample * sample)
                                        sampleCount++
                                        totalSampleCount++
                                        pos += step
                                    }
                                }
                                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                    val remaining = outputBuf.remaining()
                                    val step = max(3, (remaining / 128) / 3 * 3)
                                    var pos = outputBuf.position()
                                    val end = outputBuf.limit() - 2
                                    while (pos < end) {
                                        val b0 = outputBuf.get(pos).toInt() and 0xFF
                                        val b1 = outputBuf.get(pos + 1).toInt() and 0xFF
                                        val b2 = outputBuf.get(pos + 2).toInt()
                                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                                        val sample = sample24.toFloat() / 8388608.0f
                                        samplesSumSquares += (sample * sample)
                                        sampleCount++
                                        totalSampleCount++
                                        pos += step
                                    }
                                }
                                else -> {
                                    val shortBuffer = outputBuf.asShortBuffer()
                                    val limit = shortBuffer.remaining()
                                    val step = max(1, limit / 128)
                                    var pos = 0
                                    while (pos < limit) {
                                        val sample = shortBuffer.get(pos).toFloat() / 32768.0f
                                        samplesSumSquares += (sample * sample)
                                        sampleCount++
                                        totalSampleCount++
                                        pos += step
                                    }
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outputBufIdx, false)
                        if (sampleCount >= 64) {
                            break
                        }
                    } else if (sawOutput && outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                }

                val rms = if (sampleCount > 0) {
                    sqrt(samplesSumSquares / sampleCount).toFloat()
                } else {
                    0f
                }
                rmsValues[barIdx] = rms
            }

            if (totalSampleCount == 0) {
                return@withContext null
            }

            var maxRms = 0.001f
            for (v in rmsValues) {
                if (v > maxRms) maxRms = v
            }

            val normalized = FloatArray(barCount)
            for (i in 0 until barCount) {
                val prev = if (i > 0) rmsValues[i - 1] else rmsValues[i]
                val curr = rmsValues[i]
                val next = if (i < barCount - 1) rmsValues[i + 1] else rmsValues[i]
                val smoothed = (0.2f * prev + 0.6f * curr + 0.2f * next)
                val ratio = (smoothed / maxRms).coerceIn(0.04f, 1.0f)
                normalized[i] = ratio
            }

            normalized
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Audio decode error for ${song.title}: ${e.message}")
            null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            codec = null
            try {
                extractor?.release()
            } catch (_: Exception) {}
            extractor = null
        }
    }

    private fun getAudioUri(song: Song): Uri {
        if (song.contentUri != Uri.EMPTY) {
            return song.contentUri
        }
        return if (song.id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        } else {
            Uri.parse(song.dataPath)
        }
    }
}
