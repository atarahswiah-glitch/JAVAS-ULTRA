package com.javasultra.app.core.learning

/**
 * Persistence boundary used by the learning-cycle orchestrator. The Android adapter
 * delegates to the encrypted SQLite journal; tests can provide an in-memory port.
 */
interface LearningCycleJournalPort {
    fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit>
    fun append(
        event: LearningJournalEvent,
        expectedTaskId: String
    ): LearningJournalStoreResult<LearningJournalEntry>
}

class EncryptedLearningCycleJournalPort(
    private val store: EncryptedLearningJournalStore
) : LearningCycleJournalPort {
    override fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit> =
        store.create(entry)

    override fun append(
        event: LearningJournalEvent,
        expectedTaskId: String
    ): LearningJournalStoreResult<LearningJournalEntry> =
        store.append(event, expectedTaskId)
}
