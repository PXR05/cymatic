package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun PlaybackSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val resumeOnBt by SettingsStore.resumeOnBluetoothReconnectFlow.collectAsState(
        initial = SettingsStore.currentResumeOnBluetoothReconnect
    )
    val fadeEnabled by SettingsStore.fadeEnabledFlow.collectAsState(
        initial = SettingsStore.currentFadeEnabled
    )

    BaseScreen(
        title = "Playback",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Text(
                text = "Bluetooth",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (resumeOnBt) "I" else "O",
                    fontSize = 16.sp,
                    color = if (resumeOnBt) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .background(if (resumeOnBt) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                        .padding(28.dp, 20.dp)
                        .clickable(
                            onClick = {
                                scope.launch {
                                    SettingsStore.setResumeOnBluetoothReconnect(!resumeOnBt)
                                }
                            },
                            indication = null,
                            interactionSource = null
                        ),
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(64.dp)
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Auto-resume",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Resume on Bluetooth connect",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Audio Transitions",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (fadeEnabled) "I" else "O",
                    fontSize = 16.sp,
                    color = if (fadeEnabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .background(if (fadeEnabled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                        .padding(28.dp, 20.dp)
                        .clickable(
                            onClick = {
                                scope.launch {
                                    SettingsStore.setFadeEnabled(!fadeEnabled)
                                }
                            },
                            indication = null,
                            interactionSource = null
                        ),
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(64.dp)
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Fade In / Out",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Smoothly fade audio",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
