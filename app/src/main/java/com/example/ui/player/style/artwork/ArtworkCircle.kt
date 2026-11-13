package com.example.ui.player.style.artwork

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.model.Song
import com.example.ui.common.components.AlbumArtwork
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun CircleArtworkStyle(
    song: Song,
    isPlaying: Boolean = false,
    elevation: Dp = 14.dp,
    shadowColor: Color? = null,
    currentAngle: Float = 0f,
    onAngleChange: (Float) -> Unit = {},
    isSpinningActive: Boolean = false,
    onSpinningChange: (Boolean) -> Unit = {},
    spinDirection: Float = 1f,
    onSpinDirectionChange: (Float) -> Unit = {},
    enabledGestures: Boolean = true,
    modifier: Modifier = Modifier
) {
    val latestCurrentAngle by rememberUpdatedState(currentAngle)
    val latestOnAngleChange by rememberUpdatedState(onAngleChange)
    val latestOnSpinDirectionChange by rememberUpdatedState(onSpinDirectionChange)
    val latestOnSpinningChange by rememberUpdatedState(onSpinningChange)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestIsSpinningActive by rememberUpdatedState(isSpinningActive)
    val latestSpinDirection by rememberUpdatedState(spinDirection)

    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val baseSpeed = 0.75f

    LaunchedEffect(isDragging, isSpinningActive, isPlaying) {
        if (isDragging) return@LaunchedEffect
        var lastFrameTimeNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                    return@withFrameNanos
                }
                val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameTimeNanos = frameTimeNanos
                val shouldSpin = latestIsSpinningActive && latestIsPlaying
                val targetSpeed = if (shouldSpin) baseSpeed * latestSpinDirection else 0f
                val deltaAngle: Float
                if (abs(angularVelocity) > 0.05f) {
                    deltaAngle = angularVelocity * (dt / 0.016f)
                    val decay = 1f - (3.5f * dt).coerceIn(0f, 0.25f)
                    angularVelocity = (angularVelocity * decay) + (targetSpeed * (1f - decay))
                } else if (shouldSpin) {
                    angularVelocity = 0f
                    deltaAngle = targetSpeed * (dt / 0.016f)
                } else {
                    angularVelocity = 0f
                    deltaAngle = 0f
                }
                if (deltaAngle != 0f) {
                    var nextAngle = (latestCurrentAngle + deltaAngle) % 360f
                    if (nextAngle < 0f) nextAngle += 360f
                    latestOnAngleChange(nextAngle)
                }
            }
        }
    }

    val spotColor = shadowColor ?: MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.40f else 0.18f)

    val gestureModifier = if (enabledGestures) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                var lastAngle = Math.toDegrees(atan2(down.position.y - centerY, down.position.x - centerX).toDouble()).toFloat()
                var totalDragDistance = 0f
                isDragging = true
                angularVelocity = 0f
                var currentTrackedAngle = latestCurrentAngle
                do {
                    val event = awaitPointerEvent()
                    val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (currentPointer.pressed) {
                        currentPointer.consume()
                        val newAngle = Math.toDegrees(atan2(currentPointer.position.y - centerY, currentPointer.position.x - centerX).toDouble()).toFloat()
                        var diff = newAngle - lastAngle
                        if (diff > 180f) diff -= 360f
                        if (diff < -180f) diff += 360f
                        totalDragDistance += abs(diff)
                        if (diff > 0.15f) latestOnSpinDirectionChange(1f)
                        else if (diff < -0.15f) latestOnSpinDirectionChange(-1f)
                        currentTrackedAngle = (currentTrackedAngle + diff) % 360f
                        if (currentTrackedAngle < 0f) currentTrackedAngle += 360f
                        latestOnAngleChange(currentTrackedAngle)
                        angularVelocity = diff.coerceIn(-24f, 24f)
                        lastAngle = newAngle
                    }
                } while (event.changes.any { it.pressed })
                isDragging = false
                if (totalDragDistance < 5f) {
                    val next = !latestIsSpinningActive
                    latestOnSpinningChange(next)
                    if (next) {
                        angularVelocity = baseSpeed * latestSpinDirection
                    } else {
                        angularVelocity = 0f
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .onSizeChanged { componentSize = it }
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = CircleShape,
                        spotColor = spotColor,
                        ambientColor = spotColor
                    )
                } else Modifier
            )
            .clip(CircleShape)
            .then(gestureModifier)
            .testTag("circle_artwork_style"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentAngle)
                .clip(CircleShape)
        ) {
            AlbumArtwork(
                artworkUri = song.contentUri,
                dataPath = song.dataPath,
                fallbackText = song.title,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                targetSizePx = 800,
                shape = CircleShape,
                isPinned = true
            )
        }
    }
}
