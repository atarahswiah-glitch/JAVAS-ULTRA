package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MemoryRetentionProposalFactoryTest {
    private val now = Instant.parse("2026-11-10T08:00:00Z")

    private fun candidate(
        type: MemoryRetentionType = MemoryRetentionType.GENERAL_FACT,
        verified: Boolean = true,
        referenceAvailable: Boolean = true,
        retain: Boolean = false
    ) = MemoryRetentionCandidate(type, 0.9, 0.8, 0.8, verified, referenceAvailable, retain)

    private fun record(
        kind: MemoryKind = MemoryKind.SEMANTIC,
        sensitivity: MemorySensitivity = MemorySensitivity.PUBLIC,
        sourceType: MemorySourceType = MemorySourceType.RESEARCH_EVIDENCE,
        verification: MemoryVerification = MemoryVerification.CORROBORATED,
        reference: String? = "https://example.org/source"
    ) = MemoryRecord(
        MemoryId("proposal-1"), "creator-1", kind, "A concise verified finding",
        MemoryProvenance(sourceType, reference, now.minusSeconds(60), "Research source"),
        now.minusSeconds(30), now.minusSeconds(30), 0.9, verification, sensitivity
    )

    @Test fun verifiedUsefulFindingBecomesOnlyAProposedSemanticRecord() {
        val result = MemoryRetentionProposalFactory.prepare(candidate(), record())
        assertEquals(MemoryRetentionAction.STORE_KNOWLEDGE, result.retention.action)
        assertEquals(MemoryLifecycle.PROPOSED, result.recordToPropose?.lifecycle)
        assertNotNull(result.recordToPropose)
    }

    @Test fun sourceReferenceDecisionRequiresAReferenceInTheRecord() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(verified = false, referenceAvailable = true),
            record(verification = MemoryVerification.UNVERIFIED)
        )
        assertEquals(MemoryRetentionAction.STORE_SOURCE_REFERENCE, result.retention.action)
        assertNotNull(result.recordToPropose)
    }

    @Test fun sourceReferenceCannotBeInventedWhenMissing() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(verified = false, referenceAvailable = true),
            record(reference = null, verification = MemoryVerification.UNVERIFIED)
        )
        assertEquals(MemoryRetentionPreparationDenial.SOURCE_REFERENCE_REQUIRED, result.denial)
        assertNull(result.recordToPropose)
    }

    @Test fun verifiedKnowledgeRejectsUnverifiedRecordMetadata() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(),
            record(verification = MemoryVerification.UNVERIFIED)
        )
        assertEquals(MemoryRetentionPreparationDenial.SOURCE_NOT_VERIFIED, result.denial)
    }

    @Test fun creatorPreferenceMapsToCreatorPrivatePreferenceMemory() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(MemoryRetentionType.STABLE_PREFERENCE),
            record(
                kind = MemoryKind.PREFERENCE,
                sensitivity = MemorySensitivity.CREATOR_PRIVATE,
                sourceType = MemorySourceType.CREATOR_STATEMENT,
                reference = null
            )
        )
        assertEquals(MemoryRetentionAction.STORE_CONTEXT, result.retention.action)
        assertNotNull(result.recordToPropose)
    }

    @Test fun creatorContextCannotBePreparedAsPublicMemory() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(MemoryRetentionType.STABLE_PREFERENCE),
            record(kind = MemoryKind.PREFERENCE, sourceType = MemorySourceType.CREATOR_STATEMENT)
        )
        assertEquals(MemoryRetentionPreparationDenial.RECORD_SENSITIVITY_MISMATCH, result.denial)
    }

    @Test fun secretsAndTransientInformationNeverProduceRecords() {
        listOf(MemoryRetentionType.CREDENTIAL_OR_SECRET, MemoryRetentionType.TRANSIENT).forEach { type ->
            val result = MemoryRetentionProposalFactory.prepare(candidate(type), record())
            assertEquals(MemoryRetentionPreparationDenial.NO_RECORD_REQUIRED, result.denial)
            assertNull(result.recordToPropose)
        }
    }

    @Test fun sensitiveInformationIsHeldForCreatorReviewWithoutPersistence() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(MemoryRetentionType.SENSITIVE_INFORMATION),
            record(kind = MemoryKind.SENSITIVE, sensitivity = MemorySensitivity.HIGHLY_SENSITIVE)
        )
        assertEquals(MemoryRetentionAction.REQUIRE_CREATOR_REVIEW, result.retention.action)
        assertNull(result.recordToPropose)
    }

    @Test fun invalidOrAlreadyActiveRecordCannotEnterProposalPipeline() {
        val active = record().copy(lifecycle = MemoryLifecycle.ACTIVE)
        val result = MemoryRetentionProposalFactory.prepare(candidate(), active)
        assertEquals(MemoryRetentionPreparationDenial.INVALID_PROPOSED_RECORD, result.denial)
        assertNull(result.recordToPropose)
    }

    @Test fun wrongRecordKindCannotBypassRetentionClassification() {
        val result = MemoryRetentionProposalFactory.prepare(
            candidate(),
            record(kind = MemoryKind.PREFERENCE)
        )
        assertEquals(MemoryRetentionPreparationDenial.RECORD_KIND_MISMATCH, result.denial)
        assertNull(result.recordToPropose)
    }
}
