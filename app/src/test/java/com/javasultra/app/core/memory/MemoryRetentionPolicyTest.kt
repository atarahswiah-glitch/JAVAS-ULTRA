package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRetentionPolicyTest {
    private fun candidate(
        type: MemoryRetentionType = MemoryRetentionType.GENERAL_FACT,
        futureUse: Double = 0.1,
        uniqueness: Double = 0.1,
        researchCost: Double = 0.1,
        verified: Boolean = true,
        source: Boolean = true,
        retain: Boolean = false,
        doNotRetain: Boolean = false
    ) = MemoryRetentionCandidate(type, futureUse, uniqueness, researchCost, verified, source, retain, doNotRetain)

    @Test fun easyToResearchCommonFactIsNotCopiedIntoMemory() {
        assertEquals(
            MemoryRetentionAction.STORE_SOURCE_REFERENCE,
            MemoryRetentionPolicy.evaluate(candidate()).action
        )
    }

    @Test fun usefulVerifiedFindingIsRetainedAsKnowledge() {
        assertEquals(
            MemoryRetentionAction.STORE_KNOWLEDGE,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.RESEARCH_FINDING, futureUse = 0.8)).action
        )
    }

    @Test fun creatorInstructionsAreRetainedAsContext() {
        assertEquals(
            MemoryRetentionAction.STORE_CONTEXT,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.CREATOR_INSTRUCTION)).action
        )
    }

    @Test fun stablePreferencesAreRetainedAsContext() {
        assertEquals(
            MemoryRetentionAction.STORE_CONTEXT,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.STABLE_PREFERENCE)).action
        )
    }

    @Test fun secretsAreNeverRetained() {
        assertEquals(
            MemoryRetentionAction.DO_NOT_RETAIN,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.CREDENTIAL_OR_SECRET, retain = true)).action
        )
    }

    @Test fun transientInformationIsNotRetained() {
        assertEquals(
            MemoryRetentionAction.DO_NOT_RETAIN,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.TRANSIENT)).action
        )
    }

    @Test fun sensitiveInformationRequiresCreatorReview() {
        assertEquals(
            MemoryRetentionAction.REQUIRE_CREATOR_REVIEW,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.SENSITIVE_INFORMATION)).action
        )
    }

    @Test fun explicitNoRetentionInstructionWins() {
        assertEquals(
            MemoryRetentionAction.DO_NOT_RETAIN,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.CREATOR_INSTRUCTION, doNotRetain = true)).action
        )
    }

    @Test fun unverifiedFindingKeepsOnlyItsSourceReference() {
        assertEquals(
            MemoryRetentionAction.STORE_SOURCE_REFERENCE,
            MemoryRetentionPolicy.evaluate(candidate(type = MemoryRetentionType.RESEARCH_FINDING, verified = false, source = true)).action
        )
    }

    @Test fun invalidScoresAreRejected() {
        assertEquals(
            MemoryRetentionReason.INVALID_CANDIDATE,
            MemoryRetentionPolicy.evaluate(candidate(futureUse = 1.5)).reason
        )
    }
}
