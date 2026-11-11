package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class LearningEvidenceDigestTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")

    private fun claims() = listOf(
        EvidenceClaim(
            "claim-a", "A corroborated statement",
            setOf("source-a", "source-b"), setOf("source-a", "source-b"), emptySet(),
            now.minusSeconds(60), now.minusSeconds(5), ClaimStatus.SUPPORTED, 0.85
        )
    )

    private fun sources() = listOf(
        ResearchSource("source-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "Official source",
            "Publisher A", "https://a.example.org", now.minusSeconds(120), now.minusSeconds(30), 0.9),
        ResearchSource("source-b", EvidenceSourceKind.ACADEMIC_REVIEW, "Academic review",
            "Publisher B", "https://b.example.org", now.minusSeconds(120), now.minusSeconds(20), 0.8)
    )

    @Test fun digestIsIndependentOfClaimSourceAndSetIterationOrder() {
        val forward = LearningEvidenceDigest.computeSha256(claims(), sources())
        val reversedClaim = claims().single().copy(
            sourceIds = linkedSetOf("source-b", "source-a"),
            supportingSourceIds = linkedSetOf("source-b", "source-a")
        )
        val reversed = LearningEvidenceDigest.computeSha256(listOf(reversedClaim), sources().reversed())
        assertEquals(forward, reversed)
    }

    @Test fun changingClaimTextChangesDigest() {
        val original = LearningEvidenceDigest.computeSha256(claims(), sources())
        val altered = LearningEvidenceDigest.computeSha256(
            listOf(claims().single().copy(statement = "A changed statement")), sources()
        )
        assertNotEquals(original, altered)
    }

    @Test fun changingPublisherOrSourceUriChangesDigest() {
        val original = LearningEvidenceDigest.computeSha256(claims(), sources())
        val altered = LearningEvidenceDigest.computeSha256(
            claims(), sources().map { if (it.id == "source-a") it.copy(publisher = "Impersonator") else it }
        )
        assertNotEquals(original, altered)
    }

    @Test fun changingEvidenceStatusOrConfidenceChangesDigest() {
        val original = LearningEvidenceDigest.computeSha256(claims(), sources())
        val altered = LearningEvidenceDigest.computeSha256(
            listOf(claims().single().copy(confidence = 0.84)), sources()
        )
        assertNotEquals(original, altered)
    }
}
