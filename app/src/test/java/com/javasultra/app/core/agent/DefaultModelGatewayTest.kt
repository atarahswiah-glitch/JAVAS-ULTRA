package com.javasultra.app.core.agent

import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.*
import org.junit.Test

class DefaultModelGatewayTest {
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private val request = ModelRequest(
        "task-1", "Explain gravity", setOf(RuntimeModality.TEXT), DataClassification.PUBLIC,
        false, 100, now.plusSeconds(30)
    )
    private fun descriptor(id: String = "local") = ModelProviderDescriptor(
        id, ModelProviderKind.ON_DEVICE, setOf(RuntimeModality.TEXT), false,
        1_000, 100, setOf(DataClassification.PUBLIC), true
    )
    private fun response(
        completedAt: Instant = now.plusSeconds(1),
        outputUnits: Int = 12,
        text: String = "Gravity attracts mass."
    ) = ModelResponse(text, emptyList(), request.inputUnits, outputUnits, completedAt)

    private fun adapter(
        id: String = "local",
        result: Result<ModelResponse> = Result.success(response()),
        block: (ModelRequest) -> Result<ModelResponse> = { result }
    ) = object : ModelProviderAdapter {
        override val descriptor = descriptor(id)
        override suspend fun generate(request: ModelRequest): Result<ModelResponse> = block(request)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return outcome!!.getOrThrow()
    }

    @Test fun returnsValidatedResponseFromCompatibleAdapter() {
        val result = runSuspend { DefaultModelGateway(listOf(adapter())).generate(request, true, now) }
        assertTrue(result.isSuccess)
        assertEquals("Gravity attracts mass.", result.getOrThrow().text)
    }

    @Test fun refusesWhenNoCompatibleAdapterExists() {
        val result = runSuspend { DefaultModelGateway(emptyList()).generate(request, true, now) }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsResponseExceedingOutputBudget() {
        val result = runSuspend {
            DefaultModelGateway(listOf(adapter(result = Result.success(response(outputUnits = 101)))))
                .generate(request, true, now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsResponseCompletedAfterDeadline() {
        val result = runSuspend {
            DefaultModelGateway(listOf(adapter(result = Result.success(response(completedAt = request.deadline)))))
                .generate(request, true, now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsOversizedToolProposalList() {
        val proposals = (1..17).map { ProposedToolCall("p$it", "tool", emptyMap()) }
        val invalid = response().copy(proposedToolCalls = proposals)
        val result = runSuspend {
            DefaultModelGateway(listOf(adapter(result = Result.success(invalid)))).generate(request, true, now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun convertsAdapterFailureToGatewayFailure() {
        val result = runSuspend {
            DefaultModelGateway(listOf(adapter(result = Result.failure(IllegalArgumentException("provider")))))
                .generate(request, true, now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsDuplicateProviderIdsAtConstruction() {
        try {
            DefaultModelGateway(listOf(adapter(), adapter()))
            fail("Expected duplicate provider IDs to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
