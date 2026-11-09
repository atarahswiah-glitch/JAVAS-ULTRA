package com.javasultra.app.core.memory

enum class MemoryAction { READ, UPDATE, EXPORT, DELETE }

data class MemoryAccessRequest(
    val actorPrincipalId: String,
    val isCreator: Boolean,
    val action: MemoryAction,
    val stepUpAuthenticated: Boolean = false
)

enum class MemoryAccessDenial {
    INVALID_RECORD, CREATOR_ONLY, OWNER_MISMATCH, STEP_UP_REQUIRED, NOT_ACTIVE
}

sealed interface MemoryAccessDecision {
    data object Allowed : MemoryAccessDecision
    data class Denied(val reason: MemoryAccessDenial) : MemoryAccessDecision
}

/**
 * Local policy preflight only. A trusted persistence/API layer must repeat these
 * checks against authenticated server-side identity and current record state.
 */
object MemoryAccessPolicy {
    fun evaluate(record: MemoryRecord, request: MemoryAccessRequest): MemoryAccessDecision {
        if (record.validate().isNotEmpty()) return denied(MemoryAccessDenial.INVALID_RECORD)
        if (record.lifecycle != MemoryLifecycle.ACTIVE &&
            !(request.action == MemoryAction.DELETE &&
                record.lifecycle == MemoryLifecycle.DELETION_REQUESTED)
        ) return denied(MemoryAccessDenial.NOT_ACTIVE)

        val creatorOwned = record.sensitivity == MemorySensitivity.CREATOR_PRIVATE ||
            record.sensitivity == MemorySensitivity.HIGHLY_SENSITIVE ||
            record.kind in setOf(MemoryKind.PREFERENCE, MemoryKind.EPISODIC, MemoryKind.PROJECT)

        if (creatorOwned && !request.isCreator) return denied(MemoryAccessDenial.CREATOR_ONLY)
        if (request.isCreator && record.ownerPrincipalId != request.actorPrincipalId) {
            return denied(MemoryAccessDenial.OWNER_MISMATCH)
        }
        if (record.sensitivity == MemorySensitivity.HIGHLY_SENSITIVE &&
            request.action in setOf(MemoryAction.READ, MemoryAction.EXPORT, MemoryAction.DELETE) &&
            !request.stepUpAuthenticated
        ) return denied(MemoryAccessDenial.STEP_UP_REQUIRED)

        return MemoryAccessDecision.Allowed
    }

    private fun denied(reason: MemoryAccessDenial) = MemoryAccessDecision.Denied(reason)
}
