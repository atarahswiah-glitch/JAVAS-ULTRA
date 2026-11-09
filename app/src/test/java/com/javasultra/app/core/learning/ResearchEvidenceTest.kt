package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ResearchEvidenceTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")

    @Test fun rejectsClaimWithoutSourceProvenance() {
        val claim = EvidenceClaim("c1", "A claim", emptySet(), emptySet(), emptySet(), now, null, ClaimStatus.CANDIDATE, 0.4)
        assertTrue(claim.validate(emptySet()).contains(ResearchValidationIssue.NO_PROVENANCE))
    }

    @Test fun rejectsUnknownEvidenceSource() {
        val claim = EvidenceClaim("c1", "A claim", setOf("missing"), setOf("missing"), emptySet(), now, null, ClaimStatus.CANDIDATE, 0.4)
        assertTrue(claim.validate(emptySet()).contains(ResearchValidationIssue.UNKNOWN_SOURCE))
    }

    @Test fun flagsContradictoryClaimMarkedSupported() {
        val claim = EvidenceClaim("c1", "A claim", setOf("a", "b"), setOf("a"), setOf("b"), now, now, ClaimStatus.SUPPORTED, 0.9)
        assertTrue(claim.validate(setOf("a", "b")).contains(ResearchValidationIssue.CONTRADICTION_NEEDS_REVIEW))
    }

    @Test fun confidenceBandIsTransparent() {
        val claim = EvidenceClaim("c1", "A claim", setOf("a"), setOf("a"), emptySet(), now, null, ClaimStatus.SUPPORTED, 0.85)
        assertEquals(ConfidenceBand.HIGH, claim.confidenceBand())
    }
}
