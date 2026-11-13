package com.example.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import com.example.ui.common.components.SongInfoBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.player.components.rememberAudioOutputInfo
import com.example.core.model.NowPlayingTheme
import com.example.core.model.PlaybackProgress
import com.example.core.util.ArtworkPaletteExtractor
import com.example.ui.player.components.PlayerAudioPill
import com.example.ui.player.components.PlayerBottomActions
import com.example.ui.player.components.PlayerControlsRow
import com.example.ui.player.components.PlayerQueueSheet
import com.example.ui.player.core.PlayerAction
import com.example.ui.player.core.PlayerUiState
import com.example.core.model.NowPlayingArtworkStyle
import com.example.ui.player.style.artwork.FullArtworkStyle
import com.example.ui.player.style.progress.PlayerProgressContainer
import com.example.ui.player.style.NowPlayingThemeContainer
import com.example.ui.player.style.PlayerTrackInfo
import androidx.compose.runtime.DisposableEffect
import com.example.ui.player.components.AudioOutputPipelineSheet
import com.example.core.data.preferences.EqualizerPreferences
import com.example.ui.equalizer.core.EqualizerUiState
import com.example.core.data.mediastore.tags.TaglibHelper
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    playbackProgressFlow: StateFlow<PlaybackProgress>? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    hideArtwork: Boolean = false,
    expansionProgress: Float = 1f,
    onArtworkCoordinatesMeasured: ((LayoutCoordinates) -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null,
    circleAngle: Float = 0f,
    onCircleAngleChange: (Float) -> Unit = {},
    isCircleSpinning: Boolean = false,
    onCircleSpinningChange: (Boolean) -> Unit = {},
    circleSpinDirection: Float = 1f,
    onCircleSpinDirectionChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val song = uiState.currentSong ?: return

    var showInfoDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAudioOutput by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }

    val audioOutputInfo = rememberAudioOutputInfo()
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f

    val eqPrefs = remember(context) { EqualizerPreferences(context) }
    var eqConfig by remember { mutableStateOf(eqPrefs.loadEqualizerConfig()) }

    DisposableEffect(eqPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            eqConfig = eqPrefs.loadEqualizerConfig()
        }
        eqPrefs.registerChangeListener(listener)
        onDispose { eqPrefs.unregisterChangeListener(listener) }
    }

    val equalizerUiState = remember(eqConfig, audioOutputInfo) {
        EqualizerUiState(
            isBitPerfectEnabled = eqConfig.isBitPerfectEnabled,
            isDacConnected = audioOutputInfo.isUsbDac || com.example.core.audio.AudioOutputDetector.isDacConnected(context),
            isEqualizerEnabled = eqConfig.isEqualizerEnabled,
            selectedAudioOutputApi = eqConfig.selectedAudioOutputApi,
            selectedPreset = eqConfig.selectedPreset,
            preampGain = eqConfig.preampGain,
            bassBoost = eqConfig.bassBoost,
            trebleBoost = eqConfig.trebleBoost
        )
    }

    val artworkColorsState = produceState(
        ArtworkPaletteExtractor.defaultColors(
            primary = MaterialTheme.colorScheme.primary,
            darkBg = MaterialTheme.colorScheme.background,
            lightBg = MaterialTheme.colorScheme.background,
            isDark = isDarkTheme
        ),
        song.id,
        uiState.nowPlayingTheme,
        isDarkTheme,
        uiState.isArtworkAccent,
        uiState.artworkStyle
    ) {
        if ((uiState.nowPlayingTheme != NowPlayingTheme.DEFAULT && uiState.isArtworkAccent) ||
            uiState.artworkStyle == NowPlayingArtworkStyle.FULL
        ) {
            value = ArtworkPaletteExtractor.extractColors(context, song, isDarkTheme)
        }
    }

    val isFullArtworkStyle = uiState.artworkStyle == NowPlayingArtworkStyle.FULL
    val isExpandedFullStyle = expansionProgress > 0.5f && isFullArtworkStyle
    val view = LocalView.current

    DisposableEffect(isExpandedFullStyle, isDarkTheme) {
        var hostContext: Context? = view.context
        while (hostContext is ContextWrapper) {
            if (hostContext is Activity) break
            hostContext = hostContext.baseContext
        }
        val window = (hostContext as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = if (isExpandedFullStyle) false else !isDarkTheme
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }
        }
    }

    val showThemeBackground = uiState.nowPlayingTheme != NowPlayingTheme.DEFAULT && expansionProgress > 0.001f
    val themeBackgroundAlpha = if (showThemeBackground) expansionProgress.coerceIn(0f, 1f) else 0f
    val isDynamicTheme = uiState.nowPlayingTheme != NowPlayingTheme.DEFAULT && uiState.isArtworkAccent

    val defaultActiveProgress = MaterialTheme.colorScheme.primary
    val defaultInactiveProgress = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val defaultPlayButtonColor = MaterialTheme.colorScheme.primaryContainer
    val defaultOnPlayButtonColor = MaterialTheme.colorScheme.onPrimaryContainer
    val defaultIconTintColor = MaterialTheme.colorScheme.onSurface
    val defaultActiveAccent = MaterialTheme.colorScheme.primary

    val targetActiveProgress = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultActiveProgress, artworkColorsState.value.activeProgress, expansionProgress.coerceIn(0f, 1f))
    } else defaultActiveProgress

    val targetInactiveProgress = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultInactiveProgress, artworkColorsState.value.inactiveProgress, expansionProgress.coerceIn(0f, 1f))
    } else defaultInactiveProgress

    val targetPlayButtonColor = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultPlayButtonColor, artworkColorsState.value.controlButton, expansionProgress.coerceIn(0f, 1f))
    } else defaultPlayButtonColor

    val targetOnPlayButtonColor = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultOnPlayButtonColor, artworkColorsState.value.onControlButton, expansionProgress.coerceIn(0f, 1f))
    } else defaultOnPlayButtonColor

    val targetIconTintColor = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultIconTintColor, artworkColorsState.value.controlIcon, expansionProgress.coerceIn(0f, 1f))
    } else defaultIconTintColor

    val targetActiveAccentColor = if (isDynamicTheme && showThemeBackground) {
        lerp(defaultActiveAccent, artworkColorsState.value.primary, expansionProgress.coerceIn(0f, 1f))
    } else defaultActiveAccent

    val dynamicActiveProgress by animateColorAsState(targetActiveProgress, tween(380), label = "activeProgress")
    val dynamicInactiveProgress by animateColorAsState(targetInactiveProgress, tween(380), label = "inactiveProgress")
    val dynamicPlayButtonColor by animateColorAsState(targetPlayButtonColor, tween(380), label = "playButton")
    val dynamicOnPlayButtonColor by animateColorAsState(targetOnPlayButtonColor, tween(380), label = "onPlayButton")
    val dynamicIconTintColor by animateColorAsState(targetIconTintColor, tween(380), label = "iconTint")
    val dynamicActiveAccentColor by animateColorAsState(targetActiveAccentColor, tween(380), label = "activeAccent")

    val collapseIconTint = if (isFullArtworkStyle && expansionProgress > 0.5f) {
        Color.White
    } else {
        dynamicIconTintColor
    }

    val streamPropsState = produceState<com.example.core.data.mediastore.tags.AudioStreamProps?>(
        initialValue = null,
        key1 = song.id
    ) {
        value = withContext(Dispatchers.IO) {
            TaglibHelper.getAudioStreamProps(song, context)
        }
    }

    val audioPillText = remember(song.id, uiState.technicalInfo, streamPropsState.value) {
        val techInfo = uiState.technicalInfo
        val streamProps = streamPropsState.value
        val sampleRateFormatted = streamProps?.formatSampleRate() ?: techInfo?.sampleRate?.takeIf { it.isNotBlank() }?.lowercase()
        val bitDepthStr = streamProps?.bitDepth?.let { "${it} bit" } ?: techInfo?.bitDepth?.takeIf { it.isNotBlank() }?.replace("-bit", " bit")

        if (techInfo != null && techInfo.bitrate.isNotBlank() && sampleRateFormatted != null) {
            val format = if (techInfo.format.contains("aac", ignoreCase = true)) "M4A" else techInfo.format.uppercase()
            val parts = mutableListOf<String>()
            parts.add(format)
            if (!bitDepthStr.isNullOrBlank()) parts.add(bitDepthStr)
            parts.add(techInfo.bitrate.lowercase())
            parts.add(sampleRateFormatted)
            parts.joinToString(" · ")
        } else if (streamProps != null) {
            val parts = mutableListOf<String>()
            parts.add(streamProps.format)
            if (!bitDepthStr.isNullOrBlank()) parts.add(bitDepthStr)
            if (streamProps.bitrateKbps != null && streamProps.bitrateKbps > 0) {
                parts.add("${streamProps.bitrateKbps} kbps")
            }
            if (sampleRateFormatted != null) {
                parts.add(sampleRateFormatted)
            }
            parts.joinToString(" · ")
        } else {
            var format = song.displayName?.let { dn ->
                if (dn.contains('.')) dn.substringAfterLast('.').uppercase() else null
            } ?: song.dataPath?.let { dp ->
                if (dp.contains('.')) dp.substringAfterLast('.').uppercase() else null
            } ?: "MP3"

            if (format.contains("AAC", ignoreCase = true) || format.contains("MP4", ignoreCase = true)) {
                format = "M4A"
            }

            if (song.duration > 0 && (song.size ?: 0L) > 0) {
                val calculatedKbps = (((song.size ?: 0L) * 8) / song.duration).toInt()
                "$format · ${calculatedKbps}kb/s"
            } else {
                format
            }
        }
    }

    val isPlaying = uiState.playbackInfo.isPlaying

    val artworkScale = 1.0f
    val artworkCornerRadius = 24.dp
    val artworkElevation = 12.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
    ) {
        if (uiState.nowPlayingTheme != NowPlayingTheme.DEFAULT && containerColor != Color.Transparent && themeBackgroundAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = themeBackgroundAlpha }
            ) {
                NowPlayingThemeContainer(
                    theme = uiState.nowPlayingTheme,
                    song = song,
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
        }

        if (isFullArtworkStyle) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (hideArtwork) 0f else 1f }
            ) {
                val fullArtworkHeight = (maxWidth * 1.18f).coerceAtLeast(400.dp)
                Crossfade(
                    targetState = song,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "full_artwork_crossfade"
                ) { currentArtworkSong ->
                    FullArtworkStyle(
                        song = currentArtworkSong,
                        containerColor = containerColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(fullArtworkHeight)
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("now_playing_screen"),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isFullArtworkStyle && expansionProgress > 0.05f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .graphicsLayer { alpha = expansionProgress }
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.60f),
                                            Color.Black.copy(alpha = 0.28f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    TopAppBar(
                        title = {},
                        windowInsets = WindowInsets.statusBars,
                        navigationIcon = {
                            IconButton(
                                onClick = { onAction(PlayerAction.Collapse) },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .testTag("now_playing_dismiss")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse Now Playing",
                                    tint = collapseIconTint,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        actions = {},
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val availableHeight = maxHeight - navBarBottom
                val isCompact = availableHeight < 560.dp
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .then(if (isCompact) Modifier.verticalScroll(scrollState) else Modifier)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (isCompact) Arrangement.spacedBy(14.dp) else Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    val measuredModifier = if (onArtworkCoordinatesMeasured != null) {
                        Modifier.onGloballyPositioned { coordinates ->
                            onArtworkCoordinatesMeasured(coordinates)
                        }
                    } else Modifier

                    val artworkModifier = if (isCompact) {
                        val artworkSize = (availableHeight * 0.38f).coerceIn(120.dp, 240.dp)
                        Modifier
                            .size(artworkSize)
                            .aspectRatio(1f)
                    } else {
                        Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                    }

                    if (isFullArtworkStyle) {
                        val placeholderHeight = if (isCompact) {
                            (availableHeight * 0.30f).coerceIn(100.dp, 180.dp)
                        } else {
                            (availableHeight * 0.40f).coerceIn(240.dp, 360.dp)
                        }
                        Spacer(modifier = Modifier.height(placeholderHeight))
                    } else {
                        Box(
                            modifier = artworkModifier.then(measuredModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            val shadowColor = dynamicActiveAccentColor?.copy(alpha = if (isPlaying) 0.42f else 0.2f)
                                ?: MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.32f else 0.15f)

                            Crossfade(
                                targetState = song,
                                animationSpec = tween(350, easing = FastOutSlowInEasing),
                                label = "artwork_container_crossfade"
                            ) { currentArtworkSong ->
                                com.example.ui.player.style.artwork.PlayerArtworkContainer(
                                    song = currentArtworkSong,
                                    style = uiState.artworkStyle,
                                    isPlaying = isPlaying,
                                    cornerRadius = artworkCornerRadius,
                                    elevation = artworkElevation,
                                    shadowColor = shadowColor,
                                    containerColor = containerColor,
                                    currentAngle = circleAngle,
                                    onAngleChange = onCircleAngleChange,
                                    isSpinningActive = isCircleSpinning,
                                    onSpinningChange = onCircleSpinningChange,
                                    spinDirection = circleSpinDirection,
                                    onSpinDirectionChange = onCircleSpinDirectionChange,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = if (hideArtwork) 0f else 1f
                                            scaleX = artworkScale
                                            scaleY = artworkScale
                                        }
                                )
                            }
                        }
                    }

                    if (!isCompact && !isFullArtworkStyle) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    PlayerTrackInfo(
                        song = song,
                        alignment = uiState.trackInfoAlignment
                    )

                    NowPlayingProgressSection(
                        playbackProgressFlow = playbackProgressFlow,
                        fallbackProgress = uiState.playbackProgress,
                        isPlaying = uiState.playbackInfo.isPlaying,
                        progressStyle = uiState.progressStyle,
                        onSeek = { targetMs -> onAction(PlayerAction.SeekTo(targetMs)) },
                        song = song,
                        nextSong = uiState.nextSong,
                        activeColor = dynamicActiveProgress,
                        inactiveColor = dynamicInactiveProgress
                    )

                    PlayerControlsRow(
                        isShuffle = uiState.isShuffle,
                        repeatMode = uiState.repeatMode,
                        playbackInfo = uiState.playbackInfo,
                        onAction = onAction,
                        playButtonColor = dynamicActiveProgress,
                        onPlayButtonColor = if (dynamicActiveProgress.luminance() > 0.5f) Color.Black else Color.White,
                        iconTintColor = dynamicIconTintColor,
                        activeAccentColor = dynamicActiveAccentColor
                    )

                    PlayerAudioPill(
                        audioPillText = if (showAudioOutput) audioOutputInfo.displayName else audioPillText,
                        pillBgColor = dynamicPlayButtonColor,
                        pillTextColor = dynamicOnPlayButtonColor,
                        onClick = { showAudioOutput = !showAudioOutput },
                        onLongClick = { showAudioOutputSheet = true }
                    )

                    PlayerBottomActions(
                        onOpenLyrics = onOpenLyrics,
                        onOpenQueue = { showQueueSheet = true },
                        onNavigateToEqualizer = { onAction(PlayerAction.OpenEqualizer) },
                        onShowInfo = { showInfoDialog = true },
                        iconTintColor = dynamicIconTintColor,
                        activeAccentColor = dynamicActiveAccentColor
                    )

                    if (isCompact) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        SongInfoBottomSheet(
            song = song,
            onDismissRequest = { showInfoDialog = false }
        )
    }

    if (showQueueSheet) {
        PlayerQueueSheet(
            queue = uiState.currentQueue,
            currentSongId = song.id,
            onDismissRequest = { showQueueSheet = false },
            onSelectQueueItem = { index ->
                onAction(PlayerAction.SelectQueueItem(index))
                showQueueSheet = false
            }
        )
    }

    if (showAudioOutputSheet) {
        AudioOutputPipelineSheet(
            song = song,
            technicalInfo = uiState.technicalInfo,
            streamProps = streamPropsState.value,
            audioOutputInfo = audioOutputInfo,
            equalizerUiState = equalizerUiState,
            onDismissRequest = { showAudioOutputSheet = false }
        )
    }
}

@Composable
private fun NowPlayingProgressSection(
    playbackProgressFlow: StateFlow<PlaybackProgress>?,
    fallbackProgress: PlaybackProgress,
    isPlaying: Boolean,
    progressStyle: com.example.core.model.NowPlayingProgressStyle,
    onSeek: (Long) -> Unit,
    song: com.example.core.model.Song,
    nextSong: com.example.core.model.Song?,
    activeColor: Color,
    inactiveColor: Color
) {
    val currentProgress = if (playbackProgressFlow != null) {
        playbackProgressFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackProgress
    }

    PlayerProgressContainer(
        progress = currentProgress,
        isPlaying = isPlaying,
        progressStyle = progressStyle,
        onSeek = onSeek,
        song = song,
        nextSong = nextSong,
        activeColor = activeColor,
        inactiveColor = inactiveColor
    )
}
