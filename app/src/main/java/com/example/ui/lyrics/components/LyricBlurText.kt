package com.example.ui.lyrics.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LyricBlurText(
    text: String,
    style: TextStyle,
    color: Color,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
    annotatedText: AnnotatedString? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurRadius > 0.5.dp) {
                    Modifier.blur(
                        radiusX = blurRadius,
                        radiusY = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (annotatedText != null) {
            Text(
                text = annotatedText,
                style = style,
                color = color,
                maxLines = 3
            )
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 3
            )
        }
    }
}
