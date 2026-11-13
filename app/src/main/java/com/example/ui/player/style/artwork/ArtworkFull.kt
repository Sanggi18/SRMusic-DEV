package com.example.ui.player.style.artwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.example.core.model.Song
import com.example.ui.common.components.AlbumArtwork

@Composable
fun FullArtworkStyle(
    song: Song,
    containerColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.White,
                        0.45f to Color.White,
                        0.72f to Color.White.copy(alpha = 0.60f),
                        0.88f to Color.White.copy(alpha = 0.18f),
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AlbumArtwork(
            artworkUri = song.contentUri,
            dataPath = song.dataPath,
            fallbackText = song.title,
            contentDescription = "Album art for ${song.title}",
            modifier = Modifier.fillMaxSize(),
            targetSizePx = 800,
            shape = RectangleShape,
            isPinned = true
        )
    }
}
