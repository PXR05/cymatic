package com.pxr.cymatic.audio.usb

import org.json.JSONObject

internal fun JSONObject.putUsbStreamStatistics(stats: LongArray): JSONObject {
    require(stats.size >= 7)
    put("completedFrames", stats[1])
        .put("pendingBytesAtClose", stats[2])
        .put("packetErrors", stats[3])
        .put("shortPackets", stats[4])
        .put("underruns", stats[5])
        .put("completedBytes", stats[6])
        .put("transferError", stats[0])
    if (stats.size >= 17) {
        put("completedPackets", stats[15]).put("completedTransfers", stats[16])
        if (
            optJSONObject("transport")?.optString("synchronization") ==
            "ASYNCHRONOUS_EXPLICIT_FEEDBACK"
        ) {
            val ticks = if (optString("usbSpeed") == "HIGH") 8000 else 1000
            put(
                "feedback",
                JSONObject()
                    .put("acceptedPackets", stats[7])
                    .put("rejectedPackets", stats[8])
                    .put("emptyPackets", stats[9])
                    .put("packetErrors", stats[10])
                    .put("timeouts", stats[14])
                    .put("rateConfirmed", stats[7] > 0)
                    .put("lastSamplesPerTickQ16", stats[11])
                    .put("minimumSamplesPerTickQ16", stats[12])
                    .put("maximumSamplesPerTickQ16", stats[13])
                    .put("usbTicksPerSecond", ticks)
                    .put(
                        "lastRateHz",
                        if (stats[7] > 0) stats[11] * ticks / 65536.0 else JSONObject.NULL,
                    )
                    .put(
                        "minimumRateHz",
                        if (stats[7] > 0) stats[12] * ticks / 65536.0 else JSONObject.NULL,
                    )
                    .put(
                        "maximumRateHz",
                        if (stats[7] > 0) stats[13] * ticks / 65536.0 else JSONObject.NULL,
                    ),
            )
            if (stats.size >= 19) {
                getJSONObject("feedback")
                    .put("lastPayloadUnsigned", stats[17])
                    .put("lastPayloadBytes", stats[18])
            }
        }
    }
    if (stats.size >= 22) {
        put("completedClockPrimingFrames", stats[19])
            .put("pendingClockPrimingFrames", stats[20])
            .put("completedWireBytes", stats[21])
    }
    return this
}
