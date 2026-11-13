// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import io.github.chronosauros.contour.core.devices.GeneratedDevices
import io.github.chronosauros.contour.core.devices.WalkPlayData
import io.github.chronosauros.contour.core.native.DacSetting

enum class DeviceProtocol
private constructor(internal val data: WalkPlayData, val settings: List<DacSetting> = emptyList()) {

    MICRO(GeneratedDevices.CRINEAR_PROTOCOL_MICRO),
    MAX(GeneratedDevices.CRINEAR_PROTOCOL_MAX),
    TRN(GeneratedDevices.TRN_BLACK_PEARL);

    val caps: DeviceCapabilities
        get() =
            when {
                data === GeneratedDevices.CRINEAR_PROTOCOL_MICRO -> ProtocolMicro.CAPABILITIES
                data === GeneratedDevices.TRN_BLACK_PEARL -> ProtocolTrn.CAPABILITIES
                data === GeneratedDevices.CRINEAR_PROTOCOL_MAX -> ProtocolMax.CAPABILITIES
                else -> recordCaps
            }

    private val recordCaps: DeviceCapabilities by lazy { data.caps() }

    val experimental: Boolean
        get() = this != MICRO

    val supportsAb: Boolean
        get() = this == MICRO

    val preampMin: Int
        get() = data.preampMinDb

    val preampMax: Int
        get() = if (this == MICRO) ProtocolMicro.PREAMP_MAX_EFFECTIVE_DB else data.preampMaxDb

    fun shelfOffset(bands: List<Band>): Double =
        if (this == MICRO) ProtocolMicro.highShelfGainSum(bands) else 0.0

    fun devicePreamp(bands: List<Band>, preampDb: Double?): Int =
        if (this == MICRO) {
            ProtocolMicro.devicePreamp(bands, preampDb)
        } else {
            require(preampDb == null || preampDb.isFinite())
            (if (preampDb == null) Preamp.auto(bands)
                else kotlin.math.floor(preampDb + 1e-6).toInt())
                .coerceIn(preampMin, preampMax)
        }

    fun preampFits(bands: List<Band>, preampDb: Double): Boolean =
        preampDb.isFinite() &&
            (if (this == MICRO) ProtocolMicro.preampFits(bands, preampDb)
            else kotlin.math.floor(preampDb + 1e-6) in preampMin.toDouble()..preampMax.toDouble())

    fun preampRefusal(bands: List<Band>, preampDb: Double): String? =
        when {
            preampFits(bands, preampDb) -> null
            this == MICRO && preampDb.isFinite() -> ProtocolMicro.preampRefusal(bands, preampDb)
            else ->
                "Preamp ${dbText(preampDb) { !preampFits(bands, it) }} dB outside $preampMin..$preampMax dB policy"
        }

    fun fitImportedPreamp(bands: List<Band>, preampDb: Double): Double {
        if (!preampDb.isFinite())
            return if (preampDb.isNaN()) 0.0
            else if (preampDb > 0) preampMax.toDouble() else preampMin.toDouble()
        val down = Preamp.floorTo(preampDb)
        return if (preampFits(bands, down) == preampFits(bands, preampDb)) down else preampDb
    }

    fun manualForAuto(bands: List<Band>): Double? {
        val register = runCatching { devicePreamp(bands, null) }.getOrNull() ?: return null

        val first = kotlin.math.ceil((register - shelfOffset(bands) - 1e-6) * 10).toLong()
        for (n in first..first + 3) {
            val m = n / 10.0
            if (kotlin.math.floor(m + shelfOffset(bands) + 1e-6).toInt() == register) return m
        }
        return null
    }

    fun autoRefusal(bands: List<Band>): String? {
        if (this == MICRO) return ProtocolMicro.autoRefusal(bands)
        val needed =
            runCatching { Preamp.auto(bands.filter { it.enabled }) }.getOrNull() ?: return null
        return if (needed < preampMin) SendBlocked.auto(needed, preampMin) else null
    }

    fun shownPreamp(profile: Profile): Double =
        devicePreamp(profile.bands, profile.preampDb) - shelfOffset(profile.bands)

    private fun flatBand(index: Int): WalkPlay.BandWrite =
        WalkPlay.flatWrite(index, data.factoryFlat[index])

    fun writeSequence(plan: DevicePlan.Ready, slot: Int): List<WalkPlay.Step> =
        if (this == TRN) WalkPlay.trnWriteSequence(plan.bands, plan.preampDb.toDouble(), slot)
        else WalkPlay.writeSequence(plan.bands, plan.preampDb.toDouble(), slot, commit = true)

    fun flatPlan(): DevicePlan.Ready =
        if (this == MICRO) ProtocolMicro.flatPlan()
        else DevicePlan.Ready(List(caps.bands) { flatBand(it) }, 0)

    fun plan(profile: Profile): DevicePlan = plan(profile.bands, profile.preampDb)

    fun plan(
        bands: List<Band>,
        preampDb: Double?,
        caps: DeviceCapabilities = this.caps,
    ): DevicePlan {
        if (this == MICRO) return ProtocolMicro.plan(bands, preampDb)
        val issues = ArrayList<String>()
        val active = bands.filter { it.enabled }
        if (active.size > caps.bands)
            issues += "${active.size} enabled bands; ${caps.name} has ${caps.bands}"
        bands.forEachIndexed { i, b ->
            if (!b.freqHz.isFinite() || !b.gainDb.isFinite() || !b.q.isFinite())
                issues += "Band ${i + 1}: non-finite values"
            if (
                b.enabled &&
                    (b.type !in caps.types ||
                        b.freqHz !in caps.freqMinHz..caps.freqMaxHz ||
                        b.gainDb !in caps.gainMinDb..caps.gainMaxDb ||
                        b.q !in caps.qRange(b.type))
            )
                issues += "Band ${i + 1}: outside ${caps.name} supported types/ranges"
        }
        if (preampDb != null) preampRefusal(active, preampDb)?.let { issues += it }
        if (issues.isNotEmpty()) return DevicePlan.Rejected(issues)
        return runCatching {
            val writes =
                List(caps.bands) { i ->
                    if (i < active.size) WalkPlay.bandWrite(i, active[i], caps) else flatBand(i)
                }

            val rows = bands.indices.filter { bands[it].enabled }
            val blocked = ArrayList<String>()
            writes.forEachIndexed { i, w ->
                try {
                    WalkPlay.bandWriteReport(w, 0)
                } catch (_: WalkPlay.Q30Overflow) {
                    blocked +=
                        SendBlocked.band(
                            caps.name,
                            (rows.getOrNull(i) ?: i) + 1,
                            active.getOrNull(i)?.type ?: FilterType.PEAK,
                        )
                }
            }
            if (blocked.isNotEmpty()) return@runCatching DevicePlan.Rejected(blocked)

            if (preampDb == null) {
                val needed = Preamp.auto(active)
                if (needed < preampMin)
                    return@runCatching DevicePlan.Rejected(
                        listOf(SendBlocked.auto(needed, preampMin))
                    )
            }
            DevicePlan.Ready(writes, devicePreamp(active, preampDb))
        }
            .getOrElse { DevicePlan.Rejected(listOf("Invalid EQ: ${it.message}")) }
    }

    fun matches(plan: DevicePlan.Ready, regs: List<WalkPlay.Registers>, preampDb: Int): Boolean =
        if (this == MICRO) ProtocolMicro.matches(plan, regs, preampDb)
        else plan.bands.size == caps.bands && ProtocolMicro.matches(plan, regs, preampDb)

    fun matches(profile: Profile, regs: List<WalkPlay.Registers>, preampDb: Int): Boolean =
        if (this == MICRO) ProtocolMicro.matches(profile, regs, preampDb)
        else (plan(profile) as? DevicePlan.Ready)?.let { matches(it, regs, preampDb) } ?: false

    fun importExact(
        bands: List<WalkPlay.DeviceBand>,
        preampDb: Int,
        caps: DeviceCapabilities = this.caps,
    ): DacImport {
        if (this == MICRO) return ProtocolMicro.importExact(bands, preampDb)
        return importExactTolerant(bands, preampDb, caps)
    }

    private fun importExactTolerant(
        bands: List<WalkPlay.DeviceBand>,
        preampDb: Int,
        caps: DeviceCapabilities,
    ): DacImport {
        if (bands.size != caps.bands || bands.indices.any { bands[it].registers.index != it })
            return DacImport.Rejected(listOf("Expected all ${caps.bands} DAC slots in order"))
        if (preampDb !in preampMin..preampMax)
            return DacImport.Rejected(listOf("DAC preamp outside supported policy"))
        val held = bands.filter { it.enabled }
        val skipped = bands.size - held.size
        if (held.isEmpty()) return DacImport.Rejected(listOf("DAC holds no enabled band"))

        val held10 =
            List(caps.bands) { i ->
                held.getOrNull(i)?.registers?.copy(index = i) ?: flatBand(i).registers()
            }

        val last = maxOf(0, held.indices.indexOfLast { held10[it] != flatBand(it).registers() })
        val nativeCodes = caps.types.map { WalkPlay.typeCode(it) }.toSet()
        val reconstructed =
            held.take(last + 1).mapIndexed { i, b ->
                val r = b.registers
                if (r.typeCode !in nativeCodes)
                    return DacImport.Rejected(listOf("DAC band ${r.index + 1}: unsupported type"))
                Band(
                    "dac$i",
                    b.type,
                    r.freq.toDouble(),
                    r.gain256 / 256.0,
                    WalkPlay.intendedQ(r.q256, b.type, caps),
                )
            }
        val eq = ImportedEq(reconstructed, preampDb.toDouble())
        val note =
            if (skipped == 0) ""
            else " $skipped disabled DAC slot${if (skipped == 1) "" else "s"} left out."
        val candidate = plan(eq.bands, eq.preampDb, caps)
        if (candidate is DevicePlan.Ready)
            return if (matches(candidate, held10, preampDb))
                DacImport.Ready(eq, note.trim().ifEmpty { null })
            else DacImport.Rejected(listOf("DAC state cannot be re-encoded exactly"))
        val refused = (candidate as DevicePlan.Rejected).issues
        if (
            refused.all {
                SendBlocked.isBandRefusal(it) ||
                    it.endsWith(": outside ${caps.name} supported types/ranges")
            }
        ) {
            val writes =
                List(caps.bands) { i ->
                    if (i < reconstructed.size) WalkPlay.bandWrite(i, reconstructed[i], caps)
                    else flatBand(i)
                }
            if (matches(DevicePlan.Ready(writes, preampDb), held10, preampDb))
                return DacImport.Ready(
                    eq,
                    refused.joinToString(" ") { if (it.endsWith(".")) it else "$it." } +
                        " Sending it back stays blocked until you change the band.$note",
                )
        }
        return DacImport.Rejected(listOf("DAC state cannot be re-encoded exactly"))
    }

    fun isReply(buf: ByteArray, cmd: Int): Boolean =
        if (this == MICRO) WalkPlay.isReply(buf, cmd)
        else
            buf.size >= WalkPlay.REPORT_SIZE &&
                WalkPlay.isReply(buf, cmd) &&
                (buf[1].toInt() and 0xFF) == WalkPlay.READ

    fun isBandReply(buf: ByteArray, index: Int): Boolean =
        if (this == MICRO) WalkPlay.isBandReply(buf, index)
        else
            index in 0 until caps.bands &&
                isReply(buf, WalkPlay.CMD_PEQ) &&
                WalkPlay.isBandReply(buf, index)

    fun isSlotReply(buf: ByteArray): Boolean =
        if (this == MICRO) WalkPlay.isReply(buf, WalkPlay.CMD_PEQ) else isBandReply(buf, 0)

    fun parseBand(
        buf: ByteArray,
        index: Int,
        caps: DeviceCapabilities = this.caps,
    ): WalkPlay.DeviceBand {
        if (this == MICRO) return WalkPlay.parseBand(buf)
        require(isBandReply(buf, index)) { "Invalid band $index reply" }
        require(buf[3].toInt() == 0 && buf[4].toInt() == 0) { "Invalid PEQ reply header" }
        val b = WalkPlay.parseBand(buf)

        if (b.enabled) {
            require(b.registers.typeCode in WalkPlay.TYPE_LSQ..WalkPlay.TYPE_HP) {
                "Unsupported ${caps.name} native type"
            }
            val hz =
                requireNotNull(data.readBoundsHz) {
                    "devices/${data.id}.json readback.readBoundsHz"
                }
            val db =
                requireNotNull(data.readBoundsDb) {
                    "devices/${data.id}.json readback.readBoundsDb"
                }
            require(
                b.registers.freq in hz &&
                    b.registers.q256 in 1..0xFFFF &&
                    b.registers.gain256 in (db.start * 256).toInt()..(db.endInclusive * 256).toInt()
            ) {
                "Malformed ${if (this == TRN) "TRN" else caps.name} registers"
            }
        }
        return b
    }

    fun parseSlot(buf: ByteArray, caps: DeviceCapabilities = this.caps): Int {
        if (this == MICRO) return WalkPlay.parseSlot(buf)
        require(isBandReply(buf, 0)) { "Invalid bulk slot reply" }
        parseBand(buf, 0, caps)
        return WalkPlay.parseSlot(buf)
    }

    fun parseVersion(buf: ByteArray): String {
        if (this == MICRO) return WalkPlay.parseVersion(buf)
        require(isReply(buf, WalkPlay.CMD_VERSION)) { "Invalid version reply" }
        val version = WalkPlay.parseVersion(buf)
        require(version.isNotEmpty() && version.all { it.code in 0x20..0x7E }) {
            "Invalid firmware version"
        }
        return version
    }

    fun parsePreamp(buf: ByteArray): Int {
        if (this == MICRO) return WalkPlay.parsePreamp(buf)
        require(isReply(buf, WalkPlay.CMD_GLOBAL_GAIN)) { "Invalid preamp reply" }

        if (this != TRN)
            require(buf[3].toInt() == 2 && buf[4].toInt() == 0) { "Invalid preamp header" }
        return WalkPlay.parsePreamp(buf)
    }

    companion object {

        fun find(vendorId: Int, productId: Int): DeviceProtocol? = entries.firstOrNull {
            it.caps.vendorId == vendorId && it.caps.productId == productId
        }
    }
}
