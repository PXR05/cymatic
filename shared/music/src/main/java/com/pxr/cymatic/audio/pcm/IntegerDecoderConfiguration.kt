package com.pxr.cymatic.audio.pcm

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.io.Closeable
import java.nio.ByteBuffer

internal interface IntegerFrameDecoder : Closeable {
    fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int
}

@UnstableApi
internal data class IntegerDecoderConfiguration(
    val name: String,
    val outputFormat: Format,
    val frameBytes: Int,
    val maxOutputBytes: Int,
    val initialInputBytes: Int,
    val open: () -> IntegerFrameDecoder,
)
