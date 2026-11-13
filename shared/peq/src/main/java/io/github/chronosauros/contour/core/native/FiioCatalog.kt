// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.devices.GeneratedDevices

data class FiioCaptureIdentity(
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val fixturePath: String,
)

data class FiioConfig(
    val productName: String,
    val vendorIds: Set<Int>,
    val reportId: Int,
    val maxFilters: Int,
    val minGainDb: Double,
    val maxGainDb: Double,
    val minQ: Double,
    val maxQ: Double,
    val saveCommand: Int,
    val userSlots: Set<Int>,
    val declaredWritableSlots: Set<Int>,
    val stockSlots: Set<Int>,
    val slotLabels: Map<Int, String>,
    val bypassSlot: Int?,
    val disconnectOnSave: Boolean,
    val peakingGainCompensation: Boolean,
    val shelfAlphaCompensation: Boolean,
    val sourceUrl: String,
    val constraintsUrl: String,
    val constraintsRef: String,
    val upstreamExperimental: Boolean,
    val bestGuessProductName: Boolean,
    val capture: FiioCaptureIdentity?,
    val codecBlockers: List<String>,
    val evidenceCaveats: List<String>,
    val status: String = "beta-unverified",
    val typeCodes: Map<FilterType, Int> =
        mapOf(FilterType.PEAK to 0, FilterType.LOW_SHELF to 1, FilterType.HIGH_SHELF to 2),
    val minFrequencyHz: Int = 20,
    val maxFrequencyHz: Int = 20000,
    val fixedBandCount: Boolean = false,
    val qReadbackSlack: Boolean = false,
    val consumeWriteEchoes: Boolean = false,
    val writeGapMs: Int = 0,
    val afterSaveMs: Int = 800,
    val checksumFrames: Boolean = false,
    val needsAudioStream: Boolean = false,
    val userSlotNames: Boolean = false,
    val userSlotPicker: Boolean = false,
    val shelfQScale: Double = 1.0,
    val peakQScale: Double = 1.0,
    val preampOffsetDb: Double = 0.0,
    val preampWholeDb: Boolean = false,
    val hardwareVerified: Boolean = false,
    val snowskyExchange: Boolean = false,
    val shortName: String? = null,
    val nameBySlotId: Boolean = false,
    val nameLength: Int = 7,
    val settings: List<DacSetting> = emptyList(),
    val probeGapMs: Int? = 100,
    val info: List<DacInfo> = emptyList(),
    val settingsPreview: Map<String, Int> = emptyMap(),
) {

    val preampMinDb: Double
        get() = minGainDb - preampOffsetDb

    val preampMaxDb: Double
        get() = maxGainDb - preampOffsetDb

    val shelfQMin: Double
        get() = minQ / shelfQScale

    val shelfQMax: Double
        get() = maxQ / shelfQScale

    val peakQMin: Double
        get() = minQ / peakQScale

    val peakQMax: Double
        get() = maxQ / peakQScale

    val hardwareGates: List<String>
        get() =
            listOf(
                "Verify exact USB identity and HID interface/descriptor/report size before I/O",
                "Verify selection, complete backup, post-save native readback on actual firmware",
                "Persistence across power cycle and acoustic behaviour unverified",
            )
}

object FiioCatalog {

    const val PINNED_COMMIT = "0617f382e76629792a5933e6933e4b396a756a93"

    const val K13_R2R_PRODUCT_ID = GeneratedDevices.FIIO_K13_R2R_PRODUCT_ID

    const val MELODY_PRODUCT_ID = GeneratedDevices.SNOWSKY_MELODY_PRODUCT_ID
    const val TINY_A_PRODUCT_ID = GeneratedDevices.SNOWSKY_TINY_A_PRODUCT_ID

    const val JA11_PRODUCT_ID = GeneratedDevices.FIIO_JA11_PRODUCT_ID

    val rules: List<FiioConfig> =
        listOf(
            GeneratedDevices.FIIO_KA15,
            GeneratedDevices.FIIO_K13_R2R,
            GeneratedDevices.SNOWSKY_MELODY,
            GeneratedDevices.SNOWSKY_TINY_A,
            GeneratedDevices.FIIO_JA11,
        )

    val MELODY: FiioConfig = GeneratedDevices.SNOWSKY_MELODY
    val TINY_A: FiioConfig = GeneratedDevices.SNOWSKY_TINY_A

    val KA15: FiioConfig = GeneratedDevices.FIIO_KA15

    val K13: FiioConfig = GeneratedDevices.FIIO_K13_R2R

    val JA11: FiioConfig = GeneratedDevices.FIIO_JA11

    fun capturedRoute(vendorId: Int, productId: Int, productName: String): FiioConfig? =
        rules.singleOrNull { rule ->
            rule.codecBlockers.isEmpty() &&
                rule.capture?.let {
                    it.vendorId == vendorId &&
                        it.productId == productId &&
                        it.productName == productName
                } == true
        }
}
