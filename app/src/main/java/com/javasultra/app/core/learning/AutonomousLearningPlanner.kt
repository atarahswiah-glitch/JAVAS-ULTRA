package com.javasultra.app.core.learning

import java.time.Instant
import java.time.Duration

enum class DiscoveryTrigger { CAPABILITY_GAP, STALE_KNOWLEDGE, USER_INTEREST, NEW_FIELD_DISCOVERY, SCHEDULED_REVIEW }
enum class LearningPriority { LOW, NORMAL, HIGH }
enum class AutonomyPermission { PUBLIC_WEB_READ, PUBLIC_DOCUMENTATION_READ, APPROVED_FILE_READ }

data class LearningSignal(
    val id: String,
    val topic: String,
    val domain: LearningDomain,
    val trigger: DiscoveryTrigger,
    val detectedAt: Instant,
    val priority: LearningPriority,
    val involvesPrivateData: Boolean = false
)

data class AutonomousLearningSettings(
    val enabled: Boolean,
    val permissions: Set<AutonomyPermission>,
    val maxTasksPerDay: Int,
    val maxSourcesPerTask: Int,
    val maxTaskDuration: Duration,
    val allowedDomains: Set<String>,
    val pausedUntil: Instant? = null
)

enum class PlanningDenial { DISABLED, PAUSED, PRIVATE_DATA, INVALID_LIMITS, NO_PERMISSION, DUPLICATE_OR_EMPTY_TOPIC }

data class LearningPlanDecision(val approvedForQueue: Boolean, val denial: PlanningDenial? = null)

/**
 * Decides whether a discovery signal may enter the bounded learning queue.
 * It does not fetch websites, execute tools or persist knowledge.
 */
object AutonomousLearningPlanner {
    fun evaluate(
        signal: LearningSignal,
        settings: AutonomousLearningSettings,
        now: Instant,
        tasksCreatedToday: Int
    ): LearningPlanDecision {
        if (!settings.enabled) return denied(PlanningDenial.DISABLED)
        if (settings.pausedUntil?.isAfter(now) == true) return denied(PlanningDenial.PAUSED)
        if (signal.involvesPrivateData) return denied(PlanningDenial.PRIVATE_DATA)
        if (signal.id.isBlank() || signal.topic.isBlank()) return denied(PlanningDenial.DUPLICATE_OR_EMPTY_TOPIC)
        if (settings.maxTasksPerDay !in 1..100 || settings.maxSourcesPerTask !in 1..100 ||
            settings.maxTaskDuration.isNegative || settings.maxTaskDuration.isZero ||
            settings.maxTaskDuration > Duration.ofHours(24) ||
            tasksCreatedToday < 0 || tasksCreatedToday >= settings.maxTasksPerDay
        ) return denied(PlanningDenial.INVALID_LIMITS)
        if (settings.permissions.none { it == AutonomyPermission.PUBLIC_WEB_READ || it == AutonomyPermission.PUBLIC_DOCUMENTATION_READ }) {
            return denied(PlanningDenial.NO_PERMISSION)
        }
        return LearningPlanDecision(true)
    }

    private fun denied(reason: PlanningDenial) = LearningPlanDecision(false, reason)
}
