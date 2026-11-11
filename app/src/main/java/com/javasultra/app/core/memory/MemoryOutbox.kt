package com.javasultra.app.core.memory

import java.time.Instant

/**
 * Encrypted, owner-scoped work waiting to be delivered to a durable secondary journal.
 * Payload bytes are opaque to the memory layer; the learning module owns their codec.
 */
data class MemoryOutboxItem(
    val id: String,
    val ownerPrincipalId: String,
    val payload: ByteArray,
    val createdAt: Instant
) {
    fun validate(): Boolean = id.isNotBlank() &&
        ownerPrincipalId.isNotBlank() &&
        payload.isNotEmpty() &&
        payload.size <= MAX_PAYLOAD_BYTES

    companion object {
        const val MAX_PAYLOAD_BYTES = 262_144
    }
}

sealed interface MemoryOutboxResult<out T> {
    data class Success<T>(val value: T) : MemoryOutboxResult<T>
    data class Failure(val reason: MemoryRepositoryFailure) : MemoryOutboxResult<Nothing>
}

/** The same SQLite database must commit the proposal batch and its outbox item together. */
interface MemoryOutboxStore {
    fun insertProposedBatchWithOutbox(
        records: List<MemoryRecord>,
        outboxItem: MemoryOutboxItem,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit>

    fun pendingOutbox(actorPrincipalId: String, limit: Int = 25): MemoryOutboxResult<List<MemoryOutboxItem>>

    /** Acknowledgement is idempotent for an already-removed item, but never crosses owners. */
    fun acknowledgeOutbox(id: String, actorPrincipalId: String): MemoryOutboxResult<Unit>
}
