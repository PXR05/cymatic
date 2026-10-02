package com.pxr.cymatic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

@Composable
internal fun PixelMeter(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    onFractionChange: ((Float) -> Unit)? = null
) {
    val onChange by rememberUpdatedState(onFractionChange)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                if (onFractionChange != null) setProgress { value ->
                    onChange?.invoke(value.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(onFractionChange != null) {
                if (onFractionChange == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun fractionAt(x: Float) = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    var target = fractionAt(down.position.x)
                    dragFraction = target
                    try {
                        val released = drag(down.id) { change ->
                            target = fractionAt(change.position.x)
                            dragFraction = target
                            change.consume()
                        }
                        if (released) onChange?.invoke(target)
                    } finally {
                        dragFraction = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            drawRect(color.copy(alpha = 0.10f))
            val pixel = 1.dp.toPx()
            val gap = 3.dp.toPx()
            var row = 0
            var y = 0f
            while (y + pixel <= size.height) {
                var x = if (row % 2 == 0) 0f else gap / 2
                while (x + pixel <= size.width) {
                    drawRect(color.copy(alpha = 0.45f), Offset(x, y), Size(pixel, pixel))
                    x += gap
                }
                row++
                y += gap
            }
            if (progress > 0f) drawRect(color, size = Size(size.width * progress, size.height))
        }
    }
}
