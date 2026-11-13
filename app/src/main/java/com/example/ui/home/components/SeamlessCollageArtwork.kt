package com.example.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.Song
import com.example.ui.common.ArtworkPlaceholderType
import com.example.ui.common.ArtworkShape
import com.example.ui.common.components.ArtworkCard

@Composable
fun SeamlessCollageArtwork(
    songs: List<Song>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    decodeSizePx: Int = 512,
    placeholderType: ArtworkPlaceholderType = ArtworkPlaceholderType.ALBUM
) {
    val sampleSongs = remember(songs) {
        val distinct = songs.distinctBy { it.albumId }.take(4)
        if (distinct.size >= 4) distinct
        else (distinct + songs.take(4 - distinct.size)).take(4)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (sampleSongs.isEmpty()) {
            ArtworkCard(
                placeholderType = placeholderType,
                shape = ArtworkShape.ROUNDED_SQUARE,
                cornerRadius = 0.dp,
                size = 180.dp,
                decodeSizePx = decodeSizePx,
                modifier = Modifier.fillMaxSize()
            )
        } else if (sampleSongs.size < 4) {
            val first = sampleSongs.first()
            ArtworkCard(
                artworkUri = first.artworkUri,
                contentUri = first.contentUri,
                dataPath = first.dataPath,
                title = first.title,
                placeholderType = placeholderType,
                shape = ArtworkShape.ROUNDED_SQUARE,
                cornerRadius = 0.dp,
                size = 180.dp,
                decodeSizePx = decodeSizePx,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ArtworkCard(
                            artworkUri = sampleSongs[0].artworkUri,
                            contentUri = sampleSongs[0].contentUri,
                            dataPath = sampleSongs[0].dataPath,
                            title = sampleSongs[0].title,
                            placeholderType = placeholderType,
                            shape = ArtworkShape.ROUNDED_SQUARE,
                            cornerRadius = 0.dp,
                            size = 180.dp,
                            decodeSizePx = decodeSizePx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ArtworkCard(
                            artworkUri = sampleSongs[1].artworkUri,
                            contentUri = sampleSongs[1].contentUri,
                            dataPath = sampleSongs[1].dataPath,
                            title = sampleSongs[1].title,
                            placeholderType = placeholderType,
                            shape = ArtworkShape.ROUNDED_SQUARE,
                            cornerRadius = 0.dp,
                            size = 180.dp,
                            decodeSizePx = decodeSizePx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ArtworkCard(
                            artworkUri = sampleSongs[2].artworkUri,
                            contentUri = sampleSongs[2].contentUri,
                            dataPath = sampleSongs[2].dataPath,
                            title = sampleSongs[2].title,
                            placeholderType = placeholderType,
                            shape = ArtworkShape.ROUNDED_SQUARE,
                            cornerRadius = 0.dp,
                            size = 180.dp,
                            decodeSizePx = decodeSizePx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ArtworkCard(
                            artworkUri = sampleSongs[3].artworkUri,
                            contentUri = sampleSongs[3].contentUri,
                            dataPath = sampleSongs[3].dataPath,
                            title = sampleSongs[3].title,
                            placeholderType = placeholderType,
                            shape = ArtworkShape.ROUNDED_SQUARE,
                            cornerRadius = 0.dp,
                            size = 180.dp,
                            decodeSizePx = decodeSizePx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
