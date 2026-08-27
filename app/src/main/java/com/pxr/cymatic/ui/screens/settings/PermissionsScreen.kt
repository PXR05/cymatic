package com.pxr.cymatic.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationsGranted by remember { mutableStateOf(false) }
    var usageAccessGranted by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(false) }

    fun refreshStatuses() {
        notificationsGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        usageAccessGranted = LauncherAppsLoader.hasUsageStatsPermission(context)
        storageGranted = hasStoragePermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        refreshStatuses()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (!granted) {
            openNotificationSettings()
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storageGranted = granted
        if (!granted) {
            openAppSettings()
        }
    }

    BaseScreen(
        title = "Permissions",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = listOf(
                NavigationItem(
                    label = "Notifications",
                    subLabel = if (notificationsGranted) "allowed" else "blocked",
                    onClick = {
                        if (notificationsGranted) {
                            openNotificationSettings()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings()
                        }
                    }
                ),
                NavigationItem(
                    label = "Usage Access",
                    subLabel = if (usageAccessGranted) "allowed" else "blocked",
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                ),
                NavigationItem(
                    label = "Media Access",
                    subLabel = if (storageGranted) "allowed" else "blocked",
                    onClick = {
                        if (storageGranted) {
                            openAppSettings()
                        } else {
                            storageLauncher.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_AUDIO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            )
                        }
                    }
                )
            )
        )
    }
}
