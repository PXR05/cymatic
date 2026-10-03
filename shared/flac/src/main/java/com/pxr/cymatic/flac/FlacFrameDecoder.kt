package com.pxr.cymatic.flac

import java.nio.ByteBuffer

class FlacFrameDecoder(
    metadata: ByteArray,
    rate: Int,
    bits: Int,
    channels: Int,
    maxBlockSize: Int,
) : AutoCloseable {
    private var handle =
        create(metadata, rate, bits, channels, maxBlockSize).also {
            check(it != 0L) { "FLAC decoder could not be created" }
        }

    @Synchronized
    fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int {
        check(handle != 0L)
        require(input.isDirect && output.isDirect)
        require(output.position() == 0)
        require(input.remaining() in 1..MAX_FRAME_BYTES)
        return decodeFrame(
            handle,
            input,
            input.position(),
            input.remaining(),
            output,
            output.remaining(),
            reset,
        )
    }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        destroy(handle)
        handle = 0L
    }

    private external fun create(
        metadata: ByteArray,
        rate: Int,
        bits: Int,
        channels: Int,
        maxBlockSize: Int,
    ): Long

    private external fun decodeFrame(
        handle: Long,
        input: ByteBuffer,
        offset: Int,
        length: Int,
        output: ByteBuffer,
        capacity: Int,
        reset: Boolean,
    ): Int

    private external fun destroy(handle: Long)

    companion object {
        const val MAX_FRAME_BYTES = 1024 * 1024

        init {
            System.loadLibrary("cymatic_flac")
        }
    }
}
