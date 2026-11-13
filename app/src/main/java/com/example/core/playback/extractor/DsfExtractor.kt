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
import com.example.core.audio.dac.DsdEngine
import java.io.EOFException

@OptIn(UnstableApi::class)
class DsfExtractor : Extractor {

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null

    private var channelCount: Int = 2
    private var dsdSampleRate: Int = 5644800
    private var pcmSampleRate: Int = 88200
    private var sampleCount: Long = 0L
    private var blockSizePerChannel: Int = 4096
    private var durationUs: Long = C.TIME_UNSET

    private var dataStartPosition: Long = 0L
    private var dataEndPosition: Long = Long.MAX_VALUE
    private var headerParsed: Boolean = false
    private var currentPcmFrames: Long = 0L

    private var decimationFactor: Int = 64
    private val gainCompensation: Float = 1.9952623f // +6 dB for standard SACD headroom

    private var dsdBlockBuffer: ByteArray = ByteArray(8192)
    private var pcmOutputBytes: ByteArray = ByteArray(8192)

    private val scratch = ParsableByteArray(64)

    companion object {
        private const val FOURCC_DSD = 0x44534420 // 'DSD '
        private const val FOURCC_FMT = 0x666D7420 // 'fmt '
        private const val FOURCC_DATA = 0x64617461 // 'data'
    }

    override fun sniff(input: ExtractorInput): Boolean {
        scratch.reset(4)
        if (!input.peekFully(scratch.data, 0, 4, true)) {
            return false
        }
        val magic = scratch.readInt()
        return magic == FOURCC_DSD
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

        val totalBlockBytes = blockSizePerChannel * channelCount
        if (dsdBlockBuffer.size < totalBlockBytes) {
            dsdBlockBuffer = ByteArray(totalBlockBytes)
        }

        val bytesAvailable = dataEndPosition - input.position
        if (bytesAvailable < totalBlockBytes) {
            return Extractor.RESULT_END_OF_INPUT
        }

        // Read the entire DSF stereo block reliably
        try {
            input.readFully(dsdBlockBuffer, 0, totalBlockBytes)
        } catch (_: EOFException) {
            return Extractor.RESULT_END_OF_INPUT
        }

        val totalDsdBitsPerChannel = blockSizePerChannel * 8
        val outSamplesPerChannel = totalDsdBitsPerChannel / decimationFactor
        val totalOutPcmSamples = outSamplesPerChannel * channelCount
        val bytesNeeded = totalOutPcmSamples * 2 // 16-bit PCM = 2 bytes per sample

        if (pcmOutputBytes.size < bytesNeeded) {
            pcmOutputBytes = ByteArray(bytesNeeded)
        }

        val bytesWritten = if (DsdEngine.isAvailable()) {
            DsdEngine.nativeConvertDsdToPcm16(
                dsdBytes = dsdBlockBuffer,
                numBytes = totalBlockBytes,
                channels = channelCount,
                decimationFactor = decimationFactor,
                gain = gainCompensation,
                outPcmBytes = pcmOutputBytes
            )
        } else {
            0
        }

        if (bytesWritten > 0) {
            val parsable = ParsableByteArray(pcmOutputBytes, bytesWritten)
            trackOutput?.sampleData(parsable, bytesWritten)

            val timeUs = (currentPcmFrames * C.MICROS_PER_SECOND) / pcmSampleRate
            trackOutput?.sampleMetadata(
                timeUs,
                C.BUFFER_FLAG_KEY_FRAME,
                bytesWritten,
                0,
                null
            )
            val framesWritten = bytesWritten / (channelCount * 2)
            currentPcmFrames += framesWritten
        } else {
            val framesFallback = outSamplesPerChannel.toLong()
            currentPcmFrames += framesFallback
        }

        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        if (dsdSampleRate > 0 && pcmSampleRate > 0) {
            val totalDsdBitsPerChannel = blockSizePerChannel * 8
            val targetDsdSample = (timeUs * dsdSampleRate) / C.MICROS_PER_SECOND
            val blockIndex = (targetDsdSample / totalDsdBitsPerChannel).coerceAtLeast(0L)
            val samplesPerBlockPerChannel = totalDsdBitsPerChannel / decimationFactor
            currentPcmFrames = blockIndex * samplesPerBlockPerChannel
        } else {
            currentPcmFrames = 0L
        }
    }

    override fun release() {
        extractorOutput = null
        trackOutput = null
    }

    private fun parseHeader(input: ExtractorInput): Boolean {
        try {
            // 1. Read 'DSD ' chunk (28 bytes)
            scratch.reset(28)
            input.readFully(scratch.data, 0, 28)
            val dsdMagic = scratch.readInt()
            if (dsdMagic != FOURCC_DSD) return false
            scratch.readLittleEndianLong()
            scratch.readLittleEndianLong()
            scratch.readLittleEndianLong()

            // 2. Read 'fmt ' chunk (52 bytes)
            scratch.reset(52)
            input.readFully(scratch.data, 0, 52)
            val fmtMagic = scratch.readInt()
            if (fmtMagic != FOURCC_FMT) return false
            scratch.readLittleEndianLong()
            scratch.readLittleEndianInt()
            scratch.readLittleEndianInt()
            scratch.readLittleEndianInt()
            channelCount = scratch.readLittleEndianInt().coerceIn(1, 8)
            dsdSampleRate = scratch.readLittleEndianInt().coerceAtLeast(2822400)
            scratch.readLittleEndianInt()
            sampleCount = scratch.readLittleEndianLong()
            blockSizePerChannel = scratch.readLittleEndianInt().coerceIn(1024, 65536)

            // DSD128 (5.6448 MHz) -> factor 64 -> 88.2 kHz
            // DSD64  (2.8224 MHz) -> factor 32 -> 88.2 kHz
            decimationFactor = if (dsdSampleRate >= 5000000) 64 else 32
            pcmSampleRate = dsdSampleRate / decimationFactor

            if (dsdSampleRate > 0) {
                durationUs = (sampleCount * C.MICROS_PER_SECOND) / dsdSampleRate
            }

            // 3. Read 'data' chunk header (12 bytes: 4 bytes magic + 8 bytes size)
            scratch.reset(12)
            input.readFully(scratch.data, 0, 12)
            val dataMagic = scratch.readInt()
            if (dataMagic != FOURCC_DATA) return false
            val dataChunkSize = scratch.readLittleEndianLong()

            dataStartPosition = input.position
            dataEndPosition = dataStartPosition + (dataChunkSize - 12)

            val format = Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(channelCount)
                .setSampleRate(pcmSampleRate)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()

            trackOutput?.format(format)

            val seekMap = if (durationUs != C.TIME_UNSET && dsdSampleRate > 0) {
                DsfSeekMap(durationUs, dataStartPosition, dataEndPosition, blockSizePerChannel, channelCount, dsdSampleRate)
            } else {
                SeekMap.Unseekable(durationUs)
            }
            extractorOutput?.seekMap(seekMap)
            return true
        } catch (_: EOFException) {
            return false
        }
    }

    private class DsfSeekMap(
        private val durationUs: Long,
        private val dataStart: Long,
        private val dataEnd: Long,
        private val blockSizePerChannel: Int,
        private val channelCount: Int,
        private val dsdSampleRate: Int
    ) : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val totalBlockBytes = blockSizePerChannel * channelCount
            val bitsPerBlock = blockSizePerChannel * 8
            val targetSample = (timeUs.coerceIn(0, durationUs) * dsdSampleRate) / C.MICROS_PER_SECOND
            val blockIndex = targetSample / bitsPerBlock
            val byteOffset = blockIndex * totalBlockBytes
            val position = (dataStart + byteOffset).coerceAtMost(dataEnd - totalBlockBytes)
            val seekPoint = androidx.media3.extractor.SeekPoint(timeUs, position)
            return SeekMap.SeekPoints(seekPoint)
        }
    }
}
