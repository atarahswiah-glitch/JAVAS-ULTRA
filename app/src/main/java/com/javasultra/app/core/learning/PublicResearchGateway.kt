package com.javasultra.app.core.learning

import java.net.URI

enum class ResearchHttpMethod { GET, POST, PUT, DELETE }
data class PublicPageRequest(
    val uri: URI,
    val method: ResearchHttpMethod = ResearchHttpMethod.GET,
    val declaredPublic: Boolean,
    val robotsPolicyChecked: Boolean,
    val containsPersonalData: Boolean = false,
    val attemptsAuthentication: Boolean = false,
    val attemptsPaywallOrCaptchaBypass: Boolean = false
)

enum class PublicFetchDenial {
    HTTPS_REQUIRED, DOMAIN_NOT_ALLOWED, GET_ONLY, PUBLIC_ACCESS_ONLY,
    ROBOTS_POLICY_NOT_CHECKED, PERSONAL_DATA_BLOCKED, AUTHENTICATION_NOT_ALLOWED,
    BYPASS_ATTEMPT_BLOCKED
}

/** Gate for public read-only research. A transport adapter must enforce timeouts and size limits too. */
object PublicResearchGateway {
    fun evaluate(request: PublicPageRequest, allowedDomains: Set<String>): PublicFetchDenial? {
        if (!request.uri.scheme.equals("https", ignoreCase = true)) return PublicFetchDenial.HTTPS_REQUIRED
        val host = request.uri.host?.lowercase() ?: return PublicFetchDenial.DOMAIN_NOT_ALLOWED
        val allowed = allowedDomains.map { it.lowercase().removePrefix("www.") }
        if (allowed.none { host == it || host.endsWith(".$it") }) return PublicFetchDenial.DOMAIN_NOT_ALLOWED
        if (request.method != ResearchHttpMethod.GET) return PublicFetchDenial.GET_ONLY
        if (!request.declaredPublic) return PublicFetchDenial.PUBLIC_ACCESS_ONLY
        if (!request.robotsPolicyChecked) return PublicFetchDenial.ROBOTS_POLICY_NOT_CHECKED
        if (request.containsPersonalData) return PublicFetchDenial.PERSONAL_DATA_BLOCKED
        if (request.attemptsAuthentication) return PublicFetchDenial.AUTHENTICATION_NOT_ALLOWED
        if (request.attemptsPaywallOrCaptchaBypass) return PublicFetchDenial.BYPASS_ATTEMPT_BLOCKED
        return null
    }
}

data class ResearchDocument(
    val canonicalUri: URI,
    val title: String,
    val retrievedAtEpochMillis: Long,
    val contentType: String,
    val extractedText: String,
    val contentDigestSha256: String
)

/** Adapter contract only. Implementations must apply network, size, timeout and content-safety limits. */
interface PublicResearchAdapter {
    suspend fun retrieve(request: PublicPageRequest): Result<ResearchDocument>
}
