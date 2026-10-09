package com.pxr.cymatic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.dsdSettingsKey
import com.pxr.cymatic.data.store.DsdUsbMode
import com.pxr.cymatic.data.store.UsbPlaybackSettings
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun UsbSettingsScreen(modifier: Modifier = Modifier) {
    val model: UsbAudioDiagnosticsViewModel = viewModel()
    val state by model.state.collectAsState()
    val enabled by
        UsbPlaybackSettings.enabledFlow.collectAsState(initial = UsbPlaybackSettings.enabled)
    val active by UsbPlaybackState.active.collectAsState()
    val playbackStatus by UsbPlaybackState.status.collectAsState()
    val sessions by UsbPlaybackState.sessionReports.collectAsState()
    val dsdModes by UsbPlaybackSettings.dsdModesFlow.collectAsState(initial = emptyMap())
    val nav = LocalNavController.current
    val wheel = LocalWheelNavigation.current
    val controller = LocalMediaController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember {
        mutableStateOf(BackgroundPlaybackGuide.isExempt(context))
    }
    DisposableEffect(lifecycleOwner) {
        batteryExempt = BackgroundPlaybackGuide.isExempt(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = BackgroundPlaybackGuide.isExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dsdDevice by remember { mutableStateOf<Pair<String, String>?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            model.finishExport(it)
        }
    DisposableEffect(model) {
        model.start()
        onDispose { model.stop() }
    }
    val report = state.report
    val canOperate = !state.inspecting && !state.probing && state.permissionDeviceId == null
    val playbackLabel =
        when {
            !enabled -> "Off · normal playback"
            active -> "On · DAC output"
            report?.devices?.isEmpty() == true -> "On · connect a DAC"
            report?.devices?.any { !it.permission } == true -> "On · allow USB access"
            playbackStatus.startsWith("Normal playback") || playbackStatus.startsWith("Stopped") ->
                "On · Android fallback"

            else -> "On · ready"
        }
    BaseScreen("USB audio", { nav.popBackStack() }, modifier = modifier) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Direct USB",
                        playbackLabel,
                        enabled = !state.probing,
                        checked = enabled,
                    ) {
                        scope.launch { UsbPlaybackSettings.setEnabled(!enabled) }
                    }
                )
                add(
                    NavigationItem(
                        "Background playback",
                        if (batteryExempt) "Unrestricted · keeps playing when hidden"
                        else "Battery optimized · may stop when hidden",
                        key = "background-playback",
                    ) {
                        if (batteryExempt) BackgroundPlaybackGuide.openBatterySettings(context)
                        else BackgroundPlaybackGuide.requestExemption(context)
                    }
                )
                add(
                    NavigationItem(
                        "Device steps",
                        "${BackgroundPlaybackGuide.manufacturerLabel()} · keep Cymatic alive",
                        key = "background-steps",
                    ) {
                        details =
                            "Background playback · ${BackgroundPlaybackGuide.manufacturerLabel()}" to
                                BackgroundPlaybackGuide.stepsText()
                    }
                )
                if (report?.devices.isNullOrEmpty()) {
                    add(
                        NavigationItem(
                            "DAC",
                            if (state.inspecting) "Searching" else "Not connected",
                            enabled = false,
                        )
                    )
                }
                report?.devices?.forEach { device ->
                    add(
                        NavigationItem(
                            device.label,
                            when {
                                state.permissionDeviceId == device.device.deviceId ->
                                    "Waiting for access"

                                !device.permission -> "Allow USB access"
                                else -> "Connected · ${device.protocolLabel()}"
                            },
                            key = device.device.deviceName,
                            enabled = canOperate,
                        ) {
                            if (device.permission) {
                                details =
                                    device.label to
                                        usbSettingsReport(
                                            device,
                                            state,
                                            playbackStatus,
                                            sessions,
                                        )
                            } else model.inspectDevice(device.device)
                        }
                    )
                    if (device.permission) {
                        val deviceKey = device.device.dsdSettingsKey()
                        val mode =
                            DsdUsbMode.entries.firstOrNull {
                                it.name == dsdModes["USB_DSD_MODE_$deviceKey"]
                            } ?: DsdUsbMode.AUTO
                        add(
                            NavigationItem(
                                "DSD output",
                                mode.label,
                                key = "dsd-${device.device.deviceName}",
                                enabled = canOperate,
                            ) {
                                dsdDevice = deviceKey to device.label
                            }
                        )
                        val checks =
                            state.probeReports
                                .filter {
                                    it.optInt("vendorId") == device.device.vendorId &&
                                        it.optInt("productId") == device.device.productId
                                }
                                .takeLast(2)
                        val checkLabel =
                            when {
                                state.probing -> state.message ?: "Checking"
                                checks.any { !it.optBoolean("streamingCompleted") } ->
                                    "Failed · open DAC details"

                                checks.size == 2 -> "Passed · 44.1 + 48 kHz"
                                else -> "Pauses playback · 4 s silence"
                            }
                        add(
                            NavigationItem(
                                "Check DAC",
                                checkLabel,
                                key = "check-${device.device.deviceName}",
                                enabled = canOperate && (controller != null || !active),
                            ) {
                                controller?.pause()
                                controller?.stop()
                                model.runChecks(device.device)
                            }
                        )
                    }
                }
                if (state.probing) {
                    add(NavigationItem("Cancel", "Stop check", onClick = model::cancelProbe))
                }
                add(
                    NavigationItem(
                        "Refresh",
                        if (state.inspecting) "Updating" else "Update connected DACs",
                        enabled = canOperate,
                        onClick = model::refresh,
                    )
                )
                add(
                    NavigationItem(
                        "Export",
                        if (state.exporting) "Saving" else "Save full report",
                        enabled = report != null && canOperate && !state.exporting,
                    ) {
                        if (model.prepareExport()) export.launch("cymatic-usb-diagnostics.json")
                    }
                )
            }
        )
    }
    dsdDevice?.let { (deviceKey, label) ->
        val selected = dsdModes["USB_DSD_MODE_$deviceKey"] ?: DsdUsbMode.AUTO.name
        val items =
            DsdUsbMode.entries.map { mode ->
                NavigationItem(
                    mode.label,
                    if (mode.name == selected) "Selected"
                    else
                        when (mode) {
                            DsdUsbMode.AUTO -> "Verified DACs; otherwise PCM"
                            DsdUsbMode.PCM -> "Works with PCM-only DACs"
                            DsdUsbMode.DOP -> "Use with a DoP-capable DAC"
                        },
                    checked = mode.name == selected,
                ) {
                    scope.launch { UsbPlaybackSettings.setDsdMode(deviceKey, mode) }
                    dsdDevice = null
                }
            }
        if (wheel != null) {
            WheelContextMenu("DSD output", items, { dsdDevice = null })
        } else {
            CymaticDialog(
                title = "DSD output · $label",
                onDismissRequest = { dsdDevice = null },
                content = {
                    NavigationList(items + NavigationItem("Cancel", onClick = { dsdDevice = null }))
                },
                buttons = {},
            )
        }
    }
    details?.let { (title, text) ->
        val content: @Composable () -> Unit = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (wheel != null) {
            WheelReadingPage(title, { details = null }, content)
        } else {
            BackHandler { details = null }
            BaseScreen(title, { details = null }) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
                ) {
                    content()
                }
            }
        }
    }
}
