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
    internal fun setRequested(percent: Int) {
        mutableRequested.value = percent.coerceIn(0, 100)
        deviceKey?.let { saves.trySend(it to mutableRequested.value) }
    }

    @Synchronized
    internal fun beginDevice(key: String) {
        if (deviceKey != key) {
            // Configuration runs on the audio thread. Restore before hardware volume is applied.
            val remembered =
                runBlocking(Dispatchers.IO) { DeviceVolumeSettings.get(key, direct = true) }
            deviceKey = key
            mutableRequested.value = (remembered ?: 25).coerceIn(0, 100)
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
