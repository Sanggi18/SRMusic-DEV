package com.example.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.player.components.*
import com.example.ui.player.core.*
import com.example.ui.common.components.*
import com.example.core.model.NowPlayingArtworkStyle
import com.example.core.model.NowPlayingTheme
import com.example.ui.player.style.NowPlayingThemeContainer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlayerHost(
    uiState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    playbackProgressFlow: kotlinx.coroutines.flow.StateFlow<com.example.core.model.PlaybackProgress>? = null,
    isEqualizerOpen: Boolean = false,
    isLyricsOpen: Boolean = false,
    isSettingsOpen: Boolean = false,
    isBottomBarVisible: Boolean = true,
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToLyrics: () -> Unit = {},
    onExpansionProgressChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSong = uiState.currentSong
    if (currentSong == null || uiState.isDismissed) return

    val coroutineScope = rememberCoroutineScope()
    val expansionAnimatable = remember { Animatable(if (uiState.isExpanded) 1f else 0f) }
    val expansionProgress = expansionAnimatable.value

    LaunchedEffect(expansionProgress) {
        onExpansionProgressChanged(expansionProgress)
    }

    BackHandler(enabled = (uiState.isExpanded || expansionProgress > 0.05f) && !isLyricsOpen && !isEqualizerOpen && !isSettingsOpen) {
        coroutineScope.launch {
            expansionAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = 520f
                )
            )
            onAction(PlayerAction.Collapse)
        }
    }

    LaunchedEffect(uiState.isExpanded) {
        val target = if (uiState.isExpanded) 1f else 0f
        if (kotlin.math.abs(expansionAnimatable.value - target) > 0.02f && !expansionAnimatable.isRunning) {
            expansionAnimatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = 520f
                )
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        val screenHeightDp = maxHeight
        val screenWidthDp = maxWidth
        val density = LocalDensity.current
        val collapsedHeight = 55.dp
        val dismissOffsetY = remember { Animatable(0f) }
        val progress = expansionProgress.coerceIn(0f, 1f)
        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val targetBottomPadding = if (isBottomBarVisible) 80.dp + navBarBottomInset + 16.dp else navBarBottomInset + 12.dp

        val defaultBottomPadding by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetBottomPadding,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 380f
            ),
            label = "mini_player_bottom_padding"
        )

        var targetArtworkCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var surfaceCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var measuredTargetTopDp by remember(uiState.artworkStyle, screenWidthDp, screenHeightDp) { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }

        LaunchedEffect(progress >= 0.99f, targetArtworkCoordinates, surfaceCoordinates) {
            if (progress >= 0.99f) {
                val targetCoords = targetArtworkCoordinates
                val surfCoords = surfaceCoordinates
                if (targetCoords != null && targetCoords.isAttached && surfCoords != null && surfCoords.isAttached) {
                    val relativePos = surfCoords.localPositionOf(targetCoords, Offset.Zero)
                    val sTop = with(density) { relativePos.y.toDp() }
                    if (sTop > 0.dp) {
                        measuredTargetTopDp = if (uiState.artworkStyle == NowPlayingArtworkStyle.FULL) 0.dp else sTop
                    }
                }
            }
        }

        var circleAngle by remember { mutableFloatStateOf(0f) }
        var isCircleSpinning by remember { mutableStateOf(false) }
        var circleSpinDirection by remember { mutableFloatStateOf(1f) }
        var lastExpandedAngle by remember { mutableFloatStateOf(0f) }
        var wasSpinningBeforeDrag by remember { mutableStateOf(false) }

        LaunchedEffect(progress >= 0.98f) {
            if (progress >= 0.98f) {
                lastExpandedAngle = circleAngle
                if (wasSpinningBeforeDrag) {
                    isCircleSpinning = true
                    wasSpinningBeforeDrag = false
                }
            }
        }

        LaunchedEffect(progress < 0.98f) {
            if (progress < 0.98f && isCircleSpinning) {
                wasSpinningBeforeDrag = true
                isCircleSpinning = false
            }
        }

        LaunchedEffect(progress <= 0.05f) {
            if (progress <= 0.05f) {
                circleAngle = 0f
                lastExpandedAngle = 0f
                isCircleSpinning = false
                wasSpinningBeforeDrag = false
            }
        }

        val shortestDelta = remember(lastExpandedAngle) {
            val normalized = ((lastExpandedAngle % 360f) + 360f) % 360f
            if (normalized > 180f) normalized - 360f else normalized
        }
        val returnRatio = (progress / 0.95f).coerceIn(0f, 1f)
        val effectiveDiscAngle = if (progress >= 0.98f) circleAngle else shortestDelta * returnRatio

        val currentHorizontalPadding = (12 * (1f - progress)).dp
        val currentCornerRadius = (28 * (1f - progress)).dp
        val currentBottomPadding = lerp(defaultBottomPadding, 0.dp, progress)
        val currentHeight = lerp(collapsedHeight, screenHeightDp, progress)
        val surfaceShape = RoundedCornerShape(currentCornerRadius)

        val containerColor = lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.background,
            progress
        )

        val shadowElevation = (6 * (1f - progress)).dp
        val tonalElevation = 0.dp
        val borderWidth = (0.5f * (1f - progress)).dp
        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * (1f - progress))

        val dismissAlpha = if (progress == 0f && dismissOffsetY.value > 0f) {
            (1f - (dismissOffsetY.value / 250f)).coerceIn(0f, 1f)
        } else {
            1f
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, dismissOffsetY.value.roundToInt()) }
                .padding(horizontal = currentHorizontalPadding)
                .padding(bottom = currentBottomPadding)
                .fillMaxWidth()
                .height(currentHeight)
                .clip(surfaceShape)
                .onGloballyPositioned { coords ->
                    surfaceCoordinates = coords
                }
                .graphicsLayer { alpha = dismissAlpha }
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(
                            width = borderWidth,
                            color = borderColor,
                            shape = surfaceShape
                        )
                    } else Modifier
                )
                .playerSurfaceVerticalDrag(
                    coroutineScope = coroutineScope,
                    expansionAnimatable = expansionAnimatable,
                    dismissOffsetY = dismissOffsetY,
                    screenHeight = screenHeight,
                    density = density,
                    onExpandedChange = { expanded ->
                        onAction(PlayerAction.SetExpanded(expanded))
                    },
                    onDismissMiniPlayer = {
                        onAction(PlayerAction.Dismiss)
                    }
                )
                .testTag("interactive_player_surface"),
            shape = surfaceShape,
            color = containerColor,
            shadowElevation = shadowElevation,
            tonalElevation = tonalElevation
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.nowPlayingTheme != NowPlayingTheme.DEFAULT && progress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress }
                    ) {
                        NowPlayingThemeContainer(
                            theme = uiState.nowPlayingTheme,
                            song = currentSong,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }

                val nowPlayingAlpha = ((progress - 0.08f) / 0.40f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = nowPlayingAlpha
                        }
                ) {
                    NowPlayingScreen(
                        uiState = uiState,
                        onAction = { action ->
                            if (action is PlayerAction.OpenEqualizer) {
                                onNavigateToEqualizer()
                            } else if (action is PlayerAction.Collapse) {
                                coroutineScope.launch {
                                    expansionAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.90f,
                                            stiffness = 520f
                                        )
                                    )
                                    onAction(PlayerAction.Collapse)
                                }
                            } else {
                                onAction(action)
                            }
                        },
                        playbackProgressFlow = if (progress > 0.05f) playbackProgressFlow else null,
                        containerColor = Color.Transparent,
                        hideArtwork = progress < 0.99f,
                        expansionProgress = progress,
                        onArtworkCoordinatesMeasured = { coords ->
                            targetArtworkCoordinates = coords
                        },
                        onOpenLyrics = onNavigateToLyrics,
                        circleAngle = effectiveDiscAngle,
                        onCircleAngleChange = { angle ->
                            circleAngle = angle
                            if (progress >= 0.98f) lastExpandedAngle = angle
                        },
                        isCircleSpinning = isCircleSpinning,
                        onCircleSpinningChange = { isCircleSpinning = it },
                        circleSpinDirection = circleSpinDirection,
                        onCircleSpinDirectionChange = { circleSpinDirection = it },
                        modifier = Modifier
                            .requiredWidth(screenWidthDp)
                            .requiredHeight(screenHeightDp)
                    )
                }

                if (progress < 0.70f) {
                    val miniPlayerAlpha = (1f - (progress / 0.30f)).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(collapsedHeight)
                            .graphicsLayer {
                                alpha = miniPlayerAlpha
                            }
                            .clickable(
                                enabled = progress < 0.05f,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    onAction(PlayerAction.Expand)
                                    expansionAnimatable.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 380f
                                        )
                                    )
                                }
                            }
                            .testTag("mini_player")
                    ) {
                        MiniPlayerContent(
                            currentSong = currentSong,
                            playbackInfo = uiState.playbackInfo,
                            onAction = onAction,
                            showArtwork = false
                        )
                    }
                }

                val morphingArtworkAlpha = if (progress >= 0.99f) 0f else 1f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = morphingArtworkAlpha }
                ) {
                    MorphingPlayerArtwork(
                        song = currentSong,
                        progress = progress,
                        targetArtworkCoordinates = targetArtworkCoordinates,
                        surfaceCoordinates = surfaceCoordinates,
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp,
                        currentHorizontalPadding = currentHorizontalPadding,
                        artworkStyle = uiState.artworkStyle,
                        isPlaying = uiState.playbackInfo.isPlaying,
                        containerColor = MaterialTheme.colorScheme.background,
                        density = density,
                        currentAngle = effectiveDiscAngle,
                        onAngleChange = { angle ->
                            circleAngle = angle
                            if (progress >= 0.98f) lastExpandedAngle = angle
                        },
                        isSpinningActive = isCircleSpinning && progress >= 0.98f,
                        onSpinningChange = { isCircleSpinning = it },
                        spinDirection = circleSpinDirection,
                        onSpinDirectionChange = { circleSpinDirection = it },
                        measuredTargetTopDp = measuredTargetTopDp
                    )
                }
            }
        }
    }
}
