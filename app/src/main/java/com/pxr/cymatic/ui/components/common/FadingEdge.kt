package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

@Composable
fun Modifier.verticalFadingEdge(
    enabled: Boolean = true,
    top: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    bottom: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
): Modifier {
    if (!enabled) return this
    return this
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
}
