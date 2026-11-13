// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.BuildChannel
import io.github.chronosauros.contour.core.Profile
import io.github.chronosauros.contour.core.Proven

object KtMicroSaveWait {
    const val SILENT_READS = 5

    fun silentVerdict(failedReads: Int): String? =
        if (failedReads >= SILENT_READS)
            "the DAC is back on USB but does not answer; unplug it, plug it back in, then tap to read it"
        else null
}

class KtMicroSession(val model: KtMicroCatalog.Model, private val port: NativePort) {
    private val io = NativeIo(port)

    var lastPlan: KtMicroCodec.Plan? = null
        private set

    private fun reply(f: KtMicroCodec.Frame, command: Int = KtMicroCodec.READ): KtMicroCodec.Reply {
        val reg = f.payload[0].toInt() and 255
        val bytes =
            io.request(f.reportId, f.payload) { p ->
                runCatching {
                    KtMicroCodec.parseRegister(
                        KtMicroCodec.Reply(port.deviceKey, f.reportId, p),
                        port.deviceKey,
                        reg,
                        command,
                    )
                }
                    .isSuccess
            }
        return KtMicroCodec.Reply(port.deviceKey, f.reportId, bytes)
    }

    fun read(): KtMicroCodec.Snapshot = io.guarded {
        val replies = KtMicroCodec.readQueries(model).map { reply(it) }
        val required = KtMicroCodec.snapshot(model, port.deviceKey, replies)
        val enable = model.registers.enableRegister
        val closing =
            KtMicroCodec.parseRegister(
                reply(KtMicroCodec.readRegister(enable)),
                port.deviceKey,
                enable,
            )
        require(closing == required.registers.single { it.register == enable }) {
            "KT EQ enable changed during read"
        }

        val flags = optionalFlags()
        val s = KtMicroCodec.snapshot(model, port.deviceKey, replies + listOfNotNull(flags))
        if (s.digitalGain == null)
            port.log("KT channel layout unknown: no valid 0x01 reply; digital DAC gain unknown.")
        s
    }

    private fun optionalFlags(): KtMicroCodec.Reply? =
        try {
            reply(KtMicroCodec.readRegister(KtMicroCodec.CHANNEL_FLAGS_REGISTER))
        } catch (e: java.io.IOException) {
            port.guard()
            null
        } catch (e: IllegalArgumentException) {
            port.guard()
            null
        }

    fun plan(
        profile: Profile,
        before: KtMicroCodec.Snapshot,
        eqEnabled: Boolean? = true,
    ): KtMicroCodec.Plan = KtMicroCodec.compile(profile, before, eqEnabled)

    fun write(
        profile: Profile,
        explicitHold: Boolean,
        eqEnabled: Boolean? = true,
    ): WriteOutcome<KtMicroCodec.Snapshot> {
        port.guard()
        require(explicitHold) { "HOLD TO SEND required" }
        return run(plan(profile, read(), eqEnabled).also { lastPlan = it })
    }

    fun writeSwitch(
        eqEnabled: Boolean,
        explicitHold: Boolean,
    ): WriteOutcome<KtMicroCodec.Snapshot> {
        port.guard()
        require(explicitHold) { "An explicit user action is required" }
        return run(KtMicroCodec.compileSwitch(read(), eqEnabled).also { lastPlan = it })
    }

    var lastGainBefore: KtMicroCodec.RegisterValue? = null
        private set

    fun writeGlobalGain(
        step: Int,
        explicitHold: Boolean,
        save: Boolean = true,
    ): WriteOutcome<KtMicroCodec.Snapshot> {
        port.guard()
        require(explicitHold) { "An explicit user action is required" }
        require(model.globalGainUserSteps != null) {
            "${model.productName} offers no Global Gain: register 0x3B is never written for it"
        }
        KtMicroCodec.requireGlobalGainStep(step)
        val before = read()
        val current =
            readGlobalGain()
                ?: throw java.io.IOException(
                    "Global Gain (0x3B) gave no valid reply; nothing written"
                )
        lastGainBefore = current
        val plan =
            KtMicroCodec.compileGlobalGain(before, current, step, save).also { lastPlan = it }
        if (save) return run(plan)
        plan.frames.forEach { f ->
            val reg = f.payload[0].toInt() and 255
            val r =
                try {
                    reply(f, KtMicroCodec.WRITE)
                } catch (e: java.io.IOException) {
                    if (!io.isReplyTimeout(e)) throw e
                    throw java.io.IOException(
                        "KT write acknowledgement missing at 0x${reg.toString(16)}; send stopped",
                        e,
                    )
                }
            KtMicroCodec.checkWriteAck(r, port.deviceKey, reg, plan.model.registers)
            io.pause(f.delayAfterMs.toLong())
        }
        val back = read()
        val gain = readGlobalGain()
        val problems = KtMicroCodec.mismatches(plan, back, gain)
        return WriteOutcome(
            problems.isEmpty(),
            false,
            problems.joinToString("; ").ifEmpty { null },
            back,
        )
    }

    fun readGlobalGain(): KtMicroCodec.RegisterValue? = io.guarded {
        val reg = KtMicroCodec.GLOBAL_GAIN_REGISTER
        try {
            KtMicroCodec.parseRegister(reply(KtMicroCodec.readRegister(reg)), port.deviceKey, reg)
        } catch (e: java.io.IOException) {
            port.guard()
            if (!io.isReplyTimeout(e)) throw e
            null
        } catch (e: IllegalArgumentException) {
            port.guard()
            null
        }
    }

    fun captureForReset(explicitUserAction: Boolean, takenAtMs: Long): KtMicroResetBackup =
        io.guarded {
            require(explicitUserAction) {
                "A backup before a reset requires an explicit user action"
            }
            val state = read()
            KtMicroResetBackup(takenAtMs, state, readGlobalGain())
        }

    fun factoryClear(ticket: KtMicroResetTicket, log: (String) -> Unit = {}): KtMicroResetSent {
        require(model.resetOffered) {
            "${model.productName} has no reset in Contour: CLEAR (0x43) is never sent to it"
        }
        port.guard()
        val frame = KtMicroCodec.compileClear(ticket)
        var acknowledged = false
        var note: String? = null
        try {
            val reply = io.request(frame.reportId, frame.payload) { KtMicroCodec.isClearReply(it) }
            acknowledged = KtMicroCodec.clearAck(reply, model.registers)
            if (!acknowledged)
                note =
                    "CLEAR was echoed with byte 6 = ${reply[6].toInt() and 255}, not ${model.registers.writeAck}: whether it ran is for the read after to say"
        } catch (e: java.io.IOException) {
            if (!io.isReplyTimeout(e)) throw e
            note =
                "no acknowledgement of CLEAR (the DAC may have restarted or left the bus): whether it ran is for the read after to say"
        }
        log("reset: CLEAR sent" + (note?.let { ", $it" } ?: ", acknowledged"))
        io.pause(KtMicroReset.SETTLE_MS)
        return KtMicroResetSent(acknowledged, note)
    }

    fun restoreAfterReset(
        backup: KtMicroResetBackup,
        after: KtMicroResetBackup,
        log: (String) -> Unit = {},
    ): KtMicroRestoreResult {
        port.guard()
        backup.problems(model).let {
            require(it.isEmpty()) { "Backup incomplete: " + it.joinToString("; ") }
        }
        val lines = ArrayList<String>()
        var ok = true
        var lost = false
        val plan = KtMicroCodec.compileRestore(backup.snapshot, after.snapshot)
        if (plan == null) lines += "EQ switch and bands: unchanged by the reset, nothing written"
        else
            try {
                lastPlan = plan
                run(plan)
                io.pause(KtMicroReset.SETTLE_MS)
                val problems = KtMicroCodec.mismatches(plan, read())
                log(
                    "reset restore: EQ switch and bands ${if (problems.isEmpty()) "VERIFIED" else "NOT VERIFIED"}"
                )
                lines +=
                    if (problems.isEmpty()) "EQ switch and bands: restored"
                    else "EQ switch and bands: NOT restored: ${problems.joinToString("; ")}"
                if (problems.isNotEmpty()) ok = false
            } catch (e: java.io.IOException) {
                lines += "EQ switch and bands: NOT restored: ${e.message}"
                ok = false
                lost = true
            } catch (e: IllegalArgumentException) {
                lines += "EQ switch and bands: NOT restored: ${e.message}"
                ok = false
            }
        val wanted = requireNotNull(backup.globalGain)
        if (lost) {
            lines += "Global Gain: not restored (the DAC session ended)"
            ok = false
        } else if (after.globalGain?.value == wanted.value)
            lines += "Global Gain: unchanged by the reset, nothing written"
        else
            try {
                val step = requireNotNull(KtMicroCodec.globalGainStep(wanted))
                writeGlobalGain(step, explicitHold = true, save = true)
                io.pause(KtMicroReset.SETTLE_MS)
                val problems =
                    KtMicroCodec.mismatches(requireNotNull(lastPlan), read(), readGlobalGain())
                log(
                    "reset restore: Global Gain ${if (problems.isEmpty()) "VERIFIED" else "NOT VERIFIED"}"
                )
                lines +=
                    if (problems.isEmpty()) "Global Gain: restored"
                    else "Global Gain: NOT restored: ${problems.joinToString("; ")}"
                if (problems.isNotEmpty()) ok = false
            } catch (e: java.io.IOException) {
                lines += "Global Gain: NOT restored: ${e.message}"
                ok = false
            } catch (e: IllegalArgumentException) {
                lines += "Global Gain: NOT restored: ${e.message}"
                ok = false
            }
        return KtMicroRestoreResult(lines, ok)
    }

    private fun run(p: KtMicroCodec.Plan): WriteOutcome<KtMicroCodec.Snapshot> {
        p.frames.forEach { f ->
            if (f.expectsReply) {
                val reg = f.payload[0].toInt() and 255
                val r =
                    try {
                        reply(f, KtMicroCodec.WRITE)
                    } catch (e: java.io.IOException) {
                        if (!io.isReplyTimeout(e)) throw e
                        throw java.io.IOException(
                            "KT write acknowledgement missing at 0x${reg.toString(16)}; send stopped",
                            e,
                        )
                    }
                KtMicroCodec.checkWriteAck(r, port.deviceKey, reg, p.model.registers)
            } else if (
                p.reconnectAfterSave && (f.payload[4].toInt() and 255) == KtMicroCodec.SAVE
            ) {

                port.send(f.reportId, NativeHidReports.Kind.OUTPUT, f.payload)
                return@forEach
            } else io.send(f.reportId, f.payload)
            io.pause(f.delayAfterMs.toLong())
        }
        return WriteOutcome(
            false,
            true,
            if (Proven.channel == BuildChannel.PERF)
                "Save sent; the DAC reconnects: read it again after it is back and compare (not verified)"
            else "Saved. The DAC restarts - Contour reads it again when it is back.",
            null,
        )
    }
}
