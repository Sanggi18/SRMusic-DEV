package com.example.ui.player.components

import com.example.ui.player.core.*
import com.example.ui.common.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.example.core.model.NowPlayingArtworkStyle
import com.example.core.model.Song
import com.example.ui.common.components.AlbumArtwork
import com.example.ui.player.style.artwork.CircleArtworkStyle

@Composable
fun MorphingPlayerArtwork(
    song: Song,
    progress: Float,
    targetArtworkCoordinates: LayoutCoordinates?,
    surfaceCoordinates: LayoutCoordinates?,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    currentHorizontalPadding: Dp,
    modifier: Modifier = Modifier,
    artworkStyle: NowPlayingArtworkStyle = NowPlayingArtworkStyle.DEFAULT,
    isPlaying: Boolean = false,
    containerColor: Color = Color.Transparent,
    density: Density = LocalDensity.current,
    currentAngle: Float = 0f,
    onAngleChange: (Float) -> Unit = {},
    isSpinningActive: Boolean = false,
    onSpinningChange: (Boolean) -> Unit = {},
    spinDirection: Float = 1f,
    onSpinDirectionChange: (Float) -> Unit = {},
    measuredTargetTopDp: Dp? = null
) {
    val miniCoverSizeDp = 40.dp
    val miniCoverLeftDp = 8.dp
    val miniCoverTopDp = 7.5.dp
    val miniCornerRadius = miniCoverSizeDp / 2

    val isFull = artworkStyle == NowPlayingArtworkStyle.FULL
    val targetWidth = if (isFull) screenWidthDp else (screenWidthDp - 48.dp) * 0.92f
    val targetHeight = if (isFull) (screenWidthDp * 1.18f).coerceAtLeast(400.dp) else targetWidth
    val targetLeft = if (isFull) 0.dp else (screenWidthDp - targetWidth) / 2

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val availableHeight = screenHeightDp - (statusBarTop + 64.dp) - navBarBottom
    val isCompact = availableHeight < 560.dp

    val defaultTargetTopDp = if (isFull) {
        0.dp
    } else if (isCompact) {
        statusBarTop + 64.dp + 16.dp
    } else {
        val contentHeight = targetWidth + 266.dp
        val extraSpace = (availableHeight - contentHeight).coerceAtLeast(0.dp)
        val spaceBetween = extraSpace / 7
        statusBarTop + 64.dp + 2.dp + spaceBetween
    }

    val finalTargetLeft = targetLeft
    val finalTargetTop = measuredTargetTopDp ?: defaultTargetTopDp
    val finalTargetWidth = targetWidth
    val finalTargetHeight = targetHeight

    val currentArtworkWidth = lerp(miniCoverSizeDp, finalTargetWidth, progress)
    val currentArtworkHeight = lerp(miniCoverSizeDp, finalTargetHeight, progress)
    val currentArtworkLeft = lerp(miniCoverLeftDp, finalTargetLeft, progress)
    val currentArtworkTop = lerp(miniCoverTopDp, finalTargetTop, progress)

    val expandedCornerRadius = when (artworkStyle) {
        NowPlayingArtworkStyle.CIRCLE -> finalTargetWidth / 2
        NowPlayingArtworkStyle.DEFAULT -> 24.dp
        NowPlayingArtworkStyle.FULL -> 0.dp
    }
    val currentArtworkCornerRadius = lerp(miniCornerRadius, expandedCornerRadius, progress)
    val artworkShape = RoundedCornerShape(currentArtworkCornerRadius)
    val shadowElevation = if (isFull) 0.dp else (12 * progress).dp

    val maskProgress = if (isFull) progress.coerceIn(0f, 1f) else 0f
    val fullMaskModifier = if (isFull && maskProgress > 0.001f) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.White,
                        0.40f to Color.White,
                        0.55f to Color.White.copy(alpha = lerp(1f, 0.90f, maskProgress)),
                        0.70f to Color.White.copy(alpha = lerp(1f, 0.65f, maskProgress)),
                        0.82f to Color.White.copy(alpha = lerp(1f, 0.38f, maskProgress)),
                        0.92f to Color.White.copy(alpha = lerp(1f, 0.15f, maskProgress)),
                        1.0f to Color.White.copy(alpha = 1f - maskProgress)
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    } else Modifier

    if (artworkStyle == NowPlayingArtworkStyle.CIRCLE) {
        CircleArtworkStyle(
            song = song,
            isPlaying = isPlaying,
            elevation = shadowElevation,
            shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * progress),
            currentAngle = currentAngle,
            onAngleChange = onAngleChange,
            isSpinningActive = isSpinningActive,
            onSpinningChange = onSpinningChange,
            spinDirection = spinDirection,
            onSpinDirectionChange = onSpinDirectionChange,
            enabledGestures = progress >= 0.85f,
            modifier = modifier
                .offset(x = currentArtworkLeft, y = currentArtworkTop)
                .size(currentArtworkWidth)
        )
    } else {
        Box(
            modifier = modifier
                .offset(x = currentArtworkLeft, y = currentArtworkTop)
                .width(currentArtworkWidth)
                .height(currentArtworkHeight)
                .then(
                    if (shadowElevation > 0.dp) {
                        Modifier.shadow(
                            elevation = shadowElevation,
                            shape = artworkShape,
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * progress),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * progress)
                        )
                    } else Modifier
                )
                .clip(artworkShape)
                .then(fullMaskModifier)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtwork(
                artworkUri = song.contentUri,
                fallbackUri = song.artworkUri,
                dataPath = song.dataPath,
                fallbackText = song.title,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                size = currentArtworkWidth,
                targetSizePx = 800,
                shape = artworkShape
            )

            if (isFull && progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.70f * progress),
                                    Color.Black.copy(alpha = 0.35f * progress),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}
