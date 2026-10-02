package com.pxr.cymatic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BatteryIndicator(battery: BatteryState) {
    val color = MaterialTheme.colorScheme.onBackground
    val percent = battery.percent?.let { "$it%" } ?: "--%"
    Row(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Battery $percent${if (battery.charging) ", charging" else ""}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(Modifier.size(22.dp, 12.dp)) {
            val stroke = 1.dp.toPx()
            val bodyWidth = size.width - 3.dp.toPx()
            drawRect(
                color,
                Offset(stroke / 2, stroke / 2),
                Size(bodyWidth - stroke, size.height - stroke),
                style = Stroke(stroke)
            )
            drawRect(color, Offset(bodyWidth, size.height / 3), Size(2.dp.toPx(), size.height / 3))
            val inset = 3.dp.toPx()
            val fill = (bodyWidth - inset * 2) * (battery.percent ?: 0) / 100f
            if (fill > 0) drawRect(color, Offset(inset, inset), Size(fill, size.height - inset * 2))
        }
        Text(percent, fontSize = 11.sp, maxLines = 1)
    }
}
