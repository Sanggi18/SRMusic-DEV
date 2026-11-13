package com.example.ui.player.style.artwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.Song
import com.example.ui.common.components.AlbumArtwork

@Composable
fun DefaultArtworkStyle(
    song: Song,
    cornerRadius: Dp = 24.dp,
    isPlaying: Boolean = false,
    elevation: Dp = 12.dp,
    shadowColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)
    val spotColor = shadowColor ?: MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.35f else 0.15f)
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        spotColor = spotColor,
                        ambientColor = spotColor
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AlbumArtwork(
            artworkUri = song.contentUri,
            dataPath = song.dataPath,
            fallbackText = song.title,
            contentDescription = "Album art for ${song.title}",
            modifier = Modifier.fillMaxSize(),
            targetSizePx = 800,
            shape = shape,
            isPinned = true
        )
    }
}
