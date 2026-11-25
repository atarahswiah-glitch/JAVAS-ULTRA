package com.javasultra.app.core.memory

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorMemoryManagerTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")

    private class FakeRepository : MemoryRepository {
        val records = linkedMapOf<MemoryId, MemoryRecord>()
        var lastRequest: MemoryAccessRequest? = null

        override fun insertProposedBatch(records: List<MemoryRecord>, actorPrincipalId: String) =
            MemoryRepositoryResult.Success(Unit)

        override fun insertProposed(record: MemoryRecord, actorPrincipalId: String): MemoryRepositoryResult<Unit> {
            if (record.ownerPrincipalId != actorPrincipalId) return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
            records[record.id] = record
            return MemoryRepositoryResult.Success(Unit)
        }

        override fun activate(
            id: MemoryId,
            actorPrincipalId: String,
            at: Instant,
            stepUpAuthenticated: Boolean
        ): MemoryRepositoryResult<MemoryRecord> {
            val record = records[id] ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
            val prior = record.supersedes?.let { records[it] }
            if (MemoryActivationPolicy.evaluate(
                    record, prior, MemoryActivationRequest(actorPrincipalId, at, stepUpAuthenticated)
                ) != MemoryActivationDecision.Allowed
            ) return failure(MemoryRepositoryFailure.ACCESS_DENIED)
            prior?.let { records[it.id] = it.copy(lifecycle = MemoryLifecycle.SUPERSEDED, updatedAt = at) }
            val active = record.copy(
                lifecycle = MemoryLifecycle.ACTIVE,
                updatedAt = at,
                verification = if (record.provenance.sourceType == MemorySourceType.CREATOR_STATEMENT) {
                    MemoryVerification.CREATOR_CONFIRMED
                } else {
                    record.verification
                }
            )
            records[id] = active
            return MemoryRepositoryResult.Success(active)
        }

        override fun read(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<MemoryRecord> {
            lastRequest = request
            val record = records[id] ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
            return if (MemoryAccessPolicy.evaluate(record, request) == MemoryAccessDecision.Allowed) {
                MemoryRepositoryResult.Success(record)
            } else failure(MemoryRepositoryFailure.ACCESS_DENIED)
        }

        override fun listReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>> {
            lastRequest = request
            return MemoryRepositoryResult.Success(
                records.values.filter { MemoryAccessPolicy.evaluate(it, request) == MemoryAccessDecision.Allowed }
            )
        }

        override fun exportReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>> {
            lastRequest = request
            if (request.action != MemoryAction.EXPORT) return failure(MemoryRepositoryFailure.ACCESS_DENIED)
            return listReadable(request)
        }

        override fun delete(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<Unit> {
            lastRequest = request
            val record = records[id] ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
            if (MemoryAccessPolicy.evaluate(record, request.copy(action = MemoryAction.DELETE)) != MemoryAccessDecision.Allowed) {
                return failure(MemoryRepositoryFailure.ACCESS_DENIED)
            }
            records.remove(id)
            return MemoryRepositoryResult.Success(Unit)
        }

        override fun proposeCorrection(
            id: MemoryId,
            replacement: MemoryRecord,
            request: MemoryAccessRequest
        ): MemoryRepositoryResult<MemoryRecord> {
            lastRequest = request
            val current = records[id] ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
            if (MemoryAccessPolicy.evaluate(current, request.copy(action = MemoryAction.UPDATE)) != MemoryAccessDecision.Allowed) {
                return failure(MemoryRepositoryFailure.ACCESS_DENIED)
            }
            records[replacement.id] = replacement
            return MemoryRepositoryResult.Success(replacement)
        }

        private fun <T> failure(reason: MemoryRepositoryFailure) = MemoryRepositoryResult.Failure(reason)
    }

    @Test fun newCreatorMemoryIsOnlyProposedUntilExplicitConfirmation() {
        val repository = FakeRepository()
        val manager = CreatorMemoryManager(repository) { "memory-1" }

        val proposal = manager.proposeCreatorStatement(
            creatorPrincipalId = "creator-1",
            content = "Prefers practical explanations.",
            kind = MemoryKind.PREFERENCE,
            sensitivity = MemorySensitivity.CREATOR_PRIVATE,
            now = now
        )

        assertTrue(proposal is MemoryRepositoryResult.Success)
        val proposed = (proposal as MemoryRepositoryResult.Success).value
        assertEquals(MemoryLifecycle.PROPOSED, proposed.lifecycle)
        assertEquals(MemoryVerification.SINGLE_SOURCE, proposed.verification)
        assertEquals(MemorySourceType.CREATOR_STATEMENT, proposed.provenance.sourceType)
        assertEquals(MemoryLifecycle.PROPOSED, repository.records[proposed.id]?.lifecycle)

        val confirmed = manager.confirm("creator-1", proposed.id, now.plusSeconds(1))
        assertTrue(confirmed is MemoryRepositoryResult.Success)
        val confirmedRecord = (confirmed as MemoryRepositoryResult.Success).value
        assertEquals(MemoryLifecycle.ACTIVE, confirmedRecord.lifecycle)
        assertEquals(MemoryVerification.CREATOR_CONFIRMED, confirmedRecord.verification)
    }

    @Test fun correctionProposalDoesNotReplaceTheActiveRecordUntilConfirmed() {
        val repository = FakeRepository()
        val manager = CreatorMemoryManager(repository) { "memory-correction" }
        val original = MemoryRecord(
            id = MemoryId("memory-original"),
            ownerPrincipalId = "creator-1",
            kind = MemoryKind.PREFERENCE,
            content = "Prefers long explanations.",
            provenance = MemoryProvenance(MemorySourceType.CREATOR_STATEMENT, null, now, "creator-1"),
            createdAt = now,
            updatedAt = now,
            confidence = 1.0,
            verification = MemoryVerification.CREATOR_CONFIRMED,
            sensitivity = MemorySensitivity.CREATOR_PRIVATE,
            lifecycle = MemoryLifecycle.ACTIVE
        )
        repository.records[original.id] = original

        val proposal = manager.proposeCorrection(
            "creator-1", original.id, "Prefers concise explanations.", now.plusSeconds(1)
        )

        assertTrue(proposal is MemoryRepositoryResult.Success)
        assertEquals(MemoryLifecycle.ACTIVE, repository.records[original.id]?.lifecycle)
        val replacement = (proposal as MemoryRepositoryResult.Success).value
        assertEquals(MemoryLifecycle.PROPOSED, replacement.lifecycle)
        assertEquals(original.id, replacement.supersedes)

        val activated = manager.confirm("creator-1", replacement.id, now.plusSeconds(2))
        assertTrue(activated is MemoryRepositoryResult.Success)
        assertEquals(MemoryLifecycle.SUPERSEDED, repository.records[original.id]?.lifecycle)
        assertEquals(MemoryLifecycle.ACTIVE, repository.records[replacement.id]?.lifecycle)
        assertEquals(MemoryVerification.CREATOR_CONFIRMED, repository.records[replacement.id]?.verification)
    }

    @Test fun highlySensitiveMemoryRequiresStepUpForReviewAndDeletion() {
        val repository = FakeRepository()
        val manager = CreatorMemoryManager(repository) { "sensitive-1" }
        val proposed = manager.proposeCreatorStatement(
            creatorPrincipalId = "creator-1",
            content = "A highly sensitive private note.",
            kind = MemoryKind.SENSITIVE,
            sensitivity = MemorySensitivity.HIGHLY_SENSITIVE,
            now = now
        ) as MemoryRepositoryResult.Success
        manager.confirm("creator-1", proposed.value.id, now.plusSeconds(1), stepUpAuthenticated = true)

        val deniedReview = manager.review("creator-1", now.plusSeconds(2))
        assertTrue(deniedReview is MemoryRepositoryResult.Success)
        assertTrue((deniedReview as MemoryRepositoryResult.Success).value.isEmpty())

        val allowedReview = manager.review("creator-1", now.plusSeconds(2), stepUpAuthenticated = true)
        assertTrue(allowedReview is MemoryRepositoryResult.Success)
        assertEquals(1, (allowedReview as MemoryRepositoryResult.Success).value.size)

        val deniedDelete = manager.delete("creator-1", proposed.value.id, now.plusSeconds(3))
        assertTrue(deniedDelete is MemoryRepositoryResult.Failure)
        assertTrue(repository.records.containsKey(proposed.value.id))
        assertFalse(repository.lastRequest?.stepUpAuthenticated ?: true)
    }

    @Test fun rejectsInvalidOwnerExpiryAndOversizedMemoryBeforeStorage() {
        val repository = FakeRepository()
        val manager = CreatorMemoryManager(repository) { "bad" }

        val blankOwner = manager.proposeCreatorStatement(
            "", "Some note", MemoryKind.PREFERENCE, MemorySensitivity.CREATOR_PRIVATE, now
        )
        val expired = manager.proposeCreatorStatement(
            "creator-1", "Some note", MemoryKind.PREFERENCE, MemorySensitivity.CREATOR_PRIVATE,
            now, expiresAt = now
        )
        val tooLong = manager.proposeCreatorStatement(
            "creator-1", "x".repeat(4_001), MemoryKind.PREFERENCE, MemorySensitivity.CREATOR_PRIVATE, now
        )

        assertTrue(blankOwner is MemoryRepositoryResult.Failure)
        assertTrue(expired is MemoryRepositoryResult.Failure)
        assertTrue(tooLong is MemoryRepositoryResult.Failure)
        assertTrue(repository.records.isEmpty())
    }
}
