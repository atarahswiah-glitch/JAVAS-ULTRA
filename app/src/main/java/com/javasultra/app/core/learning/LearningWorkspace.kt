package com.javasultra.app.core.learning

import java.time.Instant

enum class LearningTaskState { PROPOSED, APPROVED, RESEARCHING, EVALUATING, READY_FOR_REVIEW, COMPLETED, PAUSED, REJECTED }
enum class LearningDomain { GENERAL, SCIENCE, MATHEMATICS, GEOGRAPHY, COMPUTING, ENGINEERING, SECURITY, LANGUAGE, CREATOR_PROJECT }

data class LearningTask(
    val id: String,
    val objective: String,
    val domain: LearningDomain,
    val createdAt: Instant,
    val state: LearningTaskState,
    val requestedByCreator: Boolean,
    val sourceBudget: Int,
    val timeBudgetSeconds: Long,
    val privacySafe: Boolean
)

enum class LearningTaskDenial { EMPTY_OBJECTIVE, INVALID_BUDGET, PRIVATE_DATA_NOT_APPROVED, CREATOR_APPROVAL_REQUIRED }

object LearningTaskPolicy {
    fun validate(task: LearningTask): LearningTaskDenial? {
        if (task.id.isBlank() || task.objective.isBlank()) return LearningTaskDenial.EMPTY_OBJECTIVE
        if (task.sourceBudget !in 1..100 || task.timeBudgetSeconds !in 1..86_400) return LearningTaskDenial.INVALID_BUDGET
        if (!task.privacySafe) return LearningTaskDenial.PRIVATE_DATA_NOT_APPROVED
        return null
    }
}
