package com.pxr.cymatic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.pxr.cymatic.audio.usb.dacProfile
import com.pxr.cymatic.audio.usb.dacType
import com.pxr.cymatic.audio.usb.playerType
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.FilterType
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelNumberEditor
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.eq.EqBandDialog
import com.pxr.cymatic.ui.components.eq.EqBandEditorLimits
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticConfirmDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.Dsp
import io.github.chronosauros.contour.core.isPass
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private enum class DacEqPage(val title: String) {
    MAIN("Equalizer"),
    PRESETS("DAC presets"),
    BANDS("DAC bands"),
}

private data class DacNumberEdit(
    val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val save: (Float) -> Unit,
)

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
    var types by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<DacNumberEdit?>(null) }
    fun db(value: Float) = String.format(Locale.US, "%.1f dB", value)
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
                                                buildString {
                                                    appendLine(target.caps.name)
                                                    state.firmware?.let {
                                                        appendLine("Firmware: $it")
                                                    }
                                                    appendLine("Bands: ${target.caps.bands}")
                                                    appendLine(
                                                        target.caps.types.joinToString {
                                                            it.playerType().displayName
                                                        }
                                                    )
                                                    appendLine(
                                                        "Frequency: ${target.caps.freqMinHz}–${target.caps.freqMaxHz} Hz"
                                                    )
                                                    appendLine(
                                                        "Gain: ${target.caps.gainMinDb}–${target.caps.gainMaxDb} dB; some filters have narrower limits"
                                                    )
                                                    appendLine(
                                                        if (target.hasPreamp)
                                                            "Preamp: ${target.preampMin}–${target.preampMax} dB in ${target.preampStep} dB steps"
                                                        else "No writable EQ preamp"
                                                    )
                                                    if (target.emulatesHighShelf)
                                                        appendLine(
                                                            "High shelves use an inverted low shelf and preamp; gain uses whole dB."
                                                        )
                                                    target.ktMicro?.let {
                                                        appendLine(
                                                            "Tuning assumes ${it.assumedStreamHz.toInt()} Hz playback. Other sample rates shift the filter frequencies."
                                                        )
                                                    }
                                                }
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
                                            db(state.draft.preamp),
                                            enabled = editable,
                                        ) {
                                            number =
                                                DacNumberEdit(
                                                    "DAC preamp",
                                                    state.draft.preamp,
                                                    target.preampMin.toFloat()..target.preampMax
                                                            .toFloat(),
                                                    target.preampStep.toFloat(),
                                                    ::db,
                                                    { model.edit(state.draft.copy(preamp = it)) },
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
                                            "${band.frequency.toInt()} Hz · ${db(band.gain)} · ${band.type.displayName}",
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
        DacChoices(
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
        DacChoices(
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
        DacConfirm(
            "Apply EQ?",
            "Save on the DAC.",
            { confirmApply = false },
            {
                confirmApply = false
                model.applyToDac()
            },
        )
    if (confirmRead)
        DacConfirm(
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
            WheelSettingsOverlay("DAC band ${bandIndex + 1}", { bandIndex = -1 }) {
                NavigationList(
                    buildList {
                        add(
                            NavigationItem("Enabled", checked = band.enabled) {
                                update(band.copy(enabled = !band.enabled))
                            }
                        )
                        add(NavigationItem("Type", band.type.displayName) { types = true })
                        add(
                            NavigationItem("Frequency", "${band.frequency.toInt()} Hz") {
                                number =
                                    DacNumberEdit(
                                        "Frequency",
                                        ln(band.frequency.coerceIn(limits.frequency)),
                                        ln(limits.frequency.start)..ln(
                                                limits.frequency.endInclusive
                                            ),
                                        0.025f,
                                        { "${exp(it).roundToInt()} Hz" },
                                        {
                                            update(
                                                band.copy(
                                                    frequency = exp(it).roundToInt().toFloat()
                                                )
                                            )
                                        },
                                    )
                            }
                        )
                        if (limits.showGain)
                            add(
                                NavigationItem("Gain", db(band.gain)) {
                                    number =
                                        DacNumberEdit(
                                            "Gain",
                                            band.gain,
                                            limits.gain,
                                            limits.gainStep,
                                            ::db,
                                            { update(band.copy(gain = it)) },
                                        )
                                }
                            )
                        add(
                            NavigationItem("Q", String.format(Locale.US, "%.3f", band.q)) {
                                number =
                                    DacNumberEdit(
                                        "Q",
                                        band.q,
                                        limits.q,
                                        0.01f,
                                        { String.format(Locale.US, "%.3f", it) },
                                        { update(band.copy(q = it)) },
                                    )
                            }
                        )
                        add(
                            NavigationItem("Remove band") {
                                model.removeBand(bandIndex)
                                bandIndex = -1
                            }
                        )
                    }
                )
            }
        if (types)
            DacChoices(
                "Filter type",
                { types = false },
                limits.types.map { type ->
                    NavigationItem(type.displayName, checked = type == band.type) {
                        update(band.copy(type = type))
                        types = false
                    }
                },
            )
    }
    number?.let { edit ->
        if (wheel)
            WheelNumberEditor(
                edit.title,
                edit.value,
                edit.range,
                edit.step,
                edit.format,
                edit.save,
                { number = null },
            )
        else {
            var value by remember(edit) { mutableStateOf(edit.value.toString()) }
            var invalid by remember(edit) { mutableStateOf(false) }
            CymaticInputDialog(
                edit.title,
                if (invalid)
                    "Use ${edit.range.start}–${edit.range.endInclusive} in ${edit.step} steps"
                else "${edit.range.start}–${edit.range.endInclusive}; ${edit.step} steps",
                value,
                { value = it },
                onConfirm = {
                    val parsed = value.toFloatOrNull()
                    if (parsed != null && parsed.isFinite() && parsed in edit.range) {
                        val quantized = kotlin.math.round(parsed / edit.step) * edit.step
                        edit.save(quantized.coerceIn(edit.range))
                        number = null
                    } else invalid = true
                },
                onDismiss = { number = null },
            )
        }
    }
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
        DacReading("DAC response", { graph = false }) {
            if (response != null)
                EqBodePlot(state.draft, Modifier.fillMaxWidth().aspectRatio(16f / 9f), response)
            else Text("This filter has no valid response. Adjust gain or Q before applying.")
        }
    }
    details?.let { (title, text) ->
        DacReading(title, { details = null }) { ReadingDocument(text) }
    }
}

private fun bandLimits(target: DeviceTarget, band: EqBand): EqBandEditorLimits {
    val type = runCatching {
        band.type.dacType()
    }
        .getOrDefault(io.github.chronosauros.contour.core.FilterType.PEAK)
    val gain = target.caps.gainRange(type)
    val q = target.qRange(type)
    return EqBandEditorLimits(
        target.caps.freqMinHz.toFloat()..target.caps.freqMaxHz.toFloat(),
        gain.start.toFloat()..gain.endInclusive.toFloat(),
        q.start.toFloat()..q.endInclusive.toFloat(),
        target.caps.types.map { it.playerType() },
        if (band.type == FilterType.HIGH_SHELF) target.highShelfStepDb?.toFloat() ?: 0.1f else 0.1f,
        !type.isPass,
    )
}

@Composable
private fun DacChoices(title: String, dismiss: () -> Unit, items: List<NavigationItem>) {
    if (LocalWheelNavigation.current != null) WheelContextMenu(title, items, dismiss)
    else
        CymaticDialog(
            title = title,
            onDismissRequest = dismiss,
            content = { NavigationList(items) },
            buttons = { CymaticDialogButton("Close", dismiss) },
        )
}

@Composable
private fun DacConfirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    if (LocalWheelNavigation.current != null)
        WheelContextMenu(
            title,
            listOf(NavigationItem("Confirm", text, subLabelMaxLines = 8, onClick = confirm)),
            dismiss,
        )
    else CymaticConfirmDialog(message = "$title\n\n$text", onConfirm = confirm, onDismiss = dismiss)
}

@Composable
private fun DacReading(title: String, dismiss: () -> Unit, content: @Composable () -> Unit) {
    if (LocalWheelNavigation.current != null) WheelReadingPage(title, dismiss, content = content)
    else
        CymaticDialog(
            title = title,
            onDismissRequest = dismiss,
            content = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
            buttons = { CymaticDialogButton("Close", dismiss) },
        )
}
