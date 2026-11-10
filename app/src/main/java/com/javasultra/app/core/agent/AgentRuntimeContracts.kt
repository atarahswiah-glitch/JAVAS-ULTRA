package com.javasultra.app.core.agent

import java.time.Instant

enum class RuntimeModality { TEXT, IMAGE, AUDIO, LOCATION, SENSOR, FILE }
enum class ToolEffect { READ_ONLY, LOCAL_REVERSIBLE, EXTERNAL_SIDE_EFFECT, PRIVILEGED }
enum class ToolPermission { WEB_PUBLIC_READ, FILE_READ_APPROVED, CAMERA, MICROPHONE, LOCATION, NOTIFICATION, LOCAL_CODE_SANDBOX }

data class ToolDescriptor(
    val id: String,
    val version: String,
    val description: String,
    val acceptedModalities: Set<RuntimeModality>,
    val effect: ToolEffect,
    val requiredPermissions: Set<ToolPermission>,
    val networkHosts: Set<String> = emptySet(),
    val enabled: Boolean = false
)

data class AgentTaskEnvelope(
    val id: String,
    val objective: String,
    val createdAt: Instant,
    val allowedTools: Set<String>,
    val deadline: Instant,
    val maxToolCalls: Int,
    val maxTokensOrWorkUnits: Long,
    val requiresCreatorApproval: Boolean,
    val creatorApprovalGranted: Boolean = false
)

enum class RuntimeDenial { INVALID_TASK, EXPIRED_TASK, TOOL_NOT_ALLOWED, TOOL_DISABLED, PERMISSION_MISSING, PRIVILEGED_ACTION_REQUIRES_APPROVAL, BUDGET_EXCEEDED }

data class RuntimeAuthorizationRequest(
    val task: AgentTaskEnvelope,
    val tool: ToolDescriptor,
    val grantedPermissions: Set<ToolPermission>,
    val callsAlreadyUsed: Int,
    val now: Instant
)

/** Capability preflight. Real tool execution must repeat checks at the trusted host boundary. */
object AgentRuntimePolicy {
    fun evaluate(request: RuntimeAuthorizationRequest): RuntimeDenial? {
        val task = request.task
        val tool = request.tool
        if (task.id.isBlank() || task.objective.isBlank() || task.maxToolCalls !in 1..1000 ||
            task.maxTokensOrWorkUnits <= 0 || !task.deadline.isAfter(task.createdAt)
        ) return RuntimeDenial.INVALID_TASK
        if (!task.deadline.isAfter(request.now)) return RuntimeDenial.EXPIRED_TASK
        if (request.callsAlreadyUsed < 0 || request.callsAlreadyUsed >= task.maxToolCalls) return RuntimeDenial.BUDGET_EXCEEDED
        if (!task.allowedTools.contains(tool.id)) return RuntimeDenial.TOOL_NOT_ALLOWED
        if (!tool.enabled) return RuntimeDenial.TOOL_DISABLED
        if (!request.grantedPermissions.containsAll(tool.requiredPermissions)) return RuntimeDenial.PERMISSION_MISSING
        if ((tool.effect == ToolEffect.PRIVILEGED || tool.effect == ToolEffect.EXTERNAL_SIDE_EFFECT) &&
            (!task.requiresCreatorApproval || !task.creatorApprovalGranted)
        ) return RuntimeDenial.PRIVILEGED_ACTION_REQUIRES_APPROVAL
        return null
    }
}
