package com.example.ui.common

import androidx.compose.runtime.compositionLocalOf

data class SongDisplayOptions(
    val showCodec: Boolean = false,
    val showFavoritesToggle: Boolean = false,
    val showFileName: Boolean = false,
    val showSize: Boolean = false
)

val LocalSongDisplayOptions = compositionLocalOf { SongDisplayOptions() }
