package com.javasultra.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoryActivationPolicyTest {
    private val now = Instant.parse("2026-11-10T06:00:00Z")
    private val owner = "creator-1"

    private fun record(
        id: String,
        lifecycle: MemoryLifecycle = MemoryLifecycle.PROPOSED,
        sensitivity: MemorySensitivity = MemorySensitivity.CREATOR_PRIVATE,
        supersedes: MemoryId? = null,
        updatedAt: Instant = now.minusSeconds(5)
    ) = MemoryRecord(
        MemoryId(id), owner, MemoryKind.PREFERENCE, "Creator-approved memory",
        MemoryProvenance(MemorySourceType.CREATOR_STATEMENT, null, now.minusSeconds(30), owner),
        now.minusSeconds(20), updatedAt, 0.9, MemoryVerification.CREATOR_CONFIRMED,
        sensitivity, lifecycle, supersedes = supersedes
    )

    private fun evaluate(
        proposed: MemoryRecord,
        prior: MemoryRecord? = null,
        actor: String = owner,
        stepUp: Boolean = false,
        at: Instant = now
    ) = MemoryActivationPolicy.evaluate(proposed, prior, MemoryActivationRequest(actor, at, stepUp))

    private fun denied(result: MemoryActivationDecision) =
        (result as MemoryActivationDecision.Denied).reason

    @Test fun activatesOrdinaryProposedMemory() {
        assertEquals(MemoryActivationDecision.Allowed, evaluate(record("new")))
    }

    @Test fun highlySensitiveNewMemoryRequiresStepUp() {
        assertEquals(
            MemoryActivationDenial.STEP_UP_REQUIRED,
            denied(evaluate(record("sensitive", sensitivity = MemorySensitivity.HIGHLY_SENSITIVE)))
        )
    }

    @Test fun highlySensitiveNewMemoryAllowsStepUp() {
        assertEquals(
            MemoryActivationDecision.Allowed,
            evaluate(record("sensitive", sensitivity = MemorySensitivity.HIGHLY_SENSITIVE), stepUp = true)
        )
    }

    @Test fun highlySensitiveReplacementRequiresStepUpEvenIfPriorIsOrdinary() {
        val prior = record("old", lifecycle = MemoryLifecycle.ACTIVE)
        val replacement = record("new", sensitivity = MemorySensitivity.HIGHLY_SENSITIVE, supersedes = prior.id)
        assertEquals(MemoryActivationDenial.STEP_UP_REQUIRED, denied(evaluate(replacement, prior)))
    }

    @Test fun replacingHighlySensitiveMemoryRequiresStepUp() {
        val prior = record("old", lifecycle = MemoryLifecycle.ACTIVE, sensitivity = MemorySensitivity.HIGHLY_SENSITIVE)
        val replacement = record("new", supersedes = prior.id)
        assertEquals(MemoryActivationDenial.STEP_UP_REQUIRED, denied(evaluate(replacement, prior)))
    }

    @Test fun rejectsActivationByAnotherPrincipal() {
        assertEquals(MemoryActivationDenial.OWNER_MISMATCH, denied(evaluate(record("new"), actor = "other")))
    }

    @Test fun rejectsNonProposedRecord() {
        assertEquals(
            MemoryActivationDenial.INVALID_STATE,
            denied(evaluate(record("new", lifecycle = MemoryLifecycle.ACTIVE)))
        )
    }

    @Test fun replacementMustReferenceTheLoadedPriorRecord() {
        val prior = record("old", lifecycle = MemoryLifecycle.ACTIVE)
        val replacement = record("new", supersedes = MemoryId("different"))
        assertEquals(MemoryActivationDenial.PRIOR_RECORD_INVALID, denied(evaluate(replacement, prior)))
    }

    @Test fun rejectsBackdatedActivation() {
        val proposed = record("new", updatedAt = now.plusSeconds(5))
        assertEquals(
            MemoryActivationDenial.INVALID_STATE,
            denied(evaluate(proposed, at = now))
        )
    }
}
