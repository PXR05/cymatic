package com.pxr.cymatic.ui.components.eq

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.ui.components.common.SettingsChoicesDialog
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
internal fun WheelEqBandEditor(
    title: String,
    band: EqBand,
    onChange: (EqBand) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    canRemove: Boolean = true,
    limits: EqBandEditorLimits = EqBandEditorLimits(),
) {
    var types by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<EqNumberEdit?>(null) }
    WheelSettingsOverlay(title, onDismiss) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Enabled",
                        if (band.enabled) "On" else "Off",
                        checked = band.enabled,
                    ) {
                        onChange(band.copy(enabled = !band.enabled))
                    }
                )
                add(NavigationItem("Type", band.type.displayName) { types = true })
                add(
                    NavigationItem("Frequency", "${band.frequency.toInt()} Hz") {
                        number =
                            EqNumberEdit(
                                "Frequency",
                                ln(band.frequency.coerceIn(limits.frequency)),
                                ln(limits.frequency.start)..ln(limits.frequency.endInclusive),
                                0.025f,
                                { "${exp(it).roundToInt()} Hz" },
                                inputToValue = { ln(it) },
                                save = { onChange(band.copy(frequency = exp(it).roundToInt().toFloat())) },
                            )
                    }
                )
                if (limits.showGain)
                    add(
                        NavigationItem("Gain", formatEqGain(band.gain)) {
                            number =
                                EqNumberEdit(
                                    "Gain",
                                    band.gain,
                                    limits.gain,
                                    limits.gainStep,
                                    ::formatEqGain,
                                ) {
                                    onChange(band.copy(gain = it))
                                }
                        }
                    )
                add(
                    NavigationItem("Q", String.format(Locale.US, "%.1f", band.q)) {
                        number =
                            EqNumberEdit(
                                "Q",
                                band.q,
                                limits.q,
                                limits.qStep,
                                { String.format(Locale.US, "%.1f", it) },
                            ) {
                                onChange(band.copy(q = it))
                            }
                    }
                )
                add(NavigationItem("Remove band", enabled = canRemove, onClick = onRemove))
            }
        )
    }
    if (types)
        SettingsChoicesDialog(
            "Filter type",
            { types = false },
            limits.types.map { type ->
                NavigationItem(type.displayName, checked = type == band.type) {
                    onChange(band.copy(type = type))
                    types = false
                }
            },
        )
    number?.let { EqNumberEditor(it) { number = null } }
}
