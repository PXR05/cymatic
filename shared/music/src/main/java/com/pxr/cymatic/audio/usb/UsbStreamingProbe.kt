package com.pxr.cymatic.audio.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class UsbStreamingProbe(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val activeDevice = AtomicReference<UsbPcmDevice?>(null)
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
        activeDevice.get()?.cancelProbe()
    }

    suspend fun run(device: UsbDevice, rate: Int): JSONObject = withContext(Dispatchers.IO) {
        cancelled.set(false)
        val report = JSONObject().put("capturedAt", Instant.now().toString())
            .put("vendorId", device.vendorId).put("productId", device.productId)
            .put("sampleRateHz", rate).put("source", "Two seconds of signed 16-bit stereo silence")
            .put("digitalEqualityVerified", false).put("streamingCompleted", false)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            ).setOnAudioFocusChangeListener(
                { if (it < 0) cancel() },
                Handler(Looper.getMainLooper()),
            ).build()
        var granted = false
        var output: UsbPcmDevice? = null
        try {
            require(rate == 44100 || rate == 48000)
            granted =
                audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            check(granted) { "Exclusive audio focus was not granted" }
            currentCoroutineContext().ensureActive()
            output = UsbPcmDevice.open(context, rate, device, requireVolume = false)
            activeDevice.set(output)
            currentCoroutineContext().ensureActive()
            check(!cancelled.get()) { "USB probe was cancelled" }
            val pcm = ByteArray(rate * 4 * 2)
            val result = output.writeProbe(pcm)
            report.put("transferError", result.error).put("completedPackets", result.packets)
                .put("completedFrames", result.frames).put("completedBytes", result.bytes)
                .put("packetErrors", result.packetErrors).put("shortPackets", result.shortPackets)
                .put("completedTransfers", result.transfers).put(
                    "streamingCompleted",
                    result.error == 0 && result.bytes == rate * 2L * output.layout.frameBytes && result.frames == rate * 2L,
                )
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            report.put("error", e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                output?.close()
                output?.report?.let { details ->
                    details.keys().forEach { report.put(it, details.get(it)) }
                }
            } finally {
                activeDevice.set(null)
                if (granted) audioManager.abandonAudioFocusRequest(focus)
            }
        }
        report
    }
}
