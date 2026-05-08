package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun PixelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var barWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionPx by remember { mutableIntStateOf(0) }
    val barWidthDp = with(density) { barWidthPx.toDp() }

    val seekModifier = modifier
        .fillMaxWidth()
        .height(16.dp)
        .onSizeChanged { barWidthPx = it.width }
        .pointerInput(valueRange, barWidthPx) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val width = barWidthPx.toFloat().coerceAtLeast(1f)
                val startX = down.position.x.coerceIn(0f, width)
                
                val fraction = startX / width
                val targetValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                
                isDragging = true
                dragPositionPx = startX.toInt()
                onValueChange(targetValue)
                
                drag(down.id) { change ->
                    val x = change.position.x.coerceIn(0f, width)
                    val currentFraction = x / width
                    val currentValue = valueRange.start + currentFraction * (valueRange.endInclusive - valueRange.start)
                    
                    dragPositionPx = x.toInt()
                    onValueChange(currentValue)
                    change.consume()
                }
                isDragging = false
            }
        }

    Box(modifier = seekModifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.secondary.let {
                    Color(it.red, it.green, it.blue, 0.5f)
                })
        )
        
        if (barWidthPx > 0) {
            val fraction = if (isDragging) {
                dragPositionPx.toFloat() / barWidthPx.toFloat()
            } else {
                (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            }
            
            val safeFraction = fraction.coerceIn(0f, 1f)
            val progressWidth = barWidthDp * safeFraction
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(progressWidth.coerceIn(0.dp, barWidthDp))
                    .background(MaterialTheme.colorScheme.onBackground)
            )
        }
    }
}
