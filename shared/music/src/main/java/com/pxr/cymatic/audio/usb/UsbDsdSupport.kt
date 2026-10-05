package com.pxr.cymatic.audio.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.dsd.dsdOutputFormat
import com.pxr.cymatic.audio.dsd.dsfHeader
import com.pxr.cymatic.data.store.DsdUsbMode
import com.pxr.cymatic.data.store.UsbPlaybackSettings
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

internal fun UsbDevice.dsdSettingsKey(): String =
    "$vendorId:$productId:${runCatching { serialNumber }.getOrNull().orEmpty()}"

@UnstableApi
internal object UsbDsdSupport {
    private data class Capability(val rates: Set<Int>)

    @Volatile private var capability: Capability? = null
    @Volatile private var selectedMode: DsdUsbMode? = null
    @Volatile private var knownInterface = false
    private val failedRates = ConcurrentHashMap.newKeySet<Int>()

    fun refresh(context: Context, device: UsbDevice) {
        capability = null
        failedRates.clear()
        val manager = context.getSystemService(UsbManager::class.java)
        val key = device.dsdSettingsKey()
        val mode = UsbPlaybackSettings.dsdMode(key)
        val known = device.vendorId == 0x16d0 && device.productId == 0x071a
        selectedMode = mode
        knownInterface = known
        if (mode == DsdUsbMode.PCM || (mode == DsdUsbMode.AUTO && !known)) return
        if (!manager.hasPermission(device)) return
        val connection = manager.openDevice(device) ?: return
        try {
            val parsed = UsbAudioDescriptors.parse(connection.rawDescriptors ?: byteArrayOf())
            val current = ByteArray(1)
            if (connection.controlTransfer(0x80, 8, 0, 0, current, 1, 500) != 1) return
            val configuration = current.u8(0)
            val eligible =
                parsed.formats.filter {
                    it.configuration == configuration &&
                        it.pcm &&
                        it.channels in 1..2 &&
                        it.containerBytes in 3..4 &&
                        it.validBits >= 24 &&
                        it.controlInterface != null
                }
            val clocks = mutableMapOf<UsbClockSource, List<Triple<Long, Long, Long>>>()
            val rates =
                setOf(176400, 192000, 352800, 384000)
                    .filter { rate ->
                        eligible.any { format ->
                            if (format.protocol == 0) format.advertisesRate(rate)
                            else {
                                val clock =
                                    parsed.clocks.singleOrNull {
                                        it.configuration == configuration &&
                                            it.id == format.clockSourceId &&
                                            it.controlInterface == format.controlInterface
                                    }
                                clock != null &&
                                    runCatching {
                                            clocks
                                                .getOrPut(clock) { readRanges(connection, clock) }
                                                .any { (low, high, step) ->
                                                    rate.toLong() in low..high &&
                                                        (step == 0L || (rate - low) % step == 0L)
                                                }
                                        }
                                        .getOrDefault(true)
                            }
                        }
                    }
                    .toSet()
            capability = Capability(rates)
        } catch (_: RuntimeException) {
            capability = null
        } finally {
            connection.close()
        }
    }

    fun clear() {
        capability = null
        selectedMode = null
        knownInterface = false
        failedRates.clear()
    }

    fun outputFormat(format: Format): Format? {
        val info = format.dsfHeader() ?: return null
        val available = capability
        val carrierRate = info.sampleRate / 16
        val dop =
            UsbPlaybackState.routeToUsb &&
                available != null &&
                carrierRate in available.rates &&
                carrierRate !in failedRates
        return format.dsdOutputFormat(dop)
    }

    fun fallback(rate: Int): Boolean = failedRates.add(rate)

    fun diagnosticSnapshot(): JSONObject =
        JSONObject()
            .put("selectedMode", selectedMode?.label ?: "No ready DAC")
            .put("knownDopInterface", knownInterface)
            .put(
                "candidateCarrierRatesHz",
                JSONArray(capability?.rates?.sorted() ?: emptyList<Int>()),
            )
            .put("failedCarrierRatesHz", JSONArray(failedRates.toList().sorted()))
            .put(
                "carrierRateValidation",
                "Confirmed during USB initialization; candidate rates alone do not prove active output",
            )
}
