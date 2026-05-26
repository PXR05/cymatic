package com.pxr.cymatic.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.storage.DirectoryActions
import com.pxr.cymatic.ui.components.storage.DirectoryBox
import com.pxr.cymatic.ui.components.storage.StatusBento
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch

@SuppressLint("WrongConstant")
@Composable
fun StorageSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            hasStoragePermission(
                context
            )
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val directories by SettingsStore.scanDirectoriesFlow.collectAsState(initial = SettingsStore.currentScanDirectories)

    BaseScreen(
        title = "Storage",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Text(
                text = "Status",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatusBento()

            if (!hasPermission) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "PERMISSION REQUIRED",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Cymatic needs storage access to scan music on your device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.onBackground)
                            .clickable {
                                permissionLauncher.launch(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    } else {
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "GRANT PERMISSION",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Directories",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            DirectoryActions(
                directories = directories
            )

            Spacer(modifier = Modifier.height(16.dp))

            DirectoryBox(
                directories = directories,
                onRemove = { uri ->
                    scope.launch { SettingsStore.removeScanDirectory(uri) }
                },
                onToggleScanAll = { enabled ->
                    scope.launch { SettingsStore.setScanAllMedia(enabled) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
