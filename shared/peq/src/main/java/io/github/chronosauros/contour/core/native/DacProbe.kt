// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.WalkPlay

object ProbePlan {

    const val FIIO_SLOT = 0x16

    val FIIO_COMMANDS =
        listOf(
            0x01,
            0x02,
            0x03,
            0x04,
            0x05,
            0x06,
            0x07,
            0x08,
            0x09,
            0x0B,
            0x0C,
            0x0D,
            0x0E,
            0x0F,
            0x10,
            0x11,
            0x12,
            0x1D,
            0x1E,
            0x20,
            0x22,
            0x32,
            0x33,
            0x34,
        )

    val WALKPLAY_COMMANDS = listOf(0x11, 0x16, 0x19, 0x1B, 0x1D, 0x02)

    val FIIO_FORBIDDEN = setOf(0x00, 0x19, 0x1A, 0x1B, 0x1C, 0x21, 0x30, 0x4F)

    val FIIO_SEQUENCE: List<Int> = listOf(FIIO_SLOT) + FIIO_COMMANDS + FIIO_SLOT

    fun fiioFrame(codec: FiioCodec, cmd: Int): FiioFrame {
        require(cmd == FIIO_SLOT || cmd in FIIO_COMMANDS) {
            "Not a probe command: 0x%02x".format(cmd)
        }
        require(cmd !in FIIO_FORBIDDEN)
        return codec.querySetting(cmd).also {
            require(it.effect == FiioEffect.READ_ONLY && it.bytes[0] == 0xBB)
        }
    }

    fun fiioGapMs(config: FiioConfig): Int? =
        config.probeGapMs?.let { maxOf(it, config.writeGapMs) }

    fun walkPlayRead(cmd: Int): ByteArray {
        require(cmd in WALKPLAY_COMMANDS) { "Not a probe command: 0x%02x".format(cmd) }
        return WalkPlay.report(WalkPlay.READ, cmd, WalkPlay.END)
    }

    fun safeLabel(label: String): String =
        label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "probe" }

    fun deviceToken(name: String): String = name.trim().replace(Regex("\\s+"), "-")
}

class ProbeReport(
    label: String,
    device: String,
    private val out: (String) -> Unit,
    private val nowMs: () -> Long = { 0L },
) {
    val label = ProbePlan.safeLabel(label)
    private val device = ProbePlan.deviceToken(device)
    private val start = nowMs()

    private class Count {
        var sent = 0
        var answered = 0
    }

    private val layers = LinkedHashMap<String, Count>()
    val sent: Int
        get() = layers.values.sumOf { it.sent }

    val answered: Int
        get() = layers.values.sumOf { it.answered }

    fun sent(layer: String) = layers[layer]?.sent ?: 0

    fun answered(layer: String) = layers[layer]?.answered ?: 0

    private fun line(rest: String) = out("PROBE $label $rest")

    private fun count(layer: String) = layers.getOrPut(layer) { Count() }

    fun note(layer: String, text: String) = line("$device $layer $text")

    fun tx(layer: String, cmd: String, hex: String) {
        count(layer).sent++
        line("$device $layer $cmd TX $hex")
    }

    fun rx(layer: String, cmd: String, hex: String) {
        count(layer).answered++
        line("$device $layer $cmd RX $hex")
    }

    fun noReply(layer: String, cmd: String) = line("$device $layer $cmd no reply")

    fun error(layer: String, cmd: String, reason: String) =
        line("$device $layer $cmd err ${reason.replace('\n', ' ')}")

    fun stopped(text: String) = line(text)

    fun summary() = layers.forEach { (layer, c) -> line("summary $layer ${c.answered}/${c.sent}") }

    fun done() {
        summary()
        line("done $answered/$sent ${nowMs() - start}")
    }
}
