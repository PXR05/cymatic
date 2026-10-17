package com.pxr.cymatic.ui.components.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.design.R
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchableScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBackClick: (() -> Unit)?,
    onTitleClick: (() -> Unit)? = null,
    onMenuLongClick: (() -> Unit)? = null,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    showSearchAction: Boolean = true
) {
    if (LocalWheelNavigation.current != null) {
        WheelSearchableScreenHeader(
            modifier = modifier,
            title = title,
            onBackClick = onBackClick,
            onTitleClick = onTitleClick,
            onMenuLongClick = onMenuLongClick,
            isSearchActive = isSearchActive,
            onSearchActiveChange = onSearchActiveChange,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            actions = actions,
            showSearchAction = showSearchAction
        )
        return
    }

    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isTitleCJK = title.contains(cjkRegex)

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isSearchActive) {
        BackHandler {
            onSearchActiveChange(false)
            onSearchQueryChange("")
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "<",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    onSearchActiveChange(false)
                                    onSearchQueryChange("")
                                },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(24.dp, 12.dp)
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                }

                Spacer(Modifier.width(64.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "X",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        onSearchQueryChange("")
                                    },
                                    indication = null,
                                    interactionSource = null
                                )
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
            HeaderClock(modifier = Modifier.align(Alignment.Center))
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBackClick != null) {
                        val backModifier = if (onMenuLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = onBackClick,
                                onLongClick = onMenuLongClick,
                                indication = null,
                                interactionSource = null
                            )
                        } else {
                            Modifier.clickable(
                                onClick = onBackClick,
                                indication = null,
                                interactionSource = null
                            )
                        }
                        Text(
                            text = "<",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .then(backModifier)
                                .padding(24.dp, 16.dp)
                        )
                    }
                    val titleModifier = when {
                        onTitleClick != null && onMenuLongClick != null -> {
                            Modifier.combinedClickable(
                                onClick = onTitleClick,
                                onLongClick = onMenuLongClick,
                                indication = null,
                                interactionSource = null
                            )
                        }
                        onTitleClick != null -> {
                            Modifier.clickable(
                                onClick = onTitleClick,
                                indication = null,
                                interactionSource = null
                            )
                        }
                        onMenuLongClick != null -> {
                            Modifier.combinedClickable(
                                onClick = onMenuLongClick,
                                onLongClick = onMenuLongClick,
                                indication = null,
                                interactionSource = null
                            )
                        }
                        else -> Modifier
                    }
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (onBackClick != null) 0.dp else 24.dp,
                                top = 16.dp,
                                bottom = 16.dp,
                                end = 8.dp
                            )
                            .then(titleModifier)
                    )
                }

                Spacer(Modifier.width(64.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()

                    if (showSearchAction) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pixel_search),
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        onSearchActiveChange(true)
                                    },
                                    indication = null,
                                    interactionSource = null
                                )
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .size(20.dp)
                        )
                    }
                }
            }
            HeaderClock(modifier = Modifier.align(Alignment.Center))
        }
    }
}
