package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.FilterType
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelNumberEditor
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private data class EqNumberEdit(
    val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val save: (Float) -> Unit
)

@Composable
internal fun WheelEqSettingsScreen(
    state: EqUiState,
    preset: EqPreset,
    viewModel: EqViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nav = LocalNavController.current
    var menu by remember { mutableStateOf<String?>(null) }
    var bandIndex by remember { mutableIntStateOf(-1) }
    var number by remember { mutableStateOf<EqNumberEdit?>(null) }
    var nameAction by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    var graph by remember { mutableStateOf(false) }
    fun db(value: Float) = String.format(Locale.US, "%.1f dB", value)
    BaseScreen(title = "Equalizer", onBackClick = { nav.popBackStack() }, modifier = modifier) {
        NavigationList(buildList {
            add(NavigationItem("Enabled", if (state.eqEnabled) "On" else "Off") { viewModel.setEnabled(!state.eqEnabled) })
            add(NavigationItem("Preset", state.selectedPresetName) { menu = "Presets" })
            add(NavigationItem("Preset actions") { menu = "Preset actions" })
            add(NavigationItem("Response graph") { graph = true })
            add(NavigationItem("Preamp", db(preset.preamp)) {
                number = EqNumberEdit("Preamp", preset.preamp, -12f..12f, 0.1f, ::db, viewModel::updatePreamp)
            })
            preset.bands.forEachIndexed { index, band ->
                add(NavigationItem("Band ${index + 1}", "${band.frequency.toInt()} Hz · ${db(band.gain)}", key = band.id) { bandIndex = index })
            }
            add(NavigationItem("Add band", enabled = preset.bands.size < EqViewModel.MAX_BANDS, onClick = viewModel::addBand))
        })
    }
    if (bandIndex in preset.bands.indices) {
        val index = bandIndex
        val band = preset.bands[index]
        WheelSettingsOverlay("Band ${index + 1}", { bandIndex = -1 }) {
            NavigationList(listOf(
                NavigationItem("Enabled", if (band.enabled) "On" else "Off") { viewModel.updateBand(index, band.copy(enabled = !band.enabled)) },
                NavigationItem("Type", band.type.displayName) { menu = "Filter type" },
                NavigationItem("Frequency", "${band.frequency.toInt()} Hz") {
                    number = EqNumberEdit("Frequency", ln(band.frequency.coerceIn(20f, 20000f)), ln(20f)..ln(20000f), 0.025f,
                        { "${exp(it).roundToInt()} Hz" }, { viewModel.updateBand(index, band.copy(frequency = exp(it).roundToInt().toFloat())) })
                },
                NavigationItem("Gain", db(band.gain)) {
                    number = EqNumberEdit("Gain", band.gain, -12f..12f, 0.1f, ::db,
                        { viewModel.updateBand(index, band.copy(gain = it)) })
                },
                NavigationItem("Q", String.format(Locale.US, "%.2f", band.q)) {
                    number = EqNumberEdit("Q", band.q, 0.1f..2f, 0.01f, { String.format(Locale.US, "%.2f", it) },
                        { viewModel.updateBand(index, band.copy(q = it)) })
                },
                NavigationItem("Remove band", enabled = preset.bands.size > 1) { viewModel.removeBand(index); bandIndex = -1 }
            ))
        }
    }
    menu?.let { title ->
        val items = when (title) {
            "Presets" -> state.presets.map { choice -> NavigationItem(choice.name,
                if (choice.name == state.selectedPresetName) "Selected" else null) { viewModel.selectPreset(choice.name); menu = null } }
            "Filter type" -> FilterType.entries.map { type -> NavigationItem(type.displayName) {
                preset.bands.getOrNull(bandIndex)?.let { viewModel.updateBand(bandIndex, it.copy(type = type)) }
                menu = null
            } }
            else -> listOf(
                NavigationItem("New preset") { menu = null; nameAction = "New preset" },
                NavigationItem("Rename preset") { menu = null; nameAction = "Rename preset" },
                NavigationItem("Delete preset", enabled = state.presets.size > 1) { menu = null; delete = true },
                NavigationItem("Import") { menu = null; onImport() },
                NavigationItem("Export") { menu = null; onExport() }
            )
        }
        WheelContextMenu(title, items, { menu = null })
    }
    nameAction?.let { action ->
        WheelTextEditor(action, if (action == "Rename preset") state.selectedPresetName else "", onSave = {
            if (action == "Rename preset") viewModel.renamePreset(state.selectedPresetName, it.trim())
            else viewModel.addPreset(it.trim())
        }, onDismiss = { nameAction = null })
    }
    if (delete) WheelContextMenu("Delete preset?", listOf(NavigationItem("Delete ${state.selectedPresetName}") {
        viewModel.deletePreset(state.selectedPresetName); delete = false
    }), { delete = false })
    number?.let { edit ->
        WheelNumberEditor(edit.title, edit.value, edit.range, edit.step, edit.format, edit.save, { number = null })
    }
    if (graph) WheelReadingPage("Response graph", { graph = false }) {
        EqBodePlot(preset, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
    }
}
