package com.example.ui.home.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.preferences.AppPreferences
import com.example.core.data.repository.FavoritesRepository
import com.example.core.data.repository.HistoryRepository
import com.example.core.data.repository.SongRepository
import com.example.core.model.Song
import com.example.core.playback.PlaybackManager
import com.example.ui.common.SongActionDispatcher
import com.example.ui.library.core.SongAction
import com.example.ui.player.core.PlayerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class HomeStats(
    val totalPlayCount: Int,
    val totalListenedSeconds: Long,
    val songPlayCounts: Map<Long, Int>
)

private data class HomeOffsets(
    val remixOffset: Int,
    val genreOffset: Int,
    val selectedDailyGenreIndex: Int
)

class HomeViewModel(
    private val songRepository: SongRepository,
    private val historyRepository: HistoryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val appPreferences: AppPreferences,
    private val playbackManager: PlaybackManager
) : ViewModel() {
    private val _playerEvents = MutableSharedFlow<PlayerAction>(extraBufferCapacity = 64)

    val playerEvents: SharedFlow<PlayerAction> = _playerEvents.asSharedFlow()

    private val _shuffleTracks = MutableStateFlow<List<Song>>(emptyList())

    private var isShuffleSessionActive = false

    private val _remixOffset = MutableStateFlow(0)

    private val _genreOffset = MutableStateFlow(0)

    private val _selectedDailyGenreIndex = MutableStateFlow(0)

    private var lastRemixKey: Pair<Int, Int>? = null
    private var cachedRemixQueue: List<Song> = emptyList()

    private var lastGenreKey: Pair<Int, Int>? = null
    private var cachedDailyGenres: List<DailyGenreItem> = emptyList()

    private var lastStatsMap: Map<Long, Int>? = null
    private var lastSongsCount: Int = -1
    private var cachedTopArtist: Map.Entry<String, List<Song>>? = null
    private var cachedTopAlbum: Map.Entry<String, List<Song>>? = null
    private var cachedTopSong: Song? = null
    private var cachedTopMostPlayed: List<Song> = emptyList()

    init {
        viewModelScope.launch {
            songRepository.allSongsFlow.collect { songs ->
                if (songs.isNotEmpty() && _shuffleTracks.value.isEmpty()) {
                    val savedIds = appPreferences.getShuffleTrackIds()

                    val savedSongs = savedIds.mapNotNull { id -> songs.find { it.id == id } }

                    if (savedSongs.isNotEmpty() && (savedSongs.size == savedIds.size || savedSongs.size == 14 || savedSongs.size == songs.size)) {
                        _shuffleTracks.value = savedSongs
                    } else {
                        val newBatch = if (songs.size <= 14) songs.shuffled() else songs.shuffled().take(14)

                        _shuffleTracks.value = newBatch

                        appPreferences.setShuffleTrackIds(newBatch.map { it.id })
                    }
                }
            }
        }

        viewModelScope.launch {
            playbackManager.onPlaybackEndedEvent.collect {
                if (playbackManager.isShuffleSessionActive.value) {
                    val allSongs = songRepository.getAllSongs()

                    if (allSongs.isNotEmpty()) {
                        val prevBatchIds = _shuffleTracks.value.map { it.id }.toSet()

                        val candidates = allSongs.filter { it.id !in prevBatchIds }

                        val nextBatch = if (candidates.size >= 14) {
                            candidates.shuffled().take(14)
                        } else if (allSongs.size <= 14) {
                            allSongs.shuffled()
                        } else {
                            (candidates.shuffled() + allSongs.filter { it.id in prevBatchIds }.shuffled()).take(14)
                        }

                        _shuffleTracks.value = nextBatch

                        appPreferences.setShuffleTrackIds(nextBatch.map { it.id })

                        playbackManager.playSong(nextBatch[0], nextBatch, isShuffleSession = true)
                    }
                }
            }
        }
    }

    private val statsFlow = combine(
        historyRepository.totalPlayCountFlow,
        historyRepository.totalListenedSecondsFlow,
        historyRepository.allPlayCountsFlow
    ) { count, seconds, countsMap ->
        HomeStats(count, seconds, countsMap)
    }

    private val offsetsFlow = combine(
        _remixOffset,
        _genreOffset,
        _selectedDailyGenreIndex
    ) { r, g, s ->
        HomeOffsets(r, g, s)
    }

private data class LibrarySnapshot(
    val allSongs: List<Song>,
    val songById: Map<Long, Song>,
    val recentSongs: List<Song>,
    val albumsCount: Int,
    val artistsCount: Int,
    val totalGenreCount: Int,
    val genrePreviewSongs: List<Song>
)

    private val librarySnapshotFlow = songRepository.allSongsFlow.map { allSongs ->
        val installSeed = appPreferences.getInstallSeed()
        val (genreCount, genrePreviews) = HomeContentGenerator.computeGenreSummary(allSongs, installSeed)
        LibrarySnapshot(
            allSongs = allSongs,
            songById = allSongs.associateBy { it.id },
            recentSongs = allSongs.sortedByDescending { it.dateModified }.take(100),
            albumsCount = allSongs.asSequence().map { it.album }.distinct().count(),
            artistsCount = allSongs.asSequence().map { it.artist }.distinct().count(),
            totalGenreCount = genreCount,
            genrePreviewSongs = genrePreviews
        )
    }.flowOn(Dispatchers.Default)

    private val favoriteSongIdsFlow = favoritesRepository.favoriteSongsFlow.map { list ->
        list.map { it.id }.toSet()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        librarySnapshotFlow,
        historyRepository.historySongIdsFlow,
        favoriteSongIdsFlow,
        statsFlow,
        offsetsFlow,
        _shuffleTracks
    ) { flowArray ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = flowArray[0] as LibrarySnapshot

        @Suppress("UNCHECKED_CAST")
        val historySongIds = flowArray[1] as List<Long>

        @Suppress("UNCHECKED_CAST")
        val favoriteSongIds = flowArray[2] as Set<Long>

        val stats = flowArray[3] as HomeStats

        val offsets = flowArray[4] as HomeOffsets

        @Suppress("UNCHECKED_CAST")
        val currentShuffleTracks = flowArray[5] as List<Song>

        val allSongs = snapshot.allSongs

        val installSeed = appPreferences.getInstallSeed()

        val remixKey = Pair(allSongs.size, offsets.remixOffset)
        val remixQueue = if (remixKey == lastRemixKey && cachedRemixQueue.isNotEmpty()) {
            cachedRemixQueue
        } else {
            lastRemixKey = remixKey
            HomeContentGenerator.generateRemixQueue(allSongs, installSeed, offsets.remixOffset).also {
                cachedRemixQueue = it
            }
        }

        val genreKey = Pair(allSongs.size, offsets.genreOffset)
        val dailyGenres = if (genreKey == lastGenreKey && cachedDailyGenres.isNotEmpty()) {
            cachedDailyGenres
        } else {
            lastGenreKey = genreKey
            HomeContentGenerator.generateDailyGenres(allSongs, installSeed, offsets.genreOffset).also {
                cachedDailyGenres = it
            }
        }

        val statsUnchanged = lastSongsCount == allSongs.size && lastStatsMap === stats.songPlayCounts
        val top1Artist = if (statsUnchanged) cachedTopArtist else {
            HomeContentGenerator.computeTopArtist(allSongs, stats.songPlayCounts).also { cachedTopArtist = it }
        }
        val top1Album = if (statsUnchanged) cachedTopAlbum else {
            HomeContentGenerator.computeTopAlbum(allSongs, stats.songPlayCounts).also { cachedTopAlbum = it }
        }
        val top1Song = if (statsUnchanged) cachedTopSong else {
            HomeContentGenerator.computeTopSong(allSongs, stats.songPlayCounts).also { cachedTopSong = it }
        }
        val topMostPlayed = if (statsUnchanged && cachedTopMostPlayed.isNotEmpty()) cachedTopMostPlayed else {
            allSongs.filter { (stats.songPlayCounts[it.id] ?: 0) > 0 }
                .sortedByDescending { stats.songPlayCounts[it.id] ?: 0 }
                .take(20).also {
                    cachedTopMostPlayed = it
                    lastStatsMap = stats.songPlayCounts
                    lastSongsCount = allSongs.size
                }
        }

        HomeUiState(
            songs = allSongs,
            favoriteSongIds = favoriteSongIds,
            totalPlayCount = stats.totalPlayCount,
            totalListenedSeconds = stats.totalListenedSeconds,
            songPlayCounts = stats.songPlayCounts,
            shuffleTracks = currentShuffleTracks,
            remixQueue = remixQueue,
            dailyGenres = dailyGenres,
            selectedDailyGenreIndex = offsets.selectedDailyGenreIndex,
            recentSongs = snapshot.recentSongs,
            top1Artist = top1Artist,
            top1Album = top1Album,
            top1Song = top1Song,
            topMostPlayed = topMostPlayed,
            totalAlbumsCount = snapshot.albumsCount,
            totalArtistsCount = snapshot.artistsCount,
            genrePreviewSongs = snapshot.genrePreviewSongs,
            totalGenreCount = snapshot.totalGenreCount,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.PlaySong -> {
                isShuffleSessionActive = false

                _playerEvents.tryEmit(PlayerAction.PlaySong(action.song, action.playlist))
            }

            is HomeAction.PlayShuffleSession -> {
                isShuffleSessionActive = true

                val batch = if (_shuffleTracks.value.isNotEmpty()) _shuffleTracks.value else action.tracks

                _shuffleTracks.value = batch

                _playerEvents.tryEmit(PlayerAction.PlayShuffleSession(action.startSong, sessionBatch = batch))
            }

            is HomeAction.RefreshShuffleTracks -> {
                viewModelScope.launch {
                    val allSongs = songRepository.getAllSongs()

                    if (allSongs.isNotEmpty()) {
                        val currentIds = _shuffleTracks.value.map { it.id }.toSet()

                        val candidates = allSongs.filter { it.id !in currentIds }

                        val newBatch = if (candidates.size >= 14) {
                            candidates.shuffled().take(14)
                        } else if (allSongs.size <= 14) {
                            allSongs.shuffled()
                        } else {
                            (candidates.shuffled() + allSongs.filter { it.id in currentIds }.shuffled()).take(14)
                        }

                        _shuffleTracks.value = newBatch

                        appPreferences.setShuffleTrackIds(newBatch.map { it.id })
                    }
                }
            }

            is HomeAction.RefreshRemixQueue -> {
                _remixOffset.value += 1
            }

            is HomeAction.NextDailyGenre -> {
                _genreOffset.value += 1
            }

            is HomeAction.ToggleFavorite -> {
                viewModelScope.launch {
                    favoritesRepository.toggleFavorite(action.song.id)
                }
            }

            is HomeAction.HandleSongAction -> {
                val handled = SongActionDispatcher.dispatch(
                    action = action.action,
                    song = action.song,
                    playerEvents = _playerEvents,
                    favoritesRepository = favoritesRepository,
                    scope = viewModelScope
                )

                if (!handled) {
                    when (action.action) {
                        SongAction.ADD_TO_PLAYLIST -> {   }

                        SongAction.REMOVE_FROM_FAVORITES -> {
                            viewModelScope.launch { favoritesRepository.toggleFavorite(action.song.id) }
                        }

                        SongAction.GO_TO_ARTIST,
                        SongAction.GO_TO_ALBUM,
                        SongAction.GO_TO_FOLDER -> {   }

                        SongAction.SONG_INFO -> {   }

                        SongAction.DELETE -> {   }

                        SongAction.PLAY,
                        SongAction.PLAY_NEXT,
                        SongAction.ADD_TO_QUEUE,
                        SongAction.TOGGLE_FAVORITE -> {   }
                    }
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            songRepository: SongRepository,
            historyRepository: HistoryRepository,
            favoritesRepository: FavoritesRepository,
            appPreferences: AppPreferences,
            playbackManager: PlaybackManager
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        songRepository = songRepository,
                        historyRepository = historyRepository,
                        favoritesRepository = favoritesRepository,
                        appPreferences = appPreferences,
                        playbackManager = playbackManager
                    ) as T
                }
            }
    }
}
