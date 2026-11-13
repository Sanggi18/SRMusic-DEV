package com.example.ui.player.style.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.core.model.NowPlayingProgressStyle
import com.example.core.model.PlaybackProgress
import com.example.core.model.Song
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun PlayerProgressContainer(
    progress: PlaybackProgress,
    isPlaying: Boolean,
    progressStyle: NowPlayingProgressStyle,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    song: Song? = null,
    nextSong: Song? = null,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    isPreview: Boolean = false
) {
    val activeSongId = song?.id?.takeIf { it != -1L }
    val effectiveSongId = activeSongId ?: progress.songId
    val progressBelongsToSong = isPreview || activeSongId == null || progress.songId == activeSongId
    val fallbackDuration = song?.duration?.coerceAtLeast(0L) ?: 0L
    val effectiveDuration = if (progressBelongsToSong) {
        progress.duration.takeIf { it > 0L } ?: fallbackDuration
    } else fallbackDuration
    val initialPosition = if (progressBelongsToSong) {
        progress.currentPosition.coerceIn(0L, effectiveDuration)
    } else 0L

    var displayPositionMs by remember(effectiveSongId) { mutableLongStateOf(initialPosition) }
    var manualSeekTargetMs by remember(effectiveSongId) { mutableLongStateOf(-1L) }
    var manualSeekVersion by remember(effectiveSongId) { mutableLongStateOf(0L) }

    val speedMultiplier = if (isPreview) 5L else 1L

    LaunchedEffect(
        effectiveSongId, progress.songId, progress.currentPosition,
        effectiveDuration, isPlaying, progressBelongsToSong, manualSeekVersion, isPreview
    ) {
        if (!progressBelongsToSong || effectiveDuration <= 0L) return@LaunchedEffect

        val incoming = progress.currentPosition.coerceIn(0L, effectiveDuration)
        val seekTarget = manualSeekTargetMs
        if (seekTarget >= 0L) {
            if (abs(incoming - seekTarget) <= 1500L) {
                manualSeekTargetMs = -1L
                displayPositionMs = incoming
            }
            return@LaunchedEffect
        }

        displayPositionMs = incoming

        if (!isPlaying) return@LaunchedEffect

        var previousFrameNanos = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos != 0L) {
                    val deltaMs = (frameNanos - previousFrameNanos) / 1_000_000L
                    if (deltaMs in 1..250) {
                        val updated = (displayPositionMs + deltaMs * speedMultiplier).coerceIn(0L, effectiveDuration)
                        displayPositionMs = updated
                    }
                }
                previousFrameNanos = frameNanos
            }
        }
    }

    val smoothProgress = remember(displayPositionMs, effectiveDuration, effectiveSongId) {
        PlaybackProgress(displayPositionMs, effectiveDuration, effectiveSongId)
    }

    val wrappedOnSeek: (Long) -> Unit = { targetMs ->
        val clamped = targetMs.coerceIn(0L, effectiveDuration)
        manualSeekTargetMs = clamped
        manualSeekVersion += 1L
        displayPositionMs = clamped
        onSeek(clamped)
    }

    key(
        if (progressStyle == NowPlayingProgressStyle.WAVEFORM) {
            null
        } else {
            effectiveSongId
        }
    ) {
        when (progressStyle) {
            NowPlayingProgressStyle.LINE_THUMB -> LineThumbProgress(smoothProgress, wrappedOnSeek, activeColor = activeColor, inactiveColor = inactiveColor, modifier = modifier)
            NowPlayingProgressStyle.CAPSULE_PILL -> CapsulePillProgress(smoothProgress, wrappedOnSeek, activeColor = activeColor, inactiveColor = inactiveColor, modifier = modifier)
            NowPlayingProgressStyle.ROUNDED_BAR -> RoundedBarProgress(smoothProgress, wrappedOnSeek, activeColor = activeColor, inactiveColor = inactiveColor, modifier = modifier)
            NowPlayingProgressStyle.WAVE_MINIMAL -> WaveMinimalProgress(smoothProgress, isPlaying, wrappedOnSeek, activeColor = activeColor, inactiveColor = inactiveColor, modifier = modifier)
            NowPlayingProgressStyle.MATERIAL_3 -> Material3Progress(smoothProgress, wrappedOnSeek, activeColor = activeColor, inactiveColor = inactiveColor, modifier = modifier)
            NowPlayingProgressStyle.WAVEFORM -> WaveformProgress(smoothProgress, wrappedOnSeek, song = song, nextSong = nextSong, activeColor = activeColor, inactiveColor = inactiveColor, isPreview = isPreview, modifier = modifier)
        }
    }
}
