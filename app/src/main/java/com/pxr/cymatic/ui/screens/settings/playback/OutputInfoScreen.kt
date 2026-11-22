package com.pxr.cymatic.ui.screens.settings.playback

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.R
import com.pxr.cymatic.playback.OutputInfoReport
import com.pxr.cymatic.playback.OutputInfoState
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun OutputInfoScreen(selected: String? = null) {
    val nav = LocalNavController.current
    val onDismissRequest: () -> Unit = { nav.popBackStack() }
    val report by OutputInfoState.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copy = {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Cymatic output info", report.text()))
        Toast.makeText(context, "Output info copied", Toast.LENGTH_SHORT).show()
    }
    val section = report.sections.firstOrNull { it.title == selected }
    val menuItems =
        report.sections.map { page ->
            NavigationItem(
                page.title,
                icon = pixelIcon(R.drawable.ic_pixel_info),
                onClick = { nav.navigate(Screen.OutputInfoSection.createRoute(page.title)) },
            )
        } +
                NavigationItem(
                    "Copy report",
                    icon = pixelIcon(R.drawable.ic_pixel_copy),
                    onClick = copy,
                ) +
                NavigationItem(
                    "Return",
                    icon = pixelIcon(R.drawable.ic_pixel_back),
                    onClick = onDismissRequest,
                )
    if (selected == null) {
        BaseScreen("Output info", onDismissRequest) {
            if (report.sections.isEmpty())
                Text("Waiting for playback details…", Modifier.padding(24.dp))
            else NavigationList(menuItems)
        }
    } else {
        ReadingScreen(selected, onDismissRequest) {
            OutputInformation(OutputInfoReport(section?.let(::listOf).orEmpty()))
        }
    }
}

@Composable
private fun OutputInformation(report: OutputInfoReport) {
    if (report.sections.isEmpty()) {
        Text(
            "Waiting for the playback service…",
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        report.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.sections.size > 1)
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                section.fields.forEach { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            field.name,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            field.value,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
