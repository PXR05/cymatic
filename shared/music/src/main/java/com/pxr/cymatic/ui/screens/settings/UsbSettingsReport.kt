package com.pxr.cymatic.ui.screens.settings

import com.pxr.cymatic.audio.usb.UsbDiagnosticDevice
import org.json.JSONObject

internal fun UsbDiagnosticDevice.protocolLabel(): String {
    val protocols = details.optJSONArray("formats")?.let { formats ->
            (0 until formats.length()).map { formats.getJSONObject(it).optInt("protocol", -1) }
                .distinct()
        }.orEmpty()
    return protocols.mapNotNull {
            when (it) {
                0 -> "UAC1"
                32 -> "UAC2"
                else -> null
            }
        }.joinToString(" / ").ifBlank { "USB audio" }
}

internal fun usbSettingsReport(
    device: UsbDiagnosticDevice,
    state: UsbDiagnosticsState,
    playbackStatus: String,
    sessions: List<String>,
): String = buildString {
    appendLine(device.label)
    appendLine(device.protocolLabel())
    appendLine()
    appendLine("Playback")
    appendLine(playbackStatus)
    val formats = device.details.optJSONArray("formats")?.let { values ->
            (0 until values.length()).map { values.getJSONObject(it) }
                .filter { !it.isNull("controlInterface") }
        }.orEmpty()
    if (formats.isNotEmpty()) {
        appendLine()
        appendLine("Formats")
        appendLine(
            formats.map { it.optInt("validBits") }.distinct().sorted().joinToString(" / ") + "-bit"
        )
        appendLine(formats.map { if (it.optInt("channels") == 1) "Mono" else "Stereo" }.distinct()
            .joinToString(" / "))
        val rates = formats.flatMap { format ->
                format.optJSONArray("sampleRatesHz")?.let { values ->
                        (0 until values.length()).map { values.getInt(it) }
                    }.orEmpty()
            }.distinct().sorted()
        if (rates.isNotEmpty()) appendLine(rates.joinToString(" / ") { usbRateLabel(it) })
    }
    val checks = state.probeReports.filter {
            it.optInt("vendorId") == device.device.vendorId && it.optInt("productId") == device.device.productId
        }.takeLast(2)
    if (checks.isNotEmpty()) {
        appendLine()
        appendLine("Checks")
        checks.forEach { check ->
            appendLine(
                "${usbRateLabel(check.optInt("sampleRateHz"))}: ${if (check.optBoolean("streamingCompleted")) "Passed" else "Failed"}"
            )
            if (!check.optBoolean("streamingCompleted")) {
                appendLine(check.optString("error", "USB error ${check.optInt("transferError")}"))
            }
            check.optJSONArray("cleanupWarnings")?.let { warnings ->
                repeat(warnings.length()) { appendLine(warnings.getString(it)) }
            }
        }
    }
    sessions.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.lastOrNull {
            it.optInt("vendorId") == device.device.vendorId && it.optInt("productId") == device.device.productId
        }?.let { session ->
            appendLine()
            appendLine("Last session")
            if (session.optBoolean("initializationFailed")) {
                appendLine(session.optString("error"))
            } else {
                appendLine("${session.optLong("completedFrames")} frames")
                appendLine("USB error: ${session.optInt("transferError")}")
                appendLine("Underruns: ${session.optLong("underruns")}")
            }
        }
    appendLine()
    appendLine(
        "Direct USB uses DAC volume and bypasses EQ and fades. Unsupported tracks use Android playback."
    )
    appendLine("Export includes full device and playback details.")
}.trim()

internal fun usbRateLabel(rate: Int): String = "${rate / 1000f} kHz"
