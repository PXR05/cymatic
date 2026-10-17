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
    val save: (Float) -> Unit,
)

@Composable
internal fun WheelEqSettingsScreen(
    state: EqUiState,
    preset: EqPreset,
    viewModel: EqViewModel,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = LocalNavController.current
    var showPresets by remember { mutableStateOf(false) }
    var showPresetsPageMenu by remember { mutableStateOf(false) }
    var selectedPresetForActions by remember { mutableStateOf<String?>(null) }
    var presetToRename by remember { mutableStateOf<String?>(null) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    var showNewPresetDialog by remember { mutableStateOf(false) }

    var bandIndex by remember { mutableIntStateOf(-1) }
    var showFilterTypeMenu by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<EqNumberEdit?>(null) }
    var graph by remember { mutableStateOf(false) }

    fun db(value: Float) = String.format(Locale.US, "%.1f dB", value)

    BaseScreen(title = "Equalizer", onBackClick = { nav.popBackStack() }, modifier = modifier) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Enabled",
                        if (state.eqEnabled) "On" else "Off",
                        checked = state.eqEnabled,
                    ) {
                        viewModel.setEnabled(!state.eqEnabled)
                    }
                )
                add(NavigationItem("Preset", state.selectedPresetName) { showPresets = true })
                add(NavigationItem("Response graph") { graph = true })
                add(
                    NavigationItem("Preamp", db(preset.preamp)) {
                        number =
                            EqNumberEdit(
                                "Preamp",
                                preset.preamp,
                                -12f..12f,
                                0.1f,
                                ::db,
                                viewModel::updatePreamp,
                            )
                    }
                )
                preset.bands.forEachIndexed { index, band ->
                    add(
                        NavigationItem(
                            "Band ${index + 1}",
                            "${band.frequency.toInt()} Hz · ${db(band.gain)}",
                            key = band.id,
                        ) {
                            bandIndex = index
                        }
                    )
                }
                add(
                    NavigationItem(
                        "Add band",
                        enabled = preset.bands.size < EqViewModel.MAX_BANDS,
                        onClick = viewModel::addBand,
                    )
                )
            }
        )
    }

    if (showPresets) {
        WheelSettingsOverlay(
            title = "Presets",
            onDismiss = { showPresets = false },
            onMenuLongClick = { showPresetsPageMenu = true },
        ) {
            NavigationList(
                state.presets.map { choice ->
                    NavigationItem(
                        label = choice.name,
                        subLabel = if (choice.name == state.selectedPresetName) "Selected" else null,
                        checked = choice.name == state.selectedPresetName,
                        onClick = {
                            viewModel.selectPreset(choice.name)
                            showPresets = false
                        },
                        onLongClick = {
                            selectedPresetForActions = choice.name
                        },
                    )
                }
            )
        }
    }

    selectedPresetForActions?.let { presetName ->
        WheelContextMenu(
            title = presetName,
            items = listOf(
                NavigationItem("Rename") {
                    selectedPresetForActions = null
                    presetToRename = presetName
                },
                NavigationItem("Delete", enabled = state.presets.size > 1) {
                    selectedPresetForActions = null
                    presetToDelete = presetName
                },
                NavigationItem("Export") {
                    selectedPresetForActions = null
                    onExport(presetName)
                },
            ),
            onDismiss = { selectedPresetForActions = null },
        )
    }

    if (showPresetsPageMenu) {
        WheelContextMenu(
            title = "Presets",
            items = listOf(
                NavigationItem("New preset") {
                    showPresetsPageMenu = false
                    showNewPresetDialog = true
                },
                NavigationItem("Import") {
                    showPresetsPageMenu = false
                    onImport()
                },
            ),
            onDismiss = { showPresetsPageMenu = false },
        )
    }

    if (showNewPresetDialog) {
        WheelTextEditor(
            title = "New preset",
            value = "",
            onSave = { name ->
                viewModel.addPreset(name.trim())
            },
            onDismiss = { showNewPresetDialog = false },
        )
    }

    presetToRename?.let { target ->
        WheelTextEditor(
            title = "Rename preset",
            value = target,
            onSave = { newName ->
                viewModel.renamePreset(target, newName.trim())
            },
            onDismiss = { presetToRename = null },
        )
    }

    presetToDelete?.let { target ->
        WheelContextMenu(
            title = "Delete preset?",
            items = listOf(
                NavigationItem("Delete $target") {
                    viewModel.deletePreset(target)
                    presetToDelete = null
                }
            ),
            onDismiss = { presetToDelete = null },
        )
    }

    if (bandIndex in preset.bands.indices) {
        val index = bandIndex
        val band = preset.bands[index]
        WheelSettingsOverlay("Band ${index + 1}", { bandIndex = -1 }) {
            NavigationList(
                listOf(
                    NavigationItem(
                        "Enabled",
                        if (band.enabled) "On" else "Off",
                        checked = band.enabled,
                    ) {
                        viewModel.updateBand(index, band.copy(enabled = !band.enabled))
                    },
                    NavigationItem("Type", band.type.displayName) { showFilterTypeMenu = true },
                    NavigationItem("Frequency", "${band.frequency.toInt()} Hz") {
                        number =
                            EqNumberEdit(
                                "Frequency",
                                ln(band.frequency.coerceIn(20f, 20000f)),
                                ln(20f)..ln(20000f),
                                0.025f,
                                { "${exp(it).roundToInt()} Hz" },
                                {
                                    viewModel.updateBand(
                                        index,
                                        band.copy(frequency = exp(it).roundToInt().toFloat()),
                                    )
                                },
                            )
                    },
                    NavigationItem("Gain", db(band.gain)) {
                        number =
                            EqNumberEdit(
                                "Gain",
                                band.gain,
                                -12f..12f,
                                0.1f,
                                ::db,
                                { viewModel.updateBand(index, band.copy(gain = it)) },
                            )
                    },
                    NavigationItem("Q", String.format(Locale.US, "%.2f", band.q)) {
                        number =
                            EqNumberEdit(
                                "Q",
                                band.q,
                                0.1f..2f,
                                0.01f,
                                { String.format(Locale.US, "%.2f", it) },
                                { viewModel.updateBand(index, band.copy(q = it)) },
                            )
                    },
                    NavigationItem("Remove band", enabled = preset.bands.size > 1) {
                        viewModel.removeBand(index)
                        bandIndex = -1
                    },
                )
            )
        }
    }

    if (showFilterTypeMenu) {
        WheelContextMenu(
            title = "Filter type",
            items = FilterType.entries.map { type ->
                NavigationItem(
                    type.displayName,
                    checked = preset.bands.getOrNull(bandIndex)?.type == type,
                ) {
                    preset.bands.getOrNull(bandIndex)?.let {
                        viewModel.updateBand(bandIndex, it.copy(type = type))
                    }
                    showFilterTypeMenu = false
                }
            },
            onDismiss = { showFilterTypeMenu = false },
        )
    }

    number?.let { edit ->
        WheelNumberEditor(
            edit.title,
            edit.value,
            edit.range,
            edit.step,
            edit.format,
            onSave = { edit.save(it) },
            onDismiss = { number = null },
        )
    }

    if (graph) {
        WheelReadingPage("Response graph", { graph = false }) {
            EqBodePlot(preset, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
    }
}
