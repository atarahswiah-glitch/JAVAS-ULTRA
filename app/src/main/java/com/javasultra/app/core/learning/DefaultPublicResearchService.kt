package com.javasultra.app.core.learning

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException

/**
 * Bounded orchestration for public research adapters.
 * Network adapters remain responsible for DNS/IP validation, redirect checks, robots handling,
 * connection timeouts and transport byte limits. This service validates the final document too.
 */
class DefaultPublicResearchService(
    private val adapter: PublicResearchAdapter
) {
    suspend fun retrieve(
        request: PublicPageRequest,
        allowedDomains: Set<String>,
        nowEpochMillis: Long
    ): Result<ResearchDocument> {
        val denial = PublicResearchGateway.evaluate(request, allowedDomains)
        if (denial != null) return Result.failure(IllegalArgumentException("Public research denied: $denial"))
        if (nowEpochMillis < 0) return Result.failure(IllegalArgumentException("Invalid clock value"))

        val document = try {
            adapter.retrieve(request).getOrElse { return Result.failure(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return Result.failure(failure)
        }

        val requestedHost = request.uri.host?.lowercase()
        val returnedHost = document.canonicalUri.host?.lowercase()
        val validUri = document.canonicalUri.isAbsolute &&
            !document.canonicalUri.isOpaque &&
            document.canonicalUri.scheme.equals("https", ignoreCase = true) &&
            document.canonicalUri.rawUserInfo == null &&
            document.canonicalUri.port in setOf(-1, 443) &&
            requestedHost == returnedHost
        val contentType = document.contentType.substringBefore(';').trim().lowercase()
        val supportedType = contentType in setOf("text/html", "text/plain", "application/pdf")
        val contentBytes = document.extractedText.toByteArray(StandardCharsets.UTF_8).size
        val valid = validUri &&
            document.title.isNotBlank() && document.title.length <= MAX_TITLE_CHARS &&
            supportedType &&
            document.extractedText.isNotBlank() && contentBytes <= MAX_EXTRACTED_BYTES &&
            document.retrievedAtEpochMillis in 1..nowEpochMillis &&
            SHA256.matches(document.contentDigestSha256)

        if (!valid) return Result.failure(IllegalStateException("Research adapter returned invalid or over-limit evidence"))
        return Result.success(document)
    }

    private companion object {
        const val MAX_TITLE_CHARS = 500
        const val MAX_EXTRACTED_BYTES = 1_000_000
        val SHA256 = Regex("[a-fA-F0-9]{64}")
    }
}
