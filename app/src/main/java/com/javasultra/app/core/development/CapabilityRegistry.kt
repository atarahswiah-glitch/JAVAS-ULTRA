package com.javasultra.app.core.development

import java.time.Instant

enum class CapabilityStatus { AVAILABLE, LIMITED, PLANNED, BLOCKED, DISABLED }
enum class CapabilityRisk { LOW, MEDIUM, HIGH, CRITICAL }

data class CapabilityDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val status: CapabilityStatus,
    val requiredPermissions: Set<String>,
    val dependencies: Set<String>,
    val risk: CapabilityRisk,
    val lastVerifiedAt: Instant?,
    val knownLimitations: List<String>
)

enum class RegistryIssue { EMPTY_ID, EMPTY_NAME, EMPTY_VERSION, MISSING_DEPENDENCY, SELF_DEPENDENCY, DUPLICATE_ID }

object CapabilityRegistryValidator {
    fun validate(entries: List<CapabilityDescriptor>): List<RegistryIssue> = buildList {
        val ids = entries.map { it.id }
        if (ids.size != ids.toSet().size) add(RegistryIssue.DUPLICATE_ID)
        entries.forEach { entry ->
            if (entry.id.isBlank()) add(RegistryIssue.EMPTY_ID)
            if (entry.name.isBlank()) add(RegistryIssue.EMPTY_NAME)
            if (entry.version.isBlank()) add(RegistryIssue.EMPTY_VERSION)
            if (entry.dependencies.contains(entry.id)) add(RegistryIssue.SELF_DEPENDENCY)
            if (!ids.toSet().containsAll(entry.dependencies)) add(RegistryIssue.MISSING_DEPENDENCY)
        }
    }
}
