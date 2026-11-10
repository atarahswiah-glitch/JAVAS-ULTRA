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
    INVALID_CANDIDATE
}

data class MemoryRetentionCandidate(
    val type: MemoryRetentionType,
    val futureUseLikelihood: Double,
    val uniqueness: Double,
    val reResearchCost: Double,
    val sourceVerified: Boolean,
    val sourceReferenceAvailable: Boolean,
    val explicitlyRequestedToRetain: Boolean = false,
    val explicitlyRequestedNotToRetain: Boolean = false
)

data class MemoryRetentionDecision(
    val action: MemoryRetentionAction,
    val reason: MemoryRetentionReason
)

/**
 * Chooses whether information should become durable memory.
 *
 * This is a local decision policy only. It does not persist, encrypt, authorize
 * or delete records; repository and trusted identity boundaries must enforce those operations.
 */
object MemoryRetentionPolicy {
    fun evaluate(candidate: MemoryRetentionCandidate): MemoryRetentionDecision {
        if (candidate.futureUseLikelihood !in 0.0..1.0 ||
            candidate.uniqueness !in 0.0..1.0 ||
            candidate.reResearchCost !in 0.0..1.0
        ) return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.INVALID_CANDIDATE)

        if (candidate.explicitlyRequestedNotToRetain) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.EXPLICITLY_NOT_REQUESTED)
        }
        if (candidate.type == MemoryRetentionType.CREDENTIAL_OR_SECRET) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.SECRET_MUST_NOT_BE_STORED)
        }
        if (candidate.type == MemoryRetentionType.TRANSIENT) {
            return decision(MemoryRetentionAction.DO_NOT_RETAIN, MemoryRetentionReason.TRANSIENT_INFORMATION)
        }
        if (candidate.type == MemoryRetentionType.SENSITIVE_INFORMATION) {
            return decision(MemoryRetentionAction.REQUIRE_CREATOR_REVIEW, MemoryRetentionReason.SENSITIVE_INFORMATION_REQUIRES_REVIEW)
        }

        if (candidate.type in setOf(
                MemoryRetentionType.CREATOR_INSTRUCTION,
                MemoryRetentionType.STABLE_PREFERENCE,
                MemoryRetentionType.PROJECT_DECISION
            )
        ) {
            return decision(MemoryRetentionAction.STORE_CONTEXT, MemoryRetentionReason.CREATOR_CONTEXT_IS_DURABLE)
        }

        if (candidate.explicitlyRequestedToRetain) {
            return when {
                candidate.sourceVerified ->
                    decision(MemoryRetentionAction.STORE_KNOWLEDGE, MemoryRetentionReason.EXPLICIT_RETENTION_REQUEST)
                candidate.sourceReferenceAvailable ->
                    decision(MemoryRetentionAction.STORE_SOURCE_REFERENCE, MemoryRetentionReason.SOURCE_REFERENCE_IS_SUFFICIENT)
                else ->
                    decision(MemoryRetentionAction.REQUIRE_CREATOR_REVIEW, MemoryRetentionReason.UNVERIFIED_WITHOUT_SOURCE)
            }
        }

        val worthRetaining = candidate.futureUseLikelihood >= 0.6 ||
            candidate.uniqueness >= 0.6 ||
            candidate.reResearchCost >= 0.7

        if (candidate.sourceVerified && worthRetaining) {
            return decision(MemoryRetentionAction.STORE_KNOWLEDGE, MemoryRetentionReason.VERIFIED_AND_USEFUL)
        }
        if (candidate.sourceReferenceAvailable) {
            return decision(MemoryRetentionAction.STORE_SOURCE_REFERENCE, MemoryRetentionReason.SOURCE_REFERENCE_IS_SUFFICIENT)
        }
        return decision(MemoryRetentionAction.RESEARCH_AGAIN, MemoryRetentionReason.EASILY_RESEARCHED_AGAIN)
    }

    private fun decision(action: MemoryRetentionAction, reason: MemoryRetentionReason) =
        MemoryRetentionDecision(action, reason)
}
