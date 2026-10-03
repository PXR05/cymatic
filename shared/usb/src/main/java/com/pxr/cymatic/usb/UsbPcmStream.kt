package com.pxr.cymatic.usb

import java.nio.ByteBuffer

class UsbPcmStream(
    fd: Int,
    endpoint: Int,
    capacity: Int,
    interval: Int,
    rate: Int,
    frameBytes: Int,
    feedbackEndpoint: Int = 0,
    feedbackCapacity: Int = 0,
    feedbackInterval: Int = 0,
    feedbackRefreshMs: Int = 0,
    primingFrames: Int = 0,
) {
    private var handle =
        create(
            fd,
            endpoint,
            capacity,
            interval,
            rate,
            frameBytes,
            feedbackEndpoint,
            feedbackCapacity,
            feedbackInterval,
            feedbackRefreshMs,
            primingFrames,
        )
            .also { check(it != 0L) { "Continuous USB transport could not be created" } }

    @Synchronized
    fun write(buffer: ByteBuffer): Int {
        check(handle != 0L)
        require(buffer.isDirect)
        return enqueue(handle, buffer, buffer.position(), buffer.remaining())
    }

    @Synchronized
    fun play(value: Boolean) {
        if (handle != 0L) setPlaying(handle, value)
    }

    @Synchronized
    fun end() {
        if (handle != 0L) endInput(handle)
    }

    @Synchronized
    fun statistics(): LongArray = if (handle == 0L) LongArray(22) else stats(handle)

    @Synchronized
    fun stop() {
        if (handle != 0L) stopWorker(handle)
    }

    @Synchronized
    fun releaseAfterConnectionClosed() {
        if (handle == 0L) return
        destroy(handle)
        handle = 0L
    }

    private external fun create(
        fd: Int,
        endpoint: Int,
        capacity: Int,
        interval: Int,
        rate: Int,
        frameBytes: Int,
        feedbackEndpoint: Int,
        feedbackCapacity: Int,
        feedbackInterval: Int,
        feedbackRefreshMs: Int,
        primingFrames: Int,
    ): Long

    private external fun enqueue(handle: Long, buffer: ByteBuffer, offset: Int, length: Int): Int

    private external fun setPlaying(handle: Long, value: Boolean)

    private external fun endInput(handle: Long)

    private external fun stats(handle: Long): LongArray

    private external fun stopWorker(handle: Long)

    private external fun destroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("cymatic_usb")
        }
    }
}
