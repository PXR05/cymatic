package com.pxr.cymatic.audio.dsd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.pcm.IntegerDecoderConfiguration
import com.pxr.cymatic.audio.pcm.IntegerFrameDecoder
import com.pxr.cymatic.dsd.DsdFrameDecoder
import java.nio.ByteBuffer

internal data class DsdOutput(val sourceRate: Int, val dop: Boolean) {
    val label: String
        get() = if (dop) "DSD over PCM (DoP)" else "DSD converted to PCM"
}

@UnstableApi
internal fun Format.dsfHeader(): DsfHeader? {
    if (sampleMimeType != DSD_MIME_TYPE || cryptoType != C.CRYPTO_TYPE_NONE) return null
    val bytes = initializationData.singleOrNull() ?: return null
    return runCatching { DsfHeader.parse(bytes) }
        .getOrNull()
        ?.takeIf {
            it.sampleRate == sampleRate && it.channels == channelCount
        }
}

@UnstableApi
internal fun Format.dsdOutputFormat(dop: Boolean): Format? {
    val info = dsfHeader() ?: return null
    val rate = if (dop) info.sampleRate / 16 else info.pcmRate
    if (dop && rate > 384000) return null
    return Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(info.channels)
        .setSampleRate(rate)
        .setPcmEncoding(C.ENCODING_PCM_24BIT)
        .setCustomData(DsdOutput(info.sampleRate, dop))
        .build()
}

@UnstableApi
internal fun Format.dsdDecoderConfiguration(output: Format): IntegerDecoderConfiguration {
    val info = requireNotNull(dsfHeader())
    val mode = requireNotNull(output.customData as? DsdOutput)
    return IntegerDecoderConfiguration(
        mode.label,
        output,
        info.channels * 3,
        (info.blockSamples / (info.sampleRate / output.sampleRate)) * info.channels * 3,
        info.blockBytes + 4,
    ) {
        object : IntegerFrameDecoder {
            private val decoder =
                DsdFrameDecoder(
                    info.sampleRate,
                    output.sampleRate,
                    info.channels,
                    info.lsbFirst,
                    mode.dop,
                )

            override fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int =
                decoder.decode(input, output, reset)

            override fun close() = decoder.close()
        }
    }
}
