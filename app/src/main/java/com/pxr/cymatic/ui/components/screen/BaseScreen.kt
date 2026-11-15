package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.LocalWheelOverlay

val LocalScreenNotice = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun BaseScreen(
    title: String,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    onMenuLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    isSearchActive: Boolean = false,
    onSearchActiveChange: ((Boolean) -> Unit)? = null,
    showSearchAction: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wheel = LocalWheelNavigation.current
    val overlay = LocalWheelOverlay.current
    val back = if (onBackClick != null) wheel?.onBackRequested ?: onBackClick else null

    val menuHold = onMenuLongClick ?: onTitleClick
    if (menuHold != null && wheel != null) {
        val currentMenuHold by rememberUpdatedState(menuHold)
        DisposableEffect(wheel, overlay) {
            val handler: () -> Unit = { currentMenuHold() }
            wheel.registerMenuHold(handler, overlay)
            onDispose {
                wheel.unregisterMenuHold(handler)
            }
        }
    }

    val background = MaterialTheme.colorScheme.background
    CompositionLocalProvider(LocalContentColor provides contentColorFor(background)) {
        Column(modifier.fillMaxSize().background(background)) {
            if (
                searchQuery != null && onSearchQueryChange != null && onSearchActiveChange != null
            ) {
                WheelSearchableScreenHeader(
                    onBackClick = back ?: onTitleClick,
                    onMenuLongClick = onMenuLongClick,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = onSearchActiveChange,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    actions = actions,
                    showSearchAction = showSearchAction,
                )
            } else {
                WheelScreenHeader(
                    onBackClick = back ?: onTitleClick,
                    onMenuLongClick = onMenuLongClick,
                    actions = actions,
                )
            }
            LocalScreenNotice.current?.invoke()
            content()
        }
    }
}
