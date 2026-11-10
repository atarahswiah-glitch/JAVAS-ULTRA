package com.javasultra.app.core.memory

/**
 * Bridges retention classification to the existing encrypted-memory proposal model.
 *
 * This factory is deliberately side-effect free: it never writes or activates memory.
 * Callers may pass only a prepared PROPOSED record to MemoryRepository.insertProposed,
 * which enforces ownership and encrypts the complete record. Creator review and
 * trusted-server authorization remain separate gates.
 */
enum class MemoryRetentionPreparationDenial {
    NO_RECORD_REQUIRED,
    INVALID_PROPOSED_RECORD,
    RECORD_KIND_MISMATCH,
    RECORD_SENSITIVITY_MISMATCH,
    SOURCE_REFERENCE_REQUIRED,
    SOURCE_NOT_VERIFIED
}

data class MemoryRetentionPreparation(
    val retention: MemoryRetentionDecision,
    val recordToPropose: MemoryRecord? = null,
    val denial: MemoryRetentionPreparationDenial? = null
)

object MemoryRetentionProposalFactory {
    fun prepare(
        candidate: MemoryRetentionCandidate,
        proposedRecord: MemoryRecord
    ): MemoryRetentionPreparation {
        val retention = MemoryRetentionPolicy.evaluate(candidate)
        when (retention.action) {
            MemoryRetentionAction.DO_NOT_RETAIN,
            MemoryRetentionAction.RESEARCH_AGAIN,
            MemoryRetentionAction.REQUIRE_CREATOR_REVIEW ->
                return MemoryRetentionPreparation(
                    retention,
                    denial = MemoryRetentionPreparationDenial.NO_RECORD_REQUIRED
                )
            MemoryRetentionAction.STORE_SOURCE_REFERENCE,
            MemoryRetentionAction.STORE_KNOWLEDGE,
            MemoryRetentionAction.STORE_CONTEXT -> Unit
        }

        if (proposedRecord.validate().isNotEmpty() ||
            proposedRecord.lifecycle != MemoryLifecycle.PROPOSED ||
            proposedRecord.supersedes != null
        ) return denied(retention, MemoryRetentionPreparationDenial.INVALID_PROPOSED_RECORD)

        val expectedKind = when (retention.action) {
            MemoryRetentionAction.STORE_CONTEXT -> when (candidate.type) {
                MemoryRetentionType.CREATOR_INSTRUCTION -> MemoryKind.EPISODIC
                MemoryRetentionType.STABLE_PREFERENCE -> MemoryKind.PREFERENCE
                MemoryRetentionType.PROJECT_DECISION -> MemoryKind.PROJECT
                else -> return denied(retention, MemoryRetentionPreparationDenial.RECORD_KIND_MISMATCH)
            }
            MemoryRetentionAction.STORE_SOURCE_REFERENCE,
            MemoryRetentionAction.STORE_KNOWLEDGE -> MemoryKind.SEMANTIC
            else -> return denied(retention, MemoryRetentionPreparationDenial.NO_RECORD_REQUIRED)
        }
        if (proposedRecord.kind != expectedKind) {
            return denied(retention, MemoryRetentionPreparationDenial.RECORD_KIND_MISMATCH)
        }

        if (retention.action == MemoryRetentionAction.STORE_CONTEXT) {
            if (proposedRecord.sensitivity != MemorySensitivity.CREATOR_PRIVATE ||
                proposedRecord.provenance.sourceType != MemorySourceType.CREATOR_STATEMENT
            ) return denied(retention, MemoryRetentionPreparationDenial.RECORD_SENSITIVITY_MISMATCH)
        } else {
            if (proposedRecord.sensitivity !in setOf(
                    MemorySensitivity.PUBLIC,
                    MemorySensitivity.INTERNAL,
                    MemorySensitivity.CREATOR_PRIVATE
                )
            ) return denied(retention, MemoryRetentionPreparationDenial.RECORD_SENSITIVITY_MISMATCH)
            if (proposedRecord.provenance.sourceReference.isNullOrBlank()) {
                return denied(retention, MemoryRetentionPreparationDenial.SOURCE_REFERENCE_REQUIRED)
            }
            if (retention.action == MemoryRetentionAction.STORE_KNOWLEDGE &&
                (!candidate.sourceVerified ||
                    proposedRecord.verification in setOf(MemoryVerification.UNVERIFIED, MemoryVerification.DISPUTED))
            ) return denied(retention, MemoryRetentionPreparationDenial.SOURCE_NOT_VERIFIED)
        }

        return MemoryRetentionPreparation(retention, recordToPropose = proposedRecord)
    }

    private fun denied(
        retention: MemoryRetentionDecision,
        reason: MemoryRetentionPreparationDenial
    ) = MemoryRetentionPreparation(retention, denial = reason)
}
