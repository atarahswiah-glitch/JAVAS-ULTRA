package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoryPolicyTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val creatorId = "creator-1"

    private fun record(
        kind: MemoryKind = MemoryKind.PREFERENCE,
        sensitivity: MemorySensitivity = MemorySensitivity.CREATOR_PRIVATE,
        lifecycle: MemoryLifecycle = MemoryLifecycle.ACTIVE
    ) = MemoryRecord(
        id = MemoryId("memory-1"),
        ownerPrincipalId = creatorId,
        kind = kind,
        content = "Use concise explanations",
        provenance = MemoryProvenance(
            MemorySourceType.CREATOR_STATEMENT, null, now.minusSeconds(30), creatorId
        ),
        createdAt = now.minusSeconds(20),
        updatedAt = now.minusSeconds(10),
        confidence = 1.0,
        verification = MemoryVerification.CREATOR_CONFIRMED,
        sensitivity = sensitivity,
        lifecycle = lifecycle
    )

    private fun request(
        creator: Boolean = true,
        actor: String = creatorId,
        action: MemoryAction = MemoryAction.READ,
        stepUp: Boolean = false
    ) = MemoryAccessRequest(actor, creator, action, stepUp)

    @Test fun creatorCanReadOwnPreference() {
        assertEquals(MemoryAccessDecision.Allowed,
            MemoryAccessPolicy.evaluate(record(), request()))
    }

    @Test fun nonCreatorCannotReadCreatorPreference() {
        assertEquals(MemoryAccessDenial.CREATOR_ONLY,
            (MemoryAccessPolicy.evaluate(record(), request(creator = false, actor = "other")) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun creatorCannotReadAnotherOwnersMemory() {
        assertEquals(MemoryAccessDenial.OWNER_MISMATCH,
            (MemoryAccessPolicy.evaluate(record(), request(actor = "impostor")) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun highlySensitiveReadRequiresStepUp() {
        assertEquals(MemoryAccessDenial.STEP_UP_REQUIRED,
            (MemoryAccessPolicy.evaluate(
                record(MemoryKind.SENSITIVE, MemorySensitivity.HIGHLY_SENSITIVE),
                request()
            ) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun highlySensitiveReadAllowsFreshStepUpPreflight() {
        assertEquals(MemoryAccessDecision.Allowed,
            MemoryAccessPolicy.evaluate(
                record(MemoryKind.SENSITIVE, MemorySensitivity.HIGHLY_SENSITIVE),
                request(stepUp = true)
            ))
    }

    @Test fun proposedMemoryCannotBeReadAsActive() {
        assertEquals(MemoryAccessDenial.NOT_ACTIVE,
            (MemoryAccessPolicy.evaluate(
                record(lifecycle = MemoryLifecycle.PROPOSED), request()
            ) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun invalidConfidenceIsRejected() {
        val invalid = record().copy(confidence = 1.5)
        assertEquals(MemoryAccessDenial.INVALID_RECORD,
            (MemoryAccessPolicy.evaluate(invalid, request()) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun sensitiveKindRequiresHighestClassification() {
        val invalid = record(MemoryKind.SENSITIVE, MemorySensitivity.CREATOR_PRIVATE)
        assertEquals(MemoryAccessDenial.INVALID_RECORD,
            (MemoryAccessPolicy.evaluate(invalid, request()) as MemoryAccessDecision.Denied).reason)
    }

    @Test fun generalPublicKnowledgeCanBeReadWithoutCreatorOwnership() {
        val publicFact = record(
            kind = MemoryKind.SEMANTIC,
            sensitivity = MemorySensitivity.PUBLIC
        )
        assertEquals(MemoryAccessDecision.Allowed,
            MemoryAccessPolicy.evaluate(publicFact, request(creator = false, actor = "research-worker")))
    }
}
