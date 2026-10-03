package com.pxr.cymatic.audio.alac

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.alac.AlacFrameDecoder
import com.pxr.cymatic.audio.pcm.IntegerDecoderConfiguration
import com.pxr.cymatic.audio.pcm.IntegerFrameDecoder
import java.nio.ByteBuffer

@UnstableApi
internal fun Format.integerAlacDecoderConfiguration(): IntegerDecoderConfiguration {
    val metadata = requireNotNull(integerAlacMetadata()) { "Unsupported integer ALAC source" }
    val frameBytes = metadata.channels * ((metadata.bits + 7) / 8)
    return IntegerDecoderConfiguration(
        "Apple ALAC integer decoder",
        requireNotNull(integerAlacOutputFormat()),
        frameBytes,
        metadata.frameSamples * frameBytes,
        minOf(metadata.maxPacketBytes, 65536),
    ) {
        object : IntegerFrameDecoder {
            private val decoder = AlacFrameDecoder(metadata.config)

            override fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int =
                decoder.decode(input, output)

            override fun close() = decoder.close()
        }
    }
}
