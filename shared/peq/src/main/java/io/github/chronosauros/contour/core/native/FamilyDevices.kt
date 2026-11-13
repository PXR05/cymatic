// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Band
import io.github.chronosauros.contour.core.BuildChannel
import io.github.chronosauros.contour.core.DacImport
import io.github.chronosauros.contour.core.DevicePlan
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.ImportedEq
import io.github.chronosauros.contour.core.Profile
import io.github.chronosauros.contour.core.ProtocolMicro
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.WalkPlay
import io.github.chronosauros.contour.core.isPass
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface FamilyState {

    val slot: Int

    val preampDb: Double?

    val importable: Boolean

    fun holds(profile: Profile): Boolean

    fun matches(profile: Profile): Boolean

    fun importExact(): ImportedEq

    fun readings(): List<SettingReading>

    fun describe(): String

    data class Dawn(val model: MoondropCatalog.Model, val snapshot: MoondropCodec.Snapshot) :
        FamilyState {
        override val slot: Int
            get() = snapshot.activeIndex

        override val preampDb: Double?
            get() = snapshot.preGain?.db

        val onWritableBank: Boolean
            get() = snapshot.peqMode

        override val importable: Boolean
            get() = onWritableBank

        val bankNotice: String?
            get() =
                if (onWritableBank) null
                else
                    "The DAC is on EQ index ${snapshot.activeIndex}, one of its own presets. Switch the DAC to its PEQ mode (EQ index ${model.writableIndexes.sorted().joinToString(" or ")}): HOLD TO SEND writes the EQ the DAC is on."

        private fun planned(profile: Profile) =
            MoondropCodec.planFor(model, profile.bands, profile.effectivePreampDb())

        override fun holds(profile: Profile): Boolean = runCatching {
            onWritableBank &&
                planned(profile).let { p ->
                    p.issues.isEmpty() &&
                        p.bands.all { e ->
                            snapshot.bands
                                .singleOrNull { it.index == e.index }
                                ?.let { sameSound(e, it) } == true
                        }
                }
        }
            .getOrDefault(false)

        override fun matches(profile: Profile): Boolean = runCatching {
            onWritableBank &&
                planned(profile).let { p ->
                    p.issues.isEmpty() &&
                        MoondropCodec.matches(p.bands, snapshot.activeIndex, snapshot.bands)
                            .isEmpty() &&
                        snapshot.preGain?.rawQ8 == preGainQ8(profile.effectivePreampDb())
                }
        }
            .getOrDefault(false)

        override fun importExact(): ImportedEq {
            require(onWritableBank) {
                "${model.productName} is on EQ index ${snapshot.activeIndex}, not one of Contour's banks (${model.writableIndexes.sorted().joinToString(", ")}): nothing to open"
            }
            val raw = snapshot.bands.sortedBy { it.index }

            val last = maxOf(0, raw.indexOfLast { it.nativeType != 0 && !isFiller(it) })
            val bands =
                raw.take(last + 1).map { r ->
                    val type = r.type
                    if (r.nativeType == 0 || type == null)
                        Band(
                            "dac${r.index}",
                            FilterType.PEAK,
                            r.frequencyHz
                                .toDouble()
                                .coerceIn(model.minFrequencyHz, model.maxFrequencyHz),
                            0.0,
                            r.q.coerceIn(model.minQ, model.maxQ),
                            enabled = false,
                        )
                    else {
                        require(!type.isPass || r.gainQ8 == 0) {
                            "Band ${r.index + 1}: ${type.name.replace('_', ' ')} has gain ${r.gainDb} dB on the DAC; Contour keeps pass filters at 0 dB"
                        }
                        Band(
                            "dac${r.index}",
                            type,
                            r.frequencyHz.toDouble(),
                            if (type.isPass) 0.0 else r.gainDb,
                            MoondropCodec.intendedQ(model, type, r.q),
                        )
                    }
                }
            return ImportedEq(
                bands,
                snapshot.preGain?.db?.takeIf { it in model.preGainMinDb..model.preGainMaxDb },
            )
        }

        override fun readings(): List<SettingReading> =
            listOfNotNull(
                SettingReading(FamilySettings.EQ_INDEX, snapshot.activeIndex, null),
                SettingReading(
                    FamilySettings.GLOBAL_GAIN,
                    (snapshot.globalGain.db * 10).roundToInt(),
                    null,
                ),
                snapshot.preGain?.let {
                    SettingReading(FamilySettings.PRE_GAIN, (it.db * 10).roundToInt(), null)
                },
            )

        override fun describe(): String =
            "${model.productName}: EQ index ${snapshot.activeIndex}" +
                (if (onWritableBank) " (Contour's bank)"
                else
                    " (the unit's own; Contour writes index ${model.writableIndexes.sorted().joinToString(" or ")})") +
                "\nglobal gain ${snapshot.globalGain.db} dB; PRE GAIN ${snapshot.preGain?.db?.let { "$it dB" } ?: "not read"}\n" +
                snapshot.bands.joinToString("\n") { b ->
                    "${b.index + 1}  ${b.type?.name?.take(2) ?: "--"} ${b.frequencyHz} Hz ${"%.2f".format(java.util.Locale.ROOT, b.gainDb)} dB Q ${"%.3f".format(java.util.Locale.ROOT, b.q)}"
                }
    }

    data class Allegro(
        val model: KtMicroCatalog.Model,
        val snapshot: KtMicroCodec.Snapshot,
        val globalGain: KtMicroCodec.RegisterValue? = null,
    ) : FamilyState {
        override val slot: Int
            get() = 0

        override val preampDb: Double?
            get() = null

        override val importable: Boolean
            get() = true

        val eqEnabled: Boolean
            get() = snapshot.eqEnabled

        val globalGainStep: Int?
            get() =
                model.globalGainUserSteps?.let { steps ->
                    globalGain?.value?.firstOrNull()?.takeIf { it in steps }
                }

        private fun planned(profile: Profile) =
            KtMicroCodec.compile(profile.copy(preampDb = 0.0), snapshot, snapshot.eqEnabled)
                .expectedBands

        override fun holds(profile: Profile): Boolean = runCatching {
            planned(profile).all { e ->
                snapshot.bands.singleOrNull { it.index == e.index }?.let { sameSound(e, it) } ==
                    true
            }
        }
            .getOrDefault(false)

        override fun matches(profile: Profile): Boolean = snapshot.eqEnabled && holds(profile)

        override fun importExact(): ImportedEq {

            val last =
                maxOf(
                    0,
                    snapshot.bands.indexOfLast {
                        if (it.nativeType == 0) !isFiller(it) else it.gainTenths != 0
                    },
                )
            val bands =
                snapshot.bands.take(last + 1).map { r ->
                    val type = KtMicroCodec.type(r.nativeType)

                    require(type in model.nativeTypes) {
                        if (type == FilterType.PEAK)
                            "Band ${r.index + 1}: the DAC holds a type Contour does not write to the ${model.productName}"
                        else
                            "Band ${r.index + 1}: the DAC holds a shelf; Contour writes ${if (model.nativeTypes.size == 1) "PEAK bands to the ${model.productName} only" else "only ${model.nativeTypes.joinToString { it.name }} to the ${model.productName}"}"
                    }
                    Band(
                        "dac${r.index}",
                        type,
                        model.intendedFrequency(r.storedFrequencyHz),
                        r.gainTenths / 10.0,
                        r.qMilli /
                            1000.0 /
                            (if (type == FilterType.PEAK) 1.0 else model.shelfQScale),
                    )
                }
            return ImportedEq(bands, 0.0)
        }

        override fun readings(): List<SettingReading> =
            listOfNotNull(
                SettingReading(FamilySettings.EQ_SWITCH, if (eqEnabled) 1 else 0, null),
                globalGainStep?.let { SettingReading(FamilySettings.GLOBAL_GAIN, it, null) },
            )

        override fun describe(): String =
            "${model.productName}: EQ ${if (eqEnabled) "ON" else "OFF"}; ${snapshot.digitalGain?.describe() ?: "digital DAC gain unknown"}" +
                (if (model.globalGainUserSteps == null) "\n"
                else
                    "; Global Gain register 0x3B ${globalGain?.value?.joinToString(" ") { "%02x".format(it) } ?: "not read"} (raw)\n") +
                snapshot.bands.joinToString("\n") { b ->
                    "${b.index + 1}  ${KtMicroCodec.type(b.nativeType).name.take(2)} ${b.storedFrequencyHz} Hz ${b.gainTenths / 10.0} dB Q ${b.qMilli / 1000.0}"
                }
    }

    data class Dusk(
        val model: MoondropGen1Catalog.Model,
        val snapshot: MoondropGen1Codec.Snapshot,
    ) : FamilyState {
        override val slot: Int
            get() = snapshot.activeIndex

        override val preampDb: Double?
            get() = null

        val onCustom: Boolean
            get() = snapshot.onCustom

        val presetBands: List<Band>?
            get() = if (onCustom) null else model.presetBands(snapshot.activeIndex)

        override val importable: Boolean
            get() = onCustom || presetBands != null

        val importName: String?
            get() =
                if (onCustom || presetBands == null) null
                else model.presetName(snapshot.activeIndex)

        val footLabel: String
            get() = model.presetName(snapshot.activeIndex).uppercase(java.util.Locale.ROOT)

        private fun planned(profile: Profile) = MoondropGen1Codec.plan(model, profile.bands)

        override fun holds(profile: Profile): Boolean = runCatching {
            if (onCustom)
                MoondropGen1Codec.issues(model, profile.bands).isEmpty() &&
                    MoondropGen1Codec.mismatches(
                            planned(profile),
                            snapshot.activeIndex,
                            snapshot.bands,
                            model.customIndex,
                        )
                        .isEmpty()
            else presetBands?.let { samePreset(it, profile.bands) } == true
        }
            .getOrDefault(false)

        override fun matches(profile: Profile): Boolean = holds(profile)

        override fun importExact(): ImportedEq {
            presetBands?.let { table ->
                return ImportedEq(table.mapIndexed { i, b -> b.copy(id = "dac$i") }, 0.0)
            }
            require(onCustom) {
                "${model.productName} plays its ${model.presetName(snapshot.activeIndex)} preset, which the record lists no bands for, so nothing is opened"
            }
            val raw = snapshot.bands.sortedBy { it.index }

            val last = maxOf(0, raw.indexOfLast { !isFiller(model, it) })
            val bands =
                raw.take(last + 1).map { r ->
                    val type =
                        r.type
                            ?: throw IllegalArgumentException(
                                "Band ${r.index}: the cable holds filter type ${r.nativeType}, which Contour does not write"
                            )
                    Band("dac${r.index - 1}", type, r.freqHz.toDouble(), r.gainDb.toDouble(), r.q)
                }
            return ImportedEq(bands, 0.0)
        }

        override fun readings(): List<SettingReading> =
            listOf(SettingReading(FamilySettings.PRESET, snapshot.activeIndex, null))

        override fun describe(): String =
            "${model.productName}: preset ${snapshot.activeIndex} (${model.presetName(snapshot.activeIndex)})" +
                (if (onCustom) ""
                else
                    "; the bands below are the preset's table, the cable cannot report what plays") +
                "\n" +
                (presetBands?.mapIndexed { i, b ->
                        "${i + 1}  ${b.type.name.take(2)} ${b.freqHz} Hz ${b.gainDb} dB Q ${"%.3f".format(java.util.Locale.ROOT, b.q)}"
                    }
                        ?: snapshot.bands.map { b ->
                            "${b.index}  ${b.type?.name?.take(2) ?: "--"} ${b.freqHz} Hz ${b.gainDb} dB Q ${"%.3f".format(java.util.Locale.ROOT, b.q)}"
                        })
                    .joinToString("\n")
    }

    data class EchoA(val profile: WalkPlayProfile, val snapshot: WalkPlaySnapshot) : FamilyState {
        override val slot: Int
            get() = snapshot.slot

        override val preampDb: Double
            get() = snapshot.preampDb.toDouble()

        override val importable: Boolean
            get() = profile.writeOn

        private val registers
            get() = snapshot.bands.map { it.registers }

        private fun planned(eq: Profile): DevicePlan.Ready? =
            ProtocolMicro.plan(eq, profile.caps) as? DevicePlan.Ready

        override fun holds(profile: Profile): Boolean =
            this.profile.writeOn &&
                planned(profile)?.let { p ->
                    p.bands.indices.all { p.bands[it].registers() == registers.getOrNull(it) }
                } == true

        override fun matches(profile: Profile): Boolean =
            this.profile.writeOn &&
                planned(profile)?.let { ProtocolMicro.matches(it, registers, snapshot.preampDb) } ==
                    true

        override fun importExact(): ImportedEq {
            check(profile.writeOn) { "${profile.name} is read only in Contour 1.6" }
            return when (
                val r = ProtocolMicro.importExact(snapshot.bands, snapshot.preampDb, profile.caps)
            ) {
                is DacImport.Ready -> r.eq
                is DacImport.Rejected -> throw IllegalStateException(r.issues.joinToString("; "))
            }
        }

        override fun readings(): List<SettingReading> = emptyList()

        override fun describe(): String =
            "${profile.name}: ${if (profile.writeOn) "write enabled" else "READ ONLY"}${if (profile.hardwareVerified) "" else " - not verified on hardware"}\n" +
                "firmware ${snapshot.firmware}, slot ${snapshot.slot}, preamp ${snapshot.preampDb} dB\n" +
                snapshot.bands.joinToString("\n") { b ->
                    val r = b.registers
                    "%d  %s %7.1f Hz %6.2f dB Q %.3f   raw f=%d q=%d g=%d t=%d"
                        .format(
                            java.util.Locale.ROOT,
                            r.index + 1,
                            b.type.name.take(2),
                            b.freqHz,
                            b.gainDb,
                            b.q,
                            r.freq,
                            r.q256,
                            r.gain256,
                            r.typeCode,
                        )
                }
    }
}

private fun preGainQ8(db: Double) = WalkPlay.toInt32(WalkPlay.jsRound(db * 256))

private fun silent(type: Int, gainQ8: Int) = type == 0 || (type == 2 && gainQ8 == 0)

private fun isFiller(r: MoondropCodec.RawBand): Boolean =
    MoondropCodec.neutralBand(r.index).let { n ->
        r.nativeType == n.nativeType &&
            r.frequencyHz == n.frequencyHz &&
            r.qQ8 == n.qQ8 &&
            r.gainQ8 == n.gainQ8
    }

private fun isFiller(r: KtMicroCodec.RawBand): Boolean =
    r.gainTenths == 0 && r.storedFrequencyHz == 100 && r.qMilli == 1000 && r.nativeType == 0

private fun isFiller(model: MoondropGen1Catalog.Model, r: MoondropGen1Codec.RawBand): Boolean =
    MoondropGen1Codec.meta(MoondropGen1Codec.neutralBand(model, r.index - 1)) == r.meta

private fun samePreset(table: List<Band>, bands: List<Band>): Boolean {
    val order = compareBy<Band>({ it.freqHz }, { it.gainDb }, { it.q })
    val mine = bands.filter { it.enabled && it.gainDb != 0.0 }.sortedWith(order)
    return table.size == mine.size &&
        table.sortedWith(order).zip(mine).all { (t, b) ->
            t.type == b.type &&
                abs(t.freqHz - b.freqHz) <= 0.5 &&
                abs(t.gainDb - b.gainDb) <= 0.01 &&
                abs(t.q - b.q) <= 0.01
        }
}

private fun sameSound(e: MoondropCodec.BandWrite, a: MoondropCodec.RawBand): Boolean =
    (silent(e.nativeType, e.gainQ8) && silent(a.nativeType, a.gainQ8)) ||
        (e.nativeType == a.nativeType &&
            e.frequencyHz == a.frequencyHz &&
            e.qQ8 == a.qQ8 &&
            e.gainQ8 == a.gainQ8)

private fun sameSound(e: KtMicroCodec.RawBand, a: KtMicroCodec.RawBand): Boolean =
    (e.gainTenths == 0 && a.gainTenths == 0) ||
        (e.nativeType == a.nativeType &&
            e.storedFrequencyHz == a.storedFrequencyHz &&
            e.qMilli == a.qMilli &&
            e.gainTenths == a.gainTenths)

object FamilyDevices {

    val READ_ONLY_LABEL: String
        get() = if (Proven.channel == BuildChannel.PERF) "READ ONLY - NOT VERIFIED" else "READ ONLY"

    fun readEchoA(port: NativePort): FamilyState.EchoA {
        val profile = WalkPlayProfiles.echoA
        return FamilyState.EchoA(profile, WalkPlaySession(profile, port).read())
    }

    fun stateIssues(target: DeviceTarget, state: FamilyState?): List<String> =
        when (target.family) {
            DeviceTarget.Family.MOONDROP ->
                if (state is FamilyState.Dawn) emptyList() else listOf(READ_FIRST)
            DeviceTarget.Family.KT_MICRO ->
                if (target.readOnly) listOf(DeviceTarget.READ_ONLY_ISSUE.format(target.caps.name))
                else if (state is FamilyState.Allegro) listOfNotNull(state.snapshot.writeBlocker)
                else listOf(READ_FIRST)
            DeviceTarget.Family.MOONDROP_GEN1 ->
                if (target.readOnly) listOf(DeviceTarget.READ_ONLY_ISSUE.format(target.caps.name))
                else if (state is FamilyState.Dusk) emptyList() else listOf(READ_FIRST)
            DeviceTarget.Family.WALKPLAY_READ_ONLY ->
                if (target.readOnly) listOf(DeviceTarget.READ_ONLY_ISSUE.format(target.caps.name))
                else if (state is FamilyState.EchoA) emptyList() else listOf(READ_FIRST)
            else -> emptyList()
        }

    const val READ_FIRST = "Read the DAC first"
}
