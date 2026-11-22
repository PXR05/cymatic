package com.pxr.cymatic.sync.subsonic

import com.pxr.cymatic.sync.http.HttpTransport
import java.security.MessageDigest
import java.security.SecureRandom

internal sealed interface SubsonicAuthMode {
    data class ApiKey(val apiKey: String) : SubsonicAuthMode
    data class SaltedToken(val username: String, val password: String) : SubsonicAuthMode
}

internal object SubsonicAuth {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun queryParams(
        mode: SubsonicAuthMode,
        saltProvider: () -> String = ::generateSalt,
    ): List<Pair<String, String>> =
        when (mode) {
            is SubsonicAuthMode.ApiKey ->
                listOf("apiKey" to mode.apiKey)

            is SubsonicAuthMode.SaltedToken -> {
                val salt = saltProvider()
                val token = md5Hex(mode.password + salt)
                listOf(
                    "u" to mode.username,
                    "s" to salt,
                    "t" to token,
                )
            }
        }

    fun buildQuery(
        params: List<Pair<String, String>>,
    ): String =
        params.joinToString("&") { (key, value) ->
            "${HttpTransport.encodeQuery(key)}=${HttpTransport.encodeQuery(value)}"
        }
}
