package com.javasultra.app.core.learning

import java.time.Instant
import java.net.URI
import java.util.concurrent.CancellationException

/** A candidate returned by a domain-approved discovery adapter, not yet trusted evidence. */
data class PublicResearchCandidate(
    val id: String,
    val request: PublicPageRequest,
    val publisher: String,
    val kind: EvidenceSourceKind,
    val publishedAt: Instant?,
    val trustScore: Double
)

fun interface PublicResearchDiscovery {
    suspend fun discover(
        task: LearningTask,
        allowedDomains: Set<String>,
        maxSources: Int
    ): Result<List<PublicResearchCandidate>>
}

fun interface EvidenceClaimExtractor {
    suspend fun extract(
        task: LearningTask,
        sources: List<ResearchSource>,
        documents: List<ResearchDocument>
    ): Result<List<EvidenceClaim>>
}

/**
 * Joins discovery, safe page retrieval and claim extraction into the LearningResearchProvider
 * contract. Discovery and extraction remain injected adapters so no search vendor, model provider,
 * API key or remote identity is silently selected by the Android client.
 */
class PublicResearchLearningProvider(
    private val discovery: PublicResearchDiscovery,
    private val pageService: DefaultPublicResearchService,
    private val claimExtractor: EvidenceClaimExtractor,
    private val clock: () -> Instant = Instant::now
) : LearningResearchProvider {

    override suspend fun research(
        task: LearningTask,
        allowedDomains: Set<String>,
        maxSources: Int,
        maxDurationSeconds: Long
    ): Result<LearningResearchBundle> {
        if (LearningTaskPolicy.validate(task) != null ||
            maxSources !in 1..100 ||
            maxSources > task.sourceBudget ||
            maxDurationSeconds !in 1..86_400 ||
            allowedDomains.isEmpty()
        ) return Result.failure(IllegalArgumentException("Invalid bounded research request"))

        return try {
            val candidates = discovery.discover(task, allowedDomains.toSet(), maxSources)
                .getOrElse { return Result.failure(it) }
            if (candidates.isEmpty() || candidates.size > maxSources ||
                candidates.map { it.id }.toSet().size != candidates.size ||
                candidates.any { !isValidCandidate(it, allowedDomains) }
            ) {
                return Result.failure(IllegalStateException("Discovery returned invalid or over-budget candidates"))
            }

            val documents = mutableListOf<ResearchDocument>()
            val sources = mutableListOf<ResearchSource>()
            for (candidate in candidates) {
                val now = clock()
                if (!now.isAfter(Instant.EPOCH)) {
                    return Result.failure(IllegalStateException("Invalid research clock"))
                }
                val document = pageService.retrieve(
                    candidate.request,
                    allowedDomains,
                    now.toEpochMilli()
                ).getOrElse { return Result.failure(it) }
                documents += document
                sources += ResearchSource(
                    id = candidate.id,
                    kind = candidate.kind,
                    title = document.title,
                    publisher = candidate.publisher.trim(),
                    uri = document.canonicalUri.toString(),
                    publishedAt = candidate.publishedAt,
                    retrievedAt = Instant.ofEpochMilli(document.retrievedAtEpochMillis),
                    trustScore = candidate.trustScore
                )
            }

            val claims = claimExtractor.extract(task, sources.toList(), documents.toList())
                .getOrElse { return Result.failure(it) }
            if (claims.isEmpty() || claims.size > MAX_CLAIMS ||
                claims.any { it.id.isBlank() } ||
                claims.map { it.id }.toSet().size != claims.size
            ) {
                return Result.failure(IllegalStateException("Claim extractor returned invalid or over-limit claims"))
            }
            val knownSourceIds = sources.map { it.id }.toSet()
            if (claims.any { claim ->
                    claim.sourceIds.isEmpty() ||
                        !knownSourceIds.containsAll(claim.sourceIds) ||
                        !claim.sourceIds.containsAll(claim.supportingSourceIds + claim.contradictingSourceIds)
                }
            ) {
                return Result.failure(IllegalStateException("Claim provenance does not match retrieved sources"))
            }
            Result.success(LearningResearchBundle(sources.toList(), claims.toList()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private fun isValidCandidate(
        candidate: PublicResearchCandidate,
        allowedDomains: Set<String>
    ): Boolean {
        val uri: URI = candidate.request.uri
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val domainAllowed = allowedDomains.any { raw ->
            val domain = raw.trim().lowercase().removePrefix("www.").removeSuffix(".")
            domain.isNotBlank() && (host == domain || host.endsWith(".$domain"))
        }
        val permittedKind = candidate.kind in setOf(
            EvidenceSourceKind.PRIMARY_RESEARCH,
            EvidenceSourceKind.OFFICIAL_DOCUMENTATION,
            EvidenceSourceKind.ACADEMIC_REVIEW,
            EvidenceSourceKind.REPUTABLE_REFERENCE,
            EvidenceSourceKind.NEWS
        )
        return candidate.id.isNotBlank() &&
            candidate.publisher.isNotBlank() &&
            candidate.trustScore in 0.0..1.0 &&
            permittedKind &&
            domainAllowed &&
            candidate.request.declaredPublic &&
            candidate.request.robotsPolicyChecked &&
            !candidate.request.containsPersonalData &&
            !candidate.request.attemptsAuthentication &&
            !candidate.request.attemptsPaywallOrCaptchaBypass &&
            PublicResearchGateway.evaluate(candidate.request, allowedDomains) == null
    }

    private companion object {
        const val MAX_CLAIMS = 500
    }
}
