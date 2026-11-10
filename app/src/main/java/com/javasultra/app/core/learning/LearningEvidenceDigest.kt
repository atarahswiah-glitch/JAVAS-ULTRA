package com.javasultra.app.core.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * Canonical digest of the exact claims and source metadata presented for Creator review.
 * Sorting IDs and using length-prefixed UTF-8 fields makes the digest independent of collection
 * iteration order while binding every material claim/source field to the review payload.
 */
object LearningEvidenceDigest {
    private const val FORMAT_VERSION = 1
    private const val MAX_FIELD_BYTES = 16_384
    private const val MAX_CLAIMS = 500
    private const val MAX_SOURCES = 2_000
    private const val MAX_SOURCE_REFERENCES = 500
    private const val MAX_DIGEST_INPUT_BYTES = 4 * 1024 * 1024

    fun computeSha256(claims: List<EvidenceClaim>, sources: List<ResearchSource>): String {
        require(claims.size in 1..MAX_CLAIMS) { "Invalid claim count" }
        require(sources.size in 0..MAX_SOURCES) { "Invalid source count" }
        val bytes = BoundedByteArrayOutputStream(MAX_DIGEST_INPUT_BYTES)
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(claims.size)
            claims.sortedBy { it.id }.forEach { claim ->
                out.writeString(claim.id)
                out.writeString(claim.statement)
                out.writeStringSet(claim.sourceIds)
                out.writeStringSet(claim.supportingSourceIds)
                out.writeStringSet(claim.contradictingSourceIds)
                out.writeInstant(claim.createdAt)
                out.writeNullableInstant(claim.reviewedAt)
                out.writeString(claim.status.name)
                out.writeLong(java.lang.Double.doubleToLongBits(claim.confidence))
            }
            out.writeInt(sources.size)
            sources.sortedBy { it.id }.forEach { source ->
                out.writeString(source.id)
                out.writeString(source.kind.name)
                out.writeString(source.title)
                out.writeNullableString(source.publisher)
                out.writeNullableString(source.uri)
                out.writeNullableInstant(source.publishedAt)
                out.writeInstant(source.retrievedAt)
                out.writeLong(java.lang.Double.doubleToLongBits(source.trustScore))
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private class BoundedByteArrayOutputStream(private val maxBytes: Int) : ByteArrayOutputStream() {
        override fun write(b: Int) {
            require(count < maxBytes) { "Evidence digest input too large" }
            super.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            require(len >= 0 && len <= maxBytes - count) { "Evidence digest input too large" }
            super.write(b, off, len)
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_BYTES) { "Evidence field too large" }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataOutputStream.writeStringSet(values: Set<String>) {
        require(values.size <= MAX_SOURCE_REFERENCES && values.none(String::isBlank))
        writeInt(values.size)
        values.sorted().forEach { writeString(it) }
    }

    private fun DataOutputStream.writeInstant(value: Instant) {
        writeLong(value.epochSecond)
        writeInt(value.nano)
    }

    private fun DataOutputStream.writeNullableInstant(value: Instant?) {
        writeBoolean(value != null)
        if (value != null) writeInstant(value)
    }
}
