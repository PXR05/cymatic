package com.pxr.cymatic.ui.components.eq

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.ui.components.primitives.PixelDialog
import com.pxr.cymatic.ui.components.primitives.PixelDialogButton
import com.pxr.cymatic.ui.components.primitives.PixelSlider

@SuppressLint("DefaultLocale")
@Composable
fun EqBandDialog(
    fontFamily: FontFamily,
    index: Int,
    band: EqBand,
    onDismiss: () -> Unit,
    onBandChange: (EqBand) -> Unit,
    onRemove: () -> Unit
) {
    PixelDialog(
        title = "Band ${index + 1} Controls",
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.7f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    "Type",
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterTypeSelector(fontFamily = fontFamily, selected = band.type) { newType ->
                    onBandChange(band.copy(type = newType))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    String.format("Frequency: %.0f Hz", band.frequency),
                    fontFamily = fontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                PixelSlider(
                    value = band.frequency,
                    onValueChange = { onBandChange(band.copy(frequency = it)) },
                    valueRange = 20f..20_000f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    String.format("Gain: %.1f dB", band.gain),
                    fontFamily = fontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                PixelSlider(
                    value = band.gain,
                    onValueChange = { onBandChange(band.copy(gain = it)) },
                    valueRange = -12f..12f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    String.format("Q: %.2f", band.q),
                    fontFamily = fontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                PixelSlider(
                    value = band.q,
                    onValueChange = { onBandChange(band.copy(q = it)) },
                    valueRange = 0.1f..2f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Remove Band",
                    fontFamily = fontFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable(onClick = onRemove, indication = null, interactionSource = null)
                        .padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        buttons = {
            PixelDialogButton(
                text = "Done",
                fontFamily = fontFamily,
                onClick = onDismiss
            )
        }
    )
}
