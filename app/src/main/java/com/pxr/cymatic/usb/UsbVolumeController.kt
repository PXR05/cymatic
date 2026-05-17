package com.pxr.cymatic.usb

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import kotlin.math.roundToInt

object UsbVolumeController {
    private const val TAG = "UsbVolumeController"
    private const val USB_DT_INTERFACE = 0x04
    private const val USB_DT_CS_INTERFACE = 0x24
    private const val USB_CLASS_AUDIO = 0x01
    private const val USB_SUBCLASS_AUDIOCONTROL = 0x01
    private const val UAC_FU_DESCRIPTOR = 0x06
    private const val VOLUME_CONTROL = 0x02
    private const val UAC1_VOLUME_BIT = 1 shl 1
    private const val UAC2_VOLUME_BIT = 1 shl 2

    private data class VolumeCaps(
        val unitId: Int,
        val interfaceNumber: Int,
        val valueSize: Int,
        val min: Int,
        val max: Int,
        val res: Int
    )

    @Volatile
    private var cachedCaps: VolumeCaps? = null
    @Volatile
    private var cachedConnection: UsbDeviceConnection? = null

    fun adjustVolumeDb(stepDb: Float): Boolean {
        val connection = UsbConnectionStore.getConnection() ?: return false
        val caps = ensureCaps(connection) ?: return false
        val current = readVolume(connection, caps) ?: return false
        val stepValue = if (caps.res > 0) caps.res else (1.shl(8))
        val delta = (stepDb * 256f).roundToInt().takeIf { it != 0 } ?: stepValue
        val target = (current + delta).coerceIn(caps.min, caps.max)
        return setVolume(connection, caps, target)
    }

    fun adjustVolumeSteps(direction: Int): Boolean {
        val connection = UsbConnectionStore.getConnection() ?: return false
        Log.i(TAG, "Adjusting volume by steps: direction=$direction")
        val caps = ensureCaps(connection) ?: return false
        Log.i(TAG, "Volume caps: min=${caps.min} max=${caps.max} res=${caps.res}")
        val current = readVolume(connection, caps) ?: return false
        Log.i(TAG, "Current volume: $current")
        val stepValue = if (caps.res > 0) caps.res else (1.shl(8))
        val target = (current + (stepValue * direction)).coerceIn(caps.min, caps.max)
        Log.i(TAG, "Target volume: $target")
        return setVolume(connection, caps, target)
    }

    private fun ensureCaps(connection: UsbDeviceConnection): VolumeCaps? {
        if (cachedConnection === connection && cachedCaps != null) return cachedCaps
        val caps = parseFeatureUnit(connection)
        cachedCaps = caps
        cachedConnection = connection
        return caps
    }

    private fun parseFeatureUnit(connection: UsbDeviceConnection): VolumeCaps? {
        val raw = connection.rawDescriptors ?: return null
        var i = 0
        var inAudioControl = false
        var controlInterfaceNumber = -1

        while (i + 1 < raw.size) {
            val length = raw[i].toInt() and 0xFF
            if (length < 2) break
            if (i + length > raw.size) break

            val type = raw[i + 1].toInt() and 0xFF
            if (type == USB_DT_INTERFACE && length >= 9) {
                val ifaceNumber = raw[i + 2].toInt() and 0xFF
                val ifaceClass = raw[i + 5].toInt() and 0xFF
                val ifaceSub = raw[i + 6].toInt() and 0xFF
                inAudioControl = ifaceClass == USB_CLASS_AUDIO && ifaceSub == USB_SUBCLASS_AUDIOCONTROL
                if (inAudioControl) {
                    controlInterfaceNumber = ifaceNumber
                }
            } else if (inAudioControl && type == USB_DT_CS_INTERFACE && length >= 7) {
                val subtype = raw[i + 2].toInt() and 0xFF
                if (subtype == UAC_FU_DESCRIPTOR) {
                    val unitId = raw[i + 3].toInt() and 0xFF

                    val supportsVolume = hasVolumeControlUac1(raw, i, length) ||
                                        hasVolumeControlUac2(raw, i, length)

                    if (supportsVolume && controlInterfaceNumber >= 0) {
                        Log.i(TAG, "Feature Unit candidate: unitId=$unitId iface=$controlInterfaceNumber, querying range...")
                        val range = queryVolumeRange(connection, unitId, controlInterfaceNumber)
                        if (range != null) {
                            Log.i(TAG, "Feature Unit found: unitId=$unitId iface=$controlInterfaceNumber")
                            return range
                        }
                    } else if (controlInterfaceNumber >= 0) {
                        // Probe anyway – some descriptors have non-standard bitmasks
                        Log.d(TAG, "Probing Feature Unit (no volume bit set): unitId=$unitId")
                        val range = queryVolumeRange(connection, unitId, controlInterfaceNumber)
                        if (range != null) {
                            Log.i(TAG, "Feature Unit found via probe: unitId=$unitId iface=$controlInterfaceNumber")
                            return range
                        }
                    }
                }
            }
            i += length
        }
        Log.w(TAG, "No Feature Unit with volume control found")
        return null
    }

    private fun hasVolumeControlUac1(raw: ByteArray, base: Int, length: Int): Boolean {
        if (base + 6 >= raw.size) return false
        val controlSize = raw[base + 5].toInt() and 0xFF
        if (controlSize == 0 || controlSize > 4) return false
        val controlsOffset = base + 6
        if (controlsOffset + controlSize > base + length) return false
        var bitmask = 0
        for (index in 0 until controlSize) {
            bitmask = bitmask or ((raw[controlsOffset + index].toInt() and 0xFF) shl (index * 8))
        }
        return (bitmask and UAC1_VOLUME_BIT) != 0
    }

    private fun hasVolumeControlUac2(raw: ByteArray, base: Int, length: Int): Boolean {
        if (length < 10) return false
        if (base + 9 >= raw.size) return false
        val controlSize = raw[base + 5].toInt() and 0xF
        val bmControls = (raw[base + 5].toInt() and 0xFF) or
                         ((raw[base + 6].toInt() and 0xFF) shl 8) or
                         ((raw[base + 7].toInt() and 0xFF) shl 16) or
                         ((raw[base + 8].toInt() and 0xFF) shl 24)
        return (bmControls and UAC2_VOLUME_BIT) != 0
    }

    private fun queryVolumeRange(
        connection: UsbDeviceConnection,
        unitId: Int,
        ifaceNumber: Int
    ): VolumeCaps? {
        val min = readControl(connection, unitId, ifaceNumber, 0x82, 2)
            ?: readControl(connection, unitId, ifaceNumber, 0x82, 4)
            ?: return null
        val max = readControl(connection, unitId, ifaceNumber, 0x83, min.size)
            ?: return null
        val res = readControl(connection, unitId, ifaceNumber, 0x84, min.size)
            ?: ByteArray(min.size)

        val size = min.size
        val minValue = decodeSigned(min)
        val maxValue = decodeSigned(max)
        val resValue = decodeSigned(res)
        return VolumeCaps(unitId, ifaceNumber, size, minValue, maxValue, resValue)
    }

    private fun readVolume(connection: UsbDeviceConnection, caps: VolumeCaps): Int? {
        val data = readControl(connection, caps.unitId, caps.interfaceNumber, 0x81, caps.valueSize)
            ?: readControl(connection, caps.unitId, caps.interfaceNumber, 0x01, caps.valueSize)
            ?: return null
        return decodeSigned(data)
    }

    private fun setVolume(connection: UsbDeviceConnection, caps: VolumeCaps, value: Int): Boolean {
        val data = encodeSigned(value, caps.valueSize)
        val selector = VOLUME_CONTROL
        val wValue = (selector shl 8) or 0
        val wIndex = (caps.unitId shl 8) or caps.interfaceNumber
        val result = connection.controlTransfer(
            0x21,
            0x01,
            wValue,
            wIndex,
            data,
            data.size,
            1000
        )
        Log.i(TAG, "Set volume control transfer result: $result")
        return result >= 0
    }

    private fun readControl(
        connection: UsbDeviceConnection,
        unitId: Int,
        ifaceNumber: Int,
        request: Int,
        size: Int
    ): ByteArray? {
        val selector = VOLUME_CONTROL
        val wValue = (selector shl 8) or 0
        val wIndex = (unitId shl 8) or ifaceNumber
        val data = ByteArray(size)
        val result = connection.controlTransfer(
            0xA1,
            request,
            wValue,
            wIndex,
            data,
            data.size,
            1000
        )
        return if (result >= 0) data else null
    }

    private fun decodeSigned(bytes: ByteArray): Int {
        var value = 0
        for (i in bytes.indices.reversed()) {
            value = (value shl 8) or (bytes[i].toInt() and 0xFF)
        }
        val signBit = 1 shl ((bytes.size * 8) - 1)
        return if ((value and signBit) != 0) value - (1 shl (bytes.size * 8)) else value
    }

    private fun encodeSigned(value: Int, size: Int): ByteArray {
        var temp = value
        val data = ByteArray(size)
        for (i in 0 until size) {
            data[i] = (temp and 0xFF).toByte()
            temp = temp shr 8
        }
        return data
    }
}
