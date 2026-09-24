package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.screens.home.LauncherAppsViewModel

@Composable
fun HiddenAppsScreen(
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: LauncherAppsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val allApps by viewModel.allApps.collectAsState()
    val hiddenPackages by viewModel.hiddenPackages.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(allApps, hiddenPackages, searchQuery) {
        val query = searchQuery.trim()
        // Sort hidden first, then alphabetically.
        val ordered = (allApps.filter { it.packageName in hiddenPackages }
            .sortedBy { it.label.lowercase() } +
            allApps.filter { it.packageName !in hiddenPackages }
                .sortedBy { it.label.lowercase() })
        if (query.isBlank()) {
            ordered
        } else {
            ordered.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    BaseScreen(
        title = "Hidden Apps",
        onBackClick = if (showBackButton) {
            { navController.popBackStack() }
        } else {
            null
        },
        modifier = modifier,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = if (hiddenPackages.isEmpty()) {
                    "No hidden apps. Hide apps here or via long-press in the drawer."
                } else {
                    "${hiddenPackages.size} hidden — tap UNHIDE to restore, HIDE to add more."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (filteredApps.isEmpty()) {
                Text(
                    text = "NO MATCH",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isHidden = app.packageName in hiddenPackages
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            val iconBitmap = remember(app.packageName, app.icon) {
                                app.icon?.asImageBitmap()
                            }
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = null,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val pillShape = RoundedCornerShape(8.dp)
                            Text(
                                text = if (isHidden) "UNHIDE" else "HIDE",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .clip(pillShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onBackground, pillShape)
                                    .clickable(
                                        onClick = {
                                            if (isHidden) {
                                                viewModel.unhideApp(app.packageName)
                                            } else {
                                                viewModel.hideApp(app.packageName)
                                            }
                                        },
                                        indication = null,
                                        interactionSource = null
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
