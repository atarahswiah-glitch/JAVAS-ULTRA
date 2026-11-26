package com.javasultra.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest
import java.util.UUID

/**
 * Creates a per-Creator-installation signing key in Android Keystore.
 *
 * This proves possession of the corresponding private key when a fresh server
 * challenge is signed. It does NOT by itself register or trust a device.
 * The server must issue single-use challenges, verify the signature, bind the
 * public key to the authenticated Creator, and require explicit enrollment approval.
 */
class DeviceKeyManager(
    context: Context,
    creatorPrincipalId: String
) {
    private val appContext = context.applicationContext
    private val accountKey = sha256(creatorPrincipalId).take(32)
    private val keyAlias = "$KEY_ALIAS_PREFIX$accountKey"
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        require(creatorPrincipalId.isNotBlank()) { "Creator principal is required" }
    }

    @Synchronized
    fun getOrCreateKeyMaterial(): DeviceKeyMaterial {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(keyAlias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }

        val publicKey = keyStore.getCertificate(keyAlias)?.publicKey
            ?: error("Device public key is unavailable")
        val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
            ?: error("Device private key is unavailable")
        val hardwareBacked = runCatching {
            val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            factory.getKeySpec(privateKey, KeyInfo::class.java).isInsideSecureHardware
        }.getOrDefault(false)

        return DeviceKeyMaterial(
            deviceId = getOrCreateDeviceId(),
            publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
            algorithm = "EC_P256_SHA256",
            hardwareBacked = hardwareBacked
        )
    }

    /** Signs a fresh server-issued nonce; the server must enforce one-time use and expiry. */
    @Synchronized
    fun signServerChallenge(challenge: ByteArray): ByteArray {
        require(challenge.size in MIN_CHALLENGE_BYTES..MAX_CHALLENGE_BYTES) {
            "Server challenge must be between 32 and 512 bytes"
        }
        getOrCreateKeyMaterial()
        val privateKey = loadKeyStore().getKey(keyAlias, null) as? PrivateKey
            ?: error("Device signing key is unavailable")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
    }

    /** Local key deletion is a prerequisite for re-enrollment after a device compromise. */
    @Synchronized
    fun deleteRegistrationKey() {
        loadKeyStore().deleteEntry(keyAlias)
        preferences.edit().remove(devicePreferenceKey()).commit()
    }

    private fun getOrCreateDeviceId(): String {
        val key = devicePreferenceKey()
        preferences.getString(key, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        check(preferences.edit().putString(key, created).commit()) {
            "Unable to persist local device identifier"
        }
        return created
    }

    private fun devicePreferenceKey() = "device_id_$accountKey"

    private fun loadKeyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    data class DeviceKeyMaterial(
        val deviceId: String,
        val publicKeyBase64: String,
        val algorithm: String,
        val hardwareBacked: Boolean
    )

    private companion object {
        const val PREFERENCES = "javas_device_identity"
        const val KEY_ALIAS_PREFIX = "javas.device.signing."
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val MIN_CHALLENGE_BYTES = 32
        const val MAX_CHALLENGE_BYTES = 512
    }
}
