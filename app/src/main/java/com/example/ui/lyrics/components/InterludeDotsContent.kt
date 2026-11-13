package com.example.ui.lyrics.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private const val INTERLUDE_DOT_COUNT = 3

private const val INTERLUDE_ROW_HEIGHT_DP = 36

private const val INTERLUDE_DOT_REST_ALPHA = 0.25f

private const val INTERLUDE_DOT_REST_SCALE = 0.80f

@Composable
fun InterludeDotsContent(
    progress: Float,
    isCurrent: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    var lastProgress by remember { mutableFloatStateOf(0f) }

    if (isCurrent && progress > 0f) {
        lastProgress = progress
    }

    val effectiveProgress = if (isCurrent) progress else lastProgress

    val infiniteTransition = rememberInfiniteTransition(label = "interlude_dots_bounce")

    val bouncePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "interlude_bounce_phase"
    )

    val heightFactor by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isCurrent) 400 else 450,
            easing = FastOutSlowInEasing
        ),
        label = "interlude_height_factor",
    )

    val dotAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isCurrent) 320 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "interlude_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val targetHeightPx = (INTERLUDE_ROW_HEIGHT_DP.dp * heightFactor).roundToPx()

                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = targetHeightPx,
                        maxHeight = targetHeightPx
                    )
                )

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(width = 54.dp, height = 20.dp)
                .graphicsLayer { alpha = dotAlpha },
        ) {
            val radius = 4.dp.toPx()

            val dotSpacing = 7.dp.toPx()

            val dotStride = radius * 2f + dotSpacing

            val maxBouncePx = 2.8.dp.toPx()

            repeat(INTERLUDE_DOT_COUNT) { dotIndex ->
                val lit = (effectiveProgress * INTERLUDE_DOT_COUNT - dotIndex).coerceIn(0f, 1f)

                val alpha = INTERLUDE_DOT_REST_ALPHA + (1f - INTERLUDE_DOT_REST_ALPHA) * lit

                val dotScale = INTERLUDE_DOT_REST_SCALE + (1f - INTERLUDE_DOT_REST_SCALE) * lit

                val dotPhaseOffset = dotIndex * (Math.PI / 2.5).toFloat()

                val verticalOffset = if (isCurrent) {
                    (sin(bouncePhase + dotPhaseOffset) * maxBouncePx)
                } else {
                    0f
                }

                drawCircle(
                    color = accentColor.copy(alpha = alpha),
                    radius = radius * dotScale,
                    center = Offset(
                        x = radius + dotIndex * dotStride,
                        y = (size.height / 2f) + verticalOffset,
                    ),
                )
            }
        }
    }
}
