package com.pxr.cymatic.audio.eq

import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.FilterType
import io.github.chronosauros.contour.core.Band
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.FilterType as DacFilterType
import io.github.chronosauros.contour.core.Profile

internal fun FilterType.dacType(): DacFilterType =
    when (this) {
        FilterType.PEAKING -> DacFilterType.PEAK
        FilterType.NOTCH -> error("Notch filters are not supported by the connected DAC")
        else -> DacFilterType.valueOf(name)
    }

internal fun DacFilterType.playerType(): FilterType =
    when (this) {
        DacFilterType.PEAK -> FilterType.PEAKING
        else -> FilterType.valueOf(name)
    }

internal fun EqPreset.dacProfile(): Profile =
    Profile(
        id = "cymatic-dac",
        name = name,
        preampDb = preamp.toDouble(),
        createdAt = 0,
        updatedAt = 0,
        bands =
            bands.map { b ->
                Band(
                    b.id.toString(),
                    b.type.dacType(),
                    b.frequency.toDouble(),
                    b.gain.toDouble(),
                    b.q.toDouble(),
                    b.enabled,
                )
            },
    )

internal fun List<Band>.playerBands() = mapIndexed { index, b ->
    EqBand(
        index,
        b.type.playerType(),
        b.freqHz.toFloat(),
        b.gainDb.toFloat(),
        b.q.toFloat(),
        b.enabled,
    )
}

internal fun DeviceTarget.draftIssues(preset: EqPreset): List<String> = runCatching {
    val profile = preset.dacProfile()
    buildList {
        addAll(issues(profile))
        val preamp = profile.preampDb
        if (hasPreamp && preamp != null && kotlin.math.abs(heldPreamp(preamp) - preamp) > 0.00001) {
            add("Preamp must use ${preampStep} dB steps for this DAC")
        }
        highShelfStepDb?.let { step ->
            profile.bands.forEachIndexed { i, b ->
                if (
                    b.enabled &&
                        b.type == DacFilterType.HIGH_SHELF &&
                        kotlin.math.abs(b.gainDb / step - kotlin.math.round(b.gainDb / step)) >
                            0.00001
                ) {
                    add("Band ${i + 1}: high-shelf gain must use $step dB steps")
                }
            }
        }
        if (!hasPreamp && preset.preamp != 0f) add("This DAC has no EQ preamp; set it to 0 dB")
    }
}
    .getOrElse { listOf(it.message ?: "This profile cannot be sent to the DAC") }

internal fun DeviceTarget.emptyDraft(): EqPreset =
    EqPreset(
        "DAC draft",
        0f,
        listOf(
            EqBand(
                0,
                frequency = 1000f.coerceIn(caps.freqMinHz.toFloat(), caps.freqMaxHz.toFloat()),
                q =
                    0.71f.coerceIn(
                        caps.qRange(DacFilterType.PEAK).start.toFloat(),
                        caps.qRange(DacFilterType.PEAK).endInclusive.toFloat(),
                    ),
            )
        ),
    )
