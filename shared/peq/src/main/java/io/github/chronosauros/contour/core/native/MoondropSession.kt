// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.WalkPlay

class MoondropSession(val model: MoondropCatalog.Model, private val port: NativePort) {
    private val io = NativeIo(port)

    private fun reply(
        frame: MoondropCodec.Frame,
        inputBytes: Int,
        bank: () -> Int?,
    ): MoondropCodec.Reply {
        val bytes =
            io.request(frame.reportId, frame.payload) { p ->
                runCatching {
                    val r = MoondropCodec.Reply(port.deviceKey, frame.reportId, p)
                    when (frame.kind) {
                        MoondropCodec.Kind.SLOT ->
                            MoondropCodec.parseSlot(r, port.deviceKey, inputBytes)
                        MoondropCodec.Kind.BAND ->
                            MoondropCodec.parseBand(
                                r,
                                port.deviceKey,
                                model,
                                requireNotNull(frame.index),
                                requireNotNull(bank()),
                                inputBytes,
                            )
                        MoondropCodec.Kind.GLOBAL_GAIN ->
                            MoondropCodec.parseGain(
                                r,
                                port.deviceKey,
                                inputBytes,
                                MoondropCodec.CMD_GLOBAL_GAIN,
                            )
                        MoondropCodec.Kind.PRE_GAIN ->
                            MoondropCodec.parseGain(
                                r,
                                port.deviceKey,
                                inputBytes,
                                MoondropCodec.CMD_PRE_GAIN,
                            )
                        MoondropCodec.Kind.VERSION ->
                            MoondropCodec.parseVersion(r, port.deviceKey, inputBytes)
                        else -> error("Not an automatic read query")
                    }
                }
                    .isSuccess
            }
        return MoondropCodec.Reply(port.deviceKey, frame.reportId, bytes)
    }

    private fun inputBytes(): Int {
        val inputBytes = port.payloadSize(model.reportId, NativeHidReports.Kind.INPUT)
        val frames = MoondropCodec.readQueries(model)
        require(
            inputBytes >= 36 &&
                port.payloadSize(model.reportId, NativeHidReports.Kind.OUTPUT) >=
                    frames.maxOf { it.minimumDescriptorOutputPayloadBytes }
        ) {
            "The HID report descriptor lacks the complete Moondrop report 0x4B"
        }
        return inputBytes
    }

    fun read(withPreGain: Boolean = false): MoondropCodec.Snapshot = io.guarded {
        val inBytes = inputBytes()
        var slot: Int? = null
        val replies =
            MoondropCodec.readQueries(model).map { frame ->
                reply(frame, inBytes) { slot }
                    .also {
                        if (slot == null)
                            slot = MoondropCodec.parseSlot(it, port.deviceKey, inBytes)
                    }
            }
        val pre =
            if (!withPreGain) null
            else
                try {
                    reply(MoondropCodec.preGainQuery(), inBytes) { slot }
                } catch (e: java.io.IOException) {
                    port.guard()
                    if (!io.isReplyTimeout(e)) throw e
                    port.log("Pre-gain (0x23) read: no valid reply; pre-gain unknown")
                    null
                }
        MoondropCodec.snapshot(model, port.deviceKey, replies, inBytes, pre)
    }

    fun selectIndex(index: Int, explicitUserAction: Boolean, persist: Boolean = true): Int =
        io.guarded {
            require(explicitUserAction) {
                "Switching the EQ index requires an explicit user action"
            }
            require(index in 0..255) { "EQ index $index out of range" }
            MoondropCodec.selectSteps(index, persist).forEach {
                io.send(model.reportId, it.payload)
                io.pause(it.delayAfterMs)
            }
            confirmedIndex()
        }

    private fun confirmedIndex(): Int {
        val inBytes = inputBytes()
        return MoondropCodec.parseSlot(
            io.retryTimeouts { reply(MoondropCodec.slotQuery(), inBytes) { null } },
            port.deviceKey,
            inBytes,
        )
    }

    fun readFirmware(): MoondropCodec.Firmware = io.guarded {
        val inBytes = inputBytes()
        val frame = MoondropCodec.versionQuery()
        MoondropCodec.requireSendable(frame.payload)
        val r = io.retryTimeouts { reply(frame, inBytes) { null } }
        MoondropCodec.parseVersion(r, port.deviceKey, inBytes)
    }

    class EnableAllPeqResult(val before: MoondropCodec.Snapshot, val after: MoondropCodec.Snapshot)

    fun enableAllPeq(
        index: Int,
        persist: Boolean,
        explicitUserAction: Boolean,
    ): EnableAllPeqResult = io.guarded {
        require(explicitUserAction) { "An explicit user action is required" }
        val steps = MoondropCodec.enableAllPeqSteps(index, persist)
        steps.forEach { MoondropCodec.requireSendable(it.payload, enableIndex = index) }
        val before = read().also { lastBefore = it }
        steps.forEach {
            io.send(model.reportId, it.payload)
            io.pause(it.delayAfterMs)
        }
        EnableAllPeqResult(before, read())
    }

    fun readWritableBank(
        explicitUserAction: Boolean,
        withPreGain: Boolean = false,
    ): MoondropCodec.Snapshot = io.guarded {
        require(explicitUserAction) { "Reading another EQ index requires an explicit user action" }
        val now = read().also { lastBefore = it }
        if (now.activeIndex != model.writableIndex) {
            val confirmed =
                selectIndex(model.writableIndex, explicitUserAction = true, persist = false)
            require(confirmed == model.writableIndex) {
                "EQ index switch failed: asked ${model.writableIndex}, the DAC reports $confirmed"
            }
        }
        read(withPreGain)
    }

    fun padBands(bands: List<MoondropCodec.BandWrite>): List<MoondropCodec.BandWrite> {
        require(bands.size <= model.bandCount) {
            "${bands.size} bands; ${model.productName} has ${model.bandCount}"
        }
        return List(model.bandCount) { i ->
            val b = bands.getOrNull(i)
            if (b == null) MoondropCodec.neutralBand(i)
            else b.also { require(it.index == i) { "Band ${it.index} stands at position $i" } }
        }
    }

    var lastExpected: List<MoondropCodec.BandWrite>? = null
        private set

    var lastBefore: MoondropCodec.Snapshot? = null
        private set

    fun writeGain(
        register: MoondropCodec.GainRegister,
        db: Double,
        explicitHold: Boolean,
        persist: Boolean = false,
    ): WriteOutcome<MoondropCodec.Snapshot> = io.guarded {
        require(explicitHold) { "An explicit user action is required" }
        val steps = MoondropCodec.gainSteps(model, register, db, persist)
        val want = MoondropCodec.gainRawQ8(model, register, db)
        steps.forEach { MoondropCodec.requireSendable(it.payload) }
        val before = read(withPreGain = true).also { lastBefore = it }
        require(register != MoondropCodec.GainRegister.PRE || before.preGain != null) {
            "Pre-gain (0x23) gave no valid read; it is not written over, nothing sent"
        }
        if (persist)
            require(model.isWritable(before.activeIndex)) {
                "The DAC is on EQ index ${before.activeIndex}, not one of Contour's banks (${model.writableIndexes.sorted().joinToString(", ")}): the flash save is the active bank's, nothing sent"
            }
        steps.forEach {
            io.send(model.reportId, it.payload)
            io.pause(it.delayAfterMs)
        }
        val back = read(withPreGain = true)
        val problems = ArrayList<String>()
        if (back.activeIndex != before.activeIndex)
            problems += "EQ index ${before.activeIndex} -> ${back.activeIndex}"
        if (back.bands != before.bands) problems += "the bands changed"
        val got =
            when (register) {
                MoondropCodec.GainRegister.PRE -> back.preGain?.rawQ8
                MoondropCodec.GainRegister.GLOBAL -> back.globalGain.rawQ8
            }
        if (got == null) problems += "${register.what} not read back"
        else if (got != want) problems += "${register.what} $want -> $got (Q8.8)"
        WriteOutcome(problems.isEmpty(), false, problems.joinToString("; ").ifEmpty { null }, back)
    }

    private fun notPeqMode(index: Int) =
        "The DAC is on EQ index $index, one of the unit's own presets (Contour writes EQ index ${model.writableIndexes.sorted().joinToString(" or ")}): switch the DAC to its PEQ mode first; nothing written"

    fun write(
        bands: List<MoondropCodec.BandWrite>,
        preGainDb: Double?,
        explicitHold: Boolean,
    ): WriteOutcome<MoondropCodec.Snapshot> = io.guarded {
        require(explicitHold) { "HOLD TO SEND required" }
        val full = padBands(bands)
        MoondropCodec.writeSteps(model, full, preGainDb)
        lastExpected = full
        val before = read().also { lastBefore = it }

        require(model.isWritable(before.activeIndex)) { notPeqMode(before.activeIndex) }
        val steps = MoondropCodec.writeSteps(model, full, preGainDb, before.globalGain.rawQ8)
        steps.forEach { MoondropCodec.requireSendable(it.payload) }
        steps.forEach {
            io.send(model.reportId, it.payload)
            io.pause(it.delayAfterMs)
        }
        val back = read(withPreGain = preGainDb != null)
        port.log(
            "Moondrop write: EQ index found ${before.activeIndex}, after the write ${back.activeIndex}"
        )
        val problems = ArrayList(MoondropCodec.matches(full, back.activeIndex, back.bands))
        val note =
            if (preGainDb == null) null
            else {
                val want = WalkPlay.toInt32(WalkPlay.jsRound(preGainDb * 256))
                when {
                    back.preGain == null -> "pre-gain not confirmed: no valid read of 0x23"
                    back.preGain.rawQ8 != want -> {
                        problems += "pre-gain ${want} -> ${back.preGain.rawQ8} (Q8)"
                        null
                    }
                    else -> null
                }
            }
        if (problems.isNotEmpty())
            problems += "EQ index ${before.activeIndex} -> ${back.activeIndex}"
        val reason = (problems + listOfNotNull(note)).joinToString("; ").ifEmpty { null }
        WriteOutcome(problems.isEmpty(), false, reason, back)
    }
}
