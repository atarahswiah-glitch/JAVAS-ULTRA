package com.javasultra.app.core.memory

enum class MemoryProposalReplayDecision {
    EXACT_REPLAY,
    CONFLICTING_CONTENT,
    OWNER_MISMATCH
}

/**
 * Makes proposed-memory insertion safe to retry after an interrupted multi-record workflow.
 * An existing id is accepted only when the complete encrypted record decodes to the exact
 * same value and both records belong to the authenticated actor. Reusing an id for changed
 * content is never treated as success.
 */
object MemoryProposalIdempotencyPolicy {
    fun evaluate(
        existing: MemoryRecord,
        proposed: MemoryRecord,
        actorPrincipalId: String
    ): MemoryProposalReplayDecision {
        if (actorPrincipalId.isBlank() ||
            existing.ownerPrincipalId != actorPrincipalId ||
            proposed.ownerPrincipalId != actorPrincipalId
        ) return MemoryProposalReplayDecision.OWNER_MISMATCH
        return if (existing == proposed) {
            MemoryProposalReplayDecision.EXACT_REPLAY
        } else {
            MemoryProposalReplayDecision.CONFLICTING_CONTENT
        }
    }
}
