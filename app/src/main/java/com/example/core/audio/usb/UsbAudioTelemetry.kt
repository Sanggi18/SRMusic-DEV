package com.example.core.audio.usb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UsbTransferTelemetry(
    val isStreaming: Boolean = false,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val queuedFrames: Long = 0L,
    val dropCount: Int = 0,
    val currentWaterlineMs: Int = 0,
    val minWaterlineMs: Int = 0,
    val targetWaterlineMs: Int = 200,
    val status: String = "Standby",
    val nativeWriteCalls: Long = 0L,
    val framesTransferred: Long = 0L,
    val lastWriteLatencyUs: Long = 0L,
    val lastFramesCount: Int = 0,
    val lastSamplesCount: Int = 0,
    val urbsInFlight: Int = 0
)

object UsbAudioTelemetry {
    private val _telemetry = MutableStateFlow(UsbTransferTelemetry())
    val telemetry: StateFlow<UsbTransferTelemetry> = _telemetry.asStateFlow()

    fun updateStreaming(isStreaming: Boolean, sampleRate: Int, bitDepth: Int, targetBufferFrames: Int = 256) {
        val targetMs = if (sampleRate > 0) {
            ((targetBufferFrames.toLong() * 1000L) / sampleRate).coerceAtLeast(10L).toInt()
        } else {
            50
        }
        _telemetry.update { cur ->
            cur.copy(
                isStreaming = isStreaming,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                targetWaterlineMs = targetMs,
                framesTransferred = if (isStreaming) 0L else cur.framesTransferred,
                nativeWriteCalls = if (isStreaming) 0L else cur.nativeWriteCalls,
                lastWriteLatencyUs = if (isStreaming) 0L else cur.lastWriteLatencyUs,
                lastFramesCount = if (isStreaming) 0 else cur.lastFramesCount,
                lastSamplesCount = if (isStreaming) 0 else cur.lastSamplesCount,
                urbsInFlight = if (isStreaming) 0 else cur.urbsInFlight,
                status = if (isStreaming) "Active" else "Standby"
            )
        }
    }

    fun updateWaterline(queuedFrames: Long, sampleRate: Int, dropCount: Int) {
        _telemetry.update { cur ->
            val currentMs = if (sampleRate > 0) ((queuedFrames * 1000L) / sampleRate).toInt() else 0
            val prevMin = cur.minWaterlineMs
            val newMin = if (currentMs > 0) {
                if (prevMin == 0) currentMs else minOf(prevMin, currentMs)
            } else {
                prevMin
            }

            val status = when {
                dropCount > cur.dropCount -> "Underrun"
                dropCount > 0 -> "Warning"
                cur.isStreaming -> "Active"
                else -> "Standby"
            }

            cur.copy(
                queuedFrames = queuedFrames,
                currentWaterlineMs = currentMs,
                minWaterlineMs = newMin,
                dropCount = dropCount,
                status = status
            )
        }
    }

    fun recordWrite(
        framesCount: Int,
        latencyUs: Long = 0L,
        inFlight: Int = 0,
        samplesCount: Int = 0,
        totalFramesWritten: Long = 0L,
        nativeWriteCalls: Long = 0L
    ) {
        _telemetry.update { cur ->
            val actualSamples = if (samplesCount > 0) samplesCount else framesCount * 2
            val frames = if (totalFramesWritten > 0L) totalFramesWritten else (cur.framesTransferred + framesCount)
            val calls = if (nativeWriteCalls > 0L) nativeWriteCalls else (cur.nativeWriteCalls + 1L)
            cur.copy(
                isStreaming = true,
                nativeWriteCalls = calls,
                framesTransferred = frames,
                lastWriteLatencyUs = latencyUs,
                lastFramesCount = framesCount,
                lastSamplesCount = actualSamples,
                urbsInFlight = inFlight,
                status = "Active"
            )
        }
    }

    fun onFlush() {
        _telemetry.update { cur ->
            cur.copy(
                framesTransferred = 0L,
                queuedFrames = 0L
            )
        }
    }

    fun reset() {
        _telemetry.value = UsbTransferTelemetry()
    }
}

object UsbSourceTrackFormat {
    private val _sourceFormatFlow = MutableStateFlow("")
    val sourceFormatFlow: StateFlow<String> = _sourceFormatFlow.asStateFlow()

    @Volatile
    var sourceBitDepth: Int = 0

    @Volatile
    var sourceSampleRate: Int = 0

    @Volatile
    var sourceFormat: String = ""

    fun update(bitDepth: Int, sampleRate: Int = 0, format: String? = null) {
        if (bitDepth in listOf(16, 24, 32)) {
            sourceBitDepth = bitDepth
        }
        if (sampleRate > 0) {
            sourceSampleRate = sampleRate
        }
        if (!format.isNullOrBlank()) {
            sourceFormat = format
            _sourceFormatFlow.value = format
        }
    }
}

