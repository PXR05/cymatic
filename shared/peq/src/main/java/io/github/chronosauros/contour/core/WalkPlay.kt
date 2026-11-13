// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import io.github.chronosauros.contour.core.devices.FlatBand
import io.github.chronosauros.contour.core.devices.GeneratedDevices
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object WalkPlay {

    const val VENDOR_ID: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_VENDOR_ID
    const val PRODUCT_ID: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_PRODUCT_ID
    const val REPORT_ID: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_REPORT_ID
    const val REPORT_SIZE: Int = 64
    const val READ: Int = 0x80
    const val WRITE: Int = 0x01
    const val END: Int = 0x00
    const val CMD_GLOBAL_GAIN: Int = 0x03
    const val CMD_PEQ: Int = 0x09
    const val CMD_VERSION: Int = 0x0C
    const val BANDS: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_BANDS
    const val DESIGN_FS: Double = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_DESIGN_RATE_HZ

    const val TYPE_LSQ: Int = 1
    const val TYPE_PK: Int = 2
    const val TYPE_HSQ: Int = 3
    const val TYPE_LP: Int = 4
    const val TYPE_HP: Int = 5

    fun jsRound(x: Double): Double = floor(x + 0.5)

    fun toInt32(x: Double): Int = x.toLong().toInt()

    fun typeCode(type: FilterType): Int =
        when (type) {
            FilterType.LOW_SHELF -> TYPE_LSQ
            FilterType.PEAK -> TYPE_PK
            FilterType.HIGH_SHELF -> TYPE_HSQ
            FilterType.LOW_PASS -> TYPE_LP
            FilterType.HIGH_PASS -> TYPE_HP

            FilterType.BAND_PASS,
            FilterType.ALL_PASS ->
                throw IllegalArgumentException("${type.name} is not supported by WalkPlay DACs")
        }

    fun filterType(code: Int): FilterType =
        when (code) {
            TYPE_LSQ -> FilterType.LOW_SHELF
            TYPE_HSQ -> FilterType.HIGH_SHELF
            TYPE_LP -> FilterType.LOW_PASS
            TYPE_HP -> FilterType.HIGH_PASS
            else -> FilterType.PEAK
        }

    fun report(vararg payload: Int): ByteArray {
        require(payload.size <= REPORT_SIZE - 1)
        val buf = ByteArray(REPORT_SIZE)
        buf[0] = REPORT_ID.toByte()
        payload.forEachIndexed { i, v -> buf[i + 1] = v.toByte() }
        return buf
    }

    fun versionRequest(): ByteArray = report(READ, CMD_VERSION, END)

    fun slotRequest(): ByteArray = report(READ, CMD_PEQ, END)

    fun bandRequest(index: Int): ByteArray = report(READ, CMD_PEQ, 0x00, 0x00, index, END)

    fun preampRequest(): ByteArray = report(READ, CMD_GLOBAL_GAIN, END)

    private fun u8(buf: ByteArray, i: Int): Int = buf[i].toInt() and 0xFF

    private fun u16(buf: ByteArray, i: Int): Int = u8(buf, i) or (u8(buf, i + 1) shl 8)

    fun isReply(buf: ByteArray, cmd: Int): Boolean =
        buf.size >= 3 && u8(buf, 0) == REPORT_ID && u8(buf, 2) == cmd

    fun isBandReply(buf: ByteArray, index: Int): Boolean =
        isReply(buf, CMD_PEQ) && buf.size >= 37 && u8(buf, 5) == index

    fun parseVersion(buf: ByteArray): String {
        require(buf.size >= 7) { "version reply too short: ${buf.size}" }
        return String(buf.copyOfRange(4, 7), Charsets.US_ASCII).trimEnd('\u0000')
    }

    fun parseSlot(buf: ByteArray): Int {
        require(buf.size >= 37) { "slot reply too short: ${buf.size}" }
        return u8(buf, 36)
    }

    fun parsePreamp(buf: ByteArray): Int {
        require(buf.size >= 6) { "preamp reply too short: ${buf.size}" }
        return buf[5].toInt()
    }

    data class Registers(
        val index: Int,
        val freq: Int,
        val q256: Int,
        val gain256: Int,
        val typeCode: Int,
    )

    data class DeviceBand(
        val registers: Registers,
        val slotByte: Int,
        val biquad: ByteArray,
        val enabled: Boolean,
        val type: FilterType,
        val freqHz: Double,
        val gainDb: Double,
        val q: Double,
    )

    fun parseBand(buf: ByteArray): DeviceBand {
        require(buf.size >= 37) { "band reply too short: ${buf.size}" }
        val index = u8(buf, 5)
        val freqRaw = u16(buf, 28)
        val qRaw = u16(buf, 30)
        var gainRaw = u16(buf, 32)
        if (gainRaw > 32767) gainRaw -= 65536
        val typeCode = u8(buf, 34)
        val type = filterType(typeCode)
        val gain = jsRound(gainRaw / 256.0 * 100) / 100
        val qStored = jsRound(qRaw / 256.0 * 100) / 100
        val freq = freqRaw.toDouble()
        val q = qStored
        val uninit = freqRaw == 0xFFFF && qRaw == 0xFFFF && gainRaw == -1
        val disabled = uninit || freqRaw == 0 || freqRaw == 0xFFFF || q == 0.0
        return DeviceBand(
            registers = Registers(index, freqRaw, qRaw, gainRaw, typeCode),
            slotByte = u8(buf, 36),
            biquad = buf.copyOfRange(8, 28),
            enabled = !disabled,
            type = type,
            freqHz = freq,
            gainDb = gain,
            q = q,
        )
    }

    data class BandWrite(
        val index: Int,
        val freq: Double,
        val gainDb: Double,
        val q: Double,
        val typeCode: Int,
        val qScale: Double = 1.0,
        val rateHz: Double = DESIGN_FS,
        val wrapQ30: Boolean = false,
    ) {

        internal val qRegister: Int
            get() = toInt32(jsRound(q * qScale * 256)) and 0xFFFF

        fun registers(): Registers {
            var g = toInt32(jsRound(gainDb * 256)) and 0xFFFF
            if (g > 32767) g -= 65536
            return Registers(index, toInt32(freq) and 0xFFFF, qRegister, g, typeCode)
        }
    }

    fun factoryFlat(index: Int): BandWrite =
        flatWrite(index, GeneratedDevices.CRINEAR_PROTOCOL_MICRO.factoryFlat[index])

    internal fun flatWrite(index: Int, flat: FlatBand): BandWrite =
        BandWrite(index, flat.freqHz.toDouble(), 0.0, flat.q, TYPE_PK)

    fun bandWrite(index: Int, band: Band): BandWrite =
        BandWrite(
            index,
            band.freqHz,
            if (band.type.isPass) 0.0 else band.gainDb,
            band.q,
            typeCode(band.type),
        )

    fun bandWrite(index: Int, band: Band, caps: DeviceCapabilities): BandWrite =
        bandWrite(index, band)
            .copy(
                qScale = caps.qScale(band.type),
                rateHz = caps.coefficientRateHz,
                wrapQ30 = caps.wrapQ30,
            )

    fun intendedQ(q256: Int, type: FilterType, caps: DeviceCapabilities): Double =
        q256 / 256.0 / caps.qScale(type)

    class Q30Overflow(message: String) : IllegalArgumentException(message)

    private val COEFFICIENT_NAMES = arrayOf("b0", "b1", "b2", "-a1", "-a2")

    private fun q30Violation(scaled: DoubleArray): String? {
        scaled.forEachIndexed { i, v ->
            val k = if (i < 3) jsRound(v) else -jsRound(-v)

            if (!(k >= Int.MIN_VALUE.toDouble() && k <= Int.MAX_VALUE.toDouble())) {
                return "Coefficient ${COEFFICIENT_NAMES[i]}=$k not representable as signed Q30"
            }
        }
        return null
    }

    fun q30Violation(
        freq: Double,
        gainDb: Double,
        q: Double,
        typeCode: Int,
        rateHz: Double = DESIGN_FS,
    ): String? = q30Violation(scaledCoefficients(freq, gainDb, q, typeCode, rateHz))

    fun shelfQCeiling(maxAbsGainDb: Double): Double? {
        val a = sqrt(10.0.pow(abs(maxAbsGainDb) / 20))
        val spread = a + 1 / a
        if (spread <= 2.0 + 1e-12) return null
        var q = floor(100.0 / (1 - 2 / spread)) / 100.0
        while (spread * (1 / q - 1) + 2 < 0) q -= 0.01
        return q
    }

    fun q30ViolationQuantized(w: BandWrite): String? {
        val r = w.registers()
        return q30Violation(
            r.freq.toDouble(),
            r.gain256 / 256.0,
            r.q256 / 256.0 / w.qScale,
            r.typeCode,
            w.rateHz,
        )
    }

    private fun scaledCoefficients(
        freq: Double,
        gainDb: Double,
        q: Double,
        typeCode: Int,
        rateHz: Double,
    ): DoubleArray {
        val a = sqrt(10.0.pow(gainDb / 20))
        val w0 = freq * 6.283185307179586 / rateHz
        val s = sin(w0)
        val c = cos(w0)
        val alpha = s / (2 * q)
        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        if (typeCode == TYPE_LSQ || typeCode == TYPE_HSQ) {
            val sa = (s / 2) * sqrt((a + 1 / a) * (1 / q - 1) + 2)
            val k = 2 * sqrt(a) * sa
            if (typeCode == TYPE_LSQ) {
                b0 = a * ((a + 1) - (a - 1) * c + k)
                b1 = 2 * a * ((a - 1) - (a + 1) * c)
                b2 = a * ((a + 1) - (a - 1) * c - k)
                a0 = (a + 1) + (a - 1) * c + k
                a1 = -2 * ((a - 1) + (a + 1) * c)
                a2 = (a + 1) + (a - 1) * c - k
            } else {
                b0 = a * ((a + 1) + (a - 1) * c + k)
                b1 = -2 * a * ((a - 1) + (a + 1) * c)
                b2 = a * ((a + 1) + (a - 1) * c - k)
                a0 = (a + 1) - (a - 1) * c + k
                a1 = 2 * ((a - 1) - (a + 1) * c)
                a2 = (a + 1) - (a - 1) * c - k
            }
        } else if (typeCode == TYPE_LP || typeCode == TYPE_HP) {

            if (typeCode == TYPE_LP) {
                b0 = (1 - c) / 2
                b1 = 1 - c
                b2 = (1 - c) / 2
            } else {
                b0 = (1 + c) / 2
                b1 = -(1 + c)
                b2 = (1 + c) / 2
            }
            a0 = 1 + alpha
            a1 = -2 * c
            a2 = 1 - alpha
        } else {
            b0 = 1 + alpha * a
            b1 = -2 * c
            b2 = 1 - alpha * a
            a0 = 1 + alpha / a
            a1 = -2 * c
            a2 = 1 - alpha / a
        }
        val scale = 1073741824.0
        return doubleArrayOf(
            b0 / a0 * scale,
            b1 / a0 * scale,
            b2 / a0 * scale,
            -a1 / a0 * scale,
            -a2 / a0 * scale,
        )
    }

    fun computeIir(
        freq: Double,
        gainDb: Double,
        q: Double,
        typeCode: Int,
        rateHz: Double = DESIGN_FS,
        wrapQ30: Boolean = false,
    ): ByteArray {
        val values = scaledCoefficients(freq, gainDb, q, typeCode, rateHz)
        require(values.all { it.isFinite() }) { "non-finite biquad coefficients" }

        if (!wrapQ30) q30Violation(values)?.let { throw Q30Overflow(it) }
        val out = ByteArray(20)
        values.forEachIndexed { i, v ->
            val n = toInt32(if (i < 3) jsRound(v) else -jsRound(-v))
            out[i * 4] = (n and 0xFF).toByte()
            out[i * 4 + 1] = ((n shr 8) and 0xFF).toByte()
            out[i * 4 + 2] = ((n shr 16) and 0xFF).toByte()
            out[i * 4 + 3] = ((n shr 24) and 0xFF).toByte()
        }
        return out
    }

    const val BAND_LEN: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_BAND_LEN
    const val TRN_BAND_LEN: Int = GeneratedDevices.TRN_BLACK_PEARL_BAND_LEN

    fun bandWriteReport(w: BandWrite, slot: Int, lenByte: Int = BAND_LEN): ByteArray {
        val biquad = computeIir(w.freq, w.gainDb, w.q, w.typeCode, w.rateHz, w.wrapQ30)
        if (!w.wrapQ30) q30ViolationQuantized(w)?.let { throw Q30Overflow(it) }

        val f = toInt32(w.freq) and 0xFFFF
        val q = w.qRegister
        val g = toInt32(jsRound(w.gainDb * 256)) and 0xFFFF
        val payload = IntArray(7 + 20 + 10)
        intArrayOf(WRITE, CMD_PEQ, lenByte, 0x00, w.index, 0x00, 0x00).copyInto(payload)
        biquad.forEachIndexed { i, b -> payload[7 + i] = b.toInt() and 0xFF }
        intArrayOf(
                f and 0xFF,
                f shr 8,
                q and 0xFF,
                q shr 8,
                g and 0xFF,
                g shr 8,
                w.typeCode,
                0x00,
                slot,
                END,
            )
            .copyInto(payload, 27)
        return report(*payload)
    }

    fun preampWriteReport(db: Double): ByteArray =
        report(WRITE, CMD_GLOBAL_GAIN, 0x02, 0x00, toInt32(jsRound(db)) and 0xFF)

    val COMMIT_SEQUENCE: List<ByteArray> =
        listOf(
            report(WRITE, 0x05, END),
            report(WRITE, 0x17, END),
            report(WRITE, 0x0A, 0x04, 0x00, 0x00, 0xFF, 0xFF, END),
            report(WRITE, 0x01, 0x01, END),
        )
    private val COMMIT_DELAYS_MS = longArrayOf(20, 20, 50, 0)

    class Step(val report: ByteArray, val delayAfterMs: Long)

    fun temporaryBandSequence(bands: List<BandWrite>, slot: Int): List<Step> =
        bands.map { Step(bandWriteReport(it, slot), 20) } +
            Step(report(WRITE, 0x0A, 0x04, 0x00, 0x00, 0xFF, 0xFF, END), 0)

    fun writeSequence(
        bands: List<BandWrite>,
        preampDb: Double,
        slot: Int,
        commit: Boolean,
    ): List<Step> {
        val steps = ArrayList<Step>()
        bands.forEachIndexed { i, b ->
            steps += Step(bandWriteReport(b, slot), if (i == bands.lastIndex) 120 else 20)
        }
        if (!commit) {
            steps += Step(preampWriteReport(preampDb), 50 + 200)
            return steps
        }
        steps += Step(preampWriteReport(preampDb), 50)
        COMMIT_SEQUENCE.forEachIndexed { i, r ->
            steps += Step(r, COMMIT_DELAYS_MS[i] + if (i == COMMIT_SEQUENCE.lastIndex) 200 else 0)
        }
        return steps
    }

    fun trnWriteSequence(bands: List<BandWrite>, preampDb: Double, slot: Int): List<Step> =
        bands.map { Step(bandWriteReport(it, slot, TRN_BAND_LEN), 25) } +
            listOf(
                Step(report(WRITE, 0x0A, 0x04, 0x00, 0x00, 0xFF, 0xFF), 50),
                Step(preampWriteReport(preampDb), 50),
                Step(report(WRITE, 0x01, END), 200),
                Step(report(WRITE, 0x04, END), 200),
            )
}
