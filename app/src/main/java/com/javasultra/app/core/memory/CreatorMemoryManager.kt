package com.javasultra.app.core.memory

import java.time.Instant
import java.util.UUID

/**
 * Creator-facing use cases for encrypted memory.
 *
 * New memories and corrections are always proposals first. Only an explicit Creator
 * confirmation calls activate(); corrections do not replace the old record until then.
 * UI and trusted server layers must still authenticate the Creator independently.
 */
class CreatorMemoryManager(
    private val repository: MemoryRepository,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun review(
        creatorPrincipalId: String,
        now: Instant,
        stepUpAuthenticated: Boolean = false
    ): MemoryRepositoryResult<List<MemoryRecord>> =
        repository.listReadable(access(creatorPrincipalId, MemoryAction.READ, now, stepUpAuthenticated))

    fun proposeCreatorStatement(
        creatorPrincipalId: String,
        content: String,
        kind: MemoryKind,
        sensitivity: MemorySensitivity,
        now: Instant,
        expiresAt: Instant? = null,
        tags: Set<String> = emptySet()
    ): MemoryRepositoryResult<MemoryRecord> {
        if (!validInput(creatorPrincipalId, content, now, expiresAt, tags)) {
            return failure(MemoryRepositoryFailure.INVALID_RECORD)
        }
        val record = MemoryRecord(
            id = MemoryId(idFactory()),
            ownerPrincipalId = creatorPrincipalId,
            kind = kind,
            content = content.trim(),
            provenance = MemoryProvenance(
                sourceType = MemorySourceType.CREATOR_STATEMENT,
                sourceReference = null,
                observedAt = now,
                publisherOrActor = creatorPrincipalId,
                notes = "Creator-submitted memory proposal; awaiting explicit activation"
            ),
            createdAt = now,
            updatedAt = now,
            confidence = 1.0,
            verification = MemoryVerification.SINGLE_SOURCE,
            sensitivity = sensitivity,
            lifecycle = MemoryLifecycle.PROPOSED,
            expiresAt = expiresAt,
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        )
        if (record.validate().isNotEmpty()) return failure(MemoryRepositoryFailure.INVALID_RECORD)
        val stored = repository.insertProposed(record, creatorPrincipalId)
        return when (stored) {
            is MemoryRepositoryResult.Success -> MemoryRepositoryResult.Success(record)
            is MemoryRepositoryResult.Failure -> stored
        }
    }

    /** Explicit confirmation is the only path from PROPOSED to ACTIVE. */
    fun confirm(
        creatorPrincipalId: String,
        id: MemoryId,
        now: Instant,
        stepUpAuthenticated: Boolean = false
    ): MemoryRepositoryResult<MemoryRecord> =
        repository.activate(id, creatorPrincipalId, now, stepUpAuthenticated)

    /** Proposes a correction without changing the currently active record. */
    fun proposeCorrection(
        creatorPrincipalId: String,
        currentId: MemoryId,
        correctedContent: String,
        now: Instant,
        stepUpAuthenticated: Boolean = false
    ): MemoryRepositoryResult<MemoryRecord> {
        if (creatorPrincipalId.isBlank() || creatorPrincipalId.length > MAX_OWNER_CHARS ||
            correctedContent.isBlank() || correctedContent.length > MAX_CONTENT_CHARS
        ) return failure(MemoryRepositoryFailure.INVALID_RECORD)

        val request = access(creatorPrincipalId, MemoryAction.UPDATE, now, stepUpAuthenticated)
        val current = when (val result = repository.read(currentId, request)) {
            is MemoryRepositoryResult.Success -> result.value
            is MemoryRepositoryResult.Failure -> return result
        }
        val replacement = current.copy(
            id = MemoryId(idFactory()),
            content = correctedContent.trim(),
            provenance = MemoryProvenance(
                sourceType = MemorySourceType.CREATOR_STATEMENT,
                sourceReference = current.id.value,
                observedAt = now,
                publisherOrActor = creatorPrincipalId,
                notes = "Creator-proposed correction; prior memory remains active until confirmation"
            ),
            createdAt = now,
            updatedAt = now,
            confidence = 1.0,
            verification = MemoryVerification.SINGLE_SOURCE,
            lifecycle = MemoryLifecycle.PROPOSED,
            expiresAt = current.expiresAt,
            supersedes = current.id
        )
        if (replacement.validate().isNotEmpty()) return failure(MemoryRepositoryFailure.INVALID_RECORD)
        return repository.proposeCorrection(currentId, replacement, request)
    }

    fun delete(
        creatorPrincipalId: String,
        id: MemoryId,
        now: Instant,
        stepUpAuthenticated: Boolean = false
    ): MemoryRepositoryResult<Unit> =
        repository.delete(id, access(creatorPrincipalId, MemoryAction.DELETE, now, stepUpAuthenticated))

    fun export(
        creatorPrincipalId: String,
        now: Instant,
        stepUpAuthenticated: Boolean = false
    ): MemoryRepositoryResult<List<MemoryRecord>> =
        repository.exportReadable(access(creatorPrincipalId, MemoryAction.EXPORT, now, stepUpAuthenticated))

    private fun access(
        owner: String,
        action: MemoryAction,
        now: Instant,
        stepUpAuthenticated: Boolean
    ) = MemoryAccessRequest(
        actorPrincipalId = owner,
        isCreator = true,
        action = action,
        stepUpAuthenticated = stepUpAuthenticated,
        now = now
    )

    private fun validInput(
        owner: String,
        content: String,
        now: Instant,
        expiresAt: Instant?,
        tags: Set<String>
    ): Boolean =
        owner.isNotBlank() && owner.length <= MAX_OWNER_CHARS &&
            content.isNotBlank() && content.length <= MAX_CONTENT_CHARS &&
            (expiresAt == null || expiresAt.isAfter(now)) &&
            tags.size <= MAX_TAGS && tags.all { it.length <= MAX_TAG_CHARS }

    private fun failure(reason: MemoryRepositoryFailure) =
        MemoryRepositoryResult.Failure(reason)

    private companion object {
        const val MAX_OWNER_CHARS = 256
        const val MAX_CONTENT_CHARS = 4_000
        const val MAX_TAGS = 30
        const val MAX_TAG_CHARS = 64
    }
}
