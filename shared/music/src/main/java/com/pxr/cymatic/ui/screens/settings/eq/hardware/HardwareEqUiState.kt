package com.pxr.cymatic.ui.screens.settings.eq.hardware

import com.pxr.cymatic.audio.eq.draftIssues
import com.pxr.cymatic.data.model.EqPreset
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.usb.DacHandle

internal data class HardwareEqUiState(
    val devices: List<DacHandle> = emptyList(),
    val device: DacHandle? = null,
    val permission: Boolean = false,
    val permissionPending: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val draft: EqPreset = EqPreset("DAC draft"),
    val slot: Int? = null,
    val message: String = "Connect a supported USB DAC",
    val onDac: Boolean = false,
    val pendingVerification: Boolean = false,
    val firmware: String? = null,
) {
    val target: DeviceTarget?
        get() = device?.target

    val issues: List<String>
        get() = target?.draftIssues(draft).orEmpty()

    val canApply: Boolean
        get() =
            device != null &&
                permission &&
                !busy &&
                !permissionPending &&
                !pendingVerification &&
                target?.readOnly == false &&
                issues.isEmpty()
}
