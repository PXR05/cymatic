package com.pxr.cymatic.ui.screens.settings.eq

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.eq.EqNumberEdit
import com.pxr.cymatic.ui.components.eq.EqNumberEditor
import com.pxr.cymatic.ui.components.eq.WheelEqBandEditor
import com.pxr.cymatic.ui.components.eq.formatEqGain
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun WheelEqSettingsScreen(
    state: EqUiState,
    preset: EqPreset,
    viewModel: EqViewModel,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    onHardwareEq: () -> Unit,
    modifier: Modifier = Modifier,
    pageRoute: String = Screen.EQSettings.route,
    bandIndex: Int = -1,
) {
    val nav = LocalNavController.current
    var showPresetsPageMenu by remember { mutableStateOf(false) }
    var selectedPresetForActions by remember { mutableStateOf<String?>(null) }
    var presetToRename by remember { mutableStateOf<String?>(null) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    var showNewPresetDialog by remember { mutableStateOf(false) }

    var number by remember { mutableStateOf<EqNumberEdit?>(null) }

    if (pageRoute == Screen.EQSettings.route) {
        BaseScreen(title = "Equalizer", onBackClick = { nav.popBackStack() }, modifier = modifier) {
            NavigationList(
                buildList {
                    add(
                        NavigationItem(
                            "EQ target",
                            "Player",
                            onClick = onHardwareEq,
                        )
                    )
                    add(
                        NavigationItem(
                            "Enabled",
                            if (state.eqEnabled) "On" else "Off",
                            checked = state.eqEnabled,
                        ) {
                            viewModel.setEnabled(!state.eqEnabled)
                        }
                    )
                    add(
                        NavigationItem("Preset", state.selectedPresetName) {
                            nav.navigate(Screen.EqPresets.route)
                        }
                    )
                    add(NavigationItem("Response graph") { nav.navigate(Screen.EqGraphPage.route) })
                    add(
                        NavigationItem("Preamp", formatEqGain(preset.preamp)) {
                            number =
                                EqNumberEdit(
                                    "Preamp",
                                    preset.preamp,
                                    -12f..12f,
                                    0.1f,
                                    ::formatEqGain,
                                    save = viewModel::updatePreamp,
                                )
                        }
                    )
                    add(
                        NavigationItem("Bands", "${preset.bands.size}") {
                            nav.navigate(Screen.EqBands.route)
                        }
                    )
                }
            )
        }
    }

    if (pageRoute == Screen.EqBands.route) {
        BaseScreen("Bands", { nav.popBackStack() }) {
            NavigationList(
                buildList {
                    preset.bands.forEachIndexed { index, band ->
                        add(
                            NavigationItem(
                                "Band ${index + 1}",
                                "${band.frequency.toInt()} Hz · ${formatEqGain(band.gain)}",
                                key = band.id,
                            ) {
                                nav.navigate(Screen.EqBand.createRoute(index))
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
    }

    if (pageRoute == Screen.EqPresets.route) {
        BaseScreen(
            title = "Presets",
            onBackClick = { nav.popBackStack() },
            onMenuLongClick = { showPresetsPageMenu = true },
        ) {
            NavigationList(
                state.presets.map { choice ->
                    NavigationItem(
                        label = choice.name,
                        subLabel =
                            if (choice.name == state.selectedPresetName) "Selected" else null,
                        checked = choice.name == state.selectedPresetName,
                        onClick = {
                            viewModel.selectPreset(choice.name)
                            nav.popBackStack()
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
            items =
                listOf(
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
            items =
                listOf(
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
            items =
                listOf(
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
        WheelEqBandEditor(
            title = "Band ${index + 1}",
            band = preset.bands[index],
            onChange = { viewModel.updateBand(index, it) },
            onRemove = {
                viewModel.removeBand(index)
                nav.popBackStack()
            },
            onDismiss = { nav.popBackStack() },
            canRemove = preset.bands.size > 1,
        )
    }

    if (pageRoute == Screen.EqBand.route && bandIndex !in preset.bands.indices) {
        BaseScreen("Band unavailable", { nav.popBackStack() }) {
            Text("This band is no longer available.")
        }
    }
    number?.let { edit -> EqNumberEditor(edit) { number = null } }

    if (pageRoute == Screen.EqGraphPage.route) {
        ReadingScreen("Response graph", { nav.popBackStack() }) {
            EqBodePlot(
                preset,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
    }
}
