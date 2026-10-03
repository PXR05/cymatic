package com.pxr.cymatic.usb

import android.hardware.usb.UsbDeviceConnection
import java.io.Closeable

data class UsbTransferResult(
    val error: Int,
    val packets: Long,
    val frames: Long,
    val bytes: Long,
    val packetErrors: Long,
    val shortPackets: Long,
    val transfers: Long,
)

class UsbIsochronousConnection(private val connection: UsbDeviceConnection) : Closeable {
    private val cancellationLock = Any()
    private var handle =
        create(connection.fileDescriptor).also {
            check(it != 0L) { "Native USB allocation failed" }
        }

    @Synchronized
    fun speed(): Int {
        check(handle != 0L)
        return getSpeed(handle)
    }

    @Synchronized
    fun writePcm(
        endpoint: Int,
        maxPacketBytes: Int,
        interval: Int,
        sampleRate: Int,
        frameBytes: Int,
        pcm: ByteArray,
    ): UsbTransferResult {
        check(handle != 0L)
        require(endpoint in 1..15 && interval in 1..16 && sampleRate in 8000..384000)
        require(frameBytes in 1..32 && pcm.isNotEmpty() && pcm.size % frameBytes == 0)
        require(pcm.size.toLong() <= sampleRate.toLong() * frameBytes * 2)
        val result =
            transfer(handle, endpoint, maxPacketBytes, interval, sampleRate, frameBytes, pcm)
        return UsbTransferResult(
            result[0].toInt(),
            result[1],
            result[2],
            result[3],
            result[4],
            result[5],
            result[6],
        )
    }

    @Synchronized
    fun reconnectInterface(number: Int): Int {
        check(handle != 0L)
        require(number in 0..255)
        return reconnect(handle, number)
    }

    @Synchronized
    fun interfaceDriver(number: Int): String? {
        check(handle != 0L)
        require(number in 0..255)
        return getDriver(handle, number)
    }

    fun cancel() =
        synchronized(cancellationLock) {
            if (handle != 0L) cancelTransfer(handle)
        }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        synchronized(cancellationLock) {
            connection.close()
            destroy(handle)
            handle = 0L
        }
    }

    private external fun create(fd: Int): Long

    private external fun getSpeed(handle: Long): Int

    private external fun transfer(
        handle: Long,
        endpoint: Int,
        maxPacketBytes: Int,
        interval: Int,
        sampleRate: Int,
        frameBytes: Int,
        pcm: ByteArray,
    ): LongArray

    private external fun reconnect(handle: Long, number: Int): Int

    private external fun getDriver(handle: Long, number: Int): String?

    private external fun destroy(handle: Long)

    private external fun cancelTransfer(handle: Long)

    companion object {
        init {
            System.loadLibrary("cymatic_usb")
        }
    }
}
