package com.pxr.cymatic.ui.screens.settings.eq.hardware

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.audio.eq.DacConnectionMonitor
import com.pxr.cymatic.audio.eq.DacEqInitialRead
import com.pxr.cymatic.audio.eq.PendingDacVerification
import com.pxr.cymatic.audio.eq.dacProfile
import com.pxr.cymatic.audio.eq.emptyDraft
import com.pxr.cymatic.audio.eq.playerPreset
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.eq.EqPresetRepository
import com.pxr.cymatic.data.eq.HardwareEqStore
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import io.github.chronosauros.contour.usb.DacClient
import io.github.chronosauros.contour.usb.DacHandle
import io.github.chronosauros.contour.usb.DacSnapshot
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

internal class HardwareEqViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val drafts = HardwareEqStore(context)
    private val presets = EqPresetRepository()
    @Volatile private var operation: Job? = null
    @Volatile private var closed = false
    private val mutableState = MutableStateFlow(HardwareEqUiState())
    val state = mutableState.asStateFlow()
    private var pending: PendingDacVerification? = null
    private val initialRead = DacEqInitialRead()
    private val connection = DacConnectionMonitor(context, ::refresh, ::message)
    private val manager
        get() = connection.manager

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

    init {
        refresh()
    }

    fun refresh() {
        val devices = connection.devices()
        initialRead.retainConnections(devices.map { it.key }.toSet())
        val previous = state.value
        val selected =
            devices.firstOrNull { it.key == previous.device?.key }
                ?: devices.firstOrNull { pending?.matchesModel(it) == true }
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
                    permission = connection.hasPermission(selected),
                    permissionPending = connection.permissionPending,
                )
        autoRead()
    }

    fun selectDevice(handle: DacHandle?, devices: List<DacHandle> = state.value.devices) {
        if (state.value.busy) return
        val slot = handle?.target?.destinationSlot
        val draft =
            handle?.let {
                drafts.draft(it, slot) ?: it.target.emptyDraft()
            } ?: EqPreset("DAC draft")
        mutableState.value =
            HardwareEqUiState(
                devices = devices,
                device = handle,
                permission = connection.hasPermission(handle),
                permissionPending = connection.permissionPending,
                draft = draft,
                slot = slot,
                pendingVerification = handle != null && pending?.matchesModel(handle) == true,
                message =
                    if (handle == null) "No supported DAC connected"
                    else if (pending?.matchesModel(handle) == true) "Verification pending"
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
        if (state.value.busy) return
        state.value.device?.let(connection::requestAccess)
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
            drafts.saveDraft(it, current.slot, preset)
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
            val saved = presets.saveCopy(preset)
            message("Saved preset: ${saved.name}")
        }
    }

    private suspend fun read(handle: DacHandle, slot: Int?): DacSnapshot =
        if (handle.target.fiio != null && slot != null) client.readSlot(handle, slot)
        else client.readDevice(handle)

    fun readDac() =
        operate("Reading DAC") { handle, _ ->
            val ticket = pending?.takeIf { it.matchesModel(handle) }
            if (ticket != null) finishPending(handle, ticket)
            else {
                val snapshot = client.readDevice(handle)
                val draft = snapshot.playerPreset()
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
                    drafts.saveDraft(handle, selectedSlot, draft)
                }
            }
        }

    fun applyToDac() {
        val selected = state.value
        if (!selected.canApply) return
        val preset = selected.draft
        operate("Reading DAC") { handle, slot ->
            val before = read(handle, slot)
            val backup = before.playerPreset()
            drafts.saveBackup(handle, slot, backup, UsbLog.lines.value)
            withContext(Dispatchers.Main) {
                mutableState.value = state.value.copy(message = "Applying EQ to DAC")
            }
            val result =
                client.writeProfile(handle, preset.dacProfile(), targetSlot = slot) { progress ->
                    mutableState.value = state.value.copy(progress = progress.coerceIn(0f, 1f))
                }
            val pendingSave = result.pending
            if (pendingSave != null) {
                val verification = PendingDacVerification(pendingSave, preset, handle)
                pending = verification
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
                        connection
                            .devices()
                            .filter {
                                verification.matchesDevice(it) && connection.hasPermission(it)
                            }
                            .singleOrNull() ?: continue
                    try {
                        finishPending(reconnected, verification)
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

    private suspend fun finishPending(handle: DacHandle, verification: PendingDacVerification) {
        check(verification.matchesDevice(handle)) { "Reconnect the same DAC to verify" }
        if (!verification.selectionDone) {
            verification.ticket.afterReturn?.invoke(handle)
            verification.selectionDone = true
        }
        val snapshot = client.readDevice(handle)
        val mismatches = verification.ticket.verify(snapshot)
        if (mismatches.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                pending = null
                mutableState.value = state.value.copy(pendingVerification = false, onDac = false)
            }
            error("Verification failed · load DAC EQ to inspect")
        }
        val draft = verification.profile
        withContext(Dispatchers.Main) {
            pending = null
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
        connection.close()
        client.shutdown()
        super.onCleared()
    }
}
