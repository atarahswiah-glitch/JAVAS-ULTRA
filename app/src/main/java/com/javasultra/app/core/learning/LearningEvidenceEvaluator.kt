package com.javasultra.app.core.learning

import java.time.Duration
import java.time.Instant

enum class EvidenceEvaluationDenial {
    INVALID_TASK,
    INVALID_FRESHNESS_WINDOW,
    NO_CLAIMS,
    INVALID_CLAIM,
    CONTRADICTIONS_REQUIRE_REVIEW,
    MODEL_OUTPUT_CANNOT_BE_EVIDENCE,
    STALE_OR_FUTURE_SOURCE,
    INSUFFICIENT_INDEPENDENT_SOURCES
}

data class EvidenceEvaluationDecision(
    val readyForCreatorReview: Boolean,
    val denial: EvidenceEvaluationDenial? = null,
    val reviewedClaimIds: Set<String> = emptySet()
)

/**
 * Conservative evidence gate. Passing this gate only queues material for Creator review;
 * it never marks claims as trusted, completes a task, or activates memory.
 */
object LearningEvidenceEvaluator {
    fun evaluate(
        task: LearningTask,
        sources: List<ResearchSource>,
        claims: List<EvidenceClaim>,
        now: Instant,
        maxEvidenceAge: Duration = Duration.ofDays(365),
        minimumIndependentSources: Int = 2
    ): EvidenceEvaluationDecision {
        if (LearningTaskPolicy.validate(task) != null || task.state != LearningTaskState.EVALUATING) {
            return denied(EvidenceEvaluationDenial.INVALID_TASK)
        }
        if (maxEvidenceAge.isNegative || maxEvidenceAge.isZero || minimumIndependentSources !in 1..10) {
            return denied(EvidenceEvaluationDenial.INVALID_FRESHNESS_WINDOW)
        }
        if (claims.isEmpty()) return denied(EvidenceEvaluationDenial.NO_CLAIMS)

        val sourceMap = sources.associateBy { it.id }
        val knownIds = sourceMap.keys
        for (claim in claims) {
            if (claim.validate(knownIds).isNotEmpty()) return denied(EvidenceEvaluationDenial.INVALID_CLAIM)
            if (claim.contradictingSourceIds.isNotEmpty()) {
                return denied(EvidenceEvaluationDenial.CONTRADICTIONS_REQUIRE_REVIEW)
            }
            val cited = claim.supportingSourceIds.mapNotNull(sourceMap::get)
            if (cited.any { it.kind == EvidenceSourceKind.MODEL_OUTPUT }) {
                return denied(EvidenceEvaluationDenial.MODEL_OUTPUT_CANNOT_BE_EVIDENCE)
            }
            if (cited.any {
                    it.retrievedAt.isAfter(now) ||
                        it.retrievedAt.isBefore(now.minus(maxEvidenceAge))
                }) {
                return denied(EvidenceEvaluationDenial.STALE_OR_FUTURE_SOURCE)
            }
            val independentPublishers = cited.mapNotNull { it.publisher?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
                .toSet()
            if (independentPublishers.size < minimumIndependentSources) {
                return denied(EvidenceEvaluationDenial.INSUFFICIENT_INDEPENDENT_SOURCES)
            }
        }
        return EvidenceEvaluationDecision(true, reviewedClaimIds = claims.map { it.id }.toSet())
    }

    private fun denied(reason: EvidenceEvaluationDenial) = EvidenceEvaluationDecision(false, reason)
}
