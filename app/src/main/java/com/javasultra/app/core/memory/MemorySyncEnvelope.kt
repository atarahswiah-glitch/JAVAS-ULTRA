package com.javasultra.app.core.memory

/**
 * Opaque, client-encrypted memory payload prepared for a future approved-device sync transport.
 *
 * This is a wire-contract model only. It does not encrypt, wrap keys, authenticate a device,
 * or authorise a server write. The server must derive the owner from the verified JWT and
 * must never trust an owner ID supplied by this client.
 *
 * Plaintext memory and provenance must never be added to this envelope.
 */
data class MemorySyncEnvelope(
    val recordId: String,
    val formatVersion: Int,
    val keyVersion: Int,
    val wrappingKeyId: String,
    val wrappedDataKey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray
) {
    fun validate(): List<MemorySyncEnvelopeIssue> = buildList {
        if (recordId.isBlank() || recordId.length > MAX_IDENTIFIER_LENGTH || recordId.any(Char::isISOControl)) {
            add(MemorySyncEnvelopeIssue.INVALID_RECORD_ID)
        }
        if (formatVersion != SUPPORTED_FORMAT_VERSION) {
            add(MemorySyncEnvelopeIssue.UNSUPPORTED_FORMAT_VERSION)
        }
        if (keyVersion < 1) add(MemorySyncEnvelopeIssue.INVALID_KEY_VERSION)
        if (wrappingKeyId.isBlank() || wrappingKeyId.length > MAX_IDENTIFIER_LENGTH ||
            wrappingKeyId.any(Char::isISOControl)
        ) {
            add(MemorySyncEnvelopeIssue.INVALID_WRAPPING_KEY_ID)
        }
        if (wrappedDataKey.size !in MIN_WRAPPED_KEY_BYTES..MAX_WRAPPED_KEY_BYTES) {
            add(MemorySyncEnvelopeIssue.INVALID_WRAPPED_KEY_SIZE)
        }
        if (nonce.size != AES_GCM_NONCE_BYTES) {
            add(MemorySyncEnvelopeIssue.INVALID_NONCE_SIZE)
        }
        if (ciphertext.size !in AES_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES) {
            add(MemorySyncEnvelopeIssue.INVALID_CIPHERTEXT_SIZE)
        }
    }

    fun isValid(): Boolean = validate().isEmpty()

    companion object {
        const val SUPPORTED_FORMAT_VERSION = 1
        const val AES_GCM_NONCE_BYTES = 12
        const val AES_GCM_TAG_BYTES = 16
        const val MIN_WRAPPED_KEY_BYTES = 32
        const val MAX_WRAPPED_KEY_BYTES = 4_096
        const val MAX_CIPHERTEXT_BYTES = 524_288
        const val MAX_IDENTIFIER_LENGTH = 128
    }
}

enum class MemorySyncEnvelopeIssue {
    INVALID_RECORD_ID,
    UNSUPPORTED_FORMAT_VERSION,
    INVALID_KEY_VERSION,
    INVALID_WRAPPING_KEY_ID,
    INVALID_WRAPPED_KEY_SIZE,
    INVALID_NONCE_SIZE,
    INVALID_CIPHERTEXT_SIZE
}
