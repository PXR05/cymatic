package com.pxr.cymatic.audio.usb

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaLibraryInfo
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class UsbAudioDiagnostics(private val context: Context) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    suspend fun inspect(): UsbDiagnosticReport =
        withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val devices =
                usbManager.deviceList.values
                    .filter(::isUsbAudioDevice)
                    .sortedWith(compareBy({ it.vendorId }, { it.productId }, { it.deviceId }))
                    .map { device ->
                        currentCoroutineContext().ensureActive()
                        inspectDevice(device)
                    }
            val outputs =
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter {
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                    }
                    .map { device ->
                        val output =
                            JSONObject()
                                .put("id", device.id)
                                .put("name", device.productName.toString())
                                .put("type", device.type)
                                .put("sampleRatesHz", JSONArray(device.sampleRates.toList()))
                                .put("channelCounts", JSONArray(device.channelCounts.toList()))
                                .put("encodings", JSONArray(device.encodings.map(::encodingName)))
                                .put("isActualPlaybackRoute", JSONObject.NULL)
                        if (Build.VERSION.SDK_INT >= 34) {
                            try {
                                output.put("mixerAttributes", mixerAttributes(device))
                            } catch (e: RuntimeException) {
                                output.put("mixerQueryError", e.message ?: e.javaClass.simpleName)
                            }
                        } else {
                            output.put("mixerQueryUnavailable", "Requires Android 14 or newer")
                        }
                        output
                    }
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (devices.isEmpty())
                warnings += "No USB audio devices enumerated; check connection, host mode and power"
            UsbDiagnosticReport(
                Instant.now().toString(),
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST),
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("androidRelease", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                    .put("package", context.packageName)
                    .put("appVersion", packageInfo.versionName)
                    .put("media3Version", MediaLibraryInfo.VERSION),
                devices,
                outputs,
                warnings,
            )
        }

    private suspend fun inspectDevice(device: UsbDevice): UsbDiagnosticDevice {
        val permission = usbManager.hasPermission(device)
        val label =
            runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "USB audio %04x:%04x".format(device.vendorId, device.productId)
        val details =
            JSONObject()
                .put("name", label)
                .put("sessionId", device.deviceId)
                .put("vendorId", device.vendorId)
                .put("productId", device.productId)
                .put("permissionGranted", permission)
        val configurations = JSONArray()
        repeat(device.configurationCount) { index ->
            val configuration = device.getConfiguration(index)
            val interfaces = JSONArray()
            repeat(configuration.interfaceCount) { interfaceIndex ->
                val usbInterface = configuration.getInterface(interfaceIndex)
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                    val endpoints = JSONArray()
                    repeat(usbInterface.endpointCount) { endpointIndex ->
                        val endpoint = usbInterface.getEndpoint(endpointIndex)
                        endpoints.put(
                            JSONObject()
                                .put("address", endpoint.address)
                                .put(
                                    "direction",
                                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) "OUT"
                                    else "IN",
                                )
                                .put("transferType", endpoint.type)
                                .put("attributes", endpoint.attributes)
                                .put("synchronizationType", (endpoint.attributes shr 2) and 3)
                                .put("usageType", (endpoint.attributes shr 4) and 3)
                                .put("maxPacketSize", endpoint.maxPacketSize)
                                .put("interval", endpoint.interval)
                        )
                    }
                    interfaces.put(
                        JSONObject()
                            .put("number", usbInterface.id)
                            .put("alternateSetting", usbInterface.alternateSetting)
                            .put("subclass", usbInterface.interfaceSubclass)
                            .put("protocol", usbInterface.interfaceProtocol)
                            .put("endpoints", endpoints)
                    )
                }
            }
            configurations.put(
                JSONObject()
                    .put("value", configuration.id)
                    .put("maxPowerMa", configuration.maxPower)
                    .put("selfPowered", configuration.isSelfPowered)
                    .put("interfaces", interfaces)
            )
        }
        details.put("configurations", configurations)
        if (permission) {
            val connection = runCatching { usbManager.openDevice(device) }.getOrNull()
            if (connection == null) {
                details.put(
                    "inspectionError",
                    "Could not open device; it may have disconnected or permission changed",
                )
            } else {
                try {
                    val raw = connection.rawDescriptors ?: byteArrayOf()
                    val parsed = UsbAudioDescriptors.parse(raw)
                    details
                        .put(
                            "rawDescriptorsHex",
                            raw.joinToString("") { "%02x".format(it.toInt() and 0xff) },
                        )
                        .put("formats", JSONArray(parsed.formats.map { it.json() }))
                        .put("featureUnits", JSONArray(parsed.features.map { it.json() }))
                        .put(
                            "playbackVolume",
                            JSONArray(
                                parsed.formats.map { format ->
                                    JSONObject()
                                        .put("interface", format.interfaceNumber)
                                        .put("alternateSetting", format.alternateSetting)
                                        .put(
                                            "featureUnit",
                                            parsed.playbackVolume(format)?.id ?: JSONObject.NULL,
                                        )
                                }
                            ),
                        )
                        .put("descriptorWarnings", JSONArray(parsed.warnings))
                    val configuration = ByteArray(1)
                    val result = connection.controlTransfer(0x80, 8, 0, 0, configuration, 1, 300)
                    val activeConfiguration = if (result == 1) configuration.u8(0) else null
                    details.put("activeConfiguration", activeConfiguration ?: JSONObject.NULL)
                    val clocks = JSONArray()
                    for (clock in parsed.clocks.take(8)) {
                        currentCoroutineContext().ensureActive()
                        val report =
                            JSONObject()
                                .put("id", clock.id)
                                .put("configuration", clock.configuration)
                                .put("controlInterface", clock.controlInterface)
                        if (activeConfiguration == clock.configuration && clock.frequencyReadable) {
                            readClockRanges(connection, clock, report)
                        } else {
                            report.put(
                                "rangeQueryUnavailable",
                                "Clock is unreadable or its configuration is not confirmed active",
                            )
                        }
                        clocks.put(report)
                    }
                    details
                        .put("clockSources", clocks)
                        .put(
                            "clockAssociation",
                            "UAC1 terminals and rates are resolved from descriptors; UAC2 uses direct clock sources. Selectors and multipliers are unsupported. Clock ranges alone are not per-format guarantees",
                        )
                        .put("streamingVerified", false)
                } catch (e: RuntimeException) {
                    currentCoroutineContext().ensureActive()
                    details.put("inspectionError", e.message ?: e.javaClass.simpleName)
                } finally {
                    connection.close()
                }
            }
        }
        return UsbDiagnosticDevice(device, label, permission, details)
    }

    private fun readClockRanges(
        connection: UsbDeviceConnection,
        clock: UsbClockSource,
        report: JSONObject,
    ) {
        val index = (clock.id shl 8) or clock.controlInterface
        val header = ByteArray(2)
        if (connection.controlTransfer(0xa1, 2, 0x0100, index, header, header.size, 300) != 2) {
            report.put("rangeQueryError", "GET_RANGE header failed or timed out")
            return
        }
        val count = header.le16(0)
        if (count !in 1..32) {
            report.put("rangeQueryError", "Unexpected range count: $count")
            return
        }
        val bytes = ByteArray(2 + count * 12)
        val received = connection.controlTransfer(0xa1, 2, 0x0100, index, bytes, bytes.size, 300)
        if (received != bytes.size || bytes.le16(0) != count) {
            report.put("rangeQueryError", "Incomplete or changed GET_RANGE response")
            return
        }
        val ranges = JSONArray()
        repeat(count) { range ->
            val offset = 2 + range * 12
            ranges.put(
                JSONObject()
                    .put("minimumHz", bytes.le32(offset))
                    .put("maximumHz", bytes.le32(offset + 4))
                    .put("resolutionHz", bytes.le32(offset + 8))
            )
        }
        report.put("sampleRateRanges", ranges)
    }

    @RequiresApi(34)
    private fun mixerAttributes(device: AudioDeviceInfo): JSONArray =
        JSONArray(
            audioManager.getSupportedMixerAttributes(device).map { attributes ->
                val format = attributes.format
                JSONObject()
                    .put(
                        "behavior",
                        if (
                            attributes.mixerBehavior ==
                            AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                        )
                            "BIT_PERFECT"
                        else "DEFAULT",
                    )
                    .put("encoding", encodingName(format.encoding))
                    .put("sampleRateHz", format.sampleRate)
                    .put("channelMask", format.channelMask)
                    .put("channelIndexMask", format.channelIndexMask)
                    .put("channelCount", format.channelCount)
            }
        )
}

private fun encodingName(encoding: Int): String =
    when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT"
        AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM_24BIT_PACKED"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM_32BIT"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        else -> "ENCODING_$encoding"
    }
