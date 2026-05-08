package com.pxr.cymatic.ui.components.player

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import kotlin.math.roundToLong

@SuppressLint("DefaultLocale")
@Composable
fun ProgressBar(
    currentPosition: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seekEnabled: Boolean = true,
    showNumber: Boolean = true
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val density = LocalDensity.current
    var barWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionPx by remember { mutableIntStateOf(0) }
    val barWidthDp = with(density) { barWidthPx.toDp() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy((-16).dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val seekModifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .onSizeChanged { barWidthPx = it.width }
                .pointerInput(durationMs, barWidthPx, seekEnabled) {
                    if (durationMs <= 0L || !seekEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = barWidthPx.toFloat().coerceAtLeast(1f)
                        val startX = down.position.x.coerceIn(0f, width)
                        var targetMs = (durationMs * (startX / width)).roundToLong()
                        isDragging = true
                        dragPositionPx = startX.roundToLong().toInt()
                        drag(down.id) { change ->
                            val x = change.position.x.coerceIn(0f, width)
                            targetMs = (durationMs * (x / width)).roundToLong()
                            dragPositionPx = x.roundToLong().toInt()
                            change.consume()
                        }
                        onSeek(targetMs)
                        isDragging = false
                    }
                }

            Box(modifier = seekModifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.secondary.let {
                            Color(
                                it.red,
                                it.green,
                                it.blue,
                                0.5f
                            )
                        })
                )
                if (durationMs > 0 && barWidthPx > 0) {
                    val fraction = if (isDragging) {
                        dragPositionPx.toFloat() / barWidthPx.toFloat()
                    } else {
                        currentPosition.toFloat() / durationMs
                    }
                    val progressWidth = barWidthDp * fraction
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(progressWidth.coerceIn(0.dp, barWidthDp))
                            .background(MaterialTheme.colorScheme.onBackground)
                    )
                }
            }

            if (showNumber) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp, 24.dp, 4.dp, 0.dp)
                ) {
                    Text(
                        text = currentPosition.let { pos ->
                            val totalSeconds = pos / 1000
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            String.format("%d:%02d", minutes, seconds)
                        },
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                        )
                    )

                    Text(
                        text = durationMs.let { dur ->
                            val totalSeconds = dur / 1000
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            String.format("%d:%02d", minutes, seconds)
                        },
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                        )
                    )
                }
            }
        }
    }
}
