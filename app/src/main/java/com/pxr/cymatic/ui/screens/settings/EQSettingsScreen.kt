package com.pxr.cymatic.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.eq.EqBandRow
import com.pxr.cymatic.ui.components.eq.EqBodePlot
import com.pxr.cymatic.ui.components.primitives.CymaticConfirmDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import com.pxr.cymatic.ui.components.primitives.CymaticSlider
import com.pxr.cymatic.ui.locals.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EqViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val context = LocalContext.current

    val state by viewModel.uiState.collectAsState()
    val livePreset by viewModel.livePreset.collectAsState()
    val activePreset = livePreset ?: state.activePreset

    var showPresetMenu by remember { mutableStateOf(false) }
    var showPresetDropdown by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var dialogInput by remember { mutableStateOf("") }
    var expandedBandIndex by remember { mutableIntStateOf(-1) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.exportPreset(context, state.selectedPresetName, uri)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = getFileNameFromUri(context, uri)
                    ?.removeSuffix(".txt")
                    ?: "Imported"
                viewModel.importPreset(context, uri, fileName)
            }
        }
    }

    if (showAddDialog) {
        CymaticInputDialog(
            title = "New Preset",
            hint = "Preset name",
            value = dialogInput,
            onValueChange = { dialogInput = it },
            onConfirm = {
                viewModel.addPreset(dialogInput.trim())
                dialogInput = ""
                showAddDialog = false
            },
            onDismiss = { dialogInput = ""; showAddDialog = false }
        )
    }

    if (showRenameDialog) {
        CymaticInputDialog(
            title = "Rename Preset",
            hint = "New name",
            value = dialogInput,
            onValueChange = { dialogInput = it },
            onConfirm = {
                viewModel.renamePreset(state.selectedPresetName, dialogInput.trim())
                dialogInput = ""
                showRenameDialog = false
            },
            onDismiss = { dialogInput = ""; showRenameDialog = false }
        )
    }

    if (showDeleteConfirm) {
        CymaticConfirmDialog(
            message = "Delete \"${state.selectedPresetName}\"?",
            onConfirm = {
                viewModel.deletePreset(state.selectedPresetName)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    BaseScreen(
        title = "Equalizer",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (state.eqEnabled) "I" else "O",
                    fontSize = 14.sp,
                    color = if (state.eqEnabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                        .background(if (state.eqEnabled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                        .padding(24.dp, 16.dp)
                        .clickable(
                            onClick = { viewModel.setEnabled(!state.eqEnabled) },
                            indication = null,
                            interactionSource = null
                        )
                )

                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                        .weight(1f)
                ) {
                    Text(
                        text = state.selectedPresetName,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable(
                                onClick = { showPresetDropdown = true },
                                indication = null,
                                interactionSource = null
                            )
                    )

                    CymaticDropdownMenu(
                        expanded = showPresetDropdown,
                        onDismissRequest = { showPresetDropdown = false }
                    ) {
                        state.presets.forEach { preset ->
                            CymaticDropdownMenuItem(
                                text = preset.name,
                                onClick = {
                                    viewModel.selectPreset(preset.name)
                                    showPresetDropdown = false
                                }
                            )
                        }
                    }
                }

                Box {
                    Text(
                        text = ":",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary)
                            .padding(22.dp, 16.dp)
                            .clickable(
                                onClick = { showPresetMenu = true },
                                indication = null,
                                interactionSource = null
                            )
                    )

                    CymaticDropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { showPresetMenu = false }
                    ) {
                        CymaticDropdownMenuItem(
                            text = "New",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pixel_plus),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showPresetMenu = false
                                dialogInput = ""
                                showAddDialog = true
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Rename",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pixel_edit),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showPresetMenu = false
                                dialogInput = state.selectedPresetName
                                showRenameDialog = true
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Delete",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pixel_trash),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showPresetMenu = false
                                showDeleteConfirm = true
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Import",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pixel_arrow_down),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showPresetMenu = false
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                importLauncher.launch(intent)
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Export",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pixel_arrow_up),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showPresetMenu = false
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TITLE,
                                        "${state.selectedPresetName}.txt"
                                    )
                                }
                                exportLauncher.launch(intent)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Graph",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            EqBodePlot(
                preset = activePreset,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pre-amp",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${"%.1f".format(activePreset.preamp)} dB",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CymaticSlider(
                value = activePreset.preamp,
                onValueChange = { viewModel.updatePreamp(it) },
                valueRange = -12f..12f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Bands",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                activePreset.bands.forEachIndexed { index, band ->
                    EqBandRow(
                        index = index,
                        band = band,
                        isExpanded = expandedBandIndex == index,
                        onToggleExpand = {
                            expandedBandIndex = if (expandedBandIndex == index) -1 else index
                        },
                        onToggleEnabled = {
                            viewModel.updateBand(index, band.copy(enabled = !band.enabled))
                        },
                        onBandChange = { updated -> viewModel.updateBand(index, updated) },
                        onRemove = { viewModel.removeBand(index) }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .border(1.dp, MaterialTheme.colorScheme.secondary)
                    )
                }

                if (activePreset.bands.size < EqViewModel.MAX_BANDS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable(
                                onClick = { viewModel.addBand() },
                                indication = null,
                                interactionSource = null
                            ),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "+ Add Band",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
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
