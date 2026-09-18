package com.pxr.cymatic.ui.components.eq

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.EqBand

@SuppressLint("DefaultLocale")
@Composable
fun EqBandRow(
    index: Int,
    band: EqBand,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onBandChange: (EqBand) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand, indication = null, interactionSource = null),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (band.enabled) "I" else "O",
                fontSize = 16.sp,
                color = if (band.enabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .background(if (band.enabled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                    .padding(28.dp, 20.dp)
                    .clickable(
                        onClick = onToggleEnabled,
                        indication = null,
                        interactionSource = null
                    )
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Band ${index + 1} (${band.type.displayName})",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = formatBandSummary(band),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Text(
                text = ">",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp)
            )
        }

        if (isExpanded) {
            EqBandDialog(
                index = index,
                band = band,
                onDismiss = onToggleExpand,
                onBandChange = onBandChange,
                onRemove = {
                    onRemove()
                    onToggleExpand()
                }
            )
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatBandSummary(band: EqBand): String {
    val freqStr = if (band.frequency >= 1000f) {
        String.format("%.1f kHz", band.frequency / 1000f)
    } else {
        String.format("%.0f Hz", band.frequency)
    }
    return "$freqStr | ${String.format("%.1f", band.gain)} dB | ${
        String.format(
            "%.2f",
            band.q
        )
    }"
}
