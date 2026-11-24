package com.javasultra.app.core.learning

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.Instant

class PublicResearchLearningProviderTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val task = LearningTask(
        id = "task-1",
        objective = "Explain orbital motion",
        domain = LearningDomain.SCIENCE,
        createdAt = now.minusSeconds(5),
        state = LearningTaskState.RESEARCHING,
        requestedByCreator = false,
        sourceBudget = 3,
        timeBudgetSeconds = 60,
        privacySafe = true
    )
    private val allowed = setOf("nasa.gov", "esa.int")

    private fun candidate(id: String, host: String, publisher: String) = PublicResearchCandidate(
        id = id,
        request = PublicPageRequest(
            uri = URI("https://$host/research"),
            method = ResearchHttpMethod.GET,
            declaredPublic = true,
            robotsPolicyChecked = true
        ),
        publisher = publisher,
        kind = EvidenceSourceKind.OFFICIAL_DOCUMENTATION,
        publishedAt = now.minusSeconds(120),
        trustScore = 0.95
    )

    private fun provider(
        candidates: List<PublicResearchCandidate>,
        onFetch: (PublicPageRequest) -> Unit = {}
    ): PublicResearchLearningProvider {
        val discovery = PublicResearchDiscovery { _, _, _ -> Result.success(candidates) }
        val adapter = object : PublicResearchAdapter {
            override suspend fun retrieve(request: PublicPageRequest): Result<ResearchDocument> {
                onFetch(request)
                val uri = request.uri
                return Result.success(
                    ResearchDocument(
                        canonicalUri = uri,
                        title = "Official research",
                        retrievedAtEpochMillis = now.toEpochMilli(),
                        contentType = "text/plain",
                        extractedText = "Orbital motion is described by gravitational dynamics.",
                        contentDigestSha256 = "a".repeat(64)
                    )
                )
            }
        }
        val service = DefaultPublicResearchService(adapter)
        val extractor = EvidenceClaimExtractor { _, sources, _ ->
            val ids = sources.map { it.id }.toSet()
            Result.success(
                listOf(
                    EvidenceClaim(
                        id = "claim-1",
                        statement = "Orbital motion is governed by gravity.",
                        sourceIds = ids,
                        supportingSourceIds = ids,
                        contradictingSourceIds = emptySet(),
                        createdAt = now,
                        reviewedAt = null,
                        status = ClaimStatus.SUPPORTED,
                        confidence = 0.9
                    )
                )
            )
        }
        return PublicResearchLearningProvider(discovery, service, extractor) { now }
    }

    @Test fun retrievesApprovedSourcesAndReturnsProvenancedClaims() = runBlocking {
        var fetchCount = 0
        val provider = provider(
            listOf(candidate("nasa", "nasa.gov", "NASA"), candidate("esa", "esa.int", "ESA"))
        ) { fetchCount++ }

        val result = provider.research(task, allowed, 3, 60)

        assertTrue(result.isSuccess)
        assertEquals(2, fetchCount)
        assertEquals(setOf("nasa", "esa"), result.getOrThrow().claims.single().sourceIds)
        assertEquals(2, result.getOrThrow().sources.size)
    }

    @Test fun rejectsCandidateOutsideAllowedDomainsBeforeNetworkFetch() = runBlocking {
        var fetchCount = 0
        val provider = provider(listOf(candidate("bad", "example.com", "Unknown"))) { fetchCount++ }

        val result = provider.research(task, allowed, 3, 60)

        assertFalse(result.isSuccess)
        assertEquals(0, fetchCount)
    }

    @Test fun rejectsDuplicateCandidateIdsBeforeNetworkFetch() = runBlocking {
        var fetchCount = 0
        val provider = provider(
            listOf(candidate("duplicate", "nasa.gov", "NASA"), candidate("duplicate", "esa.int", "ESA"))
        ) { fetchCount++ }

        val result = provider.research(task, allowed, 3, 60)

        assertFalse(result.isSuccess)
        assertEquals(0, fetchCount)
    }

    @Test fun rejectsOverBudgetDiscoveryBeforeNetworkFetch() = runBlocking {
        var fetchCount = 0
        val provider = provider(
            listOf(
                candidate("one", "nasa.gov", "NASA"),
                candidate("two", "esa.int", "ESA"),
                candidate("three", "nasa.gov", "NASA"),
                candidate("four", "esa.int", "ESA")
            )
        ) { fetchCount++ }

        val result = provider.research(task, allowed, 3, 60)

        assertFalse(result.isSuccess)
        assertEquals(0, fetchCount)
    }
}
