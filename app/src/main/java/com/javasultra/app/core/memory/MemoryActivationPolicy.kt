package com.javasultra.app.core.memory

import java.time.Instant

data class MemoryActivationRequest(
    val actorPrincipalId: String,
    val at: Instant,
    val stepUpAuthenticated: Boolean
)

enum class MemoryActivationDenial {
    INVALID_RECORD,
    OWNER_MISMATCH,
    INVALID_STATE,
    PRIOR_RECORD_INVALID,
    STEP_UP_REQUIRED
}

sealed interface MemoryActivationDecision {
    data object Allowed : MemoryActivationDecision
    data class Denied(val reason: MemoryActivationDenial) : MemoryActivationDecision
}

/** Pure preflight for activating a proposed memory record and superseding its predecessor. */
object MemoryActivationPolicy {
    fun evaluate(
        proposed: MemoryRecord,
        prior: MemoryRecord?,
        request: MemoryActivationRequest
    ): MemoryActivationDecision {
        if (proposed.validate().isNotEmpty()) return denied(MemoryActivationDenial.INVALID_RECORD)
        if (proposed.ownerPrincipalId != request.actorPrincipalId) {
            return denied(MemoryActivationDenial.OWNER_MISMATCH)
        }
        if (proposed.lifecycle != MemoryLifecycle.PROPOSED || request.at.isBefore(proposed.updatedAt)) {
            return denied(MemoryActivationDenial.INVALID_STATE)
        }
        if (proposed.supersedes == null && prior != null) {
            return denied(MemoryActivationDenial.PRIOR_RECORD_INVALID)
        }
        if (proposed.supersedes != null) {
            if (prior == null || prior.id != proposed.supersedes ||
                prior.ownerPrincipalId != request.actorPrincipalId ||
                prior.lifecycle != MemoryLifecycle.ACTIVE ||
                request.at.isBefore(prior.updatedAt)
            ) return denied(MemoryActivationDenial.PRIOR_RECORD_INVALID)
        }
        if ((proposed.sensitivity == MemorySensitivity.HIGHLY_SENSITIVE ||
                prior?.sensitivity == MemorySensitivity.HIGHLY_SENSITIVE) &&
            !request.stepUpAuthenticated
        ) return denied(MemoryActivationDenial.STEP_UP_REQUIRED)
        return MemoryActivationDecision.Allowed
    }

    private fun denied(reason: MemoryActivationDenial) = MemoryActivationDecision.Denied(reason)
}
