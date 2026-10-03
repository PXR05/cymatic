package com.pxr.cymatic.audio.usb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbVolumeLevel(
    val percent: Int? = null,
    val decibels: Float? = null,
    val message: String = "Waiting for DAC",
    val muted: Boolean = false,
)

object UsbVolumeState {
    private val mutableRequested = MutableStateFlow(25)
    val requestedPercent = mutableRequested.asStateFlow()
    private val mutableLevel = MutableStateFlow(UsbVolumeLevel())
    val level = mutableLevel.asStateFlow()
    private var deviceKey: String? = null

    @Synchronized
    fun adjust(steps: Int) {
        setRequested(mutableRequested.value + steps)
    }

    @Synchronized
    internal fun setRequested(percent: Int) {
        mutableRequested.value = percent.coerceIn(0, 100)
    }

    @Synchronized
    internal fun initialize(percent: Int) {
        deviceKey = null
        setRequested(minOf(percent, 25))
        mutableLevel.value = UsbVolumeLevel()
    }

    @Synchronized
    internal fun beginDevice(key: String) {
        if (deviceKey != key) {
            deviceKey = key
            setRequested(minOf(mutableRequested.value, 25))
        }
        mutableLevel.value = UsbVolumeLevel(message = "Reading DAC volume")
    }

    internal fun confirm(percent: Int, decibels: Float?, muted: Boolean) {
        mutableLevel.value =
            UsbVolumeLevel(
                percent,
                decibels,
                if (muted) "DAC hardware mute" else "DAC hardware volume",
                muted,
            )
    }

    internal fun unavailable(message: String) {
        mutableLevel.value = UsbVolumeLevel(message = message)
    }
}
