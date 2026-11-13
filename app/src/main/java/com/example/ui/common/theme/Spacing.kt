package com.example.ui.common.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val minTouchTarget: Dp = 48.dp,
    val listTwoLineHeight: Dp = 72.dp,
    val miniPlayerHeight: Dp = 55.dp,
    val navigationBarHeight: Dp = 80.dp,
    val artworkThumbnailSize: Dp = 52.dp,
    val artworkCornerRadius: Dp = 12.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
