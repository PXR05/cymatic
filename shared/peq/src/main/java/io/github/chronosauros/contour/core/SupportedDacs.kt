// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import io.github.chronosauros.contour.core.devices.GeneratedDevices

object SupportedDacs {
    val names: List<String> = GeneratedDevices.USB_IDS.map { it.name }
}
