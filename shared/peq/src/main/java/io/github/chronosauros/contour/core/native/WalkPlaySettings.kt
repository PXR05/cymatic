// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.WalkPlay
import kotlin.math.floor

object WalkPlaySettings {
    const val CMD_MIC = 0x02
    const val CMD_DAC_FILTER = 0x11
    const val CMD_BALANCE = 0x16
    const val CMD_GAIN_MODE = 0x19
    const val CMD_WORK_MODE = 0x1D

    const val CMD_SAVE = 0x01

    const val MIC = "mic"
    const val DAC_FILTER = "dac-filter"
    const val WORK_MODE = "work-mode"
    const val GAIN_MODE = "gain-mode"
    const val BALANCE = "balance"

    private fun i8(b: Byte) = b.toInt()

    private fun u(b: Byte) = b.toInt() and 255

    private fun reply(buf: ByteArray, size: Int) = buf.size >= size

    private val micDecode: (ByteArray) -> Int? = { buf -> if (reply(buf, 6)) i8(buf[5]) else null }

    private val maxMicDecode: (ByteArray) -> Int? = { buf ->
        if (reply(buf, 6)) {
            val raw = u(buf[4]) or (u(buf[5]) shl 8)
            val signed = if (raw > 32767) raw - 65536 else raw
            floor(signed * 15.0 / 32767 + 0.5).toInt().takeIf { it in -15..15 }
        } else null
    }
    private val filterDecode: (ByteArray) -> Int? = { buf ->
        if (reply(buf, 5)) (u(buf[4]) - 1).takeIf { it in 0..4 } else null
    }
    private val workDecode: (ByteArray) -> Int? = { buf ->
        if (reply(buf, 5)) u(buf[4]).takeIf { it in 0..1 } else null
    }
    private val gainDecode: (ByteArray) -> Int? = { buf ->
        if (reply(buf, 5)) u(buf[4]).takeIf { it in 0..1 } else null
    }

    private val balanceDecode: (ByteArray) -> Int? = { buf ->
        if (reply(buf, 7))
            u(buf[6]).let { r -> if (r == 0) 0 else 256 - r }.takeIf { it in 0..BALANCE_STEPS }
        else null
    }

    const val BALANCE_STEPS = 15
    private const val LEFT = 0
    private const val RIGHT = 1

    fun balanceChannel(buf: ByteArray): Int? =
        if (reply(buf, 5)) u(buf[4]).takeIf { it in 0..1 } else null

    private val mic =
        DacSetting(
            MIC,
            "MIC GAIN",
            SettingKind.LEVEL,
            EvidenceRank.VB,
            min = -15,
            max = 15,
            group = 1,
            readCommand = CMD_MIC,
            read = SettingRead.WalkPlayGet(CMD_MIC, micDecode),
            writeCommand = CMD_MIC,
            writeVerified = false,
        )

    private val maxMic = mic.copy(read = SettingRead.WalkPlayGet(CMD_MIC, maxMicDecode))

    private fun isMaxMic(s: DacSetting) =
        (s.read as? SettingRead.WalkPlayGet)?.decode === maxMicDecode

    private const val MIC_FULL_SCALE = 0x7F00

    private fun maxMicReport(value: Int): ByteArray {
        val t =
            Math.round(value * (32767.0 / 15))
                .toInt()
                .coerceIn(-MIC_FULL_SCALE, MIC_FULL_SCALE)
                .let { if (it < 0) it + 65536 else it }
        return WalkPlay.report(WalkPlay.WRITE, CMD_MIC, 0x02, t and 0xFF, (t shr 8) and 0xFF)
    }

    private val dacFilter =
        DacSetting(
            DAC_FILTER,
            "FILTER",
            SettingKind.CHOICE,
            EvidenceRank.VB,
            options = listOf("FAST-LL", "FAST-PC", "SLOW-LL", "SLOW-PC", "NON-OS"),
            group = 0,
            readCommand = CMD_DAC_FILTER,
            read = SettingRead.WalkPlayGet(CMD_DAC_FILTER, filterDecode),
            writeCommand = CMD_DAC_FILTER,
            writeVerified = true,
        )
    private val workMode =
        DacSetting(
            WORK_MODE,
            "OUTPUT",
            SettingKind.CHOICE,
            EvidenceRank.VB,
            options = listOf("CLASS H", "CLASS AB"),
            group = 0,
            readCommand = CMD_WORK_MODE,
            read = SettingRead.WalkPlayGet(CMD_WORK_MODE, workDecode),
            writeCommand = CMD_WORK_MODE,
            writeVerified = true,
        )
    private val gainMode =
        DacSetting(
            GAIN_MODE,
            "GAIN",
            SettingKind.CHOICE,
            EvidenceRank.VB,
            options = listOf("LOW", "HIGH"),
            group = 0,
            readCommand = CMD_GAIN_MODE,
            read = SettingRead.WalkPlayGet(CMD_GAIN_MODE, gainDecode),
            writeCommand = CMD_GAIN_MODE,
            writeVerified = true,
        )

    private val readOnlyMic = mic.copy(writeCommand = null)

    private val balance =
        DacSetting(
            BALANCE,
            "BALANCE",
            SettingKind.BALANCE,
            EvidenceRank.VB,
            max = BALANCE_STEPS,
            group = 0,
            readCommand = CMD_BALANCE,
            read = SettingRead.WalkPlayGet(CMD_BALANCE, balanceDecode),
            writeCommand = CMD_BALANCE,
            writeVerified = true,
        )

    const val MAX_WORK_MODE_VERIFIED = true

    private val maxRows =
        listOf(
            dacFilter,
            workMode.copy(writeVerified = MAX_WORK_MODE_VERIFIED),
            balance,
            maxMic.copy(writeVerified = MAX_MIC_VERIFIED),
        )

    const val MAX_MIC_VERIFIED = true

    fun of(profile: WalkPlayProfile): List<DacSetting> =
        when (profile.id) {
            "trn" -> listOf(dacFilter, workMode, gainMode, balance, readOnlyMic)
            "max" -> maxRows
            "micro",
            "echo-a" -> listOf(readOnlyMic)
            else -> emptyList()
        }

    fun offered(profile: WalkPlayProfile): List<DacSetting> =
        of(profile).filter { Proven.on(it.writeVerified) }

    fun row(profile: WalkPlayProfile, id: String): DacSetting =
        of(profile).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException(
                "${profile.name} has no setting \"$id\" (${of(profile).joinToString { it.id }.ifEmpty { "none" }})"
            )

    fun readRequests(s: DacSetting): List<ByteArray> =
        when (s.id) {
            BALANCE ->
                listOf(
                    WalkPlay.report(WalkPlay.READ, CMD_BALANCE, 0x02, 0x00, 0x00),
                    WalkPlay.report(WalkPlay.READ, CMD_BALANCE, 0x02, 0x01, 0x00),
                )
            else ->
                listOf(WalkPlay.report(WalkPlay.READ, requireNotNull(s.readCommand), WalkPlay.END))
        }

    fun isReply(s: DacSetting, buf: ByteArray, channel: Int? = null): Boolean =
        buf.size >= WalkPlay.REPORT_SIZE &&
            WalkPlay.isReply(buf, requireNotNull(s.readCommand)) &&
            (buf[1].toInt() and 255) == WalkPlay.READ &&
            (s.id != BALANCE || (channel != null && balanceChannel(buf) == channel))

    fun decode(s: DacSetting, buf: ByteArray): Int? =
        (s.read as? SettingRead.WalkPlayGet)?.decode?.invoke(buf)

    fun balanceValue(channel0: ByteArray, channel1: ByteArray): Int? {
        val left = balanceDecode(channel0) ?: return null
        val right = balanceDecode(channel1) ?: return null
        return left - right
    }

    private fun balanceFrame(c: Int, steps: Int) =
        WalkPlay.report(
            WalkPlay.WRITE,
            CMD_BALANCE,
            0x04,
            c,
            0x00,
            if (steps == 0) 0 else 256 - steps,
            0x00,
        )

    fun writeReports(s: DacSetting, value: Int): List<ByteArray> {
        require(s.writeCommand != null) { "${s.label} is read only" }
        DacWrite.checkValue(s, value)
        return when (s.id) {
            DAC_FILTER -> listOf(WalkPlay.report(WalkPlay.WRITE, CMD_DAC_FILTER, 0x01, value + 1))
            WORK_MODE -> listOf(WalkPlay.report(WalkPlay.WRITE, CMD_WORK_MODE, 0x01, value))
            GAIN_MODE -> listOf(WalkPlay.report(WalkPlay.WRITE, CMD_GAIN_MODE, 0x01, value))
            BALANCE ->
                listOf(balanceFrame(RIGHT, maxOf(-value, 0)), balanceFrame(LEFT, maxOf(value, 0)))
            MIC ->
                listOf(
                    if (isMaxMic(s)) maxMicReport(value)
                    else WalkPlay.report(WalkPlay.WRITE, CMD_MIC, 0x02, 0x80, value and 0xFF)
                )
            else -> throw IllegalArgumentException("${s.label} has no write")
        }
    }

    fun saveReport(): ByteArray = WalkPlay.report(WalkPlay.WRITE, CMD_SAVE, WalkPlay.END)
}
