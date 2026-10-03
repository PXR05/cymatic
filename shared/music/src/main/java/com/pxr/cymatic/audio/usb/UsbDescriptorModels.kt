package com.pxr.cymatic.audio.usb

import org.json.JSONArray
import org.json.JSONObject

internal data class UsbPcmFormat(
    val configuration: Int,
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val protocol: Int,
    val channels: Int?,
    val containerBytes: Int,
    val validBits: Int,
    val pcm: Boolean,
    val sampleRates: List<Int>,
    val continuousRates: Pair<Int, Int>?,
    val terminalLink: Int? = null,
    val clockSourceId: Int? = null,
    val controlInterface: Int? = null,
    val channelMask: Long? = null,
) {
    fun json() =
        JSONObject()
            .put("configuration", configuration)
            .put("interface", interfaceNumber)
            .put("alternateSetting", alternateSetting)
            .put("protocol", protocol)
            .put("channels", channels ?: JSONObject.NULL)
            .put("containerBytes", containerBytes)
            .put("validBits", validBits)
            .put("pcm", pcm)
            .put("terminalLink", terminalLink ?: JSONObject.NULL)
            .put("clockSourceId", clockSourceId ?: JSONObject.NULL)
            .put("controlInterface", controlInterface ?: JSONObject.NULL)
            .put("channelMask", channelMask ?: JSONObject.NULL)
            .put("sampleRatesHz", JSONArray(sampleRates))
            .put(
                "continuousRatesHz",
                continuousRates?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL,
            )
}

internal data class UsbClockSource(
    val configuration: Int,
    val controlInterface: Int,
    val id: Int,
    val frequencyReadable: Boolean,
    val frequencyWritable: Boolean,
    val validityReadable: Boolean,
)

internal data class UsbDescriptorSummary(
    val formats: List<UsbPcmFormat>,
    val clocks: List<UsbClockSource>,
    val features: List<UsbFeatureUnit>,
    val outputs: List<UsbOutputTerminal>,
    val warnings: List<String>,
) {
    fun playbackVolume(format: UsbPcmFormat): UsbFeatureUnit? {
        val units = features.filter {
            it.configuration == format.configuration &&
                    it.controlInterface == format.controlInterface
        }
        val paths =
            outputs
                .filter {
                    it.configuration == format.configuration &&
                            it.controlInterface == format.controlInterface &&
                            it.type shr 8 != 1
                }
                .map { output ->
                    val path = mutableListOf<UsbFeatureUnit>()
                    val visited = mutableSetOf<Int>()
                    var source = output.sourceId
                    while (source != format.terminalLink) {
                        if (!visited.add(source)) return null
                        val unit = units.singleOrNull { it.id == source } ?: return null
                        path += unit
                        source = unit.sourceId
                    }
                    path
                }
        if (paths.isEmpty()) return null
        val candidates = paths.map { path ->
            path
                .filter {
                    it.volumeChannels != null &&
                            it.protocol == format.protocol &&
                            it.controls.size in listOf(1, (format.channels ?: 0) + 1)
                }
                .singleOrNull() ?: return null
        }
        return candidates.distinct().singleOrNull()
    }
}

internal data class UsbFeatureUnit(
    val configuration: Int,
    val controlInterface: Int,
    val id: Int,
    val sourceId: Int,
    val controls: List<Long>,
    val protocol: Int = 0x20,
) {
    fun access(channel: Int, selector: Int): Int {
        val bits = controls.getOrNull(channel) ?: return 0
        return if (protocol == 0) {
            if (bits and (1L shl (selector - 1)) != 0L) 3 else 0
        } else ((bits shr ((selector - 1) * 2)) and 3L).toInt()
    }

    private fun writableChannels(selector: Int): List<Int>? =
        when {
            access(0, selector) == 3 -> listOf(0)
            controls.size in 2..3 && (1 until controls.size).all { access(it, selector) == 3 } ->
                (1 until controls.size).toList()

            else -> null
        }

    val volumeChannels: List<Int>?
        get() = writableChannels(2)

    val muteChannels: List<Int>?
        get() = writableChannels(1)

    fun json() =
        JSONObject()
            .put("configuration", configuration)
            .put("controlInterface", controlInterface)
            .put("id", id)
            .put("sourceId", sourceId)
            .put("protocol", protocol)
            .put("controls", JSONArray(controls))
            .put("writableVolumeChannels", volumeChannels?.let(::JSONArray) ?: JSONObject.NULL)
            .put("writableMuteChannels", muteChannels?.let(::JSONArray) ?: JSONObject.NULL)
}

internal data class UsbOutputTerminal(
    val configuration: Int,
    val controlInterface: Int,
    val id: Int,
    val sourceId: Int,
    val type: Int,
)
