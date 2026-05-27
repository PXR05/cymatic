package com.pxr.cymatic.ui.components.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.pxr.cymatic.R

@Composable
fun SearchableScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBackClick: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "<",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
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
                    fontSize = 20.sp,
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .padding(vertical = 12.dp)
            )

            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "X",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
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
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "<",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(
                        onClick = onBackClick,
                        indication = null,
                        interactionSource = null
                    )
                    .padding(24.dp, 16.dp)
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        vertical = 16.dp
                    )
                    .then(
                        if (onTitleClick != null) {
                            Modifier.clickable(
                                onClick = onTitleClick,
                                indication = null,
                                interactionSource = null
                            )
                        } else {
                            Modifier
                        }
                    )
            )

            actions()

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
                    .size(24.dp)
            )
        }
    }
}
