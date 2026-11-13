// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.devices.GeneratedDevices

object FiioJa11 {

    const val PASS_FILTERS_VERIFIED = true

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    private val PASS_TYPES = setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS)

    private val RECORD: FiioConfig = GeneratedDevices.FIIO_JA11
    private val WITHOUT_PASS: FiioConfig = RECORD.copy(typeCodes = RECORD.typeCodes - PASS_TYPES)

    val CONFIG: FiioConfig
        get() = if (passFiltersOn) RECORD else WITHOUT_PASS

    internal fun isRecord(c: FiioConfig): Boolean = c === RECORD
}
