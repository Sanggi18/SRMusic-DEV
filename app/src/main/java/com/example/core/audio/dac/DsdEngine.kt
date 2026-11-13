package com.example.core.audio.dac

import android.util.Log

object DsdEngine {
    private const val TAG = "DsdEngine"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("dsd")
            isLoaded = true
            Log.i(TAG, "libdsd.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libdsd.so", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading libdsd.so", e)
        }
    }

    fun isAvailable(): Boolean = isLoaded

    @JvmStatic
    external fun nativeConvertDsdToPcm16(
        dsdBytes: ByteArray,
        numBytes: Int,
        channels: Int,
        decimationFactor: Int,
        gain: Float,
        outPcmBytes: ByteArray
    ): Int

    @JvmStatic
    external fun nativeConvertDsdToPcmFloat(
        dsdBytes: ByteArray,
        numBytes: Int,
        channels: Int,
        decimationFactor: Int,
        gain: Float,
        outPcm: FloatArray
    ): Int
}
