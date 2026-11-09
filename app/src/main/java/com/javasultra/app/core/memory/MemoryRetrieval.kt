package com.javasultra.app.core.memory

import java.time.Instant
import java.util.Locale

data class MemorySearchRequest(
    val query: String,
    val access: MemoryAccessRequest,
    val now: Instant,
    val limit: Int = 10
)

data class RankedMemory(val record: MemoryRecord, val score: Double, val matchedTerms: Set<String>)

enum class MemorySearchDenial { EMPTY_QUERY, INVALID_LIMIT }

sealed interface MemorySearchResult {
    data class Success(val matches: List<RankedMemory>) : MemorySearchResult
    data class Denied(val reason: MemorySearchDenial) : MemorySearchResult
}

/**
 * Deterministic local retrieval ranking. Access and lifecycle checks happen before scoring;
 * this class is not a semantic embedding engine and must not replace trusted storage policy.
 */
object MemoryRetrieval {
    fun search(records: Iterable<MemoryRecord>, request: MemorySearchRequest): MemorySearchResult {
        val terms = tokenize(request.query)
        if (terms.isEmpty()) return MemorySearchResult.Denied(MemorySearchDenial.EMPTY_QUERY)
        if (request.limit !in 1..100) return MemorySearchResult.Denied(MemorySearchDenial.INVALID_LIMIT)

        val matches = records.asSequence()
            .filter { MemoryAccessPolicy.evaluate(it, request.access) == MemoryAccessDecision.Allowed }
            .filter { it.expiresAt == null || it.expiresAt.isAfter(request.now) }
            .mapNotNull { record ->
                val searchable = tokenize(record.content + " " + record.tags.joinToString(" "))
                val matched = terms.intersect(searchable)
                if (matched.isEmpty()) return@mapNotNull null
                val coverage = matched.size.toDouble() / terms.size.toDouble()
                val phraseBonus = if (record.content.lowercase(Locale.ROOT).contains(request.query.trim().lowercase(Locale.ROOT))) 0.20 else 0.0
                val verificationWeight = when (record.verification) {
                    MemoryVerification.CREATOR_CONFIRMED -> 1.0
                    MemoryVerification.CORROBORATED -> 0.95
                    MemoryVerification.SINGLE_SOURCE -> 0.75
                    MemoryVerification.UNVERIFIED -> 0.55
                    MemoryVerification.DISPUTED -> 0.25
                }
                val confidenceWeight = 0.5 + (record.confidence.coerceIn(0.0, 1.0) * 0.5)
                val score = ((coverage * 0.7) + phraseBonus) * verificationWeight * confidenceWeight
                RankedMemory(record, score.coerceIn(0.0, 1.0), matched)
            }
            .sortedWith(compareByDescending<RankedMemory> { it.score }.thenByDescending { it.record.updatedAt })
            .take(request.limit)
            .toList()
        return MemorySearchResult.Success(matches)
    }

    private fun tokenize(value: String): Set<String> = Regex("[\\p{L}\\p{N}]{2,}")
        .findAll(value.lowercase(Locale.ROOT))
        .map { it.value }
        .toSet()
}
