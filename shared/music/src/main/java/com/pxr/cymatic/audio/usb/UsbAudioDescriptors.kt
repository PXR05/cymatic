package com.pxr.cymatic.audio.usb

internal object UsbAudioDescriptors {
    fun parse(bytes: ByteArray): UsbDescriptorSummary {
        val formats = mutableListOf<UsbPcmFormat>()
        val clocks = mutableListOf<UsbClockSource>()
        val terminals = mutableListOf<UsbTerminalClock>()
        val collections = mutableListOf<UsbAudioCollection>()
        val features = mutableListOf<UsbFeatureUnit>()
        val outputs = mutableListOf<UsbOutputTerminal>()
        val warnings = mutableListOf<String>()
        var offset = 0
        var configuration = -1
        var interfaceNumber = -1
        var alternate = 0
        var subclass = 0
        var protocol = 0
        var audio = false
        var channels: Int? = null
        var channelMask: Long? = null
        var pcm = false
        var terminalLink: Int? = null
        while (offset < bytes.size) {
            if (bytes.size - offset < 2) {
                warnings += "Truncated descriptor header at byte $offset"
                break
            }
            val length = bytes.u8(offset)
            if (length < 2 || length > bytes.size - offset) {
                warnings += "Invalid descriptor length $length at byte $offset"
                break
            }
            val descriptor = bytes.copyOfRange(offset, offset + length)
            when (descriptor.u8(1)) {
                2 -> if (length >= 9) configuration = descriptor.u8(5)
                4 ->
                    if (length >= 9) {
                        interfaceNumber = descriptor.u8(2)
                        alternate = descriptor.u8(3)
                        audio = descriptor.u8(5) == 1
                        subclass = descriptor.u8(6)
                        protocol = descriptor.u8(7)
                        channels = null
                        channelMask = null
                        pcm = false
                        terminalLink = null
                        if (audio && subclass in 1..2 && protocol != 0 && protocol != 0x20) {
                            warnings +=
                                "Audio protocol $protocol on interface $interfaceNumber is not decoded by this prototype"
                        }
                    }

                0x24 ->
                    if (audio && length >= 3) {
                        val subtype = descriptor.u8(2)
                        if (subclass == 1 && protocol == 0 && subtype == 1 && length >= 8) {
                            val count = descriptor.u8(7)
                            if (length >= 8 + count && descriptor.le16(3) == 0x0100) {
                                repeat(count) {
                                    collections +=
                                        UsbAudioCollection(
                                            configuration,
                                            interfaceNumber,
                                            descriptor.u8(8 + it),
                                        )
                                }
                            } else
                                warnings +=
                                    "Malformed UAC1 AudioControl collection on interface $interfaceNumber"
                        }
                        if (subclass == 1 && protocol == 0x20 && subtype == 0x0a && length >= 8) {
                            val frequencyAccess = descriptor.u8(5) and 3
                            clocks +=
                                UsbClockSource(
                                    configuration,
                                    interfaceNumber,
                                    descriptor.u8(3),
                                    frequencyAccess == 1 || frequencyAccess == 3,
                                    frequencyAccess == 3,
                                    ((descriptor.u8(5) shr 2) and 3) in listOf(1, 3),
                                )
                        }
                        if (subclass == 1 && protocol == 0x20 && subtype == 2 && length >= 17) {
                            terminals +=
                                UsbTerminalClock(
                                    configuration,
                                    interfaceNumber,
                                    descriptor.u8(3),
                                    descriptor.u8(7),
                                    descriptor.u8(8),
                                    descriptor.le32(9),
                                )
                        }
                        if (subclass == 1 && protocol == 0 && subtype == 2 && length >= 12) {
                            terminals +=
                                UsbTerminalClock(
                                    configuration,
                                    interfaceNumber,
                                    descriptor.u8(3),
                                    null,
                                    descriptor.u8(7),
                                    descriptor.le16(8).toLong(),
                                )
                        }
                        if (
                            subclass == 1 &&
                            subtype == 3 &&
                            ((protocol == 0x20 && length >= 12) ||
                                    (protocol == 0 && length >= 9))
                        ) {
                            outputs +=
                                UsbOutputTerminal(
                                    configuration,
                                    interfaceNumber,
                                    descriptor.u8(3),
                                    descriptor.u8(7),
                                    descriptor.le16(4),
                                )
                        }
                        if (subclass == 1 && protocol == 0x20 && subtype == 6) {
                            if (length >= 10 && (length - 6) % 4 == 0) {
                                features +=
                                    UsbFeatureUnit(
                                        configuration,
                                        interfaceNumber,
                                        descriptor.u8(3),
                                        descriptor.u8(4),
                                        List((length - 6) / 4) { descriptor.le32(5 + it * 4) },
                                    )
                            } else
                                warnings +=
                                    "Malformed UAC2 feature unit on interface $interfaceNumber"
                        }
                        if (subclass == 1 && protocol == 0 && subtype == 6 && length >= 7) {
                            val size = descriptor.u8(5)
                            if (size in 1..4 && length >= 7 + size && (length - 7) % size == 0) {
                                features +=
                                    UsbFeatureUnit(
                                        configuration,
                                        interfaceNumber,
                                        descriptor.u8(3),
                                        descriptor.u8(4),
                                        List((length - 7) / size) { channel ->
                                            (0 until size).fold(0L) { value, byte ->
                                                value or
                                                        (descriptor
                                                            .u8(6 + channel * size + byte)
                                                            .toLong() shl (byte * 8))
                                            }
                                        },
                                        protocol,
                                    )
                            } else
                                warnings +=
                                    "Malformed UAC1 feature unit on interface $interfaceNumber"
                        }
                        if (subclass == 2 && subtype == 1) {
                            if (length >= 4) terminalLink = descriptor.u8(3)
                            if (protocol == 0 && length >= 7) pcm = descriptor.le16(5) == 1
                            if (protocol == 0x20 && length >= 16) {
                                channels = descriptor.u8(10)
                                channelMask = descriptor.le32(11)
                                pcm = descriptor.le32(6) and 1L != 0L
                            }
                        }
                        if (subclass == 2 && subtype == 2 && length >= 4 && descriptor.u8(3) == 1) {
                            if (protocol == 0 && length >= 8) {
                                val count = descriptor.u8(7)
                                val required = if (count == 0) 14 else 8 + count * 3
                                if (length < required) {
                                    warnings +=
                                        "Truncated UAC1 format on interface $interfaceNumber/$alternate"
                                } else {
                                    formats +=
                                        UsbPcmFormat(
                                            configuration,
                                            interfaceNumber,
                                            alternate,
                                            protocol,
                                            descriptor.u8(4),
                                            descriptor.u8(5),
                                            descriptor.u8(6),
                                            pcm,
                                            if (count == 0) emptyList()
                                            else List(count) { descriptor.le24(8 + it * 3) },
                                            if (count == 0)
                                                descriptor.le24(8) to descriptor.le24(11)
                                            else null,
                                            terminalLink,
                                        )
                                }
                            } else if (protocol == 0x20 && length >= 6) {
                                formats +=
                                    UsbPcmFormat(
                                        configuration,
                                        interfaceNumber,
                                        alternate,
                                        protocol,
                                        channels,
                                        descriptor.u8(4),
                                        descriptor.u8(5),
                                        pcm,
                                        emptyList(),
                                        null,
                                        terminalLink,
                                        channelMask = channelMask,
                                    )
                            }
                        }
                    }
            }
            offset += length
        }
        val resolvedFormats = formats.map { format ->
            val collection =
                collections
                    .filter {
                        it.configuration == format.configuration &&
                                it.streamingInterface == format.interfaceNumber
                    }
                    .singleOrNull()
            val terminal =
                terminals
                    .filter {
                        it.configuration == format.configuration &&
                                it.id == format.terminalLink &&
                                (format.protocol != 0 ||
                                        it.controlInterface == collection?.controlInterface)
                    }
                    .singleOrNull()
            val clock = terminal?.let { value ->
                clocks
                    .filter {
                        it.configuration == value.configuration &&
                                it.controlInterface == value.controlInterface &&
                                it.id == value.clockId
                    }
                    .singleOrNull()
            }
            if (format.protocol == 0x20 && clock == null) {
                warnings +=
                    "Clock is unresolved for interface ${format.interfaceNumber}/${format.alternateSetting}"
            }
            format.copy(
                clockSourceId = clock?.id,
                controlInterface =
                    terminal?.controlInterface.takeIf { terminal?.channels == format.channels },
                channelMask = format.channelMask ?: terminal?.channelMask,
            )
        }
        return UsbDescriptorSummary(resolvedFormats, clocks.distinct(), features, outputs, warnings)
    }
}

private data class UsbTerminalClock(
    val configuration: Int,
    val controlInterface: Int,
    val id: Int,
    val clockId: Int?,
    val channels: Int,
    val channelMask: Long,
)

private data class UsbAudioCollection(
    val configuration: Int,
    val controlInterface: Int,
    val streamingInterface: Int,
)
