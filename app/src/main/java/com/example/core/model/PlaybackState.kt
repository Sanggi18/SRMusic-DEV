package com.example.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackInfo(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasNext: Boolean = false
)

@Immutable
data class PlaybackProgress(
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val songId: Long = -1L
) {
    val progressFraction: Float

        get() = if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
}
