package com.pxr.cymatic.ui.screens.settings.eq.hardware

import com.pxr.cymatic.audio.eq.dacType
import com.pxr.cymatic.audio.eq.playerType
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.FilterType
import com.pxr.cymatic.ui.components.eq.EqBandEditorLimits
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.isPass

internal fun bandLimits(target: DeviceTarget, band: EqBand): EqBandEditorLimits {
    val type = runCatching {
        band.type.dacType()
    }
        .getOrDefault(io.github.chronosauros.contour.core.FilterType.PEAK)
    val gain = target.caps.gainRange(type)
    val q = target.qRange(type)
    return EqBandEditorLimits(
        target.caps.freqMinHz.toFloat()..target.caps.freqMaxHz.toFloat(),
        gain.start.toFloat()..gain.endInclusive.toFloat(),
        q.start.toFloat()..q.endInclusive.toFloat(),
        target.caps.types.map { it.playerType() },
        if (band.type == FilterType.HIGH_SHELF) target.highShelfStepDb?.toFloat() ?: 0.1f else 0.1f,
        0.1f,
        !type.isPass,
    )
}

internal fun deviceDetails(target: DeviceTarget, firmware: String?): String = buildString {
    appendLine(target.caps.name)
    firmware?.let {
        appendLine("Firmware: $it")
    }
    appendLine("Bands: ${target.caps.bands}")
    appendLine(
        target.caps.types.joinToString {
            it.playerType().displayName
        }
    )
    appendLine("Frequency: ${target.caps.freqMinHz}–${target.caps.freqMaxHz} Hz")
    appendLine(
        "Gain: ${target.caps.gainMinDb}–${target.caps.gainMaxDb} dB; some filters have narrower limits"
    )
    appendLine(
        if (target.hasPreamp)
            "Preamp: ${target.preampMin}–${target.preampMax} dB in ${target.preampStep} dB steps"
        else "No writable EQ preamp"
    )
    if (target.emulatesHighShelf)
        appendLine("High shelves use an inverted low shelf and preamp; gain uses whole dB.")
    target.ktMicro?.let {
        appendLine(
            "Tuning assumes ${it.assumedStreamHz.toInt()} Hz playback. Other sample rates shift the filter frequencies."
        )
    }
}
