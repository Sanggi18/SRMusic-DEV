package com.example.ui.sidebar.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.FavoritesRepository
import com.example.core.data.repository.HistoryRepository
import com.example.core.data.repository.PlaylistRepository
import com.example.core.data.repository.SongRepository
import com.example.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository? = null,
    private val songRepository: SongRepository? = null
) : ViewModel() {
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val playlistAndAllSongsFlow = combine(
        playlistRepository.playlistsFlow,
        playlistRepository.allCrossRefsFlow,
        songRepository?.allSongsFlow ?: flowOf(emptyList())
    ) { playlists, crossRefs, allSongs ->
        val songById = allSongs.associateBy { it.id }
        val map = mutableMapOf<Long, List<Song>>()
        val groupedRefs = crossRefs.groupBy { it.playlistId }

        for (pl in playlists) {
            val refs = groupedRefs[pl.id].orEmpty().sortedBy { it.orderIndex }
            map[pl.id] = refs.mapNotNull { songById[it.songId] }
        }

        Triple(playlists, map, allSongs)
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        favoritesRepository.favoriteSongsFlow,
        playlistAndAllSongsFlow,
        historyRepository?.historySongIdsFlow ?: MutableStateFlow(emptyList()),
        _statusMessage
    ) { favs, (playlists, playlistMap, allSongs), historyIds, msg ->
        val songById = allSongs.associateBy { it.id }
        val resolvedHistory = historyIds.mapNotNull { songById[it] }

        CollectionUiState(
            favoriteSongs = favs,
            playlists = playlists,
            playlistSongsMap = playlistMap,
            historySongs = resolvedHistory,
            statusMessage = msg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CollectionUiState()
    )

    fun onAction(action: CollectionAction) {
        when (action) {
            is CollectionAction.ToggleFavorite -> {
                viewModelScope.launch { favoritesRepository.toggleFavorite(action.songId) }
            }

            is CollectionAction.CreatePlaylist -> {
                viewModelScope.launch {
                    val id = playlistRepository.createPlaylist(action.name)
                    action.onCreated?.invoke(id)
                }
            }

            is CollectionAction.RenamePlaylist -> {
                viewModelScope.launch { playlistRepository.renamePlaylist(action.playlistId, action.newName) }
            }

            is CollectionAction.DeletePlaylist -> {
                viewModelScope.launch { playlistRepository.deletePlaylist(action.playlistId) }
            }

            is CollectionAction.DeleteMultiplePlaylists -> {
                viewModelScope.launch { playlistRepository.deleteMultiplePlaylists(action.playlistIds) }
            }

            is CollectionAction.AddSongToPlaylist -> {
                viewModelScope.launch { playlistRepository.addSongToPlaylist(action.playlistId, action.songId) }
            }

            is CollectionAction.AddSongsToPlaylist -> {
                viewModelScope.launch {
                    playlistRepository.addSongsToPlaylist(action.playlistId, action.songIds)
                    _statusMessage.value = "${action.songIds.size} songs added to playlist"
                }
            }

            is CollectionAction.RemoveSongFromPlaylist -> {
                viewModelScope.launch { playlistRepository.removeSongFromPlaylist(action.playlistId, action.songId) }
            }

            is CollectionAction.ExportPlaylist -> {
                viewModelScope.launch {
                    val res = playlistRepository.exportPlaylist(action.uri, action.songs)
                    _statusMessage.value = res.fold(
                        onSuccess = { "Playlist exported successfully" },
                        onFailure = { "Export failed: ${it.message}" }
                    )
                }
            }

            is CollectionAction.ImportPlaylist -> {
                viewModelScope.launch {
                    val res = playlistRepository.importPlaylist(action.uri, action.allSongs)
                    res.onSuccess { data ->
                        if (data.matchedSongs.isNotEmpty()) {
                            val newId = playlistRepository.createPlaylist(data.playlistName)
                            playlistRepository.addSongsToPlaylist(newId, data.matchedSongs.map { it.id })
                            _statusMessage.value = "Imported '${data.playlistName}' (${data.matchedSongs.size}/${data.totalCount} songs)"
                        } else {
                            _statusMessage.value = "No matching songs in library (0/${data.totalCount})"
                        }
                    }.onFailure {
                        _statusMessage.value = "Failed to import playlist: ${it.message}"
                    }
                }
            }

            is CollectionAction.ImportPlaylistFromSongs -> {
                viewModelScope.launch {
                    if (action.songIds.isNotEmpty()) {
                        val newId = playlistRepository.createPlaylist(action.name)
                        playlistRepository.addSongsToPlaylist(newId, action.songIds)
                        _statusMessage.value = "Imported '${action.name}' (${action.songIds.size} songs)"
                    } else {
                        _statusMessage.value = "No songs found in '${action.name}'"
                    }
                }
            }

            CollectionAction.ClearHistory -> {
                viewModelScope.launch {
                    historyRepository?.clearHistory()
                    _statusMessage.value = "Playback history cleared"
                }
            }

            CollectionAction.ClearStatusMessage -> {
                _statusMessage.value = null
            }
        }
    }

    companion object {
        fun provideFactory(
            favoritesRepository: FavoritesRepository,
            playlistRepository: PlaylistRepository,
            historyRepository: HistoryRepository? = null,
            songRepository: SongRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CollectionViewModel(
                    favoritesRepository = favoritesRepository,
                    playlistRepository = playlistRepository,
                    historyRepository = historyRepository,
                    songRepository = songRepository
                ) as T
            }
        }
    }
}
