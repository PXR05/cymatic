// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.DacImport
import io.github.chronosauros.contour.core.DeviceCapabilities
import io.github.chronosauros.contour.core.DevicePlan
import io.github.chronosauros.contour.core.DeviceProtocol
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.Profile
import io.github.chronosauros.contour.core.ProtocolMicro
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.WalkPlay
import io.github.chronosauros.contour.core.devices.GeneratedDevices
import io.github.chronosauros.contour.core.devices.PendingRecords
import java.io.IOException

data class WalkPlayProfile(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val protocol: DeviceProtocol,
    val types: Set<FilterType>,
    val schemeNo: Int,
    val hardwareVerified: Boolean,
    val usbName: String? = null,
    val capsOverride: DeviceCapabilities? = null,
    val preampMaxOverride: Int? = null,
) {
    val bands: Int
        get() = protocol.caps.bands

    val caps: DeviceCapabilities
        get() = capsOverride ?: protocol.caps

    val preampMaxDb: Int
        get() =
            preampMaxOverride
                ?: if (
                    protocol == DeviceProtocol.MICRO &&
                        productId == WalkPlayProfiles.ECHO_A_PRODUCT_ID
                )
                    ProtocolMicro.ECHO_A_PREAMP_MAX_EFFECTIVE_DB
                else protocol.preampMax

    val writeOn: Boolean
        get() = Proven.on(hardwareVerified)
}

object WalkPlayProfiles {

    const val ECHO_A_PRODUCT_ID = GeneratedDevices.MOONDROP_ECHO_A_PRODUCT_ID

    const val ECHO_A_HARDWARE_VERIFIED = true

    private fun stable(id: String, protocol: DeviceProtocol) =
        protocol.data.let { d ->
            WalkPlayProfile(
                id,
                d.name,
                d.vendorId,
                d.productId,
                protocol,
                d.types,
                d.schemeNo,
                true,
                usbName = d.usbName,
            )
        }

    val micro = stable("micro", DeviceProtocol.MICRO)
    val max = stable("max", DeviceProtocol.MAX)
    val trn = stable("trn", DeviceProtocol.TRN)
    val echoA =
        GeneratedDevices.MOONDROP_ECHO_A.let { d ->
            require(
                d.bands == DeviceProtocol.MICRO.caps.bands &&
                    d.schemeNo == DeviceProtocol.MICRO.data.schemeNo &&
                    d.reportId == WalkPlay.REPORT_ID
            ) {
                "devices/moondrop-echo-a.json (bands ${d.bands}, SchemeNo ${d.schemeNo}, report ${d.reportId}) does not match the Protocol Micro code that drives it"
            }

            val caps =
                DeviceCapabilities(
                    d.name,
                    d.vendorId,
                    d.productId,
                    d.bands,
                    d.gainMinDb,
                    d.gainMaxDb,
                    d.qMin,
                    d.qMax,
                    PendingRecords.ECHO_A_FREQ_HZ.start,
                    PendingRecords.ECHO_A_FREQ_HZ.endInclusive,
                    d.types,
                    DeviceProtocol.MICRO.caps.preampWholeDb,
                    coefficientRateHz = d.coefficientRateHz,
                )
            WalkPlayProfile(
                "echo-a",
                d.name,
                d.vendorId,
                d.productId,
                DeviceProtocol.MICRO,
                d.types,
                d.schemeNo,
                ECHO_A_HARDWARE_VERIFIED,
                usbName = d.usbName,
                capsOverride = caps,
            )
        }
    val all = listOf(micro, max, trn, echoA)

    fun find(vendorId: Int, productId: Int): WalkPlayProfile? = all.singleOrNull {
        it.vendorId == vendorId && it.productId == productId
    }

    fun of(protocol: DeviceProtocol): WalkPlayProfile =
        when (protocol) {
            DeviceProtocol.MICRO -> micro
            DeviceProtocol.MAX -> max
            DeviceProtocol.TRN -> trn
        }
}

data class WalkPlaySnapshot(
    val firmware: String,
    val slot: Int,
    val bands: List<WalkPlay.DeviceBand>,
    val preampDb: Int,
)

data class WalkPlayReading(val id: String, val value: Int?)

class WalkPlaySession(val profile: WalkPlayProfile, private val port: NativePort) {
    private val io = NativeIo(port)
    private val p: DeviceProtocol
        get() = profile.protocol

    var lastPlan: DevicePlan.Ready? = null
        private set

    var lastSlot: Int? = null
        private set

    private fun full(payload: ByteArray) = byteArrayOf(WalkPlay.REPORT_ID.toByte()) + payload

    private fun payload(report: ByteArray): ByteArray {
        require(
            report.size == WalkPlay.REPORT_SIZE && (report[0].toInt() and 255) == WalkPlay.REPORT_ID
        ) {
            "Not a 64-byte WalkPlay report"
        }
        return report.copyOfRange(1, report.size)
    }

    private var context = WalkPlayGuard.Context.NORMAL

    var lastSettingBefore: Int? = null
        private set

    private fun checked(report: ByteArray): ByteArray {
        val p = payload(report)
        WalkPlayGuard.refusal(p, profile, context)?.let {
            throw IllegalArgumentException("Refused, never sent: $it")
        }
        return report
    }

    private fun send(report: ByteArray) = io.send(WalkPlay.REPORT_ID, payload(checked(report)))

    private fun request(
        report: ByteArray,
        what: String,
        matches: (ByteArray) -> Boolean,
    ): ByteArray {
        var last: IOException? = null
        checked(report)
        repeat(ATTEMPTS) {
            try {
                return full(io.request(WalkPlay.REPORT_ID, payload(report)) { matches(full(it)) })
            } catch (e: IOException) {
                if (!io.isReplyTimeout(e)) throw e
                last = e
            }
        }
        throw IOException("the DAC did not answer $what", last)
    }

    private fun bandReply(i: Int): ByteArray =
        try {
            request(WalkPlay.bandRequest(i), "band $i") { p.isBandReply(it, i) }
        } catch (e: IOException) {
            if (p != DeviceProtocol.TRN || e.message?.startsWith("the DAC did not answer") != true)
                throw e
            port.log("band $i missing: asked once more")
            request(WalkPlay.bandRequest(i), "band $i") { p.isBandReply(it, i) }
        }

    fun read(): WalkPlaySnapshot = io.guarded {
        val version =
            p.parseVersion(
                request(WalkPlay.versionRequest(), "version") {
                    p.isReply(it, WalkPlay.CMD_VERSION)
                }
            )
        val slot =
            p.parseSlot(request(WalkPlay.slotRequest(), "slot") { p.isSlotReply(it) }, profile.caps)
        val bands = (0 until profile.bands).map { i -> p.parseBand(bandReply(i), i, profile.caps) }
        val preamp =
            p.parsePreamp(
                request(WalkPlay.preampRequest(), "preamp") {
                    p.isReply(it, WalkPlay.CMD_GLOBAL_GAIN)
                }
            )
        WalkPlaySnapshot(version, slot, bands, preamp)
    }

    fun plan(eq: Profile): DevicePlan {
        if (p == DeviceProtocol.MICRO)
            return ProtocolMicro.plan(eq, profile.caps, profile.preampMaxDb)
        val plan = p.plan(eq.bands, eq.preampDb, profile.caps)
        if (plan !is DevicePlan.Ready) return rename(plan as DevicePlan.Rejected)

        val offered =
            if (profile.capsOverride == null) profile.types + p.caps.types else profile.types
        val refused =
            eq.bands
                .withIndex()
                .filter { (_, b) -> b.enabled && b.type !in offered }
                .map { (i, b) ->
                    "Band ${i + 1}: ${b.type} is not offered for ${profile.name} (${offered.sorted().joinToString()})"
                }
        return if (refused.isEmpty()) plan else DevicePlan.Rejected(refused)
    }

    private fun rename(r: DevicePlan.Rejected): DevicePlan.Rejected =
        if (profile.name == p.caps.name) r
        else
            DevicePlan.Rejected(
                r.issues.map {
                    it.replace(p.caps.name, profile.name)
                        .replace(p.caps.name.removePrefix("CrinEar Protocol "), profile.name)
                }
            )

    fun importExact(s: WalkPlaySnapshot): DacImport =
        if (p == DeviceProtocol.MICRO)
            ProtocolMicro.importExact(s.bands, s.preampDb, profile.caps, profile.preampMaxDb)
        else p.importExact(s.bands, s.preampDb, profile.caps)

    fun plannedFrames(plan: DevicePlan.Ready): Int =
        (if (p.experimental) profile.bands + 3 else 2) +
            runCatching { p.writeSequence(plan, 0).size }.getOrDefault(plan.bands.size + 8) +
            profile.bands +
            3

    fun writeTemporary(bands: List<WalkPlay.BandWrite>, slot: Int, explicitHold: Boolean) =
        io.guarded {
            require(explicitHold) { "HOLD TO SEND required" }
            require(p.supportsAb) {
                "A/B unavailable on the ${profile.name.removePrefix("CrinEar ")}: RAM-only writes are not verified"
            }
            val steps = WalkPlay.temporaryBandSequence(bands, slot)
            for (step in steps) {
                send(step.report)
                io.pause(step.delayAfterMs)
            }
        }

    fun probeRequest(report: ByteArray, command: Int): ByteArray? =
        try {
            request(report, "probe 0x%02x".format(command)) { WalkPlay.isReply(it, command) }
        } catch (e: IOException) {
            if (e.message?.startsWith("the DAC did not answer") == true) null else throw e
        }

    fun write(eq: Profile, explicitHold: Boolean): WriteOutcome<WalkPlaySnapshot> {
        val plan =
            when (val r = plan(eq)) {
                is DevicePlan.Rejected -> throw IOException(r.issues.joinToString("; "))
                is DevicePlan.Ready -> r
            }
        return writePlan(plan, explicitHold)
    }

    fun writePlan(
        plan: DevicePlan.Ready,
        explicitHold: Boolean,
        slotByte: Int? = null,
    ): WriteOutcome<WalkPlaySnapshot> = io.guarded {
        require(explicitHold) { "HOLD TO SEND required" }
        val steps0 = runCatching { p.writeSequence(plan, 0) }.getOrNull()
        requireNotNull(steps0) { "The plan does not encode" }
        val found =
            if (p.experimental) {
                require(
                    plan.bands.size == p.caps.bands &&
                        plan.bands.indices.all { plan.bands[it].index == it }
                ) {
                    "Plan does not cover every ${profile.name} slot"
                }
                read().slot
            } else {
                request(WalkPlay.versionRequest(), "version") {
                    WalkPlay.isReply(it, WalkPlay.CMD_VERSION)
                }
                WalkPlay.parseSlot(
                    request(WalkPlay.slotRequest(), "slot") {
                        WalkPlay.isReply(it, WalkPlay.CMD_PEQ)
                    }
                )
            }
        val slot = slotByte ?: found
        lastPlan = plan
        lastSlot = slot
        for (step in p.writeSequence(plan, slot)) {
            send(step.report)
            io.pause(step.delayAfterMs)
        }
        val back = read()
        val problems = mismatches(plan, back, slot)
        WriteOutcome(problems.isEmpty(), false, problems.joinToString("; ").ifEmpty { null }, back)
    }

    fun mismatches(plan: DevicePlan.Ready, back: WalkPlaySnapshot, slot: Int): List<String> {
        val out = ArrayList<String>()
        plan.bands.forEachIndexed { i, w ->
            val want = w.registers()
            val have = back.bands[i].registers
            if (want != have) out += "band ${i + 1}: wrote $want, read $have"
        }
        if (back.preampDb != plan.preampDb)
            out += "preamp: wrote ${plan.preampDb}, read ${back.preampDb}"
        if (p.experimental && back.slot != slot) out += "slot: wrote $slot, read ${back.slot}"
        return out
    }

    private fun readValue(s: DacSetting): Int? =
        try {
            if (s.id == WalkPlaySettings.BALANCE) {
                val asks = WalkPlaySettings.readRequests(s)
                val c0 =
                    request(asks[0], "${s.label} channel 0") { WalkPlaySettings.isReply(s, it, 0) }
                val c1 =
                    request(asks[1], "${s.label} channel 1") { WalkPlaySettings.isReply(s, it, 1) }
                WalkPlaySettings.balanceValue(c0, c1)
            } else {
                WalkPlaySettings.decode(
                    s,
                    request(WalkPlaySettings.readRequests(s).single(), s.label) {
                        WalkPlaySettings.isReply(s, it)
                    },
                )
            }
        } catch (e: IOException) {
            if (e.message?.startsWith("the DAC did not answer") == true) null else throw e
        }

    fun readSettings(rows: List<DacSetting> = WalkPlaySettings.of(profile)): List<WalkPlayReading> =
        io.guarded {
            rows.filter { it.read != null }.map { WalkPlayReading(it.id, readValue(it)) }
        }

    fun writeSetting(
        id: String,
        value: Int,
        explicitHold: Boolean,
        save: Boolean = false,
    ): WriteOutcome<Int> = io.guarded {
        require(explicitHold) { "HOLD TO SEND required" }
        val s = WalkPlaySettings.row(profile, id)
        val frames = WalkPlaySettings.writeReports(s, value)
        lastSettingBefore = readValue(s)
        for (f in frames) {
            send(f)
            io.pause(SETTING_GAP_MS)
        }
        if (save) {

            val before = context
            if (id == WalkPlaySettings.MIC) context = WalkPlayGuard.Context.MIC_SAVE
            try {
                send(WalkPlaySettings.saveReport())
            } finally {
                context = before
            }
            io.pause(SAVE_GAP_MS)
        }
        val back = readValue(s)
        WriteOutcome(
            back == value,
            false,
            if (back == value) null else "the DAC reads ${back ?: "nothing"}, wrote $value",
            back,
        )
    }

    fun captureForReset(explicitUserAction: Boolean, takenAtMs: Long): WalkPlayResetBackup =
        io.guarded {
            require(explicitUserAction) {
                "A backup before a reset requires an explicit user action"
            }
            require(WalkPlayReset.offered(profile)) {
                "${profile.name} has no factory reset in Contour"
            }
            val state = read()
            val readings = readSettings()
            WalkPlayResetBackup(
                profile.id,
                takenAtMs,
                state.firmware,
                state.slot,
                state.preampDb,
                state.bands.map { it.registers },
                readings.mapNotNull { r -> r.value?.let { r.id to it } }.toMap(),
                readings.filter { it.value == null }.map { it.id },
            )
        }

    fun factoryReset(ticket: WalkPlayResetTicket, log: (String) -> Unit = {}): WalkPlayResetSent {
        port.guard()
        val steps = WalkPlayReset.sequence(profile, ticket)
        context = WalkPlayGuard.Context.RESET
        var sent = 0
        try {
            for (step in steps) {
                log("reset TX ${hexOf(payload(step.report))}")
                send(step.report)
                sent++
                io.pause(step.delayAfterMs)
            }
            io.pause(WalkPlayReset.SETTLE_MS)
            return WalkPlayResetSent(sent, null)
        } catch (e: IOException) {
            if (sent == 0) throw e
            val note = "the session ended after step $sent of ${steps.size}: ${e.message}"
            log("reset: $note")
            return WalkPlayResetSent(sent, note)
        } finally {
            context = WalkPlayGuard.Context.NORMAL
        }
    }

    private fun hexOf(b: ByteArray) =
        b.copyOf(b.indexOfLast { it != 0.toByte() }.coerceAtLeast(2) + 1).joinToString(" ") {
            "%02x".format(it.toInt() and 255)
        }

    fun restoreAfterReset(
        backup: WalkPlayResetBackup,
        settingRows: List<DacSetting> = WalkPlaySettings.of(profile),
        log: (String) -> Unit = {},
    ): WalkPlayRestoreResult = io.guarded {
        require(WalkPlayReset.offered(profile)) {
            "${profile.name} has no factory reset in Contour"
        }
        backup.problems(profile).let {
            require(it.isEmpty()) { "Backup incomplete: " + it.joinToString("; ") }
        }
        val lines = ArrayList<String>()
        var ok = true
        var lost = false
        try {

            val r =
                writePlan(
                    DevicePlan.Ready(
                        backup.writes(profile.caps.coefficientRateHz),
                        backup.preampDb,
                    ),
                    explicitHold = true,
                    slotByte = backup.slot,
                )
            log(
                "reset restore: bands and preamp ${if (r.verified) "VERIFIED" else "NOT VERIFIED"}${r.reason?.let { ": $it" }.orEmpty()}"
            )
            lines +=
                if (r.verified) "Bands and preamp: restored"
                else "Bands and preamp: NOT restored: ${r.reason}"
            if (!r.verified) ok = false
        } catch (e: IOException) {
            lines += "Bands and preamp: NOT restored: ${e.message}"
            ok = false
            lost = true
        } catch (e: IllegalArgumentException) {
            lines += "Bands and preamp: NOT restored: ${e.message}"
            ok = false
        }
        val rows = settingRows.associateBy { it.id }
        for ((id, value) in backup.settings.entries.sortedBy { it.key }) {
            val row = rows[id] ?: continue
            if (lost) {
                lines += "${row.label}: not restored (the DAC session ended)"
                ok = false
                continue
            }
            if (row.writeCommand == null) {
                lines += "${row.label}: read only, not written back"
                continue
            }
            try {
                val now = readValue(row)
                if (now == value) {
                    lines += "${row.label}: unchanged"
                    continue
                }
                val back = writeSetting(id, value, explicitHold = true)
                lines +=
                    if (back.verified) "${row.label}: restored"
                    else "${row.label}: NOT restored: ${back.reason}"
                if (!back.verified) ok = false
            } catch (e: IOException) {
                lines += "${row.label}: NOT restored: ${e.message}"
                ok = false
                lost = true
            } catch (e: IllegalArgumentException) {
                lines += "${row.label}: NOT restored: ${e.message}"
                ok = false
            }
        }
        WalkPlayRestoreResult(lines, ok)
    }

    private companion object {

        const val SETTING_GAP_MS = 100L
        const val SAVE_GAP_MS = 200L

        const val ATTEMPTS = 3
    }
}
