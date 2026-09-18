package com.pxr.cymatic.ui.components.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    delayMillis: Long = 300L
) {
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        showLoading = true
    }

    if (showLoading) {
        val infiniteTransition = rememberInfiniteTransition(label = "loading")
        val frame by infiniteTransition.animateValue(
            initialValue = 0,
            targetValue = 8,
            typeConverter = Int.VectorConverter,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "frame"
        )

        val spinner = when (frame) {
            0 -> "▖"
            1 -> "▘"
            2 -> "▝"
            3 -> "▗"
            4 -> "▚"
            5 -> "▞"
            6 -> "█"
            else -> "░"
        }

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = spinner,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String = "NO CONTENT",
    message: String = "There is nothing to show here at the moment.",
    iconText: String = "( ! )",
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = iconText,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title.uppercase(),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel.uppercase(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelLarge
                      )
                }
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String = "An unexpected error occurred while loading content.",
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = "ERROR OCCURRED",
        message = message,
        iconText = "[ X ]",
        actionLabel = "RETRY",
        onActionClick = onRetry,
        modifier = modifier
    )
}

@Composable
fun PermissionDeniedState(
    onGrantClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = "PERMISSION REQUIRED",
        message = "Cymatic needs storage permission to scan and play audio files on your device.",
        iconText = "[ ! ]",
        actionLabel = "GRANT PERMISSION",
        onActionClick = onGrantClick,
        modifier = modifier
    )
}

fun hasStoragePermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}
