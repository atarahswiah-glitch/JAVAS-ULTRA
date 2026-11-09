package com.javasultra.app.core.memory

import java.time.Instant

@JvmInline
value class MemoryId(val value: String)

enum class MemoryKind {
    SESSION, PREFERENCE, EPISODIC, SEMANTIC, PROJECT, SENSITIVE
}

enum class MemorySensitivity {
    PUBLIC, INTERNAL, CREATOR_PRIVATE, HIGHLY_SENSITIVE
}

enum class MemoryVerification {
    UNVERIFIED, SINGLE_SOURCE, CORROBORATED, CREATOR_CONFIRMED, DISPUTED
}

enum class MemoryLifecycle {
    PROPOSED, ACTIVE, SUPERSEDED, EXPIRED, DELETION_REQUESTED, DELETED
}

enum class MemorySourceType {
    CREATOR_STATEMENT, APPROVED_FILE, PUBLIC_WEB_SOURCE, SENSOR_OBSERVATION,
    TOOL_RESULT, MODEL_INFERENCE, SYSTEM_EVENT
}

data class MemoryProvenance(
    val sourceType: MemorySourceType,
    val sourceReference: String?,
    val observedAt: Instant,
    val publisherOrActor: String?,
    val notes: String? = null
)

data class MemoryRecord(
    val id: MemoryId,
    val ownerPrincipalId: String,
    val kind: MemoryKind,
    val content: String,
    val provenance: MemoryProvenance,
    val createdAt: Instant,
    val updatedAt: Instant,
    val confidence: Double,
    val verification: MemoryVerification,
    val sensitivity: MemorySensitivity,
    val lifecycle: MemoryLifecycle = MemoryLifecycle.PROPOSED,
    val expiresAt: Instant? = null,
    val supersedes: MemoryId? = null,
    val tags: Set<String> = emptySet()
) {
    fun validate(): List<MemoryValidationIssue> = buildList {
        if (id.value.isBlank()) add(MemoryValidationIssue.EMPTY_ID)
        if (ownerPrincipalId.isBlank()) add(MemoryValidationIssue.EMPTY_OWNER)
        if (content.isBlank()) add(MemoryValidationIssue.EMPTY_CONTENT)
        if (confidence !in 0.0..1.0) add(MemoryValidationIssue.CONFIDENCE_OUT_OF_RANGE)
        if (updatedAt.isBefore(createdAt)) add(MemoryValidationIssue.UPDATE_BEFORE_CREATION)
        if (provenance.observedAt.isAfter(createdAt)) add(MemoryValidationIssue.PROVENANCE_AFTER_CREATION)
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) add(MemoryValidationIssue.INVALID_EXPIRY)
        if (kind == MemoryKind.SENSITIVE && sensitivity != MemorySensitivity.HIGHLY_SENSITIVE) {
            add(MemoryValidationIssue.SENSITIVE_KIND_MUST_BE_HIGHLY_SENSITIVE)
        }
    }
}

enum class MemoryValidationIssue {
    EMPTY_ID, EMPTY_OWNER, EMPTY_CONTENT, CONFIDENCE_OUT_OF_RANGE,
    UPDATE_BEFORE_CREATION, PROVENANCE_AFTER_CREATION, INVALID_EXPIRY,
    SENSITIVE_KIND_MUST_BE_HIGHLY_SENSITIVE
}
