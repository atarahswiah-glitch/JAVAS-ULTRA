package com.javasultra.app.core.learning

import java.time.Instant

enum class LearningJournalState {
    DISCOVERED, QUEUED, RUNNING, SOURCE_REVIEW, KNOWLEDGE_PROPOSED,
    PAUSED, COMPLETED, FAILED, CANCELLED
}

object LearningJournalSafeDetail {
    private val codePattern = Regex("[A-Z][A-Z0-9_]{0,63}")

    /** Accept only stable error/event codes; arbitrary text may contain private data or secrets. */
    fun normalize(value: String?): String? =
        value?.takeIf { codePattern.matches(it) }
}

enum class LearningJournalEventType {
    SIGNAL_DETECTED, TASK_QUEUED, TASK_STARTED, SOURCE_RETRIEVED,
    CLAIM_REVIEWED, KNOWLEDGE_PROPOSED, TASK_PAUSED, TASK_RESUMED,
    TASK_COMPLETED, TASK_FAILED, TASK_CANCELLED
}

data class LearningJournalEntry(
    val id: String,
    val taskId: String,
    val objective: String,
    val domain: LearningDomain,
    val state: LearningJournalState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sourceIds: Set<String> = emptySet(),
    val claimIds: Set<String> = emptySet(),
    val failureCode: String? = null
)

data class LearningJournalEvent(
    val id: String,
    val entryId: String,
    val type: LearningJournalEventType,
    val occurredAt: Instant,
    val actor: String,
    val sourceId: String? = null,
    val claimId: String? = null,
    val safeDetail: String? = null
)

enum class LearningJournalDenial {
    INVALID_ENTRY, INVALID_EVENT, TIME_MOVED_BACKWARDS, INVALID_TRANSITION,
    SOURCE_REQUIRED, CLAIM_REQUIRED, TERMINAL_STATE
}

data class LearningJournalApplyResult(
    val updatedEntry: LearningJournalEntry? = null,
    val denial: LearningJournalDenial? = null
) {
    val accepted: Boolean get() = updatedEntry != null && denial == null
}

object LearningJournalPolicy {
    private val transitions = mapOf(
        LearningJournalState.DISCOVERED to setOf(LearningJournalState.QUEUED, LearningJournalState.CANCELLED),
        LearningJournalState.QUEUED to setOf(LearningJournalState.RUNNING, LearningJournalState.PAUSED, LearningJournalState.CANCELLED),
        LearningJournalState.RUNNING to setOf(LearningJournalState.SOURCE_REVIEW, LearningJournalState.PAUSED, LearningJournalState.FAILED),
        LearningJournalState.SOURCE_REVIEW to setOf(LearningJournalState.KNOWLEDGE_PROPOSED, LearningJournalState.PAUSED, LearningJournalState.FAILED),
        LearningJournalState.KNOWLEDGE_PROPOSED to setOf(LearningJournalState.COMPLETED, LearningJournalState.PAUSED, LearningJournalState.FAILED),
        LearningJournalState.PAUSED to setOf(LearningJournalState.QUEUED, LearningJournalState.CANCELLED),
        LearningJournalState.FAILED to setOf(LearningJournalState.QUEUED, LearningJournalState.CANCELLED),
        LearningJournalState.COMPLETED to emptySet(),
        LearningJournalState.CANCELLED to emptySet()
    )

    fun validateEntry(entry: LearningJournalEntry): LearningJournalDenial? {
        if (entry.id.isBlank() || entry.taskId.isBlank() || entry.objective.isBlank() ||
            entry.updatedAt.isBefore(entry.createdAt)
        ) return LearningJournalDenial.INVALID_ENTRY
        if (entry.failureCode != null && LearningJournalSafeDetail.normalize(entry.failureCode) != entry.failureCode) {
            return LearningJournalDenial.INVALID_ENTRY
        }
        if (entry.sourceIds.any(String::isBlank) || entry.claimIds.any(String::isBlank)) {
            return LearningJournalDenial.INVALID_ENTRY
        }
        return null
    }

    fun validateEvent(entry: LearningJournalEntry, event: LearningJournalEvent): LearningJournalDenial? {
        if (event.id.isBlank() || event.entryId != entry.id || event.actor.isBlank()) {
            return LearningJournalDenial.INVALID_EVENT
        }
        if (event.occurredAt.isBefore(entry.updatedAt)) return LearningJournalDenial.TIME_MOVED_BACKWARDS
        if (entry.state == LearningJournalState.COMPLETED || entry.state == LearningJournalState.CANCELLED) {
            return LearningJournalDenial.TERMINAL_STATE
        }
        if (event.type == LearningJournalEventType.SOURCE_RETRIEVED && event.sourceId.isNullOrBlank()) {
            return LearningJournalDenial.SOURCE_REQUIRED
        }
        if (event.type == LearningJournalEventType.CLAIM_REVIEWED && event.claimId.isNullOrBlank()) {
            return LearningJournalDenial.CLAIM_REQUIRED
        }
        return null
    }

    /**
     * Applies a validated event to an entry. Persistence layers must append the event and
     * replace the entry atomically; this pure policy function never writes storage.
     */
    fun apply(entry: LearningJournalEntry, event: LearningJournalEvent): LearningJournalApplyResult {
        validateEntry(entry)?.let { return LearningJournalApplyResult(denial = it) }
        validateEvent(entry, event)?.let { return LearningJournalApplyResult(denial = it) }

        val target = when (event.type) {
            LearningJournalEventType.SIGNAL_DETECTED ->
                if (entry.state == LearningJournalState.DISCOVERED) entry.state else null
            LearningJournalEventType.TASK_QUEUED ->
                LearningJournalState.QUEUED
            LearningJournalEventType.TASK_STARTED ->
                LearningJournalState.RUNNING
            LearningJournalEventType.SOURCE_RETRIEVED ->
                LearningJournalState.SOURCE_REVIEW
            LearningJournalEventType.CLAIM_REVIEWED ->
                if (entry.state == LearningJournalState.SOURCE_REVIEW) entry.state else null
            LearningJournalEventType.KNOWLEDGE_PROPOSED ->
                LearningJournalState.KNOWLEDGE_PROPOSED
            LearningJournalEventType.TASK_PAUSED ->
                LearningJournalState.PAUSED
            LearningJournalEventType.TASK_RESUMED ->
                if (entry.state == LearningJournalState.PAUSED) LearningJournalState.QUEUED else null
            LearningJournalEventType.TASK_COMPLETED ->
                LearningJournalState.COMPLETED
            LearningJournalEventType.TASK_FAILED ->
                LearningJournalState.FAILED
            LearningJournalEventType.TASK_CANCELLED ->
                LearningJournalState.CANCELLED
        } ?: return LearningJournalApplyResult(denial = LearningJournalDenial.INVALID_TRANSITION)

        // A task may collect multiple independent sources and review multiple claims.
        // These evidence events may repeat without advancing the task state.
        val observationOnlyEvent = event.type == LearningJournalEventType.SIGNAL_DETECTED ||
            event.type == LearningJournalEventType.SOURCE_RETRIEVED ||
            event.type == LearningJournalEventType.CLAIM_REVIEWED
        if ((target == entry.state && !observationOnlyEvent) ||
            (target != entry.state && !canTransition(entry.state, target))
        ) {
            return LearningJournalApplyResult(denial = LearningJournalDenial.INVALID_TRANSITION)
        }

        val updated = entry.copy(
            state = target,
            updatedAt = event.occurredAt,
            sourceIds = event.sourceId?.let { entry.sourceIds + it } ?: entry.sourceIds,
            claimIds = event.claimId?.let { entry.claimIds + it } ?: entry.claimIds,
            failureCode = if (event.type == LearningJournalEventType.TASK_FAILED) {
                LearningJournalSafeDetail.normalize(event.safeDetail)
            } else entry.failureCode
        )
        return LearningJournalApplyResult(updatedEntry = updated)
    }

    fun canTransition(from: LearningJournalState, to: LearningJournalState): Boolean =
        transitions[from]?.contains(to) == true
}

data class LearningSchedule(
    val enabled: Boolean,
    val intervalSeconds: Long,
    val dailyTaskLimit: Int,
    val pausedUntil: Instant? = null
)

enum class LearningScheduleDenial { DISABLED, PAUSED, INVALID_INTERVAL, DAILY_LIMIT_REACHED, NOT_DUE }

object LearningSchedulePolicy {
    fun evaluate(
        schedule: LearningSchedule,
        now: Instant,
        lastRunAt: Instant?,
        tasksCreatedToday: Int
    ): LearningScheduleDenial? {
        if (!schedule.enabled) return LearningScheduleDenial.DISABLED
        if (schedule.pausedUntil?.isAfter(now) == true) return LearningScheduleDenial.PAUSED
        if (schedule.intervalSeconds !in 60..2_592_000 || schedule.dailyTaskLimit !in 1..100 ||
            tasksCreatedToday < 0
        ) return LearningScheduleDenial.INVALID_INTERVAL
        if (tasksCreatedToday >= schedule.dailyTaskLimit) return LearningScheduleDenial.DAILY_LIMIT_REACHED
        if (lastRunAt != null && now.isBefore(lastRunAt.plusSeconds(schedule.intervalSeconds))) {
            return LearningScheduleDenial.NOT_DUE
        }
        return null
    }
}
