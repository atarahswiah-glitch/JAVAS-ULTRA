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
    INVALID_URI, INVALID_DOMAIN_POLICY, HTTPS_REQUIRED, DOMAIN_NOT_ALLOWED, GET_ONLY, PUBLIC_ACCESS_ONLY,
    ROBOTS_POLICY_NOT_CHECKED, PERSONAL_DATA_BLOCKED, AUTHENTICATION_NOT_ALLOWED,
    BYPASS_ATTEMPT_BLOCKED
}

/**
 * Preflight gate for public, read-only research.
 *
 * This is not an SSRF-safe network transport: adapters must independently validate every
 * redirect and resolved IP address, pin/verify DNS results, enforce timeouts and byte limits,
 * and re-run this gate for each redirect target before connecting.
 */
object PublicResearchGateway {
    fun evaluate(request: PublicPageRequest, allowedDomains: Set<String>): PublicFetchDenial? {
        val uri = request.uri
        if (!uri.isAbsolute || uri.isOpaque || uri.rawUserInfo != null ||
            uri.host.isNullOrBlank() || uri.port !in setOf(-1, 443)
        ) return PublicFetchDenial.INVALID_URI
        if (!uri.scheme.equals("https", ignoreCase = true)) return PublicFetchDenial.HTTPS_REQUIRED

        if (allowedDomains.isEmpty() || allowedDomains.any { !isValidDomain(it) }) {
            return PublicFetchDenial.INVALID_DOMAIN_POLICY
        }
        val host = normalizeDomain(uri.host) ?: return PublicFetchDenial.INVALID_URI
        // IP literals and local hostnames are never accepted as public research targets.
        if (host.startsWith("[") || host.contains(':') || IPV4.matches(host) || !host.contains('.')) {
            return PublicFetchDenial.DOMAIN_NOT_ALLOWED
        }
        val allowed = allowedDomains.mapNotNull(::normalizeDomain)
        if (allowed.none { host == it || host.endsWith(".$it") }) return PublicFetchDenial.DOMAIN_NOT_ALLOWED
        if (request.method != ResearchHttpMethod.GET) return PublicFetchDenial.GET_ONLY
        if (!request.declaredPublic) return PublicFetchDenial.PUBLIC_ACCESS_ONLY
        if (!request.robotsPolicyChecked) return PublicFetchDenial.ROBOTS_POLICY_NOT_CHECKED
        if (request.containsPersonalData) return PublicFetchDenial.PERSONAL_DATA_BLOCKED
        if (request.attemptsAuthentication) return PublicFetchDenial.AUTHENTICATION_NOT_ALLOWED
        if (request.attemptsPaywallOrCaptchaBypass) return PublicFetchDenial.BYPASS_ATTEMPT_BLOCKED
        return null
    }

    private fun isValidDomain(value: String): Boolean {
        val domain = normalizeDomain(value) ?: return false
        if (domain.length > 253 || !domain.contains('.') || IPV4.matches(domain)) return false
        return domain.split('.').all { label ->
            label.length in 1..63 && label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun normalizeDomain(value: String): String? {
        val candidate = value.trim().lowercase().removePrefix("www.").removeSuffix(".")
        if (candidate.isBlank()) return null
        if (candidate.any { it.isWhitespace() || it == '/' || it == ':' || it == '@' || it == '*' }) return null
        return candidate
    }

    private val IPV4 = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
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
