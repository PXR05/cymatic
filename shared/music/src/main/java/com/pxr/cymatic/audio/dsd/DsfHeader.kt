package com.pxr.cymatic.audio.dsd

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val DSD_MIME_TYPE = "audio/dsd"
internal const val DSF_HEADER_BYTES = 92
internal const val DSF_BLOCK_BYTES = 4096

internal data class DsfHeader(
    val fileSize: Long,
    val metadataOffset: Long,
    val channels: Int,
    val sampleRate: Int,
    val lsbFirst: Boolean,
    val sampleCount: Long,
    val dataBytes: Long,
) {
    val blockBytes: Int
        get() = DSF_BLOCK_BYTES * channels

    val blockSamples: Int
        get() = DSF_BLOCK_BYTES * 8

    val blockCount: Long
        get() = (sampleCount - 1) / blockSamples + 1

    val durationUs: Long
        get() =
            sampleCount / sampleRate * 1_000_000 + sampleCount % sampleRate * 1_000_000 / sampleRate

    val pcmRate: Int
        get() = if (sampleRate % 44100 == 0) 176400 else 192000

    val label: String
        get() = "DSD${sampleRate / (pcmRate / 4)}"

    fun position(block: Long): Long =
        DSF_HEADER_BYTES + block.coerceIn(0, blockCount - 1) * blockBytes

    fun timeUs(block: Long): Long = block * blockSamples * 1_000_000 / sampleRate

    companion object {
        fun parse(bytes: ByteArray, length: Long = -1L): DsfHeader {
            require(bytes.size >= DSF_HEADER_BYTES) { "Truncated DSF header" }
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun tag(offset: Int, value: String) =
                value.indices.all { bytes[offset + it] == value[it].code.toByte() }
            require(tag(0, "DSD ") && data.getLong(4) == 28L) { "Invalid DSF signature" }
            require(tag(28, "fmt ") && data.getLong(32) == 52L) { "Invalid DSF format chunk" }
            require(data.getInt(40) == 1 && data.getInt(44) == 0) {
                "Unsupported DSF version or compression"
            }
            val channels = data.getInt(52)
            require(channels in 1..2 && data.getInt(48) == channels) {
                "DSF requires mono or stereo DSD"
            }
            val rate = data.getInt(56)
            require(
                rate in
                    setOf(
                        2822400,
                        5644800,
                        11289600,
                        22579200,
                        3072000,
                        6144000,
                        12288000,
                        24576000,
                    )
            ) {
                "Unsupported DSF sample rate"
            }
            val bitOrder = data.getInt(60)
            require(bitOrder == 1 || bitOrder == 8) { "Invalid DSF bit order" }
            val samples = data.getLong(64)
            require(samples > 0 && samples <= rate.toLong() * 86400) { "Invalid DSF sample count" }
            require(data.getInt(72) == DSF_BLOCK_BYTES && data.getInt(76) == 0) {
                "Invalid DSF block size"
            }
            require(tag(80, "data")) { "Missing DSF data chunk" }
            val dataSize = data.getLong(84) - 12
            val blocks = (samples - 1) / (DSF_BLOCK_BYTES * 8) + 1
            require(dataSize == blocks * DSF_BLOCK_BYTES * channels) {
                "DSF data length does not match its sample count"
            }
            val fileSize = data.getLong(12)
            val metadata = data.getLong(20)
            val end = DSF_HEADER_BYTES + dataSize
            require(fileSize >= end && (length < 0 || fileSize == length)) {
                "Truncated or invalid DSF file length"
            }
            require(metadata == 0L || metadata in end until fileSize) {
                "Invalid DSF metadata offset"
            }
            return DsfHeader(fileSize, metadata, channels, rate, bitOrder == 1, samples, dataSize)
        }
    }
}
