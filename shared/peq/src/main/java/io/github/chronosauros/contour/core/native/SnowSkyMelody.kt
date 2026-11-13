// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.devices.GeneratedDevices

object SnowSkyMelody {

    const val PASS_FILTERS_VERIFIED = true

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    val PASS_TYPE_CODES: Map<FilterType, Int> =
        mapOf(
            FilterType.BAND_PASS to 3,
            FilterType.LOW_PASS to 4,
            FilterType.HIGH_PASS to 5,
            FilterType.ALL_PASS to 6,
        )

    private val PLAIN: FiioConfig = GeneratedDevices.SNOWSKY_MELODY
    private val WITH_PASS: FiioConfig = PLAIN.copy(typeCodes = PLAIN.typeCodes + PASS_TYPE_CODES)

    val CONFIG: FiioConfig
        get() = if (passFiltersOn) WITH_PASS else PLAIN

    fun config(passFilters: Boolean): FiioConfig = if (passFilters) WITH_PASS else PLAIN

    internal fun isRecord(c: FiioConfig): Boolean = c === PLAIN
}
