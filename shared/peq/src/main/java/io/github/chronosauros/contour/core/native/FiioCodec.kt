// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Band
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.Profile
import io.github.chronosauros.contour.core.isPass
import io.github.chronosauros.contour.core.isShelf
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

enum class FiioEffect {
    READ_ONLY,
    SELECT_ACTIVE_USER_BANK,
    SELECT_BYPASS_SLOT,
    WRITE_ACTIVE_BANK,
    SAVE_USER_BANK,
    RENAME_USER_BANK,
    WRITE_SETTING,
    FACTORY_RESET,
}

data class FiioFrame(val reportId: Int, val bytes: List<Int>, val effect: FiioEffect) {
    fun payload(): ByteArray = bytes.map { it.toByte() }.toByteArray()
}

data class FiioRegisters(
    val index: Int,
    val frequencyHz: Int,
    val gainTenths: Int,
    val qHundredths: Int,
    val typeCode: Int,
)

data class FiioSnapshot(
    val productName: String,
    val activeSlot: Int,
    val count: Int,
    val preampTenths: Int,
    val bands: List<FiioRegisters>,
)

data class FiioExactEq(val bands: List<Band>, val preampDb: Double)

enum class FiioSaveVerification {
    POST_SAVE_READ_REQUIRED,
    RECONNECT_AND_READ_REQUIRED,
}

data class FiioWritePlan(
    val productName: String,
    val targetSlot: Int,
    val expected: FiioSnapshot,
    val selection: FiioFrame,
    val selectionVerification: FiioFrame,
    val backupQueries: List<FiioFrame>,
    val writes: List<FiioFrame>,
    val save: FiioFrame,
    val verificationQueries: List<FiioFrame>,
    val saveVerification: FiioSaveVerification,
    val delayAfterCountMs: Int = 100,
    val delayBeforeSaveMs: Int = 100,
    val delayAfterSelectMs: Int = 300,
    val delayAfterSaveMs: Int = 800,
)

sealed interface FiioVerification {
    data object RegistersMatchPersistenceUnproven : FiioVerification

    data class Pending(val reason: String) : FiioVerification

    data class Mismatch(val reason: String) : FiioVerification
}

class FiioCodec(val config: FiioConfig) {
    companion object {
        const val BAND = 0x15
        const val SLOT = 0x16
        const val PREAMP = 0x17
        const val COUNT = 0x18
        const val NAME = 0x30
        const val NAME_LENGTH = 7
        private const val NAME_REPLY_LEN = 8
        private const val NAME_REPLY_DATA = 10

        fun slotName(text: String, length: Int = NAME_LENGTH): String =
            java.text.Normalizer.normalize(
                    text.replace('ł', 'l').replace('Ł', 'L'),
                    java.text.Normalizer.Form.NFD,
                )
                .uppercase()
                .filter { it in 'A'..'Z' || it in '0'..'9' }
                .take(length)

        private const val SLOT_NAME_REPLY_LEN = 9

        private fun u(b: Byte) = b.toInt() and 255

        private fun word(p: ByteArray, offset: Int) = (u(p[offset]) shl 8) or u(p[offset + 1])

        private fun signed(p: ByteArray, offset: Int) = word(p, offset).toShort().toInt()

        private fun quantize(v: Double, scale: Int) = floor(v * scale + 0.5).toInt()

        private fun tenthsDown(v: Double) = floor(v * 10 + 1e-6).toInt()
    }

    init {
        require(config.reportId in 1..255 && config.maxFilters in 1..255)
        require(config.saveCommand == 0x19)
        require(
            config.minGainDb.isFinite() &&
                config.maxGainDb.isFinite() &&
                config.minGainDb <= config.maxGainDb
        )
        require(
            config.minQ.isFinite() &&
                config.maxQ.isFinite() &&
                config.minQ > 0 &&
                config.minQ <= config.maxQ
        )
        require(
            config.minFrequencyHz >= 1 &&
                config.maxFrequencyHz <= 65535 &&
                config.minFrequencyHz <= config.maxFrequencyHz
        )

        require(
            config.typeCodes.isNotEmpty() &&
                config.typeCodes.values.toSet().size == config.typeCodes.size
        )
        require(config.typeCodes.values.all { it in 0..255 })
        require(
            config.userSlots.all {
                it in 0..255 &&
                    it in config.declaredWritableSlots &&
                    it !in config.stockSlots &&
                    it != config.bypassSlot
            }
        )
        require(config.shelfQScale.isFinite() && config.shelfQScale > 0) { "Invalid shelf Q scale" }
        require(config.shelfQScale == 1.0 || !config.shelfAlphaCompensation) {
            "Shelf Q scale and shelf slope compensation are exclusive"
        }
        require(config.peakQScale.isFinite() && config.peakQScale > 0) { "Invalid peak Q scale" }
        require(config.peakQScale == 1.0 || !config.peakingGainCompensation) {
            "Peak Q scale and peak gain compensation are exclusive"
        }
        require(
            config.preampOffsetDb.isFinite() &&
                abs(config.preampOffsetDb * 10 - offsetTenths) < 1e-9
        ) {
            "Preamp offset must be whole 0.1 dB"
        }
    }

    private fun preampTenths(v: Double): Int =
        if (config.preampWholeDb) floor(v + 1e-6).toInt() * 10 else tenthsDown(v)

    private val offsetTenths: Int
        get() = Math.round(config.preampOffsetDb * 10).toInt()

    fun intendedPreampDb(registerTenths: Int): Double = (registerTenths - offsetTenths) / 10.0

    private var sequence = 0

    private fun frame(
        cmd: Int,
        data: List<Int>,
        effect: FiioEffect,
        forceChecksum: Boolean = false,
    ): FiioFrame {
        require(data.all { it in 0..255 })
        val header = if (effect == FiioEffect.READ_ONLY) listOf(0xBB, 0x0B) else listOf(0xAA, 0x0A)
        if (!config.checksumFrames && !forceChecksum)
            return FiioFrame(
                config.reportId,
                header + listOf(0, 0, cmd, data.size) + data + listOf(0, 0xEE),
                effect,
            )
        val body = header + listOf(0, sequence++ and 255, cmd, data.size) + data
        return FiioFrame(config.reportId, body + listOf(crc8Maxim(body), 0xEE), effect)
    }

    private fun crc8Maxim(bytes: List<Int>): Int {
        var crc = 0
        for (b in bytes) {
            crc = crc xor b
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8C else crc ushr 1 }
        }
        return crc
    }

    fun querySetting(cmd: Int): FiioFrame {
        require(cmd in 0..255 && cmd != 0x21) { "Not a settings read: $cmd" }
        return frame(cmd, emptyList(), FiioEffect.READ_ONLY, forceChecksum = true)
    }

    fun setSetting(setting: DacSetting, value: Int): FiioFrame {
        val cmd = DacWrite.check(config, setting, value)
        return frame(
            cmd,
            DacWrite.payload(setting, value),
            FiioEffect.WRITE_SETTING,
            forceChecksum = true,
        )
    }

    internal fun resetFrame(ticket: FiioResetTicket): FiioFrame {
        require(FiioReset.offered(config)) {
            "${config.productName} has no factory reset in Contour"
        }
        ticket.spend(config)
        return frame(
            FiioReset.COMMAND,
            FiioReset.DATA,
            FiioEffect.FACTORY_RESET,
            forceChecksum = true,
        )
    }

    fun settingReply(
        payload: ByteArray,
        cmd: Int,
        minData: Int,
        crcOptional: Boolean = false,
    ): ByteArray {
        require(payload.size >= 7 + minData) { "Short setting reply" }
        require(u(payload[0]) == 0xBB && u(payload[1]) == 0x0B) { "Wrong reply header/direction" }
        require(u(payload[4]) == cmd) { "Wrong command" }
        val len = u(payload[5])
        require(len >= minData && payload.size >= len + 7) { "Wrong data length" }

        if (
            crcOptional &&
                u(payload[len + 6]) == 0xEE &&
                (payload.size < len + 8 || u(payload[len + 7]) != 0xEE)
        )
            return payload.copyOf(len + 7)
        require(payload.size >= len + 8 && u(payload[len + 7]) == 0xEE) {
            "Missing reply terminator"
        }
        return payload.copyOf(len + 8)
    }

    fun querySlot() = frame(SLOT, emptyList(), FiioEffect.READ_ONLY)

    fun queryCount() = frame(COUNT, emptyList(), FiioEffect.READ_ONLY)

    fun queryPreamp() = frame(PREAMP, emptyList(), FiioEffect.READ_ONLY)

    fun queryBand(index: Int): FiioFrame {
        require(index in 0 until config.maxFilters) { "Filter index out of range" }
        return frame(BAND, listOf(index), FiioEffect.READ_ONLY)
    }

    fun activeReadHeaderQueries() = listOf(querySlot(), queryCount(), queryPreamp())

    fun activeBandQueries(count: Int): List<FiioFrame> {
        validateCount(count)
        return List(count) { queryBand(it) }
    }

    fun activeReadQueries(count: Int) =
        activeReadHeaderQueries() + activeBandQueries(count) + querySlot()

    private fun requireUserSlot(slot: Int) {
        require(config.codecBlockers.isEmpty()) { config.codecBlockers.joinToString("; ") }
        require(slot in config.userSlots) { "Not a proven writable user slot: $slot" }
    }

    fun selectUserSlot(slot: Int): FiioFrame {
        requireUserSlot(slot)
        return frame(SLOT, listOf(slot), FiioEffect.SELECT_ACTIVE_USER_BANK)
    }

    fun selectBypassSlot(): FiioFrame {
        require(config.codecBlockers.isEmpty()) { config.codecBlockers.joinToString("; ") }
        val slot = requireNotNull(config.bypassSlot) { "${config.productName} has no bypass slot" }
        return frame(SLOT, listOf(slot), FiioEffect.SELECT_BYPASS_SLOT)
    }

    private fun nameIndex(slot: Int): Int {
        require(config.userSlotNames) { "${config.productName}: USER names are not proven" }
        requireUserSlot(slot)
        return if (config.nameBySlotId) slot else config.userSlots.sorted().indexOf(slot)
    }

    private val nameReplyLen
        get() = if (config.nameBySlotId) SLOT_NAME_REPLY_LEN else NAME_REPLY_LEN

    fun queryName(slot: Int) = frame(NAME, listOf(nameIndex(slot)), FiioEffect.READ_ONLY)

    fun writeName(slot: Int, name: String): FiioFrame {
        require(name.isNotEmpty() && name == slotName(name, config.nameLength)) {
            "Unsupported USER name: $name"
        }
        val pad = List(config.nameLength - name.length) { 0 }
        return frame(
            NAME,
            listOf(nameIndex(slot)) + name.map { it.code } + pad,
            FiioEffect.RENAME_USER_BANK,
        )
    }

    fun parseName(payload: ByteArray, slot: Int): String {
        reply(payload, NAME, NAME_REPLY_DATA, nameReplyLen)
        require(u(payload[6]) == nameIndex(slot)) { "Uncorrelated USER name index" }
        return (7 until 7 + config.nameLength)
            .map { u(payload[it]) }
            .takeWhile { it != 0 }
            .filter { it in 0x20..0x7E }
            .map { it.toChar() }
            .joinToString("")
    }

    private fun validateCount(count: Int) {
        require(count in 0..config.maxFilters) { "Invalid active count: $count" }
    }

    private fun range(value: Double, min: Double, max: Double, field: String) {
        require(value.isFinite() && value in min..max) { "$field outside [$min, $max]: $value" }
    }

    private fun reply(payload: ByteArray, cmd: Int, dataLength: Int, lenField: Int = dataLength) {
        require(payload.size == dataLength + 8) { "Wrong logical reply length" }
        require(u(payload[0]) == 0xBB && u(payload[1]) == 0x0B) { "Wrong reply header/direction" }
        require(u(payload[4]) == cmd && u(payload[5]) == lenField) { "Wrong command/data length" }
        require(u(payload.last()) == 0xEE) { "Missing reply terminator" }
    }

    fun logicalReply(reportId: Int, payload: ByteArray, actualPayloadSize: Int): ByteArray {
        require(
            reportId == config.reportId &&
                payload.size == actualPayloadSize &&
                actualPayloadSize >= 8
        )
        val cmd = u(payload[4])
        val n =
            when (cmd) {
                SLOT,
                COUNT -> 1
                PREAMP -> 2
                BAND -> 8
                NAME -> NAME_REPLY_DATA
                config.saveCommand -> 1
                else -> throw IllegalArgumentException("Unsupported reply command: $cmd")
            }
        require(payload.size >= n + 8) { "Short transport payload" }
        val logical = payload.copyOf(n + 8)
        reply(logical, cmd, n, if (cmd == NAME) nameReplyLen else n)
        return logical
    }

    fun parseSlot(payload: ByteArray): Int {
        reply(payload, SLOT, 1)
        return u(payload[6])
    }

    fun parseCount(payload: ByteArray): Int {
        reply(payload, COUNT, 1)
        return u(payload[6]).also { validateCount(it) }
    }

    fun parsePreamp(payload: ByteArray): Int {
        reply(payload, PREAMP, 2)
        return signed(payload, 6).also {
            range(it / 10.0, config.minGainDb, config.maxGainDb, "Pregain")
        }
    }

    fun parseBand(payload: ByteArray, expectedIndex: Int, activeCount: Int): FiioRegisters {
        validateCount(activeCount)
        require(expectedIndex in 0 until activeCount)
        reply(payload, BAND, 8)
        require(u(payload[6]) == expectedIndex) { "Uncorrelated band index" }
        return FiioRegisters(
                expectedIndex,
                word(payload, 9),
                signed(payload, 7),
                word(payload, 11),
                u(payload[13]),
            )
            .also { validateRegisters(it, readback = true) }
    }

    fun parseSaveSlot(payload: ByteArray, expectedSlot: Int): Int {
        requireUserSlot(expectedSlot)
        reply(payload, config.saveCommand, 1)
        return u(payload[6]).also { require(it == expectedSlot) { "Save slot mismatch" } }
    }

    private fun validateRegisters(r: FiioRegisters, readback: Boolean = false) {
        require(r.index in 0 until config.maxFilters)
        require(r.typeCode in config.typeCodes.values) {
            "Unsupported native filter type ${r.typeCode}; never coerce to PK"
        }
        range(
            r.frequencyHz.toDouble(),
            config.minFrequencyHz.toDouble(),
            config.maxFrequencyHz.toDouble(),
            "Frequency",
        )
        range(r.gainTenths / 10.0, config.minGainDb, config.maxGainDb, "Gain")
        if (
            readback &&
                config.qReadbackSlack &&
                r.qHundredths in
                    minQHundredths - qSlack(minQHundredths)..maxQHundredths + qSlack(maxQHundredths)
        )
            return
        range(r.qHundredths / 100.0, config.minQ, config.maxQ, "Native Q")
    }

    private fun qSlack(qHundredths: Int) = 1 + qHundredths / 250

    private val minQHundredths: Int
        get() = Math.ceil(config.minQ * 100 - 1e-9).toInt()

    private val maxQHundredths: Int
        get() = floor(config.maxQ * 100 + 1e-9).toInt()

    private fun registersMatch(expected: FiioRegisters, actual: FiioRegisters): Boolean =
        if (!config.qReadbackSlack) expected == actual
        else
            expected.copy(qHundredths = 0) == actual.copy(qHundredths = 0) &&
                abs(expected.qHundredths - actual.qHundredths) <= qSlack(expected.qHundredths)

    fun snapshot(
        slotReply: ByteArray,
        countReply: ByteArray,
        preampReply: ByteArray,
        bandReplies: List<ByteArray>,
        closingSlotReply: ByteArray,
    ): FiioSnapshot {
        val slot = parseSlot(slotReply)
        require(slot == parseSlot(closingSlotReply)) { "Active bank changed during read" }
        return snapshotBody(slot, countReply, preampReply, bandReplies)
    }

    fun melodyUserSnapshot(
        slotReply: ByteArray,
        countReply: ByteArray,
        preampReply: ByteArray,
        bandReplies: List<ByteArray>,
    ): FiioSnapshot {
        require(FiioMelodyExchange.appliesTo(config))
        val slot = parseSlot(slotReply)
        require(slot in config.userSlots || slot in 7..9)
        return snapshotBody(slot, countReply, preampReply, bandReplies)
    }

    private fun snapshotBody(
        slot: Int,
        countReply: ByteArray,
        preampReply: ByteArray,
        bandReplies: List<ByteArray>,
    ): FiioSnapshot {
        val count = parseCount(countReply)
        require(bandReplies.size == count) { "Incomplete/extra filters" }
        val indices = bandReplies.map {
            require(it.size >= 7)
            u(it[6])
        }
        require(indices.toSet() == (0 until count).toSet() && indices.distinct().size == count) {
            "Duplicate/missing/out-of-range band indices"
        }

        val parsed = runCatching { bandReplies.map { parseBand(it, u(it[6]), count) } }
        val bands =
            (if (parsed.isFailure && slot == config.bypassSlot && count == config.maxFilters)
                    bandReplies.map {
                        reply(it, BAND, 8)
                        FiioRegisters(u(it[6]), 0, 0, 0, 0)
                    }
                else parsed.getOrThrow())
                .sortedBy { it.index }
        return FiioSnapshot(config.productName, slot, count, parsePreamp(preampReply), bands)
    }

    private fun validateSnapshot(s: FiioSnapshot) {
        require(s.productName == config.productName)
        require(s.activeSlot in 0..255)
        validateCount(s.count)
        require(
            s.bands.size == s.count && s.bands.map { it.index }.toSet() == (0 until s.count).toSet()
        )
        s.bands.forEach { validateRegisters(it, readback = true) }
        range(s.preampTenths / 10.0, config.minGainDb, config.maxGainDb, "Pregain")
    }

    private fun amplitude(gain: Double) = 10.0.pow(abs(gain) / 40.0)

    private fun nativeQ(type: FilterType, q: Double, gain: Double): Double {
        if (type == FilterType.PEAK && config.peakingGainCompensation) return q * amplitude(gain)
        if (type.isShelf && config.shelfAlphaCompensation) {
            val a = amplitude(gain)
            val radicand = (a + 1 / a) * (1 / q - 1) + 2
            require(radicand.isFinite() && radicand > 0) { "Unrepresentable shelf slope" }
            return 1 / sqrt(radicand)
        }

        return if (type.isShelf) q * config.shelfQScale
        else if (type == FilterType.PEAK) q * config.peakQScale else q
    }

    private fun intendedQ(type: FilterType, q: Double, gain: Double): Double {
        if (type == FilterType.PEAK && config.peakingGainCompensation) return q / amplitude(gain)
        if (type.isShelf && config.shelfAlphaCompensation) {
            val a = amplitude(gain)
            val denominator = 1 + (1 / (q * q) - 2) / (a + 1 / a)
            require(denominator.isFinite() && denominator > 0) { "Unsupported stored shelf slope" }
            return 1 / denominator
        }
        return if (type.isShelf) q / config.shelfQScale
        else if (type == FilterType.PEAK) q / config.peakQScale else q
    }

    private fun compileBand(band: Band, index: Int): FiioRegisters {
        val code =
            config.typeCodes[band.type]
                ?: throw IllegalArgumentException("Unsupported filter type ${band.type}")
        range(
            band.freqHz,
            config.minFrequencyHz.toDouble(),
            config.maxFrequencyHz.toDouble(),
            "Frequency",
        )

        if (!band.type.isPass) range(band.gainDb, config.minGainDb, config.maxGainDb, "Gain")
        require(band.q.isFinite() && band.q > 0) { "Invalid Q" }

        val gain = if (band.enabled && !band.type.isPass) tenthsDown(band.gainDb) else 0
        val q = nativeQ(band.type, band.q, gain / 10.0)

        require(
            q.isFinite() &&
                q >= config.minQ - 4 * Math.ulp(config.minQ) &&
                q <= config.maxQ + 4 * Math.ulp(config.maxQ)
        ) {
            if (band.type.isShelf && config.shelfQScale != 1.0)
                "Shelf Q outside %.2f..%.2f: %.2f"
                    .format(java.util.Locale.ROOT, config.shelfQMin, config.shelfQMax, band.q)
            else if (band.type == FilterType.PEAK && config.peakQScale != 1.0)
                "Peak Q outside %.2f..%.2f: %.2f"
                    .format(java.util.Locale.ROOT, config.peakQMin, config.peakQMax, band.q)
            else "Compensated native Q out of range: $q"
        }
        return FiioRegisters(index, band.freqHz.toInt(), gain, quantize(q, 100), code).also {
            validateRegisters(it)
        }
    }

    private fun pair(v: Int) = listOf((v ushr 8) and 255, v and 255)

    private fun writeBand(r: FiioRegisters) =
        frame(
            BAND,
            listOf(r.index) +
                pair(r.gainTenths) +
                pair(r.frequencyHz) +
                pair(r.qHundredths) +
                listOf(r.typeCode),
            FiioEffect.WRITE_ACTIVE_BANK,
        )

    fun planOnExplicitSend(
        profile: Profile,
        targetSlot: Int,
        explicitUserAction: Boolean,
    ): FiioWritePlan =
        planOnExplicitSend(profile.bands, profile.preampDb, targetSlot, explicitUserAction)

    fun planOnExplicitSend(
        bands: List<Band>,
        preampDb: Double?,
        targetSlot: Int,
        explicitUserAction: Boolean,
    ): FiioWritePlan {
        require(explicitUserAction) { "Mutation requires explicit user Send" }
        requireUserSlot(targetSlot)
        validateCount(bands.size)
        val preamp = requireNotNull(preampDb) { "Explicit pregain required; missing is not zero" }

        range(preamp, config.preampMinDb, config.preampMaxDb, "Pregain")
        val registers = bands.mapIndexed { index, band -> compileBand(band, index) }
        val expected =
            FiioSnapshot(
                config.productName,
                targetSlot,
                bands.size,
                preampTenths(preamp) + offsetTenths,
                registers,
            )
        val writes =
            listOf(
                frame(PREAMP, pair(expected.preampTenths), FiioEffect.WRITE_ACTIVE_BANK),
                frame(COUNT, listOf(expected.count), FiioEffect.WRITE_ACTIVE_BANK),
            ) + registers.map { writeBand(it) }
        return FiioWritePlan(
            config.productName,
            targetSlot,
            expected,
            selectUserSlot(targetSlot),
            querySlot(),
            activeReadHeaderQueries(),
            writes,
            frame(config.saveCommand, listOf(targetSlot), FiioEffect.SAVE_USER_BANK),
            activeReadQueries(expected.count),
            if (config.disconnectOnSave) FiioSaveVerification.RECONNECT_AND_READ_REQUIRED
            else FiioSaveVerification.POST_SAVE_READ_REQUIRED,
            delayAfterSaveMs = config.afterSaveMs,
        )
    }

    fun writesAfterSelection(
        plan: FiioWritePlan,
        selectedSlotReply: ByteArray,
        destinationBackup: FiioSnapshot,
    ): List<FiioFrame> {
        require(plan.productName == config.productName)
        requireUserSlot(plan.targetSlot)
        require(parseSlot(selectedSlotReply) == plan.targetSlot) { "Selection not confirmed" }
        validateSnapshot(destinationBackup)
        require(destinationBackup.activeSlot == plan.targetSlot) {
            "Backup is not the selected destination"
        }
        return plan.writes
    }

    fun padToDeviceCount(bands: List<Band>): List<Band> {
        if (!config.fixedBandCount || bands.size >= config.maxFilters) return bands
        return bands +
            List(config.maxFilters - bands.size) {
                Band("pad-${bands.size + it}", FilterType.PEAK, 100.0, 0.0, 1.0, enabled = true)
            }
    }

    fun matches(expected: FiioSnapshot, actual: FiioSnapshot): Boolean {
        validateSnapshot(expected)
        validateSnapshot(actual)
        val e = expected.copy(bands = expected.bands.sortedBy { it.index })
        val a = actual.copy(bands = actual.bands.sortedBy { it.index })
        if (!config.qReadbackSlack) return e == a
        return e.copy(bands = emptyList()) == a.copy(bands = emptyList()) &&
            e.bands.size == a.bands.size &&
            e.bands.zip(a.bands).all { (x, y) -> registersMatch(x, y) }
    }

    fun verification(
        plan: FiioWritePlan,
        postSave: FiioSnapshot?,
        reconnected: Boolean = false,
    ): FiioVerification {
        require(
            plan.productName == config.productName && plan.expected.activeSlot == plan.targetSlot
        )
        if (postSave == null)
            return FiioVerification.Pending("Complete post-save active read required")
        if (!matches(plan.expected, postSave))
            return FiioVerification.Mismatch("Slot/count/pregain/native registers differ")
        if (
            plan.saveVerification == FiioSaveVerification.RECONNECT_AND_READ_REQUIRED &&
                !reconnected
        )
            return FiioVerification.Pending(
                "Disconnect-on-save requires reconnect and complete read"
            )
        return FiioVerification.RegistersMatchPersistenceUnproven
    }

    fun importExact(snapshot: FiioSnapshot): FiioExactEq {
        validateSnapshot(snapshot)
        val bands =
            snapshot.bands
                .sortedBy { it.index }
                .map { r ->
                    val type = config.typeCodes.entries.single { it.value == r.typeCode }.key

                    require(!type.isPass || r.gainTenths == 0) {
                        "Band ${r.index + 1}: ${type.name.replace('_', ' ')} has gain ${"%+.1f".format(java.util.Locale.ROOT, r.gainTenths / 10.0)} dB on the DAC; Contour keeps pass filters at 0 dB"
                    }
                    val nativeQ =
                        if (config.qReadbackSlack)
                            r.qHundredths.coerceIn(minQHundredths, maxQHundredths)
                        else r.qHundredths
                    Band(
                        "fiio-${r.index}",
                        type,
                        r.frequencyHz.toDouble(),
                        r.gainTenths / 10.0,
                        intendedQ(type, nativeQ / 100.0, r.gainTenths / 10.0),
                        enabled = true,
                    )
                }
        val registers = snapshot.bands.sortedBy { it.index }
        val compiled = bands.mapIndexed { i, b -> compileBand(b, i) }
        require(
            compiled.size == registers.size &&
                compiled.zip(registers).all { (c, r) -> registersMatch(c, r) }
        ) {
            "Import cannot reproduce exact native registers"
        }
        val preamp = intendedPreampDb(snapshot.preampTenths)

        require(
            config.preampWholeDb || preampTenths(preamp) + offsetTenths == snapshot.preampTenths
        ) {
            "Import cannot reproduce exact native pregain"
        }
        return FiioExactEq(bands, preamp)
    }
}
