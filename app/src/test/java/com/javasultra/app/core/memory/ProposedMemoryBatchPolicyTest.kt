package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ProposedMemoryBatchPolicyTest {
    private val now = Instant.parse("2026-11-10T14:00:00Z")

    private fun record(id: String = "memory-1", owner: String = "creator-1") = MemoryRecord(
        MemoryId(id), owner, MemoryKind.SEMANTIC, "A concise verified finding",
        MemoryProvenance(
            MemorySourceType.RESEARCH_EVIDENCE, "https://example.org/source",
            now.minusSeconds(60), "Research source"
        ),
        now.minusSeconds(30), now.minusSeconds(30), 0.9,
        MemoryVerification.CORROBORATED, MemorySensitivity.PUBLIC
    )

    @Test fun validCreatorOwnedProposedBatchIsAccepted() {
        assertNull(ProposedMemoryBatchPolicy.validate(listOf(record(), record("memory-2")), "creator-1"))
    }

    @Test fun emptyAndOversizedBatchesAreRejected() {
        assertEquals(
            MemoryRepositoryFailure.INVALID_RECORD,
            ProposedMemoryBatchPolicy.validate(emptyList(), "creator-1")
        )
        val oversized = (1..(ProposedMemoryBatchPolicy.MAX_BATCH_SIZE + 1)).map { record("memory-$it") }
        assertEquals(
            MemoryRepositoryFailure.INVALID_RECORD,
            ProposedMemoryBatchPolicy.validate(oversized, "creator-1")
        )
    }

    @Test fun duplicateIdsAreRejectedBeforeAnyWrite() {
        assertEquals(
            MemoryRepositoryFailure.DUPLICATE_ID,
            ProposedMemoryBatchPolicy.validate(listOf(record(), record()), "creator-1")
        )
    }

    @Test fun mixedOwnersAreRejectedBeforeAnyWrite() {
        assertEquals(
            MemoryRepositoryFailure.OWNER_MISMATCH,
            ProposedMemoryBatchPolicy.validate(listOf(record(), record("memory-2", "other")), "creator-1")
        )
    }

    @Test fun activeRecordsCannotEnterProposedBatch() {
        assertEquals(
            MemoryRepositoryFailure.INVALID_RECORD,
            ProposedMemoryBatchPolicy.validate(listOf(record().copy(lifecycle = MemoryLifecycle.ACTIVE)), "creator-1")
        )
    }
}
