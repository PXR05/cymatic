package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.LibrarySyncSettings
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioStreamConnection(
    private val context: Context,
    private val address: String,
    private val username: String,
    private val password: String,
) {
    private val cancelled = AtomicBoolean()
    @Volatile private var client: AudioStreamClient? = null

    fun cancel() {
        cancelled.set(true)
        client?.cancel()
    }

    suspend fun save(): RemoteLibrarySource =
        withContext(Dispatchers.IO) {
            val url = runCatching {
                AudioStreamClient.normalizeBaseUrl(address)
            }
                .getOrElse {
                    throw IllegalArgumentException(
                        "Enter a server address starting with http:// or https://."
                    )
                }
            require(username.isNotBlank()) { "Enter your username." }
            val source = RemoteLibrarySource("audiostream", url, username.trim())
            val changed = source.id != RemoteLibraries.currentSource().id
            val secret =
                password.takeIf { it.isNotEmpty() }
                    ?: if (!changed) SyncCredentialStore.getPassword(context, source.id) else null
            require(!secret.isNullOrEmpty()) { "Enter your password." }
            val connection = AudioStreamClient(url, username.trim(), secret)
            client = connection
            try {
                if (cancelled.get()) throw SyncCancelledException()
                connection.authenticate()
                currentCoroutineContext().ensureActive()
                if (cancelled.get()) throw SyncCancelledException()
                check(
                    !LibrarySyncManager.isRunning &&
                        LibraryCatalogManager.progress.value !is CatalogProgress.Refreshing
                ) {
                    "Wait for the current library operation to finish."
                }
                if (password.isNotEmpty())
                    SyncCredentialStore.savePassword(
                        context,
                        password,
                        source.id,
                    )
                LibrarySyncSettings.setSyncConnection(url, username, changed)
                if (changed) {
                    LibrarySyncManager.clearStatus()
                    LibraryCatalogManager.clearStatus()
                }
                source
            } finally {
                connection.cancel()
                client = null
            }
        }
}

internal fun connectionError(error: Throwable): String =
    when (error) {
        is AudioStreamRequestException ->
            when (error.status) {
                401,
                403 -> "Sign-in was rejected. Check your username and password."
                404 -> "AudioStream was not found at this address."
                429 -> "The server is busy. Try again shortly."
                else -> "The server could not complete sign-in. Try again."
            }

        is org.json.JSONException -> "This address did not return an AudioStream sign-in response."
        is java.net.SocketTimeoutException ->
            "Connection timed out. Check the address and network, then try again."
        is java.net.UnknownHostException -> "Server not found. Check the address and network."
        is javax.net.ssl.SSLException ->
            "Could not establish a secure connection. Check the server address and certificate."
        is IllegalArgumentException,
        is IllegalStateException -> error.message ?: "Check the connection details."

        is IOException -> "Could not connect. Check the server address and network."
        else -> "Could not save the connection. Try again."
    }
