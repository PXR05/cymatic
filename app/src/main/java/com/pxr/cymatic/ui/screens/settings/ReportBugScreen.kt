package com.pxr.cymatic.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.playback.OutputInfoReport
import com.pxr.cymatic.playback.OutputInfoState
import com.pxr.cymatic.ui.components.common.EditorSubmitHint
import com.pxr.cymatic.ui.components.common.ScreenBackHandler
import com.pxr.cymatic.ui.components.common.WheelEditorSubmit
import com.pxr.cymatic.ui.components.common.submitOnEnter
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ReportStep(val title: String) {
    CATEGORY("Choose a bug type"),
    SUMMARY("Summarize the issue"),
    DETAILS("Describe what happened"),
    REVIEW("Review and email"),
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ReportBugScreen() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var step by rememberSaveable { mutableStateOf(ReportStep.CATEGORY) }
    var category by rememberSaveable { mutableStateOf<BugCategory?>(null) }
    var summary by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var includeDiagnostics by rememberSaveable { mutableStateOf(true) }
    var capturedDiagnostics by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val playback =
        if (includeDiagnostics && (category == BugCategory.PLAYBACK || category == BugCategory.USB))
            OutputInfoState.report.collectAsStateWithLifecycle()
        else null
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val report = pendingExport
            pendingExport = null
            if (uri != null && report != null) {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val stream =
                                context.contentResolver.openOutputStream(uri, "wt")
                                    ?: error("Destination could not be opened")
                            stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                        }
                        Toast.makeText(context, "Report saved", Toast.LENGTH_SHORT).show()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not save report", Toast.LENGTH_SHORT).show()
                    } finally {
                        busy = false
                    }
                }
            }
        }

    fun prepareReport(onReady: suspend (BugCategory, String) -> Unit) {
        val selected = category ?: return
        if (busy || summary.isBlank() || details.isBlank()) return
        busy = true
        scope.launch {
            try {
                val diagnostics =
                    if (includeDiagnostics) {
                        capturedDiagnostics
                            ?: captureBugDiagnostics(
                                context,
                                selected,
                                playback?.value ?: OutputInfoReport(),
                            )
                                .also { capturedDiagnostics = it }
                    } else null
                onReady(selected, formatBugReport(selected, summary, details, diagnostics))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "Could not prepare report", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    val goBack: () -> Unit = {
        when {
            step == ReportStep.CATEGORY -> nav.popBackStack()
            !busy -> {
                if (step == ReportStep.SUMMARY) keyboard?.hide()
                step = ReportStep.entries[step.ordinal - 1]
            }
        }
    }
    ScreenBackHandler(enabled = step != ReportStep.CATEGORY, onBack = goBack)

    BaseScreen("Report bug", onBackClick = goBack) {
        Text(
            "${step.ordinal + 1}. ${step.title}",
            modifier =
                Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        key(step) {
            when (step) {
                ReportStep.CATEGORY ->
                    NavigationList(
                        BugCategory.entries.map { option ->
                            NavigationItem(option.label) {
                                if (category != option) {
                                    capturedDiagnostics = null
                                }
                                category = option
                                step = ReportStep.SUMMARY
                            }
                        }
                    )

                ReportStep.SUMMARY ->
                    ReportTextStep(
                        label = "Summary",
                        value = summary,
                        onValueChange = { summary = it },
                        hint = "Describe the issue in one sentence.",
                        onContinue = { step = ReportStep.DETAILS },
                    )

                ReportStep.DETAILS ->
                    ReportTextStep(
                        label = "Details",
                        value = details,
                        onValueChange = { details = it },
                        hint =
                            "Include the steps, what you expected, what happened, and how often.",
                        multiline = true,
                        onContinue = {
                            keyboard?.hide()
                            step = ReportStep.REVIEW
                        },
                    )

                ReportStep.REVIEW ->
                    NavigationList(
                        buildList {
                            add(NavigationItem("Bug type", category?.label, enabled = false))
                            add(
                                NavigationItem(
                                    "Summary",
                                    summary.trim(),
                                    enabled = false,
                                    subLabelMaxLines = Int.MAX_VALUE,
                                )
                            )
                            add(
                                NavigationItem(
                                    "What happened",
                                    details.trim(),
                                    enabled = false,
                                    subLabelMaxLines = Int.MAX_VALUE,
                                )
                            )
                            add(
                                NavigationItem(
                                    label = "Include diagnostics",
                                    subLabel = "App and device details",
                                    enabled = !busy,
                                    checked = includeDiagnostics,
                                ) {
                                    includeDiagnostics = !includeDiagnostics
                                }
                            )
                            add(
                                NavigationItem(
                                    "Email report",
                                    "Open a draft in your email app",
                                    enabled = !busy,
                                ) {
                                    prepareReport { selected, report ->
                                        if (
                                            !openBugReportEmail(
                                                context,
                                                selected,
                                                summary,
                                                details,
                                                report,
                                                includeDiagnostics,
                                            )
                                        ) {
                                            Toast.makeText(
                                                context,
                                                "No email app available. You can save the report instead.",
                                                Toast.LENGTH_LONG,
                                            )
                                                .show()
                                        }
                                    }
                                }
                            )
                            add(
                                NavigationItem(
                                    "Save report",
                                    "Export a text file",
                                    enabled = !busy,
                                ) {
                                    prepareReport { _, report ->
                                        pendingExport = report
                                        export.launch("cymatic-bug-report.txt")
                                    }
                                }
                            )
                        }
                    )
            }
        }
    }
}

@Composable
private fun ReportTextStep(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    multiline: Boolean = false,
    onContinue: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { if (value.isNotBlank()) onContinue() }
    WheelEditorSubmit(submit)
    LaunchedEffect(Unit) {
        requester.requestFocus()
        keyboard?.show()
    }
    Column(Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .focusRequester(requester)
                    .then(if (multiline) Modifier else Modifier.submitOnEnter(submit)),
            singleLine = !multiline,
            shape = RectangleShape,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 5 else 1,
            supportingText = { EditorSubmitHint("continue", keyboardSubmit = !multiline) },
            keyboardOptions =
                KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { submit() }),
        )
    }
}
