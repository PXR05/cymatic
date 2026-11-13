package com.pxr.cymatic.audio.eq

import io.github.chronosauros.contour.usb.DacHandle

internal fun DacHandle.modelKey() = "${target.caps.name}:${usb?.vendorId}:${usb?.productId}"

internal fun DacHandle.serial(): String? = runCatching {
    usb?.serialNumber
}
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
