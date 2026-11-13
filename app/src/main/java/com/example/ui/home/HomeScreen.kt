package com.example.ui.home

import com.example.ui.home.components.*
import com.example.ui.home.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import com.example.ui.common.theme.ExpressiveMotion
import com.example.ui.common.theme.springPress
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.ui.common.LocalMiniPlayerVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.example.core.model.Song
import com.example.ui.library.core.SongAction

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    onMenuClick: () -> Unit,
    onAction: (HomeAction) -> Unit = {},
    onSongAction: (SongAction, Song) -> Unit = { action, s -> onAction(HomeAction.HandleSongAction(action, s)) },
    activeChildScreen: HomeChildScreen = HomeChildScreen.NONE,
    onChildScreenChange: (HomeChildScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allSongsList = uiState.songs

    val remixQueue = uiState.remixQueue

    val dailyGenres = uiState.dailyGenres

    val recentSongs = uiState.recentSongs

    BackHandler(enabled = activeChildScreen != HomeChildScreen.NONE) {
        onChildScreenChange(HomeChildScreen.NONE)
    }

    val cardColor = MaterialTheme.colorScheme.surfaceContainer

    val cardTextColor = MaterialTheme.colorScheme.onSurface

    val cardSubtextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val homeListState = rememberLazyListState()

    var displayedChildScreen by remember { mutableStateOf(activeChildScreen) }
    if (activeChildScreen != HomeChildScreen.NONE) {
        displayedChildScreen = activeChildScreen
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("home_screen")
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val menuInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = menuInteractionSource,
                        indication = ripple(),
                        onClick = onMenuClick
                    )
                    .springPress(menuInteractionSource, pressedScale = 0.88f)
                    .testTag("home_menu_button"),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (allSongsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs found in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val fourArtworks = remember(remixQueue) { remixQueue.take(4) }

            val topMostPlayed = uiState.topMostPlayed

            val totalAlbumsCount = uiState.totalAlbumsCount

            val totalArtistsCount = uiState.totalArtistsCount

            val hasMiniPlayer = com.example.ui.common.LocalMiniPlayerVisible.current

            val navBarBottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val homeBottomPadding = if (hasMiniPlayer) 156.dp + navBarBottomInset else 96.dp + navBarBottomInset

            LazyColumn(
                state = homeListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_content_list"),
                contentPadding = PaddingValues(bottom = homeBottomPadding)
            ) {
                item(key = "section_top_grid") {
                    val leftWeight = 1f

                    val rightWeight = 1.25f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .layout { measurable, constraints ->
                                val availableWidth = (constraints.maxWidth.toDp() - 12.dp).coerceAtLeast(0.dp)

                                val leftWidth = availableWidth * (leftWeight / (leftWeight + rightWeight))

                                val dynamicRowHeightPx = (leftWidth + 32.dp).roundToPx()

                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minHeight = dynamicRowHeightPx,
                                        maxHeight = dynamicRowHeightPx
                                    )
                                )

                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ForYouCard(
                            fourArtworks = fourArtworks,
                            cardColor = cardColor,
                            cardTextColor = cardTextColor,
                            onCardClick = { onChildScreenChange(HomeChildScreen.FOR_YOU_REMIX) },
                            modifier = Modifier
                                .weight(leftWeight)
                                .fillMaxHeight()
                        )

                        Column(
                            modifier = Modifier
                                .weight(rightWeight)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GenreTodayCard(
                                dailyGenres = dailyGenres,
                                genrePreviewSongs = uiState.genrePreviewSongs,
                                totalGenreCount = uiState.totalGenreCount,
                                allSongs = allSongsList,
                                cardColor = cardColor,
                                cardTextColor = cardTextColor,
                                cardSubtextColor = cardSubtextColor,
                                onCardClick = { clickedSlide ->
                                    if (clickedSlide == 0) {
                                        onChildScreenChange(HomeChildScreen.GENRE_TODAY)
                                    } else {
                                        onChildScreenChange(HomeChildScreen.ALL_GENRE)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            NewReleaseCard(
                                songs = allSongsList,
                                cardColor = cardColor,
                                cardTextColor = cardTextColor,
                                cardSubtextColor = cardSubtextColor,
                                onCardClick = { clickedSlide ->
                                    if (clickedSlide == 0) {
                                        onChildScreenChange(HomeChildScreen.NEW_RELEASE)
                                    } else {
                                        onChildScreenChange(HomeChildScreen.ALL_RELEASE)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }

                item(key = "section_shuffle_mosaic") {
                    Spacer(modifier = Modifier.height(12.dp))

                    HomeShuffleSection(
                        shuffleTracks = uiState.shuffleTracks,
                        allSongs = allSongsList,
                        currentPlayingSongId = currentPlayingSongId,
                        cardColor = cardColor,
                        cardTextColor = cardTextColor,
                        cardSubtextColor = cardSubtextColor,
                        onRefreshShuffle = { onAction(HomeAction.RefreshShuffleTracks) },
                        onSongClick = { song, queue ->
                            onAction(HomeAction.PlaySong(song, queue))
                        },
                        onShuffleTrackClick = { clickedSong ->
                            onAction(HomeAction.PlayShuffleSession(clickedSong, uiState.shuffleTracks))
                        }
                    )
                }

                item(key = "section_most_played_and_statistic") {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HomeMostPlayedCard(
                            songs = topMostPlayed,
                            currentPlayingSongId = currentPlayingSongId,
                            cardColor = cardColor,
                            cardTextColor = cardTextColor,
                            cardSubtextColor = cardSubtextColor,
                            onSongClick = { song ->
                                onAction(HomeAction.PlaySong(song, topMostPlayed))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        HomeStatisticCard(
                            playSongsCount = uiState.totalPlayCount,
                            listenedSeconds = uiState.totalListenedSeconds,
                            albumsCount = totalAlbumsCount,
                            artistsCount = totalArtistsCount,
                            cardColor = cardColor,
                            cardTextColor = cardTextColor,
                            cardSubtextColor = cardSubtextColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                item(key = "recently_added_card") {
                    Spacer(modifier = Modifier.height(12.dp))

                    HomeRecentlyAddedCard(
                        songs = recentSongs,
                        currentPlayingSongId = currentPlayingSongId,
                        cardColor = cardColor,
                        cardTextColor = cardTextColor,
                        cardSubtextColor = cardSubtextColor,
                        onSongClick = { s, list ->
                            onAction(HomeAction.PlaySong(s, list))
                        },
                        onSongAction = onSongAction,
                        favoriteSongIds = uiState.favoriteSongIds,
                        onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        }

        AnimatedVisibility(
            visible = activeChildScreen != HomeChildScreen.NONE,
            enter = ExpressiveMotion.ScreenEnterTransition,
            exit = ExpressiveMotion.ScreenExitTransition
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (displayedChildScreen) {
                    HomeChildScreen.FOR_YOU_REMIX -> {
                        ForYouRemixScreen(
                            remixSongs = remixQueue,
                            currentPlayingSongId = currentPlayingSongId,
                            onBackClick = { onChildScreenChange(HomeChildScreen.NONE) },
                            onSongClick = { song, list -> onAction(HomeAction.PlaySong(song, list)) },
                            onSongAction = onSongAction,
                            onRefreshQueue = { onAction(HomeAction.RefreshRemixQueue) },
                            favoriteSongIds = uiState.favoriteSongIds,
                            onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeChildScreen.GENRE_TODAY -> {
                        val activeGenre = dailyGenres.getOrNull(uiState.selectedDailyGenreIndex)
                            ?: dailyGenres.firstOrNull()
                            ?: DailyGenreItem("Music", allSongsList, allSongsList.firstOrNull(), emptyList())

                        GenreTodayScreen(
                            genreName = activeGenre.name,
                            genreSongs = activeGenre.songs,
                            artists = activeGenre.artists,
                            currentPlayingSongId = currentPlayingSongId,
                            onBackClick = { onChildScreenChange(HomeChildScreen.NONE) },
                            onSongClick = { song, list -> onAction(HomeAction.PlaySong(song, list)) },
                            onSongAction = onSongAction,
                            onRefreshGenre = { onAction(HomeAction.NextDailyGenre) },
                            favoriteSongIds = uiState.favoriteSongIds,
                            onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeChildScreen.ALL_GENRE -> {
                        AllGenreScreen(
                            allSongs = allSongsList,
                            currentPlayingSongId = currentPlayingSongId,
                            onBackClick = { onChildScreenChange(HomeChildScreen.NONE) },
                            onSongClick = { song, list -> onAction(HomeAction.PlaySong(song, list)) },
                            onSongAction = onSongAction,
                            favoriteSongIds = uiState.favoriteSongIds,
                            onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeChildScreen.NEW_RELEASE -> {
                        NewReleaseScreen(
                            allSongs = allSongsList,
                            currentPlayingSongId = currentPlayingSongId,
                            onBackClick = { onChildScreenChange(HomeChildScreen.NONE) },
                            onSongClick = { song, list -> onAction(HomeAction.PlaySong(song, list)) },
                            onSongAction = onSongAction,
                            favoriteSongIds = uiState.favoriteSongIds,
                            onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeChildScreen.ALL_RELEASE -> {
                        AllReleaseScreen(
                            allSongs = allSongsList,
                            currentPlayingSongId = currentPlayingSongId,
                            onBackClick = { onChildScreenChange(HomeChildScreen.NONE) },
                            onSongClick = { song, list -> onAction(HomeAction.PlaySong(song, list)) },
                            onSongAction = onSongAction,
                            favoriteSongIds = uiState.favoriteSongIds,
                            onToggleFavorite = { song -> onAction(HomeAction.ToggleFavorite(song)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeChildScreen.NONE -> Unit
                }
            }
        }
    }
}
