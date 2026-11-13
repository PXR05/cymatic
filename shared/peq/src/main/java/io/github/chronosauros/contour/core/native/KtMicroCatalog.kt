// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.DeviceCapabilities
import io.github.chronosauros.contour.core.FilterType
import io.github.chronosauros.contour.core.ShelfLimits
import io.github.chronosauros.contour.core.devices.GeneratedDevices
import io.github.chronosauros.contour.core.devices.KtMicroData
import io.github.chronosauros.contour.core.devices.PendingRecords
import io.github.chronosauros.contour.core.devices.UsbIdentity

object KtMicroCatalog {
    private val allegroData = GeneratedDevices.KIWI_EARS_ALLEGRO_MINI
    private val jm12Data = GeneratedDevices.JCALLY_JM12
    const val VENDOR_ID = GeneratedDevices.KIWI_EARS_ALLEGRO_MINI_VENDOR_ID

    data class RegisterPair(val gainFrequency: Int, val qType: Int)

    data class RegisterMap(
        val name: String,
        val bands: List<RegisterPair>,
        val enableRegister: Int,
        val writeAck: Int,
        val digitalGainRegister: Int,
    )

    data class Model(
        val productName: String,
        val productIds: Set<Int>,
        val registers: RegisterMap,
        val reconnectAfterSave: Boolean = PendingRecords.ALLEGRO_RECONNECT_AFTER_SAVE,
        val hardwareVerified: Boolean = false,
    ) {
        internal val data: KtMicroData
            get() = dataByProductName.getValue(productName)

        val name: String
            get() = data.name

        val nativeTypes: Set<FilterType>
            get() = data.types

        val bandCount: Int
            get() = registers.bands.size

        val minGainDb: Double
            get() = data.gainMinDb

        val maxGainDb: Double
            get() = data.gainMaxDb

        val minQ: Double
            get() = data.qMin

        val maxQ: Double
            get() = data.qMax

        val shelfQScale: Double
            get() = data.shelfQScale

        val designRateHz: Double
            get() = data.designRateHz

        val assumedStreamHz: Double
            get() = data.assumedStreamHz

        fun registerFrequency(intendedHz: Double): Double =
            intendedHz * designRateHz / assumedStreamHz

        fun intendedFrequency(register: Int): Double = register * assumedStreamHz / designRateHz

        val minFrequencyHz: Double
            get() = intendedFrequency(data.freqMinHz.toInt())

        val maxFrequencyHz: Double
            get() = minOf(data.freqMaxHz, intendedFrequency(data.freqMaxHz.toInt()))

        val globalGainUserSteps: IntRange?
            get() = data.globalGainUserSteps

        val sendTurnsEqOn: Boolean
            get() = data.sendTurnsEqOn

        val sendEqEnabled: Boolean?
            get() = if (sendTurnsEqOn) true else null

        val shelfLimits: ShelfLimits?
            get() = data.shelfLimits

        fun gainRange(type: FilterType): ClosedFloatingPointRange<Double> = caps().gainRange(type)

        fun qRange(type: FilterType): ClosedFloatingPointRange<Double> = caps().qRange(type)

        private fun caps() =
            DeviceCapabilities(
                name,
                0,
                0,
                bandCount,
                minGainDb,
                maxGainDb,
                minQ,
                maxQ,
                minFrequencyHz,
                maxFrequencyHz,
                nativeTypes,
                false,
                shelfQScale = shelfQScale,
                shelfLimits = shelfLimits,
            )

        val resetOffered: Boolean
            get() = data.resetOffered

        val eqEnableStatusBytes: Boolean
            get() = data.eqEnableStatusBytes
    }

    private val usbByRecord: List<Pair<UsbIdentity, KtMicroData>> =
        listOf(
            GeneratedDevices.KIWI_EARS_ALLEGRO_MINI_USB to allegroData,
            GeneratedDevices.JCALLY_JM12_USB to jm12Data,
        )

    private fun registerMap(chip: String, d: KtMicroData) =
        RegisterMap(
            chip,
            List(d.bands) {
                RegisterPair(d.bandRegisterBase + 2 * it, d.bandRegisterBase + 2 * it + 1)
            },
            d.enableRegister,
            d.writeAck,
            d.digitalGainRegister,
        )

    val kt0211 = registerMap("KT_0211L", allegroData)
    val kt02h20 = registerMap("KT02H20", jm12Data)

    private fun modelsOf(d: KtMicroData, map: RegisterMap, verified: Boolean): List<Model> =
        (listOf(d.productName) + d.nameAliases).map {
            Model(
                it,
                setOf(d.productId),
                map,
                d.reconnectAfterSave ?: PendingRecords.ALLEGRO_RECONNECT_AFTER_SAVE,
                hardwareVerified = verified,
            )
        }

    val models: List<Model> = modelsOf(allegroData, kt0211, verified = true)

    val jm12Models: List<Model> = modelsOf(jm12Data, kt02h20, verified = true)

    val allModels: List<Model> = models + jm12Models

    private val dataByProductName: Map<String, KtMicroData> =
        listOf(allegroData, jm12Data)
            .flatMap { d -> (listOf(d.productName) + d.nameAliases).map { it to d } }
            .toMap()
            .also {
                require(it.size == allModels.size) { "two KT models share a USB product name" }
            }

    fun find(vendorId: Int, productId: Int, productName: String?): Model? =
        usbByRecord.firstNotNullOfOrNull { (usb, _) ->
            if (!usb.matches(vendorId, productId, productName)) null
            else allModels.singleOrNull { it.productName == productName }
        }
}
