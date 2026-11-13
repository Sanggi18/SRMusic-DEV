package com.example.core.audio.usb.media3

import android.util.Log
import com.example.core.audio.usb.UsbAudioStream
import com.example.core.audio.usb.UsbAudioTelemetry
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class UsbStreamingThread(
    private val usbStream: UsbAudioStream,
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "UsbStreamingThread"
        private const val QUEUE_CAPACITY = 128
        private const val POLL_TIMEOUT_MS = 100L
    }

    private sealed class AudioBuffer {
        class FloatBuffer(val data: FloatArray) : AudioBuffer()
        class RawBuffer(val data: ByteArray, val encoding: Int) : AudioBuffer()
    }

    private val audioQueue = ArrayBlockingQueue<AudioBuffer>(QUEUE_CAPACITY)

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var isCurrentlyWriting = false

    private var thread: Thread? = null
    private var dropCount = 0

    fun start() {
        running = true
        thread = Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}
            Log.i(TAG, "USB streaming thread started (priority=URGENT_AUDIO)")
            while (running) {
                if (paused) {
                    Thread.sleep(50)
                    continue
                }

                val qBefore = audioQueue.size
                when (val buf = audioQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    is AudioBuffer.FloatBuffer -> {
                        val startNs = System.nanoTime()
                        try {
                            isCurrentlyWriting = true
                            usbStream.write(buf.data)
                        } finally {
                            isCurrentlyWriting = false
                            val fallbackLatencyUs = (System.nanoTime() - startNs) / 1000L
                            val nativeLatencyUs = usbStream.lastWriteUs
                            val latencyUs = if (nativeLatencyUs > 0L) nativeLatencyUs else fallbackLatencyUs
                            val channels = usbStream.channelCount.coerceAtLeast(1)
                            val nativeSamples = usbStream.lastSamplesCount
                            val totalSamples = if (nativeSamples > 0) nativeSamples else buf.data.size
                            val framesCount = totalSamples / channels
                            val actualWritten = usbStream.framesWritten
                            val nativeCalls = usbStream.writeCallCount
                            val inFlight = usbStream.urbsInFlight
                            UsbAudioTelemetry.recordWrite(
                                framesCount = framesCount,
                                latencyUs = latencyUs,
                                inFlight = inFlight,
                                samplesCount = totalSamples,
                                totalFramesWritten = actualWritten,
                                nativeWriteCalls = nativeCalls
                            )
                        }
                        if (qBefore <= 1) Log.w(TAG, "Queue nearly empty: $qBefore before write")
                    }

                    is AudioBuffer.RawBuffer -> {
                        val startNs = System.nanoTime()
                        try {
                            isCurrentlyWriting = true
                            usbStream.writeRaw(buf.data, buf.encoding)
                        } finally {
                            isCurrentlyWriting = false
                            val fallbackLatencyUs = (System.nanoTime() - startNs) / 1000L
                            val nativeLatencyUs = usbStream.lastWriteUs
                            val latencyUs = if (nativeLatencyUs > 0L) nativeLatencyUs else fallbackLatencyUs
                            val bps = PcmUtils.bytesPerSample(buf.encoding).coerceAtLeast(1)
                            val channels = usbStream.channelCount.coerceAtLeast(1)
                            val nativeSamples = usbStream.lastSamplesCount
                            val totalSamples = if (nativeSamples > 0) nativeSamples else (buf.data.size / bps)
                            val framesCount = totalSamples / channels
                            val actualWritten = usbStream.framesWritten
                            val nativeCalls = usbStream.writeCallCount
                            val inFlight = usbStream.urbsInFlight
                            UsbAudioTelemetry.recordWrite(
                                framesCount = framesCount,
                                latencyUs = latencyUs,
                                inFlight = inFlight,
                                samplesCount = totalSamples,
                                totalFramesWritten = actualWritten,
                                nativeWriteCalls = nativeCalls
                            )
                        }
                        if (qBefore <= 1) Log.w(TAG, "Queue nearly empty: $qBefore before writeRaw")
                    }

                    null -> Log.w(TAG, "Queue EMPTY — poll timeout")
                }
            }
            Log.i(TAG, "USB streaming thread exited")
        }, "UsbStreamingThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun enqueue(floatBuf: FloatArray) {
        val buf = AudioBuffer.FloatBuffer(floatBuf)
        if (!audioQueue.offer(buf)) {
            audioQueue.poll()
            audioQueue.offer(buf)
            dropCount++
            if (dropCount <= 3 || dropCount % 100 == 0) {
                Log.w(TAG, "Queue full, dropped buffer #$dropCount")
            }
        }
        UsbAudioTelemetry.updateWaterline(queuedFrames(), sampleRate, dropCount)
    }

    fun enqueueRaw(rawBytes: ByteArray, encoding: Int) {
        val buf = AudioBuffer.RawBuffer(rawBytes, encoding)
        if (!audioQueue.offer(buf)) {
            audioQueue.poll()
            audioQueue.offer(buf)
            dropCount++
            if (dropCount <= 3 || dropCount % 100 == 0) {
                Log.w(TAG, "Queue full, dropped raw buffer #$dropCount")
            }
        }
        UsbAudioTelemetry.updateWaterline(queuedFrames(), sampleRate, dropCount)
    }

    fun pauseStreaming() { paused = true }

    fun resumeStreaming() { paused = false }

    fun hasPendingData(): Boolean = !audioQueue.isEmpty() || isCurrentlyWriting

    fun queuedFrames(): Long {
        val count = audioQueue.size.toLong() + (if (isCurrentlyWriting) 1L else 0L)
        return count * 1024L
    }

    fun queueSize(): Int = audioQueue.size

    fun flush() {
        audioQueue.clear()
    }

    fun stop() {
        running = false
        audioQueue.clear()
        thread?.join(2000)
        thread = null
    }
}
