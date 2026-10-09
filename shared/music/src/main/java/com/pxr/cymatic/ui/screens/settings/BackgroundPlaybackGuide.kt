package com.pxr.cymatic.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

internal object BackgroundPlaybackGuide {
    fun isExempt(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun requestExemption(context: Context) {
        try {
            context.startActivity(
                Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openBatterySettings(context)
        }
    }

    fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun manufacturerLabel(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            "xiaomi" in manufacturer -> "Xiaomi"
            "samsung" in manufacturer -> "Samsung"
            manufacturer in setOf("oneplus", "oppo", "realme", "vivo", "iqoo") -> "OnePlus / Oppo / Vivo"
            manufacturer in setOf("huawei", "honor") -> "Huawei / Honor"
            else -> "This device"
        }
    }

    fun stepsText(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val steps =
            when {
                "xiaomi" in manufacturer ->
                    listOf(
                        "Settings → Apps → Manage apps → Cymatic → Battery saver → No restrictions.",
                        "In the same app page, enable Autostart.",
                        "Open recents, long-press Cymatic and lock it so the system does not swipe it away.",
                    )
                "samsung" in manufacturer ->
                    listOf(
                        "Settings → Battery → Background usage limits → Never auto sleeping apps → add Cymatic.",
                        "App info → Cymatic → Battery → Unrestricted.",
                    )
                manufacturer in setOf("oneplus", "oppo", "realme", "vivo", "iqoo") ->
                    listOf(
                        "Settings → Apps → Cymatic → Battery usage → Allow background activity.",
                        "Lock Cymatic in the recent-apps list.",
                    )
                manufacturer in setOf("huawei", "honor") ->
                    listOf(
                        "Settings → Battery → App launch → Cymatic → Manage manually, then allow auto-launch, secondary launch and running in background.",
                    )
                else ->
                    listOf(
                        "Allow background activity for Cymatic and set its battery usage to Unrestricted.",
                        "If the device has a per-app autostart or app-lock feature, enable it for Cymatic.",
                    )
            }
        return (
            "Direct USB stops the moment the OS freezes the app in the background. " +
                "Whitelisting keeps it scheduled.\n\n" + steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        )
    }
}
