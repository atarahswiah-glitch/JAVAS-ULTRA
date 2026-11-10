package com.javasultra.app.core.learning

import java.net.URI
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.*
import org.junit.Test

class DefaultPublicResearchServiceTest {
    private val now = 1_800_000_000_000L
    private val request = PublicPageRequest(
        URI("https://example.org/research"), ResearchHttpMethod.GET, true, true
    )
    private fun document(
        uri: URI = request.uri,
        text: String = "Evidence text",
        digest: String = "a".repeat(64),
        retrievedAt: Long = now - 1_000
    ) = ResearchDocument(uri, "Research source", retrievedAt, "text/html; charset=utf-8", text, digest)

    private fun service(result: Result<ResearchDocument>) = DefaultPublicResearchService(
        object : PublicResearchAdapter {
            override suspend fun retrieve(request: PublicPageRequest) = result
        }
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return outcome!!.getOrThrow()
    }

    @Test fun acceptsBoundedPublicEvidence() {
        val result = runSuspend { service(Result.success(document())).retrieve(request, setOf("example.org"), now) }
        assertTrue(result.isSuccess)
    }

    @Test fun rejectsNonAllowlistedRequestBeforeAdapter() {
        var called = false
        val adapter = object : PublicResearchAdapter {
            override suspend fun retrieve(request: PublicPageRequest): Result<ResearchDocument> {
                called = true
                return Result.success(document())
            }
        }
        val result = runSuspend {
            DefaultPublicResearchService(adapter).retrieve(request, setOf("other.org"), now)
        }
        assertTrue(result.isFailure)
        assertFalse(called)
    }

    @Test fun rejectsCrossHostFinalUri() {
        val result = runSuspend {
            service(Result.success(document(uri = URI("https://other.org/final"))))
                .retrieve(request, setOf("example.org"), now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsOversizedExtractedText() {
        val result = runSuspend {
            service(Result.success(document(text = "x".repeat(1_000_001))))
                .retrieve(request, setOf("example.org"), now)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsMalformedDigestAndFutureRetrievalTime() {
        val badDigest = runSuspend {
            service(Result.success(document(digest = "not-a-digest")))
                .retrieve(request, setOf("example.org"), now)
        }
        val future = runSuspend {
            service(Result.success(document(retrievedAt = now + 1)))
                .retrieve(request, setOf("example.org"), now)
        }
        assertTrue(badDigest.isFailure)
        assertTrue(future.isFailure)
    }

    @Test fun propagatesAdapterFailure() {
        val result = runSuspend {
            service(Result.failure(IllegalStateException("network unavailable")))
                .retrieve(request, setOf("example.org"), now)
        }
        assertTrue(result.isFailure)
    }
}
