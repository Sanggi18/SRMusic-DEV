package com.example.core.playback

import android.content.Context
import android.media.AudioFormat
import android.os.Handler
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.example.core.audio.AudioOutputApi
import com.example.core.audio.OboeAudioOutputProvider
import com.example.core.audio.dac.DacSampleRateMode
import com.example.core.audio.dac.DacVolumeMode
import com.example.core.audio.dac.DsdGainCompensation
import com.example.core.audio.dac.DsdOutputMode
import com.example.core.audio.usb.media3.UsbAudioSink
import com.example.core.audio.usb.media3.UsbAudioSinkConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@UnstableApi
class FfmpegRenderersFactory(
    private val context: Context,
    private val audioOutputApi: AudioOutputApi = AudioOutputApi.AUDIOTRACK,
    private val isBitPerfectEnabled: Boolean = false,
    private val dacVolumeMode: DacVolumeMode = DacVolumeMode.AUTO,
    private val dacExclusiveUsb: Boolean = true,
    private val dsdOutputMode: DsdOutputMode = DsdOutputMode.AUTO,
    private val dsdGainCompensation: DsdGainCompensation = DsdGainCompensation.PLUS_0DB,
    private val usbBufferSize: Int = 256,
    private val dacSampleRateMode: DacSampleRateMode = DacSampleRateMode.FOLLOW_SOURCE,
    private val dacFixedSampleRate: Int = 192000,
    private val isHighPrecisionResamplingEnabled: Boolean = false,
    private val isGaplessEnabled: Boolean = false,
    private val onInputFormatConfigured: ((Format) -> Unit)? = null,
    private val onEffectiveOutputConfigured: ((AudioOutputProvider.OutputConfig) -> Unit)? = null,
    private val onBackendFailure: (AudioOutputApi, String) -> Unit = { _, _ -> },
    private val onUsbOutputUnavailable: () -> Unit = {},
    private val onUsbOutputActive: ((sampleRate: Int, channelCount: Int, bitDepth: Int, deviceName: String, volMode: DacVolumeMode, gain: Float) -> Unit)? = null
) : DefaultRenderersFactory(context) {
    companion object {
        private const val TAG = "SRMusicAudioDiag"
    }

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        setEnableAudioTrackPlaybackParams(true)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        val ffmpegAvailable = FfmpegLibrary.isAvailable()

        if (ffmpegAvailable && (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER || extensionRendererMode == EXTENSION_RENDERER_MODE_ON)) {
            val extensionRendererIndex = out.size

            try {
                val ffmpegRenderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
                out.add(if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) 0 else extensionRendererIndex, ffmpegRenderer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FfmpegAudioRenderer", e)
            }
        }

        val mediaCodecRenderer = MediaCodecAudioRenderer(
            context,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            audioSink
        )
        out.add(mediaCodecRenderer)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        if (isBitPerfectEnabled) {
            val delegate = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .build()

            val usbSink = UsbAudioSink(
                delegate = delegate,
                context = context,
                config = UsbAudioSinkConfig(
                    bitPerfectEnabled = true,
                    forceRouteToSpeaker = true,
                    dacVolumeMode = dacVolumeMode,
                    dacExclusiveUsb = dacExclusiveUsb,
                    dsdOutputMode = dsdOutputMode,
                    dsdGainCompensation = dsdGainCompensation,
                    usbBufferSize = usbBufferSize,
                    dacSampleRateMode = dacSampleRateMode,
                    dacFixedSampleRate = dacFixedSampleRate
                ),
                onUsbOutputUnavailable = onUsbOutputUnavailable,
                onUsbOutputActive = onUsbOutputActive
            )

            return MonitoredForwardingAudioSink(usbSink) { inputFormat ->
                Log.d(
                    TAG,
                    "DECODER_INPUT_FORMAT (C++ Direct USB) rate=${inputFormat.sampleRate} channels=${inputFormat.channelCount} pcmEncoding=${inputFormat.pcmEncoding}"
                )
                onInputFormatConfigured?.invoke(inputFormat)
            }
        }

        val effectiveFloatOutput = if (isHighPrecisionResamplingEnabled) true else enableFloatOutput
        val builder = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(effectiveFloatOutput)

        if (audioOutputApi == AudioOutputApi.AUDIOTRACK) {
            builder.setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            builder.setAudioOutputProvider(
                MonitoredAudioTrackOutputProvider(context) { outputConfig ->
                    Log.d(
                        TAG,
                        "EFFECTIVE_OUTPUT_CONFIG rate=${outputConfig.sampleRate} chMask=${outputConfig.channelMask} enc=${outputConfig.encoding}"
                    )
                    onEffectiveOutputConfigured?.invoke(outputConfig)
                }
            )
        } else {
            builder.setAudioOutputProvider(
                OboeAudioOutputProvider(
                    context = context,
                    requestedApi = audioOutputApi,
                    onBackendFailure = onBackendFailure,
                    isGaplessEnabled = isGaplessEnabled
                )
            )
            builder.setEnableAudioTrackPlaybackParams(false)
        }

        val baseSink = builder.build()

        return MonitoredForwardingAudioSink(baseSink) { inputFormat ->
            Log.d(
                TAG,
                "DECODER_INPUT_FORMAT rate=${inputFormat.sampleRate} channels=${inputFormat.channelCount} pcmEncoding=${inputFormat.pcmEncoding}"
            )
            onInputFormatConfigured?.invoke(inputFormat)
        }
    }
}

@UnstableApi
private class MonitoredAudioTrackOutputProvider(
    context: Context,
    private val onOutputConfigEvaluated: (AudioOutputProvider.OutputConfig) -> Unit
) : AudioOutputProvider {
    private val delegate: AudioTrackAudioOutputProvider =
        AudioTrackAudioOutputProvider.Builder(context).build()

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig) =
        delegate.getFormatSupport(formatConfig)

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        val config = delegate.getOutputConfig(formatConfig)
        onOutputConfigEvaluated(config)
        return config
    }

    override fun getAudioOutput(outputConfig: AudioOutputProvider.OutputConfig): AudioOutput =
        delegate.getAudioOutput(outputConfig)

    override fun addListener(listener: AudioOutputProvider.Listener) = delegate.addListener(listener)

    override fun removeListener(listener: AudioOutputProvider.Listener) = delegate.removeListener(listener)

    override fun setClock(clock: Clock) = delegate.setClock(clock)

    override fun release() = delegate.release()
}

@UnstableApi
private class MonitoredForwardingAudioSink(
    delegate: AudioSink,
    private val onFormatConfigured: (Format) -> Unit
) : ForwardingAudioSink(delegate) {

    private var sourceChannelCount: Int = C.LENGTH_UNSET
    private var sourcePcmEncoding: Int = C.ENCODING_INVALID
    private var downmixActive: Boolean = false
    private var downmixedBuffer: ByteBuffer? = null
    private var lastSourceBuffer: ByteBuffer? = null

    private fun shouldDownmixToStereo(format: Format): Boolean {
        return format.sampleMimeType == MimeTypes.AUDIO_RAW &&
            format.channelCount > 2 &&
            (format.pcmEncoding == C.ENCODING_PCM_FLOAT || format.pcmEncoding == C.ENCODING_PCM_16BIT)
    }

    private fun toStereoFormat(format: Format): Format {
        return format.buildUpon()
            .setChannelCount(2)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
    }

    override fun supportsFormat(format: Format): Boolean {
        val targetFormat = if (shouldDownmixToStereo(format)) toStereoFormat(format) else format
        return super.supportsFormat(targetFormat)
    }

    override fun getFormatSupport(format: Format): @AudioSink.SinkFormatSupport Int {
        val targetFormat = if (shouldDownmixToStereo(format)) toStereoFormat(format) else format
        return super.getFormatSupport(targetFormat)
    }

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val inputFormat = audioSinkConfig.format
        sourceChannelCount = inputFormat.channelCount
        sourcePcmEncoding = inputFormat.pcmEncoding
        downmixActive = shouldDownmixToStereo(inputFormat)
        lastSourceBuffer = null
        downmixedBuffer?.clear()

        if (downmixActive) {
            val stereoFormat = toStereoFormat(inputFormat)
            val stereoSinkConfig = AudioSink.AudioSinkConfig.Builder(stereoFormat)
                .setPreferredBufferSizeOverride(audioSinkConfig.preferredBufferSizeOverride)
                .setTimeline(audioSinkConfig.timeline)
                .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                .build()
            super.configure(stereoSinkConfig)
        } else {
            super.configure(audioSinkConfig)
        }

        onFormatConfigured(inputFormat)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!downmixActive || sourceChannelCount <= 2) {
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        if (!buffer.hasRemaining()) {
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        val outBuf = if (lastSourceBuffer === buffer && downmixedBuffer != null) {
            downmixedBuffer!!
        } else {
            val converted = downmixToStereo(buffer, sourceChannelCount, sourcePcmEncoding)
            lastSourceBuffer = buffer
            downmixedBuffer = converted
            converted
        }

        val fullyConsumed = super.handleBuffer(outBuf, presentationTimeUs, encodedAccessUnitCount)
        if (fullyConsumed) {
            buffer.position(buffer.limit())
            lastSourceBuffer = null
        }
        return fullyConsumed
    }

    override fun flush() {
        lastSourceBuffer = null
        downmixedBuffer?.clear()
        super.flush()
    }

    override fun reset() {
        lastSourceBuffer = null
        downmixedBuffer = null
        downmixActive = false
        sourceChannelCount = C.LENGTH_UNSET
        sourcePcmEncoding = C.ENCODING_INVALID
        super.reset()
    }

    private fun ensureDownmixBuffer(capacityBytes: Int): ByteBuffer {
        val current = downmixedBuffer
        if (current != null && current.capacity() >= capacityBytes) {
            current.clear()
            current.limit(capacityBytes)
            return current
        }
        val allocated = ByteBuffer.allocateDirect(capacityBytes).order(ByteOrder.nativeOrder())
        allocated.limit(capacityBytes)
        downmixedBuffer = allocated
        return allocated
    }

    private fun downmixToStereo(
        src: ByteBuffer,
        channels: Int,
        pcmEncoding: Int
    ): ByteBuffer {
        val startPos = src.position()
        val remainingBytes = src.remaining()

        return when (pcmEncoding) {
            C.ENCODING_PCM_FLOAT -> {
                val bytesPerFrame = channels * 4
                val frameCount = remainingBytes / bytesPerFrame
                val outBytes = frameCount * 2 * 4
                val dst = ensureDownmixBuffer(outBytes)

                var inOffset = startPos
                var outOffset = 0
                for (i in 0 until frameCount) {
                    val fl = src.getFloat(inOffset)
                    val fr = src.getFloat(inOffset + 4)

                    var left = fl
                    var right = fr

                    when (channels) {
                        6 -> {
                            // 5.1 layout: FL, FR, FC, LFE, SL/BL, SR/BR
                            val fc = src.getFloat(inOffset + 8)
                            val lfe = src.getFloat(inOffset + 12)
                            val sl = src.getFloat(inOffset + 16)
                            val sr = src.getFloat(inOffset + 20)
                            val centerMix = fc * 0.70710677f
                            val lfeMix = lfe * 0.5f
                            left = (fl + centerMix + sl * 0.70710677f + lfeMix) * 0.65f
                            right = (fr + centerMix + sr * 0.70710677f + lfeMix) * 0.65f
                        }
                        8 -> {
                            // 7.1 layout: FL, FR, FC, LFE, BL, BR, SL, SR
                            val fc = src.getFloat(inOffset + 8)
                            val lfe = src.getFloat(inOffset + 12)
                            val bl = src.getFloat(inOffset + 16)
                            val br = src.getFloat(inOffset + 20)
                            val sl = src.getFloat(inOffset + 24)
                            val sr = src.getFloat(inOffset + 28)
                            val centerMix = fc * 0.70710677f
                            val lfeMix = lfe * 0.5f
                            left = (fl + centerMix + (bl + sl) * 0.5f + lfeMix) * 0.6f
                            right = (fr + centerMix + (br + sr) * 0.5f + lfeMix) * 0.6f
                        }
                        3 -> {
                            // 3.0 layout: FL, FR, FC
                            val fc = src.getFloat(inOffset + 8) * 0.70710677f
                            left = (fl + fc) * 0.70710677f
                            right = (fr + fc) * 0.70710677f
                        }
                        4 -> {
                            // Quad: FL, FR, BL, BR
                            val bl = src.getFloat(inOffset + 8) * 0.70710677f
                            val br = src.getFloat(inOffset + 12) * 0.70710677f
                            left = (fl + bl) * 0.70710677f
                            right = (fr + br) * 0.70710677f
                        }
                        5 -> {
                            // 5.0: FL, FR, FC, BL, BR
                            val fc = src.getFloat(inOffset + 8) * 0.70710677f
                            val bl = src.getFloat(inOffset + 12) * 0.70710677f
                            val br = src.getFloat(inOffset + 16) * 0.70710677f
                            left = (fl + fc + bl) * 0.65f
                            right = (fr + fc + br) * 0.65f
                        }
                        else -> {
                            val fc = src.getFloat(inOffset + 8) * 0.70710677f
                            left = (fl + fc) * 0.70710677f
                            right = (fr + fc) * 0.70710677f
                        }
                    }

                    dst.putFloat(outOffset, left.coerceIn(-1.0f, 1.0f))
                    dst.putFloat(outOffset + 4, right.coerceIn(-1.0f, 1.0f))
                    inOffset += bytesPerFrame
                    outOffset += 8
                }
                dst.position(0)
                dst.limit(outBytes)
                dst
            }

            C.ENCODING_PCM_16BIT -> {
                val bytesPerFrame = channels * 2
                val frameCount = remainingBytes / bytesPerFrame
                val outBytes = frameCount * 2 * 2
                val dst = ensureDownmixBuffer(outBytes)

                var inOffset = startPos
                var outOffset = 0
                for (i in 0 until frameCount) {
                    val fl = src.getShort(inOffset).toFloat()
                    val fr = src.getShort(inOffset + 2).toFloat()

                    var left = fl
                    var right = fr

                    when (channels) {
                        6 -> {
                            val fc = src.getShort(inOffset + 4).toFloat()
                            val lfe = src.getShort(inOffset + 6).toFloat()
                            val sl = src.getShort(inOffset + 8).toFloat()
                            val sr = src.getShort(inOffset + 10).toFloat()
                            val centerMix = fc * 0.70710677f
                            val lfeMix = lfe * 0.5f
                            left = (fl + centerMix + sl * 0.70710677f + lfeMix) * 0.65f
                            right = (fr + centerMix + sr * 0.70710677f + lfeMix) * 0.65f
                        }
                        8 -> {
                            val fc = src.getShort(inOffset + 4).toFloat()
                            val lfe = src.getShort(inOffset + 6).toFloat()
                            val bl = src.getShort(inOffset + 8).toFloat()
                            val br = src.getShort(inOffset + 10).toFloat()
                            val sl = src.getShort(inOffset + 12).toFloat()
                            val sr = src.getShort(inOffset + 14).toFloat()
                            val centerMix = fc * 0.70710677f
                            val lfeMix = lfe * 0.5f
                            left = (fl + centerMix + (bl + sl) * 0.5f + lfeMix) * 0.6f
                            right = (fr + centerMix + (br + sr) * 0.5f + lfeMix) * 0.6f
                        }
                        else -> {
                            val fc = src.getShort(inOffset + 4).toFloat() * 0.70710677f
                            left = (fl + fc) * 0.70710677f
                            right = (fr + fc) * 0.70710677f
                        }
                    }

                    val sL = left.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val sR = right.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    dst.putShort(outOffset, sL)
                    dst.putShort(outOffset + 2, sR)
                    inOffset += bytesPerFrame
                    outOffset += 4
                }
                dst.position(0)
                dst.limit(outBytes)
                dst
            }

            else -> src
        }
    }
}


