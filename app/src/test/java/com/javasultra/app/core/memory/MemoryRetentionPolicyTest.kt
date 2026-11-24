package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        doNotRetain: Boolean = false,
        recurrence: Double = 0.0,
        freshness: Double = 0.0,
        changeLikelihood: Double = 0.0,
        sourceConfidence: Double = if (verified) 1.0 else 0.0,
        verificationStatus: MemoryVerification = if (verified) MemoryVerification.CORROBORATED else MemoryVerification.UNVERIFIED,
        contradictionStatus: MemoryContradictionStatus = MemoryContradictionStatus.NONE,
        provenanceComplete: Boolean = source,
        independentSources: Int = if (verified) 2 else 0,
        evidenceValid: Boolean = true,
        evidenceAgeDays: Long? = 0,
        maximumEvidenceAgeDays: Long = 365
    ) = MemoryRetentionCandidate(
        type = type,
        futureUseLikelihood = futureUse,
        uniqueness = uniqueness,
        reResearchCost = researchCost,
        sourceVerified = verified,
        sourceReferenceAvailable = source,
        explicitlyRequestedToRetain = retain,
        explicitlyRequestedNotToRetain = doNotRetain,
        recurrence = recurrence,
        freshness = freshness,
        changeLikelihood = changeLikelihood,
        sourceConfidence = sourceConfidence,
        verificationStatus = verificationStatus,
        contradictionStatus = contradictionStatus,
        provenanceComplete = provenanceComplete,
        independentSourceCount = independentSources,
        evidenceValid = evidenceValid,
        evidenceAgeDays = evidenceAgeDays,
        maximumEvidenceAgeDays = maximumEvidenceAgeDays
    )

    @Test fun easyToResearchCommonFactKeepsOnlyItsSourceReference() {
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
        val result = MemoryRetentionPolicy.evaluate(
            candidate(type = MemoryRetentionType.RESEARCH_FINDING, verified = false, source = true)
        )
        assertEquals(MemoryRetentionAction.STORE_SOURCE_REFERENCE, result.action)
        assertEquals(MemoryEvidenceGateFailure.NOT_VERIFIED, result.evidenceGateFailure)
    }

    @Test fun invalidScoresAreRejected() {
        assertEquals(
            MemoryRetentionReason.INVALID_CANDIDATE,
            MemoryRetentionPolicy.evaluate(candidate(futureUse = 1.5)).reason
        )
    }

    @Test fun scoreUsesAllSevenNumericSignals() {
        val value = candidate(
            recurrence = 1.0,
            freshness = 1.0,
            changeLikelihood = 0.0,
            uniqueness = 1.0,
            futureUse = 1.0,
            researchCost = 1.0,
            sourceConfidence = 1.0
        )
        assertEquals(1.0, MemoryRetentionPolicy.calculateScore(value), 0.0001)
    }

    @Test fun highChangeLikelihoodReducesRetentionScore() {
        val stable = MemoryRetentionPolicy.calculateScore(candidate(changeLikelihood = 0.0))
        val volatile = MemoryRetentionPolicy.calculateScore(candidate(changeLikelihood = 1.0))
        assertEquals(0.1, stable - volatile, 0.0001)
    }

    @Test fun unresolvedContradictionsVetoKnowledgeRegardlessOfScore() {
        val result = MemoryRetentionPolicy.evaluate(
            candidate(
                futureUse = 1.0,
                uniqueness = 1.0,
                researchCost = 1.0,
                recurrence = 1.0,
                freshness = 1.0,
                sourceConfidence = 1.0,
                contradictionStatus = MemoryContradictionStatus.UNRESOLVED
            )
        )
        assertEquals(MemoryRetentionAction.REQUIRE_CREATOR_REVIEW, result.action)
        assertEquals(MemoryEvidenceGateFailure.CONTRADICTION_UNRESOLVED, result.evidenceGateFailure)
        assertTrue(result.retentionScore > 0.9)
    }

    @Test fun staleEvidenceMustBeResearchedAgain() {
        val result = MemoryRetentionPolicy.evaluate(candidate(evidenceAgeDays = 500))
        assertEquals(MemoryRetentionAction.RESEARCH_AGAIN, result.action)
        assertEquals(MemoryEvidenceGateFailure.EVIDENCE_STALE, result.evidenceGateFailure)
    }

    @Test fun unknownEvidenceAgeMustNotBecomeKnowledge() {
        val result = MemoryRetentionPolicy.evaluate(candidate(evidenceAgeDays = null))
        assertEquals(MemoryRetentionAction.STORE_SOURCE_REFERENCE, result.action)
        assertEquals(MemoryEvidenceGateFailure.EVIDENCE_AGE_UNKNOWN, result.evidenceGateFailure)
    }

    @Test fun incompleteProvenanceBlocksKnowledge() {
        val result = MemoryRetentionPolicy.evaluate(candidate(provenanceComplete = false))
        assertEquals(MemoryRetentionAction.RESEARCH_AGAIN, result.action)
        assertEquals(MemoryEvidenceGateFailure.PROVENANCE_INCOMPLETE, result.evidenceGateFailure)
    }

    @Test fun oneIndependentSourceAllowsReferenceButNotKnowledge() {
        val result = MemoryRetentionPolicy.evaluate(
            candidate(independentSources = 1, verificationStatus = MemoryVerification.SINGLE_SOURCE)
        )
        assertEquals(MemoryRetentionAction.STORE_SOURCE_REFERENCE, result.action)
        assertEquals(MemoryEvidenceGateFailure.INSUFFICIENT_INDEPENDENT_SOURCES, result.evidenceGateFailure)
    }

    @Test fun lowSourceConfidenceAllowsReferenceButNotKnowledge() {
        val result = MemoryRetentionPolicy.evaluate(candidate(sourceConfidence = 0.4))
        assertEquals(MemoryRetentionAction.STORE_SOURCE_REFERENCE, result.action)
        assertEquals(MemoryEvidenceGateFailure.LOW_SOURCE_CONFIDENCE, result.evidenceGateFailure)
    }

    @Test fun invalidEvidenceRequiresCreatorReview() {
        val result = MemoryRetentionPolicy.evaluate(candidate(evidenceValid = false))
        assertEquals(MemoryRetentionAction.REQUIRE_CREATOR_REVIEW, result.action)
        assertEquals(MemoryEvidenceGateFailure.INVALID_EVIDENCE, result.evidenceGateFailure)
    }
}
