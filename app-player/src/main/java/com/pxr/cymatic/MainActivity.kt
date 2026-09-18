package com.pxr.cymatic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.state.rememberMusicWindowState

class MainActivity : MusicActivity() {
    @Composable
    override fun AppContent() {
        val state = rememberMusicWindowState(window)
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                if (!state.isExpanded) {
                    MusicNavHost(home = { HomeScreen() }, modifier = Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier.padding(
                        bottom = WindowInsets.systemBars.asPaddingValues()
                            .calculateBottomPadding()
                    )
                ) {
                    if (state.hasPlayback) {
                        if (!state.isExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        PlayerBar(
                            modifier = if (state.isExpanded) Modifier.weight(1f) else Modifier,
                            isDocked = state.isDocked
                        )
                    }
                }
            }
        }
    }
}
