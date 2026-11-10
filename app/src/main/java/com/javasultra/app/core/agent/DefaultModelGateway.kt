package com.javasultra.app.core.agent

import java.time.Instant
import java.util.concurrent.CancellationException

/**
 * Provider-neutral gateway. It selects only registered adapters and treats model tool calls
 * as untrusted proposals. Host-side authorization is still required before any execution.
 *
 * Adapter calls remain cooperative: adapters must honour coroutine cancellation and enforce
 * their own transport timeout. This class validates the deadline both before and after the call.
 */
class DefaultModelGateway(adapters: List<ModelProviderAdapter>) : ModelGateway {
    private val adaptersById: Map<String, ModelProviderAdapter>

    init {
        require(adapters.all { it.descriptor.id.isNotBlank() }) { "Provider IDs must not be blank" }
        require(adapters.map { it.descriptor.id }.distinct().size == adapters.size) {
            "Provider IDs must be unique"
        }
        adaptersById = adapters.associateBy { it.descriptor.id }
    }

    override suspend fun generate(
        request: ModelRequest,
        offline: Boolean,
        now: Instant
    ): Result<ModelResponse> {
        val provider = ModelRoutePolicy.select(request, adaptersById.values.map { it.descriptor }, offline, now)
            ?: return Result.failure(IllegalStateException("No compatible model provider"))
        val adapter = adaptersById[provider.id]
            ?: return Result.failure(IllegalStateException("Selected provider adapter is unavailable"))

        val response = try {
            adapter.generate(request).getOrElse { return Result.failure(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return Result.failure(failure)
        }

        val invalid = response.text.length > request.maxOutputUnits ||
            response.outputUnitsUsed !in 0..request.maxOutputUnits ||
            response.inputUnitsUsed !in 0..request.inputUnits ||
            response.proposedToolCalls.size > MAX_PROPOSED_TOOL_CALLS ||
            response.proposedToolCalls.any {
                it.proposalId.isBlank() || it.toolId.isBlank() ||
                    it.arguments.size > MAX_ARGUMENTS_PER_PROPOSAL ||
                    it.arguments.any { entry -> entry.key.isBlank() || entry.value.length > MAX_ARGUMENT_VALUE_LENGTH }
            } ||
            !response.completedAt.isBefore(request.deadline) ||
            response.completedAt.isBefore(now)

        if (invalid) return Result.failure(IllegalStateException("Provider returned an invalid or over-budget response"))
        return Result.success(response)
    }

    private companion object {
        const val MAX_PROPOSED_TOOL_CALLS = 16
        const val MAX_ARGUMENTS_PER_PROPOSAL = 32
        const val MAX_ARGUMENT_VALUE_LENGTH = 4_096
    }
}
