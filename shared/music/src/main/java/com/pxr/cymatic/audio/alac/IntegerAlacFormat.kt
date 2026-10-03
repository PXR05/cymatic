package com.pxr.cymatic.audio.alac

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.alac.AlacFrameDecoder
import com.pxr.cymatic.audio.usb.UsbPcmLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class AlacMetadata(
    val config: ByteArray,
    val frameSamples: Int,
    val bits: Int,
    val channels: Int,
    val rate: Int,
    val maxPacketBytes: Int,
)

@UnstableApi
internal fun Format.integerAlacMetadata(): AlacMetadata? {
    if (sampleMimeType != MimeTypes.AUDIO_ALAC || cryptoType != C.CRYPTO_TYPE_NONE) return null
    val config = initializationData.singleOrNull() ?: return null
    if (config.size != 24 && config.size != 48) return null
    val data = ByteBuffer.wrap(config).order(ByteOrder.BIG_ENDIAN)
    val frames = data.getInt(0).toLong() and 0xffffffffL
    val bits = config[5].toInt() and 0xff
    val channels = config[9].toInt() and 0xff
    val riceLimit = config[8].toInt() and 0xff
    val maxPacket = data.getInt(12).toLong() and 0xffffffffL
    val rate = data.getInt(20).toLong() and 0xffffffffL
    val channelTag = ((if (channels == 1) 100 else 101) shl 16) or channels
    if (
        config.size == 48 &&
        (data.getInt(24) != 24 ||
                data.getInt(28) != 0x6368616e ||
                data.getInt(32) != 0 ||
                data.getInt(36) != channelTag ||
                data.getInt(40) != 0 ||
                data.getInt(44) != 0)
    )
        return null
    if (
        config[4] != 0.toByte() ||
        frames !in 1L..AlacFrameDecoder.MAX_FRAME_SAMPLES.toLong() ||
        bits !in listOf(16, 20, 24, 32) ||
        channels !in 1..2 ||
        channels != channelCount ||
        riceLimit !in 1..14 ||
        rate !in 8000L..384000L ||
        rate != sampleRate.toLong() ||
        maxPacket > AlacFrameDecoder.MAX_PACKET_BYTES ||
        (maxInputSize != Format.NO_VALUE &&
                maxInputSize > AlacFrameDecoder.MAX_PACKET_BYTES + 32)
    )
        return null
    return AlacMetadata(
        config.copyOfRange(0, 24),
        frames.toInt(),
        bits,
        channels,
        rate.toInt(),
        maxPacket.toInt().takeIf { it != 0 } ?: AlacFrameDecoder.MAX_PACKET_BYTES,
    )
}

@UnstableApi
internal fun Format.integerAlacOutputFormat(): Format? {
    val metadata = integerAlacMetadata() ?: return null
    val encoding =
        when (metadata.bits) {
            16 -> C.ENCODING_PCM_16BIT
            20,
            24 -> C.ENCODING_PCM_24BIT

            32 -> C.ENCODING_PCM_32BIT
            else -> return null
        }
    return Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(metadata.channels)
        .setSampleRate(metadata.rate)
        .setPcmEncoding(encoding)
        .setCustomData(UsbPcmLayout(metadata.channels, (metadata.bits + 7) / 8, metadata.bits))
        .build()
}
