package com.pxr.cymatic.ui.screens.settings.licenses

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.data.licenses.LicenseCatalog
import com.pxr.cymatic.data.licenses.LicenseGroup
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LicensesSettingsScreen(
    modifier: Modifier = Modifier,
    group: LicenseGroup = LicenseGroup.ROOT,
    documentIndex: Int? = null,
) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val loaded by
    produceState<Result<LicenseCatalog>?>(
        LicenseCatalog.getCached()?.let { Result.success(it) },
        context,
    ) {
        if (value == null) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching { LicenseCatalog.getInstance(context.assets) }
                }
        }
    }
    val catalog = loaded?.getOrNull()
    val entries = catalog?.entries(group).orEmpty()
    if (documentIndex != null) {
        val document = entries.getOrNull(documentIndex)
        ReadingScreen(document?.title ?: group.title, { nav.popBackStack() }) {
            if (document != null && catalog != null) ReadingDocument(catalog.document(document))
            else Text(if (loaded == null) "Loading licenses" else "Could not load license")
        }
    } else {
        BaseScreen(group.title, { nav.popBackStack() }, modifier = modifier) {
            if (group != LicenseGroup.ROOT && catalog == null) {
                Text(
                    if (loaded?.isFailure == true) "Could not load licenses" else "Loading licenses"
                )
            }
            NavigationList(
                if (group == LicenseGroup.ROOT) {
                    LicenseGroup.entries
                        .filter { it != LicenseGroup.ROOT }
                        .map { choice ->
                            NavigationItem(choice.title) {
                                nav.navigate(Screen.LicenseGroupPage.createRoute(choice.name))
                            }
                        }
                } else
                    entries.mapIndexed { index, entry ->
                        NavigationItem(
                            entry.title,
                            key = entry.assets.joinToString() + entry.title,
                        ) {
                            nav.navigate(Screen.LicenseDocument.createRoute(group.name, index))
                        }
                    }
            )
        }
    }
}
