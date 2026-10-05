package com.pxr.cymatic.audio.usb

import android.hardware.usb.UsbDeviceConnection
import org.json.JSONArray
import org.json.JSONObject

internal abstract class UsbSamplingClock(
    protected val connection: UsbDeviceConnection,
    protected val report: JSONObject,
) {
    protected var previous: Int? = null
    protected var attempted = false

    abstract fun configure(rate: Int)

    protected abstract fun read(): Int

    protected abstract fun write(rate: Int)

    fun restore(): Boolean {
        if (!attempted) return true
        val rate = checkNotNull(previous)
        write(rate)
        return read() == rate
    }

    protected fun setAndConfirm(rate: Int) {
        previous = read()
        report.put("previousClockRateHz", previous)
        if (previous != rate) {
            attempted = true
            write(rate)
        }
        check(read() == rate) { "DAC did not confirm the source sample rate" }
        report.put("confirmedClockRateHz", rate).put("sampleRateReadbackConfirmed", true)
    }

    class Uac2(
        connection: UsbDeviceConnection,
        report: JSONObject,
        private val source: UsbClockSource,
    ) : UsbSamplingClock(connection, report) {
        override fun configure(rate: Int) {
            require(source.frequencyReadable) { "USB clock frequency cannot be read" }
            val ranges = readRanges(connection, source)
            report.put(
                "clockRanges",
                JSONArray(
                    ranges.map { (minimum, maximum, resolution) ->
                        JSONObject()
                            .put("minimumHz", minimum)
                            .put("maximumHz", maximum)
                            .put("resolutionHz", resolution)
                    }
                ),
            )
            require(
                ranges.any { (minimum, maximum, resolution) ->
                    rate.toLong() in minimum..maximum &&
                        (resolution == 0L || (rate - minimum) % resolution == 0L)
                }
            ) {
                "DAC does not advertise the source sample rate"
            }
            setAndConfirm(rate)
            if (source.validityReadable) {
                report
                    .put("clockValidityWaitMs", awaitClockValidity(connection, source))
                    .put("clockValidityConfirmed", true)
                check(read() == rate) { "DAC clock rate changed while settling" }
            }
        }

        override fun read(): Int = readRate(connection, source)

        override fun write(rate: Int) {
            require(source.frequencyWritable) { "USB clock frequency cannot be changed" }
            writeRate(connection, source, rate)
        }
    }

    class Uac1(
        connection: UsbDeviceConnection,
        report: JSONObject,
        private val format: UsbPcmFormat,
        private val endpoints: UsbStreamingEndpoints,
    ) : UsbSamplingClock(connection, report) {
        override fun configure(rate: Int) {
            require(format.advertisesRate(rate)) {
                "UAC1 format does not advertise the source sample rate"
            }
            report.put("sampleRateSource", "UAC1_FORMAT_DESCRIPTOR")
            if (endpoints.samplingFrequencyControl) {
                setAndConfirm(rate)
            } else {
                require(
                    format.continuousRates == null && format.sampleRates.distinct() == listOf(rate)
                ) {
                    "UAC1 endpoint has multiple rates but no sampling frequency control"
                }
                report.put("selectedFixedRateHz", rate).put("sampleRateReadbackConfirmed", false)
            }
        }

        override fun read(): Int {
            val bytes = ByteArray(3)
            check(
                connection.controlTransfer(
                    0xa2,
                    0x81,
                    0x0100,
                    endpoints.output.address,
                    bytes,
                    3,
                    500,
                ) == 3
            ) {
                "UAC1 endpoint frequency GET_CUR failed"
            }
            val rate = bytes.le24(0)
            require(rate in 8000..768000) { "UAC1 endpoint returned an invalid frequency" }
            return rate
        }

        override fun write(rate: Int) {
            val bytes = ByteArray(3) { (rate ushr (it * 8)).toByte() }
            check(
                connection.controlTransfer(
                    0x22,
                    1,
                    0x0100,
                    endpoints.output.address,
                    bytes,
                    3,
                    500,
                ) == 3
            ) {
                "UAC1 endpoint frequency SET_CUR failed"
            }
        }
    }
}

internal fun UsbPcmFormat.advertisesRate(rate: Int): Boolean =
    rate in sampleRates || continuousRates?.let { rate in it.first..it.second } == true

private fun clockIndex(clock: UsbClockSource) = (clock.id shl 8) or clock.controlInterface

private fun awaitClockValidity(connection: UsbDeviceConnection, clock: UsbClockSource): Long {
    val started = System.nanoTime()
    val deadline = started + 500_000_000L
    val valid = ByteArray(1)
    do {
        val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceIn(1, 100)
        check(
            connection.controlTransfer(0xa1, 1, 0x0200, clockIndex(clock), valid, 1, remainingMs) ==
                1
        ) {
            "USB clock validity request failed"
        }
        when (valid.u8(0)) {
            1 -> return (System.nanoTime() - started) / 1_000_000
            0 -> Thread.sleep(10)
            else -> error("USB clock returned an invalid validity value")
        }
    } while (System.nanoTime() < deadline)
    error("USB clock did not become valid within 500 ms")
}

private fun readRate(connection: UsbDeviceConnection, clock: UsbClockSource): Int {
    val bytes = ByteArray(4)
    check(
        connection.controlTransfer(
            0xa1,
            1,
            0x0100,
            clockIndex(clock),
            bytes,
            4,
            500,
        ) == 4
    ) {
        "Clock GET_CUR failed"
    }
    val rate = bytes.le32(0)
    require(rate in 8000..768000) { "Clock returned invalid frequency: $rate" }
    return rate.toInt()
}

private fun writeRate(connection: UsbDeviceConnection, clock: UsbClockSource, rate: Int) {
    val bytes = ByteArray(4) { (rate ushr (it * 8)).toByte() }
    check(
        connection.controlTransfer(
            0x21,
            1,
            0x0100,
            clockIndex(clock),
            bytes,
            4,
            500,
        ) == 4
    ) {
        "Clock SET_CUR failed"
    }
}

internal fun readRanges(
    connection: UsbDeviceConnection,
    clock: UsbClockSource,
): List<Triple<Long, Long, Long>> {
    val header = ByteArray(2)
    check(
        connection.controlTransfer(
            0xa1,
            2,
            0x0100,
            clockIndex(clock),
            header,
            2,
            500,
        ) == 2
    ) {
        "Claimed clock GET_RANGE header failed"
    }
    val count = header.le16(0)
    require(count in 1..32) { "Clock returned invalid range count: $count" }
    val bytes = ByteArray(2 + count * 12)
    check(
        connection.controlTransfer(
            0xa1,
            2,
            0x0100,
            clockIndex(clock),
            bytes,
            bytes.size,
            500,
        ) == bytes.size && bytes.le16(0) == count
    ) {
        "Clock GET_RANGE response was incomplete or changed"
    }
    return List(count) { index ->
        val offset = 2 + index * 12
        val minimum = bytes.le32(offset)
        val maximum = bytes.le32(offset + 4)
        val resolution = bytes.le32(offset + 8)
        require(minimum in 8000..768000 && maximum in minimum..768000 && resolution <= 768000) {
            "Malformed clock range"
        }
        Triple(minimum, maximum, resolution)
    }
}
