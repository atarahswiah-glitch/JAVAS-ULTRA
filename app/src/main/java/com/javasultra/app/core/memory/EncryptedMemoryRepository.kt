package com.javasultra.app.core.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.security.MessageDigest

sealed interface MemoryRepositoryResult<out T> {
    data class Success<T>(val value: T) : MemoryRepositoryResult<T>
    data class Failure(val reason: MemoryRepositoryFailure) : MemoryRepositoryResult<Nothing>
}

enum class MemoryRepositoryFailure {
    INVALID_RECORD, OWNER_MISMATCH, NOT_FOUND, ACCESS_DENIED, DUPLICATE_ID, STORAGE_ERROR, DECRYPTION_FAILED
}

/** Pure validation shared by the atomic encrypted-memory batch insertion boundary. */
object ProposedMemoryBatchPolicy {
    const val MAX_BATCH_SIZE = 100

    fun validate(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryFailure? {
        if (actorPrincipalId.isBlank() || records.isEmpty() || records.size > MAX_BATCH_SIZE) {
            return MemoryRepositoryFailure.INVALID_RECORD
        }
        if (records.map { it.id }.toSet().size != records.size) {
            return MemoryRepositoryFailure.DUPLICATE_ID
        }
        if (records.any { it.ownerPrincipalId != actorPrincipalId }) {
            return MemoryRepositoryFailure.OWNER_MISMATCH
        }
        if (records.any { it.validate().isNotEmpty() || it.lifecycle != MemoryLifecycle.PROPOSED }) {
            return MemoryRepositoryFailure.INVALID_RECORD
        }
        return null
    }
}

interface MemoryRepository {
    /** Exact same-owner retries are idempotent; same-id/different-content is rejected. */
    fun insertProposedBatch(records: List<MemoryRecord>, actorPrincipalId: String): MemoryRepositoryResult<Unit>
    fun insertProposed(record: MemoryRecord, actorPrincipalId: String): MemoryRepositoryResult<Unit>
    fun activate(id: MemoryId, actorPrincipalId: String, at: Instant, stepUpAuthenticated: Boolean = false): MemoryRepositoryResult<MemoryRecord>
    fun read(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<MemoryRecord>
    fun listReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>>
    fun exportReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>>
    fun delete(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<Unit>
    fun proposeCorrection(id: MemoryId, replacement: MemoryRecord, request: MemoryAccessRequest): MemoryRepositoryResult<MemoryRecord>
}

/** Stores ciphertext and an opaque lookup id. All memory fields are encrypted together. */
class EncryptedMemoryRepository(
    context: Context,
    private val encryptor: MemoryEncryptor = AndroidKeystoreMemoryEncryptor()
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), MemoryRepository, MemoryOutboxStore {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA secure_delete = ON")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE ($COL_ID TEXT PRIMARY KEY NOT NULL, $COL_PAYLOAD BLOB NOT NULL, $COL_CREATED INTEGER NOT NULL)")
        createOutboxTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createOutboxTable(db)
        if (newVersion > DATABASE_VERSION) {
            throw IllegalStateException("Unsupported memory database version " + newVersion)
        }
    }

    private fun createOutboxTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $OUTBOX_TABLE (" +
                "$OUTBOX_ID TEXT PRIMARY KEY NOT NULL, " +
                "$OUTBOX_OWNER_HASH TEXT NOT NULL, " +
                "$OUTBOX_PAYLOAD BLOB NOT NULL, " +
                "$OUTBOX_CREATED INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS $OUTBOX_OWNER_CREATED ON $OUTBOX_TABLE ($OUTBOX_OWNER_HASH, $OUTBOX_CREATED)")
    }

    override fun insertProposedBatch(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> = persistBatch(records, null, actorPrincipalId)

    override fun insertProposedBatchWithOutbox(
        records: List<MemoryRecord>,
        outboxItem: MemoryOutboxItem,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> {
        if (!outboxItem.validate()) return failure(MemoryRepositoryFailure.INVALID_RECORD)
        if (outboxItem.ownerPrincipalId != actorPrincipalId) return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
        return persistBatch(records, outboxItem, actorPrincipalId)
    }

    private fun persistBatch(
        records: List<MemoryRecord>,
        outboxItem: MemoryOutboxItem?,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> {
        ProposedMemoryBatchPolicy.validate(records, actorPrincipalId)?.let { return failure(it) }
        return try {
            val prepared = records.map { record ->
                record to ContentValues().apply {
                    put(COL_ID, record.id.value)
                    put(COL_PAYLOAD, encryptor.encrypt(MemoryRecordCodec.encode(record), aad(record.id)))
                    put(COL_CREATED, record.createdAt.toEpochMilli())
                }
            }
            val preparedOutbox = outboxItem?.let { item ->
                ContentValues().apply {
                    put(OUTBOX_ID, item.id)
                    put(OUTBOX_OWNER_HASH, ownerHash(actorPrincipalId))
                    put(OUTBOX_PAYLOAD, encryptor.encrypt(item.payload, outboxAad(item.id, actorPrincipalId)))
                    put(OUTBOX_CREATED, item.createdAt.toEpochMilli())
                }
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                for ((record, values) in prepared) {
                    val existingBytes = db.query(
                        TABLE, arrayOf(COL_PAYLOAD), "$COL_ID = ?", arrayOf(record.id.value),
                        null, null, null
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }
                    if (existingBytes != null) {
                        val existing = MemoryRecordCodec.decode(encryptor.decrypt(existingBytes, aad(record.id)))
                        when (MemoryProposalIdempotencyPolicy.evaluate(existing, record, actorPrincipalId)) {
                            MemoryProposalReplayDecision.EXACT_REPLAY -> Unit
                            MemoryProposalReplayDecision.CONFLICTING_CONTENT ->
                                return failure(MemoryRepositoryFailure.DUPLICATE_ID)
                            MemoryProposalReplayDecision.OWNER_MISMATCH ->
                                return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
                        }
                    } else {
                        db.insertOrThrow(TABLE, null, values)
                    }
                }
                if (outboxItem != null && preparedOutbox != null) {
                    val existing = db.query(
                        OUTBOX_TABLE,
                        arrayOf(OUTBOX_OWNER_HASH, OUTBOX_PAYLOAD, OUTBOX_CREATED),
                        "$OUTBOX_ID = ?", arrayOf(outboxItem.id), null, null, null
                    ).use { cursor ->
                        if (!cursor.moveToFirst()) null
                        else Triple(cursor.getString(0), cursor.getBlob(1), cursor.getLong(2))
                    }
                    if (existing != null) {
                        if (existing.first != ownerHash(actorPrincipalId)) {
                            return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
                        }
                        val clear = encryptor.decrypt(existing.second, outboxAad(outboxItem.id, actorPrincipalId))
                        if (!clear.contentEquals(outboxItem.payload) ||
                            existing.third != outboxItem.createdAt.toEpochMilli()
                        ) return failure(MemoryRepositoryFailure.DUPLICATE_ID)
                    } else {
                        db.insertOrThrow(OUTBOX_TABLE, null, preparedOutbox)
                    }
                }
                db.setTransactionSuccessful()
                MemoryRepositoryResult.Success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
    }

    override fun pendingOutbox(
        actorPrincipalId: String,
        limit: Int
    ): MemoryOutboxResult<List<MemoryOutboxItem>> {
        if (actorPrincipalId.isBlank() || limit !in 1..100) {
            return MemoryOutboxResult.Failure(MemoryRepositoryFailure.INVALID_RECORD)
        }
        return try {
            val result = mutableListOf<MemoryOutboxItem>()
            readableDatabase.query(
                OUTBOX_TABLE,
                arrayOf(OUTBOX_ID, OUTBOX_PAYLOAD, OUTBOX_CREATED),
                "$OUTBOX_OWNER_HASH = ?",
                arrayOf(ownerHash(actorPrincipalId)),
                null, null, "$OUTBOX_CREATED ASC", limit.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val payload = encryptor.decrypt(cursor.getBlob(1), outboxAad(id, actorPrincipalId))
                    result += MemoryOutboxItem(id, actorPrincipalId, payload, Instant.ofEpochMilli(cursor.getLong(2)))
                }
            }
            MemoryOutboxResult.Success(result)
        } catch (_: Exception) {
            MemoryOutboxResult.Failure(MemoryRepositoryFailure.DECRYPTION_FAILED)
        }
    }

    override fun acknowledgeOutbox(id: String, actorPrincipalId: String): MemoryOutboxResult<Unit> {
        if (id.isBlank() || actorPrincipalId.isBlank()) {
            return MemoryOutboxResult.Failure(MemoryRepositoryFailure.INVALID_RECORD)
        }
        return try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val storedOwnerHash = db.query(
                    OUTBOX_TABLE, arrayOf(OUTBOX_OWNER_HASH),
                    "$OUTBOX_ID = ?", arrayOf(id), null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                if (storedOwnerHash != null && storedOwnerHash != ownerHash(actorPrincipalId)) {
                    return MemoryOutboxResult.Failure(MemoryRepositoryFailure.OWNER_MISMATCH)
                }
                if (storedOwnerHash != null) db.delete(OUTBOX_TABLE, "$OUTBOX_ID = ?", arrayOf(id))
                db.setTransactionSuccessful()
                MemoryOutboxResult.Success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            MemoryOutboxResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
    }

    private fun ownerHash(owner: String): String =
        MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun outboxAad(id: String, owner: String) =
        ("javas-memory-outbox-v1:" + id + ":" + owner).toByteArray(Charsets.UTF_8)

    override fun insertProposed(record: MemoryRecord, actorPrincipalId: String): MemoryRepositoryResult<Unit> {
        if (record.validate().isNotEmpty() || record.lifecycle != MemoryLifecycle.PROPOSED) return failure(MemoryRepositoryFailure.INVALID_RECORD)
        if (record.ownerPrincipalId != actorPrincipalId) return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
        return insertProposedBatch(listOf(record), actorPrincipalId)
    }

    override fun activate(
        id: MemoryId,
        actorPrincipalId: String,
        at: Instant,
        stepUpAuthenticated: Boolean
    ): MemoryRepositoryResult<MemoryRecord> {
        val current = load(id) ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
        if (current.ownerPrincipalId != actorPrincipalId) return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
        val prior = current.supersedes?.let { load(it) }
        val activationDecision = MemoryActivationPolicy.evaluate(
            current, prior, MemoryActivationRequest(actorPrincipalId, at, stepUpAuthenticated)
        )
        if (activationDecision is MemoryActivationDecision.Denied) {
            return failure(
                when (activationDecision.reason) {
                    MemoryActivationDenial.OWNER_MISMATCH -> MemoryRepositoryFailure.OWNER_MISMATCH
                    MemoryActivationDenial.STEP_UP_REQUIRED -> MemoryRepositoryFailure.ACCESS_DENIED
                    else -> MemoryRepositoryFailure.INVALID_RECORD
                }
            )
        }
        return try {
            val activated = current.copy(lifecycle = MemoryLifecycle.ACTIVE, updatedAt = at)
            val encryptedActivated = encryptor.encrypt(MemoryRecordCodec.encode(activated), aad(id))
            val db = writableDatabase
            db.beginTransaction()
            try {
                if (prior != null) {
                    val superseded = prior.copy(lifecycle = MemoryLifecycle.SUPERSEDED, updatedAt = at)
                    val priorValues = ContentValues().apply {
                        put(COL_PAYLOAD, encryptor.encrypt(MemoryRecordCodec.encode(superseded), aad(prior.id)))
                    }
                    if (db.update(TABLE, priorValues, "$COL_ID = ?", arrayOf(prior.id.value)) != 1) {
                        return failure(MemoryRepositoryFailure.NOT_FOUND)
                    }
                }
                val values = ContentValues().apply { put(COL_PAYLOAD, encryptedActivated) }
                if (db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.value)) != 1) {
                    return failure(MemoryRepositoryFailure.NOT_FOUND)
                }
                db.setTransactionSuccessful()
                MemoryRepositoryResult.Success(activated)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
    }

    override fun read(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<MemoryRecord> {
        val record = load(id) ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
        if (MemoryAccessPolicy.evaluate(record, request) != MemoryAccessDecision.Allowed) return failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return MemoryRepositoryResult.Success(record)
    }

    override fun listReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>> = try {
        val records = mutableListOf<MemoryRecord>()
        readableDatabase.query(TABLE, arrayOf(COL_ID, COL_PAYLOAD), null, null, null, null, COL_CREATED_DESC).use { cursor ->
            while (cursor.moveToNext()) {
                val id = MemoryId(cursor.getString(0))
                val record = try {
                    MemoryRecordCodec.decode(encryptor.decrypt(cursor.getBlob(1), aad(id)))
                } catch (_: Exception) {
                    return failure(MemoryRepositoryFailure.DECRYPTION_FAILED)
                }
                if (MemoryAccessPolicy.evaluate(record, request) == MemoryAccessDecision.Allowed) records += record
            }
        }
        MemoryRepositoryResult.Success(records)
    } catch (_: Exception) {
        failure(MemoryRepositoryFailure.STORAGE_ERROR)
    }

    /** Explicit export path; callers cannot accidentally export through a READ request. */
    override fun exportReadable(request: MemoryAccessRequest): MemoryRepositoryResult<List<MemoryRecord>> {
        if (request.action != MemoryAction.EXPORT) return failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return listReadable(request)
    }

    override fun delete(id: MemoryId, request: MemoryAccessRequest): MemoryRepositoryResult<Unit> {
        val record = load(id) ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
        if (MemoryAccessPolicy.evaluate(record, request.copy(action = MemoryAction.DELETE)) != MemoryAccessDecision.Allowed) {
            return failure(MemoryRepositoryFailure.ACCESS_DENIED)
        }
        return try {
            val deleted = writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.value))
            if (deleted == 1) MemoryRepositoryResult.Success(Unit) else failure(MemoryRepositoryFailure.NOT_FOUND)
        } catch (_: Exception) {
            failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
    }

    /**
     * Stores a proposed correction while keeping the current record active.
     * The old record is superseded only when the replacement is explicitly activated.
     */
    override fun proposeCorrection(
        id: MemoryId,
        replacement: MemoryRecord,
        request: MemoryAccessRequest
    ): MemoryRepositoryResult<MemoryRecord> {
        val current = load(id) ?: return failure(MemoryRepositoryFailure.NOT_FOUND)
        if (MemoryAccessPolicy.evaluate(current, request.copy(action = MemoryAction.UPDATE)) != MemoryAccessDecision.Allowed) {
            return failure(MemoryRepositoryFailure.ACCESS_DENIED)
        }
        if (request.now.isBefore(current.updatedAt) ||
            replacement.id == id ||
            replacement.ownerPrincipalId != current.ownerPrincipalId ||
            replacement.ownerPrincipalId != request.actorPrincipalId ||
            replacement.lifecycle != MemoryLifecycle.PROPOSED ||
            replacement.supersedes != id ||
            replacement.validate().isNotEmpty()
        ) return failure(MemoryRepositoryFailure.INVALID_RECORD)

        return try {
            val encrypted = encryptor.encrypt(MemoryRecordCodec.encode(replacement), aad(replacement.id))
            val values = ContentValues().apply {
                put(COL_ID, replacement.id.value)
                put(COL_PAYLOAD, encrypted)
                put(COL_CREATED, replacement.createdAt.toEpochMilli())
            }
            val inserted = writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) failure(MemoryRepositoryFailure.DUPLICATE_ID)
            else MemoryRepositoryResult.Success(replacement)
        } catch (_: Exception) {
            failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
    }

    private fun replace(record: MemoryRecord): MemoryRepositoryResult<MemoryRecord> = try {
        val encrypted = encryptor.encrypt(MemoryRecordCodec.encode(record), aad(record.id))
        val values = ContentValues().apply { put(COL_PAYLOAD, encrypted) }
        val changed = writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(record.id.value))
        if (changed == 1) MemoryRepositoryResult.Success(record) else failure(MemoryRepositoryFailure.NOT_FOUND)
    } catch (_: Exception) {
        failure(MemoryRepositoryFailure.STORAGE_ERROR)
    }

    private fun load(id: MemoryId): MemoryRecord? = try {
        readableDatabase.query(TABLE, arrayOf(COL_PAYLOAD), "$COL_ID = ?", arrayOf(id.value), null, null, null).use { cursor ->
            if (!cursor.moveToFirst()) null else MemoryRecordCodec.decode(encryptor.decrypt(cursor.getBlob(0), aad(id)))
        }
    } catch (_: Exception) {
        null
    }

    private fun aad(id: MemoryId) = ("javas-memory-v1:" + id.value).toByteArray(Charsets.UTF_8)
    private fun failure(reason: MemoryRepositoryFailure): MemoryRepositoryResult.Failure = MemoryRepositoryResult.Failure(reason)

    private companion object {
        const val DATABASE_NAME = "javas_encrypted_memory.db"
        const val DATABASE_VERSION = 2
        const val TABLE = "memory_records"
        const val OUTBOX_TABLE = "memory_outbox"
        const val OUTBOX_ID = "outbox_id"
        const val OUTBOX_OWNER_HASH = "owner_hash"
        const val OUTBOX_PAYLOAD = "encrypted_payload"
        const val OUTBOX_CREATED = "created_at"
        const val OUTBOX_OWNER_CREATED = "idx_memory_outbox_owner_created"
        const val COL_ID = "record_id"
        const val COL_PAYLOAD = "encrypted_payload"
        const val COL_CREATED = "created_at"
        const val COL_CREATED_DESC = "created_at DESC"
    }
}
