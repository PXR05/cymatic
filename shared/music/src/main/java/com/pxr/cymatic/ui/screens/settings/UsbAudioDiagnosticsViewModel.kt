package com.pxr.cymatic.ui.screens.settings

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.audio.usb.UsbAudioDiagnostics
import com.pxr.cymatic.audio.usb.UsbConnectionManager
import com.pxr.cymatic.audio.usb.UsbOutputOwnership
import com.pxr.cymatic.audio.usb.UsbStreamingProbe
import com.pxr.cymatic.audio.usb.isUsbAudioDevice
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal class UsbAudioDiagnosticsViewModel(application: Application) :
    AndroidViewModel(application) {
    private val context = application.applicationContext
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val diagnostics = UsbAudioDiagnostics(context)
    private val streamingProbe = UsbStreamingProbe(context)
    private val permissionAction = "${context.packageName}.USB_DIAGNOSTIC_PERMISSION"
    private val mutableState = MutableStateFlow(UsbDiagnosticsState())
    val state = mutableState.asStateFlow()
    private var started = false
    private var inspection: Job? = null
    private var refreshPending = false
    private var permissionIntent: PendingIntent? = null
    private var exportSnapshot: String? = null
    private var probe: Job? = null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device =
                    IntentCompat.getParcelableExtra(
                        intent,
                        UsbManager.EXTRA_DEVICE,
                        UsbDevice::class.java,
                    )
                when (intent.action) {
                    permissionAction -> {
                        if (
                            device == null ||
                            state.value.permissionDeviceId == null ||
                            device.deviceId != state.value.permissionDeviceId
                        )
                            return
                        val granted =
                            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) &&
                                    usbManager.hasPermission(device)
                        clearPermissionRequest()
                        if (granted) UsbConnectionManager.refresh(context)
                        message(if (granted) "USB access granted" else "USB access denied")
                        refresh()
                    }

                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        if (device == null || !isUsbAudioDevice(device)) return
                        val attached = intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED
                        if (!attached) streamingProbe.cancel()
                        event(if (attached) "USB audio attached" else "USB audio detached")
                        if (!attached && device.deviceId == state.value.permissionDeviceId) {
                            clearPermissionRequest()
                            message("Device disconnected")
                        }
                        refresh()
                    }
                }
            }
        }

    private val audioCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = refresh()

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = refresh()
        }

    fun start() {
        if (started) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(permissionAction)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))
        started = true
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(receiver)
        audioManager.unregisterAudioDeviceCallback(audioCallback)
        clearPermissionRequest()
        inspection?.cancel()
        streamingProbe.cancel()
        probe?.cancel()
        refreshPending = false
        mutableState.update { it.copy(inspecting = false) }
    }

    fun refresh() {
        if (!started) return
        if (state.value.probing) {
            refreshPending = true
            return
        }
        if (inspection?.isActive == true) {
            refreshPending = true
            return
        }
        inspection = viewModelScope.launch {
            mutableState.update { it.copy(inspecting = true) }
            try {
                val report = diagnostics.inspect()
                mutableState.update { it.copy(report = report) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message("Refresh failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                mutableState.update { it.copy(inspecting = false) }
            }
        }
        inspection?.invokeOnCompletion {
            viewModelScope.launch {
                if (started && refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    fun inspectDevice(device: UsbDevice) {
        if (state.value.permissionDeviceId != null) return
        val current = usbManager.deviceList[device.deviceName]
        if (current?.deviceId != device.deviceId) {
            message("Device disconnected; refreshing")
            refresh()
            return
        }
        if (usbManager.hasPermission(current)) {
            refresh()
            return
        }
        try {
            mutableState.update { it.copy(permissionDeviceId = device.deviceId) }
            permissionIntent =
                PendingIntent.getBroadcast(
                    context,
                    device.deviceId,
                    Intent(permissionAction).setPackage(context.packageName),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            usbManager.requestPermission(current, permissionIntent)
        } catch (e: RuntimeException) {
            clearPermissionRequest()
            message("Could not request USB access: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun prepareExport(): Boolean {
        if (state.value.exporting) return false
        exportSnapshot = state.value.exportJson() ?: return false
        mutableState.update { it.copy(exporting = true) }
        return true
    }

    fun runChecks(device: UsbDevice) {
        if (!started || state.value.probing || state.value.permissionDeviceId != null) return
        mutableState.update { it.copy(probing = true, message = "Checking DAC") }
        probe = viewModelScope.launch {
            var rate = 44100
            try {
                inspection?.cancelAndJoin()
                withTimeout(2000) {
                    while (UsbOutputOwnership.inUse) delay(20)
                }
                val results = mutableListOf<JSONObject>()
                for (frequency in listOf(44100, 48000)) {
                    ensureActive()
                    rate = frequency
                    message("Checking ${frequency / 1000f} kHz")
                    val report = streamingProbe.run(device, frequency)
                    results += report
                    mutableState.update {
                        it.copy(probeReports = (it.probeReports + report).takeLast(10))
                    }
                }
                val passed = results.count { it.optBoolean("streamingCompleted") }
                val warnings = results.any {
                    (it.optJSONArray("cleanupWarnings")?.length() ?: 0) > 0
                }
                message(
                    when {
                        passed < results.size -> "$passed/${results.size} passed · see details"
                        warnings -> "Checks passed · see notes"
                        else -> "Checks passed"
                    }
                )
            } catch (e: TimeoutCancellationException) {
                probeFailure(device, rate, "DAC is still in use")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                probeFailure(device, rate, e.message ?: e.javaClass.simpleName)
            } catch (e: LinkageError) {
                probeFailure(device, rate, "USB driver unavailable")
            } finally {
                mutableState.update { it.copy(probing = false) }
                if (started) refresh()
            }
        }
    }

    fun cancelProbe() {
        streamingProbe.cancel()
        probe?.cancel()
        message("Check stopped")
    }

    private fun probeFailure(device: UsbDevice, rate: Int, error: String) {
        val report =
            JSONObject()
                .put("capturedAt", Instant.now().toString())
                .put("vendorId", device.vendorId)
                .put("productId", device.productId)
                .put("sampleRateHz", rate)
                .put("streamingCompleted", false)
                .put("digitalEqualityVerified", false)
                .put("error", error)
        mutableState.update { it.copy(probeReports = (it.probeReports + report).takeLast(10)) }
        event(error)
        message("Check failed · see details")
    }

    fun finishExport(uri: Uri?) {
        val text = exportSnapshot
        exportSnapshot = null
        if (uri == null || text == null) {
            mutableState.update { it.copy(exporting = false) }
            if (uri != null) message("Inspection was interrupted; refresh and export again")
            return
        }
        viewModelScope.launch {
            try {
                writeUsbDiagnostics(context, uri, text)
                message("Report saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message("Could not save report: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                mutableState.update { it.copy(exporting = false) }
            }
        }
    }

    private fun clearPermissionRequest() {
        permissionIntent?.cancel()
        permissionIntent = null
        mutableState.update { it.copy(permissionDeviceId = null) }
    }

    private fun message(value: String) {
        mutableState.update { it.copy(message = value) }
        event(value)
    }

    private fun event(value: String) {
        mutableState.update {
            it.copy(events = (it.events + "${Instant.now()}: $value").takeLast(50))
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
