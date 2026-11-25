package com.javasultra.app.core.agent

import com.javasultra.app.core.memory.MemoryAccessRequest
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryProvenance
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepository
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.memory.MemorySourceType
import com.javasultra.app.core.memory.MemoryVerification
import com.javasultra.app.core.memory.MemoryAction
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class EncryptedRepositoryConversationMemoryProviderTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")

    private fun record(
        id: String,
        owner: String = "creator-1",
        content: String,
        verification: MemoryVerification = MemoryVerification.CREATOR_CONFIRMED,
        confidence: Double = 0.9,
        lifecycle: MemoryLifecycle = MemoryLifecycle.ACTIVE
    ) = MemoryRecord(
        id = MemoryId(id),
        ownerPrincipalId = owner,
        kind = MemoryKind.PREFERENCE,
        content = content,
        provenance = MemoryProvenance(
            sourceType = MemorySourceType.CREATOR_STATEMENT,
            sourceReference = null,
            observedAt = now.minusSeconds(60),
            publisherOrActor = "creator-1"
        ),
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(30),
        confidence = confidence,
        verification = verification,
        sensitivity = MemorySensitivity.CREATOR_PRIVATE,
        lifecycle = lifecycle
    )

    private class FakeRepository(private val records: List<MemoryRecord>) : MemoryRepository {
        var requestSeen: MemoryAccessRequest? = null
        override fun listReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>> {
            requestSeen = request
            return MemoryRepositoryResult.Success(records)
        }
        override fun insertProposedBatch(records: List<MemoryRecord>, actorPrincipalId: String) = unsupported<Unit>()
        override fun insertProposed(record: MemoryRecord, actorPrincipalId: String) = unsupported<Unit>()
        override fun activate(id: MemoryId, actorPrincipalId: String, at: Instant, stepUpAuthenticated: Boolean) = unsupported<MemoryRecord>()
        override fun read(id: MemoryId, request: MemoryAccessRequest) = unsupported<MemoryRecord>()
        override fun exportReadable(request: MemoryAccessRequest) = unsupported<List<MemoryRecord>>()
        override fun delete(id: MemoryId, request: MemoryAccessRequest) = unsupported<Unit>()
        override fun proposeCorrection(id: MemoryId, replacement: MemoryRecord, request: MemoryAccessRequest) = unsupported<MemoryRecord>()
        private fun <T> unsupported(): MemoryRepositoryResult<T> =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
    }

    @Test fun returnsOnlyRelevantActiveNonDisputedCreatorMemory() = runBlocking {
        val repository = FakeRepository(
            listOf(
                record("good", content = "Creator prefers concise explanations and practical examples."),
                record("disputed", content = "Creator prefers concise answers.", verification = MemoryVerification.DISPUTED),
                record("weak", content = "Creator likes concise answers.", confidence = 0.2),
                record("inactive", content = "Creator prefers concise answers.", lifecycle = MemoryLifecycle.PROPOSED),
                record("other-owner", owner = "another-person", content = "Creator prefers concise answers.")
            )
        )
        val provider = EncryptedRepositoryConversationMemoryProvider(
            repository,
            Clock.fixed(now, ZoneOffset.UTC)
        )

        val result = provider.loadApprovedContext("creator-1", "concise explanations")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().context.contains("practical examples"))
        assertEquals(1, result.getOrThrow().sourceCount)
        assertEquals("creator-1", repository.requestSeen?.actorPrincipalId)
        assertEquals(MemoryAction.READ, repository.requestSeen?.action)
        assertEquals(false, repository.requestSeen?.stepUpAuthenticated)
    }

    @Test fun rejectsBlankCreatorOrQueryBeforeReadingRepository() = runBlocking {
        val repository = FakeRepository(emptyList())
        val provider = EncryptedRepositoryConversationMemoryProvider(repository, Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(provider.loadApprovedContext("", "hello").isFailure)
        assertTrue(provider.loadApprovedContext("creator-1", "  ").isFailure)
        assertEquals(null, repository.requestSeen)
    }
}
