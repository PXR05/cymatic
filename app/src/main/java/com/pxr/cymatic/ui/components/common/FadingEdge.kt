package com.pxr.cymatic.ui.components.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalFadingEdge(
    top: Dp = 48.dp,
    bottom: Dp = 48.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        val height = size.height

        if (height > 0f && (topPx > 0f || bottomPx > 0f)) {
            val topStop = if (topPx > 0f) (topPx / height).coerceIn(0f, 1f) else 0f
            val bottomStop = if (bottomPx > 0f) ((height - bottomPx) / height).coerceIn(0f, 1f) else 1f

            val brush = Brush.verticalGradient(
                0f to (if (topPx > 0f) Color.Transparent else Color.Black),
                topStop to Color.Black,
                bottomStop to Color.Black,
                1f to (if (bottomPx > 0f) Color.Transparent else Color.Black)
            )

            drawRect(
                brush = brush,
                blendMode = BlendMode.DstIn
            )
        }
    }
