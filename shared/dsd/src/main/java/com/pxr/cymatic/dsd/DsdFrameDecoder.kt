package com.pxr.cymatic.dsd

import java.nio.ByteBuffer

class DsdFrameDecoder(
    rate: Int,
    outputRate: Int,
    channels: Int,
    lsbFirst: Boolean,
    dop: Boolean,
) : AutoCloseable {
    private var handle =
        create(rate, outputRate, channels, lsbFirst, dop).also {
            check(it != 0L) { "DSD decoder could not be created" }
        }

    @Synchronized
    fun decode(input: ByteBuffer, output: ByteBuffer, reset: Boolean): Int {
        check(handle != 0L)
        require(input.isDirect && output.isDirect && output.position() == 0)
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
        rate: Int,
        outputRate: Int,
        channels: Int,
        lsbFirst: Boolean,
        dop: Boolean,
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
        const val BLOCK_BYTES = 4096
        const val MAX_PACKET_BYTES = BLOCK_BYTES * 2 + 4

        init {
            System.loadLibrary("cymatic_dsd")
        }
    }
}
