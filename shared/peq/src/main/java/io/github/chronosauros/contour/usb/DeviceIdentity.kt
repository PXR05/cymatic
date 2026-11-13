// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.usb

import android.hardware.usb.UsbDevice
import io.github.chronosauros.contour.core.DeviceTarget

internal object DeviceIdentity {
    fun target(device: UsbDevice): DeviceTarget? =
        DeviceTarget.findDevice(device.vendorId, device.productId, device.productName)
}
