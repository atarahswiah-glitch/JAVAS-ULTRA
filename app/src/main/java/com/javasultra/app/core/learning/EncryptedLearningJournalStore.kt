package com.javasultra.app.core.learning

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.javasultra.app.core.memory.AndroidKeystoreMemoryEncryptor
import com.javasultra.app.core.memory.MemoryEncryptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant

enum class LearningJournalStoreFailure {
    INVALID_ENTRY, INVALID_EVENT, NOT_FOUND, DUPLICATE_EVENT, EVENT_ID_COLLISION,
    INVALID_TRANSITION, STORAGE_ERROR, DECRYPTION_FAILED
}

sealed interface LearningJournalStoreResult<out T> {
    data class Success<T>(val value: T) : LearningJournalStoreResult<T>
    data class Failure(val reason: LearningJournalStoreFailure) : LearningJournalStoreResult<Nothing>
}

/**
 * Append-only event history plus an encrypted current-entry snapshot.
 * Event append and snapshot replacement share one SQLite transaction.
 */
class EncryptedLearningJournalStore(
    context: Context,
    private val encryptor: MemoryEncryptor = AndroidKeystoreMemoryEncryptor("javas.learning.journal.aesgcm.v1")
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $ENTRIES ($ID TEXT PRIMARY KEY NOT NULL, $PAYLOAD BLOB NOT NULL, $UPDATED INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE $EVENTS ($EVENT_ID TEXT PRIMARY KEY NOT NULL, $ENTRY_ID TEXT NOT NULL, $OCCURRED INTEGER NOT NULL, $PAYLOAD BLOB NOT NULL, FOREIGN KEY($ENTRY_ID) REFERENCES $ENTRIES($ID))")
        db.execSQL("CREATE INDEX journal_events_entry_time ON $EVENTS($ENTRY_ID, $OCCURRED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("No migration path from learning journal database version $oldVersion to $newVersion")
    }

    fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit> {
        if (LearningJournalPolicy.validateEntry(entry) != null) return failure(LearningJournalStoreFailure.INVALID_ENTRY)
        return try {
            val encrypted = encryptor.encrypt(LearningJournalCodec.encodeEntry(entry), aad("entry", entry.id))
            val values = ContentValues().apply {
                put(ID, entry.id)
                put(PAYLOAD, encrypted)
                put(UPDATED, entry.updatedAt.toEpochMilli())
            }
            val inserted = writableDatabase.insertWithOnConflict(ENTRIES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) failure(LearningJournalStoreFailure.INVALID_ENTRY)
            else LearningJournalStoreResult.Success(Unit)
        } catch (_: Exception) {
            failure(LearningJournalStoreFailure.STORAGE_ERROR)
        }
    }

    fun get(entryId: String): LearningJournalStoreResult<LearningJournalEntry> = try {
        val bytes = readableDatabase.query(ENTRIES, arrayOf(PAYLOAD), "$ID = ?", arrayOf(entryId), null, null, null).use {
            if (!it.moveToFirst()) null else it.getBlob(0)
        } ?: return failure(LearningJournalStoreFailure.NOT_FOUND)
        LearningJournalStoreResult.Success(
            LearningJournalCodec.decodeEntry(encryptor.decrypt(bytes, aad("entry", entryId)))
        )
    } catch (_: Exception) {
        failure(LearningJournalStoreFailure.DECRYPTION_FAILED)
    }

    /** Replaying the same event id and payload is safe; reusing an id for different content is rejected. */
    fun append(event: LearningJournalEvent): LearningJournalStoreResult<LearningJournalEntry> {
        val normalizedEvent = event.copy(
            safeDetail = LearningJournalSafeDetail.normalize(event.safeDetail)
        )
        if (normalizedEvent.id.isBlank() || normalizedEvent.entryId.isBlank() || normalizedEvent.actor.isBlank()) {
            return failure(LearningJournalStoreFailure.INVALID_EVENT)
        }
        val db = writableDatabase
        return try {
            db.beginTransaction()
            val existingEvent = db.query(EVENTS, arrayOf(PAYLOAD), "$EVENT_ID = ?", arrayOf(normalizedEvent.id), null, null, null).use {
                if (!it.moveToFirst()) null else it.getBlob(0)
            }
            if (existingEvent != null) {
                val prior = LearningJournalCodec.decodeEvent(encryptor.decrypt(existingEvent, aad("event", normalizedEvent.id)))
                val current = loadEntry(db, normalizedEvent.entryId)
                    ?: run { db.endTransaction(); return failure(LearningJournalStoreFailure.NOT_FOUND) }
                if (prior != normalizedEvent) {
                    db.endTransaction()
                    return failure(LearningJournalStoreFailure.EVENT_ID_COLLISION)
                }
                db.setTransactionSuccessful()
                db.endTransaction()
                return LearningJournalStoreResult.Success(current)
            }

            val current = loadEntry(db, event.entryId)
                ?: run { db.endTransaction(); return failure(LearningJournalStoreFailure.NOT_FOUND) }
            val applied = LearningJournalPolicy.apply(current, normalizedEvent)
            val updated = applied.updatedEntry
                ?: run {
                    db.endTransaction()
                    return failure(if (applied.denial == LearningJournalDenial.INVALID_ENTRY) LearningJournalStoreFailure.INVALID_ENTRY
                    else if (applied.denial == LearningJournalDenial.INVALID_EVENT) LearningJournalStoreFailure.INVALID_EVENT
                    else LearningJournalStoreFailure.INVALID_TRANSITION)
                }

            val encryptedEvent = encryptor.encrypt(LearningJournalCodec.encodeEvent(normalizedEvent), aad("event", event.id))
            val eventValues = ContentValues().apply {
                put(EVENT_ID, normalizedEvent.id)
                put(ENTRY_ID, normalizedEvent.entryId)
                put(OCCURRED, normalizedEvent.occurredAt.toEpochMilli())
                put(PAYLOAD, encryptedEvent)
            }
            check(db.insertOrThrow(EVENTS, null, eventValues) != -1L)

            val encryptedEntry = encryptor.encrypt(LearningJournalCodec.encodeEntry(updated), aad("entry", updated.id))
            val entryValues = ContentValues().apply {
                put(PAYLOAD, encryptedEntry)
                put(UPDATED, updated.updatedAt.toEpochMilli())
            }
            check(db.update(ENTRIES, entryValues, "$ID = ?", arrayOf(updated.id)) == 1)
            db.setTransactionSuccessful()
            db.endTransaction()
            LearningJournalStoreResult.Success(updated)
        } catch (_: Exception) {
            if (db.inTransaction()) db.endTransaction()
            failure(LearningJournalStoreFailure.STORAGE_ERROR)
        }
    }

    fun events(entryId: String): LearningJournalStoreResult<List<LearningJournalEvent>> = try {
        val result = mutableListOf<LearningJournalEvent>()
        readableDatabase.query(EVENTS, arrayOf(EVENT_ID, PAYLOAD), "$ENTRY_ID = ?", arrayOf(entryId), null, null, "$OCCURRED ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val eventId = cursor.getString(0)
                result += LearningJournalCodec.decodeEvent(encryptor.decrypt(cursor.getBlob(1), aad("event", eventId)))
            }
        }
        LearningJournalStoreResult.Success(result)
    } catch (_: Exception) {
        failure(LearningJournalStoreFailure.DECRYPTION_FAILED)
    }

    private fun loadEntry(db: SQLiteDatabase, id: String): LearningJournalEntry? =
        db.query(ENTRIES, arrayOf(PAYLOAD), "$ID = ?", arrayOf(id), null, null, null).use {
            if (!it.moveToFirst()) null
            else LearningJournalCodec.decodeEntry(encryptor.decrypt(it.getBlob(0), aad("entry", id)))
        }

    private fun aad(kind: String, id: String) = "javas-learning-journal-v1:$kind:$id".toByteArray(Charsets.UTF_8)
    private fun failure(reason: LearningJournalStoreFailure) = LearningJournalStoreResult.Failure(reason)

    private companion object {
        const val DATABASE_NAME = "javas_learning_journal.db"
        const val DATABASE_VERSION = 1
        const val ENTRIES = "learning_journal_entries"
        const val EVENTS = "learning_journal_events"
        const val ID = "entry_id"
        const val EVENT_ID = "event_id"
        const val ENTRY_ID = "entry_id"
        const val PAYLOAD = "encrypted_payload"
        const val UPDATED = "updated_at"
        const val OCCURRED = "occurred_at"
    }
}

/** Versioned, bounded binary format; persisted payloads are encrypted by the store. */
object LearningJournalCodec {
    private const val VERSION = 1
    private const val MAX_REFERENCES = 2_000
    private const val MAX_PAYLOAD_BYTES = 262_144

    fun encodeEntry(value: LearningJournalEntry): ByteArray = encode { out ->
        require(LearningJournalPolicy.validateEntry(value) == null)
        out.writeInt(VERSION)
        out.writeUTF(value.id); out.writeUTF(value.taskId); out.writeUTF(value.objective)
        out.writeUTF(value.domain.name); out.writeUTF(value.state.name)
        out.writeLong(value.createdAt.toEpochMilli()); out.writeLong(value.updatedAt.toEpochMilli())
        writeSet(out, value.sourceIds); writeSet(out, value.claimIds)
        out.writeNullable(LearningJournalSafeDetail.normalize(value.failureCode))
    }

    fun decodeEntry(bytes: ByteArray): LearningJournalEntry = decode(bytes) { input ->
        require(input.readInt() == VERSION) { "Unsupported journal entry format" }
        val entry = LearningJournalEntry(
            input.readUTF(), input.readUTF(), input.readUTF(),
            LearningDomain.valueOf(input.readUTF()), LearningJournalState.valueOf(input.readUTF()),
            Instant.ofEpochMilli(input.readLong()), Instant.ofEpochMilli(input.readLong()),
            readSet(input), readSet(input), input.readNullable()
        )
        require(LearningJournalPolicy.validateEntry(entry) == null)
        entry
    }

    fun encodeEvent(value: LearningJournalEvent): ByteArray = encode { out ->
        require(value.id.isNotBlank() && value.entryId.isNotBlank() && value.actor.isNotBlank())
        out.writeInt(VERSION)
        out.writeUTF(value.id); out.writeUTF(value.entryId); out.writeUTF(value.type.name)
        out.writeLong(value.occurredAt.toEpochMilli()); out.writeUTF(value.actor)
        out.writeNullable(value.sourceId); out.writeNullable(value.claimId)
        out.writeNullable(LearningJournalSafeDetail.normalize(value.safeDetail))
    }

    fun decodeEvent(bytes: ByteArray): LearningJournalEvent = decode(bytes) { input ->
        require(input.readInt() == VERSION) { "Unsupported journal event format" }
        LearningJournalEvent(
            input.readUTF(), input.readUTF(), LearningJournalEventType.valueOf(input.readUTF()),
            Instant.ofEpochMilli(input.readLong()), input.readUTF(), input.readNullable(),
            input.readNullable(), input.readNullable()
        ).also {
            require(it.id.isNotBlank() && it.entryId.isNotBlank() && it.actor.isNotBlank())
            require(it.safeDetail == null || LearningJournalSafeDetail.normalize(it.safeDetail) == it.safeDetail)
        }
    }

    private fun encode(write: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(write)
        return bytes.toByteArray().also { require(it.size <= MAX_PAYLOAD_BYTES) }
    }

    private fun <T> decode(bytes: ByteArray, read: (DataInputStream) -> T): T {
        require(bytes.size in 1..MAX_PAYLOAD_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val value = read(input)
            require(input.available() == 0) { "Unexpected trailing journal data" }
            return value
        }
    }

    private fun writeSet(out: DataOutputStream, values: Set<String>) {
        require(values.size <= MAX_REFERENCES && values.none(String::isBlank))
        out.writeInt(values.size)
        values.sorted().forEach(out::writeUTF)
    }

    private fun readSet(input: DataInputStream): Set<String> {
        val size = input.readInt()
        require(size in 0..MAX_REFERENCES)
        return buildSet { repeat(size) { add(input.readUTF()) } }
    }

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null
}
