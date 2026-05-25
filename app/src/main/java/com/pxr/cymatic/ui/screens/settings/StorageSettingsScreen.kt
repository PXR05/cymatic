package com.pxr.cymatic.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.SettingsStore
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
