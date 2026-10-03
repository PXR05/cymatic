package com.pxr.cymatic.alac

import java.nio.ByteBuffer

class AlacFrameDecoder(config: ByteArray) : AutoCloseable {
    private var handle =
        create(config).also { check(it != 0L) { "ALAC decoder could not be created" } }

    @Synchronized
    fun decode(input: ByteBuffer, output: ByteBuffer): Int {
        check(handle != 0L)
        require(input.isDirect && output.isDirect && output.position() == 0)
        require(input.remaining() in 1..MAX_PACKET_BYTES)
        return decodeFrame(
            handle,
            input,
            input.position(),
            input.remaining(),
            output,
            output.remaining(),
        )
    }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        destroy(handle)
        handle = 0L
    }

    private external fun create(config: ByteArray): Long

    private external fun decodeFrame(
        handle: Long,
        input: ByteBuffer,
        offset: Int,
        length: Int,
        output: ByteBuffer,
        capacity: Int,
    ): Int

    private external fun destroy(handle: Long)

    companion object {
        const val MAX_PACKET_BYTES = 1024 * 1024
        const val MAX_FRAME_SAMPLES = 16384

        init {
            System.loadLibrary("cymatic_alac")
        }
    }
}
