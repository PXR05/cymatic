package com.pxr.cymatic.ui.components.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.pxr.cymatic.design.R

@Composable
internal fun WheelSearchableScreenHeader(
    modifier: Modifier,
    title: String,
    onBackClick: (() -> Unit)?,
    onTitleClick: (() -> Unit)?,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    if (isSearchActive) {
        val closeSearch = {
            onSearchActiveChange(false)
            onSearchQueryChange("")
        }
        BackHandler(onBack = closeSearch)
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        WheelScreenHeader(
            modifier = modifier,
            onBackClick = closeSearch,
            actions = {
                if (searchQuery.isNotEmpty()) {
                    Box(
                        Modifier.size(48.dp).clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", fontSize = 16.sp)
                    }
                }
            }
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search...",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    } else {
        ScreenHeader(modifier, title, onBackClick, onTitleClick) {
            actions()
            Box(
                Modifier.size(48.dp).clickable { onSearchActiveChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_pixel_search), "Search", Modifier.size(20.dp))
            }
        }
    }
}
