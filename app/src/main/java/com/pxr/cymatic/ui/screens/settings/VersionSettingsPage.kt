package com.pxr.cymatic.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_OWNER = "pxr05"
private const val GITHUB_REPO = "cymatic"

private data class ReleaseInfo(
    val versionName: String,
    val versionUrl: String,
    val changelog: String,
    val apkUrl: String?
)

@Composable
fun VersionSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontFamily = FontFamily(Font(R.font.pixel))
    val sharpCorners = RoundedCornerShape(0.dp)
    val currentVersion = remember { getCurrentVersion(context) }

    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showChangelog by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadStatus by remember { mutableStateOf("Idle") }
    var downloadedApkUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        withContext(Dispatchers.IO) {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(id)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = downloadManager.getUriForDownloadedFile(id)
                                withContext(Dispatchers.Main) {
                                    downloadedApkUri = uri
                                    downloadStatus = "Downloaded"
                                }
                                return@withContext
                            }

                            DownloadManager.STATUS_FAILED -> {
                                withContext(Dispatchers.Main) {
                                    downloadStatus = "Download failed"
                                }
                                return@withContext
                            }

                            DownloadManager.STATUS_RUNNING -> withContext(Dispatchers.Main) {
                                downloadStatus = "Downloading"
                            }

                            DownloadManager.STATUS_PENDING -> withContext(Dispatchers.Main) {
                                downloadStatus = "Pending"
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    BaseScreen(
        title = "Version",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Current version: ${currentVersion.name} (${currentVersion.code})",
                fontFamily = fontFamily
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        isChecking = true
                        errorMessage = null
                        scope.launch {
                            val result = fetchLatestRelease()
                            isChecking = false
                            if (result != null) {
                                latestRelease = result
                            } else {
                                errorMessage = "Unable to check for updates."
                            }
                        }
                    },
                    enabled = !isChecking,
                    shape = sharpCorners,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Check updates",
                        fontFamily = fontFamily
                    )
                }

                latestRelease?.versionUrl?.let { url ->
                    OutlinedButton(
                        onClick = { openUrl(context, url) },
                        shape = sharpCorners,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "GitHub",
                            fontFamily = fontFamily
                        )

                    }
                }
            }

            latestRelease?.let { release ->
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.secondary
                )

                val isUpdateAvailable = isVersionNewer(release.versionName, currentVersion.name)
                Text(
                    text = if (isUpdateAvailable) {
                        "Update available: ${release.versionName}"
                    } else {
                        "You are on the latest version."
                    },
                    fontFamily = fontFamily
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showChangelog = !showChangelog },
                        shape = sharpCorners,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (showChangelog) "Hide changelog" else "Show changelog",
                            fontFamily = fontFamily
                        )
                    }

                    if (release.apkUrl != null) {
                        Button(
                            onClick = {
                                enqueueDownload(context, release).also {
                                    downloadId = it
                                    downloadStatus = "Starting"
                                }
                            },
                            enabled = downloadId == null,
                            shape = sharpCorners,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Download",
                                fontFamily = fontFamily
                            )

                        }
                    }
                }

                if (downloadId != null) {
                    Text(
                        text = "Download status: $downloadStatus",
                        fontFamily = fontFamily
                    )
                }

                if (downloadedApkUri != null) {
                    Button(
                        onClick = { installApk(context, downloadedApkUri) },
                        shape = sharpCorners,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Install update",
                            fontFamily = fontFamily
                        )

                    }
                } else if (release.apkUrl == null) {
                    Text(
                        text = "No APK asset found in latest release.",
                        fontFamily = fontFamily,
                    )
                }

                if (showChangelog) {
                    val normalizedChangelog = release.changelog
                        .replace("\r\n", "\n")
                        .replace(Regex("\n{3,}"), "\n\n")

                    Text(
                        text = normalizedChangelog.ifBlank { "No changelog provided." },
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary)
                            .padding(16.dp)
                    )
                }
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "")
            val body = json.optString("body", "")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (tagName.isNotBlank()) {
                ReleaseInfo(
                    versionName = tagName.trimStart('v', 'V'),
                    versionUrl = htmlUrl,
                    changelog = body,
                    apkUrl = apkUrl
                )
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun isVersionNewer(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val max = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until max) {
        val latestPart = latestParts.getOrElse(i) { 0 }
        val currentPart = currentParts.getOrElse(i) { 0 }
        if (latestPart > currentPart) return true
        if (latestPart < currentPart) return false
    }
    return false
}

private fun enqueueDownload(context: Context, release: ReleaseInfo): Long {
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    val request = DownloadManager.Request(release.apkUrl?.toUri())
        .setTitle("Cymatic update ${release.versionName}")
        .setDescription("Downloading update")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "cymatic-${release.versionName}.apk"
        )
    return downloadManager.enqueue(request)
}

private fun installApk(context: Context, apkUri: Uri?) {
    val uri = apkUri ?: return
    if (!context.packageManager.canRequestPackageInstalls()) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    context.startActivity(intent)
}

private data class AppVersion(
    val name: String,
    val code: Long
)

private fun getCurrentVersion(context: Context): AppVersion {
    return try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        AppVersion(
            name = info.versionName ?: "0.0.0",
            code = PackageInfoCompat.getLongVersionCode(info)
        )
    } catch (_: Exception) {
        AppVersion(name = "0.0.0", code = 0L)
    }
}