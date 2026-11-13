package com.example.core.audio.usb.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
internal object PcmUtils {
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT -> 4
        C.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    fun scalePcm(rawBytes: ByteArray, encoding: Int, volume: Float) {
        val clampedVol = volume.coerceIn(0f, 1f)
        if (clampedVol >= 0.9999f) return
        if (clampedVol <= 0.0001f) {
            java.util.Arrays.fill(rawBytes, 0.toByte())
            return
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val end = rawBytes.size - 1
                var i = 0
                while (i < end) {
                    val s = (rawBytes[i].toInt() and 0xFF or (rawBytes[i + 1].toInt() shl 8)).toShort()
                    val scaled = (s.toFloat() * clampedVol).roundToInt().coerceIn(-32768, 32767)
                    rawBytes[i] = (scaled and 0xFF).toByte()
                    rawBytes[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                    i += 2
                }
            }
            C.ENCODING_PCM_24BIT -> {
                val end = rawBytes.size - 2
                var i = 0
                val volD = clampedVol.toDouble()
                while (i < end) {
                    val b0 = rawBytes[i].toInt() and 0xFF
                    val b1 = rawBytes[i + 1].toInt() and 0xFF
                    val b2 = rawBytes[i + 2].toInt() and 0xFF
                    val unsigned = b0 or (b1 shl 8) or (b2 shl 16)
                    val sample = if (unsigned and 0x800000 != 0) unsigned or (-0x1000000) else unsigned
                    val scaled = (sample.toDouble() * volD).roundToLong().coerceIn(-8388608L, 8388607L).toInt()
                    rawBytes[i] = (scaled and 0xFF).toByte()
                    rawBytes[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                    rawBytes[i + 2] = ((scaled shr 16) and 0xFF).toByte()
                    i += 3
                }
            }
            C.ENCODING_PCM_32BIT -> {
                val end = rawBytes.size - 3
                var i = 0
                val volD = clampedVol.toDouble()
                while (i < end) {
                    val sample = (rawBytes[i].toInt() and 0xFF) or
                            ((rawBytes[i + 1].toInt() and 0xFF) shl 8) or
                            ((rawBytes[i + 2].toInt() and 0xFF) shl 16) or
                            (rawBytes[i + 3].toInt() shl 24)
                    val scaled = (sample.toDouble() * volD).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                    rawBytes[i] = (scaled and 0xFF).toByte()
                    rawBytes[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                    rawBytes[i + 2] = ((scaled shr 16) and 0xFF).toByte()
                    rawBytes[i + 3] = ((scaled shr 24) and 0xFF).toByte()
                    i += 4
                }
            }
        }
    }
}
