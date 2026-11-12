package com.pxr.cymatic.ui.components.list

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.design.R

@Composable
fun LibraryAvailabilityIcon(
    availability: LibraryAvailability,
    tint: Color = LocalContentColor.current,
    busy: Boolean = false
) {
    if (busy) {
        val transition = rememberInfiniteTransition(label = "Offline processing")
        val frame by transition.animateFloat(
            initialValue = 0f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                tween(800, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "Loading frame",
        )
        Canvas(
            Modifier
                .size(10.dp)
                .semantics { contentDescription = "Queued or downloading" }) {
            val pixel = size.width / 10f
            val points = listOf(4 to 0, 7 to 1, 8 to 4, 7 to 7, 4 to 8, 1 to 7, 0 to 4, 1 to 1)
            points.forEachIndexed { index, (x, y) ->
                val age = (frame.toInt() - index + 8) % 8
                drawRect(
                    color = tint.copy(alpha = tint.alpha * (1f - age * 0.1f)),
                    topLeft = Offset(x * pixel, y * pixel),
                    size = Size(2 * pixel, 2 * pixel),
                )
            }
        }
        return
    }
    val resource = when (availability) {
        LibraryAvailability.OFFLINE -> R.drawable.ic_pixel_offline
        LibraryAvailability.PARTIAL -> R.drawable.ic_pixel_partial_offline
        LibraryAvailability.STREAM -> R.drawable.ic_pixel_stream
    }
    Icon(
        painterResource(resource),
        contentDescription = availability.label,
        tint = tint,
        modifier = Modifier.size(10.dp)
    )
}
