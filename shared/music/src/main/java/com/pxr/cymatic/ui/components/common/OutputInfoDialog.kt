package com.pxr.cymatic.ui.components.common

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.playback.OutputInfoReport
import com.pxr.cymatic.playback.OutputInfoState
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun OutputInfoDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
    if (!showDialog) return
    val report by OutputInfoState.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copy = {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Cymatic output info", report.text()))
        Toast.makeText(context, "Output info copied", Toast.LENGTH_SHORT).show()
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val section = report.sections.firstOrNull { it.title == selected }
    val menuItems =
        report.sections.map { page ->
            NavigationItem(page.title, onClick = { selected = page.title })
        } +
            NavigationItem("Copy report", onClick = copy) +
            NavigationItem("Return", onClick = onDismissRequest)
    if (LocalWheelNavigation.current != null) {
        WheelSettingsOverlay("Output info", onDismissRequest) {
            if (report.sections.isEmpty())
                Text("Waiting for playback details…", Modifier.padding(24.dp))
            else NavigationList(menuItems)
        }
        if (section != null) {
            WheelReadingPage(section.title, { selected = null }) {
                Text(
                    "Rotate to scroll · Select to go back",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                )
                OutputInformation(OutputInfoReport(listOf(section)))
            }
        }
    } else {
        CymaticDialog(
            title = section?.title ?: "Output info",
            onDismissRequest = onDismissRequest,
            content = {
                if (section == null) {
                    if (report.sections.isEmpty()) Text("Waiting for playback details…")
                    else NavigationList(menuItems)
                } else
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                    ) {
                        OutputInformation(OutputInfoReport(listOf(section)))
                    }
            },
            buttons = {
                CymaticDialogButton(
                    text = "Copy",
                    onClick = copy,
                    color = MaterialTheme.colorScheme.secondary,
                )
                CymaticDialogButton(
                    text = if (section == null) "Close" else "Back",
                    onClick = { if (section == null) onDismissRequest() else selected = null },
                    color = MaterialTheme.colorScheme.secondary,
                )
            },
        )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(vertical = 16.dp),
    ) {
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
