package com.pxr.cymatic.ui.screens.settings.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.data.licenses.LicenseCatalog
import com.pxr.cymatic.data.licenses.LicenseGroup
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.ReadingDocument
import com.pxr.cymatic.ui.components.common.SettingsReadingDialog
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesSettingsScreen(modifier: Modifier = Modifier) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val wheel = LocalWheelNavigation.current != null
    var group by remember { mutableStateOf(LicenseGroup.ROOT) }
    var document by remember { mutableStateOf<Pair<String, String>?>(null) }
    val loaded by
        produceState<Result<LicenseCatalog>?>(null, context) {
            value = withContext(Dispatchers.IO) { runCatching { LicenseCatalog(context.assets) } }
        }
    val catalog = loaded?.getOrNull()
    val entries = catalog?.entries(group).orEmpty()
    val body: @Composable () -> Unit = {
        key(group) {
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
                            NavigationItem(choice.title) { group = choice }
                        }
                } else
                    entries.map { entry ->
                        NavigationItem(
                            entry.title,
                            key = entry.assets.joinToString() + entry.title,
                        ) {
                            catalog?.let { document = entry.title to it.document(entry) }
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
        SettingsReadingDialog(title, { document = null }, PaddingValues(horizontal = 16.dp)) {
            ReadingDocument(text)
        }
    }
}
