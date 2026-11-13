package com.pxr.cymatic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import org.json.JSONArray

private enum class LicenseGroup(val title: String) {
    ROOT("Licenses"),
    APACHE("Apache 2.0"),
    OFL("SIL Open Font License"),
    OTHER("Other licenses"),
    ATTRIBUTIONS("Attributions"),
}

private data class LicenseEntry(
    val title: String,
    val assets: List<String>,
    val summary: String = "",
    val licenseNames: Set<String> = emptySet(),
)

@Composable
fun LicensesSettingsScreen(modifier: Modifier = Modifier) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val wheel = LocalWheelNavigation.current != null
    var group by remember { mutableStateOf(LicenseGroup.ROOT) }
    var document by remember { mutableStateOf<Pair<String, String>?>(null) }
    val libraries = remember {
        val rows =
            JSONArray(
                context.assets.open("licenses/dependencies.json").bufferedReader().use {
                    it.readText()
                }
            )
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val texts = row.getJSONArray("documents")
            val licenses = row.getJSONArray("licenses")
            LicenseEntry(
                row.getString("title"),
                List(texts.length()) { texts.getString(it) },
                buildString {
                    appendLine(row.getString("coordinate"))
                    appendLine()
                    appendLine(row.optString("project"))
                    appendLine()
                    repeat(licenses.length()) { i ->
                        val license = licenses.getJSONObject(i)
                        appendLine(license.getString("name"))
                        appendLine(license.optString("url"))
                    }
                },
                List(licenses.length()) { licenses.getJSONObject(it).getString("name") }.toSet(),
            )
        }
    }
    fun components(entries: List<LicenseEntry>) =
        entries.joinToString("\n\n") { entry ->
            "## ${entry.title}\n\n${entry.summary}"
        }
    val apacheLibraries = libraries.filter { entry ->
        entry.licenseNames.any { it.contains("Apache", ignoreCase = true) }
    }
    val mitTexts =
        libraries
            .filter { entry -> entry.licenseNames.any { it.contains("MIT") } }
            .flatMap { it.assets }
            .distinct()
    val bsdTexts =
        libraries
            .filter { entry -> entry.licenseNames.any { it.contains("BSD") } }
            .flatMap { it.assets }
            .distinct()
    val extraNotices =
        remember(libraries) {
            libraries
                .flatMap { it.assets }
                .distinct()
                .filter { path ->
                    path !in mitTexts &&
                        path !in bsdTexts &&
                        !context.assets.open(path).bufferedReader().use {
                            it.readText().trimStart().startsWith("Apache License")
                        }
                }
        }
    val entries =
        when (group) {
            LicenseGroup.APACHE ->
                listOf(
                    LicenseEntry("License text", listOf("licenses/contour/LICENSE")),
                    LicenseEntry(
                        "Covered components",
                        emptyList(),
                        "## Vendored code\n\n- Contour — Copyright 2026 Chronosaur\n- Apple ALAC — Copyright 2011 Apple Inc.\n\n" +
                            components(apacheLibraries),
                    ),
                )
            LicenseGroup.OFL ->
                listOf(
                    LicenseEntry("License text", listOf("licenses/fonts/Shared-OFL.txt")),
                    LicenseEntry("Fonts & credits", listOf("licenses/fonts/FONT-CREDITS.txt")),
                )
            LicenseGroup.OTHER ->
                listOf(
                    LicenseEntry("BSD · libFLAC", listOf("licenses/libFLAC.txt")),
                    LicenseEntry(
                        "BSD · Protobuf",
                        bsdTexts,
                    ),
                    LicenseEntry(
                        "MIT",
                        mitTexts,
                    ),
                    LicenseEntry("0BSD", listOf("licenses/contour/LICENSE-devicePEQ.txt")),
                    LicenseEntry(
                        "LLVM exceptions & legacy terms",
                        listOf("licenses/LLVM-exceptions.txt"),
                    ),
                )
            LicenseGroup.ATTRIBUTIONS ->
                listOf(
                    LicenseEntry("Library credits", emptyList(), components(libraries)),
                    LicenseEntry("Decoder credits", listOf("licenses/DECODER-CREDITS.txt")),
                    LicenseEntry(
                        "DAC EQ credits",
                        listOf("licenses/contour/CYMATIC-NOTICE.txt", "licenses/contour/NOTICE"),
                    ),
                    LicenseEntry(
                        "Upstream third-party notices",
                        listOf("licenses/contour/THIRD_PARTY_NOTICES.md"),
                    ),
                    LicenseEntry(
                        "Additional library notices",
                        extraNotices,
                    ),
                )
            LicenseGroup.ROOT -> emptyList()
        }
    val body: @Composable () -> Unit = {
        key(group) {
            NavigationList(
                if (group == LicenseGroup.ROOT) {
                    LicenseGroup.entries
                        .filter { it != LicenseGroup.ROOT }
                        .map { choice ->
                            NavigationItem(choice.title) { group = choice }
                        }
                } else
                    entries.map { entry ->
                        NavigationItem(
                            entry.title,
                            key = entry.assets.joinToString() + entry.title,
                        ) {
                            document =
                                entry.title to
                                    buildString {
                                        if (entry.summary.isNotBlank()) appendLine(entry.summary)
                                        entry.assets.forEach { file ->
                                            appendLine()
                                            appendLine(
                                                context.assets.open(file).bufferedReader().use {
                                                    it.readText()
                                                }
                                            )
                                        }
                                    }
                        }
                    }
            )
        }
    }
    BackHandler(enabled = group != LicenseGroup.ROOT) { group = LicenseGroup.ROOT }
    if (wheel && group != LicenseGroup.ROOT) {
        WheelSettingsOverlay(group.title, { group = LicenseGroup.ROOT }, content = body)
    } else {
        BaseScreen(
            group.title,
            {
                if (group == LicenseGroup.ROOT) nav.popBackStack() else group = LicenseGroup.ROOT
            },
            modifier = modifier,
        ) {
            body()
        }
    }
    document?.let { (title, text) ->
        if (wheel) {
            WheelReadingPage(title, { document = null }) { ReadingDocument(text) }
        } else {
            CymaticDialog(
                title = title,
                onDismissRequest = { document = null },
                content = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    ) {
                        ReadingDocument(text)
                    }
                },
                buttons = { CymaticDialogButton("Close", { document = null }) },
            )
        }
    }
}
