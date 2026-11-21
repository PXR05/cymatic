package com.pxr.cymatic.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SyncCredentialStore {
    private const val KEY_ALIAS = "cymatic_library_sync"
    private const val PREFS = "sync_credentials"
    private const val PASSWORD = "password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val presence = mutableMapOf<String, Boolean>()

    private fun secretKey(sourceId: String) = "secret_$sourceId"
    private fun legacyPasswordKey(sourceId: String) = "password_$sourceId"

    @Synchronized
    fun cachedHas(sourceId: String): Boolean? = presence[sourceId]

    @Synchronized
    fun migrateLegacy(context: Context, sourceId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(PASSWORD, null) ?: return
        val key = legacyPasswordKey(sourceId)
        val edit = prefs.edit()
        if (!prefs.contains(key)) edit.putString(key, value)
        check(edit.remove(PASSWORD).commit()) { "Could not migrate library credentials" }
        presence[sourceId] = true
    }

    fun save(context: Context, sourceId: String, credentials: RemoteCredentials) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = secretKey(sourceId)
        if (!credentials.canAuthenticate) {
            prefs.edit().remove(key).remove(legacyPasswordKey(sourceId)).apply()
            synchronized(this) { presence[sourceId] = false }
            RemoteLibraries.invalidate(sourceId)
            return
        }
        val payload = encodeCredentials(credentials)
        val encoded = encrypt(payload)
        check(
            prefs.edit()
                .putString(key, encoded)
                .remove(legacyPasswordKey(sourceId))
                .commit()
        ) {
            "Could not save library credentials"
        }
        synchronized(this) { presence[sourceId] = true }
        RemoteLibraries.invalidate(sourceId)
    }

    fun get(context: Context, sourceId: String): RemoteCredentials? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val secretRaw = prefs.getString(secretKey(sourceId), null)
        if (secretRaw != null) {
            return@runCatching decodeCredentials(decrypt(secretRaw))
        }
        val legacyRaw =
            prefs.getString(legacyPasswordKey(sourceId), null) ?: return@runCatching null
        val password = decrypt(legacyRaw)
        if (password.isEmpty()) null else RemoteCredentials(password = password)
    }.getOrNull()

    fun has(context: Context, sourceId: String): Boolean {
        synchronized(this) { presence[sourceId] }?.let { return it }
        return (get(context, sourceId)?.canAuthenticate == true).also {
            synchronized(this) { presence[sourceId] = it }
        }
    }

    fun updateSession(
        context: Context,
        sourceId: String,
        accessToken: String?,
        userId: String?,
    ) {
        val existing = get(context, sourceId) ?: RemoteCredentials()
        save(
            context,
            sourceId,
            existing.copy(
                accessToken = accessToken,
                userId = userId ?: existing.userId,
            ),
        )
    }

    fun delete(context: Context, sourceId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(secretKey(sourceId))
            .remove(legacyPasswordKey(sourceId))
            .commit()
        synchronized(this) { presence.remove(sourceId) }
        RemoteLibraries.invalidate(sourceId)
    }

    private fun encodeCredentials(credentials: RemoteCredentials): String =
        JSONObject().apply {
            credentials.password?.let { put("password", it) }
            credentials.apiKey?.let { put("apiKey", it) }
            credentials.accessToken?.let { put("accessToken", it) }
            credentials.userId?.let { put("userId", it) }
        }.toString()

    private fun decodeCredentials(raw: String): RemoteCredentials {
        val obj = JSONObject(raw)
        return RemoteCredentials(
            password = obj.optString("password").takeIf { obj.has("password") && it.isNotEmpty() },
            apiKey = obj.optString("apiKey").takeIf { obj.has("apiKey") && it.isNotEmpty() },
            accessToken = obj.optString("accessToken")
                .takeIf { obj.has("accessToken") && it.isNotEmpty() },
            userId = obj.optString("userId").takeIf { obj.has("userId") && it.isNotEmpty() },
        )
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
