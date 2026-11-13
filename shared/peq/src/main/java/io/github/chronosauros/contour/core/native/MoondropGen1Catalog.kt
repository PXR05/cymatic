// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.Band
import io.github.chronosauros.contour.core.DeviceCapabilities
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.devices.GeneratedDevices
import io.github.chronosauros.contour.core.devices.MoondropGen1Data

object MoondropGen1Catalog {
    private val dusk = GeneratedDevices.MOONDROP_DUSK
    const val VENDOR_ID = GeneratedDevices.MOONDROP_DUSK_VENDOR_ID
    const val REPORT_ID = 1

    const val REPORT_BYTES = 62

    data class Model(
        val productName: String,
        val productIds: Set<Int>,
        val reportBytes: Int,
        val hardwareVerified: Boolean,
    ) {
        internal val data: MoondropGen1Data
            get() = dusk

        val name: String
            get() = data.name

        val bandCount: Int
            get() = data.bands

        val minGainDb: Double
            get() = data.gainMinDb

        val maxGainDb: Double
            get() = data.gainMaxDb

        val minQ: Double
            get() = data.qMin

        val maxQ: Double
            get() = data.qMax

        val minFrequencyHz: Double
            get() = data.freqMinHz

        val maxFrequencyHz: Double
            get() = data.freqMaxHz

        val types: Set<FilterType>
            get() = data.types

        val presetLabels: Map<Int, String>
            get() = data.presetLabels

        val customIndex: Int
            get() = data.customIndex

        val stockPicker: Boolean
            get() = data.stockPicker

        val stockPickerInline: Boolean
            get() = data.stockPickerInline

        val factoryFrequenciesHz: List<Int>
            get() = data.factoryFrequenciesHz

        val factoryQ: Double
            get() = data.factoryQ

        val replyWaitMs: Long
            get() = data.replyWaitMs

        val writeGapMs: Long
            get() = data.writeGapMs

        val afterCommitMs: Long
            get() = data.afterCommitMs

        val afterSelectMs: Long
            get() = data.afterSelectMs

        val restartLeavesAfterMs: Long
            get() = data.restartLeavesAfterMs

        val restartAwayMinMs: Long
            get() = data.restartAwayMinMs

        val restartAwayMaxMs: Long
            get() = data.restartAwayMaxMs

        val reconnectGoneWaitMs: Long
            get() = data.reconnectGoneWaitMs

        val reconnectBackWaitMs: Long
            get() = data.reconnectBackWaitMs

        val powerUpPreset: Int
            get() = data.powerUpPreset

        fun presetBands(index: Int): List<Band>? =
            data.presetBands[index]?.mapIndexed { i, b ->
                Band("preset$index-${i + 1}", FilterType.PEAK, b.freqHz, b.gainDb, b.q)
            }

        fun presetName(index: Int): String = presetLabels[index] ?: "preset $index"

        fun isFirmwarePreset(index: Int): Boolean = index != customIndex

        fun caps(): DeviceCapabilities =
            DeviceCapabilities(
                name,
                VENDOR_ID,
                productIds.min(),
                bandCount,
                minGainDb,
                maxGainDb,
                minQ,
                maxQ,
                minFrequencyHz,
                maxFrequencyHz,
                types,
                false,
            )
    }

    val duskSp =
        Model(
            dusk.productName,
            setOf(dusk.productId),
            dusk.reportBytes,
            hardwareVerified = dusk.hardwareVerified,
        )
    val models = listOf(duskSp)

    fun find(vendorId: Int, productId: Int, productName: String?): Model? =
        if (GeneratedDevices.MOONDROP_DUSK_USB.matches(vendorId, productId, productName)) duskSp
        else null
}
