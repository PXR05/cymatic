package com.pxr.cymatic.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
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

internal data class ReleaseInfo(
    val versionName: String,
    val versionUrl: String,
    val changelog: String,
    val apkUrl: String?,
)

@Composable
internal fun VersionSettingsScreen(
    modifier: Modifier = Modifier,
    model: VersionSettingsViewModel = viewModel(),
    pageRoute: String = Screen.VersionSettings.route,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { getCurrentVersion(context) }

    LaunchedEffect(model.downloadId) {
        val id = model.downloadId ?: return@LaunchedEffect
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        withContext(Dispatchers.IO) {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(id)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        when (
                            cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            )
                        ) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = downloadManager.getUriForDownloadedFile(id)
                                withContext(Dispatchers.Main) {
                                    model.downloadedApkUri = uri
                                    model.downloadStatus = "Downloaded"
                                }
                                return@withContext
                            }

                            DownloadManager.STATUS_FAILED -> {
                                withContext(Dispatchers.Main) {
                                    model.downloadStatus = "Download failed"
                                }
                                return@withContext
                            }

                            DownloadManager.STATUS_RUNNING ->
                                withContext(Dispatchers.Main) {
                                    model.downloadStatus = "Downloading"
                                }

                            DownloadManager.STATUS_PENDING ->
                                withContext(Dispatchers.Main) {
                                    model.downloadStatus = "Pending"
                                }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun checkUpdates() = model.checkUpdates()

    fun download(release: ReleaseInfo) {
        enqueueDownload(context, release).also {
            model.downloadId = it
            model.downloadStatus = "Starting"
        }
    }

    LaunchedEffect(pageRoute) {
        if (pageRoute == Screen.Changelog.route && model.latestRelease == null) checkUpdates()
    }
    if (pageRoute == Screen.VersionSettings.route) {
        BaseScreen(
            title = "Version",
            onBackClick = { navController.popBackStack() },
            modifier = modifier,
        ) {
            NavigationList(
                buildList {
                    add(
                        NavigationItem(
                            "Current version",
                            "${currentVersion.name} (${currentVersion.code})",
                            enabled = false,
                        )
                    )
                    add(
                        NavigationItem(
                            "Check updates",
                            if (model.isChecking) "Checking" else null,
                            enabled = !model.isChecking,
                            onClick = ::checkUpdates,
                        )
                    )
                    model.latestRelease?.let { release ->
                        add(NavigationItem("Latest version", release.versionName, enabled = false))
                        add(NavigationItem("GitHub") { openUrl(context, release.versionUrl) })
                        add(
                            NavigationItem("Changelog") {
                                navController.navigate(Screen.Changelog.route)
                            }
                        )
                        add(
                            NavigationItem(
                                "Download",
                                if (release.apkUrl == null) "No APK available"
                                else model.downloadStatus,
                                enabled = release.apkUrl != null && model.downloadId == null,
                            ) {
                                download(release)
                            }
                        )
                    }
                    if (model.downloadedApkUri != null)
                        add(
                            NavigationItem("Install update") {
                                installApk(
                                    context,
                                    model.downloadedApkUri,
                                )
                            }
                        )
                    model.errorMessage?.let {
                        add(NavigationItem("Update status", it, enabled = false))
                    }
                }
            )
        }
    }
    if (pageRoute == Screen.Changelog.route) {
        ReadingScreen("Changelog", { navController.popBackStack() }) {
            Text(
                model.latestRelease
                    ?.changelog
                    ?.replace("\r\n", "\n")
                    ?.replace(Regex("\n{3,}"), "\n\n")
                    ?.ifBlank { "No changelog provided." } ?: "No changelog provided."
            )
        }
    }
}

private suspend fun fetchLatestRelease(): ReleaseInfo? =
    withContext(Dispatchers.IO) {
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
                        if (isPlayerApk(name)) {
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
                        apkUrl = apkUrl,
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

private fun enqueueDownload(
    context: Context,
    release: ReleaseInfo,
): Long {
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    val request =
        DownloadManager.Request(release.apkUrl?.toUri())
            .setTitle("Cymatic update ${release.versionName}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "${PLAYER_APK_PREFIX}${release.versionName}.apk",
            )
    return downloadManager.enqueue(request)
}

private fun installApk(context: Context, apkUri: Uri?) {
    val uri = apkUri ?: return
    if (!context.packageManager.canRequestPackageInstalls()) {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        return
    }
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
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
    val code: Long,
)

private fun getCurrentVersion(context: Context): AppVersion {
    return try {
        val info =
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        AppVersion(
            name = info.versionName ?: "0.0.0",
            code = PackageInfoCompat.getLongVersionCode(info),
        )
    } catch (_: Exception) {
        AppVersion(name = "0.0.0", code = 0L)
    }
}

internal class VersionSettingsViewModel : ViewModel() {
    var isChecking by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var latestRelease by mutableStateOf<ReleaseInfo?>(null)
    var downloadId by mutableStateOf<Long?>(null)
    var downloadStatus by mutableStateOf("Idle")
    var downloadedApkUri by mutableStateOf<Uri?>(null)

    fun checkUpdates() {
        if (isChecking) return
        isChecking = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val result = fetchLatestRelease()
                if (result != null) latestRelease = result
                else errorMessage = "Unable to check for updates."
            } finally {
                isChecking = false
            }
        }
    }
}
