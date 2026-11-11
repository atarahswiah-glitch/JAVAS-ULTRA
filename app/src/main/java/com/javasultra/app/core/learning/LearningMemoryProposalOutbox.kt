package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryOutboxItem
import com.javasultra.app.core.memory.MemoryOutboxResult
import com.javasultra.app.core.memory.MemoryOutboxStore
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class LearningJournalReceiptEnvelope(
    val expectedTaskId: String,
    val event: LearningJournalEvent
)

object LearningJournalReceiptEnvelopeCodec {
    private const val VERSION = 1
    private const val MAX_BYTES = MemoryOutboxItem.MAX_PAYLOAD_BYTES

    fun encode(value: LearningJournalReceiptEnvelope): ByteArray {
        require(value.expectedTaskId.isNotBlank() && value.event.actor.isNotBlank())
        val eventBytes = LearningJournalCodec.encodeEvent(value.event)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeUTF(value.expectedTaskId)
            out.writeInt(eventBytes.size)
            out.write(eventBytes)
        }
        return bytes.toByteArray().also { require(it.size in 1..MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): LearningJournalReceiptEnvelope {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == VERSION)
            val taskId = input.readUTF()
            require(taskId.isNotBlank())
            val size = input.readInt()
            require(size in 1..MAX_BYTES && size <= input.available())
            val eventBytes = ByteArray(size)
            input.readFully(eventBytes)
            require(input.available() == 0)
            return LearningJournalReceiptEnvelope(taskId, LearningJournalCodec.decodeEvent(eventBytes))
        }
    }
}

data class LearningOutboxDispatchItemResult(
    val outboxId: String,
    val result: LearningJournalStoreResult<LearningJournalEntry>
)

/** Safe to replay after process death between journal append and outbox acknowledgement. */
class LearningMemoryProposalOutboxDispatcher(
    private val outbox: MemoryOutboxStore,
    private val journalWriter: LearningJournalReceiptWriter
) {
    fun dispatch(item: MemoryOutboxItem, actorPrincipalId: String): LearningJournalStoreResult<LearningJournalEntry> {
        if (actorPrincipalId.isBlank() || item.ownerPrincipalId != actorPrincipalId) {
            return failure(LearningJournalStoreFailure.INVALID_EVENT)
        }
        val envelope = try {
            LearningJournalReceiptEnvelopeCodec.decode(item.payload)
        } catch (_: Exception) {
            return failure(LearningJournalStoreFailure.INVALID_EVENT)
        }
        if (envelope.event.actor != actorPrincipalId) return failure(LearningJournalStoreFailure.INVALID_EVENT)
        return when (val appended = journalWriter.append(envelope.event, envelope.expectedTaskId)) {
            is LearningJournalStoreResult.Failure -> appended
            is LearningJournalStoreResult.Success -> when (val acknowledged = outbox.acknowledgeOutbox(item.id, actorPrincipalId)) {
                is MemoryOutboxResult.Success -> appended
                is MemoryOutboxResult.Failure -> failure(
                    if (acknowledged.reason == MemoryRepositoryFailure.DECRYPTION_FAILED)
                        LearningJournalStoreFailure.DECRYPTION_FAILED else LearningJournalStoreFailure.STORAGE_ERROR
                )
            }
        }
    }

    fun dispatchPending(actorPrincipalId: String, limit: Int = 25): List<LearningOutboxDispatchItemResult> {
        val pending = outbox.pendingOutbox(actorPrincipalId, limit)
        if (pending is MemoryOutboxResult.Failure) {
            return listOf(
                LearningOutboxDispatchItemResult(
                    "outbox-read-failed",
                    failure(
                        if (pending.reason == MemoryRepositoryFailure.DECRYPTION_FAILED)
                            LearningJournalStoreFailure.DECRYPTION_FAILED else LearningJournalStoreFailure.STORAGE_ERROR
                    )
                )
            )
        }
        return (pending as MemoryOutboxResult.Success).value.map { item ->
            LearningOutboxDispatchItemResult(item.id, dispatch(item, actorPrincipalId))
        }
    }

    private fun failure(reason: LearningJournalStoreFailure) =
        LearningJournalStoreResult.Failure(reason)
}
