// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Proven

enum class SettingKind {
    TOGGLE,
    CHOICE,
    LEVEL,
    BALANCE,
}

enum class EvidenceRank {
    HW,
    VB,
    VB_MINUS,
    OS,
    MP,
    CM,
}

sealed interface SettingRead {

    data class FiioGet(val command: Int, val decode: (ByteArray) -> Int?, val replyData: Int = 1) :
        SettingRead

    data class WalkPlayGet(val command: Int, val decode: (ByteArray) -> Int?) : SettingRead
}

data class DacSetting(
    val id: String,
    val label: String,
    val kind: SettingKind,
    val evidence: EvidenceRank,
    val options: List<String> = emptyList(),
    val shortOptions: List<String> = emptyList(),
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 1,
    val unit: String = "",
    val group: Int = 0,
    val section: String? = null,
    val readCommand: Int? = null,
    val read: SettingRead? = null,
    val writeCommand: Int? = null,
    val writeVerified: Boolean = false,
    val maxRule: MaxRule? = null,
    val scale: Int = 1,
    val valueText: ((Int) -> String)? = null,
    val reconnectsAfterWrite: Boolean = false,
    val inlineChoice: Boolean = false,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank())
        (read as? SettingRead.FiioGet)?.let {
            require(it.command in 0..255 && it.command != RESET_TO_FACTORY) {
                "$id: bad read command"
            }
        }
        require(
            writeCommand == null || (writeCommand in 0..255 && writeCommand != RESET_TO_FACTORY)
        ) {
            "$id: 0x21 is RESET_TO_FACTORY"
        }
        when (kind) {
            SettingKind.TOGGLE -> require(options.size == 2)
            SettingKind.CHOICE ->
                require(
                    options.size in 2..8 &&
                        (shortOptions.isEmpty() || shortOptions.size == options.size)
                )
            SettingKind.LEVEL -> require(step > 0 && min < max && scale >= 1)
            SettingKind.BALANCE -> require(step > 0 && max > 0)
        }
    }

    val writeEnabled: Boolean
        get() =
            writeCommand != null && writeCommand !in DacWrite.READ_ONLY && Proven.on(writeVerified)

    val readOnly: Boolean
        get() = writeCommand == null || writeCommand in DacWrite.READ_ONLY

    val longChoice: Boolean
        get() = kind == SettingKind.CHOICE && options.size > 3

    fun shortOption(i: Int): String = shortOptions.getOrNull(i) ?: options.getOrElse(i) { "--" }

    fun maxFor(valueOf: (String) -> Int?): Int =
        maxRule?.let { r -> valueOf(r.settingId)?.let { r.maxima.getOrNull(it) } } ?: max

    data class MaxRule(val settingId: String, val maxima: List<Int>)

    private companion object {
        const val RESET_TO_FACTORY = 0x21
    }
}

data class DacInfo(
    val id: String,
    val command: Int,
    val replyData: Int,
    val decode: (ByteArray) -> Int?,
    val format: (Int) -> String,
)

internal data class SettingPlacement(
    val id: String,
    val section: String?,
    val group: Int,
    val writeVerified: Boolean,
    val reconnectsAfterWrite: Boolean = false,
)

object DacSettings {
    private fun u(b: Byte) = b.toInt() and 255

    private val toggle: (ByteArray) -> Int? = { a -> u(a[6]).takeIf { it in 0..1 } }

    private fun choice(count: Int): (ByteArray) -> Int? = { a ->
        u(a[6]).takeIf { it in 0 until count }
    }

    private val rawByte: (ByteArray) -> Int? = { a -> u(a[6]) }

    private val balance: (ByteArray) -> Int? = { a ->
        val left = u(a[6])
        val right = u(a[7])
        when {
            left > 0 && right > 0 -> null
            right > 0 -> -right
            else -> left
        }
    }

    private fun get(cmd: Int, decode: (ByteArray) -> Int?, data: Int = 1) =
        SettingRead.FiioGet(cmd, decode, data)

    private val volume =
        DacSetting(
            "volume",
            "VOLUME",
            SettingKind.LEVEL,
            EvidenceRank.VB_MINUS,
            min = 0,
            max = 60,
            group = 0,
            section = null,
            read = get(0x02, rawByte),
            writeCommand = 0x02,
            maxRule = DacSetting.MaxRule("volume-steps", listOf(60, 120)),
        )
    private val volumeSteps =
        DacSetting(
            "volume-steps",
            "VOLUME STEPS",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("60", "120"),
            group = 0,
            read = get(0x0C, choice(2)),
            writeCommand = 0x0C,
        )
    private val balanceSetting =
        DacSetting(
            "balance",
            "BALANCE",
            SettingKind.BALANCE,
            EvidenceRank.VB_MINUS,
            max = 12,
            group = 0,
            read = get(0x07, balance, data = 2),
            writeCommand = 0x07,
        )

    private fun mic(group: Int) =
        DacSetting(
            "mic",
            "IN-LINE MIC",
            SettingKind.TOGGLE,
            EvidenceRank.VB_MINUS,
            options = listOf("OFF", "ON"),
            group = group,
            read = get(0x12, toggle),
            writeCommand = 0x12,
        )

    private fun light(group: Int) =
        DacSetting(
            "light",
            "LIGHT",
            SettingKind.TOGGLE,
            EvidenceRank.VB_MINUS,
            options = listOf("OFF", "ON"),
            group = group,
            read = get(0x06, toggle),
            writeCommand = 0x06,
        )

    private fun uac(group: Int) =
        DacSetting(
            "uac",
            "USB AUDIO",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("UAC 1.0", "UAC 2.0"),
            group = group,
            read = get(0x20, choice(2)),
            writeCommand = 0x20,
        )

    private val filter =
        DacSetting(
            "filter",
            "FILTER",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options =
                listOf(
                    "MINIMUM PHASE FAST",
                    "LINEAR PHASE FAST",
                    "MINIMUM PHASE SLOW",
                    "LINEAR PHASE SLOW",
                    "NON OVER-SAMPLING",
                ),
            shortOptions = listOf("MIN F", "LIN F", "MIN S", "LIN S", "NOS"),
            group = 0,
            read = get(0x03, choice(5)),
            writeCommand = 0x03,
        )
    private val gain =
        DacSetting(
            "gain",
            "GAIN",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("HIGH", "LOW"),
            group = 0,
            read = get(0x04, choice(2)),
            writeCommand = 0x04,
        )

    private val output =
        DacSetting(
            "output",
            "OUTPUT",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("CLASS AB", "CLASS H"),
            group = 0,
            read = get(0x05, choice(2)),
            writeCommand = 0x05,
        )
    private val dre =
        DacSetting(
            "dre",
            "DRE",
            SettingKind.TOGGLE,
            EvidenceRank.VB_MINUS,
            options = listOf("OFF", "ON"),
            group = 0,
            read = get(0x32, toggle),
            writeCommand = 0x32,
        )
    private val buttons =
        DacSetting(
            "buttons",
            "BUTTONS",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("MODE A", "MODE B"),
            group = 1,
            read = get(0x0D, choice(2)),
            writeCommand = 0x0D,
        )
    private val brightness =
        DacSetting(
            "brightness",
            "BRIGHTNESS",
            SettingKind.LEVEL,
            EvidenceRank.VB_MINUS,
            min = 0,
            max = 10,
            group = 2,
            read = get(0x0E, rawByte),
            writeCommand = 0x0E,
        )
    private val screenOff =
        DacSetting(
            "screen-off",
            "SCREEN OFF",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("15 S", "30 S", "1 MIN", "2 MIN", "5 MIN", "10 MIN", "30 MIN"),
            group = 2,
            read = get(0x0F, choice(7)),
            writeCommand = 0x0F,
        )
    private val rotation =
        DacSetting(
            "rotation",
            "ROTATION",
            SettingKind.TOGGLE,
            EvidenceRank.VB_MINUS,
            options = listOf("OFF", "ON"),
            group = 2,
            read = get(0x10, toggle),
            writeCommand = 0x10,
        )
    private val language =
        DacSetting(
            "language",
            "LANGUAGE",
            SettingKind.CHOICE,
            EvidenceRank.VB_MINUS,
            options = listOf("CHINESE", "ENGLISH"),
            group = 2,
            read = get(0x11, choice(2)),
            writeCommand = 0x11,
        )

    private val templates: Map<String, DacSetting> =
        listOf(
                volume,
                volumeSteps,
                balanceSetting,
                mic(0),
                light(0),
                uac(0),
                filter,
                gain,
                output,
                dre,
                buttons,
                brightness,
                screenOff,
                rotation,
                language,
            )
            .associateBy { it.id }

    internal fun forModel(placements: List<SettingPlacement>): List<DacSetting> =
        placements.map { p ->
            val template =
                templates[p.id]
                    ?: error("no DAC setting template for the id \"${p.id}\" (devices/)")

            val rule = template.maxRule?.takeIf { r -> placements.any { it.id == r.settingId } }
            template.copy(
                section = p.section,
                group = p.group,
                writeVerified = p.writeVerified,
                maxRule = rule,
                reconnectsAfterWrite = p.reconnectsAfterWrite,
            )
        }

    private val RATES =
        listOf(
            "32 kHz",
            "44.1 kHz",
            "48 kHz",
            "88.2 kHz",
            "96 kHz",
            "176.4 kHz",
            "192 kHz",
            "352.8 kHz",
            "384 kHz",
            "705.6 kHz",
            "768 kHz",
            "DSD64",
            "DSD128",
            "DSD256",
            "DSD512",
        )

    val SAMPLE_RATE = DacInfo("rate", 0x09, 1, rawByte) { RATES.getOrNull(it) ?: "$it" }

    val FIRMWARE =
        DacInfo("fw", 0x0B, 3, { a -> u(a[6]) * 10_000 + u(a[7]) * 100 + u(a[8]) }) {
            "FW ${it / 10_000}.${it / 100 % 100}.${it % 100}"
        }

    val FIRMWARE_2 =
        DacInfo("fw2", 0x0B, 2, { a -> u(a[6]) * 100 + u(a[7]) }) {
            "FW ${it / 100}.${"%02d".format(it % 100)}"
        }

    private val infos: Map<String, DacInfo> =
        listOf(SAMPLE_RATE, FIRMWARE, FIRMWARE_2).associateBy { it.id }

    internal fun infoFor(ids: List<String>): List<DacInfo> = ids.map {
        infos[it] ?: error("no DAC info read for the id \"$it\" (devices/)")
    }
}
