package com.pxr.cymatic.ui.screens.settings

import com.pxr.cymatic.audio.usb.UsbDiagnosticReport
import org.json.JSONObject

internal data class UsbDiagnosticsState(
    val report: UsbDiagnosticReport? = null,
    val inspecting: Boolean = false,
    val permissionDeviceId: Int? = null,
    val exporting: Boolean = false,
    val message: String? = null,
    val events: List<String> = emptyList(),
    val probing: Boolean = false,
    val probeReports: List<JSONObject> = emptyList(),
)
