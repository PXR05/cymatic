// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.devices.GeneratedDevices

object FiioKa15 {

    const val PASS_FILTERS_VERIFIED = true

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    private val PLAIN: FiioConfig = GeneratedDevices.FIIO_KA15
    private val WITH_PASS: FiioConfig =
        PLAIN.copy(typeCodes = PLAIN.typeCodes + SnowSkyMelody.PASS_TYPE_CODES)

    val CONFIG: FiioConfig
        get() = if (passFiltersOn) WITH_PASS else PLAIN

    fun config(passFilters: Boolean): FiioConfig = if (passFilters) WITH_PASS else PLAIN

    internal fun isRecord(c: FiioConfig): Boolean = c === PLAIN
}
