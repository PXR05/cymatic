package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun LauncherSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val use24Hour by LauncherStore.use24HourFlow.collectAsState(initial = true)
    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)
    val showPinnedLabels by LauncherStore.showPinnedLabelsFlow.collectAsState(initial = false)
    val showAllAppsLabels by LauncherStore.showAllAppsLabelsFlow.collectAsState(initial = true)

    BaseScreen(
        title = "Launcher",
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
                text = "Clock",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LauncherToggleRow(
                title = "24-Hour Time",
                subtitle = "Use 24-hour clock format",
                checked = use24Hour,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setUse24Hour(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Clock",
                subtitle = "Show the clock on the home screen",
                checked = showClock,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowClock(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Day",
                subtitle = "Show the weekday under the clock",
                checked = showDay,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowDay(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Date",
                subtitle = "Show the date under the clock",
                checked = showDate,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowDate(value) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Apps",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LauncherToggleRow(
                title = "Most Used Labels",
                subtitle = "Show app names under most used icons",
                checked = showPinnedLabels,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowPinnedLabels(value) }
                }
            )

            LauncherToggleRow(
                title = "All Apps Labels",
                subtitle = "Show app names in the app drawer",
                checked = showAllAppsLabels,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowAllAppsLabels(value) }
                }
            )
        }
    }
}

@Composable
private fun LauncherToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = if (checked) "I" else "O",
            fontSize = 16.sp,
            color = if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .background(if (checked) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .clickable(
                    onClick = { onCheckedChange(!checked) },
                    indication = null,
                    interactionSource = null
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}
