package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySyncEnvelopeTest {
    private fun validEnvelope() = MemorySyncEnvelope(
        recordId = "memory-record-opaque-001",
        formatVersion = MemorySyncEnvelope.SUPPORTED_FORMAT_VERSION,
        keyVersion = 1,
        wrappingKeyId = "approved-device-key-001",
        wrappedDataKey = ByteArray(64) { 1 },
        nonce = ByteArray(MemorySyncEnvelope.AES_GCM_NONCE_BYTES) { 2 },
        ciphertext = ByteArray(64) { 3 }
    )

    @Test
    fun acceptsWellFormedOpaqueEnvelope() {
        assertTrue(validEnvelope().isValid())
    }

    @Test
    fun rejectsUnsupportedEnvelopeVersion() {
        val issues = validEnvelope().copy(formatVersion = 99).validate()
        assertEquals(listOf(MemorySyncEnvelopeIssue.UNSUPPORTED_FORMAT_VERSION), issues)
    }

    @Test
    fun rejectsInvalidNonceLength() {
        val issues = validEnvelope().copy(nonce = ByteArray(11)).validate()
        assertTrue(MemorySyncEnvelopeIssue.INVALID_NONCE_SIZE in issues)
    }

    @Test
    fun rejectsEmptyOrOversizedCiphertext() {
        assertTrue(
            MemorySyncEnvelopeIssue.INVALID_CIPHERTEXT_SIZE in
                validEnvelope().copy(ciphertext = ByteArray(0)).validate()
        )
        assertTrue(
            MemorySyncEnvelopeIssue.INVALID_CIPHERTEXT_SIZE in
                validEnvelope().copy(
                    ciphertext = ByteArray(MemorySyncEnvelope.MAX_CIPHERTEXT_BYTES + 1)
                ).validate()
        )
    }

    @Test
    fun rejectsMissingWrappingKeyReferenceAndInvalidKeyVersion() {
        val issues = validEnvelope().copy(wrappingKeyId = " ", keyVersion = 0).validate()
        assertTrue(MemorySyncEnvelopeIssue.INVALID_WRAPPING_KEY_ID in issues)
        assertTrue(MemorySyncEnvelopeIssue.INVALID_KEY_VERSION in issues)
    }

    @Test
    fun rejectsOversizedWrappedDataKey() {
        val issues = validEnvelope().copy(
            wrappedDataKey = ByteArray(MemorySyncEnvelope.MAX_WRAPPED_KEY_BYTES + 1)
        ).validate()
        assertTrue(MemorySyncEnvelopeIssue.INVALID_WRAPPED_KEY_SIZE in issues)
    }
}
