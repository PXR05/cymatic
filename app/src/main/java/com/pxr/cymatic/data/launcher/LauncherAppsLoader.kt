package com.pxr.cymatic.data.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process

object LauncherAppsLoader {

    data class LauncherApp(
        val label: String,
        val packageName: String,
        val icon: Bitmap?
    )

    fun loadLaunchableApps(context: Context): List<LauncherApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)

        val iconSizePx = (64 * context.resources.displayMetrics.density).toInt()

        return resolveInfos.asSequence()
            .filter { it.activityInfo != null }
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                LauncherApp(
                    label = info.loadLabel(packageManager).toString().trim(),
                    packageName = info.activityInfo.packageName,
                    icon = runCatching {
                        info.loadIcon(packageManager)?.toBitmap(iconSizePx)
                    }.getOrNull()
                )
            }
            .filter { it.label.isNotEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun loadMostUsedPackageNames(context: Context, limit: Int): List<String> {
        if (!hasUsageStatsPermission(context)) {
            return emptyList()
        }
        return runCatching {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - 30L * 24L * 60L * 60L * 1000L
            val launchablePackageNames = loadLaunchableApps(context)
                .map { it.packageName }
                .toSet()

            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                start,
                now
            )
                .asSequence()
                .filter { it.totalTimeInForeground > 0 }
                .filter { it.packageName in launchablePackageNames }
                .filter { it.packageName != context.packageName }
                .groupBy { it.packageName }
                .map { (packageName, stats) ->
                    packageName to stats.sumOf { it.totalTimeInForeground }
                }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }.getOrDefault(emptyList())
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getShortcutsForPackage(context: Context, packageName: String): List<ShortcutInfo> {
        return try {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return emptyList()
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                )
            }
            launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun startShortcut(context: Context, shortcut: ShortcutInfo) {
        try {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return
            launcherApps.startShortcut(
                shortcut.`package`,
                shortcut.id,
                null,
                null,
                shortcut.userHandle
            )
        } catch (_: Exception) {
        }
    }

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        return drawToBitmap(sizePx)
    }

    private fun Drawable.drawToBitmap(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
