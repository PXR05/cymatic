package com.pxr.cymatic.audio.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import org.json.JSONObject

internal data class UsbStreamingEndpoints(
    val output: UsbEndpoint,
    val feedback: UsbEndpoint?,
    val samplingFrequencyControl: Boolean,
    val feedbackRefreshMs: Int,
    val lockDelayUnits: Int,
    val lockDelay: Int,
) {
    val asynchronous: Boolean
        get() = feedback != null

    val lockDelayApplies: Boolean
        get() = !asynchronous

    val packetCapacity: Int
        get() = (output.maxPacketSize and 0x7ff) * (1 + ((output.maxPacketSize shr 11) and 3))

    fun json() =
        JSONObject()
            .put(
                "synchronization",
                when {
                    asynchronous -> "ASYNCHRONOUS_EXPLICIT_FEEDBACK"
                    output.attributes and 0x0c == 8 -> "ADAPTIVE"
                    else -> "SYNCHRONOUS"
                },
            )
            .put("feedbackEndpoint", feedback?.address ?: JSONObject.NULL)
            .put("feedbackMaxPacketBytes", feedback?.maxPacketSize ?: JSONObject.NULL)
            .put("feedbackInterval", feedback?.interval ?: JSONObject.NULL)
            .put("feedbackAttributes", feedback?.attributes ?: JSONObject.NULL)
            .put("feedbackRefreshMs", feedbackRefreshMs)
            .put("endpointSamplingFrequencyControl", samplingFrequencyControl)
            .put("lockDelayUnits", lockDelayUnits)
            .put("lockDelay", lockDelay)
            .put("lockDelayApplies", lockDelayApplies)
            .put("ignoredAsyncLockDelay", asynchronous && (lockDelayUnits != 0 || lockDelay != 0))

    companion object {
        fun resolve(
            usbInterface: UsbInterface,
            speed: Int,
            descriptors: ByteArray,
            configuration: Int,
        ): UsbStreamingEndpoints? {
            val uac1 = usbInterface.interfaceProtocol == 0
            if (
                usbInterface.interfaceClass != UsbConstants.USB_CLASS_AUDIO ||
                usbInterface.interfaceSubclass != 2 ||
                (!uac1 && usbInterface.interfaceProtocol != 0x20)
            )
                return null
            val endpoints = (0 until usbInterface.endpointCount).map(usbInterface::getEndpoint)
            val output =
                endpoints.singleOrNull {
                    it.direction == UsbConstants.USB_DIR_OUT &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                            (it.attributes and 0xf0) == 0 &&
                            it.address in 1..15
                } ?: return null
            val properties =
                endpointProperties(descriptors, configuration, usbInterface, output.address)
                    ?: return null
            if (
                !validPacketSize(output, speed) ||
                output.interval !in 1..16 ||
                properties.attributes and 0x80 != 0
            )
                return null
            val synchronization = (output.attributes shr 2) and 3
            var feedback: UsbEndpoint? = null
            var refreshMs = 0
            when (synchronization) {
                2,
                3 ->
                    if (
                        endpoints.size != 1 ||
                        (uac1 && properties.synchAddress != 0) ||
                        properties.lockUnits !in 0..2 ||
                        (properties.lockUnits == 0 && properties.lockDelay != 0)
                    )
                        return null

                1 -> {
                    feedback =
                        endpoints.singleOrNull {
                            it.address in 0x81..0x8f &&
                                    (it.attributes == 0x11 || (uac1 && it.attributes == 1)) &&
                                    it.maxPacketSize in (if (speed == 3) 4..4 else 3..4) &&
                                    it.interval in 1..16
                        } ?: return null
                    val ticks = if (speed == 3) 8000 else 1000
                    if (endpoints.size != 2 || (1 shl (feedback.interval - 1)) > ticks / 4)
                        return null
                    if (uac1) {
                        if (properties.synchAddress != feedback.address) return null
                        val standard =
                            standardEndpoint(
                                descriptors,
                                configuration,
                                usbInterface,
                                feedback.address,
                            ) ?: return null
                        if (standard.size < 9 || standard.u8(8) != 0 || standard.u8(7) !in 1..9)
                            return null
                        refreshMs = 1 shl standard.u8(7)
                    }
                }

                else -> return null
            }
            return UsbStreamingEndpoints(
                output,
                feedback,
                uac1 && properties.attributes and 1 != 0,
                refreshMs,
                properties.lockUnits,
                properties.lockDelay,
            )
        }

        private fun validPacketSize(endpoint: UsbEndpoint, speed: Int): Boolean {
            val packet = endpoint.maxPacketSize
            val transactions = (packet shr 11) and 3
            return packet and 0xe000 == 0 &&
                    (packet and 0x7ff) in 1..(if (speed == 3) 1024 else 1023) &&
                    transactions != 3 &&
                    (speed == 3 || transactions == 0)
        }

        private data class Properties(
            val attributes: Int,
            val synchAddress: Int,
            val lockUnits: Int,
            val lockDelay: Int,
        )

        private fun endpointProperties(
            bytes: ByteArray,
            configuration: Int,
            target: UsbInterface,
            address: Int,
        ): Properties? {
            val selected = endpointDescriptors(bytes, configuration, target, address) ?: return null
            val standard = selected.first
            val general = selected.second ?: return null
            val uac1 = target.interfaceProtocol == 0
            if (standard.size < (if (uac1) 9 else 7) || general.size < (if (uac1) 7 else 8))
                return null
            return Properties(
                general.u8(3),
                if (uac1) standard.u8(8) else 0,
                general.u8(if (uac1) 4 else 5),
                general.le16(if (uac1) 5 else 6),
            )
        }

        private fun standardEndpoint(
            bytes: ByteArray,
            configuration: Int,
            target: UsbInterface,
            address: Int,
        ): ByteArray? = endpointDescriptors(bytes, configuration, target, address)?.first

        private fun endpointDescriptors(
            bytes: ByteArray,
            configuration: Int,
            target: UsbInterface,
            address: Int,
        ): Pair<ByteArray, ByteArray?>? {
            var offset = 0
            var matchingConfiguration = false
            var matchingInterface = false
            var matchingEndpoint = false
            var standard: ByteArray? = null
            var general: ByteArray? = null
            while (offset + 2 <= bytes.size) {
                val length = bytes.u8(offset)
                if (length < 2 || length > bytes.size - offset) return null
                val descriptor = bytes.copyOfRange(offset, offset + length)
                when (descriptor.u8(1)) {
                    2 -> {
                        matchingConfiguration = length >= 9 && descriptor.u8(5) == configuration
                        matchingInterface = false
                        matchingEndpoint = false
                    }

                    4 -> {
                        matchingInterface =
                            matchingConfiguration &&
                                    length >= 9 &&
                                    descriptor.u8(2) == target.id &&
                                    descriptor.u8(3) == target.alternateSetting
                        matchingEndpoint = false
                    }

                    5 -> {
                        matchingEndpoint =
                            matchingInterface && length >= 7 && descriptor.u8(2) == address
                        if (matchingEndpoint) {
                            if (standard != null) return null
                            standard = descriptor
                        }
                    }

                    0x25 ->
                        if (matchingEndpoint && length >= 3 && descriptor.u8(2) == 1) {
                            if (general != null) return null
                            general = descriptor
                        }
                }
                offset += length
            }
            return if (offset == bytes.size) standard?.let { it to general } else null
        }
    }
}
