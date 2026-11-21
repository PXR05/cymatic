package com.pxr.cymatic.ui.screens.settings.storage

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.rememberStoragePermission

@SuppressLint("WrongConstant")
@Composable
fun StorageSettingsScreen(modifier: Modifier = Modifier) {
    val permission = rememberStoragePermission()

    val directories by
    SettingsStore.scanDirectoriesFlow.collectAsState(
        initial = SettingsStore.currentScanDirectories
    )

    WheelStorageSettingsScreen(
        directories,
        permission.granted,
        onGrantPermission = permission.request,
        modifier = modifier,
    )
}
