// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.devices.PendingRecords
import kotlin.math.roundToInt

object FamilySettings {
    const val EQ_INDEX = "eq-index"
    const val GLOBAL_GAIN = "global-gain"
    const val PRE_GAIN = "pre-gain"
    const val EQ_SWITCH = "eq-switch"

    const val PRESET = "preset"

    private fun mark(command: Int) = SettingRead.WalkPlayGet(command) { null }

    private val dawn =
        listOf(
            DacSetting(
                EQ_INDEX,
                "EQ INDEX",
                SettingKind.LEVEL,
                EvidenceRank.HW,
                min = PendingRecords.DAWN_EQ_INDEX_RANGE.first,
                max = PendingRecords.DAWN_EQ_INDEX_RANGE.last,
                group = 0,
                read = mark(MoondropCodec.CMD_EQ_ACTIVE),
            ),
            DacSetting(
                GLOBAL_GAIN,
                "GLOBAL GAIN",
                SettingKind.LEVEL,
                EvidenceRank.HW,
                min = (MoondropCatalog.dawnPro2.globalGainMinDb * 10).roundToInt(),
                max = (MoondropCatalog.dawnPro2.globalGainMaxDb * 10).roundToInt(),
                unit = "dB",
                group = 0,
                read = mark(MoondropCodec.CMD_GLOBAL_GAIN),
                writeCommand = null,
                writeVerified = false,
                scale = 10,
            ),
            DacSetting(
                PRE_GAIN,
                "PRE GAIN",
                SettingKind.LEVEL,
                EvidenceRank.VB,
                min =
                    (maxOf(
                            MoondropCatalog.dawnPro2.preGainMinDb,
                            io.github.chronosauros.contour.core.Preamp.FLOOR_DB,
                        ) * 10)
                        .roundToInt(),
                max = (MoondropCatalog.dawnPro2.preGainMaxDb * 10).roundToInt(),
                unit = "dB",
                group = 0,
                read = mark(MoondropCodec.CMD_PRE_GAIN),
                writeCommand = MoondropCodec.CMD_PRE_GAIN,
                writeVerified = false,
                scale = 10,
            ),
        )

    private fun ktSwitch(writeVerified: Boolean) =
        DacSetting(
            EQ_SWITCH,
            "EQ",
            SettingKind.TOGGLE,
            EvidenceRank.HW,
            options = listOf("OFF", "ON"),
            group = 0,
            read = mark(KtMicroCodec.EQ_ENABLE_REGISTER),
            writeCommand = KtMicroCodec.EQ_ENABLE_REGISTER,
            writeVerified = writeVerified,
        )

    private fun ktGlobalGain(model: KtMicroCatalog.Model, writeVerified: Boolean) =
        ktGlobalGainRow(requireNotNull(model.globalGainUserSteps), writeVerified)

    private val ktSwitchOnly = listOf(ktSwitch(writeVerified = false))

    private val allegro =
        listOf(
            ktSwitch(writeVerified = true),
            ktGlobalGain(KtMicroCatalog.models.first(), writeVerified = true),
        )

    private val jm12 =
        listOf(
            ktSwitch(writeVerified = true),
            ktGlobalGain(KtMicroCatalog.jm12Models.first(), writeVerified = true),
        )

    private fun gen1(model: MoondropGen1Catalog.Model): List<DacSetting> =
        if (!model.stockPicker) emptyList()
        else
            listOf(
                DacSetting(
                    PRESET,
                    "PRESET",
                    SettingKind.CHOICE,
                    EvidenceRank.OS,
                    options =
                        model.presetLabels.toSortedMap().values.map {
                            it.uppercase(java.util.Locale.ROOT)
                        },
                    group = 0,
                    inlineChoice = model.stockPickerInline,
                    read = mark(MoondropGen1Codec.CMD_PRESET),
                    writeCommand = MoondropGen1Codec.CMD_PRESET,
                    writeVerified = model.hardwareVerified,
                )
            )

    private val dusk = gen1(MoondropGen1Catalog.duskSp)

    fun of(target: DeviceTarget): List<DacSetting> =
        when {
            target.moondrop != null -> dawn
            target.gen1 != null -> dusk
            target.ktMicro != null ->
                when {
                    target.ktMicro.globalGainUserSteps == null -> ktSwitchOnly
                    target.ktMicro in KtMicroCatalog.jm12Models -> jm12
                    else -> allegro
                }
            else -> emptyList()
        }
}

private fun ktGlobalGainRow(steps: IntRange, writeVerified: Boolean) =
    DacSetting(
        FamilySettings.GLOBAL_GAIN,
        "GLOBAL GAIN",
        SettingKind.LEVEL,
        EvidenceRank.VB,
        min = steps.first,
        max = steps.last,
        unit = "dB",
        group = 1,
        read = SettingRead.WalkPlayGet(KtMicroCodec.GLOBAL_GAIN_REGISTER) { null },
        writeCommand = KtMicroCodec.GLOBAL_GAIN_REGISTER,
        writeVerified = writeVerified,
        valueText = ::globalGainText,
    )

internal fun globalGainText(step: Int): String {
    if (step !in KtMicroCodec.GLOBAL_GAIN_STEP_MIN..KtMicroCodec.GLOBAL_GAIN_STEP_MAX)
        return step.toString()
    val db = KtMicroCodec.globalGainDb(step)
    return if (db > 0) String.format(java.util.Locale.ROOT, "+%.1f", db)
    else String.format(java.util.Locale.ROOT, "%.1f", db + 0.0)
}
