package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.PixelIcon
import com.pxr.cymatic.ui.components.common.ScreenBackHandler
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings

@Composable
internal fun WheelSearchableScreenHeader(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)?,
    onMenuLongClick: (() -> Unit)? = null,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    showSearchAction: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    if (isSearchActive) {
        val closeSearch = {
            onSearchActiveChange(false)
            onSearchQueryChange("")
        }
        val showIcons = LocalInterfaceSettings.current.showIcons
        ScreenBackHandler(onBack = closeSearch)
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(56.dp)
                        .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showIcons) {
                    PixelIcon(
                        resId = R.drawable.ic_pixel_search,
                        contentDescription = "Search",
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                        ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search...",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                closeSearch()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (showIcons) {
                        PixelIcon(
                            resId = R.drawable.ic_pixel_close,
                            contentDescription = if (searchQuery.isNotEmpty()) "Clear search" else "Close search",
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    } else {
                        Text(
                            "X",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    } else {
        WheelScreenHeader(
            modifier = modifier,
            onBackClick = onBackClick,
            onMenuLongClick = onMenuLongClick,
            actions = {
                actions()
                if (showSearchAction) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable { onSearchActiveChange(true) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_pixel_search),
                            "Search",
                            Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
    }
}
