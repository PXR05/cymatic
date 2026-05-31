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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import com.pxr.cymatic.ui.components.primitives.CymaticSlider

@SuppressLint("DefaultLocale")
@Composable
fun EqBandDialog(
    index: Int,
    band: EqBand,
    onDismiss: () -> Unit,
    onBandChange: (EqBand) -> Unit,
    onRemove: () -> Unit
) {
    var editingField by remember { mutableStateOf<String?>(null) }
    var inputValue by remember { mutableStateOf("") }

    if (editingField != null) {
        val field = editingField!!
        val title = when (field) {
            "frequency" -> "Edit Frequency"
            "gain" -> "Edit Gain"
            "q" -> "Edit Q Factor"
            else -> ""
        }
        val hint = when (field) {
            "frequency" -> "Enter frequency (20 - 20000 Hz)"
            "gain" -> "Enter gain (-12 - 12 dB)"
            "q" -> "Enter Q factor (0.1 - 2.0)"
            else -> ""
        }
        CymaticInputDialog(
            title = title,
            hint = hint,
            value = inputValue,
            onValueChange = { inputValue = it },
            onConfirm = {
                val floatVal = inputValue.toFloatOrNull()
                if (floatVal != null) {
                    when (field) {
                        "frequency" -> {
                            val coerced = floatVal.coerceIn(20f, 20000f)
                            onBandChange(band.copy(frequency = coerced))
                        }
                        "gain" -> {
                            val coerced = floatVal.coerceIn(-12f, 12f)
                            onBandChange(band.copy(gain = coerced))
                        }
                        "q" -> {
                            val coerced = floatVal.coerceIn(0.1f, 2f)
                            onBandChange(band.copy(q = coerced))
                        }
                    }
                }
                editingField = null
            },
            onDismiss = { editingField = null }
        )
    }

    CymaticDialog(
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
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterTypeSelector(selected = band.type) { newType ->
                    onBandChange(band.copy(type = newType))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    String.format("Frequency: %.0f Hz", band.frequency),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputValue = String.format("%.0f", band.frequency)
                            editingField = "frequency"
                        }
                        .padding(vertical = 4.dp)
                )
                CymaticSlider(
                    value = band.frequency,
                    onValueChange = { onBandChange(band.copy(frequency = it)) },
                    valueRange = 20f..20_000f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    String.format("Gain: %.1f dB", band.gain),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputValue = String.format("%.1f", band.gain)
                            editingField = "gain"
                        }
                        .padding(vertical = 4.dp)
                )
                CymaticSlider(
                    value = band.gain,
                    onValueChange = { onBandChange(band.copy(gain = it)) },
                    valueRange = -12f..12f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    String.format("Q: %.2f", band.q),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputValue = String.format("%.2f", band.q)
                            editingField = "q"
                        }
                        .padding(vertical = 4.dp)
                )
                CymaticSlider(
                    value = band.q,
                    onValueChange = { onBandChange(band.copy(q = it)) },
                    valueRange = 0.1f..2f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Remove Band",
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
            CymaticDialogButton(
                text = "Done",
                onClick = onDismiss
            )
        }
    )
}
