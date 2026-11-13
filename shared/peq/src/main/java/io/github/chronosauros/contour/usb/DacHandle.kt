// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.usb

import android.hardware.usb.UsbDevice
import io.github.chronosauros.contour.core.DeviceTarget

class DacHandle(
    val usb: UsbDevice?,
    val target: DeviceTarget,
    val key: String,
    val simulated: ((guard: () -> Unit) -> HidIo)? = null,
) {
    companion object {

        fun of(device: UsbDevice): DacHandle? =
            DeviceIdentity.target(device)?.let {
                DacHandle(
                    device,
                    it,
                    "${device.deviceName}:${device.deviceId}:${device.vendorId}:${device.productId}",
                )
            }
    }
}
