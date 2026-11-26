package com.javasultra.app.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supabase Auth password/refresh client for the Creator account.
 *
 * Access and refresh tokens are encrypted with an Android Keystore AES-GCM key.
 * Passwords are sent only for the sign-in request and are never persisted.
 */
class SupabaseAuthSessionClient(
    context: Context,
    private val projectUrl: String,
    private val publishableKey: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : JavasAccessTokenProvider {

    private val store = EncryptedSupabaseSessionStore(context.applicationContext)

    @Synchronized
    fun signInWithPassword(email: String, password: CharArray): AuthResult {
        requireConfigured()
        require(email.isNotBlank() && password.isNotEmpty()) { "Enter your email and password" }
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", String(password))
            .toString()
        password.fill('\u0000')

        val response = request("token?grant_type=password", body)
        val session = parseSession(response)
        store.save(session)
        return AuthResult.SignedIn
    }

    @Synchronized
    override fun currentAccessToken(): String? {
        val current = store.read() ?: return null
        if (current.expiresAtMillis > clockMillis() + TOKEN_REFRESH_SKEW_MS) {
            return current.accessToken
        }
        if (current.refreshToken.isBlank()) {
            store.clear()
            return null
        }

        return try {
            val response = request(
                "token?grant_type=refresh_token",
                JSONObject().put("refresh_token", current.refreshToken).toString()
            )
            val refreshed = parseSession(response)
            store.save(refreshed)
            refreshed.accessToken
        } catch (_: Exception) {
            // A transient network error must not destroy a still-valid session.
            if (current.expiresAtMillis > clockMillis()) current.accessToken else {
                store.clear()
                null
            }
        }
    }

    @Synchronized
    fun signOut() {
        val token = currentAccessToken()
        if (token != null && isConfigured()) {
            try {
                request("logout", "{}", token)
            } catch (_: Exception) {
                // Local sign-out must still clear credentials if the network is unavailable.
            }
        }
        store.clear()
    }

    fun hasStoredSession(): Boolean = store.read() != null

    /** Authentication check only. Creator authority remains a separate server-side decision. */
    @Synchronized
    fun verifyCurrentUser(): AuthenticatedUser? {
        requireConfigured()
        val token = currentAccessToken() ?: return null
        val response = request("user", "", token, method = "GET")
        val id = response.optString("id").takeIf { it.isNotBlank() } ?: return null
        return AuthenticatedUser(id, response.optString("email").takeIf { it.isNotBlank() })
    }

    data class AuthenticatedUser(val id: String, val email: String?)

    private fun request(
        path: String,
        body: String,
        bearer: String? = null,
        method: String = "POST"
    ): JSONObject {
        val connection = (URL("${projectUrl.trimEnd('/')}/auth/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = method == "POST"
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            if (method == "POST") {
                connection.outputStream.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { it.write(body) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Supabase authentication failed (HTTP $status)")
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSession(json: JSONObject): StoredSupabaseSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 0L)
        val expiresAtSeconds = json.optLong("expires_at", 0L)
        require(access.isNotBlank() && refresh.isNotBlank()) { "Supabase returned an incomplete session" }
        val expiresAt = if (expiresAtSeconds > 0L) {
            TimeUnit.SECONDS.toMillis(expiresAtSeconds)
        } else {
            clockMillis() + TimeUnit.SECONDS.toMillis(expiresIn)
        }
        require(expiresAt > clockMillis()) { "Supabase returned an expired session" }
        return StoredSupabaseSession(access, refresh, expiresAt)
    }

    private fun isConfigured(): Boolean =
        projectUrl.startsWith("https://") && publishableKey.isNotBlank()

    private fun requireConfigured() {
        require(isConfigured()) {
            "Set JAVAS_SUPABASE_URL and JAVAS_SUPABASE_PUBLISHABLE_KEY in the local build configuration"
        }
    }

    sealed interface AuthResult {
        data object SignedIn : AuthResult
    }

    private data class StoredSupabaseSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long
    ) {
        fun toJson() = JSONObject()
            .put("access_token", accessToken)
            .put("refresh_token", refreshToken)
            .put("expires_at_ms", expiresAtMillis)
            .toString()

        companion object {
            fun fromJson(raw: String): StoredSupabaseSession {
                val json = JSONObject(raw)
                return StoredSupabaseSession(
                    json.getString("access_token"),
                    json.getString("refresh_token"),
                    json.getLong("expires_at_ms")
                )
            }
        }
    }

    private class EncryptedSupabaseSessionStore(context: Context) {
        private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        @Synchronized
        fun save(session: StoredSupabaseSession) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(session.toJson().toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + encrypted
            check(preferences.edit().putString(SESSION_KEY, Base64.encodeToString(combined, Base64.NO_WRAP)).commit()) {
                "Unable to persist encrypted authentication session"
            }
        }

        @Synchronized
        fun read(): StoredSupabaseSession? {
            val encoded = preferences.getString(SESSION_KEY, null) ?: return null
            return try {
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                require(combined.size > GCM_IV_BYTES)
                val iv = combined.copyOfRange(0, GCM_IV_BYTES)
                val encrypted = combined.copyOfRange(GCM_IV_BYTES, combined.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                StoredSupabaseSession.fromJson(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            } catch (_: Exception) {
                clear()
                null
            }
        }

        @Synchronized
        fun clear() {
            preferences.edit().remove(SESSION_KEY).commit()
        }

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }

        companion object {
            private const val PREFERENCES = "javas_secure_auth"
            private const val SESSION_KEY = "encrypted_supabase_session"
            private const val KEY_ALIAS = "javas_supabase_session_aes_gcm"
            private const val ANDROID_KEY_STORE = "AndroidKeyStore"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val GCM_IV_BYTES = 12
            private const val GCM_TAG_BITS = 128
        }
    }

    companion object {
        private val TOKEN_REFRESH_SKEW_MS = TimeUnit.MINUTES.toMillis(2)
    }
}
