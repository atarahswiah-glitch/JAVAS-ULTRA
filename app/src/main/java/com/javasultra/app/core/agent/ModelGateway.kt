package com.javasultra.app.core.agent

import java.time.Instant

enum class ModelProviderKind { ON_DEVICE, APPROVED_CLOUD, SELF_HOSTED }
enum class DataClassification { PUBLIC, INTERNAL, CREATOR_PRIVATE, HIGHLY_SENSITIVE }

data class ModelProviderDescriptor(
    val id: String,
    val kind: ModelProviderKind,
    val supportedModalities: Set<RuntimeModality>,
    val supportedTools: Boolean,
    val maxInputUnits: Int,
    val maxOutputUnits: Int,
    val acceptedData: Set<DataClassification>,
    val enabled: Boolean
)

data class ModelRequest(
    val taskId: String,
    val prompt: String,
    val modalities: Set<RuntimeModality>,
    val dataClassification: DataClassification,
    val requestedToolSupport: Boolean,
    val maxOutputUnits: Int,
    val deadline: Instant
)

data class ProposedToolCall(
    val proposalId: String,
    val toolId: String,
    val arguments: Map<String, String>
)

data class ModelResponse(
    val text: String,
    val proposedToolCalls: List<ProposedToolCall>,
    val inputUnitsUsed: Int,
    val outputUnitsUsed: Int,
    val completedAt: Instant
)

enum class ModelRouteDenial {
    INVALID_REQUEST, NO_COMPATIBLE_PROVIDER, CLOUD_NOT_ALLOWED_OFFLINE,
    DATA_CLASSIFICATION_NOT_ACCEPTED, TOOL_SUPPORT_MISSING
}

/** Routing preflight; it never sends data and never executes model-proposed tools. */
object ModelRoutePolicy {
    fun select(
        request: ModelRequest,
        providers: List<ModelProviderDescriptor>,
        offline: Boolean,
        now: Instant
    ): ModelProviderDescriptor? {
        if (request.taskId.isBlank() || request.prompt.isBlank() ||
            request.maxOutputUnits !in 1..100_000 || !request.deadline.isAfter(now)
        ) return null
        return providers.asSequence()
            .filter { it.enabled && it.id.isNotBlank() }
            .filter { request.modalities.all(it.supportedModalities::contains) }
            .filter { request.maxOutputUnits <= it.maxOutputUnits }
            .filter { request.dataClassification in it.acceptedData }
            .filter { !request.requestedToolSupport || it.supportedTools }
            .filter { !offline || it.kind == ModelProviderKind.ON_DEVICE }
            .sortedWith(
                compareBy<ModelProviderDescriptor> { privacyPreference(it, request.dataClassification) }
                    .thenBy { it.kind.ordinal }
                    .thenBy { it.id }
            )
            .firstOrNull()
    }

    private fun privacyPreference(provider: ModelProviderDescriptor, classification: DataClassification): Int =
        if (classification >= DataClassification.CREATOR_PRIVATE && provider.kind == ModelProviderKind.ON_DEVICE) 0 else 1
}

/**
 * Provider adapter boundary. Implementations must apply provider-specific privacy, timeout,
 * cancellation, response-size and credential handling. Tool proposals are data only.
 */
interface ModelProviderAdapter {
    val descriptor: ModelProviderDescriptor
    suspend fun generate(request: ModelRequest): Result<ModelResponse>
}

interface ModelGateway {
    suspend fun generate(request: ModelRequest, offline: Boolean, now: Instant): Result<ModelResponse>
}
