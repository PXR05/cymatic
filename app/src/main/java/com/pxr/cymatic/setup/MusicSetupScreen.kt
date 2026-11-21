package com.pxr.cymatic.setup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.pxr.cymatic.data.media.LocalLibraryScanner
import com.pxr.cymatic.data.media.LocalScanProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.audioStoragePermission
import com.pxr.cymatic.ui.components.common.rememberStoragePermission

@Composable
fun MusicSetupScreen(model: MusicSetupViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val local by LocalLibraryScanner.progress.collectAsState()
    val catalog by LibraryCatalogManager.progress.collectAsState()
    val context = LocalContext.current
    val activity = context.setupActivity()
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = audioStoragePermission
    val storagePermission = rememberStoragePermission {
        denied = !it
        if (it) model.scan()
    }
    val granted = storagePermission.granted
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it != null) model.addFolder(it)
        }
    LaunchedEffect(granted, state.step) {
        if (granted && state.step == MusicSetupStep.DEVICE) model.scan()
        else if (state.step == MusicSetupStep.SCANNING && local == LocalScanProgress.Idle)
            model.scan()
    }
    BackHandler {
        if (state.step == MusicSetupStep.CHOICE || state.step == MusicSetupStep.NAVIGATE)
            activity?.finish()
        else model.back()
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.step == MusicSetupStep.LOADING) LoadingState()
        else
            key(state.step) {
                SetupPage(
                    state,
                    model,
                    local,
                    catalog,
                    onPermission = {
                        val blocked =
                            denied &&
                                    activity != null &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity,
                                        permission,
                                    )
                        if (blocked)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        else storagePermission.request()
                    },
                    permissionDenied = denied,
                    permissionBlocked =
                        denied &&
                                activity != null &&
                                !ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    permission,
                                ),
                    onFolder = { folder.launch(null) },
                )
            }
    }
}

private fun Context.setupActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.setupActivity()
        else -> null
    }
