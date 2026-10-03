package com.pxr.cymatic.audio.usb

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@UnstableApi
internal class UsbPcmTrimmer(private val format: Format) {
    private val frameBytes = requireNotNull(format.usbPcmLayout()).frameBytes
    private val delay = format.encoderDelay.coerceAtLeast(0)
    private val padding = format.encoderPadding.coerceAtLeast(0)
    private val tail: ByteArray
    private var tailSize = 0
    private var tailTimeUs = 0L
    private var startBytes = 0
    private var started = false
    private var storage = ByteBuffer.allocateDirect(0)
    var output: ByteBuffer? = null
        private set

    var outputTimeUs = 0L
        private set

    init {
        require(delay <= MAX_TRIM_FRAMES && padding <= MAX_TRIM_FRAMES) {
            "Invalid encoder delay or padding"
        }
        tail = ByteArray(padding * frameBytes)
    }

    fun queue(input: ByteBuffer, timeUs: Long) {
        check(output?.hasRemaining() != true)
        require(input.remaining() % frameBytes == 0) { "Incomplete source PCM frame" }
        if (delay == 0 && padding == 0) {
            output = input
            outputTimeUs = timeUs
            return
        }
        require(input.remaining() <= MAX_BUFFER_BYTES) {
            "Decoded PCM buffer exceeds the input limit"
        }
        if (!started) {
            val delayUs = delay.toLong() * 1_000_000 / format.sampleRate
            val elapsed =
                if (timeUs >= delayUs) delay
                else (timeUs.coerceAtLeast(0) * format.sampleRate / 1_000_000).toInt()
            startBytes = (delay - elapsed) * frameBytes
            started = true
        }
        val skipped = minOf(input.remaining(), startBytes)
        startBytes -= skipped
        input.position(input.position() + skipped)
        val inputTimeUs = timeUs + skipped.toLong() / frameBytes * 1_000_000 / format.sampleRate
        if (tailSize == 0) tailTimeUs = inputTimeUs
        val outputBytes = (tailSize + input.remaining() - tail.size).coerceAtLeast(0)
        if (storage.capacity() < outputBytes) storage = ByteBuffer.allocateDirect(outputBytes)
        storage.clear()
        storage.limit(outputBytes)
        val fromTail = minOf(outputBytes, tailSize)
        storage.put(tail, 0, fromTail)
        tail.copyInto(tail, 0, fromTail, tailSize)
        tailSize -= fromTail
        val fromInput = outputBytes - fromTail
        val limit = input.limit()
        input.limit(input.position() + fromInput)
        storage.put(input)
        input.limit(limit)
        val retained = input.remaining()
        input.get(tail, tailSize, retained)
        tailSize += retained
        storage.flip()
        outputTimeUs = tailTimeUs
        tailTimeUs += outputBytes.toLong() / frameBytes * 1_000_000 / format.sampleRate
        output = storage
    }

    fun discardTail() {
        tailSize = 0
    }

    private companion object {
        const val MAX_TRIM_FRAMES = 65536
        const val MAX_BUFFER_BYTES = 2_097_152
    }
}
