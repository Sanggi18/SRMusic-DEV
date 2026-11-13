package com.example.ui.common.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object ExpressiveMotion {
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f
    )

    val ScreenEnterTransition: EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.985f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
    )

    val ScreenExitTransition: ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    ) + scaleOut(
        targetScale = 0.985f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )

    fun hierarchicalTransition(isForward: Boolean): ContentTransform {
        return ScreenEnterTransition.togetherWith(ScreenExitTransition)
    }
}

fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = ExpressiveMotion.BouncySpring,
        label = "expressive_spring_press"
    )

    this.graphicsLayer {
        scaleX = scale

        scaleY = scale
    }
}
