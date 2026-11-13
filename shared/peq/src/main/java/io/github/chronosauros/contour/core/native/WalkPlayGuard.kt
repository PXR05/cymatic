// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.DeviceProtocol
import io.github.chronosauros.contour.core.WalkPlay

object WalkPlayGuard {

    enum class Context {
        NORMAL,
        RESET,
        MIC_SAVE,
    }

    private val CANDIDATES: List<List<Int>> =
        listOf(
            listOf(0x05, 0x01, 0x02, 0x00, 0xFF),
            listOf(0x05, 0x01, 0x01, 0x00, 0xFF),
            listOf(0x02, 0x00, 0x00, 0x01, 0xFF),
            listOf(0x80, 0x00, 0x00, 0x00, 0xFF),
        )
    private val CB1300D_WRITES = setOf(0x60, 0x22, 0xD3, 0x24)
    private val CB1300D_READS = setOf(0xDA, 0x22, 0xD3, 0x24, 0x26, 0x25)

    private fun u(p: ByteArray, i: Int) = if (i < p.size) p[i].toInt() and 255 else 0

    fun refusal(payload: ByteArray, profile: WalkPlayProfile, context: Context): String? {
        val op = u(payload, 0)
        val cmd = u(payload, 1)
        for (c in CANDIDATES) if (c.indices.all { u(payload, it) == c[it] })
            return "reset/probe candidate of another WalkPlay class (${hex(c)}): never sent"
        if (op == WalkPlay.WRITE) {
            if (cmd in CB1300D_WRITES) return "0x%02x is a CB1300D frame: never sent".format(cmd)
            if (cmd == 0xFF) return "01 FF is the firmware update: never sent"
            if (cmd == 0x1B) return "01 1B (noise reduction) is disabled for class CB: never sent"
            val trn = profile.protocol == DeviceProtocol.TRN
            val schemeNo16 = trn || profile.protocol == DeviceProtocol.MAX
            when (cmd) {
                0x05 -> {
                    val sub = u(payload, 2)
                    if (sub in 1..4) {
                        if (context != Context.RESET)
                            return "01 05 0$sub is the factory reset's: sent only inside a confirmed reset"
                    } else if (sub != 0 || (trn && context != Context.RESET))
                        return "01 05 %02x is not a step of ${profile.name}'s write".format(sub)
                }
                0x17 ->
                    if (u(payload, 2) != 0 || (trn && context != Context.RESET))
                        return "01 17 is not a step of ${profile.name}'s write"
                WalkPlay.CMD_GLOBAL_GAIN -> {
                    val g = payload.getOrNull(4)?.toInt() ?: 0
                    if (g > 0 && profile.preampMaxDb <= 0)
                        return "a positive preamp ($g dB) is not verified on a unit: never sent"
                }
                WalkPlaySettings.CMD_GAIN_MODE ->
                    if (!trn)
                        return "0x%02x (gain mode) is not a setting of ${profile.name}: "
                            .format(cmd) +
                            if (profile.protocol == DeviceProtocol.MAX)
                                "its physical Eco / Boost switch sets it and wins after every replug"
                            else "WalkPlay shows it for SchemeNo16 only"
                WalkPlaySettings.CMD_DAC_FILTER,
                WalkPlaySettings.CMD_WORK_MODE,
                WalkPlaySettings.CMD_BALANCE ->
                    if (!schemeNo16)
                        return "0x%02x is not a setting of ${profile.name} (WalkPlay shows it for SchemeNo16 only)"
                            .format(cmd)

                0x01 ->
                    if (
                        profile.protocol == DeviceProtocol.MAX &&
                            u(payload, 2) == 0 &&
                            context != Context.MIC_SAVE
                    )
                        return "01 01 00 is sent to ${profile.name} only as the flash save after a mic write: its `01 01 x` is also the EQ enable / flash frame"
            }
        } else if (op == WalkPlay.READ && cmd in CB1300D_READS)
            return "0x%02x is a CB1300D read: never sent".format(cmd)
        return null
    }

    private fun hex(b: List<Int>) = b.joinToString(" ") { "%02X".format(it) }
}
