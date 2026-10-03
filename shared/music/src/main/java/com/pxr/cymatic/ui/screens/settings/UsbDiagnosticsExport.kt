package com.pxr.cymatic.ui.screens.settings

import android.content.Context
import android.net.Uri
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun UsbDiagnosticsState.exportJson(): String? =
    report?.json(events)?.let { text ->
        JSONObject(text)
            .put(
                "scope",
                "USB capabilities and optional bounded transport probes; bit-perfect music playback is unverified",
            )
            .put("directUsbPlaybackStatus", UsbPlaybackState.status.value)
            .put(
                "directUsbPlaybackSessions",
                JSONArray(UsbPlaybackState.sessionReports.value.map(::JSONObject)),
            )
            .put("streamingProbes", JSONArray(probeReports))
            .toString(2)
    }

internal suspend fun writeUsbDiagnostics(context: Context, uri: Uri, report: String) =
    withContext(Dispatchers.IO) {
        val stream =
            context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Destination could not be opened")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
    }
