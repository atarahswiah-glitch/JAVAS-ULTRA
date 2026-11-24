package com.javasultra.app.core.learning

import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CancellationException

/** Research output is untrusted until it passes LearningEvidenceEvaluator. */
data class LearningResearchBundle(
    val sources: List<ResearchSource>,
    val claims: List<EvidenceClaim>
)

/**
 * Adapter boundary for a permitted, read-only research implementation.
 * Implementations must enforce the supplied domain/source/time budgets and must not
 * access private data, authenticate to sites, bypass paywalls, or perform write actions.
 */
fun interface LearningResearchProvider {
    fun research(
        task: LearningTask,
        allowedDomains: Set<String>,
        maxSources: Int,
        maxDurationSeconds: Long
    ): Result<LearningResearchBundle>
}

interface LearningCycleJournal {
    fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit>
    fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry>
}

class EncryptedLearningCycleJournal(
    private val store: EncryptedLearningJournalStore
) : LearningCycleJournal {
    override fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit> =
        store.create(entry)

    override fun append(
        event: LearningJournalEvent,
        expectedTaskId: String
    ): LearningJournalStoreResult<LearningJournalEntry> =
        store.append(event, expectedTaskId)
}

enum class LearningCycleDenial {
    PLANNING_REJECTED,
    INVALID_TASK,
    JOURNAL_CREATE_FAILED,
    JOURNAL_EVENT_FAILED,
    RESEARCH_FAILED,
    SOURCE_BUDGET_EXCEEDED,
    EVIDENCE_REJECTED
}

sealed interface LearningCycleResult {
    data class ReadyForCreatorReview(
        val task: LearningTask,
        val journalEntry: LearningJournalEntry,
        val sources: List<ResearchSource>,
        val claims: List<EvidenceClaim>,
        val evidenceDigestSha256: String
    ) : LearningCycleResult

    data class Rejected(
        val reason: LearningCycleDenial,
        val planningDenial: PlanningDenial? = null,
        val evidenceDenial: EvidenceEvaluationDenial? = null,
        val journalFailure: LearningJournalStoreFailure? = null
    ) : LearningCycleResult
}

/**
 * Executes one bounded, evidence-gated learning cycle:
 * discovery signal -> queue -> research -> evidence evaluation -> Creator review handoff.
 *
 * It does not perform Creator review, persist/activate semantic memory, or execute external
 * actions. A successful result deliberately leaves the journal in SOURCE_REVIEW until the
 * separate Creator proposal workflow records its decision.
 */
class LearningCycleCoordinator(
    private val researchProvider: LearningResearchProvider,
    private val journal: LearningCycleJournal
) {
    fun run(
        signal: LearningSignal,
        settings: AutonomousLearningSettings,
        now: Instant,
        tasksCreatedToday: Int,
        existingSignalIds: Set<String> = emptySet()
    ): LearningCycleResult {
        val plan = AutonomousLearningPlanner.evaluate(
            signal, settings, now, tasksCreatedToday, existingSignalIds
        )
        if (!plan.approvedForQueue) {
            return LearningCycleResult.Rejected(
                LearningCycleDenial.PLANNING_REJECTED,
                planningDenial = plan.denial
            )
        }

        val taskId = stableId("task", signal.id)
        val journalId = stableId("journal", signal.id)
        val task = LearningTask(
            id = taskId,
            objective = signal.topic,
            domain = signal.domain,
            createdAt = signal.detectedAt,
            state = LearningTaskState.RESEARCHING,
            requestedByCreator = false,
            sourceBudget = settings.maxSourcesPerTask,
            timeBudgetSeconds = settings.maxTaskDuration.seconds.coerceAtLeast(1),
            privacySafe = true
        )
        if (LearningTaskPolicy.validate(task) != null) {
            return LearningCycleResult.Rejected(LearningCycleDenial.INVALID_TASK)
        }

        val initialEntry = LearningJournalEntry(
            id = journalId,
            taskId = task.id,
            objective = task.objective,
            domain = task.domain,
            state = LearningJournalState.DISCOVERED,
            createdAt = signal.detectedAt,
            updatedAt = signal.detectedAt
        )
        when (val created = journal.create(initialEntry)) {
            is LearningJournalStoreResult.Failure ->
                return LearningCycleResult.Rejected(
                    LearningCycleDenial.JOURNAL_CREATE_FAILED,
                    journalFailure = created.reason
                )
            is LearningJournalStoreResult.Success -> Unit
        }

        var currentEntry = initialEntry
        var journalFailure: LearningJournalStoreFailure? = null
        fun append(type: LearningJournalEventType, suffix: String, sourceId: String? = null, claimId: String? = null): LearningCycleDenial? {
            val event = LearningJournalEvent(
                id = stableId("event", "${task.id}:$suffix"),
                entryId = journalId,
                type = type,
                occurredAt = now,
                actor = LEARNING_AGENT_ACTOR,
                sourceId = sourceId,
                claimId = claimId
            )
            return when (val result = journal.append(event, task.id)) {
                is LearningJournalStoreResult.Success -> {
                    currentEntry = result.value
                    null
                }
                is LearningJournalStoreResult.Failure -> {
                    journalFailure = result.reason
                    LearningCycleDenial.JOURNAL_EVENT_FAILED
                }
            }
        }

        append(LearningJournalEventType.TASK_QUEUED, "queued")?.let {
            return LearningCycleResult.Rejected(it, journalFailure = journalFailure)
        }
        append(LearningJournalEventType.TASK_STARTED, "started")?.let {
            return LearningCycleResult.Rejected(it, journalFailure = journalFailure)
        }

        val bundle = try {
            researchProvider.research(
                task,
                settings.allowedDomains,
                settings.maxSourcesPerTask,
                task.timeBudgetSeconds
            ).getOrElse {
                return fail(task, currentEntry, now, "RESEARCH_FAILED", LearningCycleDenial.RESEARCH_FAILED)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return fail(task, currentEntry, now, "RESEARCH_FAILED", LearningCycleDenial.RESEARCH_FAILED)
        }

        if (bundle.sources.size > task.sourceBudget) {
            return fail(task, currentEntry, now, "SOURCE_BUDGET_EXCEEDED", LearningCycleDenial.SOURCE_BUDGET_EXCEEDED)
        }
        for (source in bundle.sources) {
            append(
                LearningJournalEventType.SOURCE_RETRIEVED,
                "source:${source.id}",
                sourceId = source.id
            )?.let {
                return LearningCycleResult.Rejected(it, journalFailure = journalFailure)
            }
        }

        val evaluatingTask = task.copy(state = LearningTaskState.EVALUATING)
        val evaluation = LearningEvidenceEvaluator.evaluate(
            evaluatingTask,
            bundle.sources,
            bundle.claims,
            now
        )
        if (!evaluation.readyForCreatorReview) {
            return fail(
                task,
                currentEntry,
                now,
                "EVIDENCE_REJECTED",
                LearningCycleDenial.EVIDENCE_REJECTED,
                evaluation.denial
            )
        }

        for (claim in bundle.claims) {
            append(
                LearningJournalEventType.CLAIM_REVIEWED,
                "claim:${claim.id}",
                claimId = claim.id
            )?.let {
                return LearningCycleResult.Rejected(it, journalFailure = journalFailure)
            }
        }

        val digest = try {
            LearningEvidenceDigest.computeSha256(bundle.claims, bundle.sources)
        } catch (_: IllegalArgumentException) {
            return fail(task, currentEntry, now, "EVIDENCE_REJECTED", LearningCycleDenial.EVIDENCE_REJECTED)
        }

        return LearningCycleResult.ReadyForCreatorReview(
            task = task.copy(state = LearningTaskState.READY_FOR_REVIEW),
            journalEntry = currentEntry,
            sources = bundle.sources,
            claims = bundle.claims,
            evidenceDigestSha256 = digest
        )
    }

    private fun fail(
        task: LearningTask,
        entry: LearningJournalEntry,
        now: Instant,
        code: String,
        reason: LearningCycleDenial,
        evidenceDenial: EvidenceEvaluationDenial? = null
    ): LearningCycleResult {
        val event = LearningJournalEvent(
            id = stableId("event", "${task.id}:failed:$code"),
            entryId = entry.id,
            type = LearningJournalEventType.TASK_FAILED,
            occurredAt = now,
            actor = LEARNING_AGENT_ACTOR,
            safeDetail = code
        )
        return when (val appended = journal.append(event, task.id)) {
            is LearningJournalStoreResult.Success ->
                LearningCycleResult.Rejected(reason, evidenceDenial = evidenceDenial)
            is LearningJournalStoreResult.Failure ->
                LearningCycleResult.Rejected(
                    LearningCycleDenial.JOURNAL_EVENT_FAILED,
                    evidenceDenial = evidenceDenial,
                    journalFailure = appended.reason
                )
        }
    }

    private fun stableId(prefix: String, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$prefix-$digest"
    }

    private companion object {
        const val LEARNING_AGENT_ACTOR = "javas-learning-agent"
    }
}
