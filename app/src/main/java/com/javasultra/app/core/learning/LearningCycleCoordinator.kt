package com.javasultra.app.core.learning

import java.security.MessageDigest
import java.time.Instant

fun interface LearningGapInspectionPort {
    fun inspect(signal: LearningSignal): LearningGapInspection
}

data class LearningGapInspection(
    val isGap: Boolean,
    val rationale: String
}

/** Implementations must enforce the public-research gateway's network and privacy policy. */
fun interface LearningResourceDiscoveryPort {
    fun discover(task: LearningTask, maxSources: Int): LearningResearchBundle
}

data class LearningResearchBundle(
    val sources: List<ResearchSource>,
    val claims: List<EvidenceClaim>
)

enum class LearningCycleDenial {
    NOT_A_KNOWLEDGE_GAP,
    PLANNING_REJECTED,
    INVALID_TASK,
    RESEARCH_EMPTY,
    RESEARCH_BUDGET_EXCEEDED,
    EVIDENCE_REJECTED,
    REVIEW_PACKET_MISMATCH,
    JOURNAL_PERSISTENCE_FAILED
}

sealed interface LearningCycleResult {
    data class ReadyForCreatorReview(
        val task: LearningTask,
        val rationale: String,
        val sources: List<ResearchSource>,
        val claims: List<EvidenceClaim>,
        val evidenceDigestSha256: String,
        val journalEntryId: String
    ) : LearningCycleResult

    data class Denied(
        val reason: LearningCycleDenial,
        val planningDenial: PlanningDenial? = null,
        val evidenceDenial: EvidenceEvaluationDenial? = null
    ) : LearningCycleResult
}

sealed interface LearningCycleAuthorizationResult {
    data class ProposalResult(val result: LearningProposalWorkflowResult) : LearningCycleAuthorizationResult
    data class Denied(val reason: LearningCycleDenial) : LearningCycleAuthorizationResult
}

/**
 * Orchestrates inspect -> plan -> discover -> evaluate -> Creator authorize -> propose.
 * Every accepted cycle stage is recorded in the encrypted journal before it is exposed
 * as ready for review. This class never performs networking or practice execution.
 */
class LearningCycleCoordinator(
    private val gapInspector: LearningGapInspectionPort,
    private val resourceDiscovery: LearningResourceDiscoveryPort,
    private val proposalWorkflow: LearningMemoryProposalWorkflow,
    private val journal: LearningCycleJournalPort
) {
    fun inspectPlanResearchAndEvaluate(
        signal: LearningSignal,
        settings: AutonomousLearningSettings,
        now: Instant,
        tasksCreatedToday: Int,
        existingSignalIds: Set<String> = emptySet()
    ): LearningCycleResult {
        val planning = AutonomousLearningPlanner.evaluate(
            signal = signal,
            settings = settings,
            now = now,
            tasksCreatedToday = tasksCreatedToday,
            existingSignalIds = existingSignalIds
        )
        if (!planning.approvedForQueue) {
            return LearningCycleResult.Denied(
                LearningCycleDenial.PLANNING_REJECTED,
                planningDenial = planning.denial
            )
        }

        val inspection = gapInspector.inspect(signal)
        if (!inspection.isGap) return LearningCycleResult.Denied(LearningCycleDenial.NOT_A_KNOWLEDGE_GAP)

        val task = LearningTask(
            id = signal.id,
            objective = signal.topic,
            domain = signal.domain,
            createdAt = now,
            state = LearningTaskState.EVALUATING,
            requestedByCreator = false,
            sourceBudget = settings.maxSourcesPerTask,
            timeBudgetSeconds = settings.maxTaskDuration.seconds.coerceAtLeast(1),
            privacySafe = true
        )
        if (LearningTaskPolicy.validate(task) != null) {
            return LearningCycleResult.Denied(LearningCycleDenial.INVALID_TASK)
        }

        val journalEntryId = signal.id
        val entry = LearningJournalEntry(
            id = journalEntryId,
            taskId = task.id,
            objective = task.objective,
            domain = task.domain,
            state = LearningJournalState.DISCOVERED,
            createdAt = now,
            updatedAt = now
        )
        if (journal.create(entry) !is LearningJournalStoreResult.Success) {
            return LearningCycleResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)
        }
        if (!record(journalEntryId, signal.id, "signal", LearningJournalEventType.SIGNAL_DETECTED, now) ||
            !record(journalEntryId, signal.id, "queued", LearningJournalEventType.TASK_QUEUED, now) ||
            !record(journalEntryId, signal.id, "started", LearningJournalEventType.TASK_STARTED, now)
        ) return LearningCycleResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)

        val bundle = try {
            resourceDiscovery.discover(task, settings.maxSourcesPerTask)
        } catch (_: Exception) {
            failTask(journalEntryId, task.id, "RESEARCH_ADAPTER_FAILED", now)
            return LearningCycleResult.Denied(LearningCycleDenial.RESEARCH_EMPTY)
        }

        if (bundle.sources.isEmpty() || bundle.claims.isEmpty()) {
            failTask(journalEntryId, task.id, "RESEARCH_EMPTY", now)
            return LearningCycleResult.Denied(LearningCycleDenial.RESEARCH_EMPTY)
        }
        if (bundle.sources.size > settings.maxSourcesPerTask) {
            failTask(journalEntryId, task.id, "SOURCE_BUDGET_EXCEEDED", now)
            return LearningCycleResult.Denied(LearningCycleDenial.RESEARCH_BUDGET_EXCEEDED)
        }

        for (source in bundle.sources) {
            if (!record(
                    journalEntryId, task.id, "source-${stableId(source.id)}",
                    LearningJournalEventType.SOURCE_RETRIEVED, now, sourceId = source.id
                )
            ) return LearningCycleResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)
        }
        for (claim in bundle.claims) {
            if (!record(
                    journalEntryId, task.id, "claim-${stableId(claim.id)}",
                    LearningJournalEventType.CLAIM_REVIEWED, now, claimId = claim.id
                )
            ) return LearningCycleResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)
        }

        val evidence = LearningEvidenceEvaluator.evaluate(
            task = task,
            sources = bundle.sources,
            claims = bundle.claims,
            now = now
        )
        if (!evidence.readyForCreatorReview) {
            failTask(journalEntryId, task.id, "EVIDENCE_REJECTED", now)
            return LearningCycleResult.Denied(
                LearningCycleDenial.EVIDENCE_REJECTED,
                evidenceDenial = evidence.denial
            )
        }

        val reviewTask = task.copy(state = LearningTaskState.READY_FOR_REVIEW)
        val digest = LearningEvidenceDigest.computeSha256(bundle.claims, bundle.sources)
        return LearningCycleResult.ReadyForCreatorReview(
            task = reviewTask,
            rationale = inspection.rationale,
            sources = bundle.sources,
            claims = bundle.claims,
            evidenceDigestSha256 = digest,
            journalEntryId = journalEntryId
        )
    }

    fun authorizeAndPropose(
        packet: LearningCycleResult.ReadyForCreatorReview,
        request: CreatorLearningReviewRequest,
        consumedReviewIds: Set<String>,
        creatorPrincipalId: String,
        journalEntryId: String,
        now: Instant
    ): LearningCycleAuthorizationResult {
        if (request.task != packet.task ||
            request.creatorPrincipalId != creatorPrincipalId ||
            request.claimIds != packet.claims.map { it.id }.toSet() ||
            !request.expectedEvidenceDigestSha256.equals(packet.evidenceDigestSha256, true) ||
            !request.submittedEvidenceDigestSha256.equals(packet.evidenceDigestSha256, true) ||
            journalEntryId != packet.journalEntryId
        ) return LearningCycleAuthorizationResult.Denied(LearningCycleDenial.REVIEW_PACKET_MISMATCH)

        val review = CreatorLearningReviewPolicy.evaluate(request, consumedReviewIds, now)
        if (review.denial == null && review.outcome == CreatorReviewOutcome.APPROVED_FOR_PROPOSAL_ONLY) {
            if (!record(
                    packet.journalEntryId, packet.task.id, "knowledge-proposed",
                    LearningJournalEventType.KNOWLEDGE_PROPOSED, now
                )
            ) return LearningCycleAuthorizationResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)
        } else if (review.denial == null && review.outcome == CreatorReviewOutcome.REJECTED) {
            if (!failTask(packet.journalEntryId, packet.task.id, "CREATOR_REJECTED", now)) {
                return LearningCycleAuthorizationResult.Denied(LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED)
            }
        }

        val result = proposalWorkflow.approveAndPersist(
            request = request,
            consumedReviewIds = consumedReviewIds,
            claims = packet.claims,
            sources = packet.sources,
            creatorPrincipalId = creatorPrincipalId,
            journalEntryId = journalEntryId,
            createdAt = now
        )
        if (result is LearningProposalWorkflowResult.Rejected &&
            review.outcome == CreatorReviewOutcome.APPROVED_FOR_PROPOSAL_ONLY
        ) {
            failTask(packet.journalEntryId, packet.task.id, "PROPOSAL_PERSISTENCE_FAILED", now)
        }
        return LearningCycleAuthorizationResult.ProposalResult(result)
    }

    private fun record(
        entryId: String,
        taskId: String,
        stage: String,
        type: LearningJournalEventType,
        at: Instant,
        sourceId: String? = null,
        claimId: String? = null
    ): Boolean {
        val event = LearningJournalEvent(
            id = "cycle-${stableId(entryId)}-$stage",
            entryId = entryId,
            type = type,
            occurredAt = at,
            actor = SYSTEM_ACTOR,
            sourceId = sourceId,
            claimId = claimId
        )
        return journal.append(event, taskId) is LearningJournalStoreResult.Success
    }

    private fun failTask(entryId: String, taskId: String, code: String, at: Instant): Boolean =
        record(entryId, taskId, "failed-$code", LearningJournalEventType.TASK_FAILED, at)
    
    private fun stableId(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SYSTEM_ACTOR = "JAVAS_LEARNING_ENGINE"
    }
}
