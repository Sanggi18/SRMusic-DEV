package com.example.ui.library.song

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.library.core.*
import com.example.ui.library.components.*
import com.example.ui.common.components.*
import com.example.core.filter.SongFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.ui.common.LocalMiniPlayerVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.AppSortCriteria
import com.example.ui.library.core.LibraryUiState
import com.example.ui.library.core.SongAction
import com.example.core.model.Song
import com.example.ui.library.components.FastScrollBar
import com.example.ui.library.components.SearchPill
import com.example.ui.library.components.SortBar

@Composable
fun SongScreen(
    uiState: LibraryUiState,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onMenuClick: () -> Unit,
    searchQuery: String = uiState.searchQuery,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchAction: ((String) -> Unit)? = null,
    sortCriteria: AppSortCriteria = uiState.songSortCriteria,
    onSortCriteriaChange: (AppSortCriteria) -> Unit = {},
    isAscending: Boolean = uiState.songIsAscending,
    onAscendingChange: (Boolean) -> Unit = {},
    onSongAction: ((SongAction, Song) -> Unit)? = null,
    favoriteSongIds: Set<Long> = uiState.favoriteIds,
    onToggleFavorite: ((Song) -> Unit)? = null,
    onToggleShowCodec: ((Boolean) -> Unit)? = null,
    onToggleShowFavoritesToggle: ((Boolean) -> Unit)? = null,
    onToggleShowFileName: ((Boolean) -> Unit)? = null,
    onToggleShowSize: ((Boolean) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var isFastScrolling by remember { mutableStateOf(false) }

    var isHighSpeedScrolling by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 4.dp)
            .testTag("song_screen")
    ) {
        SearchPill(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onMenuClick = onMenuClick,
            onSearchAction = onSearchAction,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            placeholderText = "Search library..."
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val songs = uiState.songs

            val effectiveQuery = uiState.debouncedSearchQuery

            val sortedSongs = remember(songs, sortCriteria, isAscending, uiState.playCounts) {
                SongFilter.sortSongs(
                    songs = songs,
                    sortCriteria = sortCriteria,
                    isAscending = isAscending,
                    playCounts = uiState.playCounts
                )
            }

            val displaySongs = remember(sortedSongs, effectiveQuery) {
                SongFilter.filterSongs(
                    songs = sortedSongs,
                    searchQuery = effectiveQuery
                )
            }

                SortBar(
                    sortCriteria = sortCriteria,
                    isAscending = isAscending,
                    onSortCriteriaChange = onSortCriteriaChange,
                    onAscendingChange = onAscendingChange,
                    onShuffleClick = {
                        if (displaySongs.isNotEmpty()) {
                            val shuffled = displaySongs.shuffled()

                            onSongClick(shuffled.first(), shuffled)
                        }
                    },
                    totalCount = displaySongs.size,
                    countLabel = "songs",
                    showSongDisplayMenu = true,
                    showCodec = uiState.showSongCodec,
                    onToggleCodec = onToggleShowCodec,
                    showFavoritesToggle = uiState.showSongFavoritesToggle,
                    onToggleFavorites = onToggleShowFavoritesToggle,
                    showFileName = uiState.showSongFileName,
                    onToggleFileName = onToggleShowFileName,
                    showSize = uiState.showSongSize,
                    onToggleSize = onToggleShowSize,
                    availableCriteria = listOf(
                        AppSortCriteria.TITLE,
                        AppSortCriteria.ARTIST,
                        AppSortCriteria.ALBUM,
                        AppSortCriteria.DATE_ADDED,
                        AppSortCriteria.DATE_MODIFIED,
                        AppSortCriteria.DURATION,
                        AppSortCriteria.COUNT
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                if (displaySongs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No songs match \"$searchQuery\"" else "No songs found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val hasMiniPlayer = LocalMiniPlayerVisible.current

                        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                        val effectiveBottomPadding = if (hasMiniPlayer) 196.dp + navBarBottomInset else 120.dp + navBarBottomInset

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = effectiveBottomPadding
                            )
                        ) {
                            items(
                                items = displaySongs,
                                key = { it.id },
                                contentType = { "song" }
                            ) { song ->
                                SongItem(
                                    song = song,
                                    isCurrentSong = song.id == currentPlayingSongId,
                                    onSongClick = { onSongClick(song, displaySongs) },
                                    onActionClick = onSongAction,
                                    isFastScrolling = isFastScrolling,
                                    isHighSpeedScrolling = isHighSpeedScrolling,
                                    isFavorite = favoriteSongIds.contains(song.id),
                                    onToggleFavorite = onToggleFavorite,
                                    displayOptions = uiState.songDisplayOptions
                                )
                            }
                        }

                        FastScrollBar(
                            listState = listState,
                            itemCount = displaySongs.size,
                            previewTextProvider = { index ->
                                val song = displaySongs.getOrNull(index)

                                when (sortCriteria) {
                                    AppSortCriteria.ARTIST -> song?.artist?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

                                    AppSortCriteria.ALBUM -> song?.album?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

                                    else -> song?.title?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                                }
                            },
                            onDraggingChange = { isFastScrolling = it },
                            onHighSpeedChange = { isHighSpeedScrolling = it },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }
