package com.example.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.mediastore.tags.GenreUtils
import com.example.core.model.AppGridMode
import com.example.core.model.Song
import com.example.ui.common.ArtworkPlaceholderType
import com.example.ui.common.components.DynamicArtworkHero
import com.example.ui.common.components.PlayShuffleButtonRow
import com.example.ui.library.core.SongAction
import com.example.ui.library.song.SongItem

enum class AllGenreSortMode {
    BY_NAME,
    BY_SONG_COUNT
}

@Composable
fun AllGenreScreen(
    allSongs: List<Song>,
    currentPlayingSongId: Long?,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongAction: (SongAction, Song) -> Unit,
    favoriteSongIds: Set<Long> = emptySet(),
    onToggleFavorite: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var gridMode by remember { mutableStateOf(AppGridMode.GRID_2) }
    var sortMode by remember { mutableStateOf(AllGenreSortMode.BY_NAME) }
    var expandedGenres by remember { mutableStateOf(emptySet<String>()) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val rawGenreGroups = remember(allSongs) {
        val grouped = mutableMapOf<String, MutableList<Song>>()
        val unknownList = mutableListOf<Song>()

        for (song in allSongs) {
            val genre = song.genre
            if (GenreUtils.isValidGenre(genre)) {
                val clean = GenreUtils.cleanGenre(genre!!)
                grouped.getOrPut(clean) { mutableListOf() }.add(song)
            } else {
                unknownList.add(song)
            }
        }

        val sortedMap = grouped.mapValues { (_, list) ->
            list.sortedBy { it.title.lowercase() }
        }
        val sortedUnknown = if (unknownList.isNotEmpty()) {
            unknownList.sortedBy { it.title.lowercase() }
        } else {
            emptyList()
        }
        sortedMap to sortedUnknown
    }

    val genreGroups = remember(rawGenreGroups, sortMode) {
        val (grouped, unknownList) = rawGenreGroups
        val sortedKeys = when (sortMode) {
            AllGenreSortMode.BY_NAME -> grouped.keys.sortedBy { it.lowercase() }
            AllGenreSortMode.BY_SONG_COUNT -> grouped.keys.sortedWith(
                compareByDescending<String> { grouped[it]?.size ?: 0 }
                    .thenBy { it.lowercase() }
            )
        }

        val result = ArrayList<Pair<String, List<Song>>>(sortedKeys.size + 1)
        for (genre in sortedKeys) {
            val songsInGenre = grouped[genre] ?: emptyList()
            result.add(genre to songsInGenre)
        }
        if (unknownList.isNotEmpty()) {
            result.add("Other" to unknownList)
        }
        result
    }

    val flattenedSortedSongs = remember(genreGroups) {
        genreGroups.flatMap { it.second }
    }

    var displayedGenre by remember { mutableStateOf<String?>(null) }
    if (selectedGenre != null) {
        displayedGenre = selectedGenre
    }

    BackHandler(enabled = selectedGenre == null) {
        onBackClick()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .testTag("all_genre_screen")
        ) {
        GenreHeaderControls(
            totalSongs = allSongs.size,
            totalGenres = genreGroups.size,
            flattenedSongs = flattenedSortedSongs,
            sortMode = sortMode,
            gridMode = gridMode,
            onBackClick = onBackClick,
            onPlayAll = {
                val first = flattenedSortedSongs.firstOrNull()
                if (first != null) onSongClick(first, flattenedSortedSongs)
            },
            onSortToggle = {
                sortMode = if (sortMode == AllGenreSortMode.BY_NAME) {
                    AllGenreSortMode.BY_SONG_COUNT
                } else {
                    AllGenreSortMode.BY_NAME
                }
            },
            onGridModeToggle = {
                gridMode = when (gridMode) {
                    AppGridMode.LIST -> AppGridMode.GRID_2
                    AppGridMode.GRID_2 -> AppGridMode.GRID_3
                    AppGridMode.GRID_3 -> AppGridMode.GRID_4
                    AppGridMode.GRID_4 -> AppGridMode.LIST
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (gridMode == AppGridMode.LIST) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                ) {
                    itemsIndexed(
                        items = genreGroups,
                        key = { _, pair -> "genre_group_${pair.first}" },
                        contentType = { _, _ -> "genre_group" }
                    ) { _, (genre, songsInGenre) ->
                        val isExpanded = expandedGenres.contains(genre)
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            label = "arrow_rotation_$genre"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("genre_group_$genre")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        expandedGenres = if (isExpanded) expandedGenres - genre else expandedGenres + genre
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.width(124.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = if (isExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                                        modifier = Modifier.widthIn(min = 42.dp)
                                    ) {
                                        Text(
                                            text = "${songsInGenre.size}",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                val visibleCount = songsInGenre.size.coerceIn(1, 10)
                                val listHeight = (visibleCount * 65 + 8).dp
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(listHeight),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    itemsIndexed(
                                        items = songsInGenre,
                                        key = { _, song -> "genre_${genre}_song_${song.id}" },
                                        contentType = { _, _ -> "genre_song_item" }
                                    ) { index, song ->
                                        SongItem(
                                            song = song,
                                            isCurrentSong = song.id == currentPlayingSongId,
                                            onSongClick = { onSongClick(song, songsInGenre) },
                                            onActionClick = onSongAction,
                                            isFavorite = favoriteSongIds.contains(song.id),
                                            onToggleFavorite = onToggleFavorite,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                                .testTag("all_genre_song_${genre}_$index")
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridMode.columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = genreGroups,
                        key = { pair -> "genre_grid_${pair.first}" }
                    ) { (genre, songsInGenre) ->
                        GenreGridCard(
                            title = genre,
                            songs = songsInGenre,
                            gridMode = gridMode,
                            onClick = { selectedGenre = genre }
                        )
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = selectedGenre != null,
            enter = com.example.ui.common.theme.ExpressiveMotion.ScreenEnterTransition,
            exit = com.example.ui.common.theme.ExpressiveMotion.ScreenExitTransition
        ) {
            val activeGenreName = displayedGenre
            if (activeGenreName != null) {
                val songsForSelected = remember(activeGenreName, genreGroups) {
                    genreGroups.firstOrNull { it.first == activeGenreName }?.second ?: emptyList()
                }
                GenreDetailChildScreen(
                    genreName = activeGenreName,
                    genreSongs = songsForSelected,
                    currentPlayingSongId = currentPlayingSongId,
                    onBackClick = { selectedGenre = null },
                    onSongClick = onSongClick,
                    onSongAction = onSongAction,
                    favoriteSongIds = favoriteSongIds,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GenreHeaderControls(
    totalSongs: Int,
    totalGenres: Int,
    flattenedSongs: List<Song>,
    sortMode: AllGenreSortMode,
    gridMode: AppGridMode,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onSortToggle: () -> Unit,
    onGridModeToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "All Genre",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "$totalGenres Genres",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (flattenedSongs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("all_genre_play_all_btn")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onSortToggle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("all_genre_sort_btn")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (sortMode == AllGenreSortMode.BY_NAME) "Name" else "Count",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onGridModeToggle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .width(52.dp)
                        .testTag("all_genre_grid_mode_btn"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    val modeIcon = when (gridMode) {
                        AppGridMode.LIST -> Icons.Rounded.ViewList
                        AppGridMode.GRID_2 -> Icons.Rounded.GridView
                        AppGridMode.GRID_3 -> Icons.Rounded.ViewModule
                        AppGridMode.GRID_4 -> Icons.Rounded.Widgets
                    }
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = gridMode.label,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun GenreGridCard(
    title: String,
    songs: List<Song>,
    gridMode: AppGridMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSmall = gridMode == AppGridMode.GRID_3 || gridMode == AppGridMode.GRID_4
    val cornerRadius = if (isSmall) 10.dp else 16.dp

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .testTag("genre_grid_card_$title"),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isSmall) 6.dp else 10.dp)
        ) {
            SeamlessCollageArtwork(
                songs = songs,
                cornerRadius = cornerRadius,
                decodeSizePx = if (isSmall) 384 else 512,
                placeholderType = ArtworkPlaceholderType.ALBUM,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(if (isSmall) 4.dp else 8.dp))

            Text(
                text = title,
                style = if (isSmall) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                } else {
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                },
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSmall) 6.dp else 10.dp)
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = "${songs.size} Songs",
                style = if (isSmall) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                } else {
                    MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSmall) 6.dp else 10.dp)
            )
        }
    }
}

@Composable
fun GenreDetailChildScreen(
    genreName: String,
    genreSongs: List<Song>,
    currentPlayingSongId: Long?,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongAction: (SongAction, Song) -> Unit,
    favoriteSongIds: Set<Long>,
    onToggleFavorite: ((Song) -> Unit)?,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        onBackClick()
    }

    val listState = rememberLazyListState()

    val currentPlayingSong = remember(genreSongs, currentPlayingSongId) {
        genreSongs.firstOrNull { it.id == currentPlayingSongId }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("genre_detail_screen_$genreName")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "genre_detail_hero") {
                DynamicArtworkHero(
                    songs = genreSongs,
                    onBackClick = onBackClick,
                    currentPlayingSong = currentPlayingSong,
                    placeholderType = ArtworkPlaceholderType.ALBUM
                )
            }

            item(key = "genre_detail_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = genreName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${genreSongs.size} Songs",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (genreSongs.isNotEmpty()) {
                        PlayShuffleButtonRow(
                            onPlayAll = {
                                val first = genreSongs.firstOrNull()
                                if (first != null) onSongClick(first, genreSongs)
                            },
                            onShuffle = {
                                val shuffled = genreSongs.shuffled()
                                val first = shuffled.firstOrNull()
                                if (first != null) onSongClick(first, shuffled)
                            },
                            playAllTestTag = "genre_detail_play_all",
                            shuffleTestTag = "genre_detail_shuffle"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            itemsIndexed(
                items = genreSongs,
                key = { index, song -> "${song.id}_$index" },
                contentType = { _, _ -> "genre_song" }
            ) { index, song ->
                SongItem(
                    song = song,
                    isCurrentSong = song.id == currentPlayingSongId,
                    onSongClick = { onSongClick(song, genreSongs) },
                    onActionClick = onSongAction,
                    isFavorite = favoriteSongIds.contains(song.id),
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("genre_detail_song_$index")
                )
            }
        }
    }
}
