// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.devices

import io.github.chronosauros.contour.core.DeviceCapabilities
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.ShelfLimits

internal enum class Recognition {
    FIND,
    FIND_DEVICE,
}

internal data class UsbIdentity(
    val recordId: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String?,
    val matchByName: Boolean,
    val nameAliases: List<String>,
    val recognition: Recognition,
) {
    fun matches(vendorId: Int, productId: Int, productName: String?): Boolean =
        this.vendorId == vendorId &&
            this.productId == productId &&
            (!matchByName ||
                (productName != null &&
                    (productName == this.productName || productName in nameAliases)))
}

internal data class FlatBand(val freqHz: Int, val q: Double)

internal data class WalkPlayData(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val usbName: String?,
    val schemeNo: Int,
    val reportId: Int,
    val bandLen: Int,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val freqMinHz: Double,
    val freqMaxHz: Double,
    val types: Set<FilterType>,
    val shelfQScale: Double,
    val coefficientRateHz: Double,
    val preampWholeDb: Boolean,
    val preampMinDb: Int,
    val preampMaxDb: Int,
    val factoryFlat: List<FlatBand>,
    val readBoundsHz: IntRange?,
    val readBoundsDb: ClosedFloatingPointRange<Double>?,
) {
    fun caps() =
        DeviceCapabilities(
            name,
            vendorId,
            productId,
            bands,
            gainMinDb,
            gainMaxDb,
            qMin,
            qMax,
            freqMinHz,
            freqMaxHz,
            types,
            preampWholeDb,
            shelfQScale = shelfQScale,
            coefficientRateHz = coefficientRateHz,
        )
}

internal data class ReadOnlyWalkPlayData(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val usbName: String,
    val schemeNo: Int,
    val reportId: Int,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val types: Set<FilterType>,
    val coefficientRateHz: Double,
)

internal data class MoondropData(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val reportId: Int,
    val reportBytes: Int,
    val writableIndex: Int,
    val writableIndexes: Set<Int>,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val freqMinHz: Double,
    val freqMaxHz: Double,
    val offeredTypes: Set<FilterType>,
    val nativeTypes: Map<Int, FilterType>,
    val shelfQScale: Double,
    val preGainMinDb: Double,
    val preGainMaxDb: Double,
    val globalGainMinDb: Double,
    val globalGainMaxDb: Double,
)

internal data class KtMicroData(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val nameAliases: List<String>,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val freqMinHz: Double,
    val freqMaxHz: Double,
    val types: Set<FilterType>,
    val bandRegisterBase: Int,
    val enableRegister: Int,
    val digitalGainRegister: Int,
    val writeAck: Int,
    val globalGainUserSteps: IntRange?,
    val designRateHz: Double,
    val assumedStreamHz: Double,
    val shelfQScale: Double,
    val reconnectAfterSave: Boolean?,
    val resetOffered: Boolean,
    val eqEnableStatusBytes: Boolean,
    val sendTurnsEqOn: Boolean,
    val shelfLimits: ShelfLimits? = null,
)

internal data class Gen1PresetBand(val freqHz: Double, val gainDb: Double, val q: Double)

internal data class MoondropGen1Data(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val reportId: Int,
    val reportBytes: Int,
    val bands: Int,
    val gainMinDb: Double,
    val gainMaxDb: Double,
    val qMin: Double,
    val qMax: Double,
    val freqMinHz: Double,
    val freqMaxHz: Double,
    val types: Set<FilterType>,
    val presetLabels: Map<Int, String>,
    val customIndex: Int,
    val stockPicker: Boolean,
    val stockPickerInline: Boolean,
    val factoryFrequenciesHz: List<Int>,
    val factoryQ: Double,
    val replyWaitMs: Long,
    val writeGapMs: Long,
    val afterCommitMs: Long,
    val afterSelectMs: Long,
    val restartLeavesAfterMs: Long,
    val restartAwayMinMs: Long,
    val restartAwayMaxMs: Long,
    val reconnectGoneWaitMs: Long,
    val reconnectBackWaitMs: Long,
    val powerUpPreset: Int,
    val presetBands: Map<Int, List<Gen1PresetBand>>,
    val hardwareVerified: Boolean,
)
