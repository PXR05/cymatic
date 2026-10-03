package com.pxr.cymatic.audio.usb

import android.hardware.usb.UsbDevice
import org.json.JSONArray
import org.json.JSONObject

internal data class UsbDiagnosticDevice(
    val device: UsbDevice,
    val label: String,
    val permission: Boolean,
    val details: JSONObject,
)

internal data class UsbDiagnosticReport(
    val capturedAt: String,
    val hostSupported: Boolean,
    val environment: JSONObject,
    val devices: List<UsbDiagnosticDevice>,
    val androidOutputs: List<JSONObject>,
    val warnings: List<String>,
) {
    fun json(events: List<String>): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("capturedAt", capturedAt)
            .put(
                "scope",
                "Capability inspection only; playback and bit-perfectness are not verified",
            )
            .put("usbHostSupported", hostSupported)
            .put("environment", environment)
            .put("usbDevices", JSONArray(devices.map { it.details }))
            .put("androidUsbOutputs", JSONArray(androidOutputs))
            .put("warnings", JSONArray(warnings))
            .put("events", JSONArray(events))
            .toString(2)
}
