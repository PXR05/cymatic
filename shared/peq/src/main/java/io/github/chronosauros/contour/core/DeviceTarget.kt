// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import io.github.chronosauros.contour.core.devices.GeneratedDevices
import io.github.chronosauros.contour.core.devices.Recognition
import io.github.chronosauros.contour.core.native.*

data class DeviceTarget
private constructor(
    val walkplay: DeviceProtocol? = null,
    private val fiioRecord: FiioConfig? = null,
    val moondrop: MoondropCatalog.Model? = null,
    val ktMicro: KtMicroCatalog.Model? = null,
    val echo: WalkPlayProfile? = null,
    val gen1: MoondropGen1Catalog.Model? = null,
) {

    val fiio: FiioConfig?
        get() = fiioRecord?.let {
            if (SnowSkyMelody.isRecord(it)) SnowSkyMelody.CONFIG
            else if (FiioKa15.isRecord(it)) FiioKa15.CONFIG
            else if (FiioJa11.isRecord(it)) FiioJa11.CONFIG else it
        }

    enum class Family {
        WALKPLAY,
        FIIO,
        MOONDROP,
        KT_MICRO,
        WALKPLAY_READ_ONLY,
        MOONDROP_GEN1,
    }

    val family: Family
        get() =
            when {
                walkplay != null -> Family.WALKPLAY
                fiio != null -> Family.FIIO
                moondrop != null -> Family.MOONDROP
                ktMicro != null -> Family.KT_MICRO
                gen1 != null -> Family.MOONDROP_GEN1
                else -> Family.WALKPLAY_READ_ONLY
            }

    val isFamily: Boolean
        get() = walkplay == null && fiio == null

    val readOnly: Boolean
        get() =
            when {
                echo != null -> !echo.writeOn
                moondrop != null -> !Proven.on(moondrop.hardwareVerified)
                ktMicro != null -> !Proven.on(ktMicro.hardwareVerified)
                gen1 != null -> !Proven.on(gen1.hardwareVerified)
                fiioRecord != null -> !Proven.on(fiioRecord.hardwareVerified)
                else -> false
            }

    val hasPreamp: Boolean
        get() = ktMicro == null && gen1 == null

    val preampLabel: String
        get() = if (moondrop != null) "PRE GAIN" else "PREAMP"

    val supportsAb: Boolean
        get() = walkplay == DeviceProtocol.MICRO

    val experimental: Boolean
        get() = !supportsAb

    val stable: Boolean
        get() = walkplay != null

    val native: Boolean
        get() = walkplay == null

    val walkplayProfile: WalkPlayProfile?
        get() = echo ?: walkplay?.let { WalkPlayProfiles.of(it) }

    val settings: List<DacSetting>
        get() =
            walkplayProfile?.let { WalkPlaySettings.offered(it) }
                ?: fiio?.settings
                ?: FamilySettings.of(this)

    val info: List<DacInfo>
        get() = fiio?.info.orEmpty()

    val settingsPreview: Map<String, Int>
        get() = fiio?.settingsPreview.orEmpty()

    val preampMin: Double
        get() =
            maxOf(
                Preamp.FLOOR_DB,
                fiio?.preampMinDb
                    ?: walkplay?.preampMin?.toDouble()
                    ?: moondrop?.preGainMinDb
                    ?: echo?.protocol?.preampMin?.toDouble()
                    ?: 0.0,
            )

    val preampMax: Double
        get() =
            fiio?.preampMaxDb
                ?: walkplay?.preampMax?.toDouble()
                ?: moondrop?.preGainMaxDb
                ?: echo?.preampMaxDb?.toDouble()
                ?: 0.0

    val preampStep: Double
        get() = if (caps.preampWholeDb) 1.0 else 0.1

    fun heldPreamp(preampDb: Double): Double =
        if (preampStep == 0.1) preampDb else Preamp.floorTo(preampDb, preampStep)

    val caps: DeviceCapabilities
        get() =
            walkplay?.caps
                ?: fiio?.let { f ->
                    DeviceCapabilities(
                        f.productName,
                        f.capture?.vendorId ?: 0,
                        f.capture?.productId ?: 0,
                        f.maxFilters,
                        f.minGainDb,
                        f.maxGainDb,
                        f.minQ,
                        f.maxQ,
                        f.minFrequencyHz.toDouble(),
                        f.maxFrequencyHz.toDouble(),
                        f.typeCodes.keys,
                        f.preampWholeDb,
                        shelfQScale = f.shelfQScale,
                        peakQScale = f.peakQScale,
                    )
                }
                ?: familyCaps()

    private fun familyCaps(): DeviceCapabilities =
        when {
            moondrop != null ->
                DeviceCapabilities(
                    MOONDROP_NAME,
                    MoondropCatalog.VENDOR_ID,
                    moondrop.productIds.min(),
                    moondrop.bandCount,
                    moondrop.minGainDb,
                    moondrop.maxGainDb,
                    moondrop.minQ,
                    moondrop.maxQ,
                    moondrop.minFrequencyHz,
                    moondrop.maxFrequencyHz,
                    MOONDROP_TYPES,
                    false,
                    shelfQScale = moondrop.shelfQScale,
                )
            ktMicro != null ->
                DeviceCapabilities(
                    ktMicro.name,
                    KtMicroCatalog.VENDOR_ID,
                    ktMicro.productIds.min(),
                    ktMicro.bandCount,
                    ktMicro.minGainDb,
                    ktMicro.maxGainDb,
                    ktMicro.minQ,
                    ktMicro.maxQ,
                    ktMicro.minFrequencyHz,
                    ktMicro.maxFrequencyHz,
                    ktMicro.nativeTypes,
                    false,
                    shelfQScale = ktMicro.shelfQScale,
                    shelfLimits = ktMicro.shelfLimits,
                )
            gen1 != null -> gen1.caps()

            else -> requireNotNull(echo).caps
        }

    val destinationSlot: Int?
        get() = fiio?.userSlots?.minOrNull()

    private val preampRules: DeviceProtocol?
        get() = walkplay ?: writableEcho?.protocol

    private val writableEcho: WalkPlayProfile?
        get() = echo?.takeIf { it.writeOn }

    val usesWalkPlayRules: Boolean
        get() = preampRules != null

    fun shelfOffset(bands: List<Band>): Double = preampRules?.shelfOffset(bands) ?: 0.0

    val emulatesHighShelf: Boolean
        get() = walkplay == DeviceProtocol.MICRO || writableEcho != null

    val highShelfStepDb: Double?
        get() = if (emulatesHighShelf) preampStep else null

    fun preampFits(bands: List<Band>, preampDb: Double): Boolean =
        writableEcho?.let {
            preampDb.isFinite() && ProtocolMicro.preampFits(bands, preampDb, it.preampMaxDb)
        }
            ?: preampRules?.preampFits(bands, preampDb)
            ?: (!hasPreamp || (preampDb.isFinite() && preampDb in preampMin..preampMax))

    fun preampRefusal(bands: List<Band>, preampDb: Double): String? =
        writableEcho?.let { e ->
            if (preampFits(bands, preampDb)) null
            else if (preampDb.isFinite())
                ProtocolMicro.preampRefusal(bands, preampDb, e.preampMaxDb)
            else "Preamp $preampDb dB is not a number"
        } ?: preampRules?.preampRefusal(bands, preampDb)

    fun autoRefusal(bands: List<Band>): String? = preampRules?.autoRefusal(bands)

    fun manualForAuto(bands: List<Band>): Double? = preampRules?.manualForAuto(bands)

    fun fitImportedPreamp(bands: List<Band>, preampDb: Double): Double {
        if (!preampDb.isFinite())
            return if (preampDb.isNaN()) 0.0 else if (preampDb > 0) preampMax else preampMin
        val down = Preamp.floorTo(preampDb, preampStep)
        return if (preampFits(bands, down) == preampFits(bands, preampDb)) down else preampDb
    }

    fun shownPreamp(profile: Profile): Double =
        if (!hasPreamp) 0.0
        else
            writableEcho?.let { e ->
                ProtocolMicro.devicePreamp(profile.bands, profile.preampDb, e.preampMaxDb) -
                    ProtocolMicro.highShelfGainSum(profile.bands)
            } ?: preampRules?.shownPreamp(profile) ?: profile.effectivePreampDb()

    fun qRange(type: FilterType): ClosedFloatingPointRange<Double> = caps.qRange(type)

    fun invalidEqHeadline(profile: Profile, reasons: List<String> = issues(profile)): String {

        val curveFailure = runCatching { Dsp.responseDb(profile.bands) }.exceptionOrNull()?.message
        val issue =
            (curveFailure ?: reasons.firstOrNull())
                .orEmpty()
                .trim()
                .lowercase(java.util.Locale.ROOT)
        val band =
            Regex("""^band (\d+):""").find(issue)?.groupValues?.get(1)?.toIntOrNull()?.let {
                profile.bands.getOrNull(it - 1)
            }
                ?: if ("no finite response" in issue)
                    profile.bands.firstOrNull {
                        it.enabled &&
                            (!it.freqHz.isFinite() ||
                                it.freqHz !in caps.freqMinHz..caps.freqMaxHz ||
                                !it.gainDb.isFinite() ||
                                (!it.type.isPass && it.gainDb !in caps.gainRange(it.type)) ||
                                !it.q.isFinite() ||
                                it.q !in qRange(it.type))
                    }
                else null
        val cause =
            when {
                Regex("""^\d+ (enabled )?bands;""").containsMatchIn(issue) -> "band count"

                band == null &&
                    "no finite response" in issue &&
                    profile.bands.any {
                        it.enabled &&
                            (it.type == FilterType.LOW_SHELF || it.type == FilterType.HIGH_SHELF)
                    } -> "shelf gain or Q"
                issue.startsWith("pregain") ||
                    issue.startsWith("preamp") ||
                    issue.startsWith("auto preamp") ||
                    issue.startsWith("manual/auto attenuation") ||
                    issue.startsWith("this eq needs") -> "preamp"

                band != null &&
                    (!band.freqHz.isFinite() || band.freqHz !in caps.freqMinHz..caps.freqMaxHz) ->
                    "frequency"
                band != null &&
                    (!band.gainDb.isFinite() ||
                        (!band.type.isPass && band.gainDb !in caps.gainRange(band.type))) -> "gain"
                band != null && (!band.q.isFinite() || band.q !in qRange(band.type)) -> "Q"
                "shelf" in issue ||
                    (band?.type in setOf(FilterType.LOW_SHELF, FilterType.HIGH_SHELF) &&
                        "non-finite biquad" in issue) -> "shelf gain or Q"
                " hz outside " in issue || issue.startsWith("frequency") -> "frequency"
                " db outside " in issue -> "gain"
                Regex("""\bq\b""").containsMatchIn(issue) && "outside" in issue -> "Q"
                else -> return "INVALID EQ - EDIT BAND"
            }
        return "INVALID EQ - adjust $cause"
    }

    fun issues(profile: Profile): List<String> {
        if (walkplay != null)
            return (walkplay.plan(profile) as? DevicePlan.Rejected)?.issues.orEmpty()
        val f = fiio ?: return familyIssues(profile)
        return runCatching {
            require(f.codecBlockers.isEmpty()) { f.codecBlockers.joinToString("; ") }
            require(f.userSlots.isNotEmpty()) {
                if (Proven.channel == BuildChannel.PERF) "No proven writable user slot"
                else "This DAC has no slot Contour can write to."
            }
            require(profile.bands.size <= caps.bands) {
                "${profile.bands.size} bands; ${caps.name} accepts ${caps.bands}; no truncation"
            }

            val found = mutableListOf<String>()
            profile.bands.forEachIndexed { i, b ->
                val n = i + 1

                val scaled = caps.qScale(b.type) != 1.0
                val (qLo, qHi) = caps.qRange(b.type).let { it.start to it.endInclusive }
                if (b.type !in caps.types) found += "Band $n: type unsupported"
                if (!b.freqHz.isFinite() || b.freqHz !in caps.freqMinHz..caps.freqMaxHz)
                    found +=
                        "Band $n: freq ${num(b.freqHz)} Hz outside ${num(caps.freqMinHz)}-${num(caps.freqMaxHz)} Hz"

                if (!b.gainDb.isFinite() || (!b.type.isPass && b.gainDb !in caps.gainRange(b.type)))
                    found +=
                        "Band $n: gain ${num(b.gainDb)} dB outside ${num(caps.gainRange(b.type).start)}..${num(caps.gainRange(b.type).endInclusive)} dB"
                if (!b.q.isFinite() || b.q !in qLo..qHi)
                    found +=
                        "Band $n: ${if (!scaled) "" else if (b.type.isShelf) "shelf " else "peak "}Q %.2f outside %.2f-%.2f"
                            .format(java.util.Locale.ROOT, b.q, qLo, qHi)
            }
            val gain = profile.effectivePreampDb()
            if (!(gain.isFinite() && gain in preampMin..preampMax))
                found +=
                    "Pregain %.1f outside %.1f..%.1f dB"
                        .format(java.util.Locale.ROOT, gain, preampMin, preampMax)
            if (found.isNotEmpty()) return found
            FiioCodec(f).planOnExplicitSend(profile.copy(preampDb = gain), destinationSlot!!, true)
        }
            .exceptionOrNull()
            ?.let { listOf(it.message ?: "Unrepresentable EQ") }
            .orEmpty()
    }

    private fun familyIssues(profile: Profile): List<String> {
        if (readOnly) return listOf(READ_ONLY_ISSUE.format(caps.name))

        echo?.let {
            return (ProtocolMicro.plan(profile, it.caps, it.preampMaxDb) as? DevicePlan.Rejected)
                ?.issues
                .orEmpty()
        }
        val found = mutableListOf<String>()
        if (profile.bands.size > caps.bands)
            return listOf(
                "${profile.bands.size} bands; ${caps.name} accepts ${caps.bands}; no truncation"
            )
        profile.bands.forEachIndexed { i, b ->
            if (!b.enabled) return@forEachIndexed
            val n = i + 1
            if (b.type !in caps.types) found += "Band $n: type unsupported"
            if (!b.freqHz.isFinite() || b.freqHz !in caps.freqMinHz..caps.freqMaxHz)
                found +=
                    "Band $n: freq ${num(b.freqHz)} Hz outside ${num(caps.freqMinHz)}-${num(caps.freqMaxHz)} Hz"
            if (!b.gainDb.isFinite() || (!b.type.isPass && b.gainDb !in caps.gainRange(b.type)))
                found +=
                    "Band $n: gain ${num(b.gainDb)} dB outside ${num(caps.gainRange(b.type).start)}..${num(caps.gainRange(b.type).endInclusive)} dB"

            val qRange = caps.qRange(b.type)
            if (!b.q.isFinite() || b.q !in qRange)
                found +=
                    "Band $n: ${if (caps.qScale(b.type) != 1.0) "shelf " else ""}Q %.2f outside %.2f-%.2f"
                        .format(java.util.Locale.ROOT, b.q, qRange.start, qRange.endInclusive)
        }

        val gain =
            if (!hasPreamp && moondrop == null) null
            else
                runCatching { profile.effectivePreampDb() }
                    .getOrElse { e ->
                        if (found.isNotEmpty()) return found
                        return moondrop
                            ?.let { MoondropCodec.planFor(it, profile.bands, null).issues }
                            .orEmpty()
                            .ifEmpty { listOf(e.message ?: "Unrepresentable EQ") }
                    }
        if (hasPreamp && gain != null && !(gain.isFinite() && gain in preampMin..preampMax))
            found +=
                "Pregain %.1f outside %.1f..%.1f dB"
                    .format(java.util.Locale.ROOT, gain, preampMin, preampMax)
        if (found.isNotEmpty()) return found
        return when {
            moondrop != null -> MoondropCodec.planFor(moondrop, profile.bands, gain).issues
            gen1 != null -> MoondropGen1Codec.issues(gen1, profile.bands)
            else -> KtMicroCodec.issues(requireNotNull(ktMicro), profile.copy(preampDb = 0.0))
        }
    }

    companion object {

        val MOONDROP_NAME: String = GeneratedDevices.MOONDROP_DAWN_PRO_2.name

        val KT_NAME: String = GeneratedDevices.KIWI_EARS_ALLEGRO_MINI.name

        val MOONDROP_TYPES: Set<FilterType>
            get() = MoondropCatalog.OFFERED_TYPES

        val READ_ONLY_ISSUE: String
            get() =
                if (Proven.channel == BuildChannel.PERF)
                    "%s is read only: not verified on hardware, Contour sends nothing to it"
                else "%s is read only in Contour"

        private fun num(x: Double): String =
            if (x.isFinite() && x == Math.rint(x) && Math.abs(x) < 1e9) x.toLong().toString()
            else "%.1f".format(java.util.Locale.ROOT, x)

        private const val BLOCK_LABEL_MAX = 30
        private const val BLOCK_LABEL_GENERIC = "INVALID EQ - EDIT BAND"
        private val bandIssue = Regex("""^Band (\d+): (.*?)(?: outside .*)?$""")
        private val bandCountIssue = Regex("""^(\d+) bands; .* accepts (\d+);""")

        fun blockLabel(issues: List<String>): String {
            val issue = issues.firstOrNull()?.trim() ?: return BLOCK_LABEL_GENERIC
            if (issue == "Read the DAC first") return "READ THE DAC FIRST"
            if (issue.startsWith("Pregain") || issue.contains("preamp", ignoreCase = true))
                return "PREAMP OUT OF RANGE"
            bandCountIssue.find(issue)?.let {
                return "${it.groupValues[1]} BANDS - MAX ${it.groupValues[2]}"
            }
            val m = bandIssue.find(issue) ?: return BLOCK_LABEL_GENERIC
            val base =
                "BAND ${m.groupValues[1]}: ${m.groupValues[2].uppercase(java.util.Locale.ROOT)}"
            return when {
                "$base - EDIT BAND".length <= BLOCK_LABEL_MAX -> "$base - EDIT BAND"
                base.length <= BLOCK_LABEL_MAX -> base
                else -> BLOCK_LABEL_GENERIC
            }
        }

        val MICRO = DeviceTarget(walkplay = DeviceProtocol.MICRO)
        val MAX = DeviceTarget(walkplay = DeviceProtocol.MAX)
        val TRN = DeviceTarget(walkplay = DeviceProtocol.TRN)
        val KA15 = DeviceTarget(fiioRecord = FiioCatalog.KA15)
        val K13 = DeviceTarget(fiioRecord = FiioCatalog.K13)
        val MELODY = DeviceTarget(fiioRecord = FiioCatalog.MELODY)
        val TINY_A = DeviceTarget(fiioRecord = FiioCatalog.TINY_A)

        val FIIO_JA11 = DeviceTarget(fiioRecord = FiioCatalog.JA11)
        private val MELODY_WITH_PASS = DeviceTarget(fiioRecord = SnowSkyMelody.config(true))
        private val KA15_WITH_PASS = DeviceTarget(fiioRecord = FiioKa15.config(true))

        fun ka15(passFilters: Boolean): DeviceTarget = if (passFilters) KA15_WITH_PASS else KA15

        fun melody(passFilters: Boolean): DeviceTarget =
            if (passFilters) MELODY_WITH_PASS else MELODY

        val DAWN_PRO_2 = DeviceTarget(moondrop = MoondropCatalog.dawnPro2)
        val ALLEGRO_MINI = DeviceTarget(ktMicro = KtMicroCatalog.models.first())

        val JCALLY_JM12 = DeviceTarget(ktMicro = KtMicroCatalog.jm12Models.first())
        val ECHO_A = DeviceTarget(echo = WalkPlayProfiles.echoA)

        val MOONDROP_DUSK = DeviceTarget(gen1 = MoondropGen1Catalog.duskSp)

        internal fun forEcho(profile: WalkPlayProfile) = DeviceTarget(echo = profile)

        val FAMILIES = listOf(DAWN_PRO_2, ALLEGRO_MINI, JCALLY_JM12, ECHO_A, MOONDROP_DUSK)

        fun of(protocol: DeviceProtocol) =
            when (protocol) {
                DeviceProtocol.MICRO -> MICRO
                DeviceProtocol.MAX -> MAX
                DeviceProtocol.TRN -> TRN
            }

        private val byRecord: Map<String, DeviceTarget> =
            mapOf(
                    GeneratedDevices.CRINEAR_PROTOCOL_MICRO_USB.recordId to MICRO,
                    GeneratedDevices.CRINEAR_PROTOCOL_MAX_USB.recordId to MAX,
                    GeneratedDevices.TRN_BLACK_PEARL_USB.recordId to TRN,
                    GeneratedDevices.FIIO_KA15_USB.recordId to KA15,
                    GeneratedDevices.FIIO_K13_R2R_USB.recordId to K13,
                    GeneratedDevices.SNOWSKY_MELODY_USB.recordId to MELODY,
                    GeneratedDevices.SNOWSKY_TINY_A_USB.recordId to TINY_A,
                    GeneratedDevices.FIIO_JA11_USB.recordId to FIIO_JA11,
                    GeneratedDevices.MOONDROP_DAWN_PRO_2_USB.recordId to DAWN_PRO_2,
                    GeneratedDevices.KIWI_EARS_ALLEGRO_MINI_USB.recordId to ALLEGRO_MINI,
                    GeneratedDevices.JCALLY_JM12_USB.recordId to JCALLY_JM12,
                    GeneratedDevices.MOONDROP_ECHO_A_USB.recordId to ECHO_A,
                    GeneratedDevices.MOONDROP_DUSK_USB.recordId to MOONDROP_DUSK,
                )
                .also { m ->
                    require(m.keys == GeneratedDevices.USB_IDS.map { it.recordId }.toSet()) {
                        "a device record has no DeviceTarget (or the other way round)"
                    }
                }

        fun find(vendorId: Int, productId: Int): DeviceTarget? =
            GeneratedDevices.USB_IDS.firstOrNull {
                    it.recognition == Recognition.FIND &&
                        it.vendorId == vendorId &&
                        it.productId == productId
                }
                ?.let { byRecord.getValue(it.recordId) }

        fun findDevice(vendorId: Int, productId: Int, productName: String?): DeviceTarget? =
            find(vendorId, productId)
                ?: GeneratedDevices.USB_IDS.firstOrNull {
                        it.recognition == Recognition.FIND_DEVICE &&
                            it.matches(vendorId, productId, productName)
                    }
                    ?.let { byRecord.getValue(it.recordId) }
    }
}
