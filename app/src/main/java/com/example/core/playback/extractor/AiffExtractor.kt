package com.example.core.playback.extractor

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import java.io.EOFException
import kotlin.math.pow

@OptIn(UnstableApi::class)
class AiffExtractor : Extractor {

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var bitsPerSample: Int = 16
    private var bytesPerFrame: Int = 4
    private var pcmEncoding: Int = C.ENCODING_PCM_16BIT

    private var dataStartPosition: Long = 0L
    private var dataEndPosition: Long = Long.MAX_VALUE
    private var totalFrames: Long = 0L
    private var durationUs: Long = C.TIME_UNSET

    private var headerParsed: Boolean = false
    private var currentFramePosition: Long = 0L

    private val scratch = ParsableByteArray(64)
    private val buffer = ByteArray(READ_BUFFER_SIZE)

    companion object {
        private const val READ_BUFFER_SIZE = 8192
        private const val FOURCC_FORM = 0x464F524D // 'FORM'
        private const val FOURCC_AIFF = 0x41494646 // 'AIFF'
        private const val FOURCC_AIFC = 0x41494643 // 'AIFC'
        private const val FOURCC_COMM = 0x434F4D4D // 'COMM'
        private const val FOURCC_SSND = 0x53534E44 // 'SSND'
    }

    override fun sniff(input: ExtractorInput): Boolean {
        scratch.reset(12)
        if (!input.peekFully(scratch.data, 0, 12, true)) {
            return false
        }
        val form = scratch.readInt()
        if (form != FOURCC_FORM) return false
        scratch.skipBytes(4) // Skip Form size
        val format = scratch.readInt()
        return format == FOURCC_AIFF || format == FOURCC_AIFC
    }

    override fun init(output: ExtractorOutput) {
        this.extractorOutput = output
        this.trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!headerParsed) {
            if (!parseHeader(input)) {
                return Extractor.RESULT_END_OF_INPUT
            }
            headerParsed = true
        }

        if (input.position < dataStartPosition) {
            val toSkip = (dataStartPosition - input.position).toInt()
            input.skipFully(toSkip)
        }

        if (input.position >= dataEndPosition) {
            return Extractor.RESULT_END_OF_INPUT
        }

        val bytesRemaining = (dataEndPosition - input.position).coerceAtMost(buffer.size.toLong()).toInt()
        val bytesToRead = (bytesRemaining / bytesPerFrame) * bytesPerFrame
        if (bytesToRead <= 0) {
            return Extractor.RESULT_END_OF_INPUT
        }

        val bytesRead = input.read(buffer, 0, bytesToRead)
        if (bytesRead == C.RESULT_END_OF_INPUT || bytesRead <= 0) {
            return Extractor.RESULT_END_OF_INPUT
        }

        // AIFF PCM samples are Big-Endian. Convert in-place to Little-Endian for universal Media3 / Oboe compatibility
        if (bitsPerSample == 16) {
            val validBytes = bytesRead - (bytesRead % 2)
            for (i in 0 until validBytes step 2) {
                val b0 = buffer[i]
                buffer[i] = buffer[i + 1]
                buffer[i + 1] = b0
            }
        } else if (bitsPerSample == 24) {
            val validBytes = bytesRead - (bytesRead % 3)
            for (i in 0 until validBytes step 3) {
                val b0 = buffer[i]
                val b2 = buffer[i + 2]
                buffer[i] = b2
                buffer[i + 2] = b0
            }
        } else if (bitsPerSample == 32) {
            val validBytes = bytesRead - (bytesRead % 4)
            for (i in 0 until validBytes step 4) {
                val b0 = buffer[i]
                val b1 = buffer[i + 1]
                buffer[i] = buffer[i + 3]
                buffer[i + 1] = buffer[i + 2]
                buffer[i + 2] = b1
                buffer[i + 3] = b0
            }
        }

        val parsable = ParsableByteArray(buffer, bytesRead)
        trackOutput?.sampleData(parsable, bytesRead)

        val framesInThisRead = bytesRead / bytesPerFrame
        val timeUs = (currentFramePosition * C.MICROS_PER_SECOND) / sampleRate
        trackOutput?.sampleMetadata(
            timeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            bytesRead,
            0,
            null
        )
        currentFramePosition += framesInThisRead

        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        if (sampleRate > 0 && bytesPerFrame > 0) {
            val targetFrame = (timeUs * sampleRate) / C.MICROS_PER_SECOND
            currentFramePosition = targetFrame.coerceIn(0L, totalFrames)
        } else {
            currentFramePosition = 0L
        }
    }

    override fun release() {
        extractorOutput = null
        trackOutput = null
    }

    private fun parseHeader(input: ExtractorInput): Boolean {
        try {
            scratch.reset(12)
            input.readFully(scratch.data, 0, 12)
            val form = scratch.readInt()
            if (form != FOURCC_FORM) return false
            scratch.readInt()
            val format = scratch.readInt()
            if (format != FOURCC_AIFF && format != FOURCC_AIFC) return false

            var foundComm = false
            var foundSsnd = false

            while (!foundComm || !foundSsnd) {
                scratch.reset(8)
                if (!input.readFully(scratch.data, 0, 8, true)) break
                val chunkId = scratch.readInt()
                val chunkSize = scratch.readInt()

                when (chunkId) {
                    FOURCC_COMM -> {
                        scratch.reset(chunkSize)
                        input.readFully(scratch.data, 0, chunkSize)
                        channelCount = scratch.readShort().toInt() and 0xFFFF
                        totalFrames = scratch.readInt().toLong() and 0xFFFFFFFFL
                        bitsPerSample = scratch.readShort().toInt() and 0xFFFF

                        // Read 80-bit IEEE 754 extended precision float for sample rate
                        val expRaw = scratch.readShort().toInt() and 0xFFFF
                        val mantissaHi = scratch.readInt().toLong() and 0xFFFFFFFFL
                        val mantissaLo = scratch.readInt().toLong() and 0xFFFFFFFFL
                        sampleRate = decodeIeeeExtended(expRaw, mantissaHi, mantissaLo)

                        pcmEncoding = when (bitsPerSample) {
                            16 -> C.ENCODING_PCM_16BIT
                            24 -> C.ENCODING_PCM_24BIT
                            32 -> C.ENCODING_PCM_32BIT
                            else -> C.ENCODING_PCM_16BIT
                        }
                        bytesPerFrame = channelCount * (bitsPerSample / 8)

                        if (sampleRate > 0) {
                            durationUs = (totalFrames * C.MICROS_PER_SECOND) / sampleRate
                        }
                        foundComm = true
                    }
                    FOURCC_SSND -> {
                        scratch.reset(8)
                        input.readFully(scratch.data, 0, 8)
                        val offset = scratch.readInt()
                        scratch.readInt()
                        val dataLength = chunkSize - 8 - offset
                        dataStartPosition = input.position + offset
                        dataEndPosition = dataStartPosition + dataLength
                        foundSsnd = true
                        break
                    }
                    else -> {
                        val paddedSize = if (chunkSize % 2 != 0) chunkSize + 1 else chunkSize
                        input.skipFully(paddedSize)
                    }
                }
            }

            if (!foundComm || !foundSsnd) return false

            val formatBuilder = Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setPcmEncoding(pcmEncoding)

            trackOutput?.format(formatBuilder.build())

            val seekMap = if (durationUs != C.TIME_UNSET && bytesPerFrame > 0 && sampleRate > 0) {
                AiffSeekMap(durationUs, dataStartPosition, dataEndPosition, bytesPerFrame, sampleRate)
            } else {
                SeekMap.Unseekable(durationUs)
            }
            extractorOutput?.seekMap(seekMap)
            return true
        } catch (_: EOFException) {
            return false
        }
    }

    private fun decodeIeeeExtended(exp: Int, mantissaHi: Long, mantissaLo: Long): Int {
        val exponent = exp and 0x7FFF
        if (exponent == 0 && mantissaHi == 0L && mantissaLo == 0L) return 44100
        val shift = exponent - 16383 - 31
        val rate = (mantissaHi.toDouble() * 2.0.pow(shift.toDouble())).toInt()
        return if (rate in 8000..384000) rate else 44100
    }

    private class AiffSeekMap(
        private val durationUs: Long,
        private val dataStart: Long,
        private val dataEnd: Long,
        private val bytesPerFrame: Int,
        private val sampleRate: Int
    ) : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val frame = (timeUs.coerceIn(0, durationUs) * sampleRate) / C.MICROS_PER_SECOND
            val byteOffset = frame * bytesPerFrame
            val position = (dataStart + byteOffset).coerceAtMost(dataEnd - bytesPerFrame)
            val seekPoint = androidx.media3.extractor.SeekPoint(timeUs, position)
            return SeekMap.SeekPoints(seekPoint)
        }
    }
}
