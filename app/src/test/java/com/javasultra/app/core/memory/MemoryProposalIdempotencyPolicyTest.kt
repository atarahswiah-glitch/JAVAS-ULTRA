package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoryProposalIdempotencyPolicyTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private fun record(content: String = "Verified public fact", owner: String = "creator-1") =
        MemoryRecord(
            id = MemoryId("proposal-1"),
            ownerPrincipalId = owner,
            kind = MemoryKind.SEMANTIC,
            content = content,
            provenance = MemoryProvenance(
                MemorySourceType.RESEARCH_EVIDENCE, "source:a,b", now, "Publisher A,Publisher B"
            ),
            createdAt = now,
            updatedAt = now,
            confidence = 0.9,
            verification = MemoryVerification.CORROBORATED,
            sensitivity = MemorySensitivity.CREATOR_PRIVATE,
            lifecycle = MemoryLifecycle.PROPOSED
        )

    @Test fun exactSameOwnerRetryIsIdempotent() {
        assertEquals(
            MemoryProposalReplayDecision.EXACT_REPLAY,
            MemoryProposalIdempotencyPolicy.evaluate(record(), record(), "creator-1")
        )
    }

    @Test fun sameIdWithChangedContentIsRejected() {
        assertEquals(
            MemoryProposalReplayDecision.CONFLICTING_CONTENT,
            MemoryProposalIdempotencyPolicy.evaluate(record(), record("Changed claim"), "creator-1")
        )
    }

    @Test fun anotherOwnerCannotReplayProposal() {
        assertEquals(
            MemoryProposalReplayDecision.OWNER_MISMATCH,
            MemoryProposalIdempotencyPolicy.evaluate(record(), record(), "creator-2")
        )
    }

    @Test fun blankActorCannotReplayProposal() {
        assertEquals(
            MemoryProposalReplayDecision.OWNER_MISMATCH,
            MemoryProposalIdempotencyPolicy.evaluate(record(), record(), "")
        )
    }
}
