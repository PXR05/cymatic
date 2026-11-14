package com.pxr.cymatic.ui.screens.settings.eq.hardware

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.audio.eq.dacProfile
import com.pxr.cymatic.audio.eq.dacType
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.SettingsChoicesDialog
import com.pxr.cymatic.ui.components.common.SettingsConfirmationDialog
import com.pxr.cymatic.ui.components.common.SettingsReadingDialog
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.eq.EqBandDialog
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.eq.EqNumberEdit
import com.pxr.cymatic.ui.components.eq.EqNumberEditor
import com.pxr.cymatic.ui.components.eq.WheelEqBandEditor
import com.pxr.cymatic.ui.components.eq.formatEqGain
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import io.github.chronosauros.contour.core.Dsp
import io.github.chronosauros.contour.core.isPass

private enum class DacEqPage(val title: String) {
    MAIN("Equalizer"),
    PRESETS("DAC presets"),
    BANDS("DAC bands"),
}

@Composable
internal fun HardwareEqSettingsScreen(
    presets: List<EqPreset>,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    model: HardwareEqViewModel = viewModel(),
) {
    val state by model.state.collectAsState()
    val target = state.target
    val nav = LocalNavController.current
    val wheel = LocalWheelNavigation.current != null
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model) {
        model.refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var page by remember { mutableStateOf(DacEqPage.MAIN) }
    var chooseDevice by remember { mutableStateOf(false) }
    var choosePreset by remember { mutableStateOf(false) }
    var savePreset by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRead by remember { mutableStateOf(false) }
    var graph by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }
    var bandIndex by remember(state.device?.key, state.slot) { mutableIntStateOf(-1) }
    var number by remember { mutableStateOf<EqNumberEdit?>(null) }
    val editable = !state.busy && !state.pendingVerification

    BackHandler(enabled = page != DacEqPage.MAIN) { page = DacEqPage.MAIN }
    val pageContent: @Composable () -> Unit = {
        key(page) {
            NavigationList(
                buildList {
                    when (page) {
                        DacEqPage.MAIN -> {
                            add(
                                NavigationItem(
                                    "EQ target",
                                    "USB DAC",
                                    enabled = !state.busy,
                                    onClick = onPlayer,
                                )
                            )
                            add(
                                NavigationItem(
                                    target?.caps?.name ?: "No DAC connected",
                                    if (state.busy && state.progress > 0f)
                                        "${state.message} · ${(state.progress * 100).toInt()}%"
                                    else state.message,
                                    key = "dac-status",
                                    subLabelMaxLines = 2,
                                ) {
                                    if (target != null) {
                                        details =
                                            "Device details" to
                                                deviceDetails(target, state.firmware)
                                    }
                                }
                            )
                            if (!state.permission && state.device != null)
                                add(
                                    NavigationItem(
                                        "Allow USB access",
                                        enabled = !state.permissionPending && !state.busy,
                                        onClick = model::requestAccess,
                                    )
                                )
                            if (target != null) {
                                add(
                                    NavigationItem(
                                        "Preset",
                                        state.draft.name,
                                        enabled = !state.busy,
                                    ) {
                                        page = DacEqPage.PRESETS
                                    }
                                )
                                if (target.hasPreamp)
                                    add(
                                        NavigationItem(
                                            "Preamp",
                                            formatEqGain(state.draft.preamp),
                                            enabled = editable,
                                        ) {
                                            number =
                                                EqNumberEdit(
                                                    "DAC preamp",
                                                    state.draft.preamp,
                                                    target.preampMin.toFloat()..target.preampMax
                                                            .toFloat(),
                                                    target.preampStep.toFloat(),
                                                    ::formatEqGain,
                                                    save = { model.edit(state.draft.copy(preamp = it)) },
                                                )
                                        }
                                    )
                                add(
                                    NavigationItem(
                                        "Bands",
                                        "${state.draft.bands.size} / ${target.caps.bands}",
                                        enabled = !state.busy,
                                    ) {
                                        page = DacEqPage.BANDS
                                    }
                                )
                                if (state.pendingVerification)
                                    add(
                                        NavigationItem(
                                            "Verify saved EQ",
                                            enabled = !state.busy && state.permission,
                                            onClick = model::readDac,
                                        )
                                    )
                                else
                                    add(
                                        NavigationItem(
                                            "Apply to DAC",
                                            if (state.onDac) "Applied" else null,
                                            enabled = state.canApply,
                                        ) {
                                            confirmApply = true
                                        }
                                    )
                                if (state.issues.isNotEmpty())
                                    add(
                                        NavigationItem(
                                            "Cannot apply",
                                            state.issues.first(),
                                            subLabelMaxLines = 2,
                                        ) {
                                            details =
                                                "Cannot apply" to state.issues.joinToString("\n\n")
                                        }
                                    )
                            }
                        }
                        DacEqPage.PRESETS -> {
                            if (state.devices.size > 1)
                                add(
                                    NavigationItem("Select DAC", enabled = !state.busy) {
                                        chooseDevice = true
                                    }
                                )

                            add(
                                NavigationItem(
                                    "Load from DAC",
                                    enabled = !state.busy && state.permission,
                                ) {
                                    if (state.pendingVerification) model.readDac()
                                    else confirmRead = true
                                }
                            )
                            add(
                                NavigationItem("Load preset", enabled = editable) {
                                    choosePreset = true
                                }
                            )
                            add(
                                NavigationItem("Save as preset", enabled = editable) {
                                    savePreset = true
                                }
                            )
                        }
                        DacEqPage.BANDS -> {
                            add(NavigationItem("Response graph") { graph = true })
                            state.draft.bands.forEachIndexed { index, band ->
                                add(
                                    NavigationItem(
                                        "Band ${index + 1}",
                                        if (!band.enabled) "Off · ${band.type.displayName}"
                                        else
                                            "${band.frequency.toInt()} Hz · ${formatEqGain(band.gain)} · ${band.type.displayName}",
                                        key = "dac-band-${band.id}",
                                        enabled = editable,
                                    ) {
                                        bandIndex = index
                                    }
                                )
                            }
                            add(
                                NavigationItem(
                                    "Add band",
                                    enabled =
                                        editable &&
                                            state.draft.bands.size < (target?.caps?.bands ?: 0),
                                    onClick = model::addBand,
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    if (wheel && page != DacEqPage.MAIN) {
        WheelSettingsOverlay(page.title, { page = DacEqPage.MAIN }, content = pageContent)
    } else {
        BaseScreen(
            page.title,
            { if (page == DacEqPage.MAIN) nav.popBackStack() else page = DacEqPage.MAIN },
            modifier = modifier,
        ) {
            pageContent()
        }
    }

    if (chooseDevice)
        SettingsChoicesDialog(
            "USB DAC",
            { chooseDevice = false },
            state.devices
                .map { handle ->
                    NavigationItem(
                        handle.target.caps.name,
                        subLabel = handle.usb?.deviceName,
                        key = handle.key,
                        checked = handle.key == state.device?.key,
                    ) {
                        model.selectDevice(handle)
                        chooseDevice = false
                    }
                }
                .ifEmpty { listOf(NavigationItem("No supported DAC connected", enabled = false)) },
        )
    if (choosePreset)
        SettingsChoicesDialog(
            "Load preset",
            { choosePreset = false },
            presets.map { preset ->
                NavigationItem(preset.name) {
                    model.edit(preset.copy())
                    choosePreset = false
                    page = DacEqPage.MAIN
                }
            },
        )
    if (savePreset) {
        if (wheel)
            WheelTextEditor(
                "Save DAC preset",
                state.draft.name,
                onSave = {
                    model.savePreset(it)
                    page = DacEqPage.MAIN
                },
                onDismiss = { savePreset = false },
            )
        else {
            var name by remember { mutableStateOf(state.draft.name) }
            CymaticInputDialog(
                "Save DAC preset",
                "Preset name",
                name,
                { name = it },
                onConfirm = {
                    model.savePreset(name)
                    savePreset = false
                    page = DacEqPage.MAIN
                },
                onDismiss = { savePreset = false },
            )
        }
    }
    if (confirmApply)
        SettingsConfirmationDialog(
            "Apply EQ?",
            "Save on the DAC.",
            { confirmApply = false },
            {
                confirmApply = false
                model.applyToDac()
            },
        )
    if (confirmRead)
        SettingsConfirmationDialog(
            "Load DAC EQ?",
            "Replace this draft.",
            { confirmRead = false },
            {
                confirmRead = false
                page = DacEqPage.MAIN
                model.readDac()
            },
        )

    if (bandIndex in state.draft.bands.indices && target != null && editable) {
        val band = state.draft.bands[bandIndex]
        val limits = bandLimits(target, band)
        fun update(updated: EqBand) {
            val pass = runCatching { updated.type.dacType().isPass }.getOrDefault(false)
            model.updateBand(bandIndex, if (pass) updated.copy(gain = 0f) else updated)
        }
        if (!wheel)
            EqBandDialog(
                bandIndex,
                band,
                { bandIndex = -1 },
                ::update,
                {
                    model.removeBand(bandIndex)
                    bandIndex = -1
                },
                limits,
            )
        else
            WheelEqBandEditor(
                title = "DAC band ${bandIndex + 1}",
                band = band,
                onChange = ::update,
                onRemove = {
                    model.removeBand(bandIndex)
                    bandIndex = -1
                },
                onDismiss = { bandIndex = -1 },
                limits = limits,
            )
    }
    number?.let { edit -> EqNumberEditor(edit) { number = null } }
    if (graph) {
        val response =
            remember(state.draft) {
                runCatching {
                    val profile = state.draft.dacProfile()
                    Dsp.responseDb(profile.bands, Dsp.FreqGrid(Dsp.logFreqs(301)))
                        .map { (it + state.draft.preamp).toFloat() }
                        .toFloatArray()
                }
                    .getOrNull()
            }
        SettingsReadingDialog("DAC response", { graph = false }) {
            if (response != null)
                EqBodePlot(state.draft, Modifier.fillMaxWidth().aspectRatio(16f / 9f), response)
            else Text("This filter has no valid response. Adjust gain or Q before applying.")
        }
    }
    details?.let { (title, text) ->
        SettingsReadingDialog(title, { details = null }) { ReadingDocument(text) }
    }
}
