package com.pxr.cymatic.data.eq

import android.content.Context
import com.pxr.cymatic.audio.eq.modelKey
import com.pxr.cymatic.data.model.EqPreset
import io.github.chronosauros.contour.usb.DacHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HardwareEqStore(context: Context) {
    private val preferences = context.getSharedPreferences("hardware_eq", Context.MODE_PRIVATE)

    fun draft(handle: DacHandle, slot: Int?): EqPreset? =
        preferences.getString("draft:${storageKey(handle, slot)}", null)?.let(EqPreset::fromJson)

    fun saveDraft(handle: DacHandle, slot: Int?, preset: EqPreset) {
        preferences
            .edit()
            .putString("draft:${storageKey(handle, slot)}", preset.toJson().toString())
            .apply()
    }

    suspend fun saveBackup(handle: DacHandle, slot: Int?, preset: EqPreset, capture: List<String>) =
        withContext(Dispatchers.IO) {
            check(
                preferences
                    .edit()
                    .putString("backup:${storageKey(handle, slot)}", preset.toJson().toString())
                    .putString("capture:${storageKey(handle, slot)}", capture.joinToString("\n"))
                    .commit()
            ) {
                "Backup failed · DAC unchanged"
            }
        }

    private fun storageKey(handle: DacHandle, slot: Int?) = "${handle.modelKey()}:${slot ?: 0}"
}
