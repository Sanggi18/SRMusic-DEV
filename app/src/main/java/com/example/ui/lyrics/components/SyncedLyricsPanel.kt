package com.example.ui.lyrics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PlaybackProgress
import com.example.core.model.Song
import com.example.ui.lyrics.components.InstrumentalDotsIndicator
import com.example.ui.lyrics.components.InterludeDotsContent
import com.example.ui.lyrics.components.LyricBlurText
import com.example.ui.lyrics.core.LyricLine
import com.example.ui.lyrics.core.LyricsContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun estimateLineEndMs(line: LyricLine): Long {
    return if (line.words.isNotEmpty()) {
        line.words.last().startMs + 1_200L
    } else {
        line.startMs + (line.text.length * 140L).coerceIn(2_800L, 5_500L)
    }
}

@Composable
fun SyncedLyricsPanel(
    song: Song,
    lyrics: List<LyricLine>,
    playbackProgressFlow: StateFlow<PlaybackProgress>,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.onBackground,
    inactiveColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSeekToLine: ((Long) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var autoFollow by remember(song.id) { mutableStateOf(true) }
    var activeIndex by remember(song.id, lyrics) { mutableIntStateOf(-1) }
    var activeWordIndex by remember(song.id, lyrics) { mutableIntStateOf(-1) }
    var isInterludeActive by remember(song.id, lyrics) { mutableStateOf(false) }
    var interludeProgress by remember(song.id, lyrics) { mutableFloatStateOf(0f) }
    val firstLineStartMs = remember(lyrics) { lyrics.firstOrNull()?.startMs ?: 0L }
    val hasIntro = firstLineStartMs >= 1500L
    var introProgress by remember(song.id, lyrics) { mutableFloatStateOf(0f) }
    val syncedContent = remember(lyrics) { LyricsContent.Synced(lyrics) }

    LaunchedEffect(song.id, lyrics, isPlaying) {
        var lastAnchorPositionMs = 0L
        var lastAnchorFrameNanos = 0L
        var lastLineIdx = Int.MIN_VALUE
        var lastWordIdx = Int.MIN_VALUE

        fun updateIndicesAt(posMs: Long) {
            if (hasIntro && activeIndex <= 0 && posMs <= firstLineStartMs) {
                val threshold = (firstLineStartMs - 150L).coerceAtLeast(1L)
                val newIntro = (posMs.toFloat() / threshold).coerceIn(0f, 1f)
                if (abs(newIntro - introProgress) >= 0.02f) {
                    introProgress = newIntro
                }
            }

            val targetPos = posMs + 150L
            val lineIdx = syncedContent.indexAt(targetPos)
            if (lineIdx != lastLineIdx) {
                lastLineIdx = lineIdx
                activeIndex = lineIdx
                lastWordIdx = -1
            }

            val wordIdx = if (lineIdx in lyrics.indices) {
                lyrics[lineIdx].wordIndexAt(targetPos)
            } else {
                -1
            }

            if (wordIdx != lastWordIdx) {
                lastWordIdx = wordIdx
                activeWordIndex = wordIdx
            }

            if (lineIdx in lyrics.indices && lineIdx + 1 < lyrics.size) {
                val lineEndMs = estimateLineEndMs(lyrics[lineIdx])
                val nextLineStart = lyrics[lineIdx + 1].startMs
                val silentGapMs = nextLineStart - lineEndMs
                val interludeEndMs = nextLineStart - 150L

                if (silentGapMs >= 5_000L && posMs in lineEndMs..interludeEndMs) {
                    if (!isInterludeActive) isInterludeActive = true
                    val duration = (interludeEndMs - lineEndMs).coerceAtLeast(1L)
                    val newProg = ((posMs - lineEndMs).toFloat() / duration).coerceIn(0f, 1f)
                    if (abs(newProg - interludeProgress) >= 0.02f) {
                        interludeProgress = newProg
                    }
                } else if (isInterludeActive) {
                    isInterludeActive = false
                    interludeProgress = 0f
                }
            } else if (isInterludeActive) {
                isInterludeActive = false
                interludeProgress = 0f
            }
        }

        launch {
            playbackProgressFlow.collect { progress ->
                val currentPos = progress.currentPosition
                val nowNanos = System.nanoTime()
                val expectedPos = if (lastAnchorFrameNanos > 0L && isPlaying) {
                    lastAnchorPositionMs + (nowNanos - lastAnchorFrameNanos) / 1_000_000L
                } else {
                    currentPos
                }
                val isDiscontinuity = abs(currentPos - expectedPos) > 400L || currentPos < lastAnchorPositionMs
                lastAnchorPositionMs = currentPos
                lastAnchorFrameNanos = nowNanos

                if (!isPlaying || isDiscontinuity) {
                    updateIndicesAt(currentPos)
                }
            }
        }

        if (isPlaying) {
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    if (lastAnchorFrameNanos > 0L) {
                        val elapsedMs = (frameTimeNanos - lastAnchorFrameNanos) / 1_000_000L
                        val estimatedPositionMs = lastAnchorPositionMs + elapsedMs
                        updateIndicesAt(estimatedPositionMs)
                    }
                }
            }
        }
    }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            autoFollow = false
        } else {
            delay(2_500L)
            autoFollow = true
        }
    }

    val shouldShowIntroDots = hasIntro && activeIndex <= 0

    Box(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(activeIndex, autoFollow, shouldShowIntroDots) {
            if (!autoFollow) return@LaunchedEffect

            if (shouldShowIntroDots) {
                listState.animateScrollToItem(index = 0, scrollOffset = 0)
            } else if (activeIndex in lyrics.indices) {
                val targetIndex = if (hasIntro) activeIndex + 1 else activeIndex
                val viewportHeightPx = snapshotFlow { listState.layoutInfo.viewportSize.height }
                    .first { it > 0 }
                val anchorPx = (viewportHeightPx * 0.20f).toInt()
                val activeItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }

                when {
                    activeItem != null && !listState.canScrollBackward && activeItem.offset in 0..anchorPx -> Unit
                    else -> {
                        listState.animateScrollToItem(
                            index = targetIndex,
                            scrollOffset = -anchorPx
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 360.dp,
                start = 24.dp,
                end = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            if (hasIntro) {
                item(
                    key = "intro_prelude_dots",
                    contentType = "intro_dots"
                ) {
                    AnimatedVisibility(
                        visible = shouldShowIntroDots,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(400, easing = FastOutSlowInEasing))
                    ) {
                        InstrumentalDotsIndicator(
                            introProgress = introProgress,
                            isFirstLineActive = activeIndex == 0,
                            activeColor = accentColor,
                            inactiveColor = inactiveColor.copy(alpha = 0.20f)
                        )
                    }
                }
            }

            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${line.startMs}_$index" },
                contentType = { _, _ -> "synced_lyric_line" }
            ) { index, line ->
                val isLineActive = index == activeIndex
                val hasInterludeGap = index + 1 < lyrics.size &&
                    (lyrics[index + 1].startMs - estimateLineEndMs(line)) >= 5_000L
                val isCurrentInterlude = isLineActive && isInterludeActive && hasInterludeGap
                val isLineSinging = isLineActive && !isCurrentInterlude
                val distance = if (activeIndex >= 0) index - activeIndex else index + 1
                val absDistance = abs(distance)

                val distanceAlpha = when {
                    isLineSinging -> 1f
                    isCurrentInterlude -> 0.65f
                    distance < 0 -> when (distance) {
                        -1 -> 0.65f
                        -2 -> 0.45f
                        -3 -> 0.32f
                        else -> 0.22f
                    }
                    else -> when (distance) {
                        1 -> 0.50f
                        2 -> 0.35f
                        3 -> 0.24f
                        else -> 0.16f
                    }
                }

                val targetBlur = when {
                    isLineSinging || isCurrentInterlude || isUserDragging -> 0.dp
                    absDistance == 1 -> 0.8.dp
                    absDistance == 2 -> 1.6.dp
                    else -> 2.4.dp
                }

                val targetScale = if (isLineSinging) 1.0f else 0.95f

                val animatedAlpha by animateFloatAsState(
                    targetValue = distanceAlpha,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "lyric_alpha"
                )

                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "lyric_scale"
                )

                val lineInteractionSource = remember { MutableInteractionSource() }
                val lineFontWeight = if (isLineSinging) FontWeight.ExtraBold else FontWeight.Normal
                val lineFontSize = if (isLineSinging) 26.sp else 23.sp
                val lineLineHeight = if (isLineSinging) 36.sp else 32.sp

                val annotatedWordString = if (isLineSinging && line.words.isNotEmpty() && activeWordIndex >= 0) {
                    buildAnnotatedString {
                        line.words.forEachIndexed { wIdx, word ->
                            val wordAlpha = if (wIdx <= activeWordIndex) 1f else 0.45f
                            val wordColor = if (wIdx == activeWordIndex) accentColor else activeColor.copy(alpha = wordAlpha)
                            withStyle(SpanStyle(color = wordColor)) {
                                append(word.text)
                            }
                        }
                    }
                } else null

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LyricBlurText(
                        text = line.text,
                        annotatedText = annotatedWordString,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = lineFontWeight,
                            fontSize = lineFontSize,
                            lineHeight = lineLineHeight
                        ),
                        color = activeColor,
                        blurRadius = targetBlur,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (onSeekToLine != null) {
                                    Modifier.clickable(
                                        interactionSource = lineInteractionSource,
                                        indication = null
                                    ) {
                                        autoFollow = true
                                        onSeekToLine(line.startMs)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                alpha = animatedAlpha
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .padding(vertical = 2.dp)
                    )

                    if (hasInterludeGap) {
                        InterludeDotsContent(
                            progress = if (isCurrentInterlude) interludeProgress else 0f,
                            isCurrent = isCurrentInterlude,
                            accentColor = accentColor,
                        )
                    }
                }
            }
        }
    }
}
