package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.rememberPlaybackState

@Composable
fun BaseScreen(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val mediaController = LocalMediaController.current
    val playbackState = rememberPlaybackState(mediaController)

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        val bottomPadding = if (playbackState.currentMediaId == null) {
            innerPadding.calculateBottomPadding()
        } else {
            0.dp
        }
        Column(
            modifier = Modifier
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            ScreenHeader(
                title = title,
                onBackClick = onBackClick
            )
            content()
        }
    }
}


