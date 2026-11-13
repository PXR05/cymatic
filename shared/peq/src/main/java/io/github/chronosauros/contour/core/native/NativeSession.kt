// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.*

interface NativePort {
    val deviceKey: String

    fun guard()

    fun pause(ms: Long)

    fun send(id: Int, kind: NativeHidReports.Kind, payload: ByteArray)

    fun request(id: Int, payload: ByteArray, matches: (ByteArray) -> Boolean): ByteArray

    fun payloadSize(id: Int, kind: NativeHidReports.Kind): Int =
        error("This port does not know the HID report descriptor")

    fun log(message: String) {}
}

sealed interface NativeState {
    val slot: Int
    val preamp: Double?

    fun importExact(): ImportedEq

    fun matches(profile: Profile): Boolean

    fun describe(): String =
        when (this) {
            is Fiio ->
                (slotDescription?.let { "$it\n" } ?: "") +
                    "${raw.count} native filters; pregain ${raw.preampTenths}/10 dB\n" +
                    raw.bands.joinToString("\n")
        }

    data class Fiio(
        val codec: FiioCodec,
        val raw: FiioSnapshot,
        val names: Map<Int, String> = emptyMap(),
    ) : NativeState {
        override val slot
            get() = raw.activeSlot

        val slotDescription: String?
            get() =
                if (!FiioMelodyExchange.appliesTo(codec.config)) null
                else
                    when (slot) {
                        in codec.config.userSlots -> "${codec.config.slotLabels[slot]} (slot $slot)"
                        in 7..9 -> "USER slot (number unknown; DAC reports $slot)"
                        in 0..6 -> "Slot unknown (stock preset or USER; DAC reports $slot)"
                        240 -> "Close EQ (slot 240)"
                        else -> "Slot unknown (DAC reports $slot)"
                    }

        override val preamp
            get() = codec.intendedPreampDb(raw.preampTenths)

        override fun importExact(): ImportedEq =
            codec.importExact(raw).let { ImportedEq(it.bands, it.preampDb) }

        override fun matches(profile: Profile): Boolean = runCatching {
            codec.matches(
                codec
                    .planOnExplicitSend(
                        profile.copy(
                            bands = codec.padToDeviceCount(profile.bands),
                            preampDb = profile.effectivePreampDb(),
                        ),
                        slot,
                        true,
                    )
                    .expected,
                raw,
            )
        }
            .getOrDefault(false)
    }
}

data class SettingReading(val id: String, val value: Int?, val note: String?)

sealed interface SettingWriteOutcome {
    data object Verified : SettingWriteOutcome

    data class Mismatch(val read: Int) : SettingWriteOutcome

    data object NoReply : SettingWriteOutcome
}

data class WriteTestResult(
    val id: String,
    val from: Int?,
    val to: Int?,
    val back: Int?,
    val restored: Int?,
    val result: String,
    val extra: String? = null,
) {
    fun line() =
        "WRITETEST $id from=${from ?: "none"} to=${to ?: "none"} back=${back ?: "none"} restored=${restored ?: "none"} result=$result"
}

data class NativeWriteResult(
    val verified: Boolean,
    val reason: String?,
    val readback: NativeState?,
    val expected: FiioSnapshot? = null,
    val pending: Boolean = false,
)

class NativeSession(val target: DeviceTarget, private val port: NativePort) {
    private val melody = FiioMelodyExchange.appliesTo(target.fiio)

    internal companion object {

        const val SELECT_SETTLE_MS = 300L

        const val LATE_READBACK_MS = 2500L

        const val FILTER_SETTLE_MS = 1000L
        const val LATE_READBACKS = 2
    }

    private fun <T> guarded(op: () -> T): T {
        port.guard()
        return op().also { port.guard() }
    }

    private fun pause(ms: Long) {
        if (ms > 0) guarded { port.pause(ms) }
    }

    private fun send(
        id: Int,
        bytes: ByteArray,
        kind: NativeHidReports.Kind = NativeHidReports.Kind.OUTPUT,
    ) {
        guarded { port.send(id, kind, bytes) }
        frameDone()
    }

    private fun request(id: Int, bytes: ByteArray, check: (ByteArray) -> Boolean): ByteArray =
        guarded {
            port.request(id, bytes, check).also {
                require(check(it)) { "Uncorrelated native reply" }
            }
        }
        .also { frameDone() }

    private var progress: ((done: Int, total: Int) -> Unit)? = null
    private var stepsDone = 0
    private var stepsTotal = 0

    private fun frameDone() {
        progress?.let {
            stepsDone++
            it(stepsDone, maxOf(stepsTotal, stepsDone + 1))
        }
    }

    private fun fiioReply(c: FiioCodec, f: FiioFrame): ByteArray {
        fun logical(p: ByteArray) = c.logicalReply(f.reportId, p, p.size)
        val raw =
            request(f.reportId, f.payload()) { p ->
                runCatching {
                    val l = logical(p)
                    require(l[4] == f.payload()[4])
                    if ((l[4].toInt() and 255) in setOf(FiioCodec.BAND, FiioCodec.NAME))
                        require(l[6] == f.payload()[6])
                }
                    .isSuccess
            }
        return logical(raw)
    }

    private fun isReplyTimeout(e: java.io.IOException) =
        e.message?.startsWith("Native request timed out") == true

    private fun <T> retryTimeouts(attempts: Int = 8, gapMs: Long = 400, op: () -> T): T {
        var last: java.io.IOException? = null
        repeat(attempts) { i ->
            try {
                return op()
            } catch (e: java.io.IOException) {
                if (!isReplyTimeout(e)) throw e
                last = e
                if (i < attempts - 1) pause(gapMs)
            }
        }
        throw requireNotNull(last)
    }

    private fun writeAndConsumeEcho(f: FiioFrame, requireEcho: Boolean = false) {
        val frame = f.payload()
        val cmd = frame[4]
        try {
            request(f.reportId, frame) { p ->
                p.size >= 6 &&
                    p[0] == 0xAA.toByte() &&
                    p[1] == 0x0A.toByte() &&
                    p[4] == cmd &&
                    (!melody || melodyEcho(frame, p))
            }
        } catch (e: java.io.IOException) {
            if (requireEcho || !isReplyTimeout(e)) throw e
        }
    }

    private fun melodyEcho(frame: ByteArray, reply: ByteArray): Boolean {

        if ((frame[4].toInt() and 255) == FiioCodec.NAME)
            return reply.size >= 18 &&
                (reply[5].toInt() and 255) == 9 &&
                reply[6] == frame[6] &&
                (7 until frame.size - 2).all { reply[it] == frame[it] } &&
                reply[17] == 0xEE.toByte()
        if (
            reply.size < frame.size ||
                reply[5] != frame[5] ||
                reply[frame.lastIndex] != 0xEE.toByte()
        )
            return false
        val band = (frame[4].toInt() and 255) == FiioCodec.BAND
        if (!(6 until frame.size - 2).all { (band && it in 11..12) || reply[it] == frame[it] })
            return false
        if (!band) return true
        fun q(bytes: ByteArray) = ((bytes[11].toInt() and 255) shl 8) or (bytes[12].toInt() and 255)
        return kotlin.math.abs(q(frame) - q(reply)) <= 1 + q(frame) / 250
    }

    private fun saveMelody(f: FiioFrame) {
        try {
            request(f.reportId, f.payload()) { p ->
                p.size >= 5 &&
                    p[0] == 0.toByte() &&
                    p[1] == 0xEE.toByte() &&
                    p[2] == 0.toByte() &&
                    p[4] == 0.toByte()
            }
        } catch (e: java.io.IOException) {
            if (!isReplyTimeout(e)) throw e
        }
    }

    private fun saveKa15(f: FiioFrame) {
        try {
            request(f.reportId, f.payload()) { p ->
                p.size >= 2 && p[0] == 0.toByte() && p[1] == 0xEE.toByte()
            }
        } catch (e: java.io.IOException) {
            if (!isReplyTimeout(e)) throw e
        }
    }

    private fun melodySlotMismatch(c: FiioCodec, expected: Int, actual: Int) =
        "${FiioMelodyExchange.name(c.config)} requested ${c.config.slotLabels[expected] ?: "USER"} (slot $expected), but DAC reports slot $actual; not verified"

    private fun readFiio(c: FiioCodec): NativeState.Fiio {

        val early = c.config.userSlotNames && !c.config.nameBySlotId
        val slot =
            (if (early) readNames(c) else emptyMap()).let { names ->
                readFiioBody(c, fiioReply(c, c.querySlot()), names)
            }
        return if (c.config.userSlotNames && !early) withNames(c, slot) else slot
    }

    private fun readNames(c: FiioCodec) =
        c.config.userSlots.sorted().associateWith { readName(c, it) }

    private fun withNames(c: FiioCodec, state: NativeState.Fiio) =
        if (c.config.userSlotNames) state.copy(names = readNames(c)) else state

    private fun readFiioBody(
        c: FiioCodec,
        slot: ByteArray,
        names: Map<Int, String> = emptyMap(),
    ): NativeState.Fiio {
        val count = fiioReply(c, c.queryCount())
        val preamp = fiioReply(c, c.queryPreamp())
        val bands = c.activeBandQueries(c.parseCount(count)).map { fiioReply(c, it) }

        val opening = c.parseSlot(slot)
        val raw =
            if (melody && (opening in c.config.userSlots || opening in 7..9))
                c.melodyUserSnapshot(slot, count, preamp, bands)
            else {
                val closing = fiioReply(c, c.querySlot())

                if (melody && c.parseSlot(closing) != opening && c.parseSlot(closing) in 7..9)
                    c.melodyUserSnapshot(closing, count, preamp, bands)
                else c.snapshot(slot, count, preamp, bands, closing)
            }
        return NativeState.Fiio(c, raw, names = names)
    }

    private fun readName(c: FiioCodec, slot: Int) =
        c.parseName(fiioReply(c, c.queryName(slot)), slot)

    private fun nameAfterSave(
        c: FiioCodec,
        back: NativeState.Fiio,
        slot: Int,
        profileName: String,
    ): Pair<NativeState.Fiio, String?> {
        val name = FiioCodec.slotName(profileName, c.config.nameLength)
        if (name.isEmpty() || back.names[slot] == name) return back to null
        return try {
            writeAndConsumeEcho(c.writeName(slot, name))
            val read = retryTimeouts { readName(c, slot) }
            back.copy(names = back.names + (slot to read)) to
                (if (read == name) null else "Slot name reads back as $read, not $name")
        } catch (e: java.io.IOException) {
            if (!isReplyTimeout(e)) throw e
            back to "Slot name not confirmed: no reply"
        } catch (e: IllegalArgumentException) {
            back to "Slot name not confirmed: ${e.message}"
        }
    }

    private fun codec(): FiioCodec = FiioCodec(requireNotNull(target.fiio) { "Not a FiiO DAC" })

    fun renameSlot(slot: Int, name: String, explicitUserAction: Boolean): String = guarded {
        require(explicitUserAction) { "Rename requires an explicit user action" }
        val c = codec()
        val frame = c.writeName(slot, name)
        retryTimeouts { readName(c, slot) }
        writeAndConsumeEcho(frame)
        retryTimeouts { readName(c, slot) }
    }

    fun readSettings(log: (String) -> Unit = {}): List<SettingReading> = guarded {
        val c = codec()
        val readable = target.settings.filter { it.read is SettingRead.FiioGet }
        fun readRank(s: DacSetting) =
            if (s.kind == SettingKind.TOGGLE || s.kind == SettingKind.CHOICE) 1 else 0

        val gap = (ProbePlan.fiioGapMs(c.config) ?: 0).toLong()
        val jobs: List<() -> SettingReading> =
            readable.sortedBy(::readRank).map { s ->
                val r = s.read as SettingRead.FiioGet;
                { readOne(c, s.id, r.command, r.replyData, r.decode, false, log) }
            } +
                target.info.map { i ->
                    { readOne(c, i.id, i.command, i.replyData, i.decode, true, log) }
                }
        jobs.mapIndexed { n, job ->
            if (n > 0) pause(gap)
            job()
        }
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 255) }

    private fun readOne(
        c: FiioCodec,
        id: String,
        cmd: Int,
        replyData: Int,
        decode: (ByteArray) -> Int?,
        crcOptional: Boolean,
        log: (String) -> Unit,
    ): SettingReading {
        val f = c.querySetting(cmd)
        val tx = f.payload()
        log("settings $id TX ${hex(tx)}")
        try {
            val rx =
                request(f.reportId, tx) { p ->
                    p.size >= 7 &&
                        (p[0].toInt() and 255) == 0xBB &&
                        (p[1].toInt() and 255) == 0x0B &&
                        (p[4].toInt() and 255) == cmd
                }
            val logical = runCatching { c.settingReply(rx, cmd, replyData, crcOptional) }
            log("settings $id RX ${hex(logical.getOrDefault(rx))}")
            val value = logical.getOrNull()?.let { runCatching { decode(it) }.getOrNull() }
            log("settings $id = ${value ?: "NO REPLY (unparsable)"}")
            return SettingReading(id, value, if (value == null) "unparsable reply" else null)
        } catch (e: java.io.IOException) {
            if (!isReplyTimeout(e)) throw e
            log("settings $id NO REPLY")
            return SettingReading(id, null, "no reply")
        }
    }

    private fun readValue(c: FiioCodec, s: DacSetting, log: (String) -> Unit): Int? {
        val r = s.read as? SettingRead.FiioGet ?: return null
        val gap = (ProbePlan.fiioGapMs(c.config) ?: 0).toLong()
        repeat(2) { n ->
            if (n > 0) pause(gap)
            readOne(c, s.id, r.command, r.replyData, r.decode, false, log).value?.let {
                return it
            }
        }
        return null
    }

    private fun writeOne(
        c: FiioCodec,
        s: DacSetting,
        value: Int,
        log: (String) -> Unit,
    ): SettingWriteOutcome {
        val frame = c.setSetting(s, value)
        val gap = (ProbePlan.fiioGapMs(c.config) ?: 0).toLong()
        val tx = frame.payload()
        val cmd = tx[4]
        pause(gap)
        log("settings ${s.id} SET TX ${hex(tx)}")
        try {
            val echo =
                request(frame.reportId, tx) { p ->
                    p.size >= 6 && p[0] == 0xAA.toByte() && p[1] == 0x0A.toByte() && p[4] == cmd
                }
            log("settings ${s.id} SET RX ${hex(echo)}")
        } catch (e: java.io.IOException) {

            if (!isReplyTimeout(e) && !s.reconnectsAfterWrite) throw e
            log("settings ${s.id} SET no echo")
        }
        pause(gap)

        if (settleBeforeReadMs(c, s) > 0) pause(settleBeforeReadMs(c, s))
        var got = readValue(c, s, log)
        var late = 0
        while (got != value && late < LATE_READBACKS) {
            late++
            log(
                "settings ${s.id} read-back ${got ?: "NO REPLY"} (wrote $value), asking again in $LATE_READBACK_MS ms ($late of $LATE_READBACKS)"
            )
            pause(LATE_READBACK_MS)
            got = readValue(c, s, log)
        }
        log("settings ${s.id} read-back ${got ?: "NO REPLY"} (wrote $value)")
        return when {
            got == null -> SettingWriteOutcome.NoReply
            got == value -> SettingWriteOutcome.Verified
            else -> SettingWriteOutcome.Mismatch(got)
        }
    }

    private fun settleBeforeReadMs(c: FiioCodec, s: DacSetting): Long =
        if (s.id == "filter" && !melody && c.config.consumeWriteEchoes) FILTER_SETTLE_MS else 0L

    fun readSetting(id: String, log: (String) -> Unit = {}): Int? = guarded {
        val c = codec()
        val s =
            target.settings.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("$id is not in the ${target.caps.name} list")
        readValue(c, s, log)
    }

    private fun back(o: SettingWriteOutcome, wrote: Int): Int? =
        when (o) {
            SettingWriteOutcome.Verified -> wrote
            is SettingWriteOutcome.Mismatch -> o.read
            SettingWriteOutcome.NoReply -> null
        }

    fun writeSetting(id: String, value: Int, log: (String) -> Unit = {}): SettingWriteOutcome =
        guarded {
            val c = codec()
            val s =
                target.settings.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("$id is not in the ${target.caps.name} list")
            writeOne(c, s, value, log)
        }

    fun sendSettingThenLeave(id: String, value: Int, log: (String) -> Unit = {}) {
        port.guard()
        val c = codec()
        val s =
            target.settings.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("$id is not in the ${target.caps.name} list")
        require(s.reconnectsAfterWrite) {
            "${s.label} does not make the DAC leave the bus: use writeSetting"
        }
        val frame = c.setSetting(s, value)
        val tx = frame.payload()
        val cmd = tx[4]
        pause((ProbePlan.fiioGapMs(c.config) ?: 0).toLong())
        log("settings ${s.id} SET TX ${hex(tx)} (the DAC leaves the bus after it)")
        try {
            request(frame.reportId, tx) { p ->
                p.size >= 6 && p[0] == 0xAA.toByte() && p[1] == 0x0A.toByte() && p[4] == cmd
            }
            log("settings ${s.id} SET echoed")
        } catch (e: java.io.IOException) {
            log(
                "settings ${s.id} SET no echo (${e.message}): the DAC may have left the bus with the frame"
            )
        }
    }

    fun writeTest(id: String, to: Int?, log: (String) -> Unit = {}): WriteTestResult = guarded {
        val c = codec()
        val s =
            target.settings.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("$id is not in the ${target.caps.name} list")
        DacWrite.checkSetting(c.config, s)
        require(s.read is SettingRead.FiioGet) { "${s.label} has no read-back" }
        fun finish(r: WriteTestResult): WriteTestResult {
            log(r.line())
            r.extra?.let(log)
            return r
        }
        val original =
            readValue(c, s, log)
                ?: return@guarded finish(WriteTestResult(id, null, to, null, null, "NO_REPLY"))

        val top =
            s.maxRule?.let { rule ->
                val src =
                    target.settings
                        .firstOrNull { it.id == rule.settingId }
                        ?.let { readValue(c, it, log) }
                s.maxFor { if (it == rule.settingId) src else null }
            } ?: s.max
        val volume =
            if (id == "volume-steps") target.settings.firstOrNull { it.id == "volume" } else null
        val volumeBefore = volume?.let { readValue(c, it, log) }
        val goal =
            to
                ?: when (s.kind) {
                    SettingKind.TOGGLE,
                    SettingKind.CHOICE -> (original + 1) % s.options.size
                    SettingKind.LEVEL -> if (original > s.min) original - 1 else original + 1
                    SettingKind.BALANCE ->
                        if (original == 0) -1 else original - Integer.signum(original)
                }
        DacWrite.checkValue(s, goal)
        if (s.kind == SettingKind.LEVEL)
            require(goal <= top) { "${s.label}: $goal is above the current top $top" }
        val first = writeOne(c, s, goal, log)
        val volumeAfterWrite = volume?.let { readValue(c, it, log) }
        val second = writeOne(c, s, original, log)
        val volumeAfterRestore = volume?.let { readValue(c, it, log) }
        val extra = volume?.let {
            "WRITETEST $id volume before=${volumeBefore ?: "none"} afterWrite=${volumeAfterWrite ?: "none"} afterRestore=${volumeAfterRestore ?: "none"}"
        }
        val backValue = back(first, goal)
        val restored = back(second, original)
        if (second != SettingWriteOutcome.Verified) {
            log(
                "WRITETEST $id RESTORE_FAILED: wrote $original back, the DAC reads ${restored ?: "nothing"}. NOTHING MORE IS SENT - check the DAC by hand"
            )
            return@guarded finish(
                WriteTestResult(id, original, goal, backValue, restored, "RESTORE_FAILED", extra)
            )
        }
        val result =
            when (first) {
                SettingWriteOutcome.Verified -> "OK"
                is SettingWriteOutcome.Mismatch -> "MISMATCH"
                SettingWriteOutcome.NoReply -> "NO_REPLY"
            }
        finish(WriteTestResult(id, original, goal, backValue, restored, result, extra))
    }

    fun probeFiio(report: ProbeReport, gapMs: Int) {
        val c = codec()
        val layer = "fiio"
        var slotBefore: Int? = null
        ProbePlan.FIIO_SEQUENCE.forEachIndexed { i, cmd ->
            if (i > 0) pause(gapMs.toLong())
            val f = ProbePlan.fiioFrame(c, cmd)
            val tx = f.payload()
            val name = "0x%02x".format(cmd)
            report.tx(layer, name, hex(tx))
            try {
                val rx =
                    request(f.reportId, tx) { p ->
                        p.size >= 7 &&
                            (p[0].toInt() and 255) == 0xBB &&
                            (p[1].toInt() and 255) == 0x0B &&
                            (p[4].toInt() and 255) == cmd
                    }
                val logical = runCatching { c.settingReply(rx, cmd, 0) }.getOrNull()
                report.rx(layer, name, logical?.let(::hex) ?: "raw ${hex(rx)}")
                if (
                    cmd == ProbePlan.FIIO_SLOT &&
                        logical != null &&
                        logical.size >= 7 &&
                        (logical[5].toInt() and 255) >= 1
                ) {
                    val slot = logical[6].toInt() and 255
                    if (i == 0) {
                        slotBefore = slot
                        report.note(layer, "slot before $slot")
                    } else
                        report.note(
                            layer,
                            "slot after $slot" +
                                if (slot == slotBefore) " (same)"
                                else
                                    " (differs from before $slotBefore; SnowSky slot registers go stale after queries)",
                        )
                }
            } catch (e: java.io.IOException) {
                if (!isReplyTimeout(e)) throw e
                report.noReply(layer, name)
            }
        }
    }

    fun read(): NativeState = guarded { codec().let { c -> retryTimeouts(5) { readFiio(c) } } }

    fun selectSlot(slot: Int, explicitUserAction: Boolean): Int = guarded {
        require(explicitUserAction) { "Switching slots requires an explicit user action" }
        val c = codec()
        selectAndConfirm(c, c.selectUserSlot(slot))
    }

    fun selectBypassSlot(explicitUserAction: Boolean): Int = guarded {
        require(explicitUserAction) { "Switching slots requires an explicit user action" }
        val c = codec()
        selectAndConfirm(c, c.selectBypassSlot())
    }

    private fun selectAndConfirm(c: FiioCodec, select: FiioFrame): Int {
        if (c.config.consumeWriteEchoes) writeAndConsumeEcho(select, requireEcho = melody)
        else send(select.reportId, select.payload())
        pause(SELECT_SETTLE_MS)
        return c.parseSlot(retryTimeouts { fiioReply(c, c.querySlot()) })
    }

    fun readSlot(slot: Int, explicitUserAction: Boolean): NativeState.Fiio = guarded {
        require(explicitUserAction) { "Reading another slot requires an explicit user action" }
        val c = codec()
        val before = if (melody) null else read() as NativeState.Fiio
        if (before?.raw?.activeSlot != slot) {
            val select = c.selectUserSlot(slot)
            if (c.config.consumeWriteEchoes) writeAndConsumeEcho(select, requireEcho = melody)
            else send(select.reportId, select.payload())
            pause(SELECT_SETTLE_MS)
        }
        val selected = retryTimeouts { fiioReply(c, c.querySlot()) }
        val selectedSlot = c.parseSlot(selected)
        require(selectedSlot == slot) {
            if (melody) melodySlotMismatch(c, slot, selectedSlot) else "FiiO slot switch failed"
        }
        if (melody) withNames(c, retryTimeouts { readFiioBody(c, selected) })
        else retryTimeouts(5) { readFiio(c) }
    }

    fun write(
        profile: Profile,
        explicitHold: Boolean,
        targetSlot: Int? = null,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): NativeWriteResult {
        port.guard()

        return writeBody(profile, explicitHold, targetSlot, onProgress).also {
            if (!it.pending) port.guard()
        }
    }

    private fun writeBody(
        profile: Profile,
        explicitHold: Boolean,
        targetSlot: Int?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): NativeWriteResult {
        require(explicitHold) { "HOLD TO SEND required" }
        require(target.issues(profile).isEmpty()) { target.issues(profile).joinToString("; ") }

        val fiio = requireNotNull(target.fiio)
        val leavesOnSave = fiio.disconnectOnSave
        val readFrames =
            3 +
                codec().padToDeviceCount(profile.bands).size +
                (if (melody) 0 else 1) +
                (if (fiio.userSlotNames) fiio.userSlots.size else 0)
        progress = onProgress
        stepsDone = 0
        stepsTotal =
            readFrames +
                (2 + readFrames) +
                (2 + codec().padToDeviceCount(profile.bands).size) +
                1 +
                (if (leavesOnSave) 0 else readFrames + (if (fiio.userSlotNames) 2 else 0))
        onProgress?.invoke(0, stepsTotal)
        return try {
            when (val before = read()) {
                is NativeState.Fiio -> {
                    val c = before.codec
                    val p =
                        c.planOnExplicitSend(
                            profile.copy(
                                bands = c.padToDeviceCount(profile.bands),
                                preampDb = profile.effectivePreampDb(),
                            ),
                            targetSlot ?: target.destinationSlot!!,
                            true,
                        )

                    val alreadyActive = !melody && before.raw.activeSlot == p.targetSlot
                    if (alreadyActive) stepsTotal -= 2 + readFrames
                    if (!alreadyActive) {

                        if (c.config.consumeWriteEchoes)
                            writeAndConsumeEcho(p.selection, requireEcho = melody)
                        else send(p.selection.reportId, p.selection.payload())
                        pause(p.delayAfterSelectMs.toLong())
                    }
                    val selected = retryTimeouts { fiioReply(c, p.selectionVerification) }
                    val selectedSlot = c.parseSlot(selected)
                    require(selectedSlot == p.targetSlot) {
                        if (melody) melodySlotMismatch(c, p.targetSlot, selectedSlot)
                        else "FiiO destination selection failed"
                    }
                    val backup =
                        if (melody) retryTimeouts { readFiioBody(c, selected) }
                        else if (alreadyActive) before else retryTimeouts { readFiio(c) }
                    val writes = c.writesAfterSelection(p, selected, backup.raw)
                    writes.forEach {
                        if (c.config.consumeWriteEchoes) writeAndConsumeEcho(it)
                        else send(it.reportId, it.payload())
                        if ((it.payload()[4].toInt() and 255) == FiioCodec.COUNT)
                            pause(p.delayAfterCountMs.toLong())
                        pause(c.config.writeGapMs.toLong())
                    }
                    pause(p.delayBeforeSaveMs.toLong())
                    if (leavesOnSave) {

                        port.guard()

                        try {
                            port.send(
                                p.save.reportId,
                                NativeHidReports.Kind.OUTPUT,
                                p.save.payload(),
                            )
                        } catch (e: java.io.IOException) {
                            port.log(
                                "SAVE: the session ended with the frame (the DAC left the bus): ${e.message}"
                            )
                        }
                        frameDone()
                        return NativeWriteResult(
                            false,
                            null,
                            null,
                            expected = p.expected,
                            pending = true,
                        )
                    }
                    if (melody) saveMelody(p.save)
                    else if (c.config.consumeWriteEchoes) saveKa15(p.save)
                    else send(p.save.reportId, p.save.payload())
                    pause(p.delayAfterSaveMs.toLong())
                    val back =
                        if (melody) {

                            val saved = retryTimeouts { fiioReply(c, c.querySlot()) }
                            val savedSlot = c.parseSlot(saved)
                            require(savedSlot == p.targetSlot) {
                                melodySlotMismatch(c, p.targetSlot, savedSlot)
                            }

                            retryTimeouts { readFiioBody(c, saved) }.copy(names = before.names)
                        } else retryTimeouts { readFiio(c) }
                    val ok = c.matches(p.expected, back.raw)
                    if (!ok) NativeWriteResult(false, "FiiO native register mismatch", back)
                    else if (!c.config.userSlotNames) NativeWriteResult(true, null, back)
                    else
                        nameAfterSave(c, back, p.targetSlot, profile.name).let { (named, note) ->
                            NativeWriteResult(true, note, named)
                        }
                }
            }
        } finally {
            progress = null
        }
    }

    fun captureForReset(
        explicitUserAction: Boolean,
        takenAtMs: Long,
        log: (String) -> Unit = {},
    ): FiioResetBackup = guarded {
        require(explicitUserAction) { "A backup before a reset requires an explicit user action" }
        val c = codec()
        require(FiioReset.offered(c.config)) {
            "${c.config.productName} has no factory reset in Contour"
        }
        val first = retryTimeouts(5) { readFiio(c) }
        val slots =
            c.config.userSlots.sorted().map { slot ->
                log("reset backup: reading slot $slot")
                val state = readSlot(slot, explicitUserAction = true)
                FiioSlotBackup(slot, state.names[slot], state.raw.copy(activeSlot = slot))
            }
        val known = target.settings.map { it.id }.toSet()
        val readings = readSettings(log).filter { it.id in known }
        val settings = readings.mapNotNull { r -> r.value?.let { r.id to it } }.toMap()
        val back = first.slot.takeIf { it in c.config.userSlots }
        if (back != null)
            try {
                selectSlot(back, explicitUserAction = true)
            } catch (e: java.io.IOException) {
                if (!isReplyTimeout(e)) throw e
                log("reset backup: the DAC did not confirm the return to slot $back")
            }
        FiioResetBackup(
            c.config.productName,
            takenAtMs,
            first.slot,
            slots,
            settings,
            readings.filter { it.value == null }.map { it.id },
        )
    }

    fun factoryReset(ticket: FiioResetTicket, log: (String) -> Unit = {}): FiioResetSent {
        port.guard()
        val c = codec()
        val frame = c.resetFrame(ticket)
        val tx = frame.payload()
        log("reset TX ${hex(tx)}")
        var other: ByteArray? = null
        return try {
            val echo =
                port.request(frame.reportId, tx) { p ->
                    (p.size >= 6 && p[0] == 0xAA.toByte() && p[1] == 0x0A.toByte() && p[4] == tx[4])
                        .also { if (!it && other == null) other = p.copyOf(minOf(p.size, 4)) }
                }
            log("reset RX ${hex(echo)}")
            FiioResetSent(true, null)
        } catch (e: java.io.IOException) {
            val answer = other
            if (answer != null && isReplyTimeout(e)) log("reset RX (not an echo) ${hex(answer)}")
            val note =
                if (!isReplyTimeout(e)) "the session ended after the reset frame: ${e.message}"
                else if (answer != null)
                    "the DAC answered the reset frame, but not with an echo of it (it may be restarting)"
                else "no answer to the reset frame (the DAC may be restarting)"
            log("reset: $note")
            FiioResetSent(false, note)
        }
    }

    fun restoreAfterReset(backup: FiioResetBackup, log: (String) -> Unit = {}): FiioRestoreResult =
        guarded {
            val c = codec()
            require(FiioReset.offered(c.config)) {
                "${c.config.productName} has no factory reset in Contour"
            }
            backup.problems(c.config).let {
                require(it.isEmpty()) { "Backup incomplete: " + it.joinToString("; ") }
            }
            val lines = ArrayList<String>()
            var ok = true
            var lost = false
            for (sb in backup.slots.sortedBy { it.slot }) {
                val label = c.config.slotLabels[sb.slot] ?: "slot ${sb.slot}"
                if (lost) {
                    lines += "$label: not restored (the DAC session ended)"
                    ok = false
                    continue
                }
                try {
                    val eq = c.importExact(sb.snapshot)
                    val profile =
                        Profile(
                            "restore-${sb.slot}",
                            sb.name.orEmpty(),
                            bands = eq.bands,
                            preampDb = eq.preampDb,
                            createdAt = 0,
                            updatedAt = 0,
                        )
                    val r = write(profile, explicitHold = true, targetSlot = sb.slot)
                    log(
                        "reset restore: $label ${if (r.verified) "VERIFIED" else "NOT VERIFIED"}${r.reason?.let { ": $it" }.orEmpty()}"
                    )
                    lines +=
                        if (r.verified) "$label: restored" + (r.reason?.let { " ($it)" } ?: "")
                        else "$label: NOT restored: ${r.reason}"
                    if (!r.verified) ok = false
                } catch (e: java.io.IOException) {
                    lines += "$label: NOT restored: ${e.message}"
                    ok = false
                    lost = true
                } catch (e: IllegalArgumentException) {
                    lines += "$label: NOT restored: ${e.message}"
                    ok = false
                }
            }
            val byId = target.settings.associateBy { it.id }
            for ((id, value) in
                backup.settings.entries.sortedBy { FiioReset.restoreRank(it.key) }) {
                val s = byId[id] ?: continue
                val label = s.label
                if (lost) {
                    lines += "$label: not restored (the DAC session ended)"
                    ok = false
                    continue
                }
                if (!s.writeEnabled) continue
                try {
                    val now = readValue(c, s, log)
                    if (now == value) {
                        lines += "$label: unchanged"
                        continue
                    }
                    when (val o = writeOne(c, s, value, log)) {
                        SettingWriteOutcome.Verified -> lines += "$label: restored"
                        is SettingWriteOutcome.Mismatch -> {
                            lines += "$label: NOT restored: the DAC reads ${o.read}, saved $value"
                            ok = false
                        }
                        SettingWriteOutcome.NoReply -> {
                            lines += "$label: NOT restored: no reply"
                            ok = false
                        }
                    }
                } catch (e: java.io.IOException) {
                    lines += "$label: NOT restored: ${e.message}"
                    ok = false
                    lost = true
                } catch (e: IllegalArgumentException) {
                    lines += "$label: NOT restored: ${e.message}"
                    ok = false
                }
            }
            val slot = backup.activeSlot
            if (!lost && slot != null && slot in c.config.userSlots) {
                try {
                    val now = selectSlot(slot, explicitUserAction = true)
                    lines +=
                        if (now == slot)
                            "The DAC is back on ${c.config.slotLabels[slot] ?: "slot $slot"}"
                        else
                            "The DAC reports slot $now, not ${c.config.slotLabels[slot] ?: "slot $slot"}"
                } catch (e: java.io.IOException) {
                    lines += "The DAC did not confirm the return to slot $slot: ${e.message}"
                }
            } else if (slot != null && slot !in c.config.userSlots)
                lines +=
                    "The DAC was on a stock preset or Close EQ before the reset: choose it on the DAC"
            FiioRestoreResult(lines, ok)
        }
}
