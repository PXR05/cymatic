// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.devices.GeneratedDevices

object MoondropCatalog {

    private val dawn = GeneratedDevices.MOONDROP_DAWN_PRO_2
    const val VENDOR_ID = GeneratedDevices.MOONDROP_DAWN_PRO_2_VENDOR_ID
    const val REPORT_ID = GeneratedDevices.MOONDROP_DAWN_PRO_2_REPORT_ID

    val TYPES: Map<Int, FilterType> = dawn.nativeTypes

    private val RECORD_OFFERED_TYPES: Set<FilterType> = dawn.offeredTypes

    const val PASS_FILTERS_VERIFIED = true

    const val SHELVES_VERIFIED = true

    val passFiltersOn: Boolean
        get() = Proven.on(PASS_FILTERS_VERIFIED)

    val shelvesOn: Boolean
        get() = Proven.on(SHELVES_VERIFIED)

    val OFFERED_TYPES: Set<FilterType>
        get() =
            RECORD_OFFERED_TYPES +
                (if (passFiltersOn) setOf(FilterType.LOW_PASS, FilterType.HIGH_PASS)
                else emptySet()) +
                (if (shelvesOn) setOf(FilterType.LOW_SHELF, FilterType.HIGH_SHELF) else emptySet())

    data class Model(
        val productName: String,
        val productIds: Set<Int>,
        val bandCount: Int,
        val reportId: Int,
        val writableIndex: Int,
        val writableIndexes: Set<Int>,
        val minGainDb: Double,
        val maxGainDb: Double,
        val minQ: Double,
        val maxQ: Double,
        val shelfQScale: Double,
        val minFrequencyHz: Double,
        val maxFrequencyHz: Double,
        val preGainMinDb: Double,
        val preGainMaxDb: Double,
        val globalGainMinDb: Double,
        val globalGainMaxDb: Double,
        val reportBytes: Int,
        val hardwareVerified: Boolean = false,
    ) {

        fun isWritable(index: Int): Boolean = index in writableIndexes

        val nativeTypes: Set<Int>
            get() = TYPES.keys

        val types: Set<FilterType>
            get() = TYPES.values.toSet()
    }

    val dawnPro2 =
        Model(
            dawn.productName,
            setOf(dawn.productId),
            dawn.bands,
            dawn.reportId,
            dawn.writableIndex,
            dawn.writableIndexes,
            dawn.gainMinDb,
            dawn.gainMaxDb,
            dawn.qMin,
            dawn.qMax,
            dawn.shelfQScale,
            dawn.freqMinHz,
            dawn.freqMaxHz,
            dawn.preGainMinDb,
            dawn.preGainMaxDb,
            dawn.globalGainMinDb,
            dawn.globalGainMaxDb,
            dawn.reportBytes,
            hardwareVerified = true,
        )
    val models = listOf(dawnPro2)

    fun find(vendorId: Int, productId: Int, productName: String?): Model? =
        if (GeneratedDevices.MOONDROP_DAWN_PRO_2_USB.matches(vendorId, productId, productName))
            dawnPro2
        else null
}
