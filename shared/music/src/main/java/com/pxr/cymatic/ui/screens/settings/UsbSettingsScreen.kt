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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.store.UsbPlaybackSettings
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
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
    val nav = LocalNavController.current
    val wheel = LocalWheelNavigation.current
    val controller = LocalMediaController.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }
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
                    NavigationItem("Direct USB", playbackLabel, enabled = !state.probing) {
                        scope.launch { UsbPlaybackSettings.setEnabled(!enabled) }
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
                                                sessions
                                            )
                            } else model.inspectDevice(device.device)
                        }
                    )
                    if (device.permission) {
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
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    content()
                }
            }
        }
    }
}
