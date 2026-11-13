// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Band
import io.github.chronosauros.contour.core.Dsp
import io.github.chronosauros.contour.core.FilterType
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.truncate

object MoondropGen1Codec {
    const val REPORT_ID = 1

    const val PAYLOAD_BYTES = 61
    const val CMD_PRESET = 0x5A
    const val CMD_BAND_READ = 0xDD
    const val CMD_WRITE = 0xDC
    const val FLAG_READ = 1
    const val FLAG_WRITE = 0

    const val COMMIT = 0xFF

    val RATES: List<Pair<Int, Double>> =
        listOf(4 to 44100.0, 5 to 48000.0, 6 to 96000.0, 7 to 192000.0, 8 to 384000.0)

    const val COEFFICIENT_MARKER = 3
    private const val SCALE = 4_194_304.0

    const val TYPE_PEAK = 0
    const val TYPE_LOW_SHELF = 1
    const val TYPE_HIGH_SHELF = 2
    val TYPES: Map<Int, FilterType> =
        mapOf(
            TYPE_PEAK to FilterType.PEAK,
            TYPE_LOW_SHELF to FilterType.LOW_SHELF,
            TYPE_HIGH_SHELF to FilterType.HIGH_SHELF,
        )

    fun roundAway(x: Double): Long {
        val t = truncate(x)
        return (if (abs(x - t) >= 0.5) t + sign(x) else t).toLong()
    }

    enum class Kind {
        READ_PRESET,
        READ_BAND,
        WRITE,
    }

    class Frame(
        val kind: Kind,
        val payload: ByteArray,
        val what: String,
        val delayAfterMs: Long = 0,
        val band: Int? = null,
    ) {

        val report: ByteArray
            get() = byteArrayOf(REPORT_ID.toByte()) + payload
    }

    fun payload(cmd: Int, flag: Int, vararg dwords: Int): ByteArray {
        require(dwords.size * 4 <= PAYLOAD_BYTES - 9) { "Too many dwords for one report" }
        val p = ByteArray(PAYLOAD_BYTES)
        byteArrayOf(0x00, 0x0D, 0x00, cmd.toByte(), flag.toByte(), 0x00, 0x23, 0x2D, 0xB3.toByte())
            .copyInto(p)
        dwords.forEachIndexed { i, d ->
            for (k in 0 until 4) p[9 + i * 4 + k] = (d ushr (8 * k)).toByte()
        }
        return p
    }

    fun at(payload: ByteArray, wire: Int): Int = payload[wire - 1].toInt() and 255

    fun dword(payload: ByteArray, wire: Int): Int =
        at(payload, wire) or
            (at(payload, wire + 1) shl 8) or
            (at(payload, wire + 2) shl 16) or
            (at(payload, wire + 3) shl 24)

    fun presetQuery(): Frame =
        Frame(Kind.READ_PRESET, payload(CMD_PRESET, FLAG_READ, CMD_PRESET), "read active preset")

    fun bandQuery(n: Int): Frame {
        require(n in 1..9) { "Band $n is not 1-9" }
        return Frame(Kind.READ_BAND, payload(CMD_BAND_READ, FLAG_READ, n), "read band $n", band = n)
    }

    fun readQueries(bands: Int): List<Frame> =
        listOf(presetQuery()) + List(bands) { bandQuery(it + 1) }

    fun selectSteps(index: Int, afterSelectMs: Long): List<Frame> {
        require(index in 0..255) { "Preset index $index out of range" }
        return listOf(
            Frame(
                Kind.WRITE,
                payload(CMD_PRESET, FLAG_WRITE, CMD_PRESET, index),
                "select preset $index",
                afterSelectMs,
            )
        )
    }

    data class Meta(val freqHz: Int, val qRaw: Int, val type: Int, val gainDb: Int)

    class BandWrite(
        val index: Int,
        val band: Band,
        val meta: Meta,
        val coefficients: List<IntArray>,
    ) {
        override fun toString() = "band $index $meta"
    }

    fun coefficients(band: Band, fs: Double): IntArray {
        val q = Dsp.biquad(band, fs)
        val raw = doubleArrayOf(q.b0, q.b1, q.b2, -q.a1, -q.a2)
        return IntArray(5) { i ->
            val v = raw[i] * SCALE
            require(v.isFinite() && abs(v) < Int.MAX_VALUE.toDouble()) {
                "Coefficient ${i + 1} does not fit 32 bits"
            }
            roundAway(v).toInt()
        }
    }

    fun meta(band: Band): Meta {
        val type =
            TYPES.entries.firstOrNull { it.value == band.type }?.key
                ?: throw IllegalArgumentException("${band.type.name} is not a gen-1 filter type")
        return Meta(
            roundAway(band.freqHz).toInt(),
            roundAway(band.q * 256.0).toInt(),
            type,
            roundAway(band.gainDb).toInt(),
        )
    }

    fun neutralBand(model: MoondropGen1Catalog.Model, position: Int): Band =
        Band(
            "neutral$position",
            FilterType.PEAK,
            model.factoryFrequenciesHz[position].toDouble(),
            0.0,
            model.factoryQ,
        )

    fun plan(model: MoondropGen1Catalog.Model, bands: List<Band>): List<BandWrite> {
        require(bands.size <= model.bandCount) {
            "${bands.size} bands; ${model.name} has ${model.bandCount}"
        }
        return List(model.bandCount) { i ->
            val b = bands.getOrNull(i)?.takeIf { it.enabled } ?: neutralBand(model, i)
            BandWrite(i + 1, b, meta(b), RATES.map { (_, fs) -> coefficients(b, fs) })
        }
    }

    fun issues(model: MoondropGen1Catalog.Model, bands: List<Band>): List<String> {
        if (bands.size > model.bandCount)
            return listOf(
                "${bands.size} bands; ${model.name} accepts ${model.bandCount}; no truncation"
            )
        val found = ArrayList<String>()
        bands.forEachIndexed { i, b ->
            if (!b.enabled) return@forEachIndexed
            val n = i + 1
            if (b.type !in model.types) {
                found += "Band $n: type unsupported"
                return@forEachIndexed
            }
            if (!b.freqHz.isFinite() || b.freqHz !in model.minFrequencyHz..model.maxFrequencyHz)
                found +=
                    "Band $n: freq ${num(b.freqHz)} Hz outside ${num(model.minFrequencyHz)}-${num(model.maxFrequencyHz)} Hz"
            if (!b.gainDb.isFinite() || b.gainDb !in model.minGainDb..model.maxGainDb)
                found +=
                    "Band $n: gain ${num(b.gainDb)} dB outside ${num(model.minGainDb)}..${num(model.maxGainDb)} dB"
            if (!b.q.isFinite() || b.q !in model.minQ..model.maxQ)
                found +=
                    "Band $n: Q %.2f outside %.2f-%.2f"
                        .format(java.util.Locale.ROOT, b.q, model.minQ, model.maxQ)
        }
        if (found.isNotEmpty()) return found

        bands.forEachIndexed { i, b ->
            if (!b.enabled) return@forEachIndexed
            for ((_, fs) in RATES) {
                val why = runCatching { coefficients(b, fs) }.exceptionOrNull()?.message ?: continue
                found +=
                    "Band ${i + 1}: ${if (b.type.isShelfType) "shelf gain/Q has no real response" else why}"
                break
            }
        }
        return found
    }

    private val FilterType.isShelfType
        get() = this == FilterType.LOW_SHELF || this == FilterType.HIGH_SHELF

    private fun num(x: Double): String =
        if (x.isFinite() && x == Math.rint(x) && abs(x) < 1e9) x.toLong().toString()
        else "%.1f".format(java.util.Locale.ROOT, x)

    fun writeSteps(model: MoondropGen1Catalog.Model, planned: List<BandWrite>): List<Frame> {
        require(planned.size == model.bandCount) {
            "${planned.size} planned bands; ${model.name} has ${model.bandCount}"
        }
        val gap = model.writeGapMs
        val steps = ArrayList<Frame>()
        steps +=
            Frame(
                Kind.WRITE,
                payload(CMD_PRESET, FLAG_WRITE, CMD_PRESET, model.customIndex),
                "select the Custom EQ",
                gap,
            )
        for (b in planned) steps +=
            Frame(
                Kind.WRITE,
                payload(
                    CMD_WRITE,
                    FLAG_WRITE,
                    0,
                    b.index,
                    b.meta.freqHz,
                    b.meta.qRaw,
                    b.meta.type,
                    gain24(b.meta.gainDb),
                ),
                "band ${b.index} meta",
                gap,
            )
        for (b in planned) RATES.forEachIndexed { r, (code, fs) ->
            val c = b.coefficients[r]
            steps +=
                Frame(
                    Kind.WRITE,
                    payload(
                        CMD_WRITE,
                        FLAG_WRITE,
                        code,
                        b.index,
                        COEFFICIENT_MARKER,
                        c[0],
                        c[1],
                        c[2],
                        c[3],
                        c[4],
                    ),
                    "band ${b.index} coefficients ${fs.toInt()} Hz",
                    gap,
                )
        }
        steps +=
            Frame(Kind.WRITE, payload(CMD_WRITE, FLAG_WRITE, COMMIT), "commit", model.afterCommitMs)
        return steps
    }

    fun gain24(gainDb: Int): Int {
        require(gainDb in -0x800000..0x7FFFFF) { "Gain $gainDb does not fit 24 bits" }
        return if (gainDb < 0) 0x1000000 + gainDb else gainDb
    }

    fun isWholeReply(payload: ByteArray): Boolean = payload.size == PAYLOAD_BYTES

    fun isPresetReply(payload: ByteArray): Boolean =
        isWholeReply(payload) && at(payload, 4) == CMD_PRESET && at(payload, 5) and 0x80 != 0

    fun isBandReply(payload: ByteArray, n: Int): Boolean =
        isWholeReply(payload) &&
            at(payload, 4) == CMD_BAND_READ &&
            at(payload, 5) and 0x80 != 0 &&
            dword(payload, 14) == n

    fun parsePreset(payload: ByteArray): Int {
        require(isPresetReply(payload)) { "Not a Moondrop gen-1 preset reply" }
        return dword(payload, 14)
    }

    data class RawBand(
        val index: Int,
        val freqHz: Int,
        val qRaw: Int,
        val nativeType: Int,
        val gainDb: Int,
    ) {
        val q: Double
            get() = qRaw / 256.0

        val type: FilterType?
            get() = TYPES[nativeType]

        val meta: Meta
            get() = Meta(freqHz, qRaw, nativeType, gainDb)
    }

    fun parseBand(payload: ByteArray, n: Int): RawBand {
        require(isBandReply(payload, n)) { "Not the reply to a read of band $n" }
        var g = at(payload, 30) or (at(payload, 31) shl 8) or (at(payload, 32) shl 16)
        if (g and 0x800000 != 0) g -= 0x1000000
        return RawBand(n, dword(payload, 18), dword(payload, 22), dword(payload, 26), g)
    }

    class Snapshot(
        val model: MoondropGen1Catalog.Model,
        val activeIndex: Int,
        val bands: List<RawBand>,
    ) {

        val onCustom: Boolean
            get() = activeIndex == model.customIndex
    }

    fun mismatches(
        planned: List<BandWrite>,
        activeIndex: Int,
        bands: List<RawBand>,
        customIndex: Int,
    ): List<String> {
        val out = ArrayList<String>()
        if (activeIndex != customIndex)
            out += "the cable is on preset $activeIndex, not the Custom EQ ($customIndex)"
        val have = bands.associateBy { it.index }
        for (b in planned) {
            val r = have[b.index]
            if (r == null) {
                out += "band ${b.index} missing"
                continue
            }
            if (r.meta != b.meta)
                out += "band ${b.index} ${describe(b.meta)} -> ${describe(r.meta)}"
        }
        return out
    }

    private fun describe(m: Meta) =
        "${TYPES[m.type]?.name ?: "type ${m.type}"} ${m.freqHz} Hz ${m.gainDb} dB Q ${"%.3f".format(java.util.Locale.ROOT, m.qRaw / 256.0)}"

    fun plannedRequests(model: MoondropGen1Catalog.Model): Int =
        readQueries(model.bandCount).size + 1 + model.bandCount + model.bandCount * RATES.size + 1

    fun plannedRequestsAfterReturn(model: MoondropGen1Catalog.Model): Int =
        1 + readQueries(model.bandCount).size

    fun isRestartingCommit(payload: ByteArray): Boolean =
        at(payload, 4) == CMD_WRITE && at(payload, 5) == FLAG_WRITE && dword(payload, 10) == COMMIT

    fun requireSendable(payload: ByteArray) {
        require(
            payload.size == PAYLOAD_BYTES &&
                at(payload, 1) == 0 &&
                at(payload, 2) == 0x0D &&
                at(payload, 3) == 0 &&
                at(payload, 6) == 0 &&
                at(payload, 7) == 0x23 &&
                at(payload, 8) == 0x2D &&
                at(payload, 9) == 0xB3
        ) {
            "Not a Moondrop gen-1 frame"
        }
        val cmd = at(payload, 4)
        val flag = at(payload, 5)
        when {
            cmd == CMD_PRESET && flag == FLAG_READ ->
                require(dword(payload, 10) == CMD_PRESET) { "Preset read with another argument" }
            cmd == CMD_PRESET && flag == FLAG_WRITE ->
                require(dword(payload, 10) == CMD_PRESET && dword(payload, 14) in 0..5) {
                    "Preset select out of range"
                }
            cmd == CMD_BAND_READ && flag == FLAG_READ ->
                require(dword(payload, 10) in 1..9) { "Band read out of range" }
            cmd == CMD_WRITE && flag == FLAG_WRITE -> {
                val code = dword(payload, 10)
                require(
                    code == COMMIT ||
                        (code == 0 && dword(payload, 14) in 1..9) ||
                        (RATES.any { it.first == code } &&
                            dword(payload, 14) in 1..9 &&
                            dword(payload, 18) == COEFFICIENT_MARKER)
                ) {
                    "Write code $code is not a band, a coefficient set or the commit"
                }
            }
            else ->
                throw IllegalArgumentException(
                    "Command 0x%02x flag %d is not sent to a gen-1 cable".format(cmd, flag)
                )
        }
    }
}
