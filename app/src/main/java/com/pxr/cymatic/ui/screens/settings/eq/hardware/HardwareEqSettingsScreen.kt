package com.pxr.cymatic.ui.screens.settings.eq.hardware

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.audio.eq.dacProfile
import com.pxr.cymatic.audio.eq.dacType
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.common.WheelConfirmationMenu
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.eq.EqNumberEdit
import com.pxr.cymatic.ui.components.eq.EqNumberEditor
import com.pxr.cymatic.ui.components.eq.WheelEqBandEditor
import com.pxr.cymatic.ui.components.eq.formatEqGain
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import io.github.chronosauros.contour.core.Dsp
import io.github.chronosauros.contour.core.isPass

@Composable
internal fun HardwareEqSettingsScreen(
    presets: List<EqPreset>,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    model: HardwareEqViewModel = viewModel(),
    pageRoute: String = Screen.HardwareEq.route,
    bandIndex: Int = -1,
) {
    val state by model.state.collectAsState()
    val target = state.target
    val nav = LocalNavController.current
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model) {
        model.refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val page = pageRoute
    var chooseDevice by remember { mutableStateOf(false) }
    var choosePreset by remember { mutableStateOf(false) }
    var savePreset by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRead by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<EqNumberEdit?>(null) }
    val editable = !state.busy && !state.pendingVerification

    val pageContent: @Composable () -> Unit = {
        key(page) {
            NavigationList(
                buildList {
                    when (page) {
                        Screen.HardwareEq.route -> {
                            add(
                                NavigationItem(
                                    "EQ target",
                                    "USB DAC",
                                    icon = pixelIcon(R.drawable.ic_pixel_usb),
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
                                    icon = pixelIcon(R.drawable.ic_pixel_diamond),
                                    key = "dac-status",
                                    subLabelMaxLines = 2,
                                ) {
                                    if (target != null) {
                                        nav.navigate(Screen.HardwareEqDevice.route)
                                    }
                                }
                            )
                            if (!state.permission && state.device != null)
                                add(
                                    NavigationItem(
                                        "Allow USB access",
                                        icon = pixelIcon(R.drawable.ic_pixel_lock),
                                        enabled = !state.permissionPending && !state.busy,
                                        onClick = model::requestAccess,
                                    )
                                )
                            if (target != null) {
                                add(
                                    NavigationItem(
                                        "Preset",
                                        state.draft.name,
                                        icon = pixelIcon(R.drawable.ic_pixel_sliders),
                                        enabled = !state.busy,
                                    ) {
                                        nav.navigate(Screen.HardwareEqPresets.route)
                                    }
                                )
                                if (target.hasPreamp)
                                    add(
                                        NavigationItem(
                                            "Preamp",
                                            formatEqGain(state.draft.preamp),
                                            icon = pixelIcon(R.drawable.ic_pixel_speaker),
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
                                                    save = {
                                                        model.edit(state.draft.copy(preamp = it))
                                                    },
                                                )
                                        }
                                    )
                                add(
                                    NavigationItem(
                                        "Bands",
                                        "${state.draft.bands.size} / ${target.caps.bands}",
                                        icon = pixelIcon(R.drawable.ic_pixel_equalizer),
                                        enabled = !state.busy,
                                    ) {
                                        nav.navigate(Screen.HardwareEqBands.route)
                                    }
                                )
                                if (state.pendingVerification)
                                    add(
                                        NavigationItem(
                                            "Verify saved EQ",
                                            icon = pixelIcon(R.drawable.ic_pixel_check),
                                            enabled = !state.busy && state.permission,
                                            onClick = model::readDac,
                                        )
                                    )
                                else
                                    add(
                                        NavigationItem(
                                            "Apply to DAC",
                                            if (state.onDac) "Applied" else null,
                                            icon = pixelIcon(R.drawable.ic_pixel_save),
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
                                            icon = pixelIcon(R.drawable.ic_pixel_warning),
                                            subLabelMaxLines = 2,
                                        ) {
                                            nav.navigate(Screen.HardwareEqIssues.route)
                                        }
                                    )
                            }
                        }

                        Screen.HardwareEqPresets.route -> {
                            if (state.devices.size > 1)
                                add(
                                    NavigationItem(
                                        "Select DAC",
                                        icon = pixelIcon(R.drawable.ic_pixel_usb),
                                        enabled = !state.busy,
                                    ) {
                                        chooseDevice = true
                                    }
                                )

                            add(
                                NavigationItem(
                                    "Load from DAC",
                                    icon = pixelIcon(R.drawable.ic_pixel_download),
                                    enabled = !state.busy && state.permission,
                                ) {
                                    if (state.pendingVerification) model.readDac()
                                    else confirmRead = true
                                }
                            )
                            add(
                                NavigationItem(
                                    "Load preset",
                                    icon = pixelIcon(R.drawable.ic_pixel_folder),
                                    enabled = editable,
                                ) {
                                    choosePreset = true
                                }
                            )
                            add(
                                NavigationItem(
                                    "Save as preset",
                                    icon = pixelIcon(R.drawable.ic_pixel_save),
                                    enabled = editable,
                                ) {
                                    savePreset = true
                                }
                            )
                        }

                        Screen.HardwareEqBands.route -> {
                            add(
                                NavigationItem(
                                    "Response graph",
                                    icon = pixelIcon(R.drawable.ic_pixel_graph),
                                ) {
                                    nav.navigate(Screen.HardwareEqGraphPage.route)
                                }
                            )
                            state.draft.bands.forEachIndexed { index, band ->
                                add(
                                    NavigationItem(
                                        "Band ${index + 1}",
                                        if (!band.enabled) "Off · ${band.type.displayName}"
                                        else
                                            "${band.frequency.toInt()} Hz · ${formatEqGain(band.gain)} · ${band.type.displayName}",
                                        icon = pixelIcon(R.drawable.ic_pixel_sliders),
                                        key = "dac-band-${band.id}",
                                        enabled = editable,
                                    ) {
                                        nav.navigate(Screen.HardwareEqBand.createRoute(index))
                                    }
                                )
                            }
                            add(
                                NavigationItem(
                                    "Add band",
                                    icon = pixelIcon(R.drawable.ic_pixel_add),
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

    if (
        page in
        listOf(
            Screen.HardwareEq.route,
            Screen.HardwareEqPresets.route,
            Screen.HardwareEqBands.route,
        )
    ) {
        val title =
            when (page) {
                Screen.HardwareEqPresets.route -> "DAC presets"
                Screen.HardwareEqBands.route -> "DAC bands"
                else -> "Equalizer"
            }
        BaseScreen(title, { nav.popBackStack() }, modifier = modifier) { pageContent() }
    }
    if (chooseDevice)
        WheelContextMenu(
            title = "USB DAC",
            onDismiss = { chooseDevice = false },
            items =
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
                    .ifEmpty {
                        listOf(NavigationItem("No supported DAC connected", enabled = false))
                    },
        )
    if (choosePreset)
        WheelContextMenu(
            title = "Load preset",
            onDismiss = { choosePreset = false },
            items =
                presets.map { preset ->
                    NavigationItem(preset.name) {
                        model.edit(preset.copy())
                        nav.popBackStack(Screen.HardwareEq.route, false)
                    }
                },
        )
    if (savePreset) {
        WheelTextEditor(
            "Save DAC preset",
            state.draft.name,
            onSave = {
                model.savePreset(it)
                nav.popBackStack(Screen.HardwareEq.route, false)
            },
            onDismiss = { savePreset = false },
        )
    }
    if (confirmApply)
        WheelConfirmationMenu(
            "Apply EQ?",
            "Save on the DAC.",
            { confirmApply = false },
            {
                confirmApply = false
                model.applyToDac()
            },
        )
    if (confirmRead)
        WheelConfirmationMenu(
            "Load DAC EQ?",
            "Replace this draft.",
            { confirmRead = false },
            {
                confirmRead = false
                nav.popBackStack(Screen.HardwareEq.route, false)
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
        WheelEqBandEditor(
            title = "DAC band ${bandIndex + 1}",
            band = band,
            onChange = ::update,
            onRemove = {
                model.removeBand(bandIndex)
                nav.popBackStack()
            },
            onDismiss = { nav.popBackStack() },
            limits = limits,
        )
    }
    if (
        page == Screen.HardwareEqBand.route &&
        (bandIndex !in state.draft.bands.indices || target == null)
    ) {
        BaseScreen("Band unavailable", { nav.popBackStack() }) {
            Text("This band or DAC is no longer available.")
        }
    }
    number?.let { edit -> EqNumberEditor(edit) { number = null } }
    if (page == Screen.HardwareEqGraphPage.route) {
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
        ReadingScreen("DAC response", { nav.popBackStack() }) {
            if (response != null)
                EqBodePlot(
                    state.draft, Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f), response
                )
            else Text("This filter has no valid response. Adjust gain or Q before applying.")
        }
    }
    if (page == Screen.HardwareEqDevice.route) {
        ReadingScreen("Device details", { nav.popBackStack() }) {
            ReadingDocument(target?.let { deviceDetails(it, state.firmware) } ?: "No DAC connected")
        }
    }
    if (page == Screen.HardwareEqIssues.route) {
        ReadingScreen("Cannot apply", { nav.popBackStack() }) {
            ReadingDocument(state.issues.joinToString("\n\n"))
        }
    }
}
