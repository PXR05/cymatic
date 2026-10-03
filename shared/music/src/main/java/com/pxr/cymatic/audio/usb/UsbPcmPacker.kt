package com.pxr.cymatic.audio.usb

import com.pxr.cymatic.usb.UsbPcmStream
import java.nio.ByteBuffer

internal class UsbPcmPacker(private val source: UsbPcmLayout, private val target: UsbPcmLayout) {
    private val output = ByteBuffer.allocateDirect(16384)
    private val paddingMask = (1L shl (source.containerBytes * 8 - source.validBits)) - 1
    private val unchanged = source == target && paddingMask == 0L

    init {
        require(source.channels == target.channels && target.validBits >= source.validBits)
    }

    fun write(input: ByteBuffer, stream: UsbPcmStream): Int {
        require(input.remaining() % source.frameBytes == 0) { "Incomplete source PCM frame" }
        if (unchanged) return stream.write(input)
        output.clear()
        val frames =
            minOf(input.remaining() / source.frameBytes, output.capacity() / target.frameBytes)
        var offset = input.position()
        repeat(frames * source.channels) {
            var sample = 0L
            repeat(source.containerBytes) { byte ->
                sample = sample or ((input.get(offset++).toLong() and 0xff) shl (byte * 8))
            }
            require(sample and paddingMask == 0L) { "Source PCM has nonzero padding bits" }
            val normalized = sample shl (32 - source.containerBytes * 8)
            val packed = normalized ushr (32 - target.containerBytes * 8)
            repeat(target.containerBytes) { byte -> output.put((packed ushr (byte * 8)).toByte()) }
        }
        output.flip()
        val written = stream.write(output)
        return if (written < 0) written else written / target.frameBytes * source.frameBytes
    }
}
