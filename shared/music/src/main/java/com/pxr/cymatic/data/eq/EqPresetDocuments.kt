package com.pxr.cymatic.data.eq

import android.content.ContentResolver
import android.net.Uri
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.EqPreset.Companion.toApoString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EqPresetDocuments(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri, name: String): EqPreset? =
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.bufferedReader()?.use {
                EqPreset.fromApoString(it.readText(), name)
            }
        }

    suspend fun write(uri: Uri, preset: EqPreset) =
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(preset.toApoString()) }
        }
}
