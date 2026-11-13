package com.pxr.cymatic.audio.eq

import com.pxr.cymatic.data.model.EqPreset
import io.github.chronosauros.contour.usb.DacHandle
import io.github.chronosauros.contour.usb.PendingSave

internal class PendingDacVerification(
    val ticket: PendingSave,
    val profile: EqPreset,
    handle: DacHandle,
) {
    private val model = handle.modelKey()
    private val serial = handle.serial()
    var selectionDone = false

    fun matchesModel(handle: DacHandle) = handle.modelKey() == model

    fun matchesDevice(handle: DacHandle) =
        matchesModel(handle) && (serial == null || handle.serial() == serial)
}
