package com.javasultra.app.core.security

import android.content.Context
import android.util.Base64
import com.javasultra.app.core.memory.AndroidKeystoreMemoryEncryptor
import com.javasultra.app.core.memory.MemoryEncryptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant

/** Opaque provider credentials kept only in the encrypted local session vault. */
data class CreatorSessionCredential(
    val principalId: String,
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String?,
    val issuedAt: Instant,
    val authenticatedAt: Instant,
    val expiresAt: Instant,
    val strength: AuthenticationStrength,
    val verifiedFactorCount: Int
) {
    fun validate(): Boolean {
        if (principalId.isBlank() || principalId.length > 256 ||
            deviceId.isBlank() || deviceId.length > 256 ||
            accessToken.isBlank() || accessToken.length > MAX_TOKEN_CHARS ||
            refreshToken?.let { it.isBlank() || it.length > MAX_TOKEN_CHARS } == true ||
            !expiresAt.isAfter(issuedAt) ||
            authenticatedAt.isBefore(issuedAt) ||
            authenticatedAt.isAfter(expiresAt) ||
            verifiedFactorCount !in 1..3
        ) return false
        if (strength == AuthenticationStrength.NONE ||
            strength == AuthenticationStrength.LOCAL_BIOMETRIC_UNLOCK
        ) return false
        if (strength == AuthenticationStrength.PASSWORD_OR_PIN && verifiedFactorCount != 1) return false
        if (strength == AuthenticationStrength.MULTI_FACTOR && verifiedFactorCount < 2) return false
        return true
    }

    private companion object {
        const val MAX_TOKEN_CHARS = 16_384
    }
}

/** Versioned bounded codec; the encoded bytes must be encrypted before persistence. */
object CreatorSessionCredentialCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_ENCODED_BYTES = 40_000

    fun encode(value: CreatorSessionCredential): ByteArray {
        require(value.validate()) { "Invalid Creator session credential" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeUTF(value.principalId)
            out.writeUTF(value.deviceId)
            out.writeUTF(value.accessToken)
            out.writeNullable(value.refreshToken)
            out.writeLong(value.issuedAt.toEpochMilli())
            out.writeLong(value.authenticatedAt.toEpochMilli())
            out.writeLong(value.expiresAt.toEpochMilli())
            out.writeUTF(value.strength.name)
            out.writeInt(value.verifiedFactorCount)
        }
        return bytes.toByteArray().also {
            require(it.size <= MAX_ENCODED_BYTES) { "Creator session exceeds codec limit" }
        }
    }

    fun decode(bytes: ByteArray): CreatorSessionCredential {
        require(bytes.size in 1..MAX_ENCODED_BYTES) { "Invalid Creator session payload size" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported Creator session format" }
            val value = CreatorSessionCredential(
                principalId = input.readUTF(),
                deviceId = input.readUTF(),
                accessToken = input.readUTF(),
                refreshToken = input.readNullable(),
                issuedAt = Instant.ofEpochMilli(input.readLong()),
                authenticatedAt = Instant.ofEpochMilli(input.readLong()),
                expiresAt = Instant.ofEpochMilli(input.readLong()),
                strength = AuthenticationStrength.valueOf(input.readUTF()),
                verifiedFactorCount = input.readInt()
            )
            require(input.available() == 0 && value.validate()) { "Invalid decoded Creator session" }
            return value
        }
    }

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null
}

enum class CreatorSessionVaultFailure {
    INVALID_CREDENTIAL,
    ENCRYPTION_FAILED,
    DECRYPTION_FAILED,
    STORAGE_FAILED
}

sealed interface CreatorSessionVaultResult<out T> {
    data class Success<T>(val value: T) : CreatorSessionVaultResult<T>
    data class Failure(val reason: CreatorSessionVaultFailure) : CreatorSessionVaultResult<Nothing>
}

interface CreatorSessionVault {
    fun read(): CreatorSessionVaultResult<CreatorSessionCredential?>
    fun write(credential: CreatorSessionCredential): CreatorSessionVaultResult<Unit>
    fun clear(): CreatorSessionVaultResult<Unit>
}

/**
 * Stores only AES-GCM ciphertext in private SharedPreferences. The key is non-exportable and
 * Android-Keystore-backed. Backup is disabled at the application manifest level.
 */
class EncryptedCreatorSessionVault(
    context: Context,
    private val encryptor: MemoryEncryptor = AndroidKeystoreMemoryEncryptor(KEY_ALIAS)
) : CreatorSessionVault {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): CreatorSessionVaultResult<CreatorSessionCredential?> {
        val encoded = try {
            preferences.getString(SESSION_KEY, null)
        } catch (_: Exception) {
            return CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.STORAGE_FAILED)
        } ?: return CreatorSessionVaultResult.Success(null)

        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            val plaintext = encryptor.decrypt(encrypted, ASSOCIATED_DATA)
            CreatorSessionVaultResult.Success(CreatorSessionCredentialCodec.decode(plaintext))
        } catch (_: Exception) {
            CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.DECRYPTION_FAILED)
        }
    }

    override fun write(credential: CreatorSessionCredential): CreatorSessionVaultResult<Unit> {
        if (!credential.validate()) {
            return CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.INVALID_CREDENTIAL)
        }
        val encoded = try {
            Base64.encodeToString(
                encryptor.encrypt(CreatorSessionCredentialCodec.encode(credential), ASSOCIATED_DATA),
                Base64.NO_WRAP
            )
        } catch (_: Exception) {
            return CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.ENCRYPTION_FAILED)
        }
        return try {
            if (preferences.edit().putString(SESSION_KEY, encoded).commit()) {
                CreatorSessionVaultResult.Success(Unit)
            } else {
                CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.STORAGE_FAILED)
            }
        } catch (_: Exception) {
            CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.STORAGE_FAILED)
        }
    }

    override fun clear(): CreatorSessionVaultResult<Unit> = try {
        if (preferences.edit().remove(SESSION_KEY).commit()) {
            CreatorSessionVaultResult.Success(Unit)
        } else {
            CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.STORAGE_FAILED)
        }
    } catch (_: Exception) {
        CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.STORAGE_FAILED)
    }

    private companion object {
        const val PREFERENCES_NAME = "javas_creator_session_v1"
        const val SESSION_KEY = "encrypted_session"
        const val KEY_ALIAS = "javas.creator.session.aesgcm.v1"
        val ASSOCIATED_DATA = "javas.creator.session:v1".toByteArray(Charsets.UTF_8)
    }
}
