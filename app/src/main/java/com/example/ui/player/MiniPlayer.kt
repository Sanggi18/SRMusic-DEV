package com.example.ui.player

import com.example.ui.player.components.*
import com.example.ui.player.core.*
import com.example.ui.common.components.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PlaybackInfo
import com.example.core.model.Song
import com.example.ui.common.ArtworkShape
import com.example.ui.common.components.ArtworkCard
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.common.theme.LocalSpacing
import com.example.ui.common.theme.springPress

@Composable
fun MiniPlayerContent(
    currentSong: Song,
    playbackInfo: PlaybackInfo,
    onAction: (PlayerAction) -> Unit,
    showArtwork: Boolean = true,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val playInteractionSource = remember { MutableInteractionSource() }
    val prevInteractionSource = remember { MutableInteractionSource() }
    val nextInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.miniPlayerHeight)
            .padding(horizontal = spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArtwork) {
            ArtworkCard(
                artworkUri = currentSong.artworkUri,
                contentUri = currentSong.contentUri,
                dataPath = currentSong.dataPath,
                title = currentSong.title,
                size = 40.dp,
                shape = ArtworkShape.CIRCLE,
                cornerRadius = 20.dp,
                decodeSizePx = 800,
                isPinned = true,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.width(spacing.medium))

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )

            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )
        }

        IconButton(
            onClick = { onAction(PlayerAction.Previous) },
            interactionSource = prevInteractionSource,
            modifier = Modifier
                .size(40.dp)
                .springPress(prevInteractionSource, pressedScale = 0.85f)
                .align(Alignment.CenterVertically)
                .testTag("mini_player_prev")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous song",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        val isPlaying = playbackInfo.isPlaying
        var wasPlaying by remember { mutableStateOf(false) }
        if (isPlaying) wasPlaying = true
        val showPauseIcon = isPlaying || (wasPlaying && playbackInfo.isBuffering)

        if (playbackInfo.isBuffering && !showPauseIcon) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterVertically)
                    .padding(8.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButton(
                onClick = { onAction(PlayerAction.PlayPause) },
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .size(40.dp)
                    .springPress(playInteractionSource, pressedScale = 0.85f)
                    .align(Alignment.CenterVertically)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (showPauseIcon) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (showPauseIcon) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(
            onClick = { onAction(PlayerAction.Next) },
            enabled = playbackInfo.hasNext,
            interactionSource = nextInteractionSource,
            modifier = Modifier
                .size(40.dp)
                .springPress(nextInteractionSource, pressedScale = 0.85f)
                .align(Alignment.CenterVertically)
                .testTag("mini_player_next")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next song",
                tint = if (playbackInfo.hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
