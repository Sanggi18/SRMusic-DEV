package com.example.ui.settings.appearance.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.NowPlayingArtworkStyle
import com.example.core.model.NowPlayingProgressStyle
import com.example.core.model.NowPlayingTheme
import com.example.core.model.TrackInfoAlignment

@Composable
fun SectionHeaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
    )
}

@Composable
fun ProgressStyleCard(
    style: NowPlayingProgressStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "progress_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "progress_pill_content"
    )
    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(pillShape)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp)
            .testTag("progress_style_${style.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = getProgressShortLabel(style),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getProgressShortLabel(style: NowPlayingProgressStyle): String {
    return when (style) {
        NowPlayingProgressStyle.LINE_THUMB -> "Line"
        NowPlayingProgressStyle.CAPSULE_PILL -> "Capsule"
        NowPlayingProgressStyle.ROUNDED_BAR -> "Bar"
        NowPlayingProgressStyle.WAVE_MINIMAL -> "Wave"
        NowPlayingProgressStyle.MATERIAL_3 -> "Material 3"
        NowPlayingProgressStyle.WAVEFORM -> "Waveform"
    }
}

@Composable
fun PlayerThemePreviewCard(
    theme: NowPlayingTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "theme_preview_border"
    )
    val footerBgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "theme_footer_bg"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHighest
    val surfaceHighest = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val fgColor = when (theme) {
        NowPlayingTheme.DEFAULT -> onSurface
        NowPlayingTheme.COLOR -> onPrimaryContainer
        NowPlayingTheme.BLUR -> onSurface
        NowPlayingTheme.BLUR_2 -> onSurface
    }

    val themeLabel = when (theme) {
        NowPlayingTheme.DEFAULT -> "Default"
        NowPlayingTheme.COLOR -> "Color"
        NowPlayingTheme.BLUR -> "Transparent"
        NowPlayingTheme.BLUR_2 -> "Blur"
    }

    val outerShape = RoundedCornerShape(16.dp)

    Card(
        shape = outerShape,
        colors = CardDefaults.cardColors(containerColor = footerBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(outerShape)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = outerShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("theme_preview_${theme.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(outerShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    when (theme) {
                        NowPlayingTheme.DEFAULT -> {
                            drawRect(color = surfaceHigh)
                        }

                        NowPlayingTheme.COLOR -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(primaryContainer, secondaryContainer)
                                )
                            )
                        }

                        NowPlayingTheme.BLUR -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(surfaceHigh, surfaceLow)
                                )
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        tertiaryContainer.copy(alpha = 0.75f),
                                        Color.Transparent
                                    ),
                                    center = Offset(w * 0.46f, h * 0.48f),
                                    radius = w * 0.72f
                                ),
                                center = Offset(w * 0.46f, h * 0.48f),
                                radius = w * 0.72f
                            )
                        }

                        NowPlayingTheme.BLUR_2 -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(surfaceHighest, surfaceLow)
                                )
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFEC407A).copy(alpha = 0.75f),
                                        Color.Transparent
                                    ),
                                    center = Offset(w * 0.30f, h * 0.32f),
                                    radius = w * 0.55f
                                ),
                                center = Offset(w * 0.30f, h * 0.32f),
                                radius = w * 0.55f
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF42A5F5).copy(alpha = 0.75f),
                                        Color.Transparent
                                    ),
                                    center = Offset(w * 0.72f, h * 0.68f),
                                    radius = w * 0.55f
                                ),
                                center = Offset(w * 0.72f, h * 0.68f),
                                radius = w * 0.55f
                            )
                        }
                    }

                    val padX = w * 0.14f
                    val titleY = h * 0.74f
                    val subtitleY = h * 0.815f
                    val progressY = h * 0.89f

                    drawLine(
                        color = fgColor.copy(alpha = 0.90f),
                        start = Offset(padX, titleY),
                        end = Offset(w * 0.68f, titleY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = fgColor.copy(alpha = 0.55f),
                        start = Offset(padX, subtitleY),
                        end = Offset(w * 0.48f, subtitleY),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = fgColor.copy(alpha = 0.75f),
                        start = Offset(padX, progressY),
                        end = Offset(w - padX, progressY),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = themeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SegmentedAlignmentSelector(
    currentAlignment: TrackInfoAlignment,
    onAlignmentSelected: (TrackInfoAlignment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segmented_alignment_selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackInfoAlignment.entries.forEach { alignment ->
            AlignmentPillItem(
                label = alignment.label,
                isSelected = currentAlignment == alignment,
                onClick = { onAlignmentSelected(alignment) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AlignmentPillItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "align_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "align_pill_content"
    )
    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(pillShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp)
            .testTag("align_option_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun ArtworkStylePreviewCard(
    style: NowPlayingArtworkStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "artwork_style_preview_border"
    )
    val footerBgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "artwork_footer_bg"
    )

    val outerShape = RoundedCornerShape(16.dp)
    val artworkBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val accentColor = MaterialTheme.colorScheme.primary
    val lineFgColor = MaterialTheme.colorScheme.onSurface

    Card(
        shape = outerShape,
        colors = CardDefaults.cardColors(containerColor = footerBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(outerShape)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = outerShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("artwork_style_${style.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(outerShape)
                    .background(artworkBgColor)
            ) {
                when (style) {
                    NowPlayingArtworkStyle.DEFAULT -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.70f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = artworkBgColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    NowPlayingArtworkStyle.CIRCLE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.70f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = artworkBgColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    NowPlayingArtworkStyle.FULL -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.66f)
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = artworkBgColor,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padX = w * 0.14f
                    val titleY = h * 0.74f
                    val subtitleY = h * 0.815f
                    val progressY = h * 0.89f

                    drawLine(
                        color = lineFgColor.copy(alpha = 0.90f),
                        start = Offset(padX, titleY),
                        end = Offset(w * 0.68f, titleY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = lineFgColor.copy(alpha = 0.55f),
                        start = Offset(padX, subtitleY),
                        end = Offset(w * 0.48f, subtitleY),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = lineFgColor.copy(alpha = 0.75f),
                        start = Offset(padX, progressY),
                        end = Offset(w - padX, progressY),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
