package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pxr.cymatic.ui.components.launcher.LibraryWallpaperBackdrop

@Composable
fun BaseScreen(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    isSearchActive: Boolean = false,
    onSearchActiveChange: ((Boolean) -> Unit)? = null,
    showWallpaperBackdrop: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showWallpaperBackdrop) {
            LibraryWallpaperBackdrop()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (showWallpaperBackdrop) Color.Transparent else MaterialTheme.colorScheme.background
        ) { innerPadding ->
            val topPadding = innerPadding.calculateTopPadding()
            Column(
                modifier = Modifier
                    .padding(top = topPadding)
            ) {
                if (searchQuery != null && onSearchQueryChange != null && onSearchActiveChange != null) {
                    SearchableScreenHeader(
                        title = title,
                        onBackClick = onBackClick,
                        onTitleClick = onTitleClick,
                        isSearchActive = isSearchActive,
                        onSearchActiveChange = onSearchActiveChange,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        actions = actions
                    )
                } else {
                    ScreenHeader(
                        title = title,
                        onBackClick = onBackClick,
                        onTitleClick = onTitleClick,
                        actions = actions
                    )
                }
                content()
            }
        }
    }
}


