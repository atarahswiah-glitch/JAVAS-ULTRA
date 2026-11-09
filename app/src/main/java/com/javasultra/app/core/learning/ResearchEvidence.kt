package com.javasultra.app.core.learning

import java.time.Instant

enum class EvidenceSourceKind { PRIMARY_RESEARCH, OFFICIAL_DOCUMENTATION, ACADEMIC_REVIEW, REPUTABLE_REFERENCE, NEWS, USER_PROVIDED, SENSOR, MODEL_OUTPUT }
enum class ClaimStatus { CANDIDATE, SUPPORTED, DISPUTED, REJECTED, STALE }
enum class ConfidenceBand { LOW, MODERATE, HIGH }

data class ResearchSource(
    val id: String,
    val kind: EvidenceSourceKind,
    val title: String,
    val publisher: String?,
    val uri: String?,
    val publishedAt: Instant?,
    val retrievedAt: Instant,
    val trustScore: Double
)

data class EvidenceClaim(
    val id: String,
    val statement: String,
    val sourceIds: Set<String>,
    val supportingSourceIds: Set<String>,
    val contradictingSourceIds: Set<String>,
    val createdAt: Instant,
    val reviewedAt: Instant?,
    val status: ClaimStatus,
    val confidence: Double
) {
    fun validate(knownSources: Set<String>): List<ResearchValidationIssue> = buildList {
        if (id.isBlank()) add(ResearchValidationIssue.EMPTY_ID)
        if (statement.isBlank()) add(ResearchValidationIssue.EMPTY_STATEMENT)
        if (confidence !in 0.0..1.0) add(ResearchValidationIssue.CONFIDENCE_OUT_OF_RANGE)
        if (sourceIds.isEmpty()) add(ResearchValidationIssue.NO_PROVENANCE)
        if (!knownSources.containsAll(sourceIds)) add(ResearchValidationIssue.UNKNOWN_SOURCE)
        if (!sourceIds.containsAll(supportingSourceIds + contradictingSourceIds)) {
            add(ResearchValidationIssue.EVIDENCE_NOT_IN_SOURCE_SET)
        }
        if (reviewedAt != null && reviewedAt.isBefore(createdAt)) add(ResearchValidationIssue.REVIEW_BEFORE_CREATION)
        if (supportingSourceIds.isNotEmpty() && contradictingSourceIds.isNotEmpty() && status == ClaimStatus.SUPPORTED) {
            add(ResearchValidationIssue.CONTRADICTION_NEEDS_REVIEW)
        }
    }

    fun confidenceBand(): ConfidenceBand = when {
        confidence >= 0.8 -> ConfidenceBand.HIGH
        confidence >= 0.5 -> ConfidenceBand.MODERATE
        else -> ConfidenceBand.LOW
    }
}

enum class ResearchValidationIssue {
    EMPTY_ID, EMPTY_STATEMENT, CONFIDENCE_OUT_OF_RANGE, NO_PROVENANCE,
    UNKNOWN_SOURCE, EVIDENCE_NOT_IN_SOURCE_SET, REVIEW_BEFORE_CREATION,
    CONTRADICTION_NEEDS_REVIEW
}
