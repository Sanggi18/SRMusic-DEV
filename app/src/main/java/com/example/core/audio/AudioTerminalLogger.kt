package com.example.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AudioTerminalLogger {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("terminal_logger")

            isNativeLoaded = true
        } catch (e: Throwable) {
            android.util.Log.e("AudioTerminalLogger", "Failed to load terminal_logger library", e)

            isNativeLoaded = false
        }
    }

    private val _logs = MutableStateFlow<List<String>>(emptyList())

    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _diagLogs = MutableStateFlow<List<String>>(emptyList())

    val diagLogs: StateFlow<List<String>> = _diagLogs.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val syncScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var initialized = false

    private var lastSyncedGeneration = -1L

    private var syncScheduled = false

    private val fallbackBuffer = ArrayDeque<String>(250)

    private val fallbackDiagBuffer = ArrayDeque<String>(250)

    fun init(context: Context) {
        if (initialized) return

        initialized = true

        val currentOutput = AudioOutputDetector.getCurrentOutput(context)

        log("Audio Output Route: ${currentOutput.displayName} [${currentOutput.type}]")

        logDiag("SRMusicAudioDiag", "D", "EFFECTIVE_OUTPUT_CONFIG type=${currentOutput.type} name=${currentOutput.displayName}")

        startSyncDispatcher()
    }

    private fun startSyncDispatcher() {
        syncScope.launch {
            while (isActive) {
                delay(200)

                syncNow()
            }
        }
    }

    private fun scheduleSync() {
        if (syncScheduled) return

        syncScheduled = true

        mainHandler.postDelayed({
            syncScheduled = false

            syncNow()
        }, 50)
    }

    private fun syncNow() {
        if (isNativeLoaded) {
            try {
                val gen = nativeGetGeneration()

                if (gen != lastSyncedGeneration) {
                    lastSyncedGeneration = gen

                    _logs.value = nativeGetLogs().toList()

                    _diagLogs.value = nativeGetDiagLogs().toList()
                }
            } catch (_: Throwable) {}
        }
    }

    fun log(rawMessage: String) {
        if (isNativeLoaded) {
            try {
                nativeLog(rawMessage)

                scheduleSync()

                return
            } catch (_: Throwable) {}
        }

        synchronized(fallbackBuffer) {
            if (fallbackBuffer.size >= 250) fallbackBuffer.removeFirst()

            fallbackBuffer.addLast(rawMessage)

            _logs.value = fallbackBuffer.toList()
        }
    }

    fun logDiagRaw(line: String) {
        if (isNativeLoaded) {
            try {
                nativeLogDiagRaw(line)

                scheduleSync()

                return
            } catch (_: Throwable) {}
        }

        synchronized(fallbackDiagBuffer) {
            if (fallbackDiagBuffer.size >= 250) fallbackDiagBuffer.removeFirst()

            fallbackDiagBuffer.addLast(line)

            _diagLogs.value = fallbackDiagBuffer.toList()
        }
    }

    fun logDiag(tag: String, priority: String, message: String) {
        if (isNativeLoaded) {
            try {
                nativeLogDiag(tag, priority, message)

                scheduleSync()

                return
            } catch (_: Throwable) {}
        }

        val tagPrefix = if (tag == "SRMusicAudioDiag") "" else "$tag "

        logDiagRaw("$priority : $tagPrefix$message")
    }

    fun clear() {
        if (isNativeLoaded) {
            try {
                nativeClear()

                lastSyncedGeneration = -1L
            } catch (_: Throwable) {}
        }

        synchronized(fallbackBuffer) {
            fallbackBuffer.clear()

            fallbackDiagBuffer.clear()
        }

        _logs.value = emptyList()

        _diagLogs.value = emptyList()
    }

    @JvmStatic
    private external fun nativeLog(msg: String)

    @JvmStatic
    private external fun nativeLogDiag(tag: String, priority: String, msg: String)

    @JvmStatic
    private external fun nativeLogDiagRaw(line: String)

    @JvmStatic
    private external fun nativeGetLogs(): Array<String>

    @JvmStatic
    private external fun nativeGetDiagLogs(): Array<String>

    @JvmStatic
    private external fun nativeGetGeneration(): Long

    @JvmStatic
    private external fun nativeClear()
}
