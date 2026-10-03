package com.pxr.cymatic.audio.flac

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.pcm.IntegerDecoderConfiguration
import com.pxr.cymatic.audio.pcm.IntegerFrameDecoder
import com.pxr.cymatic.flac.FlacFrameDecoder
import java.nio.ByteBuffer

@UnstableApi
internal fun Format.integerFlacDecoderConfiguration(): IntegerDecoderConfiguration {
    val metadata = requireNotNull(integerFlacMetadata()) { "Unsupported integer FLAC source" }
    val frameBytes = metadata.channels * ((metadata.bitsPerSample + 7) / 8)
    val header = initializationData.single().copyOf().also { it[4] = 0x80.toByte() }
    return IntegerDecoderConfiguration(
        "libFLAC 1.5.0 integer decoder",
        requireNotNull(integerFlacOutputFormat()),
        frameBytes,
        metadata.maxBlockSizeSamples * frameBytes,
        metadata.maxFrameSize.takeIf { it > 0 } ?: 65536,
    ) {
        object : IntegerFrameDecoder {
            private val decoder =
                FlacFrameDecoder(
                    header,
                    metadata.sampleRate,
                    metadata.bitsPerSample,
                    metadata.channels,
                    metadata.maxBlockSizeSamples,
                )

            override fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int =
                decoder.decode(input, output, reset)

            override fun close() = decoder.close()
        }
    }
}
