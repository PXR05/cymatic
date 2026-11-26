package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.sync.subsonic.SubsonicClient
import com.pxr.cymatic.sync.subsonic.SubsonicMapping
import com.pxr.cymatic.sync.subsonic.SubsonicVerification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class ConnectionInput(
    val provider: RemoteProvider,
    val address: String,
    val username: String,
    val password: String = "",
    val apiKey: String = "",
    val displayName: String = "",
    val existingId: String? = null,
)

fun normalizeCandidateUrls(rawAddress: String): List<String> {
    val trimmed = rawAddress.trim()
    require(trimmed.isNotBlank()) { "Enter a server address." }
    if (trimmed.contains("://")) {
        return listOf(AudioStreamClient.normalizeBaseUrl(trimmed))
    }
    return listOf(
        AudioStreamClient.normalizeBaseUrl("https://$trimmed"),
        AudioStreamClient.normalizeBaseUrl("http://$trimmed"),
    )
}

class RemoteSourceConnector(
    private val context: Context,
    private val input: ConnectionInput,
) {
    private val cancelled = AtomicBoolean()

    @Volatile
    private var activeClient: RemoteLibraryClient? = null

    fun cancel() {
        cancelled.set(true)
        activeClient?.cancel()
    }

    suspend fun connect(): RemoteSourceConfig =
        withContext(Dispatchers.IO) {
            val application = context.applicationContext
            val candidateUrls =
                runCatching { normalizeCandidateUrls(input.address) }
                    .getOrElse {
                        throw IllegalArgumentException(
                            "Enter a valid server address (for example, music.example.com)."
                        )
                    }
            var resolvedAccount = input.username.trim()
            val allExisting = RemoteSourceRegistry.all(application)
            val existing = input.existingId?.let { id -> allExisting.firstOrNull { it.id == id } }
            val existingCreds = existing?.let { SyncCredentialStore.get(application, it.id) }

            val resolvedUrl: String
            val credentials: RemoteCredentials
            val verifiedDisplayName: String
            val extras: Map<String, String>

            when (input.provider) {
                RemoteProvider.AUDIOSTREAM -> {
                    require(resolvedAccount.isNotBlank()) { "Enter your username." }
                    val secret =
                        input.password.takeIf { it.isNotEmpty() }
                            ?: existingCreds?.password
                    require(!secret.isNullOrEmpty()) { "Enter your password." }

                    if (existing == null && candidateUrls.size == 1) {
                        checkNotDuplicate(
                            allExisting,
                            input.provider.value,
                            candidateUrls.first(),
                            resolvedAccount,
                            null
                        )
                    }

                    var matchedUrl: String? = null
                    var lastError: Throwable? = null
                    for ((index, candidate) in candidateUrls.withIndex()) {
                        val client = AudioStreamClient(candidate, resolvedAccount, secret)
                        activeClient = client
                        try {
                            if (cancelled.get()) throw SyncCancelledException()
                            client.authenticate()
                            currentCoroutineContext().ensureActive()
                            if (cancelled.get()) throw SyncCancelledException()
                            matchedUrl = candidate
                            break
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: SyncCancelledException) {
                            throw error
                        } catch (error: RemoteRequestException) {
                            lastError = error
                            if (error.status == 401 || error.status == 403 || index == candidateUrls.lastIndex) {
                                throw error
                            }
                        } catch (error: Throwable) {
                            lastError = error
                            if (index == candidateUrls.lastIndex) throw error
                        } finally {
                            client.cancel()
                            activeClient = null
                        }
                    }
                    resolvedUrl =
                        matchedUrl ?: throw (lastError ?: IOException("Could not connect."))
                    credentials = RemoteCredentials(password = secret)
                    verifiedDisplayName =
                        input.displayName.trim()
                            .ifBlank { existing?.displayName.orEmpty().trim() }
                            .ifBlank {
                                RemoteSourceMigrationPlanner.hostDisplayName(
                                    resolvedUrl,
                                    input.provider.value
                                )
                            }
                    extras = emptyMap()
                }

                RemoteProvider.SUBSONIC -> {
                    val password =
                        input.password.takeIf { it.isNotEmpty() }
                            ?: existingCreds?.password
                    val apiKey =
                        input.apiKey.trim().takeIf { it.isNotEmpty() }
                            ?: existingCreds?.apiKey
                    require(!password.isNullOrEmpty() || !apiKey.isNullOrEmpty()) {
                        "Enter your password or an API key."
                    }
                    if (apiKey.isNullOrEmpty()) {
                        require(resolvedAccount.isNotBlank()) { "Enter your username." }
                    }

                    val subsonicCandidates =
                        candidateUrls.map(SubsonicMapping::normalizeBaseUrl).distinct()
                    if (existing == null && subsonicCandidates.size == 1 && resolvedAccount.isNotBlank()) {
                        checkNotDuplicate(
                            allExisting,
                            input.provider.value,
                            subsonicCandidates.first(),
                            resolvedAccount,
                            null,
                        )
                    }

                    var matchedUrl: String? = null
                    var verification: SubsonicVerification? = null
                    var lastError: Throwable? = null
                    for ((index, candidate) in subsonicCandidates.withIndex()) {
                        val client =
                            SubsonicClient(
                                baseUrl = candidate,
                                username = resolvedAccount,
                                password = password,
                                apiKey = apiKey,
                                supportsApiKey = !apiKey.isNullOrEmpty(),
                                apiKeyRejected = false,
                            )
                        activeClient = client
                        try {
                            if (cancelled.get()) throw SyncCancelledException()
                            val result = client.verify()
                            currentCoroutineContext().ensureActive()
                            if (cancelled.get()) throw SyncCancelledException()
                            matchedUrl = candidate
                            verification = result
                            break
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: SyncCancelledException) {
                            throw error
                        } catch (error: RemoteRequestException) {
                            lastError = error
                            if (error.status == 401 || error.status == 403 || index == subsonicCandidates.lastIndex) {
                                throw error
                            }
                        } catch (error: Throwable) {
                            lastError = error
                            if (index == subsonicCandidates.lastIndex) throw error
                        } finally {
                            client.cancel()
                            activeClient = null
                        }
                    }
                    resolvedUrl =
                        matchedUrl ?: throw (lastError ?: IOException("Could not connect."))
                    val verified =
                        verification ?: throw IOException("Could not verify Subsonic server.")
                    if (resolvedAccount.isBlank()) {
                        resolvedAccount = verified.resolvedUsername.orEmpty()
                    }
                    if (password.isNullOrEmpty() && resolvedAccount.isBlank()) {
                        resolvedAccount = "api-key"
                    }
                    credentials = RemoteCredentials(password = password, apiKey = apiKey)
                    val serverLabel =
                        verified.serverType
                            ?.takeIf(String::isNotBlank)
                            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    verifiedDisplayName =
                        input.displayName.trim()
                            .ifBlank { existing?.displayName.orEmpty().trim() }
                            .ifBlank {
                                serverLabel
                                    ?: RemoteSourceMigrationPlanner.hostDisplayName(
                                        resolvedUrl,
                                        input.provider.value,
                                    )
                            }
                    extras = buildMap {
                        put("openSubsonic", verified.openSubsonic.toString())
                        if (verified.extensions.isNotEmpty()) {
                            put("extensions", verified.extensions.joinToString(","))
                        }
                        verified.serverType?.let { put("serverType", it) }
                        verified.serverVersion?.let { put("serverVersion", it) }
                        if (verified.apiKeyRejected) {
                            put("apiKeyRejected", "true")
                        }
                    }
                }

                else -> error("This library provider is not supported yet")
            }

            checkNotDuplicate(
                allExisting,
                input.provider.value,
                resolvedUrl,
                resolvedAccount,
                existing?.id,
            )

            check(!LibrarySyncManager.isRunning && !LibraryCatalogManager.isRunning) {
                "Wait for the current library operation to finish."
            }

            val id = existing?.id ?: RemoteLibrarySource.newId()
            val usernameChanged = existing != null && existing.account != resolvedAccount

            if (usernameChanged) {
                RemoteSourceRemover.clearData(application, id, deleteDownloads = true)
            }

            SyncCredentialStore.save(application, id, credentials)

            val saved =
                if (existing == null) {
                    val others =
                        RemoteSourceRegistry.all(application)
                            .mapNotNullTo(mutableSetOf()) { it.folderName?.takeIf(String::isNotBlank) }
                    val folderName = SyncStorages.uniqueFolderName(verifiedDisplayName, others)
                    val config =
                        RemoteSourceConfig(
                            id = id,
                            provider = input.provider.value,
                            displayName = verifiedDisplayName,
                            address = resolvedUrl,
                            account = resolvedAccount,
                            enabled = true,
                            folderName = folderName,
                            extras = extras,
                            createdAt = System.currentTimeMillis(),
                        )
                    RemoteSourceRegistry.add(config)
                    config
                } else {
                    RemoteSourceRegistry.update(id, invalidateClient = true) { current ->
                        current.copy(
                            displayName = verifiedDisplayName,
                            address = resolvedUrl,
                            account = resolvedAccount,
                            extras = extras,
                            lastError = null,
                            contentMode = if (usernameChanged) SyncContentMode.NONE.value else current.contentMode,
                            contentConfigured = !usernameChanged && current.contentConfigured,
                            selectedPlaylists = if (usernameChanged) emptySet() else current.selectedPlaylists,
                            lastSyncTime = if (usernameChanged) 0L else current.lastSyncTime,
                            lastSyncResult = if (usernameChanged) "Not synced yet" else current.lastSyncResult,
                        )
                    } ?: existing
                }

            RemoteLibraries.invalidate(id)
            if (usernameChanged) {
                LibrarySyncManager.clearStatus()
                LibraryCatalogManager.clearStatus()
            }
            LibrarySyncJobService.reschedule(application)
            saved
        }

    private fun checkNotDuplicate(
        existingSources: List<RemoteSourceConfig>,
        provider: String,
        url: String,
        account: String,
        editingId: String?,
    ) {
        val targetFingerprint =
            runCatching { RemoteLibrarySource.legacyId(provider, url, account) }.getOrNull()
                ?: return
        val duplicate =
            existingSources.any { other ->
                other.id != editingId &&
                        runCatching {
                            RemoteLibrarySource.legacyId(
                                other.provider,
                                other.address,
                                other.account
                            )
                        }.getOrNull() == targetFingerprint
            }
        require(!duplicate) { "This library is already connected." }
    }
}

fun isCleartextPublicUrl(address: String): Boolean {
    val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true)) return false
    val host = uri.host?.lowercase(Locale.ROOT)?.trim('[', ']') ?: return false
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return false
    if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return false
    val ipv4 = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
    if (ipv4.size == 4) {
        val (a, b) = ipv4
        if (a == 10 || a == 127) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 169 && b == 254) return false
        if (a == 100 && b in 64..127) return false // Tailscale / CGNAT
    }
    return true
}

fun connectionError(error: Throwable, provider: RemoteProvider? = null): String {
    val providerName = provider?.label ?: "The server"
    return when (error) {
        is RemoteRequestException ->
            when (error.status) {
                401,
                403 -> "Sign-in was rejected. Check your credentials."

                404 -> "$providerName was not found at this address."
                429 -> "The server is busy. Try again shortly."
                else -> "The server could not complete the request. Try again."
            }

        is org.json.JSONException ->
            "This address did not return a valid $providerName response."

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
}
