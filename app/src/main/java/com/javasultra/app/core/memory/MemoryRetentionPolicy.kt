package com.javasultra.app.core.memory

enum class MemoryRetentionType {
    GENERAL_FACT,
    RESEARCH_FINDING,
    CREATOR_INSTRUCTION,
    STABLE_PREFERENCE,
    PROJECT_DECISION,
    SENSITIVE_INFORMATION,
    CREDENTIAL_OR_SECRET,
    TRANSIENT
}

enum class MemoryRetentionAction {
    DO_NOT_RETAIN,
    RESEARCH_AGAIN,
    STORE_SOURCE_REFERENCE,
    STORE_KNOWLEDGE,
    STORE_CONTEXT,
    REQUIRE_CREATOR_REVIEW
}

enum class MemoryContradictionStatus { NONE, RESOLVED, UNRESOLVED }

enum class MemoryEvidenceGateFailure {
    INVALID_EVIDENCE,
    CONTRADICTION_UNRESOLVED,
    EVIDENCE_AGE_UNKNOWN,
    EVIDENCE_STALE,
    PROVENANCE_INCOMPLETE,
    NOT_VERIFIED,
    INSUFFICIENT_INDEPENDENT_SOURCES,
    LOW_SOURCE_CONFIDENCE
}

enum class MemoryRetentionReason {
    EXPLICITLY_NOT_REQUESTED,
    SECRET_MUST_NOT_BE_STORED,
    TRANSIENT_INFORMATION,
    SENSITIVE_INFORMATION_REQUIRES_REVIEW,
    CREATOR_CONTEXT_IS_DURABLE,
    EXPLICIT_RETENTION_REQUEST,
    VERIFIED_AND_USEFUL,
    SOURCE_REFERENCE_IS_SUFFICIENT,
    EASILY_RESEARCHED_AGAIN,
    UNVERIFIED_WITHOUT_SOURCE,
    EVIDENCE_REQUIRES_REVIEW,
    EVIDENCE_AGE_UNKNOWN,
    EVIDENCE_IS_STALE,
    PROVENANCE_IS_INCOMPLETE,
    INSUFFICIENT_INDEPENDENT_SOURCES,
    SOURCE_CONFIDENCE_TOO_LOW,
    INVALID_CANDIDATE
}

/**
 * The seven numeric signals are scored independently from evidence gates:
 * recurrence, freshness, change likelihood, uniqueness, future usefulness,
 * re-research cost and source confidence.
 *
 * Evidence gates (verification, contradiction, provenance, independent sources,
 * validity and age) are checked separately and can veto knowledge retention regardless
 * of the numeric score.
 */
data class MemoryRetentionCandidate(
    val type: MemoryRetentionType,
    val futureUseLikelihood: Double,
    val uniqueness: Double,
    val reResearchCost: Double,
    val sourceVerified: Boolean,
    val sourceReferenceAvailable: Boolean,
    val explicitlyRequestedToRetain: Boolean = false,
    val explicitlyRequestedNotToRetain: Boolean = false,
    val recurrence: Double = 0.0,
    val freshness: Double = 0.0,
    val changeLikelihood: Double = 0.0,
    val sourceConfidence: Double = if (sourceVerified) 1.0 else 0.0,
    val verificationStatus: MemoryVerification = if (sourceVerified) {
        MemoryVerification.CORROBORATED
    } else {
        MemoryVerification.UNVERIFIED
    },
    val contradictionStatus: MemoryContradictionStatus = MemoryContradictionStatus.NONE,
    val provenanceComplete: Boolean = sourceReferenceAvailable,
    val independentSourceCount: Int = if (sourceVerified) 2 else 0,
    val evidenceValid: Boolean = true,
    val evidenceAgeDays: Long? = null,
    val maximumEvidenceAgeDays: Long = 365
)

data class MemoryRetentionDecision(
    val action: MemoryRetentionAction,
    val reason: MemoryRetentionReason,
    val retentionScore: Double = 0.0,
    val evidenceGateFailure: MemoryEvidenceGateFailure? = null
)

/**
 * Chooses whether information should become durable memory.
 *
 * This is a local decision policy only. It does not persist, encrypt, authorize
 * or delete records; repository and trusted identity boundaries must enforce those operations.
 */
object MemoryRetentionPolicy {
    private const val KNOWLEDGE_RETENTION_THRESHOLD = 0.40
    private const val MIN_SOURCE_CONFIDENCE = 0.50

    fun evaluate(candidate: MemoryRetentionCandidate): MemoryRetentionDecision {
        if (!validNumericSignals(candidate)) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.INVALID_CANDIDATE)
        }

        val score = calculateScore(candidate)

        if (candidate.explicitlyRequestedNotToRetain) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.EXPLICITLY_NOT_REQUESTED, score)
        }
        if (candidate.type == MemoryRetentionType.CREDENTIAL_OR_SECRET) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.SECRET_MUST_NOT_BE_STORED, score)
        }
        if (candidate.type == MemoryRetentionType.TRANSIENT) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.TRANSIENT_INFORMATION, score)
        }
        if (candidate.type == MemoryRetentionType.SENSITIVE_INFORMATION) {
            return decision(
                MemoryRetentionAction.REQUIRE_CREATOR_REVIEW,
                MemoryRetentionReason.SENSITIVE_INFORMATION_REQUIRES_REVIEW,
                score
            )
        }

        if (candidate.type in setOf(
                MemoryRetentionType.CREATOR_INSTRUCTION,
                MemoryRetentionType.STABLE_PREFERENCE,
                MemoryRetentionType.PROJECT_DECISION
            )
        ) {
            return decision(MemoryRetentionAction.STORE_CONTEXT, MemoryRetentionReason.CREATOR_CONTEXT_IS_DURABLE, score)
        }

        val gateFailure = evidenceGate(candidate)
        if (gateFailure != null) return evidenceGateDecision(candidate, score, gateFailure)

        if (candidate.explicitlyRequestedToRetain) {
            return decision(
                MemoryRetentionAction.STORE_KNOWLEDGE,
                MemoryRetentionReason.EXPLICIT_RETENTION_REQUEST,
                score
            )
        }

        if (score >= KNOWLEDGE_RETENTION_THRESHOLD) {
            return decision(
                MemoryRetentionAction.STORE_KNOWLEDGE,
                MemoryRetentionReason.VERIFIED_AND_USEFUL,
                score
            )
        }
        if (candidate.sourceReferenceAvailable && candidate.provenanceComplete) {
            return decision(
                MemoryRetentionAction.STORE_SOURCE_REFERENCE,
                MemoryRetentionReason.SOURCE_REFERENCE_IS_SUFFICIENT,
                score
            )
        }
        return decision(
            MemoryRetentionAction.RESEARCH_AGAIN,
            MemoryRetentionReason.EASILY_RESEARCHED_AGAIN,
            score
        )
    }

    /** Weighted score from the seven requested signals; evidence gates are not part of it. */
    fun calculateScore(candidate: MemoryRetentionCandidate): Double {
        if (!validNumericSignals(candidate)) return 0.0
        val score =
            candidate.recurrence * 0.15 +
            candidate.freshness * 0.15 +
            (1.0 - candidate.changeLikelihood) * 0.10 +
            candidate.uniqueness * 0.10 +
            candidate.futureUseLikelihood * 0.20 +
            candidate.reResearchCost * 0.15 +
            candidate.sourceConfidence * 0.15
        return score.coerceIn(0.0, 1.0)
    }

    private fun validNumericSignals(candidate: MemoryRetentionCandidate): Boolean =
        candidate.futureUseLikelihood in 0.0..1.0 &&
            candidate.uniqueness in 0.0..1.0 &&
            candidate.reResearchCost in 0.0..1.0 &&
            candidate.recurrence in 0.0..1.0 &&
            candidate.freshness in 0.0..1.0 &&
            candidate.changeLikelihood in 0.0..1.0 &&
            candidate.sourceConfidence in 0.0..1.0 &&
            candidate.independentSourceCount in 0..100 &&
            candidate.maximumEvidenceAgeDays in 1..36_500 &&
            (candidate.evidenceAgeDays == null || candidate.evidenceAgeDays >= 0)

    private fun evidenceGate(candidate: MemoryRetentionCandidate): MemoryEvidenceGateFailure? {
        if (!candidate.evidenceValid) return MemoryEvidenceGateFailure.INVALID_EVIDENCE
        if (candidate.contradictionStatus == MemoryContradictionStatus.UNRESOLVED ||
            candidate.verificationStatus == MemoryVerification.DISPUTED
        ) return MemoryEvidenceGateFailure.CONTRADICTION_UNRESOLVED
        val age = candidate.evidenceAgeDays
            ?: return MemoryEvidenceGateFailure.EVIDENCE_AGE_UNKNOWN
        if (age > candidate.maximumEvidenceAgeDays) return MemoryEvidenceGateFailure.EVIDENCE_STALE
        if (!candidate.provenanceComplete || !candidate.sourceReferenceAvailable) {
            return MemoryEvidenceGateFailure.PROVENANCE_INCOMPLETE
        }
        if (!candidate.sourceVerified || candidate.verificationStatus == MemoryVerification.UNVERIFIED) {
            return MemoryEvidenceGateFailure.NOT_VERIFIED
        }
        if (candidate.verificationStatus == MemoryVerification.SINGLE_SOURCE ||
            candidate.independentSourceCount < 2
        ) return MemoryEvidenceGateFailure.INSUFFICIENT_INDEPENDENT_SOURCES
        if (candidate.sourceConfidence < MIN_SOURCE_CONFIDENCE) {
            return MemoryEvidenceGateFailure.LOW_SOURCE_CONFIDENCE
        }
        return null
    }

    private fun evidenceGateDecision(
        candidate: MemoryRetentionCandidate,
        score: Double,
        failure: MemoryEvidenceGateFailure
    ): MemoryRetentionDecision = when (failure) {
        MemoryEvidenceGateFailure.INVALID_EVIDENCE,
        MemoryEvidenceGateFailure.CONTRADICTION_UNRESOLVED ->
            decision(
                MemoryRetentionAction.REQUIRE_CREATOR_REVIEW,
                MemoryRetentionReason.EVIDENCE_REQUIRES_REVIEW,
                score,
                failure
            )
        MemoryEvidenceGateFailure.EVIDENCE_AGE_UNKNOWN ->
            referenceOrResearch(candidate, score, MemoryRetentionReason.EVIDENCE_AGE_UNKNOWN, failure)
        MemoryEvidenceGateFailure.EVIDENCE_STALE ->
            decision(MemoryRetentionAction.RESEARCH_AGAIN, MemoryRetentionReason.EVIDENCE_IS_STALE, score, failure)
        MemoryEvidenceGateFailure.PROVENANCE_INCOMPLETE ->
            decision(MemoryRetentionAction.RESEARCH_AGAIN, MemoryRetentionReason.PROVENANCE_IS_INCOMPLETE, score, failure)
        MemoryEvidenceGateFailure.NOT_VERIFIED ->
            referenceOrResearch(candidate, score, MemoryRetentionReason.UNVERIFIED_WITHOUT_SOURCE, failure)
        MemoryEvidenceGateFailure.INSUFFICIENT_INDEPENDENT_SOURCES ->
            referenceOrResearch(candidate, score, MemoryRetentionReason.INSUFFICIENT_INDEPENDENT_SOURCES, failure)
        MemoryEvidenceGateFailure.LOW_SOURCE_CONFIDENCE ->
            referenceOrResearch(candidate, score, MemoryRetentionReason.SOURCE_CONFIDENCE_TOO_LOW, failure)
    }

    private fun referenceOrResearch(
        candidate: MemoryRetentionCandidate,
        score: Double,
        reason: MemoryRetentionReason,
        failure: MemoryEvidenceGateFailure
    ): MemoryRetentionDecision =
        if (candidate.sourceReferenceAvailable && candidate.provenanceComplete) {
            decision(MemoryRetentionAction.STORE_SOURCE_REFERENCE, reason, score, failure)
        } else {
            decision(MemoryRetentionAction.RESEARCH_AGAIN, MemoryRetentionReason.EASILY_RESEARCHED_AGAIN, score, failure)
        }

    private fun decision(
        action: MemoryRetentionAction,
        reason: MemoryRetentionReason,
        score: Double = 0.0,
        gateFailure: MemoryEvidenceGateFailure? = null
    ) = MemoryRetentionDecision(action, reason, score, gateFailure)
}
