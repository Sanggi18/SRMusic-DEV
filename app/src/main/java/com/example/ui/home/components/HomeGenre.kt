package com.example.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Song
import com.example.ui.common.ArtworkPlaceholderType
import com.example.ui.common.components.ArtworkCard
import com.example.ui.common.components.DynamicArtworkHero
import com.example.ui.common.components.PlayShuffleButtonRow
import com.example.ui.home.core.DailyGenreItem
import com.example.ui.library.core.SongAction
import com.example.ui.library.song.SongItem

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha

@Composable
fun GenreStackedArtworkPreview(
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val items = remember(songs) { songs.take(3) }

    val configs = listOf(
        Triple(50.dp, 0.dp, 1f),
        Triple(46.dp, 13.dp, 0.85f),
        Triple(42.dp, 26.dp, 0.6f)
    )

    Box(
        modifier = modifier
            .width(76.dp)
            .height(54.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        for (i in (items.size - 1) downTo 0) {
            val song = items[i]
            val (size, offsetX, alphaVal) = configs[i]

            ArtworkCard(
                artworkUri = song.artworkUri,
                contentUri = song.contentUri,
                dataPath = song.dataPath,
                title = song.title,
                size = size,
                cornerRadius = 8.dp,
                placeholderType = ArtworkPlaceholderType.ALBUM,
                modifier = Modifier
                    .offset(x = offsetX)
                    .alpha(alphaVal)
            )
        }
    }
}

@Composable
fun GenreTodayCard(
    dailyGenres: List<DailyGenreItem>,
    genrePreviewSongs: List<Song> = emptyList(),
    totalGenreCount: Int = 1,
    allSongs: List<Song>,
    cardColor: Color,
    cardTextColor: Color,
    cardSubtextColor: Color,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTodayGenre = dailyGenres.firstOrNull()

    val todayGenreName = activeTodayGenre?.name ?: "Music"

    val todaySongCount = activeTodayGenre?.songs?.size ?: allSongs.size

    val todaySongs = remember(activeTodayGenre, allSongs) {
        activeTodayGenre?.songs?.take(3)?.ifEmpty { allSongs.take(3) } ?: allSongs.take(3)
    }

    val allGenreSongs = if (genrePreviewSongs.isNotEmpty()) genrePreviewSongs else todaySongs

    HomePagerCard(
        cardColor = cardColor,
        cardTextColor = cardTextColor,
        testTag = "home_genre_today_card",
        pageContent = { actualPage ->
            val isTodaySlide = actualPage == 0

            val title = if (isTodaySlide) "TODAY'S GENRE" else "ALL GENRE"

            val subtitle = if (isTodaySlide) {
                "$todayGenreName · $todaySongCount Songs"
            } else {
                "$totalGenreCount Genres"
            }

            val songsForPreview = if (isTodaySlide) todaySongs else allGenreSongs

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onCardClick(actualPage) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GenreStackedArtworkPreview(
                    songs = songsForPreview
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = cardTextColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = cardSubtextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun GenreTodayScreen(
    genreName: String,
    genreSongs: List<Song>,
    artists: List<String>,
    currentPlayingSongId: Long?,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongAction: (SongAction, Song) -> Unit,
    onRefreshGenre: () -> Unit,
    favoriteSongIds: Set<Long> = emptySet(),
    onToggleFavorite: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        onBackClick()
    }

    val currentPlayingSong = remember(genreSongs, currentPlayingSongId) {
        genreSongs.firstOrNull { it.id == currentPlayingSongId }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("genre_today_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                DynamicArtworkHero(
                    songs = genreSongs,
                    onBackClick = onBackClick,
                    currentPlayingSong = currentPlayingSong,
                    placeholderType = ArtworkPlaceholderType.ALBUM,
                    extraAction = {
                        IconButton(
                            onClick = onRefreshGenre,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .testTag("genre_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Next Genre",
                                tint = Color.White
                            )
                        }
                    }
                )
            }

            item {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                            playAllTestTag = "genre_play_all_btn",
                            shuffleTestTag = "genre_shuffle_btn"
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
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("genre_song_item_$index")
                )
            }
        }
    }
}
