package com.example.ui.lyrics.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun InstrumentalDotsIndicator(
    introProgress: Float,
    isFirstLineActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
) {
    val dotTargetActiveColor = if (isFirstLineActive) inactiveColor.copy(alpha = 0.35f) else activeColor

    val isDot1Active = introProgress >= 0.05f || isFirstLineActive

    val isDot2Active = introProgress >= 0.40f || isFirstLineActive

    val isDot3Active = introProgress >= 0.75f || isFirstLineActive

    val dot1Color by animateColorAsState(
        targetValue = if (isDot1Active) dotTargetActiveColor else inactiveColor,
        animationSpec = tween(250),
        label = "dot1_color"
    )

    val dot2Color by animateColorAsState(
        targetValue = if (isDot2Active) dotTargetActiveColor else inactiveColor,
        animationSpec = tween(250),
        label = "dot2_color"
    )

    val dot3Color by animateColorAsState(
        targetValue = if (isDot3Active) dotTargetActiveColor else inactiveColor,
        animationSpec = tween(250),
        label = "dot3_color"
    )

    val dot1Size by animateDpAsState(
        targetValue = if (isDot1Active && !isFirstLineActive) 11.dp else 9.dp,
        animationSpec = tween(250),
        label = "dot1_size"
    )

    val dot2Size by animateDpAsState(
        targetValue = if (isDot2Active && !isFirstLineActive) 11.dp else 9.dp,
        animationSpec = tween(250),
        label = "dot2_size"
    )

    val dot3Size by animateDpAsState(
        targetValue = if (isDot3Active && !isFirstLineActive) 11.dp else 9.dp,
        animationSpec = tween(250),
        label = "dot3_size"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dot1Size)
                .clip(CircleShape)
                .background(dot1Color)
        )

        Box(
            modifier = Modifier
                .size(dot2Size)
                .clip(CircleShape)
                .background(dot2Color)
        )

        Box(
            modifier = Modifier
                .size(dot3Size)
                .clip(CircleShape)
                .background(dot3Color)
        )
    }
}
