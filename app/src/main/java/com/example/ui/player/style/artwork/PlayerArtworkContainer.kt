package com.example.ui.player.style.artwork

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.NowPlayingArtworkStyle
import com.example.core.model.Song

@Composable
fun PlayerArtworkContainer(
    song: Song,
    style: NowPlayingArtworkStyle,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 12.dp,
    shadowColor: Color? = null,
    containerColor: Color = Color.Transparent,
    currentAngle: Float = 0f,
    onAngleChange: (Float) -> Unit = {},
    isSpinningActive: Boolean = true,
    onSpinningChange: (Boolean) -> Unit = {},
    spinDirection: Float = 1f,
    onSpinDirectionChange: (Float) -> Unit = {},
    enabledGestures: Boolean = true
) {
    when (style) {
        NowPlayingArtworkStyle.DEFAULT -> {
            DefaultArtworkStyle(
                song = song,
                cornerRadius = cornerRadius,
                isPlaying = isPlaying,
                elevation = elevation,
                shadowColor = shadowColor,
                modifier = modifier
            )
        }
        NowPlayingArtworkStyle.CIRCLE -> {
            CircleArtworkStyle(
                song = song,
                isPlaying = isPlaying,
                elevation = elevation,
                shadowColor = shadowColor,
                currentAngle = currentAngle,
                onAngleChange = onAngleChange,
                isSpinningActive = isSpinningActive,
                onSpinningChange = onSpinningChange,
                spinDirection = spinDirection,
                onSpinDirectionChange = onSpinDirectionChange,
                enabledGestures = enabledGestures,
                modifier = modifier
            )
        }
        NowPlayingArtworkStyle.FULL -> {
            FullArtworkStyle(
                song = song,
                containerColor = containerColor,
                modifier = modifier
            )
        }
    }
}
