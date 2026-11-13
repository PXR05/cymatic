package com.pxr.cymatic.audio.eq

import com.pxr.cymatic.data.model.EqPreset
import io.github.chronosauros.contour.core.DacImport
import io.github.chronosauros.contour.usb.DacSnapshot
import java.io.IOException

internal fun DacSnapshot.playerPreset(): EqPreset =
    when (val result = importExact()) {
        is DacImport.Ready ->
            EqPreset(
                "From DAC",
                (result.eq.preampDb ?: 0.0).toFloat(),
                result.eq.bands.playerBands(),
            )
        is DacImport.Rejected -> throw IOException(result.issues.joinToString("; "))
    }
