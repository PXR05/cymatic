package com.pxr.cymatic.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

    @Synchronized
    fun migrateLegacy(context: Context, sourceId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(PASSWORD, null) ?: return
        val key = "password_$sourceId"
        val edit = prefs.edit()
        if (!prefs.contains(key)) edit.putString(key, value)
        check(edit.remove(PASSWORD).commit()) { "Could not migrate library credentials" }
    }

    fun savePassword(context: Context, password: String, sourceId: String? = null) {
        val key = sourceId?.let { "password_$it" } ?: PASSWORD
        if (password.isEmpty()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value)
                .commit()
        ) {
            "Could not save library credentials"
        }
    }

    fun getPassword(context: Context, sourceId: String? = null): String? = runCatching {
        val key = sourceId?.let { "password_$it" } ?: PASSWORD
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        }
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun hasPassword(context: Context, sourceId: String? = null): Boolean =
        getPassword(context, sourceId) != null

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
