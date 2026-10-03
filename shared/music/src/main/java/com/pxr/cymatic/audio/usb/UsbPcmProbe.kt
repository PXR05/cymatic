package com.pxr.cymatic.audio.usb

import com.pxr.cymatic.usb.UsbPcmStream
import com.pxr.cymatic.usb.UsbTransferResult
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal fun UsbPcmStream.writeSilentProbe(
    rate: Int,
    frameBytes: Int,
    pcm: ByteArray,
    cancelled: () -> Boolean,
): UsbTransferResult {
    require(
        frameBytes in 2..8 &&
                pcm.isNotEmpty() &&
                pcm.size % frameBytes == 0 &&
                pcm.size.toLong() <= rate.toLong() * frameBytes * 2
    )
    val buffer =
        ByteBuffer.allocateDirect(pcm.size).apply {
            put(pcm)
            flip()
        }
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
    var ended = false
    var error = 0
    play(true)
    try {
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                error = -125
                break
            }
            if (System.nanoTime() >= deadline) {
                error = -110
                break
            }
            if (buffer.hasRemaining()) {
                val consumed = write(buffer)
                if (consumed < 0) {
                    error = consumed
                    break
                }
                buffer.position(buffer.position() + consumed)
            }
            if (!buffer.hasRemaining() && !ended) {
                end()
                ended = true
            }
            val stats = statistics()
            if (stats[0] != 0L) {
                error = stats[0].toInt()
                break
            }
            if (ended && stats[2] == 0L) break
            Thread.sleep(2)
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        error = -125
    } finally {
        stop()
    }
    val stats = statistics()
    return UsbTransferResult(error, stats[15], stats[1], stats[6], stats[3], stats[4], stats[16])
}
