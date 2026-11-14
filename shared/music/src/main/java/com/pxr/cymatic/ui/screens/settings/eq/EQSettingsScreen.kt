package com.pxr.cymatic.ui.screens.settings.eq

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EqViewModel = viewModel(),
    pageRoute: String = Screen.EQSettings.route,
    bandIndex: Int = -1,
) {
    val context = LocalContext.current
    val nav = LocalNavController.current

    val state by viewModel.uiState.collectAsState()
    val livePreset by viewModel.livePreset.collectAsState()
    val activePreset = livePreset ?: state.activePreset
    var presetToExport by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val presetName = presetToExport ?: state.selectedPresetName
                    viewModel.exportPreset(context, presetName, uri)
                    presetToExport = null
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName =
                        getFileNameFromUri(context, uri)?.removeSuffix(".txt") ?: "Imported"
                    viewModel.importPreset(context, uri, fileName)
                }
            }
        }

    WheelEqSettingsScreen(
        state = state,
        preset = activePreset,
        viewModel = viewModel,
        onHardwareEq = { nav.navigate(Screen.HardwareEq.route) },
        pageRoute = pageRoute,
        bandIndex = bandIndex,
        onImport = {
            importLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            )
        },
        onExport = { presetName ->
            presetToExport = presetName
            exportLauncher.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "$presetName.txt")
                }
            )
        },
        modifier = modifier,
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    } catch (_: Exception) {
        null
    }
}
