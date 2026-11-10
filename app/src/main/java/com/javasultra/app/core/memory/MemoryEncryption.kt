package com.javasultra.app.core.memory

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface MemoryEncryptor {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/** AES-256-GCM backed by a non-exportable Android Keystore key. */
class AndroidKeystoreMemoryEncryptor(
    private val alias: String = "javas.memory.aesgcm.v1"
) : MemoryEncryptor {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "Unexpected GCM IV length" }
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        require(ciphertext.size > 1 + GCM_IV_BYTES + GCM_TAG_BYTES) { "Invalid encrypted memory payload" }
        val ivLength = ciphertext[0].toInt() and 0xff
        require(ivLength == GCM_IV_BYTES && ciphertext.size > 1 + ivLength + GCM_TAG_BYTES) {
            "Invalid encrypted memory IV"
        }
        val iv = ciphertext.copyOfRange(1, 1 + ivLength)
        val encrypted = ciphertext.copyOfRange(1 + ivLength, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val GCM_TAG_BITS = 128
    }
}
