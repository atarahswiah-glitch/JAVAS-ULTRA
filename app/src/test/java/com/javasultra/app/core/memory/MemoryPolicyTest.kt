package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoryPolicyTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val creatorId = "creator-1"
    private fun record(kind: MemoryKind = MemoryKind.PREFERENCE, sensitivity: MemorySensitivity = MemorySensitivity.CREATOR_PRIVATE, lifecycle: MemoryLifecycle = MemoryLifecycle.ACTIVE) =
        MemoryRecord(MemoryId("memory-1"), creatorId, kind, "Use concise explanations",
            MemoryProvenance(MemorySourceType.CREATOR_STATEMENT, null, now.minusSeconds(30), creatorId),
            now.minusSeconds(20), now.minusSeconds(10), 1.0, MemoryVerification.CREATOR_CONFIRMED, sensitivity, lifecycle)
    private fun request(creator: Boolean = true, actor: String = creatorId, action: MemoryAction = MemoryAction.READ, stepUp: Boolean = false) =
        MemoryAccessRequest(actor, creator, action, stepUp)
    private fun denied(result: MemoryAccessDecision) = (result as MemoryAccessDecision.Denied).reason

    @Test fun creatorCanReadOwnPreference() = assertEquals(MemoryAccessDecision.Allowed, MemoryAccessPolicy.evaluate(record(), request()))
    @Test fun nonCreatorCannotReadCreatorPreference() = assertEquals(MemoryAccessDenial.CREATOR_ONLY, denied(MemoryAccessPolicy.evaluate(record(), request(false, "other"))))
    @Test fun creatorCannotReadAnotherOwnersMemory() = assertEquals(MemoryAccessDenial.OWNER_MISMATCH, denied(MemoryAccessPolicy.evaluate(record(), request(actor = "impostor"))))
    @Test fun highlySensitiveReadRequiresStepUp() = assertEquals(MemoryAccessDenial.STEP_UP_REQUIRED, denied(MemoryAccessPolicy.evaluate(record(MemoryKind.SENSITIVE, MemorySensitivity.HIGHLY_SENSITIVE), request())))
    @Test fun highlySensitiveReadAllowsStepUpPreflight() = assertEquals(MemoryAccessDecision.Allowed, MemoryAccessPolicy.evaluate(record(MemoryKind.SENSITIVE, MemorySensitivity.HIGHLY_SENSITIVE), request(stepUp = true)))
    @Test fun proposedMemoryCannotBeReadAsActive() = assertEquals(MemoryAccessDenial.NOT_ACTIVE, denied(MemoryAccessPolicy.evaluate(record(lifecycle = MemoryLifecycle.PROPOSED), request())))
    @Test fun invalidConfidenceIsRejected() = assertEquals(MemoryAccessDenial.INVALID_RECORD, denied(MemoryAccessPolicy.evaluate(record().copy(confidence = 1.5), request())))
    @Test fun sensitiveKindRequiresHighestClassification() = assertEquals(MemoryAccessDenial.INVALID_RECORD, denied(MemoryAccessPolicy.evaluate(record(MemoryKind.SENSITIVE, MemorySensitivity.CREATOR_PRIVATE), request())))
    @Test fun generalPublicKnowledgeCanBeReadByResearchWorker() = assertEquals(MemoryAccessDecision.Allowed, MemoryAccessPolicy.evaluate(record(MemoryKind.SEMANTIC, MemorySensitivity.PUBLIC), request(false, "research-worker")))
}
