package com.example.ui.lyrics.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    data object Idle : LyricsUiState

    data class Loading(
        val songId: Long
    ) : LyricsUiState

    data class Ready(
        val songId: Long,
        val document: LyricsDocument
    ) : LyricsUiState
}

class LyricsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LyricsRepository()

    private val _uiState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)

    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private var requestedSongId: Long? = null

    private var requestedModified: Long? = null

    fun load(song: Song) {
        if (requestedSongId == song.id &&
            requestedModified == song.dateModified &&
            (_uiState.value is LyricsUiState.Ready || loadJob?.isActive == true)
        ) {
            return
        }

        repository.getCached(song)?.let { cached ->
            requestedSongId = song.id

            requestedModified = song.dateModified

            _uiState.value = LyricsUiState.Ready(songId = song.id, document = cached)

            return
        }

        requestedSongId = song.id

        requestedModified = song.dateModified

        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading(song.id)

            val result = repository.load(song)

            ensureActive()

            if (requestedSongId == song.id) {
                _uiState.value = LyricsUiState.Ready(
                    songId = song.id,
                    document = result
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
    }
}
