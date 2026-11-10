package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class MemoryRecordCodecTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun record() = MemoryRecord(
        id = MemoryId("opaque-test-id"),
        ownerPrincipalId = "creator-test",
        kind = MemoryKind.SEMANTIC,
        content = "The Moon orbits Earth.",
        provenance = MemoryProvenance(
            MemorySourceType.PUBLIC_WEB_SOURCE, "https://example.org/moon",
            now.minusSeconds(120), "Example Research"
        ),
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(30),
        confidence = 0.92,
        verification = MemoryVerification.CORROBORATED,
        sensitivity = MemorySensitivity.PUBLIC,
        lifecycle = MemoryLifecycle.ACTIVE,
        expiresAt = now.plusSeconds(3600),
        tags = setOf("astronomy", "earth")
    )

    @Test fun roundTripsAllMemoryFields() {
        val original = record()
        assertEquals(original, MemoryRecordCodec.decode(MemoryRecordCodec.encode(original)))
    }

    @Test fun rejectsUnsupportedFormatVersion() {
        val bytes = MemoryRecordCodec.encode(record())
        bytes[3] = 2
        assertThrows(IllegalArgumentException::class.java) { MemoryRecordCodec.decode(bytes) }
    }

    @Test fun rejectsTruncatedPayload() {
        val bytes = MemoryRecordCodec.encode(record()).copyOf(8)
        assertThrows(Exception::class.java) { MemoryRecordCodec.decode(bytes) }
    }

    @Test fun rejectsInvalidRecordBeforeEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoryRecordCodec.encode(record().copy(confidence = 2.0))
        }
    }
}
