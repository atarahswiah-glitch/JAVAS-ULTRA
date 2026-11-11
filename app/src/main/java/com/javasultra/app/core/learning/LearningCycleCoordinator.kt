package com.javasultra.app.core.learning

import java.time.Instant

/**
 * Adapter that inspects existing capability/knowledge evidence and confirms whether
 * a signal represents a real gap. Implementations must not infer competence from
 * unverified model output.
 */
fun interface LearningGapInspectionPort {
    fun inspect(signal: LearningSignal): LearningGapInspection
}

data class LearningGapInspection(
    val isGap: Boolean,
    val rationale: String
)

/**
 * Adapter over the existing public research/discovery architecture. Implementations
 * must enforce PublicResearchGateway, domain allowlists, redirect/IP checks, byte/time
 * limits, robots policy and content validation. This coordinator performs no networking.
 */
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
    REVIEW_PACKET_MISMATCH
}

sealed interface LearningCycleResult {
    data class ReadyForCreatorReview(
        val task: LearningTask,
        val rationale: String,
        val sources: List<ResearchSource>,
        val claims: List<EvidenceClaim>,
        val evidenceDigestSha256: String
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
 * First end-to-end orchestration boundary for inspect -> plan -> discover -> evaluate
 * -> Creator authorize -> propose memory.
 *
 * Research and gap inspection are injected adapters, not fake implementations. This
 * class never fetches the network, executes practice code, activates memory, or treats
 * model output as evidence. Creator review remains an explicit second call.
 */
class LearningCycleCoordinator(
    private val gapInspector: LearningGapInspectionPort,
    private val resourceDiscovery: LearningResourceDiscoveryPort,
    private val proposalWorkflow: LearningMemoryProposalWorkflow
) {
    fun inspectPlanResearchAndEvaluate(
        signal: LearningSignal,
        settings: AutonomousLearningSettings,
        now: Instant,
        tasksCreatedToday: Int,
        existingSignalIds: Set<String> = emptySet()
    ): LearningCycleResult {
        // Apply privacy, permission, pause and resource-budget policy before inspecting
        // a signal. In particular, private-data signals must not reach an adapter.
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

        val bundle = resourceDiscovery.discover(task, settings.maxSourcesPerTask)
        if (bundle.sources.isEmpty() || bundle.claims.isEmpty()) {
            return LearningCycleResult.Denied(LearningCycleDenial.RESEARCH_EMPTY)
        }
        if (bundle.sources.size > settings.maxSourcesPerTask) {
            return LearningCycleResult.Denied(LearningCycleDenial.RESEARCH_BUDGET_EXCEEDED)
        }

        val evidence = LearningEvidenceEvaluator.evaluate(
            task = task,
            sources = bundle.sources,
            claims = bundle.claims,
            now = now
        )
        if (!evidence.readyForCreatorReview) {
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
            evidenceDigestSha256 = digest
        )
    }

    /**
     * A review packet is bound to the exact task, claim IDs and evidence digest produced
     * by this coordinator. The existing workflow performs Creator identity/step-up
     * preflight and persists PROPOSED records only. Trusted server-side authorization
     * and atomic replay protection remain production requirements.
     */
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
            !request.submittedEvidenceDigestSha256.equals(packet.evidenceDigestSha256, true)
        ) return LearningCycleAuthorizationResult.Denied(LearningCycleDenial.REVIEW_PACKET_MISMATCH)

        return LearningCycleAuthorizationResult.ProposalResult(
            proposalWorkflow.approveAndPersist(
                request = request,
                consumedReviewIds = consumedReviewIds,
                claims = packet.claims,
                sources = packet.sources,
                creatorPrincipalId = creatorPrincipalId,
                journalEntryId = journalEntryId,
                createdAt = now
            )
        )
    }
}
