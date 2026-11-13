package com.example.core.audio.usb.media3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.example.core.audio.usb.UsbAudioDevice
import com.example.core.audio.usb.UsbAudioStream
import com.example.core.audio.usb.UsbAudioTelemetry
import com.example.core.audio.usb.UsbSourceTrackFormat
import com.example.core.audio.AudioTerminalLogger
import com.example.core.audio.dac.DacVolumeMode
import com.example.core.audio.dac.DsdGainCompensation
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
class UsbAudioSink(
    private val delegate: DefaultAudioSink,
    private val context: Context,
    private val config: UsbAudioSinkConfig = UsbAudioSinkConfig(),
    private val onUsbOutputUnavailable: () -> Unit = {},
    private val onUsbOutputActive: ((sampleRate: Int, channelCount: Int, bitDepth: Int, deviceName: String, volMode: DacVolumeMode, gain: Float) -> Unit)? = null
) : ForwardingAudioSink(delegate) {

    private var usbAudioStream: UsbAudioStream? = null

    private val usbAudioDevice = UsbAudioDevice.getInstance(context)

    private var usbStreamingThread: UsbStreamingThread? = null

    private var currentEncoding: Int = C.ENCODING_PCM_16BIT

    private var currentSampleRate: Int = 0

    private var currentChannelCount: Int = 0

    private var currentBitDepth: Int = 0

    private var currentTargetBitDepth: Int = 0

    private val volumeLock = Any()

    @Volatile private var pendingVolume: Float = 1f
    private var delegateMuted: Boolean = false

    private var handleBufferCallCount: Long = 0

    private var usbStartMediaTimeUs: Long = 0L

    private var usbStartMediaTimeNeedsInit: Boolean = true

    private var usbStartFrames: Long = 0L

    private var startMediaTimeNeedsSync: Boolean = false

    private var handledEndOfStream: Boolean = false

    private var isReleasingNormal: Boolean = false

    private var hasReportedUnavailable: Boolean = false

    private var volumeReceiver: BroadcastReceiver? = null

    private fun isDirectBitPerfectPcmFormat(format: Format): Boolean {
        if (!config.bitPerfectEnabled) return false
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
        if (format.channelCount <= 0 || format.sampleRate <= 0) return false
        return when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> true
            else -> false
        }
    }

    override fun supportsFormat(format: Format): Boolean {
        if (isDirectBitPerfectPcmFormat(format)) {
            return true
        }
        return super.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        if (isDirectBitPerfectPcmFormat(format)) {
            return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
        return super.getFormatSupport(format)
    }

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val inputFormat = audioSinkConfig.format

        val enc = inputFormat.pcmEncoding

        currentEncoding = enc

        handleBufferCallCount = 0

        hasReportedUnavailable = false

        handledEndOfStream = false

        startMediaTimeNeedsSync = true

        val sr = inputFormat.sampleRate.takeIf { it > 0 }

        val ch = inputFormat.channelCount.takeIf { it > 0 }

        Log.i(TAG, "configure: pcmEncoding=${when(enc) {
            C.ENCODING_PCM_FLOAT -> "FLOAT"

            C.ENCODING_PCM_16BIT -> "16BIT"

            C.ENCODING_PCM_24BIT -> "24BIT"

            C.ENCODING_PCM_32BIT -> "32BIT"

            else -> "UNKNOWN($enc)"
        }} rate=${inputFormat.sampleRate} ch=${inputFormat.channelCount} bitPerfect=${config.bitPerfectEnabled} volMode=${config.dacVolumeMode}")

        var usbConfiguredSuccessfully = false

        if (config.bitPerfectEnabled && sr != null && ch != null) {
            val device = usbAudioDevice.findUsbAudioDevice()

            if (device != null && usbAudioDevice.hasPermission(device)) {
                val success = configureUsbBitPerfect(sr, ch, enc)

                if (success) {
                    usbStartMediaTimeNeedsInit = true

                    usbStartFrames = 0L

                    startMediaTimeNeedsSync = false

                    handledEndOfStream = false

                    if (config.forceRouteToSpeaker) forceMediaToSpeaker()

                    super.configure(audioSinkConfig)

                    muteDelegateIfNeeded()

                    Log.i(TAG, "Delegate configured (muted, routed to speaker for dummy sync)")

                    usbConfiguredSuccessfully = true

                    return
                } else {
                    Log.w(TAG, "configureUsbBitPerfect failed; falling back to normal output")
                }
            } else if (device != null) {
                Log.w(TAG, "USB DAC found but permission not granted; falling back to normal output")
            } else {
                Log.w(TAG, "No USB DAC device found; falling back to normal output")
            }
        }

        super.configure(audioSinkConfig)

        if (usbAudioStream != null && !config.bitPerfectEnabled) {
            releaseUsbStream()
        }

        if (config.bitPerfectEnabled && !usbConfiguredSuccessfully) {
            unmuteDelegateIfNeeded()

            clearForcedRouting()

            if (!isReleasingNormal && !hasReportedUnavailable) {
                hasReportedUnavailable = true

                onUsbOutputUnavailable()
            }
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        val stream = usbAudioStream

        if (config.bitPerfectEnabled && stream?.isAlive == true) {
            val targetBits = UsbSourceTrackFormat.sourceBitDepth
            if (targetBits in listOf(16, 24, 32) && currentTargetBitDepth > 0 && targetBits != currentTargetBitDepth) {
                Log.i(TAG, "Dynamic track bit depth change: $currentTargetBitDepth -> $targetBits — reconfiguring USB stream")
                val sr = currentSampleRate
                val ch = currentChannelCount
                val enc = currentEncoding
                releaseUsbStream(keepDeviceOpen = true)
                if (sr > 0 && ch > 0) {
                    configureUsbBitPerfect(sr, ch, enc)
                }
                return false
            }

            muteDelegateIfNeeded()

            if (usbStartMediaTimeNeedsInit || startMediaTimeNeedsSync) {
                usbStartMediaTimeUs = maxOf(0L, presentationTimeUs)

                val queued = usbStreamingThread?.queuedFrames() ?: 0L

                usbStartFrames = maxOf(0L, stream.framesWritten - queued)

                usbStartMediaTimeNeedsInit = false

                startMediaTimeNeedsSync = false

                handledEndOfStream = false

                Log.i(TAG, "Sync media time: startMediaTimeUs=$usbStartMediaTimeUs, usbStartFrames=$usbStartFrames (queued=$queued)")
            }

            val thread = usbStreamingThread ?: return true

            if (thread.queueSize() >= QUEUE_BACKPRESSURE_THRESHOLD) {
                return false
            }

            handleBufferCallCount++
            val snapshot: ByteBuffer = buffer.slice().order(buffer.order())

            val effectiveVolMode = getEffectiveVolumeMode()
            val softVol = if (effectiveVolMode == DacVolumeMode.SOFTWARE) getEffectiveSoftwareVolume() else 1f

            if (currentEncoding == C.ENCODING_PCM_FLOAT) {
                val totalSamples = snapshot.remaining() / 4

                if (totalSamples > 0) {
                    val floatBuf = FloatArray(totalSamples)

                    snapshot.asFloatBuffer().get(floatBuf)

                    if (config.dsdGainCompensation == DsdGainCompensation.PLUS_6DB) {
                        for (i in floatBuf.indices) {
                            floatBuf[i] = (floatBuf[i] * 1.9952623f).coerceIn(-1f, 1f)
                        }
                    }

                    if (effectiveVolMode == DacVolumeMode.SOFTWARE && softVol < 0.9999f) {
                        for (i in floatBuf.indices) {
                            floatBuf[i] *= softVol
                        }
                    }

                    if (handleBufferCallCount <= 3) {
                        Log.i(TAG, "handleBuffer #$handleBufferCallCount: FLOAT samples=$totalSamples softVol=$softVol")
                    }

                    thread.enqueue(floatBuf)
                }
            } else {
                val remaining = snapshot.remaining()

                if (remaining > 0) {
                    val rawBytes = ByteArray(remaining)

                    snapshot.get(rawBytes)

                    if (effectiveVolMode == DacVolumeMode.SOFTWARE && softVol < 0.9999f) {
                        PcmUtils.scalePcm(rawBytes, currentEncoding, softVol)
                    }

                    if (handleBufferCallCount <= 3) {
                        val bps = PcmUtils.bytesPerSample(currentEncoding)

                        Log.i(TAG, "handleBuffer #$handleBufferCallCount: RAW ${bps * 8}bit bytes=$remaining softVol=$softVol")
                    }

                    thread.enqueueRaw(rawBytes, currentEncoding)
                }
            }

            buffer.position(buffer.limit())

            return true
        }

        unmuteDelegateIfNeeded()

        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (config.bitPerfectEnabled) {
            val stream = usbAudioStream

            if (stream != null && stream.isAlive) {
                if (usbStartMediaTimeNeedsInit) return AudioSink.CURRENT_POSITION_NOT_SET

                val frames = stream.framesWritten

                val queued = usbStreamingThread?.queuedFrames() ?: 0L

                val actualPlayedFrames = maxOf(0L, frames - queued)

                val sr = currentSampleRate

                return if (sr > 0) {
                    val framesPlayedThisTrack = maxOf(0L, actualPlayedFrames - usbStartFrames)

                    usbStartMediaTimeUs + framesPlayedThisTrack * C.MICROS_PER_SECOND / sr
                } else AudioSink.CURRENT_POSITION_NOT_SET
            }
        }

        return super.getCurrentPositionUs(sourceEnded)
    }

    override fun handleDiscontinuity() {
        super.handleDiscontinuity()

        startMediaTimeNeedsSync = true

        handledEndOfStream = false

        Log.i(TAG, "handleDiscontinuity: marked startMediaTimeNeedsSync=true, reset handledEndOfStream")
    }

    override fun hasPendingData(): Boolean {
        if (config.bitPerfectEnabled && usbAudioStream?.isAlive == true) {
            return usbStreamingThread?.hasPendingData() == true
        }

        return super.hasPendingData()
    }

    override fun isEnded(): Boolean {
        if (config.bitPerfectEnabled && usbAudioStream?.isAlive == true) {
            return handledEndOfStream && !hasPendingData()
        }

        return super.isEnded()
    }

    override fun playToEndOfStream() {
        handledEndOfStream = true

        super.playToEndOfStream()
    }

    override fun play() {
        super.play()

        usbStreamingThread?.resumeStreaming()
    }

    override fun pause() {
        usbStreamingThread?.pauseStreaming()

        super.pause()
    }

    private fun getEffectiveVolumeMode(): DacVolumeMode {
        return when (config.dacVolumeMode) {
            DacVolumeMode.HARDWARE -> {
                if (usbAudioDevice.hasHardwareVolumeSupport()) DacVolumeMode.HARDWARE else DacVolumeMode.SOFTWARE
            }
            DacVolumeMode.SOFTWARE -> DacVolumeMode.SOFTWARE
            DacVolumeMode.AUTO -> {
                if (usbAudioDevice.hasHardwareVolumeSupport()) DacVolumeMode.HARDWARE else DacVolumeMode.SOFTWARE
            }
        }
    }

    private fun getEffectiveSoftwareVolume(): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return pendingVolume.coerceIn(0f, 1f)

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val isMute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            curVol <= 0
        }

        if (isMute || curVol <= 0) return 0f

        val linearRatio = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)
        val safePending = if (pendingVolume.isFinite()) pendingVolume.coerceIn(0f, 1f) else 0f
        return (linearRatio * safePending).coerceIn(0f, 1f)
    }

    override fun setVolume(volume: Float) {
        val safeVol = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f

        synchronized(volumeLock) {
            pendingVolume = safeVol
        }

        if (config.bitPerfectEnabled && usbAudioStream?.isAlive == true) {
            muteDelegateIfNeeded()
            if (getEffectiveVolumeMode() == DacVolumeMode.HARDWARE) {
                applyHardwareVolume()
            }
        } else {
            unmuteDelegateIfNeeded()
            super.setVolume(safeVol)
        }
    }

    override fun flush() {
        super.flush()

        usbStreamingThread?.flush()

        usbAudioStream?.flush()

        UsbAudioTelemetry.onFlush()

        usbStartMediaTimeNeedsInit = true

        usbStartFrames = 0L

        startMediaTimeNeedsSync = false

        handledEndOfStream = false
    }

    override fun reset() {
        super.reset()

        usbStartMediaTimeNeedsInit = true

        usbStartFrames = 0L

        startMediaTimeNeedsSync = false

        handledEndOfStream = false
    }

    override fun release() {
        isReleasingNormal = true

        try {
            releaseUsbStream()
        } finally {
            super.release()

            isReleasingNormal = false
        }
    }

    private fun calculateEffectiveHardwareVolume(): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            ?: return pendingVolume.coerceIn(0f, 1f)

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val isMute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            curVol <= 0
        }

        if (isMute || curVol <= 0) return 0f

        val linearRatio = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)

        val safePending = if (pendingVolume.isFinite()) pendingVolume.coerceIn(0f, 1f) else 0f

        val combinedLinear = (linearRatio * safePending).coerceIn(0f, 1f)

        return (combinedLinear * combinedLinear).coerceIn(0f, 1f)
    }

    private fun applyHardwareVolume(): Boolean {
        val volRatio = calculateEffectiveHardwareVolume()

        val ok = usbAudioDevice.setHardwareVolume(volRatio)

        if (!ok) {
            Log.e(TAG, "USB_FALLBACK reason=VOLUME_CONTROL_FAILED: Hardware volume command failed on DAC for ratio=$volRatio")

            releaseUsbStream()

            if (!hasReportedUnavailable) {
                hasReportedUnavailable = true

                onUsbOutputUnavailable()
            }

            return false
        }

        return true
    }

    private fun initializeSafeVolume(): Boolean {
        val mode = getEffectiveVolumeMode()
        if (mode == DacVolumeMode.HARDWARE) {
            if (!usbAudioDevice.hasHardwareVolumeSupport() || !applyHardwareVolume()) {
                Log.w(TAG, "Hardware volume unavailable or failed, falling back to software volume mode for hearing safety")
            } else {
                Log.i(TAG, "Volume safety initialized via hardware DAC control")
            }
        } else {
            if (usbAudioDevice.hasHardwareVolumeSupport()) {
                usbAudioDevice.setHardwareVolume(1.0f)
            }
            Log.i(TAG, "Volume safety initialized via software volume mode")
        }

        return true
    }

    private fun onSystemVolumeChanged() {
        if (config.bitPerfectEnabled && usbAudioStream?.isAlive == true) {
            if (getEffectiveVolumeMode() == DacVolumeMode.HARDWARE) {
                applyHardwareVolume()
            }
        }
    }

    private fun registerVolumeReceiver() {
        if (volumeReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)

                    if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                        onSystemVolumeChanged()
                    }
                }
            }
        }

        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            volumeReceiver = receiver

            Log.d(TAG, "Volume receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register volume receiver: ${e.message}")
        }
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}

            volumeReceiver = null

            Log.d(TAG, "Volume receiver unregistered")
        }
    }

    private fun configureUsbBitPerfect(sampleRate: Int, channelCount: Int, encoding: Int): Boolean {
        val sourceBits = UsbSourceTrackFormat.sourceBitDepth
        val targetBitDepth = when {
            sourceBits in listOf(16, 24, 32) -> sourceBits
            encoding == C.ENCODING_PCM_16BIT -> 16
            encoding == C.ENCODING_PCM_24BIT -> 24
            encoding == C.ENCODING_PCM_32BIT -> 32
            else -> 16
        }

        if (sampleRate == currentSampleRate && channelCount == currentChannelCount
            && targetBitDepth == currentTargetBitDepth && usbAudioStream?.isAlive == true) {
            Log.d(TAG, "USB stream cached for rate=$sampleRate ch=$channelCount bits=$targetBitDepth — reusing")

            applyHardwareVolume()

            return true
        }

        if (usbAudioStream != null) releaseUsbStream(keepDeviceOpen = true)

        val usbDevice = usbAudioDevice.findUsbAudioDevice() ?: run {
            Log.w(TAG, "configureUsbBitPerfect: no USB audio device found")

            return false
        }

        var deviceInfo = usbAudioDevice.openDevice(usbDevice)

        if (deviceInfo == null) {
            Log.e(TAG, "Failed to open USB device")

            return false
        }

        if (!initializeSafeVolume()) {
            usbAudioDevice.closeDevice()

            return false
        }

        val (altSetting, bitDepth) = usbAudioDevice.findAltSettingForBitDepth(targetBitDepth, sampleRate)

        val altEndpoints = usbAudioDevice.getEndpointInfoForAltSetting(altSetting)
        var epOut = altEndpoints?.first ?: deviceInfo.endpointOutAddress
        var epFb = altEndpoints?.second ?: deviceInfo.endpointFeedbackAddress
        var maxPkt = altEndpoints?.third ?: deviceInfo.maxPacketSize

        Log.i(
            TAG,
            "Bit-perfect setup: targetBits=$targetBitDepth (source=$sourceBits) -> selected alt=$altSetting usb=${bitDepth}bit epOut=0x${epOut.toString(16)} epFb=0x${epFb.toString(16)} maxPkt=$maxPkt clockSource=0x${deviceInfo.clockSourceId.toString(16)}"
        )

        var stream = UsbAudioStream(
            fd = deviceInfo.fd,
            interfaceId = deviceInfo.interfaceId,
            endpointOut = epOut,
            endpointFeedback = epFb,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitDepth = bitDepth,
            maxPacketSize = maxPkt
        )

        if (!stream.isReady) {
            Log.e(TAG, "USB stream creation failed")

            stream.release()

            usbAudioDevice.closeDevice()

            return false
        }

        if (!usbAudioDevice.setAltSetting(0)) {
            Log.w(TAG, "setAlt(0) failed — reopening device...")

            usbAudioDevice.closeDevice()

            stream.release()

            deviceInfo = usbAudioDevice.openDevice(usbDevice)

            if (deviceInfo == null) {
                Log.e(TAG, "Device reopen failed")

                return false
            }

            val reopenedAltEndpoints = usbAudioDevice.getEndpointInfoForAltSetting(altSetting)
            epOut = reopenedAltEndpoints?.first ?: deviceInfo.endpointOutAddress
            epFb = reopenedAltEndpoints?.second ?: deviceInfo.endpointFeedbackAddress
            maxPkt = reopenedAltEndpoints?.third ?: deviceInfo.maxPacketSize

            stream = UsbAudioStream(
                fd = deviceInfo.fd,
                interfaceId = deviceInfo.interfaceId,
                endpointOut = epOut,
                endpointFeedback = epFb,
                sampleRate = sampleRate,
                channelCount = channelCount,
                bitDepth = bitDepth,
                maxPacketSize = maxPkt
            )

            if (!stream.isReady) {
                Log.e(TAG, "USB stream recreation failed after reopen")

                stream.release()

                usbAudioDevice.closeDevice()

                return false
            }

            Log.i(TAG, "Device reopened with fresh fd=${deviceInfo.fd}")
        }

        val rateSetBeforeAlt = usbAudioDevice.setSampleRate(sampleRate)

        val clockValid = usbAudioDevice.readClockValid()

        Log.i(TAG, "SET_CUR=$sampleRate (ok=$rateSetBeforeAlt), CLOCK_VALID=$clockValid")

        if (deviceInfo.uacVersion != 100 && rateSetBeforeAlt) {
            usbAudioDevice.setAltSetting(0)
            Log.i(TAG, "Step 4: setAlt(0) again — defensive reset after clock change")
        }

        val altResult = usbAudioDevice.setAltSetting(altSetting)

        Log.i(TAG, "setAlt($altSetting) result=$altResult")

        if (deviceInfo.uacVersion == 100 || !rateSetBeforeAlt || deviceInfo.clockSourceId <= 0) {
            val rateSetAfterAlt = usbAudioDevice.setSampleRate(sampleRate)
            Log.i(TAG, "Post-alt SET_CUR=$sampleRate result=$rateSetAfterAlt")
        }

        Thread.sleep(50)

        val verifiedRate = usbAudioDevice.readSampleRate()
        if (verifiedRate > 0 && verifiedRate != sampleRate) {
            Log.w(TAG, "Rate verification mismatch ($verifiedRate Hz != $sampleRate Hz), retrying setSampleRate...")
            usbAudioDevice.setSampleRate(sampleRate)
            Thread.sleep(30)
        }

        val startResult = stream.start()

        Log.i(TAG, "stream.start() result=$startResult")

        if (!startResult) {
            Log.e(TAG, "Failed to start USB stream")

            stream.release()

            usbAudioDevice.closeDevice()

            return false
        }

        currentSampleRate = sampleRate

        currentChannelCount = channelCount

        currentBitDepth = bitDepth

        currentTargetBitDepth = targetBitDepth

        usbAudioStream = stream

        UsbAudioTelemetry.updateStreaming(true, sampleRate, bitDepth, targetBufferFrames = config.usbBufferSize)

        usbStreamingThread = UsbStreamingThread(stream, sampleRate).also { it.start() }

        registerVolumeReceiver()

        val curVolRatio = calculateEffectiveHardwareVolume()

        onUsbOutputActive?.invoke(sampleRate, channelCount, bitDepth, deviceInfo.deviceName, config.dacVolumeMode, curVolRatio)

        Log.i(
            TAG,
            "USB bit-perfect stream ACTIVE: rate=$sampleRate ch=$channelCount bits=$bitDepth device=${deviceInfo.deviceName} volumeRatio=$curVolRatio"
        )

        val vidHex = "0x" + usbDevice.vendorId.toString(16).lowercase()

        val pidHex = "0x" + usbDevice.productId.toString(16).lowercase()

        AudioTerminalLogger.log("initUsbDac SUCCESS: VID=$vidHex, PID=$pidHex")

        AudioTerminalLogger.log("playAudio START: rate=$sampleRate, bitDepth=$bitDepth")

        AudioTerminalLogger.log("SUCCESS: isPlaying=true, activeIsoTransfers=0")

        AudioTerminalLogger.log("Bit-Perfect State : Active - ${deviceInfo.deviceName} USB Exclusive")

        return true
    }

    private fun releaseUsbStream(keepDeviceOpen: Boolean = false) {
        unregisterVolumeReceiver()

        UsbAudioTelemetry.updateStreaming(false, 0, 0)

        currentBitDepth = 0

        currentTargetBitDepth = 0

        usbStartMediaTimeNeedsInit = true

        usbStartFrames = 0L

        startMediaTimeNeedsSync = false

        handledEndOfStream = false

        val stream = usbAudioStream

        usbAudioStream = null

        stream?.stop()

        val thread = usbStreamingThread

        usbStreamingThread = null

        thread?.stop()

        if (stream != null) {
            val drained = stream.drainUrbs()

            Log.i(TAG, "USB stream drained $drained URBs")

            stream.release()
        }

        if (!keepDeviceOpen) {
            if (usbAudioDevice.hasHardwareVolumeSupport()) {
                usbAudioDevice.setHardwareVolume(1.0f)

                Log.i(TAG, "Restored DAC hardware volume to 1.0 (0 dB) before release")
            }

            clearForcedRouting()

            unmuteDelegateIfNeeded()

            usbAudioDevice.closeDevice()

            Log.i(TAG, "USB audio stream released, forced routing cleared, and USB device closed")
        } else {
            Log.i(TAG, "USB audio stream released (device kept open for reconfiguration)")
        }
    }

    private fun muteDelegateIfNeeded() {
        if (!delegateMuted) {
            super.setVolume(0f)

            delegateMuted = true
        }
    }

    private fun unmuteDelegateIfNeeded() {
        if (delegateMuted) {
            super.setVolume(pendingVolume)

            delegateMuted = false

            Log.i(TAG, "Delegate unmuted, volume restored to $pendingVolume")
        }
    }

    private fun forceMediaToSpeaker() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            if (speaker != null) {
                delegate.setPreferredDevice(speaker)

                Log.i(TAG, "Delegate routed to speaker")
            }
        } catch (e: Exception) {
            Log.w(TAG, "forceMediaToSpeaker failed: ${e.message}")
        }
    }

    private fun clearForcedRouting() {
        try {
            delegate.setPreferredDevice(null)

            Log.i(TAG, "Delegate preferred device cleared")
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "UsbAudioSink"

        private const val QUEUE_BACKPRESSURE_THRESHOLD = 16
    }
}
