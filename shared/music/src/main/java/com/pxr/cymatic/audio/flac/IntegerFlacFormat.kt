package com.pxr.cymatic.audio.flac

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.FlacStreamMetadata
import com.pxr.cymatic.audio.usb.UsbPcmLayout
import com.pxr.cymatic.flac.FlacFrameDecoder

@UnstableApi
internal fun Format.integerFlacMetadata(): FlacStreamMetadata? {
    if (sampleMimeType != MimeTypes.AUDIO_FLAC || cryptoType != C.CRYPTO_TYPE_NONE) return null
    val info = initializationData.singleOrNull() ?: return null
    if (
        info.size != 42 ||
        !info.copyOfRange(0, 4).contentEquals(byteArrayOf(102, 76, 97, 67)) ||
        (info[4].toInt() and 0x7f) != 0 ||
        info[5] != 0.toByte() ||
        info[6] != 0.toByte() ||
        info[7] != 34.toByte()
    )
        return null
    val metadata = FlacStreamMetadata(info, 8)
    return metadata.takeIf {
        it.channels in 1..2 &&
                it.channels == channelCount &&
                it.sampleRate == sampleRate &&
                it.sampleRate in 8000..384000 &&
                it.bitsPerSample in listOf(16, 20, 24, 32) &&
                it.minBlockSizeSamples in 16..it.maxBlockSizeSamples &&
                it.maxBlockSizeSamples in 16..65535 &&
                (it.maxFrameSize == 0 || it.minFrameSize <= it.maxFrameSize) &&
                it.maxFrameSize <= FlacFrameDecoder.MAX_FRAME_BYTES &&
                it.minFrameSize <= FlacFrameDecoder.MAX_FRAME_BYTES
    }
}

@UnstableApi
internal fun Format.integerFlacOutputFormat(): Format? {
    val metadata = integerFlacMetadata() ?: return null
    val encoding =
        when (metadata.bitsPerSample) {
            16 -> C.ENCODING_PCM_16BIT
            20,
            24 -> C.ENCODING_PCM_24BIT

            32 -> C.ENCODING_PCM_32BIT
            else -> return null
        }
    return Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(metadata.channels)
        .setSampleRate(metadata.sampleRate)
        .setPcmEncoding(encoding)
        .setCustomData(
            UsbPcmLayout(
                metadata.channels,
                (metadata.bitsPerSample + 7) / 8,
                metadata.bitsPerSample,
            )
        )
        .build()
}
