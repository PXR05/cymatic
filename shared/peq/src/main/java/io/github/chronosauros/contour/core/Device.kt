// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import io.github.chronosauros.contour.core.devices.GeneratedDevices

data class DeviceCapabilities(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val freqMinHz: Double,
    val freqMaxHz: Double,
    val types: Set<FilterType>,
    val preampWholeDb: Boolean,
    val shelfQScale: Double = 1.0,
    val peakQScale: Double = 1.0,
    val coefficientRateHz: Double = WalkPlay.DESIGN_FS,
    val shelfLimits: ShelfLimits? = null,
    val wrapQ30: Boolean = false,
) {

    fun gainRange(type: FilterType): ClosedFloatingPointRange<Double> =
        if (type.isShelf && shelfLimits != null)
            maxOf(gainMinDb, shelfLimits.gainMinDb)..minOf(gainMaxDb, shelfLimits.gainMaxDb)
        else gainMinDb..gainMaxDb

    fun qScale(type: FilterType): Double =
        when {
            type.isShelf -> shelfQScale
            type == FilterType.PEAK -> peakQScale
            else -> 1.0
        }

    fun qRange(type: FilterType): ClosedFloatingPointRange<Double> =
        qScale(type)
            .let { qMin / it..qMax / it }
            .let { r ->
                if (type.isShelf && shelfLimits != null)
                    maxOf(r.start, shelfLimits.qMin)..minOf(r.endInclusive, shelfLimits.qMax)
                else r
            }
}

data class ShelfLimits(
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
)

sealed interface DevicePlan {
    data class Ready(val bands: List<WalkPlay.BandWrite>, val preampDb: Int) : DevicePlan

    data class Rejected(val issues: List<String>) : DevicePlan
}

sealed interface DacImport {

    data class Ready(val eq: ImportedEq, val warning: String? = null) : DacImport

    data class Rejected(val issues: List<String>) : DacImport
}

internal fun dbText(x: Double, stillRefused: (Double) -> Boolean = { true }): String {
    if (!x.isFinite()) return x.toString()
    if (kotlin.math.abs(x) >= 1e9) return java.math.BigDecimal.valueOf(x).toPlainString()
    val r = Math.round(x * 10000) / 10000.0
    if (!stillRefused(r))
        return java.math.BigDecimal.valueOf(x).stripTrailingZeros().toPlainString()
    return if (r == Math.rint(r)) r.toLong().toString() else r.toString()
}

internal object SendBlocked {

    fun band(device: String, number: Int, type: FilterType): String {
        val hint =
            if (type == FilterType.HIGH_SHELF)
                "Reduce the boost, raise the shelf frequency or adjust Q."
            else "Reduce the boost or adjust frequency/Q."
        return "${device.removePrefix("CrinEar Protocol ")} band $number cannot be represented safely. $hint"
    }

    fun isBandRefusal(issue: String): Boolean = " cannot be represented safely." in issue

    fun auto(neededDb: Int, minDb: Int): String =
        "this EQ needs $neededDb dB; Contour supports down to $minDb dB for this device. Reduce the combined boosts."
}

object ProtocolTrn {
    private val CAPS_PLAIN: DeviceCapabilities = capabilities(false)
    private val CAPS_WITH_PASS: DeviceCapabilities = capabilities(true)

    val CAPABILITIES: DeviceCapabilities
        get() = if (passFiltersOn) CAPS_WITH_PASS else CAPS_PLAIN

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    const val PASS_FILTERS_VERIFIED = false

    fun capabilities(passFilters: Boolean): DeviceCapabilities =
        GeneratedDevices.TRN_BLACK_PEARL.caps().let { base ->
            if (passFilters)
                base.copy(types = base.types + setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS))
            else base
        }
}

object ProtocolMax {

    const val CANDIDATE_GAIN_DB = 12.0

    private const val VENDOR_GAIN_DB = 10.0

    private val CAPS_PLAIN: DeviceCapabilities =
        capabilities(passFilters = false, wideGain = false, wrapQ30 = false)
    private val CAPS_CANDIDATES: DeviceCapabilities =
        capabilities(passFilters = true, wideGain = true, wrapQ30 = true)

    val CAPABILITIES: DeviceCapabilities
        get() = if (candidatesOn) CAPS_CANDIDATES else CAPS_PLAIN

    val candidatesOn: Boolean
        get() = Proven.on(CANDIDATES_VERIFIED)

    const val CANDIDATES_VERIFIED = true

    fun capabilities(
        passFilters: Boolean,
        wideGain: Boolean,
        wrapQ30: Boolean = false,
    ): DeviceCapabilities =
        GeneratedDevices.CRINEAR_PROTOCOL_MAX.caps().let { base ->
            base.copy(
                types =
                    if (passFilters) base.types + setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS)
                    else base.types - setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS),
                gainMinDb = if (wideGain) -CANDIDATE_GAIN_DB else -VENDOR_GAIN_DB,
                gainMaxDb = if (wideGain) CANDIDATE_GAIN_DB else VENDOR_GAIN_DB,
                wrapQ30 = wrapQ30,
            )
        }
}

object ProtocolMicro {

    private val CAPS_PLAIN: DeviceCapabilities = capabilities(false)
    private val CAPS_WITH_PASS: DeviceCapabilities = capabilities(true)

    val CAPABILITIES: DeviceCapabilities
        get() = if (passFiltersOn) CAPS_WITH_PASS else CAPS_PLAIN

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    val microPositivePreampOn: Boolean
        get() = Proven.on(MICRO_POSITIVE_PREAMP_VERIFIED)

    val echoAPositivePreampOn: Boolean
        get() = Proven.on(ECHO_A_POSITIVE_PREAMP_VERIFIED)

    const val PREAMP_MIN_DB: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_PREAMP_MIN_DB
    const val PREAMP_MAX_DB: Int = GeneratedDevices.CRINEAR_PROTOCOL_MICRO_PREAMP_MAX_DB

    const val PASS_FILTERS_VERIFIED = true

    const val MICRO_POSITIVE_PREAMP_VERIFIED = false

    const val ECHO_A_POSITIVE_PREAMP_VERIFIED = false

    const val VENDOR_PREAMP_MAX_DB = 6

    val PREAMP_MAX_EFFECTIVE_DB: Int
        get() = if (microPositivePreampOn) VENDOR_PREAMP_MAX_DB else PREAMP_MAX_DB

    val ECHO_A_PREAMP_MAX_EFFECTIVE_DB: Int
        get() = if (echoAPositivePreampOn) VENDOR_PREAMP_MAX_DB else PREAMP_MAX_DB

    fun capabilities(passFilters: Boolean): DeviceCapabilities =
        GeneratedDevices.CRINEAR_PROTOCOL_MICRO.caps().let { base ->
            if (passFilters)
                base.copy(types = base.types + setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS))
            else base
        }

    private fun fmt(x: Double): String =
        if (x == Math.rint(x)) x.toLong().toString() else x.toString()

    fun deviceBands(bands: List<Band>): List<Band> = bands.map {
        if (it.type == FilterType.HIGH_SHELF)
            it.copy(type = FilterType.LOW_SHELF, gainDb = -it.gainDb)
        else it
    }

    fun highShelfGainSum(bands: List<Band>): Double =
        bands.filter { it.enabled && it.type == FilterType.HIGH_SHELF }.sumOf { it.gainDb }

    fun preampFits(
        bands: List<Band>,
        preampDb: Double,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): Boolean = deviceFits(preampDb + highShelfGainSum(bands), maxDb)

    private fun deviceFits(d: Double, maxDb: Int): Boolean =
        d.isFinite() && kotlin.math.floor(d + 1e-6).let { it >= PREAMP_MIN_DB && it <= maxDb }

    fun preampRefusal(
        bands: List<Band>,
        preampDb: Double,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): String? {
        if (preampFits(bands, preampDb, maxDb)) return null
        val hs = highShelfGainSum(bands)
        val head = "Preamp ${dbText(preampDb) { !preampFits(bands, it, maxDb) }} dB"
        val device = { d: Double -> !deviceFits(d, maxDb) }
        return if (hs == 0.0) "$head outside $PREAMP_MIN_DB..$maxDb dB range"
        else
            "$head needs device preamp ${dbText(preampDb + hs, device)} dB (HIGH SHELF adjustment ${if (hs > 0) "+" else ""}${dbText(hs)} dB), outside $PREAMP_MIN_DB..$maxDb dB range"
    }

    fun devicePreamp(
        bands: List<Band>,
        preampDb: Double?,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): Int {
        val raw =
            if (preampDb == null) {
                Preamp.auto(deviceBands(bands))
            } else {
                kotlin.math.floor(preampDb + highShelfGainSum(bands) + 1e-6).toInt()
            }
        return raw.coerceIn(PREAMP_MIN_DB, maxDb)
    }

    fun plan(
        bands: List<Band>,
        preampDb: Double?,
        caps: DeviceCapabilities = CAPABILITIES,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): DevicePlan {
        val issues = ArrayList<String>()
        val active = bands.filter { it.enabled }
        if (active.size > caps.bands)
            issues += "${active.size} enabled bands; ${caps.name} has ${caps.bands}"
        bands.forEachIndexed { i, b ->
            if (!b.enabled) return@forEachIndexed
            val n = "Band ${i + 1}"
            if (b.type !in caps.types) issues += "$n: ${b.type} is not supported by ${caps.name}"
            if (!(b.freqHz >= caps.freqMinHz && b.freqHz <= caps.freqMaxHz)) {
                issues +=
                    "$n: ${fmt(b.freqHz)} Hz outside ${fmt(caps.freqMinHz)}-${fmt(caps.freqMaxHz)} Hz"
            }

            val noGain = b.type.isPass && b.type in caps.types
            if (!noGain && !(b.gainDb >= caps.gainMinDb && b.gainDb <= caps.gainMaxDb)) {
                issues +=
                    "$n: ${fmt(b.gainDb)} dB outside ${fmt(caps.gainMinDb)}..${fmt(caps.gainMaxDb)} dB"
            }
            val qRange = caps.qRange(b.type)
            if (!(b.q >= qRange.start && b.q <= qRange.endInclusive)) {
                issues +=
                    "$n: Q ${fmt(b.q)} outside ${fmt(qRange.start)}-${fmt(qRange.endInclusive)}"
            }
        }
        if (preampDb != null && !preampDb.isFinite())
            issues += "Preamp $preampDb dB is not a number"
        if (preampDb != null && preampDb.isFinite())
            preampRefusal(active, preampDb, maxDb)?.let { issues += it }
        if (issues.isNotEmpty()) return DevicePlan.Rejected(issues)
        val dev = deviceBands(active)
        val writes =
            List(caps.bands) { slot ->
                if (slot < dev.size) WalkPlay.bandWrite(slot, dev[slot], caps)
                else WalkPlay.factoryFlat(slot)
            }

        val rows = bands.indices.filter { bands[it].enabled }
        writes.forEachIndexed { slot, write ->
            when (
                val failure = runCatching { WalkPlay.bandWriteReport(write, 0) }.exceptionOrNull()
            ) {
                null -> {}
                is WalkPlay.Q30Overflow ->
                    issues +=
                        SendBlocked.band(
                            caps.name,
                            (rows.getOrNull(slot) ?: slot) + 1,
                            active.getOrNull(slot)?.type ?: FilterType.PEAK,
                        )
                else ->
                    issues +=
                        "Band ${slot + 1}: non-finite biquad coefficients; cannot send to ${caps.name}"
            }
        }
        if (issues.isNotEmpty()) return DevicePlan.Rejected(issues)
        val deviceGain =
            if (preampDb == null) {
                val needed = runCatching {
                    Preamp.auto(dev)
                }
                    .getOrElse {
                        return DevicePlan.Rejected(
                            listOf("AUTO preamp: no finite response; cannot send to ${caps.name}")
                        )
                    }

                if (needed < PREAMP_MIN_DB)
                    return DevicePlan.Rejected(listOf(SendBlocked.auto(needed, PREAMP_MIN_DB)))
                needed
            } else
                runCatching { devicePreamp(active, preampDb, maxDb) }
                    .getOrElse {
                        return DevicePlan.Rejected(
                            listOf("AUTO preamp: no finite response; cannot send to ${caps.name}")
                        )
                    }
        return DevicePlan.Ready(writes, deviceGain)
    }

    fun plan(
        profile: Profile,
        caps: DeviceCapabilities = CAPABILITIES,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): DevicePlan = plan(profile.bands, profile.preampDb, caps, maxDb)

    fun matches(plan: DevicePlan.Ready, regs: List<WalkPlay.Registers>, preampDb: Int): Boolean =
        regs.size == plan.bands.size &&
            plan.preampDb == preampDb &&
            plan.bands.indices.all { plan.bands[it].registers() == regs[it] }

    fun autoRefusal(bands: List<Band>): String? {
        val needed =
            runCatching { Preamp.auto(deviceBands(bands.filter { it.enabled })) }.getOrNull()
                ?: return null
        return if (needed < PREAMP_MIN_DB) SendBlocked.auto(needed, PREAMP_MIN_DB) else null
    }

    fun importExact(
        bands: List<WalkPlay.DeviceBand>,
        preampDb: Int,
        caps: DeviceCapabilities = CAPABILITIES,
        maxDb: Int = PREAMP_MAX_EFFECTIVE_DB,
    ): DacImport {
        if (bands.size != WalkPlay.BANDS || bands.indices.any { bands[it].registers.index != it }) {
            return DacImport.Rejected(listOf("Expected all 8 DAC slots in order"))
        }
        if (preampDb !in PREAMP_MIN_DB..maxDb) {
            return DacImport.Rejected(
                listOf("DAC preamp $preampDb dB outside $PREAMP_MIN_DB..$maxDb dB")
            )
        }

        val native =
            setOf(WalkPlay.TYPE_PK, WalkPlay.TYPE_LSQ) +
                caps.types.filter { it.isPass }.map(WalkPlay::typeCode)
        val lastUsed =
            maxOf(
                0,
                bands.indexOfLast {
                    it.registers != WalkPlay.factoryFlat(it.registers.index).registers()
                },
            )
        val reconstructed = ArrayList<Band>()
        for (slot in 0..lastUsed) {
            val deviceBand = bands[slot]
            val r = deviceBand.registers
            if (!deviceBand.enabled || r.typeCode !in native) {
                return DacImport.Rejected(
                    listOf(
                        "DAC band ${slot + 1}: disabled or unsupported native type ${r.typeCode}; cannot import exactly"
                    )
                )
            }

            if (deviceBand.type.isPass && r.gain256 != 0) {
                return DacImport.Rejected(
                    listOf(
                        "DAC band ${slot + 1}: pass filter with a gain register; cannot import exactly"
                    )
                )
            }

            val frequency = r.freq.toDouble()
            val q = WalkPlay.intendedQ(r.q256, deviceBand.type, caps)
            reconstructed += Band("dac$slot", deviceBand.type, frequency, r.gain256 / 256.0, q)
        }
        val eq = ImportedEq(reconstructed, preampDb.toDouble())
        val candidate = plan(eq.bands, eq.preampDb, caps, maxDb)
        if (
            candidate is DevicePlan.Rejected &&
                candidate.issues.all { SendBlocked.isBandRefusal(it) }
        ) {

            val writes =
                List(WalkPlay.BANDS) { slot ->
                    if (slot < reconstructed.size)
                        WalkPlay.bandWrite(slot, reconstructed[slot], caps)
                    else WalkPlay.factoryFlat(slot)
                }
            if (matches(DevicePlan.Ready(writes, preampDb), bands.map { it.registers }, preampDb)) {
                return DacImport.Ready(
                    eq,
                    candidate.issues.joinToString(" ") +
                        " Sending it back stays blocked until you change the band.",
                )
            }
        }
        if (
            candidate !is DevicePlan.Ready ||
                !matches(candidate, bands.map { it.registers }, preampDb)
        ) {
            val issue =
                if (candidate is DevicePlan.Rejected) candidate.issues.joinToString("; ")
                else "quantised registers differ on re-encode"
            return DacImport.Rejected(listOf("DAC state cannot be imported exactly: $issue"))
        }
        return DacImport.Ready(eq)
    }

    fun matches(profile: Profile, regs: List<WalkPlay.Registers>, preampDb: Int): Boolean =
        (plan(profile) as? DevicePlan.Ready)?.let { matches(it, regs, preampDb) } ?: false

    fun isFlat(regs: List<WalkPlay.Registers>, preampDb: Int): Boolean =
        matches(flatPlan(), regs, preampDb)

    fun flatPlan(): DevicePlan.Ready =
        DevicePlan.Ready(List(WalkPlay.BANDS) { WalkPlay.factoryFlat(it) }, 0)
}
