package com.javasultra.app.core.agent

import com.javasultra.app.core.memory.MemoryAccessRequest
import com.javasultra.app.core.memory.MemoryAction
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryRepository
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemoryRetrieval
import com.javasultra.app.core.memory.MemorySearchRequest
import com.javasultra.app.core.memory.MemorySearchResult
import com.javasultra.app.core.memory.MemoryVerification
import java.time.Clock
import java.util.Locale

/**
 * Bridges protected chat to the encrypted local repository.
 *
 * Only the authenticated Creator's active, unexpired, readable memories are considered.
 * Highly-sensitive records remain excluded because this read path does not claim step-up
 * authentication. Disputed and low-confidence records are not supplied to the model.
 */
class EncryptedRepositoryConversationMemoryProvider(
    private val repository: MemoryRepository,
    private val clock: Clock = Clock.systemUTC()
) : ConversationMemoryContextProvider {

    override suspend fun loadApprovedContext(
        principalId: String,
        query: String
    ): Result<ApprovedConversationMemory> {
        if (principalId.isBlank() || principalId.length > MAX_PRINCIPAL_CHARS ||
            query.isBlank() || query.length > MAX_QUERY_CHARS
        ) return Result.failure(IllegalArgumentException("Invalid Creator memory query"))

        val now = clock.instant()
        val access = MemoryAccessRequest(
            actorPrincipalId = principalId,
            isCreator = true,
            action = MemoryAction.READ,
            stepUpAuthenticated = false,
            now = now
        )
        val readable = when (val result = repository.listReadable(access)) {
            is MemoryRepositoryResult.Success -> result.value
            is MemoryRepositoryResult.Failure ->
                return Result.failure(IllegalStateException("Creator memory is unavailable"))
        }

        val eligible = readable.filter { record ->
            record.ownerPrincipalId == principalId &&
                record.lifecycle == com.javasultra.app.core.memory.MemoryLifecycle.ACTIVE &&
                (record.expiresAt == null || record.expiresAt.isAfter(now)) &&
                record.verification != MemoryVerification.DISPUTED &&
                record.confidence >= MIN_CONFIDENCE &&
                record.kind in ALLOWED_KINDS
        }
        val ranked = when (
            val result = MemoryRetrieval.search(
                eligible,
                MemorySearchRequest(query = query, access = access, now = now, limit = MAX_RESULTS)
            )
        ) {
            is MemorySearchResult.Success -> result.matches
            is MemorySearchResult.Denied -> return Result.failure(
                IllegalArgumentException("Creator memory query was rejected")
            )
        }

        val context = StringBuilder()
        var sourceCount = 0
        for (match in ranked) {
            if (sourceCount >= MAX_RESULTS) break
            val record = match.record
            val header = "[${record.kind.name}; confidence=${"%.2f".format(Locale.ROOT, record.confidence)}]\n"
            val remaining = MAX_CONTEXT_CHARS - context.length - header.length - 1
            if (remaining <= 0) break
            val body = record.content.take(minOf(MAX_RECORD_CHARS, remaining))
            if (body.isBlank()) continue
            context.append(header).append(body).append('\n')
            sourceCount++
        }

        return Result.success(
            ApprovedConversationMemory(
                context = context.toString().trim(),
                sourceCount = sourceCount
            )
        )
    }

    private companion object {
        const val MAX_PRINCIPAL_CHARS = 256
        const val MAX_QUERY_CHARS = 12_000
        const val MAX_RESULTS = 8
        const val MAX_CONTEXT_CHARS = 8_000
        const val MAX_RECORD_CHARS = 2_000
        const val MIN_CONFIDENCE = 0.50
        val ALLOWED_KINDS = setOf(
            MemoryKind.PREFERENCE,
            MemoryKind.EPISODIC,
            MemoryKind.SEMANTIC,
            MemoryKind.PROJECT
        )
    }
}
