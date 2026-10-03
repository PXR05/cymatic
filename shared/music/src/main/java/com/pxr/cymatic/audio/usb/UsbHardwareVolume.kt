package com.pxr.cymatic.audio.usb

import android.hardware.usb.UsbDeviceConnection
import java.io.Closeable
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal class UsbHardwareVolume(
    private val connection: UsbDeviceConnection,
    private val feature: UsbFeatureUnit,
    private val onFailure: (Throwable) -> Unit,
) : Closeable {
    private data class Range(val minimum: Int, val maximum: Int, val resolution: Int) {
        fun contains(value: Int) =
            value in minimum..maximum && (resolution == 0 || (value - minimum) % resolution == 0)

        fun floor(value: Int): Int? {
            if (value < minimum) return null
            val capped = minOf(value, maximum)
            return if (resolution == 0) minimum
            else minimum + (capped - minimum) / resolution * resolution
        }

        fun json() =
            JSONObject()
                .put("minimumDb", minimum / 256f)
                .put("maximumDb", maximum / 256f)
                .put("resolutionDb", resolution / 256f)
    }

    private data class Channel(
        val number: Int,
        val previous: Int,
        val ranges: List<Range>,
        var current: Int,
    )

    private data class Mute(val number: Int, val previous: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channels = mutableListOf<Channel>()
    private val mutes = mutableListOf<Mute>()
    private var minimum = 0
    private var maximum = 0
    private var closed = false
    private var changes = 0
    private var appliedPercent = -1
    val report = JSONObject().put("featureUnit", feature.json())

    @Volatile
    var failure: Throwable? = null
        private set

    @Synchronized
    fun initialize() {
        val selected =
            checkNotNull(feature.volumeChannels) { "DAC has no writable channel volume control" }
        selected.forEach { number ->
            val previous = read(2, number, 2)
            val ranges = readRanges(number)
            require(previous == SILENCE || ranges.any { it.contains(previous) }) {
                "DAC returned an invalid current volume"
            }
            channels += Channel(number, previous, ranges, previous)
        }
        maximum = minOf(0, channels.minOf { it.ranges.maxOf { range -> range.maximum } })
        minimum =
            maxOf(maximum - 60 * 256, channels.maxOf { it.ranges.minOf { range -> range.minimum } })
        require(minimum < maximum && minimum < 0) {
            "DAC does not provide a usable attenuation range"
        }
        feature.controls.indices.forEach { number ->
            val access = feature.access(number, 1)
            if (access == 1 || access == 3) {
                val current = read(1, number, 1)
                require(current in 0..1) { "DAC returned an invalid mute state" }
                if (access == 3) mutes += Mute(number, current)
                else check(current == 0) { "DAC is muted by a read-only control" }
            }
        }
        report.put(
            "channels",
            JSONArray(
                channels.map { channel ->
                    JSONObject()
                        .put("number", channel.number)
                        .put("previousRawVolume", channel.previous)
                        .put("ranges", JSONArray(channel.ranges.map { it.json() }))
                }
            ),
        )
        apply(UsbVolumeState.requestedPercent.value)
        scope.launch {
            UsbVolumeState.requestedPercent.collect { percent ->
                try {
                    apply(percent)
                } catch (e: Exception) {
                    synchronized(this@UsbHardwareVolume) {
                        if (!closed) {
                            failure = e
                            report.put("error", e.message ?: e.javaClass.simpleName)
                            UsbVolumeState.unavailable("Hardware volume failed")
                            onFailure(e)
                            scope.cancel()
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    private fun apply(percent: Int) {
        if (closed || percent == appliedPercent) return
        val muted = percent == 0
        if (muted) {
            val selected =
                checkNotNull(feature.muteChannels) {
                    "DAC has no writable channel mute control; playback stopped"
                }
            selected.forEach { writeAndConfirm(1, it, 1, 1) }
        }
        val requested = minimum + ((maximum - minimum).toLong() * percent / 100).toInt()
        channels.forEach { channel ->
            val target =
                if (muted) channel.ranges.minOf { it.minimum }
                else
                    channel.ranges
                        .mapNotNull {
                            it.floor(requested)
                        }
                        .maxOrNull() ?: error("Requested DAC volume is outside its ranges")
            writeAndConfirm(2, channel.number, target, 2)
            channel.current = target
        }
        if (!muted) mutes.forEach { writeAndConfirm(1, it.number, 0, 1) }
        appliedPercent = percent
        changes++
        val loudest = channels.maxOf { it.current }
        val confirmedPercent =
            if (muted) 0
            else
                ((loudest - minimum).toFloat() * 100 / (maximum - minimum))
                    .roundToInt()
                    .coerceIn(0, 100)
        report
            .put("requestedPercent", percent)
            .put("confirmedPercent", confirmedPercent)
            .put("muted", muted)
            .put("muteMethod", "Feature unit MUTE control")
            .put("confirmedDb", loudest / 256f)
            .put("volumeChanges", changes)
        UsbVolumeState.confirm(confirmedPercent, if (muted) null else loudest / 256f, muted)
    }

    private fun readRanges(channel: Int): List<Range> {
        if (feature.protocol == 0) {
            fun value(request: Int) =
                transfer(true, request, 2, channel, ByteArray(2)).le16(0).toShort().toInt()
            return listOf(validateRange(value(0x82), value(0x83), value(0x84)))
        }
        val header = transfer(true, 2, 2, channel, ByteArray(2))
        val count = header.le16(0)
        require(count in 1..32) { "DAC returned an invalid volume range count" }
        val bytes = transfer(true, 2, 2, channel, ByteArray(2 + count * 6))
        check(bytes.le16(0) == count) { "DAC volume ranges changed during inspection" }
        return List(count) { index ->
            val offset = 2 + index * 6
            val minimum = bytes.le16(offset).toShort().toInt()
            val maximum = bytes.le16(offset + 2).toShort().toInt()
            val resolution = bytes.le16(offset + 4)
            validateRange(minimum, maximum, resolution)
        }
    }

    private fun validateRange(minimum: Int, maximum: Int, resolution: Int): Range {
        require(
            minimum > SILENCE &&
                    maximum >= minimum &&
                    resolution in 0..32767 &&
                    (resolution > 0 || minimum == maximum)
        ) {
            "DAC returned a malformed volume range"
        }
        return Range(minimum, maximum, resolution)
    }

    private fun read(selector: Int, channel: Int, size: Int): Int {
        val bytes =
            transfer(
                true,
                if (feature.protocol == 0) 0x81 else 1,
                selector,
                channel,
                ByteArray(size),
            )
        return if (size == 1) bytes.u8(0) else bytes.le16(0).toShort().toInt()
    }

    private fun writeAndConfirm(selector: Int, channel: Int, value: Int, size: Int) {
        transfer(false, 1, selector, channel, ByteArray(size) { (value ushr (it * 8)).toByte() })
        check(
            read(
                selector,
                channel,
                size,
            ) == value
        ) {
            "DAC did not confirm hardware ${if (selector == 2) "volume" else "mute"}"
        }
    }

    private fun transfer(
        input: Boolean,
        request: Int,
        selector: Int,
        channel: Int,
        bytes: ByteArray,
    ): ByteArray {
        check(
            connection.controlTransfer(
                if (input) 0xa1 else 0x21,
                request,
                (selector shl 8) or channel,
                (feature.id shl 8) or feature.controlInterface,
                bytes,
                bytes.size,
                250,
            ) == bytes.size
        ) {
            "DAC hardware volume request failed (control $selector, channel $channel)"
        }
        return bytes
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
    }

    @Synchronized
    fun restore(): List<String> {
        close()
        val warnings = mutableListOf<String>()
        val previousSilence = channels.any { it.previous == SILENCE }
        channels.forEach { channel ->
            val previous =
                if (channel.previous == SILENCE) channel.ranges.minOf { it.minimum }
                else channel.previous
            runCatching { writeAndConfirm(2, channel.number, previous, 2) }
                .onFailure {
                    warnings += "Could not restore DAC volume on channel ${channel.number}"
                }
        }
        mutes.forEach { mute ->
            val previous =
                if (previousSilence && mute.number in feature.muteChannels.orEmpty()) 1
                else mute.previous
            runCatching { writeAndConfirm(1, mute.number, previous, 1) }
                .onFailure { warnings += "Could not restore DAC mute on channel ${mute.number}" }
        }
        report
            .put("legacySilenceVolumeNormalized", previousSilence)
            .put("restorationWarnings", JSONArray(warnings))
        return warnings
    }

    private companion object {
        const val SILENCE = -32768
    }
}
