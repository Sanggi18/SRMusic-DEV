package com.example.ui.settings.appearance

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.NowPlayingArtworkStyle
import com.example.core.model.NowPlayingProgressStyle
import com.example.core.model.NowPlayingTheme
import com.example.core.model.PlaybackProgress
import com.example.core.model.Song
import com.example.core.model.TrackInfoAlignment
import com.example.ui.player.style.progress.PlayerProgressContainer
import com.example.ui.settings.appearance.components.ArtworkStylePreviewCard
import com.example.ui.settings.appearance.components.PlayerThemePreviewCard
import com.example.ui.settings.appearance.components.ProgressStyleCard
import com.example.ui.settings.appearance.components.SectionHeaderTitle
import com.example.ui.settings.appearance.components.SegmentedAlignmentSelector
import kotlinx.coroutines.delay

private const val PREVIEW_TOTAL_DURATION_MS = 347000L
private const val PREVIEW_START_POSITION_MS = 60000L // Mulai tepat di menit 01:00

@Composable
fun NowPlayingStyleScreen(
    currentProgressStyle: NowPlayingProgressStyle,
    onProgressStyleChange: (NowPlayingProgressStyle) -> Unit,
    currentArtworkStyle: NowPlayingArtworkStyle,
    onArtworkStyleChange: (NowPlayingArtworkStyle) -> Unit,
    currentTheme: NowPlayingTheme,
    onThemeChange: (NowPlayingTheme) -> Unit,
    currentAlignment: TrackInfoAlignment,
    onAlignmentChange: (TrackInfoAlignment) -> Unit,
    previewSong: Song? = null,
    modifier: Modifier = Modifier
) {
    val displaySong = previewSong ?: Song(
        id = 101L,
        title = "Track Title",
        artist = "Artist Name",
        album = "Album Name",
        duration = PREVIEW_TOTAL_DURATION_MS,
        albumId = 0L,
        contentUri = Uri.EMPTY,
        artworkUri = Uri.EMPTY,
        displayName = "Track Title",
        size = 0L
    )

    var currentPositionMs by remember { mutableLongStateOf(PREVIEW_START_POSITION_MS) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100L)
            // Kecepatan maju 5x dari normal (500ms tiap 100ms delay)
            currentPositionMs += 500L
            if (currentPositionMs >= PREVIEW_TOTAL_DURATION_MS) {
                currentPositionMs = PREVIEW_START_POSITION_MS
            }
        }
    }

    val previewProgress = PlaybackProgress(
        currentPosition = currentPositionMs,
        duration = PREVIEW_TOTAL_DURATION_MS,
        songId = displaySong.id
    )

    val sectionCardShape = RoundedCornerShape(22.dp)
    val sectionContainerColor = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Player Theme (Tanpa outline pada section container card)
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = sectionContainerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_player_theme")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeaderTitle("Player Theme")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NowPlayingTheme.entries.forEach { theme ->
                        PlayerThemePreviewCard(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            onClick = { onThemeChange(theme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 2: Artwork Style (Tanpa outline pada section container card)
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = sectionContainerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_artwork_style")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeaderTitle("Artwork Style")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NowPlayingArtworkStyle.entries.forEach { style ->
                        ArtworkStylePreviewCard(
                            style = style,
                            isSelected = currentArtworkStyle == style,
                            onClick = { onArtworkStyleChange(style) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 3: Progress Style (Tanpa outline pada section container card)
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = sectionContainerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_progress_style")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeaderTitle("Progress Style")

                // Preview dengan tinggi terkunci agar tidak membesar saat berpindah style (termasuk Material 3)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerProgressContainer(
                        progress = previewProgress,
                        isPlaying = true,
                        progressStyle = currentProgressStyle,
                        onSeek = { newPositionMs -> currentPositionMs = newPositionMs },
                        song = displaySong,
                        activeColor = null,
                        inactiveColor = null,
                        isPreview = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val row1 = listOf(
                    NowPlayingProgressStyle.ROUNDED_BAR,
                    NowPlayingProgressStyle.CAPSULE_PILL,
                    NowPlayingProgressStyle.LINE_THUMB
                )
                val row2 = listOf(
                    NowPlayingProgressStyle.MATERIAL_3,
                    NowPlayingProgressStyle.WAVE_MINIMAL,
                    NowPlayingProgressStyle.WAVEFORM
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row1.forEach { style ->
                            ProgressStyleCard(
                                style = style,
                                isSelected = currentProgressStyle == style,
                                onClick = { onProgressStyleChange(style) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row2.forEach { style ->
                            ProgressStyleCard(
                                style = style,
                                isSelected = currentProgressStyle == style,
                                onClick = { onProgressStyleChange(style) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Track Info Alignment (Tanpa outline pada section container card)
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = sectionContainerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_track_info_alignment")
        ) {
            val textAlignAlignment = when (currentAlignment) {
                TrackInfoAlignment.LEFT -> Alignment.Start
                TrackInfoAlignment.CENTER -> Alignment.CenterHorizontally
                TrackInfoAlignment.RIGHT -> Alignment.End
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SectionHeaderTitle("Track Info Alignment")

                // Preview langsung tanpa background box tambahan
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = textAlignAlignment
                ) {
                    Text(
                        text = displaySong.title.ifBlank { "Track Title" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displaySong.artist.ifBlank { "Artist Name" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displaySong.album.ifBlank { "Album Name" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                        maxLines = 1
                    )
                }

                SegmentedAlignmentSelector(
                    currentAlignment = currentAlignment,
                    onAlignmentSelected = onAlignmentChange
                )
            }
        }
    }
}
