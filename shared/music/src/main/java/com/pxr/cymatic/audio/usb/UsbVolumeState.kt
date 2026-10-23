package com.pxr.cymatic.audio.usb

import com.pxr.cymatic.data.store.DeviceVolumeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    @Volatile private var deviceKey: String? = null
    internal val volumeProfileKey: String?
        get() = deviceKey
    private var unmutedPercent = 25

    private val saves = Channel<Pair<String, Int>>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for ((key, percent) in saves) DeviceVolumeSettings.save(key, direct = true, percent)
        }
    }

    @Synchronized
    fun adjust(steps: Int) {
        setRequested(mutableRequested.value + steps)
    }

    @Synchronized
    fun setRequested(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        if (clamped > 0) unmutedPercent = clamped
        mutableRequested.value = clamped
        if (!UsbPlaybackState.active.value) {
            mutableLevel.value =
                UsbVolumeLevel(
                    message = if (clamped == 0) "DAC hardware mute" else "DAC hardware volume",
                    muted = clamped == 0,
                )
        }
        deviceKey?.let { saves.trySend(it to clamped) }
    }

    @Synchronized
    fun setMuted(muted: Boolean) {
        if (muted) {
            if (mutableRequested.value > 0) unmutedPercent = mutableRequested.value
            setRequested(0)
        } else if (mutableRequested.value == 0) {
            setRequested(unmutedPercent.coerceAtLeast(2))
        }
    }

    @Synchronized
    internal fun beginDevice(key: String) {
        if (deviceKey != key) {
            val remembered =
                runBlocking(Dispatchers.IO) { DeviceVolumeSettings.get(key, direct = true) }
            deviceKey = key
            val initial = (remembered ?: 25).coerceIn(0, 100)
            if (initial > 0) unmutedPercent = initial
            mutableRequested.value = initial
        }
        if (!UsbPlaybackState.active.value) {
            val muted = mutableRequested.value == 0
            mutableLevel.value =
                UsbVolumeLevel(
                    message = if (muted) "DAC hardware mute" else "DAC hardware volume",
                    muted = muted,
                )
        }
    }

    @Synchronized
    internal fun standby() {
        val muted = mutableRequested.value == 0
        mutableLevel.value =
            UsbVolumeLevel(
                message = if (muted) "DAC hardware mute" else "DAC hardware volume",
                muted = muted,
            )
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
