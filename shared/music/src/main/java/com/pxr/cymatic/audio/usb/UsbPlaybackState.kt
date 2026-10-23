package com.pxr.cymatic.audio.usb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object UsbPlaybackState {
    @Volatile
    internal var enabled = false

    @Volatile
    internal var deviceReady = false
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    private val mutableVolumeActive = MutableStateFlow(false)
    val volumeActive = mutableVolumeActive.asStateFlow()
    @Volatile
    internal var routeToUsb = false
        set(value) {
            field = value
            mutableVolumeActive.value = mutableActive.value || value
        }
    private val mutableStatus = MutableStateFlow("Off")
    val status = mutableStatus.asStateFlow()
    private val mutableReports = MutableStateFlow<List<String>>(emptyList())
    val sessionReports = mutableReports.asStateFlow()

    internal fun setActive(value: Boolean) {
        mutableActive.value = value
        mutableVolumeActive.value = value || routeToUsb
    }

    internal fun update(message: String) {
        mutableStatus.value = message
    }

    internal fun record(report: JSONObject) {
        mutableReports.value = (mutableReports.value + report.toString()).takeLast(10)
    }
}
