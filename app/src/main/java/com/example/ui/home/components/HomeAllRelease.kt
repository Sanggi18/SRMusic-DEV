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
import com.example.core.model.AppGridMode
import com.example.core.model.Song
import com.example.ui.common.ArtworkPlaceholderType
import com.example.ui.common.YearSortOrder
import com.example.ui.common.components.DynamicArtworkHero
import com.example.ui.common.components.PlayShuffleButtonRow
import com.example.ui.library.core.SongAction
import com.example.ui.library.song.SongItem

@Composable
fun AllReleaseScreen(
    allSongs: List<Song>,
    currentPlayingSongId: Long?,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongAction: (SongAction, Song) -> Unit,
    favoriteSongIds: Set<Long> = emptySet(),
    onToggleFavorite: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var gridMode by remember { mutableStateOf(AppGridMode.GRID_2) }
    var sortOrder by remember { mutableStateOf(YearSortOrder.DESCENDING) }
    var expandedYears by remember { mutableStateOf(emptySet<Int>()) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val rawYearGroups = remember(allSongs) {
        allSongs
            .groupBy { it.year?.takeIf { y -> y > 0 } ?: 0 }
            .mapValues { (_, list) -> list.sortedBy { it.title.lowercase() } }
    }

    val yearGroups = remember(rawYearGroups, sortOrder) {
        val sortedYears = rawYearGroups.keys.filter { it > 0 }.let { validYears ->
            if (sortOrder == YearSortOrder.DESCENDING) validYears.sortedDescending() else validYears.sorted()
        }
        val result = ArrayList<Pair<Int, List<Song>>>(sortedYears.size + 1)
        for (year in sortedYears) {
            val songsInYear = rawYearGroups[year] ?: emptyList()
            result.add(year to songsInYear)
        }
        val unknownYearSongs = rawYearGroups[0]
        if (!unknownYearSongs.isNullOrEmpty()) {
            result.add(0 to unknownYearSongs)
        }
        result
    }

    val flattenedSortedSongs = remember(yearGroups) {
        yearGroups.flatMap { it.second }
    }

    var displayedYear by remember { mutableStateOf<Int?>(null) }
    if (selectedYear != null) {
        displayedYear = selectedYear
    }

    BackHandler(enabled = selectedYear == null) {
        onBackClick()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .testTag("all_release_screen")
        ) {
        ReleaseHeaderControls(
            totalSongs = allSongs.size,
            totalYears = yearGroups.size,
            flattenedSongs = flattenedSortedSongs,
            sortOrder = sortOrder,
            gridMode = gridMode,
            onBackClick = onBackClick,
            onPlayAll = {
                val first = flattenedSortedSongs.firstOrNull()
                if (first != null) onSongClick(first, flattenedSortedSongs)
            },
            onSortToggle = {
                sortOrder = if (sortOrder == YearSortOrder.DESCENDING) {
                    YearSortOrder.ASCENDING
                } else {
                    YearSortOrder.DESCENDING
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
                        items = yearGroups,
                        key = { _, pair -> "year_group_${pair.first}" },
                        contentType = { _, _ -> "year_group" }
                    ) { _, (year, songsInYear) ->
                        val yearLabel = if (year > 0) "$year" else "Unknown"
                        val isExpanded = expandedYears.contains(year)
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            label = "arrow_rotation_$year"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("year_group_$year")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        expandedYears = if (isExpanded) expandedYears - year else expandedYears + year
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.width(108.dp)
                                ) {
                                    Text(
                                        text = yearLabel,
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
                                            text = "${songsInYear.size}",
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
                            val visibleCount = songsInYear.size.coerceIn(1, 10)
                            val listHeight = (visibleCount * 65 + 8).dp
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(listHeight),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = songsInYear,
                                    key = { _, song -> "year_${year}_song_${song.id}" },
                                    contentType = { _, _ -> "year_song_item" }
                                ) { index, song ->
                                    SongItem(
                                        song = song,
                                        isCurrentSong = song.id == currentPlayingSongId,
                                        onSongClick = { onSongClick(song, songsInYear) },
                                        onActionClick = onSongAction,
                                        isFavorite = favoriteSongIds.contains(song.id),
                                        onToggleFavorite = onToggleFavorite,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp)
                                            .testTag("all_release_song_${year}_$index")
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
                        items = yearGroups,
                        key = { pair -> "release_grid_${pair.first}" }
                    ) { (year, songsInYear) ->
                        val yearLabel = if (year > 0) "$year" else "Unknown"
                        YearGridCard(
                            title = yearLabel,
                            songs = songsInYear,
                            gridMode = gridMode,
                            onClick = { selectedYear = year }
                        )
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = selectedYear != null,
            enter = com.example.ui.common.theme.ExpressiveMotion.ScreenEnterTransition,
            exit = com.example.ui.common.theme.ExpressiveMotion.ScreenExitTransition
        ) {
            val activeYear = displayedYear
            if (activeYear != null) {
                val songsForSelected = remember(activeYear, yearGroups) {
                    yearGroups.firstOrNull { it.first == activeYear }?.second ?: emptyList()
                }
                YearDetailChildScreen(
                    year = activeYear,
                    yearSongs = songsForSelected,
                    currentPlayingSongId = currentPlayingSongId,
                    onBackClick = { selectedYear = null },
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
private fun ReleaseHeaderControls(
    totalSongs: Int,
    totalYears: Int,
    flattenedSongs: List<Song>,
    sortOrder: YearSortOrder,
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
                    text = "All Release",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "$totalSongs Songs",
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
                        .testTag("all_release_play_all_btn")
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
                        .testTag("all_release_sort_btn")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (sortOrder == YearSortOrder.DESCENDING) "Newest" else "Oldest",
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
                        .testTag("all_release_grid_mode_btn"),
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
fun YearGridCard(
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
            .testTag("release_grid_card_$title"),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSmall) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = if (isSmall) {
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    } else {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${songs.size} Songs",
                    style = if (isSmall) {
                        MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    } else {
                        MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun YearDetailChildScreen(
    year: Int,
    yearSongs: List<Song>,
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

    val currentPlayingSong = remember(yearSongs, currentPlayingSongId) {
        yearSongs.firstOrNull { it.id == currentPlayingSongId }
    }

    val yearTitle = if (year > 0) "$year" else "Unknown Year"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("year_detail_screen_$year")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "year_detail_hero") {
                DynamicArtworkHero(
                    songs = yearSongs,
                    onBackClick = onBackClick,
                    currentPlayingSong = currentPlayingSong,
                    placeholderType = ArtworkPlaceholderType.ALBUM
                )
            }

            item(key = "year_detail_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = yearTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${yearSongs.size} Songs",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (yearSongs.isNotEmpty()) {
                        PlayShuffleButtonRow(
                            onPlayAll = {
                                val first = yearSongs.firstOrNull()
                                if (first != null) onSongClick(first, yearSongs)
                            },
                            onShuffle = {
                                val shuffled = yearSongs.shuffled()
                                val first = shuffled.firstOrNull()
                                if (first != null) onSongClick(first, shuffled)
                            },
                            playAllTestTag = "year_detail_play_all",
                            shuffleTestTag = "year_detail_shuffle"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            itemsIndexed(
                items = yearSongs,
                key = { index, song -> "${song.id}_$index" },
                contentType = { _, _ -> "year_song" }
            ) { index, song ->
                SongItem(
                    song = song,
                    isCurrentSong = song.id == currentPlayingSongId,
                    onSongClick = { onSongClick(song, yearSongs) },
                    onActionClick = onSongAction,
                    isFavorite = favoriteSongIds.contains(song.id),
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("year_detail_song_$index")
                )
            }
        }
    }
}
