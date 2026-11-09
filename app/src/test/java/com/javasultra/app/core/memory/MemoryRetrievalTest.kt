package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryRetrievalTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun record(
        id: String,
        content: String,
        sensitivity: MemorySensitivity = MemorySensitivity.PUBLIC,
        kind: MemoryKind = MemoryKind.SEMANTIC,
        verification: MemoryVerification = MemoryVerification.CORROBORATED,
        expiry: Instant? = null
    ) = MemoryRecord(
        MemoryId(id), "creator", kind, content,
        MemoryProvenance(MemorySourceType.PUBLIC_WEB_SOURCE, null, now.minusSeconds(60), "research"),
        now.minusSeconds(50), now.minusSeconds(10), 0.9, verification, sensitivity,
        MemoryLifecycle.ACTIVE, expiry, tags = setOf("knowledge")
    )

    private fun request(query: String, creator: Boolean = false, actor: String = "researcher", stepUp: Boolean = false) =
        MemorySearchRequest(query, MemoryAccessRequest(actor, creator, MemoryAction.READ, stepUp), now, 10)

    @Test fun ranksRelevantRecordsAndReturnsMatchedTerms() {
        val result = MemoryRetrieval.search(
            listOf(record("a", "The Moon orbits Earth"), record("b", "Ocean tides and currents")),
            request("Moon Earth")
        ) as MemorySearchResult.Success
        assertEquals("a", result.matches.first().record.id.value)
        assertEquals(setOf("moon", "earth"), result.matches.first().matchedTerms)
    }

    @Test fun excludesCreatorPrivateMemoriesFromOtherActors() {
        val privateRecord = record("private", "Creator prefers quiet reminders", MemorySensitivity.CREATOR_PRIVATE, MemoryKind.PREFERENCE)
        val result = MemoryRetrieval.search(listOf(privateRecord), request("quiet reminders")) as MemorySearchResult.Success
        assertTrue(result.matches.isEmpty())
    }

    @Test fun creatorCanRetrieveOwnPrivateMemory() {
        val privateRecord = record("private", "Creator prefers quiet reminders", MemorySensitivity.CREATOR_PRIVATE, MemoryKind.PREFERENCE)
        val result = MemoryRetrieval.search(listOf(privateRecord), request("quiet reminders", true, "creator")) as MemorySearchResult.Success
        assertEquals(1, result.matches.size)
    }

    @Test fun excludesExpiredRecords() {
        val expired = record("old", "Historical astronomy note", expiry = now.minusSeconds(1))
        val result = MemoryRetrieval.search(listOf(expired), request("astronomy")) as MemorySearchResult.Success
        assertTrue(result.matches.isEmpty())
    }

    @Test fun rejectsEmptyAndOversizedQueries() {
        assertEquals(MemorySearchDenial.EMPTY_QUERY, (MemoryRetrieval.search(emptyList(), request("!!!")) as MemorySearchResult.Denied).reason)
        assertEquals(MemorySearchDenial.INVALID_LIMIT, (MemoryRetrieval.search(emptyList(), request("moon").copy(limit = 101)) as MemorySearchResult.Denied).reason)
    }

    @Test fun disputedKnowledgeRanksBelowCorroboratedKnowledge() {
        val good = record("good", "Jupiter is a gas giant", verification = MemoryVerification.CORROBORATED)
        val disputed = record("disputed", "Jupiter is a gas giant", verification = MemoryVerification.DISPUTED)
        val result = MemoryRetrieval.search(listOf(disputed, good), request("Jupiter gas giant")) as MemorySearchResult.Success
        assertEquals("good", result.matches.first().record.id.value)
    }
}
