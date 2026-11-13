// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Band

class MoondropGen1Session(val model: MoondropGen1Catalog.Model, private val port: NativePort) {
    private val io = NativeIo(port)

    private fun ask(frame: MoondropGen1Codec.Frame, accepts: (ByteArray) -> Boolean): ByteArray {
        MoondropGen1Codec.requireSendable(frame.payload)
        return io.request(MoondropGen1Codec.REPORT_ID, frame.payload, accepts)
    }

    private fun check() {
        val size = port.payloadSize(MoondropGen1Codec.REPORT_ID, NativeHidReports.Kind.INPUT)
        require(
            size == MoondropGen1Codec.PAYLOAD_BYTES &&
                port.payloadSize(MoondropGen1Codec.REPORT_ID, NativeHidReports.Kind.OUTPUT) ==
                    MoondropGen1Codec.PAYLOAD_BYTES
        ) {
            "The HID report descriptor does not declare the 62-byte report 1 of a Moondrop gen-1 cable"
        }
    }

    fun read(): MoondropGen1Codec.Snapshot = io.guarded {
        check()
        val index =
            MoondropGen1Codec.parsePreset(
                io.retryTimeouts {
                    ask(MoondropGen1Codec.presetQuery(), MoondropGen1Codec::isPresetReply)
                }
            )
        val bands =
            List(model.bandCount) { i ->
                val n = i + 1
                MoondropGen1Codec.parseBand(
                    io.retryTimeouts {
                        ask(MoondropGen1Codec.bandQuery(n)) { MoondropGen1Codec.isBandReply(it, n) }
                    },
                    n,
                )
            }
        MoondropGen1Codec.Snapshot(model, index, bands)
    }

    var lastPlanned: List<MoondropGen1Codec.BandWrite>? = null
        private set

    var lastBefore: MoondropGen1Codec.Snapshot? = null
        private set

    private fun runSteps(steps: List<MoondropGen1Codec.Frame>) {
        steps.forEach { f ->
            ask(f, MoondropGen1Codec::isWholeReply)
            io.pause(f.delayAfterMs)
        }
    }

    fun selectPreset(index: Int, explicitUserAction: Boolean): Int = io.guarded {
        require(explicitUserAction) { "Switching the preset requires an explicit user action" }
        require(index in model.presetLabels.keys) {
            "Preset $index is not one of ${model.presetLabels.keys.sorted().joinToString(", ")}"
        }
        check()
        runSteps(MoondropGen1Codec.selectSteps(index, model.afterSelectMs))
        MoondropGen1Codec.parsePreset(
            io.retryTimeouts {
                ask(MoondropGen1Codec.presetQuery(), MoondropGen1Codec::isPresetReply)
            }
        )
    }

    fun write(bands: List<Band>, explicitHold: Boolean): WriteOutcome<MoondropGen1Codec.Snapshot> {
        port.guard()
        require(explicitHold) { "HOLD TO SEND required" }
        MoondropGen1Codec.issues(model, bands)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IllegalArgumentException(it.joinToString("; ")) }
        val planned = MoondropGen1Codec.plan(model, bands)
        val steps = MoondropGen1Codec.writeSteps(model, planned)
        steps.forEach { MoondropGen1Codec.requireSendable(it.payload) }
        lastPlanned = planned
        lastBefore = read()
        runSteps(steps)
        return WriteOutcome(
            false,
            true,
            "Saved. The cable restarts - Contour reads it again when it is back.",
            null,
        )
    }

    fun verifyAfterReturn(
        planned: List<MoondropGen1Codec.BandWrite>
    ): WriteOutcome<MoondropGen1Codec.Snapshot> = io.guarded {
        check()
        runSteps(MoondropGen1Codec.selectSteps(model.customIndex, model.afterSelectMs))
        val back = read()
        port.log(
            "Moondrop gen-1 write: cable found on preset ${lastBefore?.activeIndex ?: "?"}, after the restart and the select ${back.activeIndex}"
        )
        val problems =
            MoondropGen1Codec.mismatches(planned, back.activeIndex, back.bands, model.customIndex)
        WriteOutcome(problems.isEmpty(), false, problems.joinToString("; ").ifEmpty { null }, back)
    }

    fun writeAndVerify(
        bands: List<Band>,
        explicitHold: Boolean,
    ): WriteOutcome<MoondropGen1Codec.Snapshot> {
        write(bands, explicitHold)
        return verifyAfterReturn(requireNotNull(lastPlanned))
    }
}
