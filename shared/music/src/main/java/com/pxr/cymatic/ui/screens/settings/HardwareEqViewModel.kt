package com.pxr.cymatic.ui.screens.settings

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.audio.usb.DacEqInitialRead
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.dacProfile
import com.pxr.cymatic.audio.usb.draftIssues
import com.pxr.cymatic.audio.usb.emptyDraft
import com.pxr.cymatic.audio.usb.playerBands
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.store.SettingsStore
import io.github.chronosauros.contour.core.DacImport
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.usb.DacClient
import io.github.chronosauros.contour.usb.DacHandle
import io.github.chronosauros.contour.usb.DacSnapshot
import io.github.chronosauros.contour.usb.PendingSave
import io.github.chronosauros.contour.usb.UsbAudioKeepAlive
import io.github.chronosauros.contour.usb.UsbLog
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HardwareEqUiState(
    val devices: List<DacHandle> = emptyList(),
    val device: DacHandle? = null,
    val permission: Boolean = false,
    val permissionPending: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val draft: EqPreset = EqPreset("DAC draft"),
    val slot: Int? = null,
    val message: String = "Connect a supported USB DAC",
    val onDac: Boolean = false,
    val pendingVerification: Boolean = false,
    val firmware: String? = null,
) {
    val target: DeviceTarget?
        get() = device?.target

    val issues: List<String>
        get() = target?.draftIssues(draft).orEmpty()

    val canApply: Boolean
        get() =
            device != null &&
                permission &&
                !busy &&
                !permissionPending &&
                !pendingVerification &&
                target?.readOnly == false &&
                issues.isEmpty()
}

internal class HardwareEqViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val manager = context.getSystemService(UsbManager::class.java)
    private val prefs = context.getSharedPreferences("hardware_eq", Context.MODE_PRIVATE)
    private val permissionAction = "${context.packageName}.HARDWARE_EQ_PERMISSION"
    private var permissionRequest: PendingIntent? = null
    private var requestedDevice: Int? = null
    @Volatile private var operation: Job? = null
    @Volatile private var closed = false
    private val mutableState = MutableStateFlow(HardwareEqUiState())
    val state = mutableState.asStateFlow()
    private var pending: PendingSave? = null
    private var pendingModel: String? = null
    private var pendingProfile: EqPreset? = null
    private var pendingSelectionDone = false
    private var pendingSerial: String? = null
    private val initialRead = DacEqInitialRead()

    private val client =
        DacClient(
            manager,
            guardFor = { handle ->
                {
                    operation?.ensureActive()
                    check(!closed) { "EQ page closed" }
                    val device = handle.usb
                    if (
                        device == null ||
                            manager.deviceList[device.deviceName]?.deviceId != device.deviceId ||
                            !manager.hasPermission(device)
                    )
                        throw IOException("DAC disconnected or USB access changed")
                }
            },
            audioKeepAlive = { device ->
                if (UsbPlaybackState.active.value) null
                else UsbAudioKeepAlive.start(context, device)
            },
        )

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == permissionAction) {
                    val device =
                        IntentCompat.getParcelableExtra(
                            intent,
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice::class.java,
                        )
                    if (device == null || device.deviceId != requestedDevice) return
                    permissionRequest?.cancel()
                    permissionRequest = null
                    requestedDevice = null
                    refresh()
                    if (!manager.hasPermission(device)) message("USB access denied")
                } else {
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        val device =
                            IntentCompat.getParcelableExtra(
                                intent,
                                UsbManager.EXTRA_DEVICE,
                                UsbDevice::class.java,
                            )
                        if (device?.deviceId == requestedDevice) {
                            permissionRequest?.cancel()
                            permissionRequest = null
                            requestedDevice = null
                        }
                    }
                    refresh()
                }
            }
        }

    init {
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
        refresh()
    }

    private fun modelKey(handle: DacHandle) =
        "${handle.target.caps.name}:${handle.usb?.vendorId}:${handle.usb?.productId}"

    private fun serial(handle: DacHandle): String? = runCatching {
        handle.usb?.serialNumber
    }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    private fun storageKey(handle: DacHandle, slot: Int?) = "${modelKey(handle)}:${slot ?: 0}"

    private fun draftKey(handle: DacHandle, slot: Int?) = "draft:${storageKey(handle, slot)}"

    private fun backupKey(handle: DacHandle, slot: Int?) = "backup:${storageKey(handle, slot)}"

    fun refresh() {
        val devices =
            manager.deviceList.values.mapNotNull(DacHandle::of).sortedBy { it.target.caps.name }
        initialRead.retainConnections(devices.map { it.key }.toSet())
        val previous = state.value
        val selected =
            devices.firstOrNull { it.key == previous.device?.key }
                ?: devices.firstOrNull { pendingModel != null && modelKey(it) == pendingModel }
                ?: devices.firstOrNull()
        if (previous.busy) {
            mutableState.value = previous.copy(devices = devices)
            return
        }
        if (selected?.key != previous.device?.key) selectDevice(selected, devices)
        else
            mutableState.value =
                previous.copy(
                    devices = devices,
                    permission = selected?.usb?.let(manager::hasPermission) == true,
                    permissionPending = requestedDevice != null,
                )
        autoRead()
    }

    fun selectDevice(handle: DacHandle?, devices: List<DacHandle> = state.value.devices) {
        if (state.value.busy) return
        val slot = handle?.target?.destinationSlot
        val draft =
            handle?.let {
                prefs.getString(draftKey(it, slot), null)?.let(EqPreset::fromJson)
                    ?: it.target.emptyDraft()
            } ?: EqPreset("DAC draft")
        mutableState.value =
            HardwareEqUiState(
                devices = devices,
                device = handle,
                permission = handle?.usb?.let(manager::hasPermission) == true,
                permissionPending = requestedDevice != null,
                draft = draft,
                slot = slot,
                pendingVerification =
                    handle != null && pending != null && pendingModel == modelKey(handle),
                message =
                    if (handle == null) "No supported DAC connected"
                    else if (pending != null && pendingModel == modelKey(handle))
                        "Verification pending"
                    else "Ready",
            )
        autoRead()
    }

    private fun autoRead() {
        val current = state.value
        if (
            initialRead.shouldRead(
                current.device?.key,
                current.permission,
                current.busy,
                current.pendingVerification,
            )
        ) {
            readDac()
        }
    }

    fun requestAccess() {
        val device = state.value.device?.usb ?: return
        if (state.value.busy || requestedDevice != null) return
        if (manager.hasPermission(device)) {
            refresh()
            return
        }
        requestedDevice = device.deviceId
        val intent =
            PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(permissionAction).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
        permissionRequest = intent
        try {
            manager.requestPermission(device, intent)
            mutableState.value =
                state.value.copy(permissionPending = true, message = "Waiting for USB access")
        } catch (e: Exception) {
            intent.cancel()
            permissionRequest = null
            requestedDevice = null
            mutableState.value =
                state.value.copy(
                    permissionPending = false,
                    message = e.message ?: "USB access request failed",
                )
        }
    }

    fun edit(preset: EqPreset) {
        val current = state.value
        if (current.busy || current.pendingVerification) return
        mutableState.value =
            current.copy(
                draft = preset,
                onDac = false,
                message = "Unapplied changes",
            )
        current.device?.let {
            prefs.edit().putString(draftKey(it, current.slot), preset.toJson().toString()).apply()
        }
    }

    fun updateBand(index: Int, band: EqBand) {
        val draft = state.value.draft
        if (index !in draft.bands.indices) return
        edit(draft.copy(bands = draft.bands.mapIndexed { i, b -> if (i == index) band else b }))
    }

    fun removeBand(index: Int) {
        val draft = state.value.draft
        if (index !in draft.bands.indices) return
        edit(draft.copy(bands = draft.bands.filterIndexed { i, _ -> i != index }))
    }

    fun addBand() {
        val current = state.value
        val target = current.target ?: return
        if (current.draft.bands.size >= target.caps.bands) return
        edit(
            current.draft.copy(
                bands =
                    current.draft.bands +
                        target
                            .emptyDraft()
                            .bands
                            .single()
                            .copy(id = (current.draft.bands.maxOfOrNull { it.id } ?: -1) + 1)
            )
        )
    }

    fun savePreset(name: String) {
        if (name.isBlank() || state.value.busy) return
        val preset = state.value.draft.copy(name = name.trim())
        viewModelScope.launch {
            val existing = SettingsStore.currentEqPresets
            var unique = preset.name
            var suffix = 2
            while (existing.any { it.name == unique }) unique = "${preset.name} (${suffix++})"
            SettingsStore.setEqPresets(existing + preset.copy(name = unique))
            message("Saved preset: $unique")
        }
    }

    private fun imported(snapshot: DacSnapshot): EqPreset =
        when (val result = snapshot.importExact()) {
            is DacImport.Ready ->
                EqPreset(
                    "From DAC",
                    (result.eq.preampDb ?: 0.0).toFloat(),
                    result.eq.bands.playerBands(),
                )
            is DacImport.Rejected -> throw IOException(result.issues.joinToString("; "))
        }

    private suspend fun read(handle: DacHandle, slot: Int?): DacSnapshot =
        if (handle.target.fiio != null && slot != null) client.readSlot(handle, slot)
        else client.readDevice(handle)

    fun readDac() =
        operate("Reading DAC") { handle, _ ->
            val ticket = pending.takeIf { pendingModel == modelKey(handle) }
            if (ticket != null) finishPending(handle, ticket)
            else {
                val snapshot = client.readDevice(handle)
                val draft = imported(snapshot)
                val selectedSlot =
                    snapshot.slot.takeIf { it in handle.target.fiio?.userSlots.orEmpty() }
                        ?: handle.target.destinationSlot
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        state.value.copy(
                            draft = draft,
                            slot = selectedSlot,
                            firmware = snapshot.firmware,
                            onDac = snapshot.matches(draft.dacProfile()),
                            message = "Loaded from DAC",
                        )
                    prefs
                        .edit()
                        .putString(draftKey(handle, selectedSlot), draft.toJson().toString())
                        .apply()
                }
            }
        }

    fun applyToDac() {
        val selected = state.value
        if (!selected.canApply) return
        val preset = selected.draft
        operate("Reading DAC") { handle, slot ->
            val before = read(handle, slot)
            val backup = imported(before)
            withContext(Dispatchers.IO) {
                check(
                    prefs
                        .edit()
                        .putString(backupKey(handle, slot), backup.toJson().toString())
                        .putString(
                            "capture:${storageKey(handle, slot)}",
                            UsbLog.lines.value.joinToString("\n"),
                        )
                        .commit()
                ) {
                    "Backup failed · DAC unchanged"
                }
            }
            withContext(Dispatchers.Main) {
                mutableState.value = state.value.copy(message = "Applying EQ to DAC")
            }
            val result =
                client.writeProfile(handle, preset.dacProfile(), targetSlot = slot) { progress ->
                    mutableState.value = state.value.copy(progress = progress.coerceIn(0f, 1f))
                }
            val pendingSave = result.pending
            if (pendingSave != null) {
                pending = pendingSave
                pendingModel = modelKey(handle)
                pendingSerial = serial(handle)
                pendingProfile = preset
                pendingSelectionDone = false
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        state.value.copy(
                            pendingVerification = true,
                            message = "Restarting DAC · verifying",
                        )
                }

                for (attempt in 0 until 20) {
                    delay(500)
                    val reconnected =
                        manager.deviceList.values
                            .mapNotNull(DacHandle::of)
                            .filter {
                                modelKey(it) == pendingModel &&
                                    (pendingSerial == null || serial(it) == pendingSerial) &&
                                    it.usb?.let(manager::hasPermission) == true
                            }
                            .singleOrNull() ?: continue
                    try {
                        finishPending(reconnected, pendingSave)
                        return@operate
                    } catch (e: IOException) {
                        if (attempt == 19) throw e
                    }
                }
                message("Reconnect, then verify EQ")
            } else {
                check(result.verified) {
                    result.mismatches.joinToString("; ").ifBlank { "DAC EQ verification failed" }
                }
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        state.value.copy(
                            onDac = true,
                            firmware = result.readBack?.firmware,
                            message = "Applied and verified",
                        )
                }
            }
        }
    }

    private suspend fun finishPending(handle: DacHandle, ticket: PendingSave) {
        check(
            modelKey(handle) == pendingModel &&
                (pendingSerial == null || serial(handle) == pendingSerial)
        ) {
            "Reconnect the same DAC to verify"
        }
        if (!pendingSelectionDone) {
            ticket.afterReturn?.invoke(handle)
            pendingSelectionDone = true
        }
        val snapshot = client.readDevice(handle)
        val mismatches = ticket.verify(snapshot)
        if (mismatches.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                pending = null
                pendingModel = null
                pendingProfile = null
                pendingSerial = null
                mutableState.value = state.value.copy(pendingVerification = false, onDac = false)
            }
            error("Verification failed · load DAC EQ to inspect")
        }
        val draft = pendingProfile ?: imported(snapshot)
        withContext(Dispatchers.Main) {
            pending = null
            pendingModel = null
            pendingProfile = null
            mutableState.value =
                state.value.copy(
                    device = handle,
                    draft = draft,
                    onDac = snapshot.matches(draft.dacProfile()),
                    pendingVerification = false,
                    firmware = snapshot.firmware,
                    message = "Saved and verified",
                )
        }
    }

    private fun operate(label: String, block: suspend (DacHandle, Int?) -> Unit) {
        val selected = state.value
        val handle = selected.device ?: return
        if (selected.busy || !selected.permission) return
        mutableState.value =
            selected.copy(busy = true, onDac = false, progress = 0f, message = label)
        operation = viewModelScope.launch {
            try {
                block(handle, selected.slot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: "DAC EQ operation failed; read the DAC before retrying")
            } finally {
                mutableState.value = state.value.copy(busy = false)
                refresh()
            }
        }
    }

    private fun message(text: String) {
        mutableState.value = state.value.copy(message = text)
    }

    override fun onCleared() {
        closed = true
        operation?.cancel()
        permissionRequest?.cancel()
        context.unregisterReceiver(receiver)
        client.shutdown()
        super.onCleared()
    }
}
