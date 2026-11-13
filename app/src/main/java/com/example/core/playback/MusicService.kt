package com.example.core.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import com.example.core.playback.extractor.AiffExtractor
import com.example.core.playback.extractor.DsfExtractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.core.audio.usb.UsbAudioDevice
import com.example.core.audio.usb.UsbAudioPermissionHelper
import com.example.core.audio.usb.UsbAudioTelemetry
import com.example.MainActivity
import com.example.SRMusicApp
import com.example.core.audio.AudioOutputApi
import com.example.core.audio.AudioTerminalLogger
import com.example.core.audio.dac.DacSampleRateMode
import com.example.core.audio.dac.DacVolumeMode
import com.example.core.audio.dac.DsdGainCompensation
import com.example.core.audio.dac.DsdOutputMode
import com.example.core.data.preferences.EqualizerPreferences
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {
    companion object {
        private const val TAG = "SRMusicAudioDiag"

        @Volatile
        var activePlayer: ExoPlayer? = null

            private set

        @Volatile
        private var instance: MusicService? = null

        fun requestAudioOutputApiChange(api: AudioOutputApi) {
            val service = instance ?: return

            Handler(service.mainLooper).post {
                service.onUserRequestedAudioOutputApiChange(api)
            }
        }

        fun requestBitPerfectChange(enabled: Boolean) {
            val service = instance ?: return

            Handler(service.mainLooper).post {
                service.onUserRequestedBitPerfectChange(enabled)
            }
        }

        fun isBitPerfectActive(): Boolean = instance?.effectiveBitPerfectActive == true

        fun isBitPerfectRequested(): Boolean = instance?.userBitPerfectEnabled == true
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaSession: MediaSession? = null

    private lateinit var player: ExoPlayer

    private var equalizerEngine: EqualizerAudioEngine? = null

    private var userSelectedAudioApi: AudioOutputApi = AudioOutputApi.AUDIOTRACK

    private var effectiveAudioApi: AudioOutputApi = AudioOutputApi.AUDIOTRACK

    private var userBitPerfectEnabled: Boolean = false

    private var effectiveBitPerfectActive: Boolean = false

    private var isRebuilding = false

    private var pendingRebuildRequested = false

    private var playerGeneration: Long = 0L

    private var activationRequestId: Long = 0L

    private var isDucked = false

    private var resumeOnFocusGain = false

    private var isPausedByAudioFocus = false

    private var currentFocusRequest: android.media.AudioFocusRequest? = null

    private var audioManager: android.media.AudioManager? = null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        val appPrefs = (application as? SRMusicApp)?.appPreferences
        val duckingEnabled = appPrefs?.getAudioFocusDucking() ?: false
        val autoResumeEnabled = appPrefs?.getAudioAutoResume() ?: true
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                val wasPlaying = if (::player.isInitialized) player.playWhenReady else false
                resumeOnFocusGain = autoResumeEnabled && (wasPlaying || resumeOnFocusGain)
                isDucked = false
                if (::player.isInitialized && player.playWhenReady) {
                    isPausedByAudioFocus = true
                    player.pause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasPlaying = if (::player.isInitialized) player.playWhenReady else false
                resumeOnFocusGain = autoResumeEnabled && (wasPlaying || resumeOnFocusGain)
                isDucked = false
                if (::player.isInitialized && player.playWhenReady) {
                    isPausedByAudioFocus = true
                    player.pause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (duckingEnabled) {
                    isDucked = true
                    if (::player.isInitialized) player.volume = 0.2f
                } else {
                    val wasPlaying = if (::player.isInitialized) player.playWhenReady else false
                    resumeOnFocusGain = autoResumeEnabled && (wasPlaying || resumeOnFocusGain)
                    isDucked = false
                    if (::player.isInitialized && player.playWhenReady) {
                        isPausedByAudioFocus = true
                        player.pause()
                    }
                }
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    if (::player.isInitialized) player.volume = 1.0f
                    isDucked = false
                }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    isPausedByAudioFocus = false
                    if (::player.isInitialized) {
                        player.volume = 1.0f
                        player.play()
                    }
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = currentFocusRequest ?: run {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(!((application as? SRMusicApp)?.appPreferences?.getAudioFocusDucking() ?: false))
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build().also { currentFocusRequest = it }
            }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentFocusRequest?.let { req ->
                am.abandonAudioFocusRequest(req)
            }
            currentFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        if (isDucked && ::player.isInitialized) {
            player.volume = 1.0f
            isDucked = false
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                Log.i(TAG, "ACTION_USB_DEVICE_DETACHED received")

                AudioTerminalLogger.log("ACTION_USB_DEVICE_DETACHED received. Cleared Bit Perfect routing.")

                userBitPerfectEnabled = false

                val eqPrefs = EqualizerPreferences(this@MusicService)

                val currentConfig = eqPrefs.loadEqualizerConfig()

                if (currentConfig.isBitPerfectEnabled) {
                    eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
                }

                if (effectiveBitPerfectActive) {
                    onUsbStreamUnavailable(reason = "DETACHED")
                }
            } else if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                Log.i(TAG, "ACTION_USB_DEVICE_ATTACHED received")

                AudioTerminalLogger.log("ACTION_USB_DEVICE_ATTACHED received")

                if (userBitPerfectEnabled && !effectiveBitPerfectActive) {
                    val deviceManager = UsbAudioDevice.getInstance(this@MusicService)

                    val usbDevice = deviceManager.findUsbAudioDevice()

                    if (usbDevice != null) {
                        if (deviceManager.hasPermission(usbDevice)) {
                            Log.i(TAG, "USB_ATTACH_RECONNECT: Permission already granted; activating Bit Perfect")

                            rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
                        } else {
                            val reqId = ++activationRequestId

                            deviceManager.requestPermission(usbDevice) { granted ->
                                Handler(mainLooper).post {
                                    if (reqId == activationRequestId && userBitPerfectEnabled) {
                                        if (granted) {
                                            Log.i(TAG, "USB_ATTACH_PERMISSION_GRANTED: Activating direct USB Exclusive")

                                            rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
                                        } else {
                                            Log.w(TAG, "USB_ATTACH_BLOCKED reason=PERMISSION_DENIED")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onUserRequestedAudioOutputApiChange(api: AudioOutputApi) {
        userSelectedAudioApi = api

        Log.i(TAG, "USER_SELECT_AUDIO_API: $api")

        AudioTerminalLogger.log("AUDIO_OUTPUT_SWITCH: Backend changed to ${api.label} (${api.name})")

        AudioTerminalLogger.logDiag(TAG, "I", "USER_SELECT_AUDIO_API: $api")

        rebuildPlayer(api, isBitPerfect = effectiveBitPerfectActive)
    }

    private fun onUserRequestedBitPerfectChange(enabled: Boolean) {
        userBitPerfectEnabled = enabled

        val reqId = ++activationRequestId

        Log.i(TAG, "BITPERFECT_REQUEST enabled=$enabled")

        AudioTerminalLogger.logDiag(TAG, "I", "BITPERFECT_REQUEST enabled=$enabled")

        if (enabled) {
            val currentMedia = activePlayer?.currentMediaItem
            val currentUri = currentMedia?.localConfiguration?.uri?.toString() ?: ""
            val currentTitle = currentMedia?.mediaMetadata?.title?.toString() ?: ""
            val isCurrentDsd = currentUri.endsWith(".dsf", ignoreCase = true) ||
                    currentUri.endsWith(".dff", ignoreCase = true) ||
                    currentTitle.endsWith(".dsf", ignoreCase = true) ||
                    currentTitle.endsWith(".dff", ignoreCase = true)

            if (isCurrentDsd) {
                Handler(mainLooper).post {
                    Toast.makeText(this@MusicService, "Cannot resample in bit perfect mode!", Toast.LENGTH_SHORT).show()
                }
                userBitPerfectEnabled = false
                effectiveBitPerfectActive = false
                val eqPrefs = EqualizerPreferences(this)
                val currentConfig = eqPrefs.loadEqualizerConfig()
                if (currentConfig.isBitPerfectEnabled) {
                    eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
                }
                return
            }

            val deviceManager = UsbAudioDevice.getInstance(this)

            val usbDevice = deviceManager.findUsbAudioDevice()

            if (usbDevice == null) {
                Log.w(TAG, "BITPERFECT_BLOCKED reason=NO_DAC: No USB DAC connected")

                userBitPerfectEnabled = false

                effectiveBitPerfectActive = false

                val eqPrefs = EqualizerPreferences(this)

                val currentConfig = eqPrefs.loadEqualizerConfig()

                if (currentConfig.isBitPerfectEnabled) {
                    eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
                }

                if (effectiveBitPerfectActive) {
                    rebuildPlayer(userSelectedAudioApi, isBitPerfect = false, force = true)
                }

                return
            }

            if (!deviceManager.hasPermission(usbDevice)) {
                val eqPrefsPending = EqualizerPreferences(this)
                val pendingConfig = eqPrefsPending.loadEqualizerConfig()
                if (pendingConfig.isBitPerfectEnabled) {
                    eqPrefsPending.saveEqualizerConfig(pendingConfig.copy(isBitPerfectEnabled = false))
                }

                deviceManager.requestPermission(usbDevice) { granted ->
                    Handler(mainLooper).post {
                        if (reqId == activationRequestId && userBitPerfectEnabled) {
                            if (granted) {
                                Log.i(TAG, "BITPERFECT_PERMISSION_GRANTED: Activating direct USB Exclusive")

                                val eqPrefs = EqualizerPreferences(this@MusicService)
                                val currentConfig = eqPrefs.loadEqualizerConfig()
                                if (!currentConfig.isBitPerfectEnabled) {
                                    eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = true))
                                }

                                rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
                            } else {
                                Log.w(TAG, "BITPERFECT_BLOCKED reason=PERMISSION_DENIED")

                                userBitPerfectEnabled = false

                                val wasActive = effectiveBitPerfectActive
                                effectiveBitPerfectActive = false

                                val eqPrefs = EqualizerPreferences(this@MusicService)

                                val currentConfig = eqPrefs.loadEqualizerConfig()

                                if (currentConfig.isBitPerfectEnabled) {
                                    eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
                                }

                                if (wasActive) {
                                    rebuildPlayer(userSelectedAudioApi, isBitPerfect = false, force = true)
                                }
                            }
                        } else {
                            Log.i(TAG, "BITPERFECT_REQUEST cancelled or superseded before permission response; ignoring")
                        }
                    }
                }

                return
            }

            val eqPrefs = EqualizerPreferences(this)
            val currentConfig = eqPrefs.loadEqualizerConfig()
            if (!currentConfig.isBitPerfectEnabled) {
                eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = true))
            }

            rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
        } else {
            userBitPerfectEnabled = false
            effectiveBitPerfectActive = false
            val eqPrefs = EqualizerPreferences(this)
            val currentConfig = eqPrefs.loadEqualizerConfig()
            if (currentConfig.isBitPerfectEnabled) {
                eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
            }
            rebuildPlayer(userSelectedAudioApi, isBitPerfect = false, force = true)
            UsbAudioDevice.getInstance(this).closeDevice()
        }
    }

    private fun onUsbStreamUnavailable(reason: String = "CONFIGURE_FAILED") {
        Log.w(TAG, "USB_FALLBACK reason=$reason; falling back to normal output $userSelectedAudioApi")

        effectiveBitPerfectActive = false

        if (::player.isInitialized) {
            rebuildPlayer(userSelectedAudioApi, isBitPerfect = false, force = true)
        }
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(api: AudioOutputApi, isBitPerfect: Boolean): ExoPlayer {
        ++playerGeneration

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val appPrefs = (application as? SRMusicApp)?.appPreferences

        val dacVolumeMode = appPrefs?.getDacVolumeMode() ?: DacVolumeMode.AUTO
        val dsdOutputMode = appPrefs?.getDsdOutputMode() ?: DsdOutputMode.AUTO
        val dsdGainCompensation = appPrefs?.getDsdGainCompensation() ?: DsdGainCompensation.PLUS_0DB
        val usbBufferSize = appPrefs?.getDacUsbBufferSize() ?: 256
        val dacSampleRateMode = appPrefs?.getDacSampleRateMode() ?: DacSampleRateMode.FOLLOW_SOURCE
        val dacFixedSampleRate = appPrefs?.getDacFixedSampleRate() ?: 192000
        val highPrecisionResampling = appPrefs?.getHighPrecisionResampling() ?: false
        val gaplessPlayback = appPrefs?.getGaplessPlayback() ?: false

        val renderersFactory = FfmpegRenderersFactory(
            context = this,
            audioOutputApi = api,
            isBitPerfectEnabled = isBitPerfect,
            dacVolumeMode = dacVolumeMode,
            dacExclusiveUsb = isBitPerfect,
            dsdOutputMode = dsdOutputMode,
            dsdGainCompensation = dsdGainCompensation,
            usbBufferSize = usbBufferSize,
            dacSampleRateMode = dacSampleRateMode,
            dacFixedSampleRate = dacFixedSampleRate,
            isHighPrecisionResamplingEnabled = highPrecisionResampling,
            isGaplessEnabled = gaplessPlayback,
            onInputFormatConfigured = { format ->
                if (format.sampleRate > 0) {
                    com.example.core.audio.usb.UsbSourceTrackFormat.update(
                        bitDepth = 0,
                        sampleRate = format.sampleRate
                    )
                }
                Log.d(
                    TAG,
                    "DECODER_OUTPUT_PCM: rate=${format.sampleRate} ch=${format.channelCount} enc=${format.pcmEncoding}"
                )

                AudioTerminalLogger.logDiag(TAG, "D", "DECODER_OUTPUT_PCM: rate=${format.sampleRate} ch=${format.channelCount} enc=${format.pcmEncoding}")
            },
            onEffectiveOutputConfigured = { outputConfig ->
                Log.d(
                    TAG,
                    "EFFECTIVE_OUTPUT_CONFIG: rate=${outputConfig.sampleRate} mask=${outputConfig.channelMask} enc=${outputConfig.encoding}"
                )

                AudioTerminalLogger.logDiag(TAG, "D", "EFFECTIVE_OUTPUT_CONFIG: rate=${outputConfig.sampleRate} mask=${outputConfig.channelMask} enc=${outputConfig.encoding}")
            },
            onBackendFailure = { failedApi, reason ->
                Handler(mainLooper).post {
                    if (effectiveAudioApi == failedApi && failedApi != AudioOutputApi.AUDIOTRACK) {
                        Log.w(
                            TAG,
                            "FALLBACK requested=$failedApi reason=$reason; rebuilding to AudioTrack"
                        )

                        rebuildPlayer(AudioOutputApi.AUDIOTRACK, isBitPerfect = false, force = true)
                    }
                }
            },
            onUsbOutputUnavailable = {
                Handler(mainLooper).post {
                    onUsbStreamUnavailable(reason = "CONFIGURE_FAILED")
                }
            },
            onUsbOutputActive = { sampleRate, channelCount, bitDepth, deviceName, volMode, gain ->
                Handler(mainLooper).post {
                    effectiveBitPerfectActive = true

                    Log.i(
                        TAG,
                        "USB_STREAM_ACTIVE rate=$sampleRate ch=$channelCount bits=$bitDepth device=$deviceName volMode=$volMode gain=$gain"
                    )
                }
            }
        )

        val extractorsFactory = ExtractorsFactory {
            arrayOf(
                DsfExtractor(),
                AiffExtractor(),
                *DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true).createExtractors()
            )
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(
            this,
            extractorsFactory
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,
                60_000,
                1_000,
                2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .experimentalSetDynamicSchedulingEnabled(true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1.0f
                preloadConfiguration = ExoPlayer.PreloadConfiguration(
                    if (gaplessPlayback) 5_000_000L else C.TIME_UNSET
                )
            }
    }

    private fun setupPlayerListener(exoPlayer: ExoPlayer, eqEngine: EqualizerAudioEngine) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    isPausedByAudioFocus = false
                    resumeOnFocusGain = false
                    requestAudioFocus()
                } else {
                    if (isPausedByAudioFocus) {
                        isPausedByAudioFocus = false
                    } else {
                        resumeOnFocusGain = false
                        abandonAudioFocus()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val uri = mediaItem.localConfiguration?.uri?.toString() ?: ""
                    val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
                    val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown"
                    val isDsd = uri.endsWith(".dsf", ignoreCase = true) ||
                            uri.endsWith(".dff", ignoreCase = true) ||
                            uri.endsWith(".dsd", ignoreCase = true) ||
                            title.endsWith(".dsf", ignoreCase = true) ||
                            title.endsWith(".dff", ignoreCase = true) ||
                            title.endsWith(".dsd", ignoreCase = true)
                    if (isDsd && (effectiveBitPerfectActive || userBitPerfectEnabled)) {
                        Handler(mainLooper).post {
                            Toast.makeText(this@MusicService, "Cannot resample in bit perfect mode!", Toast.LENGTH_SHORT).show()
                            onUserRequestedBitPerfectChange(false)
                        }
                    }
                    val bitDepth = mediaItem.mediaMetadata.extras?.getInt("bitDepth", 0) ?: 0
                    if (bitDepth in listOf(16, 24, 32)) {
                        com.example.core.audio.usb.UsbSourceTrackFormat.update(bitDepth)
                    } else {
                        val songId = mediaItem.mediaId.toLongOrNull()
                        val cachedBits = songId?.let { com.example.core.data.mediastore.tags.TaglibHelper.getCachedProps(it)?.bitDepth } ?: 0
                        if (cachedBits in listOf(16, 24, 32)) {
                            com.example.core.audio.usb.UsbSourceTrackFormat.update(cachedBits)
                        }
                    }
                    AudioTerminalLogger.log("Selected track: '$title' ($artist)")
                    AudioTerminalLogger.logDiag(TAG, "I", "PLAYBACK_STATE songId=${mediaItem.mediaId} title='$title' bits=$bitDepth")
                }
            }

            @OptIn(UnstableApi::class)
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    eqEngine.attachToSession(audioSessionId)
                }
            }

            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sid = exoPlayer.audioSessionId
                    if (sid != 0 && sid != C.AUDIO_SESSION_ID_UNSET) {
                        eqEngine.attachToSession(sid)
                    }
                    Log.i(
                        TAG,
                        "PLAYER_READY backend=$effectiveAudioApi isBitPerfect=$effectiveBitPerfectActive audioSessionId=$sid posMs=${exoPlayer.currentPosition}"
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "PLAYER_ERROR backend=$effectiveAudioApi errorCode=${error.errorCode} message=${error.message}",
                    error
                )
            }
        })
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        instance = this

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

        val eqPrefs = EqualizerPreferences(this)

        val eqConfig = eqPrefs.loadEqualizerConfig()

        val initialApi = eqConfig.selectedAudioOutputApi

        var initialBitPerfect = eqConfig.isBitPerfectEnabled

        val dacDevice = UsbAudioDevice.getInstance(this).findUsbAudioDevice()

        if (initialBitPerfect && dacDevice == null) {
            initialBitPerfect = false

            eqPrefs.saveEqualizerConfig(eqConfig.copy(isBitPerfectEnabled = false))
        }

        userSelectedAudioApi = initialApi

        effectiveAudioApi = initialApi

        userBitPerfectEnabled = initialBitPerfect

        val startWithBitPerfect = initialBitPerfect && dacDevice != null && UsbAudioDevice.getInstance(this).hasPermission(dacDevice)

        effectiveBitPerfectActive = false

        Log.i(TAG, "INIT_PLAYER initialApi=$initialApi bitPerfectRequested=$initialBitPerfect attemptBitPerfect=$startWithBitPerfect")

        val eqEngine = EqualizerAudioEngine(eqPrefs)

        equalizerEngine = eqEngine

        player = createPlayer(initialApi, startWithBitPerfect)

        setupPlayerListener(player, eqEngine)

        activePlayer = player

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        UsbAudioPermissionHelper.onDeviceAvailable = { device ->
            Handler(mainLooper).post {
                if (userBitPerfectEnabled && !effectiveBitPerfectActive) {
                    Log.i(TAG, "USB_RECONNECT_ATTEMPT: DAC attached (${device.productName}); attempting Bit Perfect activation")

                    rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
                }
            }
        }

        val appPrefs = (application as? SRMusicApp)?.appPreferences

        var previousVolMode: DacVolumeMode? = null

        serviceScope.launch {
            appPrefs?.dacVolumeModeFlow?.collect { mode ->
                if (previousVolMode == null) {
                    previousVolMode = mode

                    return@collect
                }

                if (previousVolMode == mode) return@collect

                previousVolMode = mode

                if (effectiveBitPerfectActive) {
                    Log.i(TAG, "DAC volume mode changed to $mode while Bit Perfect active; rebuilding player to apply")

                    rebuildPlayer(userSelectedAudioApi, isBitPerfect = true, force = true)
                }
            }
        }

        var previousHighPrecision: Boolean? = null

        serviceScope.launch {
            appPrefs?.highPrecisionResamplingFlow?.collect { enabled ->
                if (previousHighPrecision == null) {
                    previousHighPrecision = enabled
                    return@collect
                }
                if (previousHighPrecision == enabled) return@collect
                previousHighPrecision = enabled
                Log.i(TAG, "High precision resampling changed to $enabled; rebuilding player")
                rebuildPlayer(userSelectedAudioApi, isBitPerfect = effectiveBitPerfectActive, force = true)
            }
        }

        var previousGapless: Boolean? = null

        serviceScope.launch {
            appPrefs?.gaplessPlaybackFlow?.collect { enabled ->
                if (previousGapless == null) {
                    previousGapless = enabled
                    return@collect
                }
                if (previousGapless == enabled) return@collect
                previousGapless = enabled
                Log.i(TAG, "Gapless playback changed to $enabled; rebuilding player")
                rebuildPlayer(userSelectedAudioApi, isBitPerfect = effectiveBitPerfectActive, force = true)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player

        val eqPrefs = EqualizerPreferences(this)
        val currentConfig = eqPrefs.loadEqualizerConfig()
        if (currentConfig.isBitPerfectEnabled || effectiveBitPerfectActive) {
            Log.i(TAG, "onTaskRemoved: App closed with Bit-Perfect active. Disabling Bit-Perfect and stopping service.")
            userBitPerfectEnabled = false
            effectiveBitPerfectActive = false
            eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
            p?.stop()
            stopSelf()
            return
        }

        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    @OptIn(UnstableApi::class)
    fun rebuildPlayer(api: AudioOutputApi, isBitPerfect: Boolean, force: Boolean = false) {
        if (isRebuilding) {
            Log.d(TAG, "rebuildPlayer already in progress, queuing pending rebuild request (api=$api, bitPerfect=$isBitPerfect)")

            pendingRebuildRequested = true

            return
        }

        if (!force && api == effectiveAudioApi && isBitPerfect == effectiveBitPerfectActive && ::player.isInitialized) {
            Log.d(TAG, "SWITCH ignored: API unchanged ($api) and BitPerfect unchanged ($isBitPerfect)")

            return
        }

        isRebuilding = true

        try {
            Log.i(TAG, "SWITCH_START requestedApi=$api bitPerfect=$isBitPerfect force=$force (previous api=$effectiveAudioApi bitPerfect=$effectiveBitPerfectActive)")

            AudioTerminalLogger.logDiag(TAG, "I", "SWITCH_START requestedApi=$api bitPerfect=$isBitPerfect force=$force (previous api=$effectiveAudioApi bitPerfect=$effectiveBitPerfectActive)")

            val startTimeMs = SystemClock.elapsedRealtime()

            val oldPlayer = player

            val session = mediaSession ?: return

            val count = oldPlayer.mediaItemCount

            val mediaItems = ArrayList<MediaItem>(count)

            for (i in 0 until count) {
                mediaItems.add(oldPlayer.getMediaItemAt(i))
            }

            val currentIndex = oldPlayer.currentMediaItemIndex

            val currentPosition = oldPlayer.currentPosition

            val playWhenReady = oldPlayer.playWhenReady

            val playbackState = oldPlayer.playbackState

            val repeatMode = oldPlayer.repeatMode

            val shuffleModeEnabled = oldPlayer.shuffleModeEnabled

            val playbackParameters = oldPlayer.playbackParameters

            oldPlayer.stop()

            oldPlayer.release()

            if (!isBitPerfect) {
                effectiveBitPerfectActive = false
                UsbAudioDevice.getInstance(this).closeDevice()
            }

            val newPlayer = createPlayer(api, isBitPerfect)

            newPlayer.volume = 1.0f
            isDucked = false

            session.setPlayer(newPlayer)

            newPlayer.repeatMode = repeatMode

            newPlayer.shuffleModeEnabled = shuffleModeEnabled

            newPlayer.playbackParameters = playbackParameters

            if (mediaItems.isNotEmpty()) {
                newPlayer.setMediaItems(mediaItems, currentIndex, currentPosition)

                if (playbackState != Player.STATE_IDLE) {
                    newPlayer.prepare()
                }
            }

            newPlayer.playWhenReady = playWhenReady

            equalizerEngine?.let { setupPlayerListener(newPlayer, it) }

            player = newPlayer

            activePlayer = newPlayer

            effectiveAudioApi = api

            if (!isBitPerfect) {
                effectiveBitPerfectActive = false
            }

            val elapsedMs = SystemClock.elapsedRealtime() - startTimeMs

            Log.i(
                TAG,
                "SWITCH_DONE newEffectiveBackend=$api requestedBitPerfect=$isBitPerfect items=${newPlayer.mediaItemCount} index=${newPlayer.currentMediaItemIndex} posMs=${newPlayer.currentPosition} playWhenReady=$playWhenReady elapsedMs=$elapsedMs"
            )

            AudioTerminalLogger.logDiag(TAG, "I", "SWITCH_DONE newEffectiveBackend=$api requestedBitPerfect=$isBitPerfect items=${newPlayer.mediaItemCount} index=${newPlayer.currentMediaItemIndex} posMs=${newPlayer.currentPosition} playWhenReady=$playWhenReady elapsedMs=$elapsedMs")
        } finally {
            isRebuilding = false

            if (pendingRebuildRequested) {
                pendingRebuildRequested = false

                val targetBitPerfect = userBitPerfectEnabled && UsbAudioDevice.getInstance(this).let {
                    val d = it.findUsbAudioDevice()

                    d != null && it.hasPermission(d)
                }

                Log.i(TAG, "Executing queued rebuild with latest user state: api=$userSelectedAudioApi bitPerfect=$targetBitPerfect")

                Handler(mainLooper).post {
                    rebuildPlayer(userSelectedAudioApi, isBitPerfect = targetBitPerfect, force = true)
                }
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }

        serviceScope.cancel()

        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}

        UsbAudioPermissionHelper.onDeviceAvailable = null

        equalizerEngine?.destroy()

        equalizerEngine = null

        activePlayer = null

        mediaSession?.run {
            player.release()

            release()

            mediaSession = null
        }

        val eqPrefs = EqualizerPreferences(this)
        val currentConfig = eqPrefs.loadEqualizerConfig()
        if (currentConfig.isBitPerfectEnabled) {
            eqPrefs.saveEqualizerConfig(currentConfig.copy(isBitPerfectEnabled = false))
        }

        userBitPerfectEnabled = false
        effectiveBitPerfectActive = false
        UsbAudioDevice.getInstance(this).closeDevice()
        UsbAudioTelemetry.updateStreaming(false, 0, 0)

        abandonAudioFocus()

        super.onDestroy()
    }
}
