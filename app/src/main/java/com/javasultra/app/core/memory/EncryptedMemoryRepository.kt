package com.javasultra.app.core.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant

sealed interface MemoryRepositoryResult<out T> {
    data class Success<T>(val value: T) : MemoryRepositoryResult<T>
    data class Failure(val reason: MemoryRepositoryFailure) : MemoryRepositoryResult<Nothing>
}

enum class MemoryRepositoryFailure {
    INVALID_RECORD, OWNER_MISMATCH, NOT_FOUND, ACCESS_DENIED, DUPLICATE_ID, STORAGE_ERROR, DECRYPTION_FAILED
}

interface MemoryRepository {
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
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), MemoryRepository {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA secure_delete = ON")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE ($COL_ID TEXT PRIMARY KEY NOT NULL, $COL_PAYLOAD BLOB NOT NULL, $COL_CREATED INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("No migration path from database version " + oldVersion + " to " + newVersion)
    }

    override fun insertProposed(record: MemoryRecord, actorPrincipalId: String): MemoryRepositoryResult<Unit> {
        if (record.validate().isNotEmpty() || record.lifecycle != MemoryLifecycle.PROPOSED) return failure(MemoryRepositoryFailure.INVALID_RECORD)
        if (record.ownerPrincipalId != actorPrincipalId) return failure(MemoryRepositoryFailure.OWNER_MISMATCH)
        return try {
            val encrypted = encryptor.encrypt(MemoryRecordCodec.encode(record), aad(record.id))
            val values = ContentValues().apply {
                put(COL_ID, record.id.value)
                put(COL_PAYLOAD, encrypted)
                put(COL_CREATED, record.createdAt.toEpochMilli())
            }
            val inserted = writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) failure(MemoryRepositoryFailure.DUPLICATE_ID) else MemoryRepositoryResult.Success(Unit)
        } catch (_: Exception) {
            failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }
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
        const val DATABASE_VERSION = 1
        const val TABLE = "memory_records"
        const val COL_ID = "record_id"
        const val COL_PAYLOAD = "encrypted_payload"
        const val COL_CREATED = "created_at"
        const val COL_CREATED_DESC = "created_at DESC"
    }
}
