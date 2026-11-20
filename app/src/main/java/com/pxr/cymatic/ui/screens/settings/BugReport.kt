package com.pxr.cymatic.ui.screens.settings

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.data.store.currentInterfaceSettings
import com.pxr.cymatic.playback.OutputInfoReport
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.SyncProgress
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal enum class BugCategory(val label: String) {
    PLAYBACK("Playback"),
    USB("USB audio"),
    LIBRARY("Library and sync"),
    INTERFACE("Interface and wheel"),
    CRASH("Crash or freeze"),
    OTHER("Other"),
}

internal fun formatBugReport(
    category: BugCategory,
    summary: String,
    details: String,
    diagnostics: String? = null,
): String = buildString {
    appendLine("Cymatic bug report")
    appendLine()
    appendLine("Category: ${category.label}")
    appendLine("Summary: ${summary.trim()}")
    appendLine()
    appendLine("What happened")
    appendLine(details.trim())
    if (diagnostics != null) {
        appendLine()
        appendLine("Diagnostics")
        append(diagnostics)
    }
}

internal suspend fun captureBugDiagnostics(
    context: Context,
    category: BugCategory,
    playback: OutputInfoReport,
): String =
    withContext(Dispatchers.IO) {
        val diagnostics =
            when (category) {
                BugCategory.PLAYBACK -> playbackDiagnostics(playback)
                BugCategory.USB -> playbackDiagnostics(playback) + usbDiagnostics()
                BugCategory.LIBRARY -> libraryDiagnostics()
                BugCategory.CRASH -> crashDiagnostics(context)
                BugCategory.INTERFACE -> interfaceDiagnostics()
                BugCategory.OTHER -> emptyList()
            }
        buildString {
            appendLine("Captured: ${Instant.now()}")
            appendLine("App version: ${appVersion(context)}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Android build: ${Build.ID}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            if (diagnostics.isNotEmpty()) appendLine()
            diagnostics.forEach { appendLine(it) }
        }
    }

private fun appVersion(context: Context): String {
    val manager = context.packageManager
    val info =
        if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") manager.getPackageInfo(context.packageName, 0)
        }
    return "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
}

private fun playbackDiagnostics(report: OutputInfoReport): List<String> {
    val fields =
        setOf(
            "Playback",
            "Route",
            "Source",
            "Decoded",
            "Processing",
            "Source bit depth",
            "Source bitrate (reported)",
            "Decoder",
            "Decoder state",
            "Decoder implementation",
            "Decoder initialization",
            "Decoded audio",
            "DSF bit order",
            "DSD handling",
            "DSD conversion filter",
            "Encoder delay / padding",
            "AudioTrack underruns",
            "Last underrun",
            "Skipped decoder buffers",
        )
    return report.sections
        .filter { it.title in setOf("Overview", "Format & decoder", "Errors & events") }
        .flatMap { section ->
            section.fields
                .filter { it.name in fields }
                .map { "${section.title} / ${it.name}: ${it.value}" }
        }
        .ifEmpty { listOf("Playback snapshot: unavailable") }
}

private fun usbDiagnostics(): List<String> = buildList {
    add("Direct USB playback active: ${UsbPlaybackState.active.value}")
    val sessions = UsbPlaybackState.sessionReports.value
    if (sessions.isEmpty()) add("Recent USB sessions: none recorded")
    sessions.forEachIndexed { index, text ->
        val session = JSONObject(text)
        val fields =
            listOf(
                "capturedAt",
                "vendorId",
                "productId",
                "initializationFailed",
                "format",
                "interval",
                "maxPacketBytes",
                "sourceLayout",
                "outputLayout",
                "pcmPackingChanged",
                "confirmedClockRateHz",
                "clockLockPrimingFrames",
                "dopPrimingFrames",
                "completedFrames",
                "completedBytes",
                "completedPackets",
                "completedTransfers",
                "packetErrors",
                "shortPackets",
                "underruns",
                "pendingBytesAtClose",
                "cleanupWarnings",
            )
        add("USB session ${index + 1}")
        fields.forEach { key ->
            session.opt(key)?.let { add("  $key: $it") }
        }
    }
}

private suspend fun libraryDiagnostics(): List<String> = buildList {
    try {
        val lastScan = SettingsStore.getLastScanTimeMs()
        if (lastScan > 0) {
            add("Last device scan: ${Instant.ofEpochMilli(lastScan)}")
            add("Songs found: ${SettingsStore.getLastScanCount()}")
            add("Scan duration: ${SettingsStore.getLastScanDurationMs()} ms")
        } else add("Last device scan: none recorded")
    } catch (_: IOException) {
        add("Last device scan: unavailable")
    }
    add(
        "Remote catalog: " +
            when (val catalog = LibraryCatalogManager.progress.value) {
                CatalogProgress.Idle -> "idle"
                CatalogProgress.Refreshing -> "refreshing"
                is CatalogProgress.Complete -> "${catalog.catalog.tracks.size} songs"
                is CatalogProgress.Failed -> "failed"
                CatalogProgress.Cancelled -> "cancelled"
            }
    )
    add(
        "Offline sync: " +
            when (val sync = LibrarySyncManager.progress.value) {
                SyncProgress.Idle -> "idle"
                is SyncProgress.Preparing -> "preparing"
                is SyncProgress.Downloading -> "${sync.current} of ${sync.total}"
                SyncProgress.Cancelling -> "cancelling"
                is SyncProgress.Complete ->
                    with(sync.summary) {
                        "$downloaded downloaded, $unchanged unchanged, $removed removed, $failed failed, $playlists playlists"
                    }
                is SyncProgress.Failed -> "failed"
                SyncProgress.Cancelled -> "cancelled"
            }
    )
}

private fun interfaceDiagnostics(): List<String> =
    with(SettingsStore.currentInterfaceSettings) {
        listOf(
            "Wheel size: $wheelSizePercent%",
            "Wheel haptics: $hapticsEnabled",
            "Wheel gesture pause: $gesturePauseMs ms",
            "Screen padding: $screenPaddingDp dp",
            "Text scroll speed: $textScrollSpeed dp/s",
            "Text scroll delay: $textScrollDelayMs ms",
            "Screen awake: ${screenAwakeMode.label}",
            "Standby timeout: $standbyTimeoutMs ms",
        )
    }

private fun crashDiagnostics(context: Context): List<String> = runCatching {
    context
        .getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(context.packageName, 0, 20)
        .mapNotNull { exit ->
            val reason =
                when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "Crash"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                    ApplicationExitInfo.REASON_ANR -> "App not responding"
                    else -> return@mapNotNull null
                }
            "${Instant.ofEpochMilli(exit.timestamp)} · $reason (status ${exit.status})"
        }
        .take(5)
        .ifEmpty { listOf("Recent crashes or freezes: none recorded by Android") }
}
    .getOrElse { listOf("Recent crashes or freezes: unavailable") }
